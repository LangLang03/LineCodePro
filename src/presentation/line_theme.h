#pragma once

#include <cstdint>

#include <huxerui/environment.h>
#include <huxerui/paint.h>
#include <huxerui/theme.h>

#include "domain/theme_palette.h"

namespace linecode::presentation {

struct LineColors final {
  huxerui::Color background;
  huxerui::Color surface;
  huxerui::Color elevated;
  huxerui::Color surface_light;
  huxerui::Color input;
  huxerui::Color accent;
  huxerui::Color accent_dim;
  huxerui::Color accent_muted;
  huxerui::Color accent_muted_strong;
  huxerui::Color user_bubble;
  huxerui::Color ai_bubble;
  huxerui::Color text;
  huxerui::Color secondary;
  huxerui::Color tertiary;
  huxerui::Color text_on_color;
  huxerui::Color border;
  huxerui::Color border_light;
  huxerui::Color code;
  huxerui::Color code_border;
  huxerui::Color danger;
  huxerui::Color warning;
  huxerui::Color success;
  huxerui::Color processing;
  huxerui::Color overlay;
  huxerui::Color danger_muted;
  huxerui::Color danger_muted_strong;
  huxerui::Color processing_muted;
  huxerui::Color diff_add_background;
  huxerui::Color diff_delete_background;
  huxerui::Color diff_add_text;
  huxerui::Color diff_delete_text;

  [[nodiscard]] static LineColors Default();
  bool operator==(const LineColors &) const = default;
};

[[nodiscard]] LineColors
LineColorsForPalette(const domain::ThemePalette &palette);
[[nodiscard]] const LineColors &UseLineColors();

huxerui::ThemeSpec LineLightTheme();
huxerui::ThemeSpec LineTheme(const LineColors &colors);
huxerui::ThemeDefinition LineLightThemeDefinition();
huxerui::ThemeDefinition LineThemeDefinition(const LineColors &colors);

namespace colors {

struct Token final {
  huxerui::Color LineColors::*member;

  operator huxerui::Color() const { return UseLineColors().*member; }
  operator huxerui::VisualFill() const {
    return huxerui::VisualFill{UseLineColors().*member};
  }
};

inline constexpr Token background{&LineColors::background};
inline constexpr Token surface{&LineColors::surface};
inline constexpr Token elevated{&LineColors::elevated};
inline constexpr Token surface_light{&LineColors::surface_light};
inline constexpr Token input{&LineColors::input};
inline constexpr Token accent{&LineColors::accent};
inline constexpr Token accent_dim{&LineColors::accent_dim};
inline constexpr Token accent_muted{&LineColors::accent_muted};
inline constexpr Token accent_muted_strong{&LineColors::accent_muted_strong};
inline constexpr Token user_bubble{&LineColors::user_bubble};
inline constexpr Token ai_bubble{&LineColors::ai_bubble};
inline constexpr Token text{&LineColors::text};
inline constexpr Token secondary{&LineColors::secondary};
inline constexpr Token tertiary{&LineColors::tertiary};
inline constexpr Token text_on_color{&LineColors::text_on_color};
inline constexpr Token border{&LineColors::border};
inline constexpr Token border_light{&LineColors::border_light};
inline constexpr Token code{&LineColors::code};
inline constexpr Token code_border{&LineColors::code_border};
inline constexpr Token danger{&LineColors::danger};
inline constexpr Token warning{&LineColors::warning};
inline constexpr Token success{&LineColors::success};
inline constexpr Token processing{&LineColors::processing};
inline constexpr Token overlay{&LineColors::overlay};
inline constexpr Token danger_muted{&LineColors::danger_muted};
inline constexpr Token danger_muted_strong{&LineColors::danger_muted_strong};
inline constexpr Token processing_muted{&LineColors::processing_muted};
inline constexpr Token diff_add_background{&LineColors::diff_add_background};
inline constexpr Token diff_delete_background{
    &LineColors::diff_delete_background};
inline constexpr Token diff_add_text{&LineColors::diff_add_text};
inline constexpr Token diff_delete_text{&LineColors::diff_delete_text};

} // namespace colors
} // namespace linecode::presentation
