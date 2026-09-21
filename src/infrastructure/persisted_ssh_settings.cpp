#include "infrastructure/persisted_ssh_settings.h"

#include <stdexcept>
#include <string>
#include <utility>

#include "infrastructure/ssh_config_codec.h"

namespace linecode::infrastructure {

PersistedSshSettings::PersistedSshSettings(
    std::shared_ptr<application::AsyncSettingsStore> store,
    std::shared_ptr<application::SshConnectionTester> tester)
    : store_(std::move(store)), tester_(std::move(tester)) {
  if (!store_)
    throw std::invalid_argument("SSH settings store must not be empty");
}

huxerui::Task<application::SettingsResult<domain::SshConfig>>
PersistedSshSettings::Load() {
  auto stored = co_await store_->GetString(
      application::ssh_setting_keys::config, std::string{});
  if (!stored)
    co_return std::unexpected(stored.error());
  if (stored->empty())
    co_return domain::SshConfig{};

  auto decoded = DecodeSshConfig(*stored);
  co_return decoded ? std::move(*decoded) : domain::SshConfig{};
}

huxerui::Task<application::SettingsResult<void>>
PersistedSshSettings::Save(domain::SshConfig config) {
  co_return co_await store_->SetString(
      application::ssh_setting_keys::config, EncodeSshConfig(config));
}

huxerui::Task<application::SshConnectionResult<std::string>>
PersistedSshSettings::Test(domain::SshConfig config) {
  if (!tester_) {
    co_return std::unexpected(application::SshConnectionError{
        .message = "SSH connection testing is unavailable on this platform."});
  }
  co_return co_await tester_->Test(domain::NormalizeSshConfig(std::move(config)));
}

} // namespace linecode::infrastructure
