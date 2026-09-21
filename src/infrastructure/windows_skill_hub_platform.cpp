#include "infrastructure/skill_hub_platform.h"

#if defined(_WIN32)

#include <memory>

#include <huxerui/platform_adapter.h>

#include "application/ports/skill_hub_platform.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/skill-hub-platform";

class WindowsSkillHubPlatformService final
    : public application::SkillHubPlatformService {
public:
  void ReadSessionCookie(CookieCompletion completion) override {
    completion({.error =
                    "SkillHub WebView session access is unavailable on Windows"});
  }

  void ClearSessionCookies() override {}

  void ReadLegacyMarkdownTextScale(ReadingScaleCompletion completion) override {
    completion(std::nullopt);
  }

};

} // namespace

void InstallSkillHubPlatform(huxerui::RootContext &root) {
  root.RegisterPlatformModule<
      std::shared_ptr<application::SkillHubPlatformService>>(
      kPlatformModuleName,
      [](huxerui::PlatformAdapter &)
          -> std::shared_ptr<application::SkillHubPlatformService> {
        return std::make_shared<WindowsSkillHubPlatformService>();
      });
  root.Provide<application::SkillHubPlatformService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::SkillHubPlatformService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
