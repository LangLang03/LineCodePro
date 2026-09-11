#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "application/chat_session.h"
#include "application/legacy_attachment_prompt_renderer.h"
#include "application/send_message.h"
#include "infrastructure/attachment_json_codec.h"
#include "infrastructure/in_memory_conversation_store.h"

namespace {

using linecode::domain::ChatMessage;
using linecode::domain::InputAttachment;
using linecode::domain::MessageRole;

void InputAttachmentMatchesLegacyNormalization() {
  const InputAttachment derived{"", " /repo/src/Main.cpp/ ", "unknown"};
  assert(derived.Name() == "Main.cpp");
  assert(derived.Path() == " /repo/src/Main.cpp/ ");
  assert(derived.Source() == InputAttachment::source_local);

  const InputAttachment remote{"remote.hpp", "/srv/remote.hpp", "ssh"};
  assert(remote.Matches("/srv/remote.hpp", "ssh"));
  assert(!remote.Matches("/srv/remote.hpp", "local"));

  const InputAttachment provider{"", "/tmp/file.txt", "terminal_provider"};
  assert(provider.Name() == "file.txt");
  assert(provider.Source() == InputAttachment::source_terminal_provider);
}

void SendSupportsAttachmentOnlyMessages() {
  linecode::infrastructure::InMemoryConversationStore store;
  linecode::application::SendMessage send{store};
  std::vector<InputAttachment> attachments{
      {"", "", "ssh"},
      {"Main.cpp", "/repo/Main.cpp", "ssh"},
      {"duplicate", "/repo/Main.cpp", "ssh"},
      {"Main.cpp", "/repo/Main.cpp", "local"},
  };

  const auto sent = send.Execute(" \t\n", std::move(attachments));
  assert(sent.has_value());
  assert(sent->content.empty());
  assert(sent->attachments.size() == 2U);
  assert(sent->attachments[0].Name() == "Main.cpp");
  assert(sent->attachments[0].Source() == "ssh");
  assert(sent->attachments[1].Source() == "local");
  assert(store.Messages().front() == *sent);

  const auto empty = send.Execute("", {{"", "", "local"}});
  assert(!empty.has_value());
  assert(empty.error() == linecode::application::SendMessageError::empty);

  const auto utf8 = send.Execute("  你好  ");
  assert(utf8.has_value());
  assert(utf8->content == "你好");

  linecode::application::ChatSession session{
      std::make_unique<linecode::infrastructure::InMemoryConversationStore>()};
  const auto session_sent =
      session.Send("", {{"notes.md", "/repo/notes.md", "local"}});
  assert(session_sent.has_value());
  assert(session.Messages().front().attachments == session_sent->attachments);
}

void PromptMatchesLegacyModelPromptController() {
  const std::vector<ChatMessage> history{
      {.id = 1,
       .role = MessageRole::user,
       .content = "  修复这个问题  ",
       .attachments = {{"Main.cpp", "/repo/Main.cpp", "local"}}},
      {.id = 2,
       .role = MessageRole::assistant,
       .content = "ignored",
       .attachments = {{"ignored", "/tmp/ignored", "local"}}},
      {.id = 3,
       .role = MessageRole::user,
       .content = "已附加文件",
       .attachments = {{"server.log", "/srv/server.log", "ssh"}}},
  };
  const linecode::application::LegacyAttachmentPromptRenderer renderer;
  const auto rendered = renderer.Render(history);
  const std::string expected =
         "## 附加文件位置\n"
         "这些路径来自用户在输入框左侧选择的文件；除非用户明确要求，不要在回复中原样复述。\n"
         "### 修复这个问题\n"
         "- Main.cpp (local): /repo/Main.cpp\n\n\n"
         "### 用户消息 2\n"
         "- server.log (ssh): /srv/server.log";
  if (rendered != expected) {
    std::cerr << "expected:\n" << expected << "\nactual:\n" << rendered << '\n';
  }
  assert(rendered == expected);

  const std::vector<ChatMessage> legacy_reference{
      {.id = 4,
       .role = MessageRole::user,
       .content = "查看这个\n\n[引用文件]\n- stale",
       .attachments = {{"a.txt", "/a.txt", "local"}}},
  };
  assert(renderer.Render(legacy_reference).find("### 查看这个\n") !=
         std::string::npos);
  assert(renderer.Render({}).empty());

  linecode::application::LegacyAttachmentPromptRenderer localized{{
      .files_header = "FILES",
      .files_description = "DESC",
      .user_message_label = "TURN ",
      .attached_files_label = "ATTACHED",
  }};
  const std::vector<ChatMessage> only_attachment{
      {.id = 5,
       .role = MessageRole::user,
       .content = "ATTACHED",
       .attachments = {{"a", "/a", "local"}}},
  };
  assert(localized.Render(only_attachment) ==
         "FILES\nDESC\n### TURN 1\n- a (local): /a");
}

void AttachmentJsonRoundTripsAndRejectsUnsafeShapes() {
  const std::vector<InputAttachment> original{
      {"quote\"\nname", "/tmp/line\npath", "terminal_provider"},
      {"remote", "/srv/remote", "ssh"},
  };
  const auto encoded =
      linecode::infrastructure::EncodeAttachmentJson(original);
  assert(!encoded.empty());
  assert(linecode::infrastructure::DecodeAttachmentJson(encoded) == original);

  assert(linecode::infrastructure::DecodeAttachmentJson("not-json").empty());
  assert(linecode::infrastructure::DecodeAttachmentJson("[]").empty());
  const auto partially_valid =
      linecode::infrastructure::DecodeAttachmentJson(
          R"({"attachments":[null,{"path":""},{"path":"/ok"},{"path":7}]})");
  assert(partially_valid.size() == 1U);
  assert(partially_valid.front().Name() == "ok");
  assert(partially_valid.front().Source() == "local");

  const std::string oversized(
      linecode::infrastructure::max_attachment_json_bytes + 1U, 'x');
  assert(linecode::infrastructure::DecodeAttachmentJson(oversized).empty());
}

} // namespace

int main() {
  InputAttachmentMatchesLegacyNormalization();
  SendSupportsAttachmentOnlyMessages();
  PromptMatchesLegacyModelPromptController();
  AttachmentJsonRoundTripsAndRejectsUnsafeShapes();
}
