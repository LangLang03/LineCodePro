#pragma once

#include <memory>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"

namespace linecode::application {

// Bridges the two persistence contracts used during the C++ migration. The
// destination remains the synchronous theme store; the source is the legacy
// shared SQLite settings table used by every other setting.
class ThemeSettingsMigration final {
public:
  [[nodiscard]] static huxerui::Task<SettingsResult<bool>> ImportIfMissing(
      std::shared_ptr<SettingsStore> destination,
      std::shared_ptr<AsyncSettingsStore> source);
};

} // namespace linecode::application
