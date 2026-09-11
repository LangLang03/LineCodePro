#pragma once

#include <memory>
#include <optional>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"

namespace linecode::application {

class SkillHubReadingSettings final {
public:
  explicit SkillHubReadingSettings(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<float>
  LoadScale(std::optional<float> legacy_scale = std::nullopt) const;
  [[nodiscard]] huxerui::Task<void> SaveScale(float scale) const;

  [[nodiscard]] static float NormalizeScale(float scale) noexcept;

private:
  std::shared_ptr<AsyncSettingsStore> store_;
};

} // namespace linecode::application
