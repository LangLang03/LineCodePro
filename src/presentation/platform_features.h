#pragma once

#include <cstdint>
#include <functional>
#include <type_traits>
#include <utility>

namespace linecode::presentation {

enum class HostPlatform : std::uint8_t {
  android,
  windows,
  other,
};

enum class PlatformFeature : std::uint8_t {
  keep_alive,
  termux,
  terminal_provider,
  android_storage_permission,
};

consteval HostPlatform CurrentHostPlatform() noexcept {
#if defined(__ANDROID__)
  return HostPlatform::android;
#elif defined(_WIN32)
  return HostPlatform::windows;
#else
  return HostPlatform::other;
#endif
}

template <PlatformFeature Feature, HostPlatform Platform = CurrentHostPlatform()>
struct FeatureAvailability final : std::false_type {};

template <PlatformFeature Feature>
struct FeatureAvailability<Feature, HostPlatform::android> final : std::true_type {};

template <PlatformFeature Feature>
inline constexpr bool FeatureAvailable = FeatureAvailability<Feature>::value;

template <PlatformFeature Feature, typename Factory>
  requires std::invocable<Factory>
void IfFeatureAvailable(Factory&& factory) {
  if constexpr (FeatureAvailable<Feature>) {
    std::invoke(std::forward<Factory>(factory));
  }
}

static_assert(!FeatureAvailability<PlatformFeature::keep_alive, HostPlatform::windows>::value);
static_assert(FeatureAvailability<PlatformFeature::keep_alive, HostPlatform::android>::value);

} // namespace linecode::presentation
