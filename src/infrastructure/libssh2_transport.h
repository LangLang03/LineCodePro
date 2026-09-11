#pragma once

#include <memory>

#include "application/ports/ssh_transport.h"

namespace linecode::infrastructure {

class Libssh2Transport final : public application::SshTransport {
public:
  explicit Libssh2Transport(
      std::shared_ptr<application::SshKnownHostsStore> known_hosts,
      application::SshHostKeyPolicy host_key_policy =
          application::SshHostKeyPolicy::trust_on_first_use);

  [[nodiscard]] application::SshResult<
      std::unique_ptr<application::SshSession>>
  Connect(const domain::SshConfig &config, std::chrono::milliseconds timeout,
          std::stop_token stop) override;

private:
  std::shared_ptr<application::SshKnownHostsStore> known_hosts_;
  application::SshHostKeyPolicy host_key_policy_;
};

} // namespace linecode::infrastructure
