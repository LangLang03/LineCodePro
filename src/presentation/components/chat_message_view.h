#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <optional>
#include <string_view>
#include <vector>

#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "presentation/components/chat_timeline_view.h"

namespace linecode::presentation::chat_message {

struct ActionCallbacks final {
  std::function<void(const domain::ChatMessage &)> copy;
  std::function<void(const domain::ChatMessage &)> quote;
  std::function<void(const domain::ChatMessage &)> share;
  std::function<void(const domain::ChatMessage &)> select_text;
  std::function<void()> enter_multi_select;
  std::function<void(const domain::ChatMessage &)> recall;
  std::function<void()> export_selected;
};

[[nodiscard]] huxerui::View
MessageBubble(const domain::ChatMessage &message,
              huxerui::State<std::optional<std::uint64_t>> action_message,
              bool multi_select,
              huxerui::State<std::vector<std::uint64_t>> selected_messages,
              ActionCallbacks callbacks,
              const chat_timeline::Settings &timeline_settings,
              huxerui::State<std::vector<std::string>> toggled_timeline,
              const TutorialMarkdownLinkHandler &on_link,
              const TutorialMarkdownCopyHandler &on_copy,
              const chat_timeline::ToolRendererContext &context,
              std::string_view compact_label, bool live = false);

[[nodiscard]] huxerui::View StreamingMessageHeader(
    const domain::ChatMessage &message,
    const chat_timeline::Settings &timeline_settings,
    huxerui::State<std::vector<std::string>> toggled_timeline,
    const TutorialMarkdownLinkHandler &on_link,
    const TutorialMarkdownCopyHandler &on_copy,
    const chat_timeline::ToolRendererContext &context,
    std::string_view compact_label);

[[nodiscard]] huxerui::View StreamingMessageChangedFiles(
    const domain::ChatMessage &message,
    huxerui::State<std::vector<std::string>> toggled_timeline,
    const TutorialMarkdownLinkHandler &on_link,
    const TutorialMarkdownCopyHandler &on_copy,
    const chat_timeline::ToolRendererContext &context);

[[huxerui::composable]] huxerui::View StreamingMessageStatus(bool thinking);

} // namespace linecode::presentation::chat_message
