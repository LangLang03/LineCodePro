#include "infrastructure/android_termux_integration.h"

#if defined(__ANDROID__)

#include <chrono>
#include <iterator>
#include <memory>
#include <ranges>
#include <string>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/termux_integration.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/termux-integration";
constexpr auto kJavaFactoryClass =
    "cn.lineai.termux.LineCodeTermuxIntegrationModule";

domain::TermuxErrorCode ErrorCode(std::string_view code) {
  struct Mapping final {
    std::string_view platform;
    domain::TermuxErrorCode domain;
  };
  static constexpr Mapping mappings[]{
      {"linecode/termux/not-installed",
       domain::TermuxErrorCode::not_installed},
      {"linecode/termux/permission-denied",
       domain::TermuxErrorCode::permission_denied},
      {"linecode/termux/no-result", domain::TermuxErrorCode::no_result},
      {"linecode/termux/command-failed",
       domain::TermuxErrorCode::command_failed},
      {"linecode/termux/timeout", domain::TermuxErrorCode::timeout},
      {"linecode/termux/bridge-closed",
       domain::TermuxErrorCode::bridge_closed},
  };
  const auto found = std::ranges::find(mappings, code, &Mapping::platform);
  return found == std::end(mappings) ? domain::TermuxErrorCode::platform_error
                                     : found->domain;
}

domain::TermuxError ToError(const huxerui::PlatformError &error) {
  return domain::TermuxError{
      .code = ErrorCode(error.code),
      .detail = error.message.empty() ? error.code : error.message,
  };
}

struct PlatformStatePayload final {
  domain::TermuxPlatformState value;

  static PlatformStatePayload Decode(const huxerui::PlatformPayload &payload) {
    const auto &fields = payload.AsObject();
    return {{
        .installed = fields.at("installed").AsBoolean(),
        .run_command_permission_granted =
            fields.at("permissionGranted").AsBoolean(),
    }};
  }
};

struct SetupPayload final {
  std::string script;
  std::chrono::milliseconds timeout;

  static huxerui::PlatformPayload Encode(const SetupPayload &payload) {
    return huxerui::PlatformPayload::Object{
        {"script", payload.script},
        {"timeoutMs", payload.timeout.count()},
    };
  }
};

class AndroidTermuxIntegrationGateway final
    : public application::TermuxIntegrationGateway {
public:
  explicit AndroidTermuxIntegrationGateway(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  [[nodiscard]] application::TermuxCapabilities
  Capabilities() const noexcept override {
    return {.integration = channel_.IsOpen()};
  }

  void QueryState(StateCompletion completion) override {
    Invoke<PlatformStatePayload>(
        "queryState", std::monostate{},
        [completion = std::move(completion)](
            domain::TermuxResult<PlatformStatePayload> result) mutable {
          if (!result) {
            completion(std::unexpected(std::move(result.error())));
            return;
          }
          completion(std::move(result->value));
        });
  }

  void RequestRunCommandPermission(VoidCompletion completion) override {
    Invoke<std::monostate>("requestRunCommandPermission", std::monostate{},
                           std::move(completion));
  }

  void OpenTermux(VoidCompletion completion) override {
    Invoke<std::monostate>("openTermux", std::monostate{},
                           std::move(completion));
  }

  void SetupOpenSsh(std::string script, std::chrono::milliseconds timeout,
                    SetupCompletion completion) override {
    Invoke<std::string>("setupOpenSsh",
                        SetupPayload{.script = std::move(script),
                                     .timeout = timeout},
                        std::move(completion));
  }

private:
  template <class Result, class Arguments, class Completion>
  void Invoke(std::string method, Arguments arguments,
              Completion completion) {
    if (!channel_.IsOpen()) {
      completion(std::unexpected(domain::TermuxError{
          .code = domain::TermuxErrorCode::bridge_closed,
          .detail = "Android Termux bridge is closed",
      }));
      return;
    }
    channel_.Invoke<Result>(
        std::move(method), arguments,
        [completion = std::move(completion)](
            huxerui::PlatformResult<Result> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion(std::unexpected(ToError(*error)));
            return;
          }
          if constexpr (std::same_as<Result, std::monostate>) {
            completion(domain::TermuxResult<void>{});
          } else {
            completion(std::get<Result>(std::move(result)));
          }
        });
  }

  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallAndroidTermuxIntegration(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::TermuxIntegrationGateway>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidTermuxIntegrationGateway>(
        std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::TermuxIntegrationGateway>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::TermuxIntegrationGateway>(
      root.OpenPlatformModule<
          std::shared_ptr<application::TermuxIntegrationGateway>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
