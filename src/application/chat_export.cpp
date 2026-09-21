#include "application/chat_export.h"

#include <algorithm>
#include <array>
#include <iterator>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

constexpr std::size_t kLargeClipboardCharacters = 5'000;

constexpr std::array<std::string_view, 3> kSpeakerLabels{"Me", "AI", "AI"};

std::string_view Speaker(const domain::MessageRole role) {
  return kSpeakerLabels.at(static_cast<std::size_t>(role));
}

std::string ToPlainText(std::span<const domain::ChatMessage> messages) {
  std::string result;
  for (const auto &message : messages) {
    result += "【";
    result += Speaker(message.role);
    result += "】\n";
    result += message.content;
    result += "\n\n";
  }
  result += "—— From LineCode Pro";
  return result;
}

std::string ToMarkdown(std::span<const domain::ChatMessage> messages) {
  std::string result;
  for (const auto &message : messages) {
    result += "## ";
    result += Speaker(message.role);
    result += "\n\n";
    result += message.content;
    result += "\n\n---\n\n";
  }
  result += "*—— From LineCode Pro*";
  return result;
}

std::vector<TranscriptBlock>
ToTranscript(std::span<const domain::ChatMessage> messages) {
  std::vector<TranscriptBlock> result;
  result.reserve(messages.size());
  std::ranges::transform(
      messages, std::back_inserter(result), [](const auto &message) {
        return TranscriptBlock{.speaker = std::string{Speaker(message.role)},
                               .content = message.content,
                               .user = message.role == domain::MessageRole::user};
      });
  return result;
}

ChatExportResult DeliveryResult(const bool accepted) {
  return {.accepted = accepted,
          .warn_large_clipboard = false,
          .error = accepted ? std::string{}
                            : std::string{"Native export is unavailable"}};
}

class ClipboardFormat final : public ChatExportFormat {
public:
  ChatExportOption Option() const override {
    return {.id = "clipboard", .display_name = "Copy to clipboard"};
  }

  ChatExportResult
  Export(const std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const override {
    const auto text = ToPlainText(messages);
    auto result = DeliveryResult(delivery.CopyText(text));
    result.warn_large_clipboard = text.size() > kLargeClipboardCharacters;
    return result;
  }
};

class PlainTextFormat final : public ChatExportFormat {
public:
  ChatExportOption Option() const override {
    return {.id = "plain_text", .display_name = "Plain text share"};
  }

  ChatExportResult
  Export(const std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const override {
    return DeliveryResult(delivery.ShareText(ToPlainText(messages)));
  }
};

class MarkdownFormat final : public ChatExportFormat {
public:
  ChatExportOption Option() const override {
    return {.id = "markdown", .display_name = "Markdown file(.md)"};
  }

  ChatExportResult
  Export(const std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const override {
    const auto text = ToMarkdown(messages);
    std::vector<std::byte> content(text.size());
    std::ranges::transform(text, content.begin(), [](const char value) {
      return static_cast<std::byte>(static_cast<unsigned char>(value));
    });
    return DeliveryResult(delivery.ShareFile(
        {.file_name = "chat_export.md",
         .mime_type = "text/markdown",
         .content = std::move(content)}));
  }
};

class RenderedFormat final : public ChatExportFormat {
public:
  RenderedFormat(ChatExportOption option, std::string renderer_id,
                 std::string file_name, std::string mime_type)
      : option_(std::move(option)), renderer_id_(std::move(renderer_id)),
        file_name_(std::move(file_name)), mime_type_(std::move(mime_type)) {}

  ChatExportOption Option() const override { return option_; }

  ChatExportResult
  Export(const std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const override {
    return DeliveryResult(delivery.RenderAndShare(
        {.renderer_id = renderer_id_,
         .file_name = file_name_,
         .mime_type = mime_type_,
         .blocks = ToTranscript(messages)}));
  }

private:
  ChatExportOption option_;
  std::string renderer_id_;
  std::string file_name_;
  std::string mime_type_;
};

} // namespace

void ChatExportRegistry::Register(std::unique_ptr<ChatExportFormat> format) {
  if (!format)
    throw std::invalid_argument("Chat export format cannot be null");
  const auto option = format->Option();
  if (option.id.empty() || option.display_name.empty())
    throw std::invalid_argument("Chat export format identity cannot be empty");
  if (std::ranges::any_of(formats_, [&option](const auto &registered) {
        return registered->Option().id == option.id;
      }))
    throw std::invalid_argument("Duplicate chat export format: " + option.id);
  formats_.push_back(std::move(format));
}

std::vector<ChatExportOption> ChatExportRegistry::Options() const {
  std::vector<ChatExportOption> result;
  result.reserve(formats_.size());
  std::ranges::transform(formats_, std::back_inserter(result),
                         [](const auto &format) { return format->Option(); });
  return result;
}

ChatExportResult ChatExportRegistry::Export(
    const std::string_view id,
    const std::span<const domain::ChatMessage> messages,
    ChatExportDelivery &delivery) const {
  const auto found = std::ranges::find_if(
      formats_, [id](const auto &format) { return format->Option().id == id; });
  if (found == formats_.end())
    return {.accepted = false,
            .warn_large_clipboard = false,
            .error = "Unknown chat export format: " + std::string{id}};
  return (*found)->Export(messages, delivery);
}

ChatExportService::ChatExportService(
    std::shared_ptr<ChatExportDelivery> delivery, ChatExportRegistry registry)
    : delivery_(std::move(delivery)), registry_(std::move(registry)) {
  if (!delivery_)
    throw std::invalid_argument("Chat export delivery cannot be null");
}

std::vector<ChatExportOption> ChatExportService::Options() const {
  return registry_.Options();
}

ChatExportResult ChatExportService::Export(
    const std::string_view id,
    const std::span<const domain::ChatMessage> messages) const {
  return registry_.Export(id, messages, *delivery_);
}

ChatExportRegistry CreateDefaultChatExportRegistry() {
  ChatExportRegistry registry;
  registry.Register(std::make_unique<ClipboardFormat>());
  registry.Register(std::make_unique<PlainTextFormat>());
  registry.Register(std::make_unique<MarkdownFormat>());
  registry.Register(std::make_unique<RenderedFormat>(
      ChatExportOption{.id = "pdf", .display_name = "PDF file"}, "pdf",
      "chat_export.pdf", "application/pdf"));
  registry.Register(std::make_unique<RenderedFormat>(
      ChatExportOption{.id = "chat_image",
                       .display_name = "Chat screenshot (image)"},
      "chat-image", "chat_screenshot.png", "image/png"));
  return registry;
}

} // namespace linecode::application
