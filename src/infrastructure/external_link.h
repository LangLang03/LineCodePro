#pragma once

#include <huxerui/root.h>

namespace linecode::infrastructure {

// Platform-selected implementation. The symbol is supplied by the Android or
// Windows translation unit and is only registered on those hosts.
void InstallExternalLink(huxerui::RootContext &root);

} // namespace linecode::infrastructure
