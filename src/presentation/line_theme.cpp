#include "presentation/line_theme.h"

#include <huxerui/navigation.h>
#include <huxerui/presentation.h>

namespace linecode::presentation {

huxerui::ThemeSpec LineLightTheme() {
  huxerui::ThemeSpec theme = huxerui::FlatLightThemeSpec();
  theme.colors.primary = colors::accent;
  theme.colors.on_primary = colors::text_on_color;
  theme.colors.primary_container = colors::accent_dim;
  theme.colors.on_primary_container = colors::text;
  theme.colors.secondary = colors::secondary;
  theme.colors.on_secondary = colors::text_on_color;
  theme.colors.secondary_container = colors::surface_light;
  theme.colors.on_secondary_container = colors::text;
  theme.colors.background = colors::background;
  theme.colors.surface = colors::surface;
  theme.colors.surface_container_low = colors::input;
  theme.colors.surface_container = colors::elevated;
  theme.colors.surface_container_high = colors::surface_light;
  theme.colors.surface_container_highest = colors::border;
  theme.colors.on_surface = colors::text;
  theme.colors.on_surface_variant = colors::secondary;
  theme.colors.outline = colors::border;
  theme.colors.error = colors::danger;
  theme.colors.scrim = huxerui::Color::Rgb(22, 26, 32, 0.26F);

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
  const auto theme = LineLightTheme();
  auto definition = huxerui::FlatThemeDefinition(theme);
  auto text_field = huxerui::detail::DefaultTextFieldStyle(theme);
  text_field.variant = huxerui::TextFieldVariant::Standard;
  text_field.show_label = false;
  text_field.standard.background = huxerui::Color::Transparent();
  text_field.standard.border = huxerui::Color::Transparent();
  text_field.standard.hovered_border = huxerui::Color::Transparent();
  text_field.standard.focused_border = huxerui::Color::Transparent();
  text_field.standard.minimum_height = 44.0F;
  text_field.text_style = {huxerui::Font::System(14.0F), colors::text};
  text_field.placeholder_style = {huxerui::Font::System(14.0F),
                                  colors::tertiary};
  text_field.padding = huxerui::EdgeInsets{
      .top = 10.0F, .right = 3.0F, .bottom = 10.0F, .left = 3.0F};
  text_field.caret = colors::accent;
  text_field.selection = colors::accent_muted_strong;
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
  bottom_sheet.scrim = huxerui::Color::Rgb(22, 26, 32, 0.26F);
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
