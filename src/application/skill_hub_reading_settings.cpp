#include "application/skill_hub_reading_settings.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

// Keep the observable legacy preference name. The typed settings store uses
// thousandths because its portable numeric contract is integral.
constexpr auto kScaleKey = "markdown_text_scale";
constexpr std::int64_t kDefaultScale = 1'000;
constexpr std::int64_t kMinimumScale = 500;
constexpr std::int64_t kMaximumScale = 1'600;
constexpr std::int64_t kMissingScale = -1;

} // namespace

SkillHubReadingSettings::SkillHubReadingSettings(
    std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_)
    throw std::invalid_argument("SkillHub reading settings store is required");
}

huxerui::Task<float> SkillHubReadingSettings::LoadScale(
    const std::optional<float> legacy_scale) const {
  auto stored = co_await store_->GetInteger(kScaleKey, kMissingScale);
  if (!stored)
    co_return legacy_scale ? NormalizeScale(*legacy_scale) : 1.0F;
  if (*stored == kMissingScale) {
    if (!legacy_scale)
      co_return 1.0F;
    const float migrated = NormalizeScale(*legacy_scale);
    const auto milli = static_cast<std::int64_t>(
        std::lround(migrated * static_cast<float>(kDefaultScale)));
    static_cast<void>(co_await store_->SetInteger(kScaleKey, milli));
    co_return migrated;
  }
  const auto milli = std::clamp(*stored, kMinimumScale, kMaximumScale);
  co_return static_cast<float>(milli) / 1'000.0F;
}

huxerui::Task<void> SkillHubReadingSettings::SaveScale(float scale) const {
  const auto milli = static_cast<std::int64_t>(
      std::lround(NormalizeScale(scale) * 1'000.0F));
  static_cast<void>(co_await store_->SetInteger(kScaleKey, milli));
}

float SkillHubReadingSettings::NormalizeScale(const float scale) noexcept {
  if (!std::isfinite(scale))
    return 1.0F;
  return std::clamp(scale, static_cast<float>(kMinimumScale) / 1'000.0F,
                    static_cast<float>(kMaximumScale) / 1'000.0F);
}

} // namespace linecode::application
