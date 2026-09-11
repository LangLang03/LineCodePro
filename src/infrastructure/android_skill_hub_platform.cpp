#include "infrastructure/skill_hub_platform.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/skill_hub_platform.h"
#include "infrastructure/skill_hub_codec.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/skill-hub-platform";
constexpr auto kJavaFactoryClass =
    "cn.lineai.skillhub.LineCodeSkillHubPlatformModule";

class AndroidSkillHubPlatformService final
    : public application::SkillHubPlatformService {
public:
  explicit AndroidSkillHubPlatformService(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  void ReadSessionCookie(CookieCompletion completion) override {
    if (!channel_.IsOpen()) {
      completion({.error = "Android SkillHub bridge is closed"});
      return;
    }
    channel_.Invoke<std::string>(
        "readSessionCookie", std::monostate{},
        [completion = std::move(completion)](
            huxerui::PlatformResult<std::string> result) mutable {
          if (const auto *error = std::get_if<huxerui::PlatformError>(&result)) {
            completion({.error = error->message.empty() ? error->code
                                                        : error->message});
            return;
          }
          auto validated =
              ValidateSkillHubCookie(std::get<std::string>(std::move(result)));
          if (!validated) {
            completion({.error = validated.error().message});
            return;
          }
          completion({.cookie = std::move(*validated)});
        });
  }

  void ClearSessionCookies() override {
    if (!channel_.IsOpen())
      return;
    channel_.Invoke<std::monostate>(
        "clearSessionCookies", std::monostate{},
        [](huxerui::PlatformResult<std::monostate>) {});
  }

private:
  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallSkillHubPlatform(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::SkillHubPlatformService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidSkillHubPlatformService>(
        std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::SkillHubPlatformService>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::SkillHubPlatformService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::SkillHubPlatformService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
