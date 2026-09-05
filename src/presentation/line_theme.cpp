#include "presentation/line_theme.h"

#include <huxerui/navigation.h>
#include <huxerui/presentation.h>

namespace linecode::presentation {
namespace {

huxerui::Color ToColor(domain::PackedColor value) {
  return huxerui::Color::Rgb(
      static_cast<int>((value >> 16U) & 0xFFU),
      static_cast<int>((value >> 8U) & 0xFFU), static_cast<int>(value & 0xFFU),
      static_cast<float>((value >> 24U) & 0xFFU) / 255.0F);
}

} // namespace

LineColors LineColors::Default() {
  return LineColorsForPalette(domain::PaletteForMode(domain::ThemeMode::light));
}

LineColors LineColorsForPalette(const domain::ThemePalette &palette) {
  return {
      .background = ToColor(palette[domain::ThemeColorRole::background]),
      .surface = ToColor(palette[domain::ThemeColorRole::surface]),
      .elevated = ToColor(palette[domain::ThemeColorRole::surface_elevated]),
      .surface_light = ToColor(palette[domain::ThemeColorRole::surface_light]),
      .input = ToColor(palette[domain::ThemeColorRole::input_background]),
      .accent = ToColor(palette[domain::ThemeColorRole::accent]),
      .accent_dim = ToColor(palette[domain::ThemeColorRole::accent_dim]),
      .accent_muted = ToColor(palette[domain::ThemeColorRole::accent_muted]),
      .accent_muted_strong =
          ToColor(palette[domain::ThemeColorRole::accent_muted_2]),
      .user_bubble = ToColor(palette[domain::ThemeColorRole::user_bubble]),
      .ai_bubble = ToColor(palette[domain::ThemeColorRole::ai_bubble]),
      .text = ToColor(palette[domain::ThemeColorRole::text]),
      .secondary = ToColor(palette[domain::ThemeColorRole::text_secondary]),
      .tertiary = ToColor(palette[domain::ThemeColorRole::text_tertiary]),
      .text_on_color = ToColor(palette[domain::ThemeColorRole::text_on_color]),
      .border = ToColor(palette[domain::ThemeColorRole::border]),
      .border_light = ToColor(palette[domain::ThemeColorRole::border_light]),
      .code = ToColor(palette[domain::ThemeColorRole::code_background]),
      .code_border = ToColor(palette[domain::ThemeColorRole::code_border]),
      .danger = ToColor(palette[domain::ThemeColorRole::danger]),
      .warning = ToColor(palette[domain::ThemeColorRole::warning]),
      .success = ToColor(palette[domain::ThemeColorRole::success]),
      .processing = ToColor(palette[domain::ThemeColorRole::processing]),
      .overlay = ToColor(palette[domain::ThemeColorRole::overlay]),
      .danger_muted = ToColor(palette[domain::ThemeColorRole::danger_muted]),
      .danger_muted_strong =
          ToColor(palette[domain::ThemeColorRole::danger_muted_2]),
      .processing_muted =
          ToColor(palette[domain::ThemeColorRole::processing_muted]),
      .diff_add_background =
          ToColor(palette[domain::ThemeColorRole::diff_add_background]),
      .diff_delete_background =
          ToColor(palette[domain::ThemeColorRole::diff_delete_background]),
      .diff_add_text = ToColor(palette[domain::ThemeColorRole::diff_add_text]),
      .diff_delete_text =
          ToColor(palette[domain::ThemeColorRole::diff_delete_text]),
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
