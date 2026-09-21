#include "infrastructure/android_storage_permission.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/storage_permission.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/android-storage-permission";
constexpr auto kJavaFactoryClass =
    "cn.lineai.storage.LineCodeStoragePermissionModule";

class AndroidStoragePermissionService final
    : public application::StoragePermissionService {
public:
  explicit AndroidStoragePermissionService(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  void Query(Completion completion) override {
    Invoke("query", std::move(completion));
  }

  void OpenManagementSettings(Completion completion) override {
    Invoke("openManagementSettings", std::move(completion));
  }

private:
  void Invoke(std::string method, Completion completion) {
    if (!channel_.IsOpen()) {
      completion({.granted = false,
                  .error = "Android storage permission bridge is closed"});
      return;
    }
    channel_.Invoke<bool>(
        std::move(method), std::monostate{},
        [completion = std::move(completion)](
            huxerui::PlatformResult<bool> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion({.granted = false,
                        .error = error->message.empty() ? error->code
                                                        : error->message});
            return;
          }
          completion({.granted = std::get<bool>(result), .error = {}});
        });
  }

  huxerui::PlatformChannel channel_;
};

} // namespace

void InstallAndroidStoragePermission(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::StoragePermissionService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidStoragePermissionService>(
        std::move(channel));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::StoragePermissionService>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::StoragePermissionService>(
      root.OpenPlatformModule<
          std::shared_ptr<application::StoragePermissionService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
