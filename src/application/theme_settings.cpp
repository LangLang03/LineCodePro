#include "application/theme_settings.h"

#include <cctype>
#include <stdexcept>
#include <string>
#include <string_view>

namespace linecode::application {
namespace {

constexpr std::string_view kThemeModeKey = "@lineai_theme_mode";
constexpr std::string_view kCustomColorsKey = "@lineai_custom_theme_colors";

std::string EscapeJson(std::string_view value) {
  std::string result;
  result.reserve(value.size());
  for (char character : value) {
    if (character == '\\' || character == '"')
      result.push_back('\\');
    result.push_back(character);
  }
  return result;
}

std::string EncodeCustomColors(const domain::ThemeColorDraft &colors) {
  std::string result{"{"};
  for (std::size_t index = 0; index < domain::theme_color_count; ++index) {
    if (index != 0)
      result.push_back(',');
    result += '"' +
              EscapeJson(domain::SerializeThemeColorRole(
                  domain::theme_color_roles[index])) +
              "\":\"";
    result += EscapeJson(colors[index]);
    result.push_back('"');
  }
  result.push_back('}');
  return result;
}

std::optional<std::string> ReadJsonString(std::string_view json,
                                          std::string_view key) {
  const std::string marker = '"' + std::string(key) + "\"";
  auto position = json.find(marker);
  if (position == std::string_view::npos)
    return std::nullopt;
  position = json.find(':', position + marker.size());
  if (position == std::string_view::npos)
    return std::nullopt;
  position = json.find('"', position + 1);
  if (position == std::string_view::npos)
    return std::nullopt;
  std::string value;
  for (++position; position < json.size(); ++position) {
    const char character = json[position];
    if (character == '"')
      return value;
    if (character == '\\' && position + 1 < json.size())
      ++position;
    value.push_back(json[position]);
  }
  return std::nullopt;
}

} // namespace

ThemeSettingsRepository::ThemeSettingsRepository(
    std::shared_ptr<SettingsStore> settings,
    std::shared_ptr<SystemThemeSource> system_theme)
    : settings_(std::move(settings)), system_theme_(std::move(system_theme)) {
  if (!settings_ || !system_theme_) {
    throw std::invalid_argument(
        "Theme settings dependencies must not be empty");
  }
}

ThemeSettingsState ThemeSettingsRepository::Load() const {
  const auto stored_mode = settings_->Read(kThemeModeKey).value_or("system");
  const auto selected = domain::ParseThemeMode(stored_mode);
  const auto resolved =
      selected == domain::ThemeMode::system
          ? (system_theme_->IsDarkModeEnabled() ? domain::ThemeMode::dark
                                                : domain::ThemeMode::light)
          : selected;
  bool has_saved = false;
  auto custom = LoadCustomColors(has_saved);
  auto palette = domain::PaletteForMode(resolved);
  if (resolved == domain::ThemeMode::custom) {
    palette = domain::ApplyThemeDraft(std::move(palette), custom);
  }
  return {.selected_mode = selected,
          .resolved_mode = resolved,
          .custom_colors = std::move(custom),
          .has_saved_custom_colors = has_saved,
          .palette = std::move(palette)};
}

ThemeSettingsState ThemeSettingsRepository::SelectMode(domain::ThemeMode mode) {
  settings_->Write(kThemeModeKey,
                   std::string(domain::SerializeThemeMode(mode)));
  return Load();
}

ThemeSettingsState ThemeSettingsRepository::SaveCustomColors(
    const domain::ThemeColorDraft &colors) {
  if (!domain::IsValidThemeDraft(colors))
    return Load();
  settings_->Write(kCustomColorsKey, EncodeCustomColors(colors));
  settings_->Write(kThemeModeKey, std::string(domain::SerializeThemeMode(
                                      domain::ThemeMode::custom)));
  return Load();
}

domain::ThemeColorDraft
ThemeSettingsRepository::LoadCustomColors(bool &has_saved) const {
  auto result = domain::EditableThemeDraft(
      domain::PaletteForMode(domain::ThemeMode::custom));
  const auto raw = settings_->Read(kCustomColorsKey);
  if (!raw || raw->empty())
    return result;
  for (std::size_t index = 0; index < domain::theme_color_count; ++index) {
    auto value = ReadJsonString(*raw, domain::SerializeThemeColorRole(
                                          domain::theme_color_roles[index]));
    if (value && domain::IsHexColor(*value)) {
      result[index] = std::move(*value);
      has_saved = true;
    }
  }
  return result;
}

} // namespace linecode::application
