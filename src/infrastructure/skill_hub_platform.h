#pragma once

#include <huxerui/root.h>

namespace linecode::infrastructure {

// Platform-selected implementation. Android uses a Java bridge for the
// system WebView cookie jar and share sheet; Windows provides an explicit
// unavailable cookie boundary while keeping the feature pages navigable.
void InstallSkillHubPlatform(huxerui::RootContext &root);

} // namespace linecode::infrastructure
