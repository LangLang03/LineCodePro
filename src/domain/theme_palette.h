#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace linecode::domain {

enum class ThemeMode : std::uint8_t {
  system,
  light,
  dark,
  coffee,
  vscode,
  github_dark,
  gruvbox,
  high_contrast,
  custom,
};

enum class ThemeColorRole : std::uint8_t {
  background,
  surface,
  surface_elevated,
  surface_light,
  input_background,
  text,
  text_secondary,
  text_tertiary,
  text_on_color,
  accent,
  user_bubble,
  ai_bubble,
  border,
  border_light,
  code_background,
  code_border,
  danger,
  warning,
  success,
  accent_dim,
  accent_muted,
  accent_muted_2,
  processing,
  overlay,
  danger_muted,
  danger_muted_2,
  processing_muted,
  diff_add_background,
  diff_delete_background,
  diff_add_text,
  diff_delete_text,
};

inline constexpr std::size_t theme_color_count = 19;
inline constexpr std::size_t theme_palette_color_count = 31;
inline constexpr std::array<ThemeColorRole, theme_color_count>
    theme_color_roles{
        ThemeColorRole::background,       ThemeColorRole::surface,
        ThemeColorRole::surface_elevated, ThemeColorRole::surface_light,
        ThemeColorRole::input_background, ThemeColorRole::text,
        ThemeColorRole::text_secondary,   ThemeColorRole::text_tertiary,
        ThemeColorRole::text_on_color,    ThemeColorRole::accent,
        ThemeColorRole::user_bubble,      ThemeColorRole::ai_bubble,
        ThemeColorRole::border,           ThemeColorRole::border_light,
        ThemeColorRole::code_background,  ThemeColorRole::code_border,
        ThemeColorRole::danger,           ThemeColorRole::warning,
        ThemeColorRole::success,
    };

using PackedColor = std::uint32_t; // 0xAARRGGBB

struct ThemePalette final {
  std::array<PackedColor, theme_palette_color_count> colors{};

  [[nodiscard]] constexpr PackedColor operator[](ThemeColorRole role) const {
    return colors[static_cast<std::size_t>(role)];
  }

  constexpr PackedColor &operator[](ThemeColorRole role) {
    return colors[static_cast<std::size_t>(role)];
  }

  bool operator==(const ThemePalette &) const = default;
};

using ThemeColorDraft = std::array<std::string, theme_color_count>;

[[nodiscard]] std::string_view SerializeThemeMode(ThemeMode mode);
[[nodiscard]] ThemeMode ParseThemeMode(std::string_view mode);
[[nodiscard]] std::string_view SerializeThemeColorRole(ThemeColorRole role);
[[nodiscard]] ThemePalette PaletteForMode(ThemeMode mode);
[[nodiscard]] ThemePalette ApplyThemeDraft(ThemePalette base,
                                           const ThemeColorDraft &draft);
[[nodiscard]] ThemeColorDraft EditableThemeDraft(const ThemePalette &palette);
[[nodiscard]] bool IsHexColor(std::string_view value);
[[nodiscard]] std::optional<PackedColor> ParseHexColor(std::string_view value);
[[nodiscard]] std::string ToHexColor(PackedColor color);
[[nodiscard]] bool IsValidThemeDraft(const ThemeColorDraft &draft);

} // namespace linecode::domain
