#pragma once

#include <huxerui/root.h>

namespace linecode::infrastructure {

// Installs the platform delivery adapter and provides ChatExportService.
void InstallChatExport(huxerui::RootContext &root);

} // namespace linecode::infrastructure
