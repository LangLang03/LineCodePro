#include "infrastructure/error_log_platform.h"

#if defined(__ANDROID__)

#include <memory>
#include <string_view>

#include <huxerui/android/jni.h>
#include <huxerui/android/platform_registry.h>
#include <huxerui/clipboard.h>
#include <huxerui/platform_adapter.h>

#include "application/ports/error_log_store.h"

namespace linecode::infrastructure {
namespace {

constexpr std::string_view kPlatformModuleName =
    "linecode/error-log-platform-actions";

bool ClearPendingException(JNIEnv *environment) noexcept {
  if (!environment->ExceptionCheck()) {
    return false;
  }
  environment->ExceptionClear();
  return true;
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

  bool OpenText(std::string_view title, std::string_view text,
                std::string_view chooser_title) override {
    const auto platform = huxerui::android::GetPlatformEnv(*adapter_);
    JNIEnv *environment = platform.jni;
    if (environment == nullptr || platform.context == nullptr) {
      return false;
    }

    huxerui::android::LocalRef<jclass> intent_class{
        environment, environment->FindClass("android/content/Intent")};
    if (!intent_class || ClearPendingException(environment)) {
      return false;
    }
    const jmethodID constructor =
        environment->GetMethodID(intent_class.Get(), "<init>", "()V");
    const jmethodID set_action = environment->GetMethodID(
        intent_class.Get(), "setAction",
        "(Ljava/lang/String;)Landroid/content/Intent;");
    const jmethodID set_type = environment->GetMethodID(
        intent_class.Get(), "setType",
        "(Ljava/lang/String;)Landroid/content/Intent;");
    const jmethodID put_extra = environment->GetMethodID(
        intent_class.Get(), "putExtra",
        "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;");
    const jmethodID add_flags = environment->GetMethodID(
        intent_class.Get(), "addFlags", "(I)Landroid/content/Intent;");
    const jmethodID create_chooser = environment->GetStaticMethodID(
        intent_class.Get(), "createChooser",
        "(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;");
    if (constructor == nullptr || set_action == nullptr || set_type == nullptr ||
        put_extra == nullptr || add_flags == nullptr ||
        create_chooser == nullptr || ClearPendingException(environment)) {
      return false;
    }

    huxerui::android::LocalRef<jobject> intent{
        environment,
        environment->NewObject(intent_class.Get(), constructor)};
    const auto action = huxerui::android::Utf8ToJavaString(
        environment, "android.intent.action.SEND");
    const auto mime =
        huxerui::android::Utf8ToJavaString(environment, "text/plain");
    const auto extra_text = huxerui::android::Utf8ToJavaString(
        environment, "android.intent.extra.TEXT");
    const auto extra_title = huxerui::android::Utf8ToJavaString(
        environment, "android.intent.extra.TITLE");
    const auto java_text =
        huxerui::android::Utf8ToJavaString(environment, text);
    const auto java_title =
        huxerui::android::Utf8ToJavaString(environment, title);
    const auto java_chooser =
        huxerui::android::Utf8ToJavaString(environment, chooser_title);
    if (!intent || !action || !mime || !extra_text || !extra_title ||
        !java_text || !java_title || !java_chooser ||
        ClearPendingException(environment)) {
      return false;
    }

    auto call_intent = [&](jmethodID method, auto... arguments) {
      huxerui::android::LocalRef<jobject> ignored{
          environment, environment->CallObjectMethod(intent.Get(), method,
                                                     arguments...)};
      return static_cast<bool>(ignored) && !ClearPendingException(environment);
    };
    if (!call_intent(set_action, action.Get()) ||
        !call_intent(set_type, mime.Get()) ||
        !call_intent(put_extra, extra_text.Get(), java_text.Get()) ||
        !call_intent(put_extra, extra_title.Get(), java_title.Get())) {
      return false;
    }

    huxerui::android::LocalRef<jobject> chooser{
        environment,
        environment->CallStaticObjectMethod(intent_class.Get(), create_chooser,
                                            intent.Get(), java_chooser.Get())};
    if (!chooser || ClearPendingException(environment)) {
      return false;
    }
    // Root-owned Context is not guaranteed to be an Activity.
    huxerui::android::LocalRef<jobject> flagged{
        environment, environment->CallObjectMethod(
                         chooser.Get(), add_flags, 0x10000000)};
    if (!flagged || ClearPendingException(environment)) {
      return false;
    }

    huxerui::android::LocalRef<jclass> context_class{
        environment, environment->GetObjectClass(platform.context)};
    if (!context_class || ClearPendingException(environment)) {
      return false;
    }
    const jmethodID start_activity = environment->GetMethodID(
        context_class.Get(), "startActivity",
        "(Landroid/content/Intent;)V");
    if (start_activity == nullptr || ClearPendingException(environment)) {
      return false;
    }
    environment->CallVoidMethod(platform.context, start_activity,
                                chooser.Get());
    return !ClearPendingException(environment);
  }

private:
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
