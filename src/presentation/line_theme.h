#pragma once

#include <huxerui/theme.h>

namespace linecode::presentation {

huxerui::ThemeSpec LineLightTheme();
huxerui::ThemeDefinition LineLightThemeDefinition();

namespace colors {

inline constexpr huxerui::Color background = huxerui::Color::Rgb(252, 252, 253);
inline constexpr huxerui::Color surface = huxerui::Color::Rgb(240, 241, 243);
inline constexpr huxerui::Color elevated = huxerui::Color::Rgb(234, 236, 240);
inline constexpr huxerui::Color surface_light =
    huxerui::Color::Rgb(225, 228, 233);
inline constexpr huxerui::Color input = huxerui::Color::Rgb(240, 241, 243);
inline constexpr huxerui::Color accent = huxerui::Color::Rgb(51, 59, 70);
inline constexpr huxerui::Color accent_dim = huxerui::Color::Rgb(229, 232, 236);
inline constexpr huxerui::Color accent_muted =
    huxerui::Color::Rgb(51, 59, 70, 0.06F);
inline constexpr huxerui::Color accent_muted_strong =
    huxerui::Color::Rgb(51, 59, 70, 0.10F);
inline constexpr huxerui::Color user_bubble =
    huxerui::Color::Rgb(240, 241, 243);
inline constexpr huxerui::Color ai_bubble = huxerui::Color::Rgb(252, 252, 253);
inline constexpr huxerui::Color text = huxerui::Color::Rgb(36, 38, 42);
inline constexpr huxerui::Color secondary = huxerui::Color::Rgb(108, 115, 125);
inline constexpr huxerui::Color tertiary = huxerui::Color::Rgb(108, 115, 125);
inline constexpr huxerui::Color text_on_color = huxerui::Color::White();
inline constexpr huxerui::Color border = huxerui::Color::Rgb(225, 228, 232);
inline constexpr huxerui::Color border_light =
    huxerui::Color::Rgb(234, 236, 240);
inline constexpr huxerui::Color code = huxerui::Color::Rgb(240, 241, 243);
inline constexpr huxerui::Color danger = huxerui::Color::Rgb(156, 80, 88);

} // namespace colors
} // namespace linecode::presentation
