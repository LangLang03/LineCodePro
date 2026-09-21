#include "application/ssh_runtime_service.h"

#include <algorithm>
#include <chrono>
#include <stdexcept>
#include <utility>

#include <huxerui/task.h>

namespace linecode::application {
namespace {

using namespace std::chrono_literals;

std::chrono::milliseconds BoundedTimeout(std::chrono::milliseconds value) {
  return std::clamp(value, std::chrono::milliseconds{1'000},
                    std::chrono::milliseconds{300'000});
}

} // namespace

SshRuntimeService::SshRuntimeService(std::shared_ptr<SshTransport> transport)
    : transport_(std::move(transport)) {
  if (!transport_)
    throw std::invalid_argument("SSH transport must not be empty");
}

huxerui::Task<SshResult<SshCommandOutput>>
SshRuntimeService::Execute(domain::SshConfig config,
                           SshCommandRequest request) {
  config = domain::NormalizeSshConfig(std::move(config));
  request.timeout = BoundedTimeout(request.timeout);
  if (!config.IsConfigured()) {
    co_return std::unexpected(SshError{
        .code = SshErrorCode::not_configured,
        .message = "SSH is not configured",
    });
  }
  if (request.command.empty()) {
    co_return std::unexpected(SshError{
        .code = SshErrorCode::invalid_argument,
        .message = "SSH command is empty",
    });
  }

  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config),
       request = std::move(request)](std::stop_token stop) mutable {
        auto session = transport->Connect(config, request.timeout, stop);
        if (!session)
          return SshResult<SshCommandOutput>{
              std::unexpected(std::move(session.error()))};
        return (*session)->Execute(request, stop);
      });
}

huxerui::Task<SshConnectionResult<std::string>>
SshRuntimeService::Test(domain::SshConfig config) {
  auto tested = co_await Execute(
      std::move(config),
      SshCommandRequest{
          .command = "printf 'LineAI SSH OK\\n' && whoami && pwd",
          .working_directory = {},
          .timeout = 10s,
          .maximum_output_bytes = 256U * 1024U,
      });
  if (!tested) {
    co_return std::unexpected(
        SshConnectionError{.message = std::move(tested.error().message)});
  }
  if (tested->exit_status != 0) {
    std::string message{"SSH connection test failed with exit status "};
    message += std::to_string(tested->exit_status);
    if (!tested->standard_error.empty()) {
      message += ": ";
      message += tested->standard_error;
    }
    co_return std::unexpected(
        SshConnectionError{.message = std::move(message)});
  }
  std::string output = std::move(tested->standard_output);
  if (!tested->standard_error.empty()) {
    if (!output.empty() && output.back() != '\n')
      output.push_back('\n');
    output += tested->standard_error;
  }
  co_return output.empty() ? std::string{"LineAI SSH OK"}
                           : std::move(output);
}

} // namespace linecode::application
