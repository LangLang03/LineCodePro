#pragma once

#if defined(__ANDROID__)

#include <huxerui/root.h>

namespace linecode::infrastructure {

void InstallAndroidStoragePermission(huxerui::RootContext &root);

} // namespace linecode::infrastructure

#endif
