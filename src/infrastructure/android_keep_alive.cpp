#include "infrastructure/android_keep_alive.h"

#if defined(__ANDROID__)

#include <cstddef>
#include <expected>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <variant>

#include <huxerui/android/platform_registry.h>
#include <huxerui/platform_registry.h>

#include "application/keep_alive_policy.h"
#include "application/ports/keep_alive.h"

namespace linecode::infrastructure {
namespace {

using application::EffectiveKeepAliveState;
using application::KeepAlivePreferences;
using application::KeepAliveService;
using application::KeepAliveSystemState;

constexpr auto kPlatformModuleName = "linecode/android-keep-alive";
constexpr auto kJavaFactoryClass =
    "cn.lineai.keepalive.LineCodeKeepAliveModule";

std::string Describe(const huxerui::PlatformError &error) {
  return error.message.empty() ? error.code : error.message;
}

struct PreferencesPayload final {
  KeepAlivePreferences value;

  [[nodiscard]] static huxerui::PlatformPayload
  Encode(const PreferencesPayload &payload) {
    return huxerui::PlatformPayload::Object{
        {"wakeLockEnabled", payload.value.wake_lock_enabled},
        {"foregroundEnabled", payload.value.foreground_service_enabled},
        {"silentAudioEnabled", payload.value.silent_audio_enabled},
    };
  }

  [[nodiscard]] static PreferencesPayload
  Decode(const huxerui::PlatformPayload &payload) {
    const auto &fields = payload.AsObject();
    return {{
        .wake_lock_enabled = fields.at("wakeLockEnabled").AsBoolean(),
        .foreground_service_enabled =
            fields.at("foregroundEnabled").AsBoolean(),
        .silent_audio_enabled = fields.at("silentAudioEnabled").AsBoolean(),
    }};
  }
};

struct SystemStatePayload final {
  KeepAliveSystemState value;

  [[nodiscard]] static SystemStatePayload
  Decode(const huxerui::PlatformPayload &payload) {
    const auto &fields = payload.AsObject();
    return {{
        .notifications_granted = fields.at("notificationsGranted").AsBoolean(),
        .battery_optimization_ignored =
            fields.at("batteryOptimizationIgnored").AsBoolean(),
    }};
  }
};

struct EffectiveStatePayload final {
  EffectiveKeepAliveState value;

  [[nodiscard]] static huxerui::PlatformPayload
  Encode(const EffectiveStatePayload &payload) {
    return huxerui::PlatformPayload::Object{
        {"wakeLockEnabled", payload.value.wake_lock_enabled},
        {"foregroundEnabled", payload.value.foreground_service_enabled},
        {"silentAudioEnabled", payload.value.silent_audio_enabled},
        {"status", payload.value.status},
    };
  }
};

class AndroidKeepAliveState final {
public:
  explicit AndroidKeepAliveState(huxerui::PlatformChannel channel)
      : channel_(std::move(channel)) {}

  [[nodiscard]] bool IsOpen() const noexcept { return channel_.IsOpen(); }

  [[nodiscard]] KeepAlivePreferences Preferences() const {
    const std::scoped_lock lock(mutex_);
    return input_.manual;
  }

  void SetPreferences(KeepAlivePreferences preferences) {
    {
      const std::scoped_lock lock(mutex_);
      input_.manual = preferences;
    }
    Apply();
  }

  void SetStatus(std::string status) {
    {
      const std::scoped_lock lock(mutex_);
      input_.status = std::move(status);
    }
    Apply();
  }

  void AddGenerationLease() {
    {
      const std::scoped_lock lock(mutex_);
      ++input_.active_generation_leases;
    }
    Apply();
  }

  void RemoveGenerationLease() {
    {
      const std::scoped_lock lock(mutex_);
      if (input_.active_generation_leases != 0) {
        --input_.active_generation_leases;
      }
    }
    Apply();
  }

  [[nodiscard]] const huxerui::PlatformChannel &Channel() const noexcept {
    return channel_;
  }

private:
  void Apply() {
    EffectiveKeepAliveState effective;
    {
      const std::scoped_lock lock(mutex_);
      effective = application::ResolveKeepAliveState(input_);
    }
    if (!channel_.IsOpen()) {
      return;
    }
    channel_.Invoke<std::monostate>(
        "applyEffectiveState", EffectiveStatePayload{std::move(effective)},
        [](huxerui::PlatformResult<std::monostate>) {});
  }

