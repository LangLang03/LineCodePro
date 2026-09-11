#include <cassert>
#include <variant>

#include <huxerui/presentation.h>

#include "presentation/line_theme.h"

namespace {

using huxerui::Color;
using huxerui::CornerRadii;
using huxerui::Easing;
using huxerui::FontWeight;
using huxerui::TweenSpec;
using huxerui::VisualFill;
using linecode::presentation::LineBottomSheetStyle;
using linecode::presentation::LineColors;
using linecode::presentation::LineDialogStyle;

void StandardDialogMatchesLegacyLineAlertDialog() {
  auto colors = LineColors::Default();
  colors.background = Color::Rgb(11, 22, 33);
  colors.text = Color::Rgb(44, 55, 66);

  const auto style = LineDialogStyle(colors);
  assert(style.scrim == Color::Rgb(0, 0, 0, 0.60F));
  assert(style.background == VisualFill(colors.background));
  assert(style.title_style.font.Size() == 20.0F);
  assert(style.title_style.font.Weight() == FontWeight::Bold);
  assert(style.title_style.foreground == colors.text);
  assert(style.message_style.font.Size() == 16.0F);
  assert(style.message_style.foreground == colors.text);
  assert(style.positive_action_style.font.Size() == 14.0F);
  assert(style.positive_action_style.font.Weight() == FontWeight::Regular);
  assert(style.positive_action_style.foreground == colors.text);
  assert(style.negative_action_style == style.positive_action_style);
  assert(style.positive_action_background ==
         VisualFill(Color::Transparent()));
  assert(style.negative_action_background ==
         VisualFill(Color::Transparent()));
  assert(style.minimum_action_height == 48.0F);
  assert(style.corner_radii == CornerRadii{24.0F});
  assert(style.maximum_width == 560.0F);
  assert(style.viewport_margin == 16.0F);
}

void BottomSheetLeavesLegacyPanelAppearanceToScreenComponents() {
  // Legacy in-app overlays paint the palette overlay, not the dialog dim.
  auto colors = LineColors::Default();
  colors.overlay = Color::Rgb(7, 8, 9, 0.26F);
  const auto style = LineBottomSheetStyle(colors);
  assert(style.scrim == Color::Rgb(7, 8, 9, 0.26F));
  assert(style.background == VisualFill(Color::Transparent()));
  assert(style.shadow.color == Color::Transparent());
  assert(style.corner_radii == CornerRadii{24.0F});
  assert(style.drag_handle == Color::Transparent());
  assert(style.maximum_width == 592.0F);

  const auto *enter = std::get_if<TweenSpec>(&style.enter);
  const auto *exit = std::get_if<TweenSpec>(&style.exit);
  assert(enter != nullptr);
  assert(exit != nullptr);
  assert(enter->duration == 0.18);
  assert(std::holds_alternative<Easing>(enter->easing));
  assert(std::get<Easing>(enter->easing) == Easing::EaseOut);
  assert(exit->duration == 0.15);
  assert(std::holds_alternative<Easing>(exit->easing));
  assert(std::get<Easing>(exit->easing) == Easing::EaseIn);
}

} // namespace

int main() {
  StandardDialogMatchesLegacyLineAlertDialog();
  BottomSheetLeavesLegacyPanelAppearanceToScreenComponents();
}
