#pragma once

#include <huxerui/view.h>

#include "domain/app_state.h"

namespace linecode::presentation {

[[nodiscard]] huxerui::View
ShellCommandScreen(const domain::ShellCommandRoute &route);

} // namespace linecode::presentation
