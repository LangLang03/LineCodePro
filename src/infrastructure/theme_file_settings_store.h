#pragma once

#include <map>
#include <mutex>

#include <huxerui/file.h>

#include "application/ports/settings_store.h"

namespace linecode::infrastructure {

/// Small portable file-backed adapter suitable until the shared settings DB
/// adapter is wired. The application depends only on SettingsStore.
class ThemeFileSettingsStore final : public application::SettingsStore {
public:
  explicit ThemeFileSettingsStore(huxerui::File data_directory);

  [[nodiscard]] std::optional<std::string>
  Read(std::string_view key) const override;
  void Write(std::string_view key, std::string value) override;

private:
  void LoadLocked() const;
  void FlushLocked() const;

  huxerui::File directory_;
  huxerui::File file_;
  mutable std::mutex mutex_;
  mutable bool loaded_{};
  mutable std::map<std::string, std::string, std::less<>> values_;
};

} // namespace linecode::infrastructure
