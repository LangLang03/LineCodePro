#include "infrastructure/unavailable_termux_integration.h"

#include <memory>
#include <string>
#include <utility>

#include "application/ports/termux_integration.h"

namespace linecode::infrastructure {
namespace {

domain::TermuxError UnsupportedError() {
  return {
      .code = domain::TermuxErrorCode::unsupported,
      .detail = "Termux integration is unavailable on this platform",
  };
}

class UnavailableTermuxIntegrationGateway final
    : public application::TermuxIntegrationGateway {
public:
  [[nodiscard]] application::TermuxCapabilities
  Capabilities() const noexcept override {
    return {};
  }

  void QueryState(StateCompletion completion) override {
    completion(std::unexpected(UnsupportedError()));
  }

  void RequestRunCommandPermission(VoidCompletion completion) override {
    completion(std::unexpected(UnsupportedError()));
  }

  void OpenTermux(VoidCompletion completion) override {
    completion(std::unexpected(UnsupportedError()));
  }

  void SetupOpenSsh(std::string, std::chrono::milliseconds,
                    SetupCompletion completion) override {
    completion(std::unexpected(UnsupportedError()));
  }
};

} // namespace

void InstallUnavailableTermuxIntegration(huxerui::RootContext &root) {
  root.Provide<application::TermuxIntegrationGateway>(
      std::make_shared<UnavailableTermuxIntegrationGateway>());
}

} // namespace linecode::infrastructure
