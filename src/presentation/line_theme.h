#pragma once

#include <huxerui/theme.h>

namespace linecode::presentation {

huxerui::ThemeSpec LineCoffeeTheme();
huxerui::ThemeDefinition LineCoffeeThemeDefinition();

namespace colors {

inline constexpr huxerui::Color background = huxerui::Color::Rgb(244, 239, 230);
inline constexpr huxerui::Color surface = huxerui::Color::Rgb(238, 229, 216);
inline constexpr huxerui::Color elevated = huxerui::Color::Rgb(231, 220, 202);
inline constexpr huxerui::Color surface_light = huxerui::Color::Rgb(222, 208, 185);
inline constexpr huxerui::Color input = huxerui::Color::Rgb(255, 251, 243);
inline constexpr huxerui::Color accent = huxerui::Color::Rgb(217, 119, 87);
inline constexpr huxerui::Color accent_dim = huxerui::Color::Rgb(241, 212, 198);
inline constexpr huxerui::Color accent_muted = huxerui::Color::Rgb(217, 119, 87, 0.12F);
inline constexpr huxerui::Color accent_muted_strong = huxerui::Color::Rgb(217, 119, 87, 0.18F);
inline constexpr huxerui::Color user_bubble = huxerui::Color::Rgb(184, 111, 80);
inline constexpr huxerui::Color ai_bubble = huxerui::Color::Rgb(239, 228, 212);
inline constexpr huxerui::Color text = huxerui::Color::Rgb(43, 33, 24);
inline constexpr huxerui::Color secondary = huxerui::Color::Rgb(108, 90, 73);
inline constexpr huxerui::Color tertiary = huxerui::Color::Rgb(155, 137, 118);
inline constexpr huxerui::Color text_on_color = huxerui::Color::White();
inline constexpr huxerui::Color border = huxerui::Color::Rgb(221, 208, 191);
inline constexpr huxerui::Color border_light = huxerui::Color::Rgb(232, 221, 207);
inline constexpr huxerui::Color code = huxerui::Color::Rgb(91, 65, 40, 0.07F);
inline constexpr huxerui::Color danger = huxerui::Color::Rgb(181, 71, 63);

} // namespace colors
} // namespace linecode::presentation
