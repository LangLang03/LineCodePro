#include "infrastructure/workspace_directory_share.h"

#if defined(__ANDROID__)

#include <memory>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/workspace_directory_share.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/workspace-directory-share";
constexpr auto kJavaFactoryClass =
    "cn.lineai.platform.LineCodeWorkspaceDirectoryShareModule";

class AndroidWorkspaceDirectoryShareService final
    : public application::WorkspaceDirectoryShareService {
public:
  explicit AndroidWorkspaceDirectoryShareService(
      huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  [[nodiscard]] bool OpenHome() override {
    if (!channel_.IsOpen())
      return false;
    channel_.Invoke<std::monostate>(
        "openHome", [](huxerui::PlatformResult<std::monostate>) {});
    return true;
  }

private:
  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallWorkspaceDirectoryShare(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::WorkspaceDirectoryShareService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidWorkspaceDirectoryShareService>(
        std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::WorkspaceDirectoryShareService>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::WorkspaceDirectoryShareService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::WorkspaceDirectoryShareService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
