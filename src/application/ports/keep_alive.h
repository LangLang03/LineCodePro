#pragma once

#include <expected>
#include <memory>
#include <string>

namespace linecode::application {

struct KeepAlivePreferences final {
  bool wake_lock_enabled{true};
  bool foreground_service_enabled{};
  bool silent_audio_enabled{};

  bool operator==(const KeepAlivePreferences&) const = default;
};

class BackgroundExecutionLease {
public:
  BackgroundExecutionLease() = default;
  virtual ~BackgroundExecutionLease() = default;

  BackgroundExecutionLease(const BackgroundExecutionLease&) = delete;
  BackgroundExecutionLease& operator=(const BackgroundExecutionLease&) = delete;
  BackgroundExecutionLease(BackgroundExecutionLease&&) = delete;
  BackgroundExecutionLease& operator=(BackgroundExecutionLease&&) = delete;
};

class KeepAliveService {
public:
  virtual ~KeepAliveService() = default;

  [[nodiscard]] virtual KeepAlivePreferences LoadPreferences() const = 0;
  virtual std::expected<void, std::string> SavePreferences(KeepAlivePreferences preferences) = 0;
  [[nodiscard]] virtual std::expected<std::unique_ptr<BackgroundExecutionLease>, std::string>
  AcquireGenerationLease() = 0;
};

} // namespace linecode::application
