#pragma once

#include <cstddef>
#include <span>
#include <string>

namespace linecode::presentation {

enum class ToolApprovalVisualKind {
  terminal,
  deletion,
  generic,
};

struct ToolApprovalViewState final {
  std::string tool_call_id;
  std::string tool_name;
  std::string arguments;
  bool can_allow_permanently = false;
  bool submitted = false;

  bool operator==(const ToolApprovalViewState &) const = default;
};

struct ToolApprovalPresentation final {
  ToolApprovalVisualKind visual = ToolApprovalVisualKind::generic;
  std::string tool_title;
  std::string explanation;
  std::string action;
  bool show_allow_always = false;
  bool actions_enabled = true;

  bool operator==(const ToolApprovalPresentation &) const = default;
};

enum class ToolApprovalActionsLayout {
  horizontal,
  stacked,
};

// Converts raw tool-call state into the text and visual policy used by the
// controlled component. Empty explanation intentionally means "use the
// localized default question"; terminal/delete titles are localized by the
// View from visual rather than being baked into this locale-neutral model.
[[nodiscard]] ToolApprovalPresentation
PresentToolApproval(const ToolApprovalViewState &state);

// Mirrors the legacy LinearLayout measurement rule using already-measured
// button widths (including each button's horizontal padding).
[[nodiscard]] ToolApprovalActionsLayout
ChooseToolApprovalActionsLayout(float available_width,
                                std::span<const float> button_widths,
                                float spacing = 6.0F) noexcept;

} // namespace linecode::presentation
