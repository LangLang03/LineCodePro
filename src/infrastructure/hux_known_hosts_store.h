#pragma once

#include <mutex>

#include <huxerui/file.h>

#include "application/ports/ssh_transport.h"

namespace linecode::infrastructure {

class HuxKnownHostsStore final : public application::SshKnownHostsStore {
public:
  explicit HuxKnownHostsStore(huxerui::File file);

  [[nodiscard]] application::SshResult<std::string> Load() override;
  [[nodiscard]] application::SshResult<void> Replace(std::string value) override;

private:
  huxerui::File file_;
  std::mutex mutex_;
};

} // namespace linecode::infrastructure
