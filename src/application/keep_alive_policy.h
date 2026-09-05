#pragma once

#include <cstddef>
#include <string>

#include "application/ports/keep_alive.h"

namespace linecode::application {

struct KeepAlivePolicyInput final {
  KeepAlivePreferences manual{};
  std::size_t active_generation_leases{};
  std::string status;

  bool operator==(const KeepAlivePolicyInput &) const = default;
};

struct EffectiveKeepAliveState final {
  bool wake_lock_enabled{};
  bool foreground_service_enabled{};
  bool silent_audio_enabled{};
  std::string status;

  bool operator==(const EffectiveKeepAliveState &) const = default;
};

/// Pure policy retained in portable C++: platform code only actuates the
/// already-resolved effective state.
[[nodiscard]] constexpr EffectiveKeepAliveState
ResolveKeepAliveState(const KeepAlivePolicyInput &input) {
  const bool generation_active = input.active_generation_leases != 0;
  const bool silent_audio = input.manual.silent_audio_enabled;
  return {
      .wake_lock_enabled = input.manual.wake_lock_enabled || generation_active,
      .foreground_service_enabled = input.manual.foreground_service_enabled ||
                                    generation_active || silent_audio,
      .silent_audio_enabled = silent_audio,
      .status = input.status,
  };
}

namespace detail {

constexpr KeepAlivePolicyInput kIdlePolicy{
    .manual = {.wake_lock_enabled = false},
};
constexpr KeepAlivePolicyInput kGenerationPolicy{
    .manual = {.wake_lock_enabled = false},
    .active_generation_leases = 1,
};
constexpr KeepAlivePolicyInput kSilentPolicy{
    .manual =
        {
            .wake_lock_enabled = false,
            .silent_audio_enabled = true,
        },
};
static_assert(!ResolveKeepAliveState(kIdlePolicy).foreground_service_enabled);
static_assert(ResolveKeepAliveState(kGenerationPolicy).wake_lock_enabled);
static_assert(
    ResolveKeepAliveState(kGenerationPolicy).foreground_service_enabled);
static_assert(ResolveKeepAliveState(kSilentPolicy).silent_audio_enabled);
static_assert(ResolveKeepAliveState(kSilentPolicy).foreground_service_enabled);

} // namespace detail
} // namespace linecode::application
