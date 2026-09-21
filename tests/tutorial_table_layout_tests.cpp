#include "gtest_support.h"
#include <cmath>
#include <string_view>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "presentation/components/tutorial_table_layout.h"

namespace {

using namespace huxerui;
using namespace huxerui::testing;
using linecode::presentation::TutorialTableCellCoordinates;
using linecode::presentation::TutorialTableCellPosition;
using linecode::presentation::TutorialTableLayout;

View Cell(std::string_view key, std::string_view label,
          TutorialTableCellCoordinates position) {
  return Stack{Text(label)}
      .With(Frame{.min_width = 84.0F, .min_height = 38.0F}, Padding(8.0F))
      .LayoutValue<TutorialTableCellPosition>(position)
      .Key(key);
}

View TableProbe() {
  return ScrollView(TutorialTableLayout{
                        Cell("left", "A", {.row = 0U, .column = 0U}),
                        Cell("right", "B", {.row = 0U, .column = 1U}),
                    })
      .ScrollAxis(Axis::Horizontal)
      .With(Frame{.width = 347.43F});
}

bool NearlyEqual(float left, float right) {
  return std::abs(left - right) < 0.01F;
}

} // namespace

TEST(tutorial_table_layout_tests, LegacySuite) {
  const Application application(TableProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {360.0F, 640.0F};
  UiTest ui(application, options);

  const auto left = ui.Find(UiSelector::Key("left")).One();
  const auto right = ui.Find(UiSelector::Key("right")).One();
  EXPECT_EXPRESSION(NearlyEqual(left.size.width, 173.715F));
  EXPECT_EXPRESSION(NearlyEqual(right.size.width, 173.715F));
  EXPECT_EXPRESSION(NearlyEqual(right.bounds.x - left.bounds.x, 173.715F));
}
