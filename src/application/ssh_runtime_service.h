#pragma once

#include <memory>

#include "application/ports/ssh_transport.h"
#include "application/ssh_settings_service.h"

namespace linecode::application {

class SshExecutionService {
public:
  virtual ~SshExecutionService() = default;

  [[nodiscard]] virtual huxerui::Task<SshResult<SshCommandOutput>>
  Execute(domain::SshConfig config, SshCommandRequest request) = 0;
};

// Async application boundary for the blocking SSH protocol adapter.  Worker
// cancellation is propagated into socket/channel waits.
class SshRuntimeService final : public SshExecutionService,
                                public SshConnectionTester {
public:
  explicit SshRuntimeService(std::shared_ptr<SshTransport> transport);

  [[nodiscard]] huxerui::Task<SshResult<SshCommandOutput>>
  Execute(domain::SshConfig config, SshCommandRequest request) override;
  [[nodiscard]] huxerui::Task<SshConnectionResult<std::string>>
  Test(domain::SshConfig config) override;

private:
  std::shared_ptr<SshTransport> transport_;
  huxerui::WorkerSequence operations_;
};

} // namespace linecode::application
