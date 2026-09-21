#pragma once

#include <functional>

#include <huxerui/view.h>

namespace linecode::presentation {

// Reproduces the compact android.widget.Switch geometry used by LineCode.
// The full 46.5 x 27 control owns input and thumb travel; its visible track is
// a separately centered 24 x 14 pill, matching the legacy platform widget.
[[nodiscard]] [[huxerui::composable]] huxerui::View
LegacySwitch(bool checked, std::function<void(bool)> on_changed);

} // namespace linecode::presentation
