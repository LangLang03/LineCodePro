#pragma once

#if defined(__ANDROID__)
#include <huxerui/view.h>
namespace linecode::presentation {
[[huxerui::composable]] huxerui::View KeepAliveSettingsScreen();
}
#endif
