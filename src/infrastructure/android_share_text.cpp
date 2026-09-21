#include "infrastructure/share_text.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/share_text.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/share-text";
constexpr auto kJavaFactoryClass = "cn.lineai.platform.LineCodeShareTextModule";

class AndroidShareTextService final : public application::ShareTextService {
public:
  explicit AndroidShareTextService(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  [[nodiscard]] bool Share(const std::string_view text) override {
    if (!channel_.IsOpen() || text.empty())
      return false;
    channel_.Invoke<std::monostate>(
        "share", std::string{text},
        [](huxerui::PlatformResult<std::monostate>) {});
    return true;
  }

private:
  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallShareText(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::ShareTextService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidShareTextService>(std::move(channel));
  };
  root.RegisterPlatformModule<std::shared_ptr<application::ShareTextService>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::ShareTextService>(
      root.OpenPlatformModule<std::shared_ptr<application::ShareTextService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
