#pragma once

#if defined(__ANDROID__)

#include <huxerui/root.h>

namespace linecode::infrastructure {

/// Installs the Android-only keep-alive bridge as the portable application
/// port. This symbol is deliberately absent from non-Android builds.
void InstallAndroidKeepAlive(huxerui::RootContext &root);

} // namespace linecode::infrastructure

#endif
