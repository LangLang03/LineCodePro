#pragma once

#include <memory>

#include <huxerui/resource.h>
#include <huxerui/view.h>

namespace linecode::application {
class SshSettingsService;
}

namespace linecode::presentation {

struct SshSettingsPresentation final {
  huxerui::StringVariant title{"SSH connection"};
  huxerui::StringVariant server_section{"Remote Linux / server"};
  huxerui::StringVariant server_description{
      "Connect a remote server via SSH and use it as the workspace"};
  huxerui::StringVariant termux{"Termux integration"};
  huxerui::StringVariant form_title{"Connection config"};
  huxerui::StringVariant host{"Host"};
  huxerui::StringVariant host_hint{"Server IP or domain"};
  huxerui::StringVariant port{"Port"};
  huxerui::StringVariant port_hint{"22 or 8022"};
  huxerui::StringVariant username{"Username"};
  huxerui::StringVariant username_hint{"Username"};
  huxerui::StringVariant password{"Password (optional)"};
  huxerui::StringVariant password_hint{"Enter when using password login"};
  huxerui::StringVariant private_key{"Private key (no-password login)"};
  huxerui::StringVariant private_key_hint{
      "-----BEGIN OPENSSH PRIVATE KEY-----"};
  huxerui::StringVariant passphrase{"Key passphrase (optional)"};
  huxerui::StringVariant passphrase_hint{"Private key passphrase"};
  huxerui::StringVariant save{"Save config"};
  huxerui::StringVariant test{"Test connection"};
  huxerui::StringVariant saved_title{"Saved"};
  huxerui::StringVariant saved_message{"SSH config saved."};
  huxerui::StringVariant testing_title{"Connecting"};
  huxerui::StringVariant testing_message{"Testing SSH connection..."};
  huxerui::StringVariant success_title{"Connected"};
  huxerui::StringVariant success_message{"Connection OK"};
  huxerui::StringVariant failed_title{"Connection failed"};
};

[[huxerui::composable]] huxerui::View SshSettingsScreen(
    std::shared_ptr<application::SshSettingsService> service,
    bool termux_available,
    SshSettingsPresentation presentation = {});

} // namespace linecode::presentation
