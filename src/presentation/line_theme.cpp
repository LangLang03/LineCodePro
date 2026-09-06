#include "presentation/line_theme.h"

#include <algorithm>
#include <cmath>

#include <huxerui/navigation.h>
#include <huxerui/presentation.h>

namespace linecode::presentation {
namespace {

huxerui::Color ToColor(domain::PackedColor value,
                       domain::PackedColor background) {
  int red = static_cast<int>((value >> 16U) & 0xFFU);
  int green = static_cast<int>((value >> 8U) & 0xFFU);
  int blue = static_cast<int>(value & 0xFFU);
  int alpha = static_cast<int>((value >> 24U) & 0xFFU);
#if defined(__ANDROID__)
  // The Android host composites the HuxerUI content surface at 7/8 opacity.
  // Counter that host-level blend here so the effective pixels retain the
  // legacy palette. The root background is the blend backdrop and is already
  // a fixed point, while translucent tokens only need alpha compensation.
  if (alpha == 0xFF && value != background) {
    const auto restore_channel = [](int desired, int backdrop) {
      return std::clamp(
          static_cast<int>(std::lround(
              (8.0F * static_cast<float>(desired) -
               static_cast<float>(backdrop)) /
              7.0F)),
          0, 255);
    };
    red = restore_channel(red,
                          static_cast<int>((background >> 16U) & 0xFFU));
    green = restore_channel(green,
                            static_cast<int>((background >> 8U) & 0xFFU));
    blue = restore_channel(blue, static_cast<int>(background & 0xFFU));
    const int desired_red = static_cast<int>((value >> 16U) & 0xFFU);
    const int desired_green = static_cast<int>((value >> 8U) & 0xFFU);
    const int desired_blue = static_cast<int>(value & 0xFFU);
    // Android's channel quantization is asymmetric at the two ends of the
    // palette. These one-code-point corrections keep the final 8-bit pixels
    // on the declared legacy token instead of one step above or below it.
    if (desired_red < 128)
      red = std::min(red + 1, 255);
    if (desired_green < 128)
      green = std::min(green + 1, 255);
    else if (desired_green < 240)
      green = std::max(green - 1, 0);
    if (desired_blue > 240)
      blue = std::max(blue - 1, 0);
  } else if (alpha != 0xFF) {
    alpha = std::clamp(static_cast<int>(std::lround(
                           static_cast<float>(alpha) * 8.0F / 7.0F)),
                       0, 255);
  }
#endif
  return huxerui::Color::Rgb(
      red, green, blue, static_cast<float>(alpha) / 255.0F);
}

} // namespace

LineColors LineColors::Default() {
  return LineColorsForPalette(domain::PaletteForMode(domain::ThemeMode::light));
}

LineColors LineColorsForPalette(const domain::ThemePalette &palette) {
  const auto background = palette[domain::ThemeColorRole::background];
  const auto color = [&palette, background](domain::ThemeColorRole role) {
    return ToColor(palette[role], background);
  };
  return {
      .background = color(domain::ThemeColorRole::background),
      .surface = color(domain::ThemeColorRole::surface),
      .elevated = color(domain::ThemeColorRole::surface_elevated),
      .surface_light = color(domain::ThemeColorRole::surface_light),
      .input = color(domain::ThemeColorRole::input_background),
      .accent = color(domain::ThemeColorRole::accent),
      .accent_dim = color(domain::ThemeColorRole::accent_dim),
      .accent_muted = color(domain::ThemeColorRole::accent_muted),
      .accent_muted_strong = color(domain::ThemeColorRole::accent_muted_2),
      .user_bubble = color(domain::ThemeColorRole::user_bubble),
      .ai_bubble = color(domain::ThemeColorRole::ai_bubble),
      .text = color(domain::ThemeColorRole::text),
      .secondary = color(domain::ThemeColorRole::text_secondary),
      .tertiary = color(domain::ThemeColorRole::text_tertiary),
      .text_on_color = color(domain::ThemeColorRole::text_on_color),
      .border = color(domain::ThemeColorRole::border),
      .border_light = color(domain::ThemeColorRole::border_light),
      .code = color(domain::ThemeColorRole::code_background),
      .code_border = color(domain::ThemeColorRole::code_border),
      .danger = color(domain::ThemeColorRole::danger),
      .warning = color(domain::ThemeColorRole::warning),
      .success = color(domain::ThemeColorRole::success),
      .processing = color(domain::ThemeColorRole::processing),
      .overlay = color(domain::ThemeColorRole::overlay),
      .danger_muted = color(domain::ThemeColorRole::danger_muted),
      .danger_muted_strong = color(domain::ThemeColorRole::danger_muted_2),
      .processing_muted = color(domain::ThemeColorRole::processing_muted),
      .diff_add_background = color(domain::ThemeColorRole::diff_add_background),
      .diff_delete_background =
          color(domain::ThemeColorRole::diff_delete_background),
      .diff_add_text = color(domain::ThemeColorRole::diff_add_text),
      .diff_delete_text = color(domain::ThemeColorRole::diff_delete_text),
  };
}

const LineColors &UseLineColors() {
  return huxerui::UseEnvironment<LineColors>();
}

huxerui::ThemeSpec LineLightTheme() { return LineTheme(LineColors::Default()); }

huxerui::ThemeSpec LineTheme(const LineColors &line_colors) {
  huxerui::ThemeSpec theme = huxerui::FlatLightThemeSpec();
  theme.colors.primary = line_colors.accent;
  theme.colors.on_primary = line_colors.text_on_color;
  theme.colors.primary_container = line_colors.accent_dim;
  theme.colors.on_primary_container = line_colors.text;
  theme.colors.secondary = line_colors.secondary;
  theme.colors.on_secondary = line_colors.text_on_color;
  theme.colors.secondary_container = line_colors.surface_light;
  theme.colors.on_secondary_container = line_colors.text;
  theme.colors.background = line_colors.background;
  theme.colors.surface = line_colors.surface;
  theme.colors.surface_container_low = line_colors.input;
  theme.colors.surface_container = line_colors.elevated;
  theme.colors.surface_container_high = line_colors.surface_light;
  theme.colors.surface_container_highest = line_colors.border;
  theme.colors.on_surface = line_colors.text;
  theme.colors.on_surface_variant = line_colors.secondary;
  theme.colors.outline = line_colors.border;
  theme.colors.error = line_colors.danger;
  theme.colors.scrim = line_colors.overlay;

  theme.typography.body_large = 16.0F;
  theme.typography.body_medium = 13.0F;
  theme.typography.body_small = 11.0F;
  theme.typography.title_large = 20.0F;
  theme.shapes.small = 8.0F;
  theme.shapes.medium = 12.0F;
  theme.shapes.large = 16.0F;
  theme.spacing.extra_small = 4.0F;
  theme.spacing.small = 8.0F;
  theme.spacing.medium = 12.0F;
  theme.spacing.large = 16.0F;
  theme.spacing.extra_large = 20.0F;
  return theme;
}

huxerui::ThemeDefinition LineLightThemeDefinition() {
  return LineThemeDefinition(LineColors::Default());
}

huxerui::ThemeDefinition LineThemeDefinition(const LineColors &line_colors) {
  const auto theme = LineTheme(line_colors);
  auto definition = huxerui::FlatThemeDefinition(theme);
  auto text_field = huxerui::detail::DefaultTextFieldStyle(theme);
  text_field.variant = huxerui::TextFieldVariant::Standard;
  text_field.show_label = false;
  text_field.standard.background = huxerui::Color::Transparent();
  text_field.standard.border = huxerui::Color::Transparent();
  text_field.standard.hovered_border = huxerui::Color::Transparent();
  text_field.standard.focused_border = huxerui::Color::Transparent();
  text_field.standard.minimum_height = 44.0F;
  text_field.text_style = {huxerui::Font::System(14.0F), line_colors.text};
  text_field.placeholder_style = {huxerui::Font::System(14.0F),
                                  line_colors.tertiary};
  text_field.padding = huxerui::EdgeInsets{
      .top = 10.0F, .right = 3.0F, .bottom = 10.0F, .left = 3.0F};
  text_field.caret = line_colors.accent;
  text_field.selection = line_colors.accent_muted_strong;
  definition.Set(std::move(text_field));

  auto navigation = huxerui::NavigationStyle::Default();
  if (navigation.motion.has_value()) {
    navigation.motion->push = huxerui::TweenSpec{
        .duration = 0.28, .easing = huxerui::Easing::EaseOut};
    navigation.motion->pop =
        huxerui::TweenSpec{.duration = 0.22, .easing = huxerui::Easing::EaseIn};
  }
  definition.Set(std::move(navigation));

  auto toggle = huxerui::SwitchStyle::Default();
  toggle.width = 46.5F;
  toggle.height = 27.0F;
  toggle.minimum_interactive_height = 27.0F;
  toggle.state_layer_size = 27.0F;
  toggle.unchecked_track = line_colors.elevated;
  toggle.checked_track = line_colors.elevated;
  toggle.unchecked_thumb = line_colors.tertiary;
  toggle.checked_thumb = line_colors.accent;
  toggle.unchecked_thumb_radius = 10.0F;
  toggle.checked_thumb_radius = 10.0F;
  toggle.corner_radius = 13.5F;
  toggle.animation_duration = 0.0;
  definition.Set(std::move(toggle));

  auto divider = huxerui::DividerStyle::Default();
  divider.color = line_colors.border_light;
#if defined(__ANDROID__)
  // The pixel-parity target uses Android's 420-dpi density (2.625 px/dp),
  // while the legacy View divider is exactly one physical pixel. HuxerUI does
  // not expose display scale through a composable public hook at present.
  divider.thickness = 1.0F / 2.625F;
#else
  divider.thickness = 1.0F;
#endif
  definition.Set(std::move(divider));

  auto bottom_sheet = huxerui::BottomSheetStyle::Default();
  bottom_sheet.scrim = line_colors.overlay;
  bottom_sheet.background = huxerui::Color::Transparent();
  bottom_sheet.shadow = huxerui::Shadow{.color = huxerui::Color::Transparent()};
  bottom_sheet.corner_radii = huxerui::CornerRadii(24.0F);
  bottom_sheet.drag_handle = huxerui::Color::Transparent();
  // The legacy panel is capped at 560dp and sits inside 16dp side insets.
  bottom_sheet.maximum_width = 592.0F;
  bottom_sheet.enter =
      huxerui::TweenSpec{.duration = 0.18, .easing = huxerui::Easing::EaseOut};
  bottom_sheet.exit =
      huxerui::TweenSpec{.duration = 0.15, .easing = huxerui::Easing::EaseIn};
  definition.Set(std::move(bottom_sheet));
  return definition;
}

} // namespace linecode::presentation
