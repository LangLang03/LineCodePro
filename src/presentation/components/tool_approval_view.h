#pragma once

#include <functional>
#include <string>

#include <huxerui/view.h>

#include "presentation/tool_approval_presentation.h"

namespace linecode::presentation {

struct ToolApprovalCallbacks final {
  std::function<void(std::string)> on_reject;
  std::function<void(std::string)> on_allow_once;
  std::function<void(std::string)> on_allow_always;
};

// Controlled replacement for the composer slot. The caller owns the pending
// state and marks submitted after accepting one of the three callbacks.
[[nodiscard]] huxerui::View ToolApprovalView(const ToolApprovalViewState &state,
                                             ToolApprovalCallbacks callbacks);

} // namespace linecode::presentation
