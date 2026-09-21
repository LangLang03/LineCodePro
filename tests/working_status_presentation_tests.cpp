#include "presentation/working_status_presentation.h"

#include "gtest_support.h"
#include <cmath>

namespace {

bool Near(float left, float right) {
  return std::abs(left - right) < 0.001F;
}

} // namespace

TEST(working_status_presentation_tests, LegacySuite) {
  using linecode::presentation::PresentWorkingStatusFrame;

  const auto first = PresentWorkingStatusFrame(0.0F, 100.0F);
  EXPECT_EXPRESSION(first.active_dot == 1);
  EXPECT_EXPRESSION(first.trailing_dot == 0);
  EXPECT_EXPRESSION(Near(first.shimmer_center, -47.0F));

  const auto second = PresentWorkingStatusFrame(0.06F, 100.0F);
  EXPECT_EXPRESSION(second.active_dot == 2);
  EXPECT_EXPRESSION(second.trailing_dot == 1);
  EXPECT_EXPRESSION(Near(second.shimmer_center, -32.36F));

  const auto third = PresentWorkingStatusFrame(0.12F, 100.0F);
  EXPECT_EXPRESSION(third.active_dot == 5);
  EXPECT_EXPRESSION(third.trailing_dot == 2);

  const auto late = PresentWorkingStatusFrame(0.99F, 100.0F);
  EXPECT_EXPRESSION(late.active_dot == 1);
  EXPECT_EXPRESSION(late.trailing_dot == 0);
  EXPECT_EXPRESSION(Near(late.shimmer_center, 194.56F));
}
