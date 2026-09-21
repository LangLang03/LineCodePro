#pragma once

#include <expected>
#include <functional>
#include <memory>
#include <string>

namespace linecode::application {

struct KeepAlivePreferences final {
  bool wake_lock_enabled{true};
  bool foreground_service_enabled{};
  bool silent_audio_enabled{};

  bool operator==(const KeepAlivePreferences &) const = default;
};

struct KeepAliveSystemState final {
  bool notifications_granted{true};
  bool battery_optimization_ignored{};

  bool operator==(const KeepAliveSystemState &) const = default;
};

class BackgroundExecutionLease {
public:
  BackgroundExecutionLease() = default;
  virtual ~BackgroundExecutionLease() = default;

  BackgroundExecutionLease(const BackgroundExecutionLease &) = delete;
  BackgroundExecutionLease &
  operator=(const BackgroundExecutionLease &) = delete;
  BackgroundExecutionLease(BackgroundExecutionLease &&) = delete;
  BackgroundExecutionLease &operator=(BackgroundExecutionLease &&) = delete;
};

class KeepAliveService {
public:
  using PreferencesResult = std::expected<KeepAlivePreferences, std::string>;
  using PreferencesCallback = std::function<void(PreferencesResult)>;
  using SystemStateResult = std::expected<KeepAliveSystemState, std::string>;
  using SystemStateCallback = std::function<void(SystemStateResult)>;

  virtual ~KeepAliveService() = default;

  /// Returns the last preferences received from the persistence bridge.
  [[nodiscard]] virtual KeepAlivePreferences LoadPreferences() const = 0;
  /// Refreshes the cache from platform persistence and completes on the UI
  /// dispatcher that owns the platform channel.
  virtual void RefreshPreferences(PreferencesCallback completion) = 0;
  virtual std::expected<void, std::string>
  SavePreferences(KeepAlivePreferences preferences) = 0;
  virtual void RefreshSystemState(SystemStateCallback completion) = 0;
  virtual void RequestNotificationPermission() = 0;
  virtual void RequestIgnoreBatteryOptimizations() = 0;
  virtual void UpdateStatus(std::string status) = 0;
  [[nodiscard]] virtual std::expected<std::unique_ptr<BackgroundExecutionLease>,
                                      std::string>
  AcquireGenerationLease() = 0;
};

} // namespace linecode::application
