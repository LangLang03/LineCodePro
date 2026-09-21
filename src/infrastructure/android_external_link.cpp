#include "infrastructure/external_link.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/external_link.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/external-link";
constexpr auto kJavaFactoryClass =
    "cn.lineai.platform.LineCodeExternalLinkModule";

class AndroidExternalLinkService final
    : public application::ExternalLinkService {
public:
  explicit AndroidExternalLinkService(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  void Open(std::string_view url) override {
    if (!channel_.IsOpen() || url.empty()) {
      return;
    }
    channel_.Invoke<std::monostate>(
        "openUrl", std::string(url),
        [](huxerui::PlatformResult<std::monostate>) {});
  }

private:
  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallExternalLink(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::ExternalLinkService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidExternalLinkService>(std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::ExternalLinkService>>(kPlatformModuleName,
                                                         std::move(factory));
  root.Provide<application::ExternalLinkService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::ExternalLinkService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
