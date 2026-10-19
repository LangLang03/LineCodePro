#include "gtest_support.h"
#include <cstddef>
#include <string>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "presentation/components/chat_composer_controls.h"

namespace {

using namespace huxerui;
using namespace huxerui::testing;
using linecode::presentation::chat_composer::ComposerControlRow;

// Stands in for one control in the strip: a fixed-size box is deterministic,
// while the pill widths the test passes are the ones a clipped model label and
// the reasoning label actually occupy.
View Box(std::string_view key, float width) {
  return Stack{Text(key)}
      .With(Frame{.width = width, .height = 44.0F})
      .Key(key);
}

float composer_width = 320.0F;

View ControlsProbe() {
  return Column{
      ComposerControlRow(Box("attach", 44.0F), Box("model", 163.0F),
                         Box("reasoning", 99.0F), Box("primary", 44.0F)),
  }.With(Frame{.width = composer_width});
}

struct Strip final {
  UiNodeInfo attach;
  UiNodeInfo model;
  UiNodeInfo reasoning;
  UiNodeInfo primary;
};

Strip Observe(UiTest &ui) {
  return {.attach = ui.Find(UiSelector::Key("attach")).One(),
          .model = ui.Find(UiSelector::Key("model")).One(),
          .reasoning = ui.Find(UiSelector::Key("reasoning")).One(),
          .primary = ui.Find(UiSelector::Key("primary")).One()};
}

float RightEdge(const Rect &rect) { return rect.x + rect.width; }

std::string Describe(const Rect &rect) {
  return "x=" + std::to_string(rect.x) + " width=" +
         std::to_string(rect.width) + " right=" + std::to_string(RightEdge(rect));
}

} // namespace

// A long model name widens the model pill. The primary action has to keep its
// place inside the composer instead of being pushed past its right edge, which
// is what the trailing pill and send button used to do.
TEST(chat_composer_controls_tests, KeepsThePrimaryActionInsideAtPhoneWidths) {
  for (const float width : {320.0F, 360.0F, 412.0F}) {
    composer_width = width;
    const Application application(ControlsProbe, {.show_debug_overlay = false});
    UiTestOptions options;
    options.viewport = {width, 200.0F};
    UiTest ui(application, options);
    ui.PumpAndSettle();

    const auto strip = Observe(ui);
    EXPECT_TRUE(strip.primary.in_viewport)
        << "primary off screen at width " << width << ": "
        << Describe(strip.primary.bounds);
    EXPECT_TRUE(RightEdge(strip.primary.bounds) <= width + 0.5F)
        << "primary pushed out at width " << width << ": "
        << Describe(strip.primary.bounds);
    EXPECT_TRUE(strip.attach.bounds.x >= -0.5F) << Describe(strip.attach.bounds);
  }
}

// The row's own ids stay inside the composer, so nothing spills over the
// rounded border when the labels are at their widest.
TEST(chat_composer_controls_tests, ClipsThePillsInsideTheComposer) {
  for (const float width : {320.0F, 360.0F}) {
    composer_width = width;
    const Application application(ControlsProbe, {.show_debug_overlay = false});
    UiTestOptions options;
    options.viewport = {width, 200.0F};
    UiTest ui(application, options);
    ui.PumpAndSettle();

    const auto strip = Observe(ui);
    EXPECT_TRUE(strip.model.visible_bounds.width > 0.0F)
        << "model pill disappeared at width " << width;
    // The reasoning pill may be clipped by the pill group, but it must still be
    // partly on screen and reachable rather than pushed outside the composer.
    EXPECT_TRUE(strip.reasoning.visible_bounds.width > 0.0F)
        << "reasoning pill disappeared at width " << width;
    EXPECT_TRUE(RightEdge(strip.reasoning.visible_bounds) <= width + 0.5F)
        << "reasoning pill spills out at width " << width << ": "
        << Describe(strip.reasoning.visible_bounds);
  }
}

// Once the composer is wide enough for both pills, neither one is clipped: the
// truncation only exists to keep a narrow strip inside its bounds.
TEST(chat_composer_controls_tests, ShowsBothPillsWhenTheyFit) {
  composer_width = 412.0F;
  const Application application(ControlsProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {412.0F, 200.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  const auto strip = Observe(ui);
  EXPECT_TRUE(strip.model.visible_bounds.width >= strip.model.bounds.width - 0.5F)
      << Describe(strip.model.visible_bounds);
  EXPECT_TRUE(strip.reasoning.visible_bounds.width >=
              strip.reasoning.bounds.width - 0.5F)
      << Describe(strip.reasoning.visible_bounds);
}
