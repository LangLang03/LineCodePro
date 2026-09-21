#pragma once

#if defined(__ANDROID__)

#include <huxerui/root.h>

namespace linecode::infrastructure {

void InstallAndroidWindowInsets(huxerui::RootContext& root);

} // namespace linecode::infrastructure

#endif
