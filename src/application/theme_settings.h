#pragma once

#include <memory>

#include "application/ports/settings_store.h"
#include "domain/theme_palette.h"

namespace linecode::application {

class SystemThemeSource {
public:
  virtual ~SystemThemeSource() = default;
  [[nodiscard]] virtual bool IsDarkModeEnabled() const = 0;
};

struct ThemeSettingsState final {
  domain::ThemeMode selected_mode{domain::ThemeMode::system};
  domain::ThemeMode resolved_mode{domain::ThemeMode::light};
  domain::ThemeColorDraft custom_colors{domain::EditableThemeDraft(
      domain::PaletteForMode(domain::ThemeMode::custom))};
  bool has_saved_custom_colors{};
  domain::ThemePalette palette{
      domain::PaletteForMode(domain::ThemeMode::light)};

  bool operator==(const ThemeSettingsState &) const = default;
};

class ThemeSettingsService {
public:
  virtual ~ThemeSettingsService() = default;
  [[nodiscard]] virtual ThemeSettingsState Load() const = 0;
  [[nodiscard]] virtual ThemeSettingsState
  SelectMode(domain::ThemeMode mode) = 0;
  [[nodiscard]] virtual ThemeSettingsState
  SaveCustomColors(const domain::ThemeColorDraft &colors) = 0;
};

class ThemeSettingsRepository final : public ThemeSettingsService {
public:
  ThemeSettingsRepository(std::shared_ptr<SettingsStore> settings,
                          std::shared_ptr<SystemThemeSource> system_theme);

  [[nodiscard]] ThemeSettingsState Load() const override;
  [[nodiscard]] ThemeSettingsState SelectMode(domain::ThemeMode mode) override;
  [[nodiscard]] ThemeSettingsState
  SaveCustomColors(const domain::ThemeColorDraft &colors) override;

private:
  [[nodiscard]] domain::ThemeColorDraft LoadCustomColors(bool &has_saved) const;

  std::shared_ptr<SettingsStore> settings_;
  std::shared_ptr<SystemThemeSource> system_theme_;
};

} // namespace linecode::application
