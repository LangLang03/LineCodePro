#include "infrastructure/error_log_platform.h"

#if defined(__ANDROID__)

#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/android/jni.h>
#include <huxerui/android/platform_registry.h>
#include <huxerui/clipboard.h>
#include <huxerui/file.h>
#include <huxerui/platform_adapter.h>

#include "application/error_log_policy.h"
#include "application/ports/error_log_store.h"

namespace linecode::infrastructure {
namespace {

constexpr std::string_view kPlatformModuleName =
    "linecode/error-log-platform-actions";
constexpr std::string_view kViewDirectoryName = "error_log_views";

bool TakeJniException(JNIEnv *environment) noexcept {
  if (!environment->ExceptionCheck()) {
    return false;
  }
  environment->ExceptionClear();
  return true;
}

bool Failed(JNIEnv *environment, const void *value) noexcept {
  const bool exception = TakeJniException(environment);
  return value == nullptr || exception;
}

std::string CacheDirectoryPath(JNIEnv *environment, jobject context) {
  huxerui::android::LocalRef<jclass> context_class{
      environment, environment->GetObjectClass(context)};
  if (Failed(environment, context_class.Get())) {
    return {};
  }
  const jmethodID get_cache_directory = environment->GetMethodID(
      context_class.Get(), "getCacheDir", "()Ljava/io/File;");
  if (Failed(environment, get_cache_directory)) {
    return {};
  }
  huxerui::android::LocalRef<jobject> cache_directory{
      environment,
      environment->CallObjectMethod(context, get_cache_directory)};
  if (Failed(environment, cache_directory.Get())) {
    return {};
  }
  huxerui::android::LocalRef<jclass> file_class{
      environment, environment->GetObjectClass(cache_directory.Get())};
  if (Failed(environment, file_class.Get())) {
    return {};
  }
  const jmethodID get_absolute_path = environment->GetMethodID(
      file_class.Get(), "getAbsolutePath", "()Ljava/lang/String;");
  if (Failed(environment, get_absolute_path)) {
    return {};
  }
  huxerui::android::LocalRef<jstring> path{
      environment, static_cast<jstring>(environment->CallObjectMethod(
                       cache_directory.Get(), get_absolute_path))};
  if (Failed(environment, path.Get())) {
    return {};
  }
  return huxerui::android::JavaStringToUtf8(environment, path.Get());
}

std::string PackageName(JNIEnv *environment, jobject context) {
  huxerui::android::LocalRef<jclass> context_class{
      environment, environment->GetObjectClass(context)};
  if (Failed(environment, context_class.Get())) {
    return {};
  }
  const jmethodID get_package_name = environment->GetMethodID(
      context_class.Get(), "getPackageName", "()Ljava/lang/String;");
  if (Failed(environment, get_package_name)) {
    return {};
  }
  huxerui::android::LocalRef<jstring> package_name{
      environment, static_cast<jstring>(
                       environment->CallObjectMethod(context,
                                                     get_package_name))};
  if (Failed(environment, package_name.Get())) {
    return {};
  }
  return huxerui::android::JavaStringToUtf8(environment,
                                            package_name.Get());
}

bool StartTextViewer(huxerui::PlatformAdapter &adapter,
                     std::string_view file_name,
                     std::string_view chooser_title) {
  const auto platform = huxerui::android::GetPlatformEnv(adapter);
  JNIEnv *environment = platform.jni;
  if (environment == nullptr || platform.context == nullptr) {
    return false;
  }

  const std::string package_name =
      PackageName(environment, platform.context);
  if (package_name.empty()) {
    return false;
  }
  const auto java_scheme =
      huxerui::android::Utf8ToJavaString(environment, "content");
  const auto java_authority = huxerui::android::Utf8ToJavaString(
      environment, package_name + ".errorlogs");
  const auto java_name =
      huxerui::android::Utf8ToJavaString(environment, file_name);
  if (!java_scheme || !java_authority || !java_name ||
      TakeJniException(environment)) {
    return false;
  }

  huxerui::android::LocalRef<jclass> builder_class{
      environment, environment->FindClass("android/net/Uri$Builder")};
  if (Failed(environment, builder_class.Get())) {
    return false;
  }
  const jmethodID builder_constructor =
      environment->GetMethodID(builder_class.Get(), "<init>", "()V");
  const jmethodID scheme = environment->GetMethodID(
      builder_class.Get(), "scheme",
      "(Ljava/lang/String;)Landroid/net/Uri$Builder;");
  const jmethodID authority = environment->GetMethodID(
      builder_class.Get(), "authority",
      "(Ljava/lang/String;)Landroid/net/Uri$Builder;");
  const jmethodID append_path = environment->GetMethodID(
      builder_class.Get(), "appendPath",
      "(Ljava/lang/String;)Landroid/net/Uri$Builder;");
  const jmethodID build = environment->GetMethodID(
      builder_class.Get(), "build", "()Landroid/net/Uri;");
  if (Failed(environment, builder_constructor) || Failed(environment, scheme) ||
      Failed(environment, authority) || Failed(environment, append_path) ||
      Failed(environment, build)) {
    return false;
  }
  huxerui::android::LocalRef<jobject> builder{
      environment,
      environment->NewObject(builder_class.Get(), builder_constructor)};
  if (Failed(environment, builder.Get())) {
    return false;
  }
  const auto call_builder = [&](jmethodID method, jobject argument) {
    huxerui::android::LocalRef<jobject> returned{
        environment, environment->CallObjectMethod(builder.Get(), method,
                                                   argument)};
    return !Failed(environment, returned.Get());
  };
  if (!call_builder(scheme, java_scheme.Get()) ||
      !call_builder(authority, java_authority.Get()) ||
      !call_builder(append_path, java_name.Get())) {
    return false;
  }
  huxerui::android::LocalRef<jobject> uri{
      environment, environment->CallObjectMethod(builder.Get(), build)};
  if (Failed(environment, uri.Get())) {
    return false;
  }

  huxerui::android::LocalRef<jclass> intent_class{
      environment, environment->FindClass("android/content/Intent")};
  if (Failed(environment, intent_class.Get())) {
    return false;
  }
  const jmethodID constructor =
      environment->GetMethodID(intent_class.Get(), "<init>", "()V");
  const jmethodID set_action = environment->GetMethodID(
      intent_class.Get(), "setAction",
      "(Ljava/lang/String;)Landroid/content/Intent;");
  const jmethodID set_data_and_type = environment->GetMethodID(
      intent_class.Get(), "setDataAndType",
      "(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;");
  const jmethodID add_flags = environment->GetMethodID(
      intent_class.Get(), "addFlags", "(I)Landroid/content/Intent;");
  const jmethodID create_chooser = environment->GetStaticMethodID(
      intent_class.Get(), "createChooser",
      "(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;");
  if (Failed(environment, constructor) || Failed(environment, set_action) ||
      Failed(environment, set_data_and_type) ||
      Failed(environment, add_flags) || Failed(environment, create_chooser)) {
    return false;
  }
  huxerui::android::LocalRef<jobject> intent{
      environment,
      environment->NewObject(intent_class.Get(), constructor)};
  const auto action = huxerui::android::Utf8ToJavaString(
      environment, "android.intent.action.VIEW");
  const auto mime =
      huxerui::android::Utf8ToJavaString(environment, "text/plain");
  const auto chooser =
      huxerui::android::Utf8ToJavaString(environment, chooser_title);
  if (!intent || !action || !mime || !chooser ||
      TakeJniException(environment)) {
    return false;
  }
  const auto call_intent = [&](jmethodID method, auto... arguments) {
    huxerui::android::LocalRef<jobject> returned{
        environment, environment->CallObjectMethod(intent.Get(), method,
                                                   arguments...)};
    return !Failed(environment, returned.Get());
  };
  // FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK. No write grant is
  // issued and the provider independently rejects writable modes.
  if (!call_intent(set_action, action.Get()) ||
      !call_intent(set_data_and_type, uri.Get(), mime.Get()) ||
      !call_intent(add_flags, 0x10000001)) {
    return false;
  }
  huxerui::android::LocalRef<jobject> chooser_intent{
      environment,
      environment->CallStaticObjectMethod(intent_class.Get(), create_chooser,
                                          intent.Get(), chooser.Get())};
  if (Failed(environment, chooser_intent.Get())) {
    return false;
  }
  huxerui::android::LocalRef<jobject> chooser_with_flags{
      environment, environment->CallObjectMethod(
                       chooser_intent.Get(), add_flags, 0x10000001)};
  if (Failed(environment, chooser_with_flags.Get())) {
    return false;
  }

  huxerui::android::LocalRef<jclass> context_class{
      environment, environment->GetObjectClass(platform.context)};
  if (Failed(environment, context_class.Get())) {
    return false;
  }
  const jmethodID start_activity = environment->GetMethodID(
      context_class.Get(), "startActivity", "(Landroid/content/Intent;)V");
  if (Failed(environment, start_activity)) {
    return false;
  }
  environment->CallVoidMethod(platform.context, start_activity,
                              chooser_intent.Get());
  return !TakeJniException(environment);
}

class AndroidErrorLogPlatformActions final
    : public application::ErrorLogPlatformActions {
public:
  explicit AndroidErrorLogPlatformActions(huxerui::PlatformAdapter &adapter)
      : adapter_(&adapter) {}

  bool CopyText(std::string_view text) override {
    huxerui::PlatformClipboard *clipboard = adapter_->Clipboard();
    return clipboard != nullptr && clipboard->WriteText(text);
  }

  huxerui::Task<bool> OpenText(std::string title, std::string text,
                              std::string chooser_title) override {
    const auto platform = huxerui::android::GetPlatformEnv(*adapter_);
    const std::string cache_path =
        CacheDirectoryPath(platform.jni, platform.context);
    if (cache_path.empty()) {
      co_return false;
    }
    const std::string file_name =
        application::SafeTemporaryErrorLogFileName(title);
    huxerui::File view_directory(cache_path);
    view_directory = view_directory.Child(kViewDirectoryName);
    if (!co_await view_directory.CreateDirectoriesAsync()) {
      co_return false;
    }
    if (!co_await view_directory.Child(file_name).WriteStringAsync(
            std::move(text))) {
      co_return false;
    }
    co_return StartTextViewer(*adapter_, file_name, chooser_title);
  }

private:
  // The root-owned Module cannot outlive its owning PlatformAdapter.
  huxerui::PlatformAdapter *adapter_;
};

} // namespace

void InstallErrorLogPlatformActions(huxerui::RootContext &root) {
  huxerui::android::PlatformModuleFactory<
      std::shared_ptr<application::ErrorLogPlatformActions>>
      factory;
  factory.create = [](huxerui::PlatformAdapter &adapter, JNIEnv *, jobject) {
    return std::make_shared<AndroidErrorLogPlatformActions>(adapter);
  };
  root.RegisterPlatformModule<
      std::shared_ptr<application::ErrorLogPlatformActions>>(
      std::string(kPlatformModuleName), std::move(factory));
  root.Provide<application::ErrorLogPlatformActions>(
      root.OpenPlatformModule<
          std::shared_ptr<application::ErrorLogPlatformActions>>(
          std::string(kPlatformModuleName)));
}

} // namespace linecode::infrastructure

#endif
