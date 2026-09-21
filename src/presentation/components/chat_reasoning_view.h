#pragma once

#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/huxerui.h>

#include "domain/app_state.h"

namespace linecode::presentation::chat_timeline {

[[nodiscard]] bool ToggleState(std::span<const std::string> toggled,
                               std::string_view key, bool default_value);

void ToggleKey(huxerui::State<std::vector<std::string>> toggled,
               std::string key);

[[nodiscard]] huxerui::View
ThinkingDisclosureBlock(std::string_view text, huxerui::StringVariant label,
                        std::string key, bool auto_expand, bool scrollable,
                        huxerui::State<std::vector<std::string>> toggled);

[[nodiscard]] huxerui::View
ReasoningTimelineBlock(const domain::AssistantReasoningEvent &reasoning,
                       std::string key, bool live, bool auto_expand,
                       bool scrollable,
                       huxerui::State<std::vector<std::string>> toggled);

} // namespace linecode::presentation::chat_timeline
