#include "application/legacy_attachment_prompt_renderer.h"

#include <string_view>
#include <utility>

namespace linecode::application {
namespace {

std::string_view Trim(std::string_view value) noexcept {
  while (!value.empty() &&
         static_cast<unsigned char>(value.front()) <=
             static_cast<unsigned char>(' ')) {
    value.remove_prefix(1);
  }
  while (!value.empty() &&
         static_cast<unsigned char>(value.back()) <=
             static_cast<unsigned char>(' ')) {
    value.remove_suffix(1);
  }
  return value;
}

} // namespace

LegacyAttachmentPromptRenderer::LegacyAttachmentPromptRenderer(
    AttachmentPromptText text)
    : text_(std::move(text)) {}

std::string LegacyAttachmentPromptRenderer::Render(
    const std::span<const domain::ChatMessage> history) const {
  std::string sections;
  std::size_t section_count{};
  for (const auto &message : history) {
    if (message.role != domain::MessageRole::user ||
        message.attachments.empty()) {
      continue;
    }

    const auto recalled = RecallText(message);
    std::string label{Trim(recalled)};
    if (label.empty()) {
      label = text_.user_message_label + std::to_string(section_count + 1U);
    }
    if (!sections.empty()) {
      sections += "\n\n";
    }
    sections += "### ";
    sections += label;
    sections += '\n';
    for (const auto &attachment : message.attachments) {
      sections += "- ";
      sections += attachment.Name();
      sections += " (";
      sections += attachment.Source();
      sections += "): ";
      sections += attachment.Path();
      sections += '\n';
    }
    ++section_count;
  }
  if (section_count == 0U) {
    return {};
  }
  while (!sections.empty() &&
         static_cast<unsigned char>(sections.back()) <=
             static_cast<unsigned char>(' ')) {
    sections.pop_back();
  }
  return text_.files_header + '\n' + text_.files_description + '\n' +
         sections;
}

std::string LegacyAttachmentPromptRenderer::RecallText(
    const domain::ChatMessage &message) const {
  constexpr std::string_view legacy_reference_marker =
      "\n\n[引用文件]\n";
  std::string_view content = message.content;
  if (const auto marker = content.find(legacy_reference_marker);
      marker != std::string_view::npos) {
    content = content.substr(0, marker);
  }
  const auto trimmed = Trim(content);
  if (trimmed.empty() || trimmed == text_.attached_files_label) {
    return {};
  }
  return std::string{content};
}

} // namespace linecode::application
