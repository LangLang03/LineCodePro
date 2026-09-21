#include "application/theme_settings_migration.h"

#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>

#include "application/theme_settings.h"

namespace linecode::application {
namespace {

huxerui::Task<SettingsResult<bool>> ImportOne(
    const std::shared_ptr<SettingsStore> &destination,
    const std::shared_ptr<AsyncSettingsStore> &source, std::string_view key) {
  if (destination->Read(key).has_value())
    co_return false;

  auto source_value = co_await source->GetString(std::string{key}, {});
  if (!source_value)
    co_return std::unexpected(std::move(source_value.error()));
  if (source_value->empty() || destination->Read(key).has_value())
    co_return false;

  destination->Write(key, std::move(*source_value));
  co_return true;
}

} // namespace

huxerui::Task<SettingsResult<bool>> ThemeSettingsMigration::ImportIfMissing(
    std::shared_ptr<SettingsStore> destination,
    std::shared_ptr<AsyncSettingsStore> source) {
  if (!destination || !source)
    throw std::invalid_argument("Theme migration stores must not be empty");

  auto mode = co_await ImportOne(destination, source, ThemeSettingsKeys::mode);
  if (!mode)
    co_return std::unexpected(std::move(mode.error()));
  auto colors =
      co_await ImportOne(destination, source, ThemeSettingsKeys::custom_colors);
  if (!colors)
    co_return std::unexpected(std::move(colors.error()));
  co_return *mode || *colors;
}

} // namespace linecode::application
