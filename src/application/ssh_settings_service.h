#pragma once

#include <expected>
#include <memory>
#include <string>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/ssh_config.h"

namespace linecode::application {

namespace ssh_setting_keys {
inline constexpr auto config = "@lineai_ssh_config";
} // namespace ssh_setting_keys

struct SshConnectionError final {
  std::string message;
};

template <class Value>
using SshConnectionResult = std::expected<Value, SshConnectionError>;

class SshConnectionTester {
public:
  virtual ~SshConnectionTester() = default;

  [[nodiscard]] virtual huxerui::Task<SshConnectionResult<std::string>>
  Test(domain::SshConfig config) = 0;
};

class SshSettingsService {
public:
  virtual ~SshSettingsService() = default;

  [[nodiscard]] virtual huxerui::Task<SettingsResult<domain::SshConfig>>
  Load() = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  Save(domain::SshConfig config) = 0;
  [[nodiscard]] virtual huxerui::Task<SshConnectionResult<std::string>>
  Test(domain::SshConfig config) = 0;
};

} // namespace linecode::application