  huxerui::PlatformChannel channel_;
  mutable std::mutex mutex_;
  application::KeepAlivePolicyInput input_{};
};

class AndroidBackgroundExecutionLease final
    : public application::BackgroundExecutionLease {
public:
  explicit AndroidBackgroundExecutionLease(
      std::shared_ptr<AndroidKeepAliveState> state)
      : state_(std::move(state)) {
    state_->AddGenerationLease();
  }

  ~AndroidBackgroundExecutionLease() override {
    state_->RemoveGenerationLease();
  }

private:
  std::shared_ptr<AndroidKeepAliveState> state_;
};

class AndroidKeepAliveService final : public KeepAliveService {
public:
  explicit AndroidKeepAliveService(huxerui::PlatformChannel channel)
      : state_(std::make_shared<AndroidKeepAliveState>(std::move(channel))) {}

  [[nodiscard]] KeepAlivePreferences LoadPreferences() const override {
    return state_->Preferences();
  }

  void RefreshPreferences(PreferencesCallback completion) override {
    if (!state_->IsOpen()) {
      completion(std::unexpected("Android keep-alive bridge is closed"));
      return;
    }
    const std::weak_ptr weak = state_;
    state_->Channel().Invoke<PreferencesPayload>(
        "loadPreferences",
        [weak, completion = std::move(completion)](
            huxerui::PlatformResult<PreferencesPayload> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion(std::unexpected(Describe(*error)));
            return;
          }
          auto state = weak.lock();
          if (!state) {
            return;
          }
          auto preferences = std::get<PreferencesPayload>(result).value;
          state->SetPreferences(preferences);
          completion(preferences);
        });
  }

  std::expected<void, std::string>
  SavePreferences(KeepAlivePreferences preferences) override {
    if (!state_->IsOpen()) {
      return std::unexpected("Android keep-alive bridge is closed");
    }
    state_->SetPreferences(preferences);
    state_->Channel().Invoke<std::monostate>(
        "savePreferences", PreferencesPayload{preferences},
        [](huxerui::PlatformResult<std::monostate>) {});
    return {};
  }

  void RefreshSystemState(SystemStateCallback completion) override {
    if (!state_->IsOpen()) {
      completion(std::unexpected("Android keep-alive bridge is closed"));
      return;
    }
    state_->Channel().Invoke<SystemStatePayload>(
        "querySystemState",
        [completion = std::move(completion)](
            huxerui::PlatformResult<SystemStatePayload> result) mutable {
          if (const auto *error =
                  std::get_if<huxerui::PlatformError>(&result)) {
            completion(std::unexpected(Describe(*error)));
            return;
          }
          completion(std::get<SystemStatePayload>(result).value);
        });
  }

  void RequestNotificationPermission() override {
    InvokeCommand("requestNotificationPermission");
  }

  void RequestIgnoreBatteryOptimizations() override {
    InvokeCommand("requestIgnoreBatteryOptimizations");
  }

  void UpdateStatus(std::string status) override {
    state_->SetStatus(std::move(status));
  }

  [[nodiscard]] std::expected<
      std::unique_ptr<application::BackgroundExecutionLease>, std::string>
  AcquireGenerationLease() override {
    if (!state_->IsOpen()) {
      return std::unexpected("Android keep-alive bridge is closed");
    }
    return std::make_unique<AndroidBackgroundExecutionLease>(state_);
  }

private:
  void InvokeCommand(std::string method) {
    if (!state_->IsOpen()) {
      return;
    }
    state_->Channel().Invoke<std::monostate>(
        std::move(method), [](huxerui::PlatformResult<std::monostate>) {});
  }

  std::shared_ptr<AndroidKeepAliveState> state_;
};

} // namespace

void InstallAndroidKeepAlive(huxerui::RootContext &root) {
  huxerui::android::JavaPlatformModuleFactory<
      std::shared_ptr<application::KeepAliveService>>
      factory;
  factory.class_name = kJavaFactoryClass;
  factory.create = [](huxerui::PlatformChannel channel) {
    return std::make_shared<AndroidKeepAliveService>(std::move(channel));
  };
  root.RegisterPlatformModule<std::shared_ptr<application::KeepAliveService>>(
      kPlatformModuleName, std::move(factory));
  root.Provide<application::KeepAliveService>(
      root.OpenPlatformModule<std::shared_ptr<application::KeepAliveService>>(
          kPlatformModuleName));
}

} // namespace linecode::infrastructure

#endif
