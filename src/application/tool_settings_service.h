#pragma once

#include <array>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <variant>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/tool_settings.h"

namespace linecode::application {

namespace tool_setting_keys {

inline constexpr std::string_view web_search_config =
    "@lineai_web_search_config";
inline constexpr std::string_view image_understanding_model_id =
    "@lineai_image_understanding_model_id";
inline constexpr std::string_view image_generation_model_id =
    "@lineai_image_generation_model_id";

} // namespace tool_setting_keys

struct ImageModelSettingDescriptor final {
  domain::ImageModelPurpose purpose;
  std::string domain::ToolSettingsState::*state_member;
  std::string_view storage_key;
};

inline constexpr std::array image_model_setting_catalog{
    ImageModelSettingDescriptor{
        domain::ImageModelPurpose::understanding,
        &domain::ToolSettingsState::image_understanding_model_id,
        tool_setting_keys::image_understanding_model_id},
    ImageModelSettingDescriptor{
        domain::ImageModelPurpose::generation,
        &domain::ToolSettingsState::image_generation_model_id,
        tool_setting_keys::image_generation_model_id},
};

[[nodiscard]] constexpr const ImageModelSettingDescriptor &
ImageModelSettingInfo(domain::ImageModelPurpose purpose) noexcept {
  for (const auto &descriptor : image_model_setting_catalog) {
    if (descriptor.purpose == purpose)
      return descriptor;
  }
  std::unreachable();
}

struct WebSearchConfigurationChange final {
  domain::WebSearchConfig value;

  bool operator==(const WebSearchConfigurationChange &) const = default;
};

struct ImageModelSelectionChange final {
  domain::ImageModelPurpose purpose;
  std::string model_id;

  bool operator==(const ImageModelSelectionChange &) const = default;
};

using ToolSettingsChange =
    std::variant<WebSearchConfigurationChange, ImageModelSelectionChange>;

[[nodiscard]] domain::ToolSettingsState
ApplyToolSettingsChange(domain::ToolSettingsState state,
                        const ToolSettingsChange &change);

class ToolSettingsService {
public:
  virtual ~ToolSettingsService() = default;

  [[nodiscard]] virtual huxerui::Task<
      SettingsResult<domain::ToolSettingsState>>
  Load() = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  Persist(ToolSettingsChange change) = 0;
};

} // namespace linecode::application
