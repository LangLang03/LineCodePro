#pragma once

#include <huxerui/view.h>

#include "domain/app_state.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
BrowserScreen(const domain::BrowserRoute &route);

} // namespace linecode::presentation
