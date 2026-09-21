#include "gtest_support.h"
#include <string>
#include <variant>

#include <huxerui/presentation.h>

#include "presentation/line_theme.h"
#include "presentation/legacy_text_presentation.h"

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
using linecode::presentation::LineDialogBottomSheetStyle;
using linecode::presentation::LineDrawerStyle;
using linecode::presentation::LineToastStyle;
using linecode::presentation::LegacySectionTitle;

void SectionTitlesMatchLegacyRootUppercaseForShippedLocales() {
  EXPECT_EXPRESSION(LegacySectionTitle("Coding") == "CODING");
  EXPECT_EXPRESSION(LegacySectionTitle("keep alive") == "KEEP ALIVE");
  EXPECT_EXPRESSION(LegacySectionTitle("编码保活") == "编码保活");
}

void StandardDialogMatchesLegacyLineAlertDialog() {
  auto colors = LineColors::Default();
  colors.background = Color::Rgb(11, 22, 33);
  colors.text = Color::Rgb(44, 55, 66);

  const auto style = LineDialogStyle(colors);
  EXPECT_EXPRESSION(style.scrim == Color::Rgb(0, 0, 0, 0.60F));
  EXPECT_EXPRESSION(style.background == VisualFill(colors.background));
  EXPECT_EXPRESSION(style.title_style.font.Size() == 20.0F);
  EXPECT_EXPRESSION(style.title_style.font.Weight() == FontWeight::Medium);
  EXPECT_EXPRESSION(style.title_style.foreground == colors.text);
  EXPECT_EXPRESSION(style.message_style.font.Size() == 16.0F);
  EXPECT_EXPRESSION(style.message_style.foreground == colors.text);
  EXPECT_EXPRESSION(style.positive_action_style.font.Size() == 14.0F);
  EXPECT_EXPRESSION(style.positive_action_style.font.Weight() == FontWeight::Regular);
  EXPECT_EXPRESSION(style.positive_action_style.foreground == colors.text);
  EXPECT_EXPRESSION(style.negative_action_style == style.positive_action_style);
  EXPECT_EXPRESSION(style.positive_action_background ==
         VisualFill(Color::Transparent()));
  EXPECT_EXPRESSION(style.negative_action_background ==
         VisualFill(Color::Transparent()));
  EXPECT_EXPRESSION(style.minimum_action_height == 48.0F);
  EXPECT_EXPRESSION(style.corner_radii == CornerRadii{24.0F});
  EXPECT_EXPRESSION(style.maximum_width == 560.0F);
  EXPECT_EXPRESSION(style.viewport_margin == 16.0F);
}

void BottomSheetLeavesLegacyPanelAppearanceToScreenComponents() {
  // Legacy in-app overlays paint the palette overlay, not the dialog dim.
  auto colors = LineColors::Default();
  colors.overlay = Color::Rgb(7, 8, 9, 0.26F);
  const auto style = LineBottomSheetStyle(colors);
  EXPECT_EXPRESSION(style.scrim == Color::Rgb(7, 8, 9, 0.26F));
  EXPECT_EXPRESSION(style.background == VisualFill(Color::Transparent()));
  EXPECT_EXPRESSION(style.shadow.color == Color::Transparent());
  EXPECT_EXPRESSION(style.corner_radii == CornerRadii{24.0F});
  EXPECT_EXPRESSION(style.drag_handle == Color::Transparent());
  EXPECT_EXPRESSION(style.maximum_width == 592.0F);

  const auto *enter = std::get_if<TweenSpec>(&style.enter);
  const auto *exit = std::get_if<TweenSpec>(&style.exit);
  EXPECT_EXPRESSION(enter != nullptr);
  EXPECT_EXPRESSION(exit != nullptr);
  EXPECT_EXPRESSION(enter->duration == 0.18);
  EXPECT_EXPRESSION(std::holds_alternative<Easing>(enter->easing));
  EXPECT_EXPRESSION(std::get<Easing>(enter->easing) == Easing::EaseOut);
  EXPECT_EXPRESSION(exit->duration == 0.15);
  EXPECT_EXPRESSION(std::holds_alternative<Easing>(exit->easing));
  EXPECT_EXPRESSION(std::get<Easing>(exit->easing) == Easing::EaseIn);
}

void ToastMatchesLegacyAndroidSurface() {
  auto colors = LineColors::Default();
  colors.background = Color::Rgb(245, 246, 247);
  colors.text = Color::Rgb(22, 23, 24);
  const auto style = LineToastStyle(colors);
  EXPECT_EXPRESSION(style.background == VisualFill(colors.background));
  EXPECT_EXPRESSION(style.text_style.font.Size() == 14.0F);
  EXPECT_EXPRESSION(style.text_style.foreground == colors.text);
  EXPECT_EXPRESSION(style.corner_radii == CornerRadii{24.0F});
  EXPECT_EXPRESSION(style.minimum_height == 48.0F);
  EXPECT_EXPRESSION(style.maximum_width == 480.0F);
}

void DialogBottomSheetUsesLegacyModalScrim() {
  auto colors = LineColors::Default();
  const auto regular = linecode::presentation::LineBottomSheetStyle(colors);
  const auto dialog = LineDialogBottomSheetStyle(colors);
  EXPECT_EXPRESSION(regular.scrim == colors.overlay);
  EXPECT_EXPRESSION(dialog.scrim == Color::Rgb(0, 0, 0, 0.60F));
  EXPECT_EXPRESSION(dialog.background == regular.background);
  EXPECT_EXPRESSION(dialog.maximum_width == regular.maximum_width);
}

void DrawerMotionMatchesLegacyTiming() {
  const auto style = LineDrawerStyle();
  EXPECT_EXPRESSION(style.motion.has_value());
  const auto *open = std::get_if<TweenSpec>(&style.motion->open);
  const auto *close = std::get_if<TweenSpec>(&style.motion->close);
  EXPECT_EXPRESSION(open != nullptr);
  EXPECT_EXPRESSION(close != nullptr);
  EXPECT_EXPRESSION(open->duration == 0.18);
  EXPECT_EXPRESSION(std::holds_alternative<Easing>(open->easing));
  EXPECT_EXPRESSION(std::get<Easing>(open->easing) == Easing::EaseOut);
  EXPECT_EXPRESSION(close->duration == 0.15);
  EXPECT_EXPRESSION(std::holds_alternative<Easing>(close->easing));
  EXPECT_EXPRESSION(std::get<Easing>(close->easing) == Easing::EaseIn);
}

} // namespace

TEST(presentation_theme_tests, LegacySuite) {
  SectionTitlesMatchLegacyRootUppercaseForShippedLocales();
  StandardDialogMatchesLegacyLineAlertDialog();
  BottomSheetLeavesLegacyPanelAppearanceToScreenComponents();
  ToastMatchesLegacyAndroidSurface();
  DialogBottomSheetUsesLegacyModalScrim();
  DrawerMotionMatchesLegacyTiming();
}
