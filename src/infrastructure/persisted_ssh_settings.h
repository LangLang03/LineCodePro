#pragma once

#include <memory>

#include "application/ssh_settings_service.h"

namespace linecode::infrastructure {

class PersistedSshSettings final : public application::SshSettingsService {
public:
  explicit PersistedSshSettings(
      std::shared_ptr<application::AsyncSettingsStore> store,
      std::shared_ptr<application::SshConnectionTester> tester = {});

  [[nodiscard]] huxerui::Task<
      application::SettingsResult<domain::SshConfig>>
  Load() override;
  [[nodiscard]] huxerui::Task<application::SettingsResult<void>>
  Save(domain::SshConfig config) override;
  [[nodiscard]] huxerui::Task<
      application::SshConnectionResult<std::string>>
  Test(domain::SshConfig config) override;

private:
  std::shared_ptr<application::AsyncSettingsStore> store_;
  std::shared_ptr<application::SshConnectionTester> tester_;
};

} // namespace linecode::infrastructure
