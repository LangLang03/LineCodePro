#pragma once

#include <huxerui/root.h>

namespace linecode::infrastructure {

// Platform-selected root service. Android is implemented directly in C++/JNI;
// no application Java/Kotlin class is involved.
void InstallErrorLogPlatformActions(huxerui::RootContext &root);

} // namespace linecode::infrastructure
