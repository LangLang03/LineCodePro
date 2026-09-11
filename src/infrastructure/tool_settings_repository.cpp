#include "infrastructure/tool_settings_repository.h"

#include <stdexcept>
#include <string>
#include <utility>

#include "infrastructure/tool_settings_codec.h"

namespace linecode::infrastructure {

PersistedToolSettings::PersistedToolSettings(
    std::shared_ptr<application::AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_)
    throw std::invalid_argument("tool settings store must not be empty");
}

huxerui::Task<application::SettingsResult<domain::ToolSettingsState>>
PersistedToolSettings::Load() {
  const auto fallback = domain::DefaultWebSearchConfig();
  auto web_search = co_await store_->GetString(
      std::string{application::tool_setting_keys::web_search_config},
      EncodeWebSearchConfig(fallback));
  if (!web_search)
    co_return std::unexpected(web_search.error());

  auto understanding = co_await store_->GetString(
      std::string{
          application::tool_setting_keys::image_understanding_model_id},
      {});
  if (!understanding)
    co_return std::unexpected(understanding.error());

  auto generation = co_await store_->GetString(
      std::string{application::tool_setting_keys::image_generation_model_id},
      {});
  if (!generation)
    co_return std::unexpected(generation.error());

  auto decoded = DecodeWebSearchConfig(*web_search);
  co_return domain::ToolSettingsState{
      .web_search = decoded ? std::move(*decoded) : fallback,
      .image_understanding_model_id =
          domain::NormalizeToolModelId(std::move(*understanding)),
      .image_generation_model_id =
          domain::NormalizeToolModelId(std::move(*generation)),
  };
}

huxerui::Task<application::SettingsResult<void>>
PersistedToolSettings::Persist(application::ToolSettingsChange change) {
  if (const auto *web =
          std::get_if<application::WebSearchConfigurationChange>(&change)) {
    co_return co_await store_->SetString(
        std::string{application::tool_setting_keys::web_search_config},
        EncodeWebSearchConfig(web->value));
  }

  auto selection =
      std::get<application::ImageModelSelectionChange>(std::move(change));
  const auto &descriptor =
      application::ImageModelSettingInfo(selection.purpose);
  co_return co_await store_->SetString(std::string{descriptor.storage_key},
      domain::NormalizeToolModelId(std::move(selection.model_id)));
}

} // namespace linecode::infrastructure
