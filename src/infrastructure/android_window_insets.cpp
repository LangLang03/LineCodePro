#include "infrastructure/android_window_insets.h"

#if defined(__ANDROID__)

#include <array>
#include <memory>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/ports/window_insets.h"

namespace linecode::infrastructure {
namespace {

constexpr auto kPlatformModuleName = "linecode/android-window-insets";

class AndroidWindowInsetsProvider final
    : public application::WindowInsetsProvider {
public:
  explicit AndroidWindowInsetsProvider(application::WindowInsets insets)
      : insets_(insets) {}

  [[nodiscard]] application::WindowInsets Current() const noexcept override {
    return insets_;
  }

private:
  application::WindowInsets insets_;
};

application::WindowInsets ReadWindowInsets(JNIEnv* environment,
                                           jobject context) {
  if (environment == nullptr || context == nullptr) {
    return {};
  }

  const jclass context_class = environment->GetObjectClass(context);
  if (context_class == nullptr) {
    return {};
  }

  const jmethodID method = environment->GetMethodID(
      context_class, "lineCodeWindowInsetsDp", "()[F");
  if (method == nullptr || environment->ExceptionCheck()) {
    environment->ExceptionClear();
    environment->DeleteLocalRef(context_class);
    return {};
  }

  auto values = static_cast<jfloatArray>(
      environment->CallObjectMethod(context, method));
  if (environment->ExceptionCheck() || values == nullptr) {
    environment->ExceptionClear();
    environment->DeleteLocalRef(context_class);
    return {};
  }

  std::array<jfloat, 4> raw{};
  if (environment->GetArrayLength(values) >=
      static_cast<jsize>(raw.size())) {
    environment->GetFloatArrayRegion(values, 0,
                                     static_cast<jsize>(raw.size()),
                                     raw.data());
  }
  environment->DeleteLocalRef(values);
  environment->DeleteLocalRef(context_class);

  return application::WindowInsets{
      .top = raw[0],
      .right = raw[1],
      .bottom = raw[2],
      .left = raw[3],
  };
}

} // namespace

void InstallAndroidWindowInsets(huxerui::RootContext& root) {
  huxerui::android::PlatformModuleFactory<
      std::shared_ptr<application::WindowInsetsProvider>>
      factory;
  factory.create = [](huxerui::PlatformAdapter&, JNIEnv* environment,
                      jobject context) {
    return std::make_shared<AndroidWindowInsetsProvider>(
        ReadWindowInsets(environment, context));
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::WindowInsetsProvider>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::WindowInsetsProvider>(
      root.OpenPlatformModule<
          std::shared_ptr<application::WindowInsetsProvider>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
