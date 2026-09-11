#include <cassert>
#include <cstddef>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "application/chat_export.h"

namespace {

using linecode::application::ChatExportDelivery;
using linecode::application::ChatExportService;
using linecode::application::RenderedTranscriptRequest;
using linecode::application::ShareFileRequest;
using linecode::domain::ChatMessage;
using linecode::domain::MessageRole;

std::string Text(const std::vector<std::byte> &bytes) {
  return {reinterpret_cast<const char *>(bytes.data()), bytes.size()};
}

class RecordingDelivery final : public ChatExportDelivery {
public:
  bool copy_result{true};
  bool share_text_result{true};
  bool share_file_result{true};
  bool render_result{true};
  std::string copied;
  std::string shared;
  std::vector<ShareFileRequest> files;
  std::vector<RenderedTranscriptRequest> rendered;

  bool CopyText(const std::string_view text) override {
    copied = text;
    return copy_result;
  }

  bool ShareText(const std::string_view text) override {
    shared = text;
    return share_text_result;
  }

  bool ShareFile(const ShareFileRequest &file) override {
    files.push_back(file);
    return share_file_result;
  }

  bool RenderAndShare(const RenderedTranscriptRequest &request) override {
    rendered.push_back(request);
    return render_result;
  }
};

std::vector<ChatMessage> Conversation() {
  std::vector<ChatMessage> result(3);
  result[0].id = 1;
  result[0].role = MessageRole::user;
  result[0].content = "Hello";
  result[1].id = 2;
  result[1].role = MessageRole::assistant;
  result[1].content = "World";
  result[2].id = 3;
  result[2].role = MessageRole::tool;
  result[2].content = "Tool output";
  return result;
}

void RegistryPreservesLegacyOrderAndNames() {
  const auto registry = linecode::application::CreateDefaultChatExportRegistry();
  const auto options = registry.Options();
  assert(options.size() == 5);
  assert(options[0].id == "clipboard");
  assert(options[0].display_name == "Copy to clipboard");
  assert(options[1].id == "plain_text");
  assert(options[2].id == "markdown");
  assert(options[3].id == "pdf");
  assert(options[4].id == "chat_image");
}

void PlainAndMarkdownMatchLegacyDocuments() {
  auto delivery = std::make_shared<RecordingDelivery>();
  ChatExportService service(
      delivery, linecode::application::CreateDefaultChatExportRegistry());
  const auto messages = Conversation();

  assert(service.Export("clipboard", messages).Succeeded());
  assert(delivery->copied ==
         "【Me】\nHello\n\n【AI】\nWorld\n\n【AI】\nTool output\n\n"
         "—— From LineCode Pro");

  assert(service.Export("plain_text", messages).Succeeded());
  assert(delivery->shared == delivery->copied);

  assert(service.Export("markdown", messages).Succeeded());
  assert(delivery->files.size() == 1);
  assert(delivery->files.front().file_name == "chat_export.md");
  assert(delivery->files.front().mime_type == "text/markdown");
  assert(Text(delivery->files.front().content) ==
         "## Me\n\nHello\n\n---\n\n## AI\n\nWorld\n\n---\n\n"
         "## AI\n\nTool output\n\n---\n\n*—— From LineCode Pro*");
}

void RenderersAreDataDrivenAndReceiveTypedBlocks() {
  auto delivery = std::make_shared<RecordingDelivery>();
  ChatExportService service(
      delivery, linecode::application::CreateDefaultChatExportRegistry());
  const auto messages = Conversation();

  assert(service.Export("pdf", messages).Succeeded());
  assert(service.Export("chat_image", messages).Succeeded());
  assert(delivery->rendered.size() == 2);
  assert(delivery->rendered[0].renderer_id == "pdf");
  assert(delivery->rendered[0].file_name == "chat_export.pdf");
  assert(delivery->rendered[1].renderer_id == "chat-image");
  assert(delivery->rendered[1].file_name == "chat_screenshot.png");
  assert(delivery->rendered[0].blocks.size() == 3);
  assert(delivery->rendered[0].blocks[0].speaker == "Me");
  assert(delivery->rendered[0].blocks[0].user);
  assert(!delivery->rendered[0].blocks[1].user);
}

void FailuresAndLargeClipboardWarningAreReported() {
  auto delivery = std::make_shared<RecordingDelivery>();
  ChatExportService service(
      delivery, linecode::application::CreateDefaultChatExportRegistry());
  std::vector<ChatMessage> large(1);
  large.front().id = 1;
  large.front().role = MessageRole::user;
  large.front().content = std::string(5'001, 'x');
  const auto copied = service.Export("clipboard", large);
  assert(copied.Succeeded());
  assert(copied.warn_large_clipboard);

  delivery->share_text_result = false;
  const auto unavailable = service.Export("plain_text", large);
  assert(!unavailable.Succeeded());
  assert(!unavailable.error.empty());

  const auto unknown = service.Export("not-registered", large);
  assert(!unknown.Succeeded());
  assert(unknown.error.find("not-registered") != std::string::npos);
}

} // namespace

int main() {
  RegistryPreservesLegacyOrderAndNames();
  PlainAndMarkdownMatchLegacyDocuments();
  RenderersAreDataDrivenAndReceiveTypedBlocks();
  FailuresAndLargeClipboardWarningAreReported();
}
