#include "gtest_support.h"
#include <array>
#include <cstddef>
#include <iostream>
#include <string>
#include <vector>

#include "application/chat_session.h"
#include "application/composer_image_input.h"
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
  EXPECT_EXPRESSION(derived.Name() == "Main.cpp");
  EXPECT_EXPRESSION(derived.Path() == " /repo/src/Main.cpp/ ");
  EXPECT_EXPRESSION(derived.Source() == InputAttachment::source_local);

  const InputAttachment remote{"remote.hpp", "/srv/remote.hpp", "ssh"};
  EXPECT_EXPRESSION(remote.Matches("/srv/remote.hpp", "ssh"));
  EXPECT_EXPRESSION(!remote.Matches("/srv/remote.hpp", "local"));

  const InputAttachment provider{"", "/tmp/file.txt", "terminal_provider"};
  EXPECT_EXPRESSION(provider.Name() == "file.txt");
  EXPECT_EXPRESSION(provider.Source() == InputAttachment::source_terminal_provider);
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
  EXPECT_EXPRESSION(sent.has_value());
  EXPECT_EXPRESSION(sent->content.empty());
  EXPECT_EXPRESSION(sent->attachments.size() == 2U);
  EXPECT_EXPRESSION(sent->attachments[0].Name() == "Main.cpp");
  EXPECT_EXPRESSION(sent->attachments[0].Source() == "ssh");
  EXPECT_EXPRESSION(sent->attachments[1].Source() == "local");
  EXPECT_EXPRESSION(store.Messages().front() == *sent);

  const auto empty = send.Execute("", {{"", "", "local"}});
  EXPECT_EXPRESSION(!empty.has_value());
  EXPECT_EXPRESSION(empty.error() == linecode::application::SendMessageError::empty);

  const auto utf8 = send.Execute("  你好  ");
  EXPECT_EXPRESSION(utf8.has_value());
  EXPECT_EXPRESSION(utf8->content == "你好");

  linecode::application::ChatSession session{
      std::make_unique<linecode::infrastructure::InMemoryConversationStore>()};
  const auto session_sent =
      session.Send("", {{"notes.md", "/repo/notes.md", "local"}});
  EXPECT_EXPRESSION(session_sent.has_value());
  EXPECT_EXPRESSION(session.Messages().front().attachments == session_sent->attachments);
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
  EXPECT_EXPRESSION(rendered == expected);

  const std::vector<ChatMessage> legacy_reference{
      {.id = 4,
       .role = MessageRole::user,
       .content = "查看这个\n\n[引用文件]\n- stale",
       .attachments = {{"a.txt", "/a.txt", "local"}}},
  };
  EXPECT_EXPRESSION(renderer.Render(legacy_reference).find("### 查看这个\n") !=
         std::string::npos);
  EXPECT_EXPRESSION(renderer.Render({}).empty());

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
  EXPECT_EXPRESSION(localized.Render(only_attachment) ==
         "FILES\nDESC\n### TURN 1\n- a (local): /a");
}

void AttachmentJsonRoundTripsAndRejectsUnsafeShapes() {
  const std::vector<InputAttachment> original{
      {"quote\"\nname", "/tmp/line\npath", "terminal_provider"},
      {"remote", "/srv/remote", "ssh"},
  };
  const auto encoded =
      linecode::infrastructure::EncodeAttachmentJson(original);
  EXPECT_EXPRESSION(!encoded.empty());
  EXPECT_EXPRESSION(linecode::infrastructure::DecodeAttachmentJson(encoded) == original);

  EXPECT_EXPRESSION(linecode::infrastructure::DecodeAttachmentJson("not-json").empty());
  EXPECT_EXPRESSION(linecode::infrastructure::DecodeAttachmentJson("[]").empty());
  const auto partially_valid =
      linecode::infrastructure::DecodeAttachmentJson(
          R"({"attachments":[null,{"path":""},{"path":"/ok"},{"path":7}]})");
  EXPECT_EXPRESSION(partially_valid.size() == 1U);
  EXPECT_EXPRESSION(partially_valid.front().Name() == "ok");
  EXPECT_EXPRESSION(partially_valid.front().Source() == "local");

  const std::string oversized(
      linecode::infrastructure::max_attachment_json_bytes + 1U, 'x');
  EXPECT_EXPRESSION(linecode::infrastructure::DecodeAttachmentJson(oversized).empty());
}

void ComposerImageInputValidatesAndEncodesSupportedFormats() {
  constexpr std::array png{
      std::byte{0x89}, std::byte{0x50}, std::byte{0x4E}, std::byte{0x47},
      std::byte{0x0D}, std::byte{0x0A}, std::byte{0x1A}, std::byte{0x0A},
  };
  const auto image = linecode::application::EncodeComposerImage("图.png", png);
  EXPECT_EXPRESSION(image.has_value());
  EXPECT_EXPRESSION(image->name == "图.png");
  EXPECT_EXPRESSION(image->mime_type == "image/png");
  EXPECT_EXPRESSION(image->base64 == "iVBORw0KGgo=");

  constexpr std::array jpeg{
      std::byte{0xFF}, std::byte{0xD8}, std::byte{0xFF}, std::byte{0x00},
  };
  const auto jpeg_image =
      linecode::application::EncodeComposerImage("photo.jpg", jpeg);
  EXPECT_EXPRESSION(jpeg_image.has_value());
  EXPECT_EXPRESSION(jpeg_image->mime_type == "image/jpeg");
  EXPECT_EXPRESSION(jpeg_image->base64 == "/9j/AA==");

  EXPECT_EXPRESSION(linecode::application::EncodeComposerImage("empty", {}).error() ==
         linecode::application::ComposerImageInputError::empty);
  constexpr std::array unsupported{std::byte{'G'}, std::byte{'I'},
                                   std::byte{'F'}};
  EXPECT_EXPRESSION(linecode::application::EncodeComposerImage("image.gif", unsupported)
             .error() == linecode::application::
                             ComposerImageInputError::unsupported_format);

  const std::vector oversized(
      linecode::application::max_composer_image_input_bytes + 1U,
      std::byte{0});
  EXPECT_EXPRESSION(linecode::application::EncodeComposerImage("too-large.png", oversized)
             .error() ==
         linecode::application::ComposerImageInputError::too_large);
}

} // namespace

TEST(attachment_backend_tests, LegacySuite) {
  InputAttachmentMatchesLegacyNormalization();
  SendSupportsAttachmentOnlyMessages();
  PromptMatchesLegacyModelPromptController();
  AttachmentJsonRoundTripsAndRejectsUnsafeShapes();
  ComposerImageInputValidatesAndEncodesSupportedFormats();
}
