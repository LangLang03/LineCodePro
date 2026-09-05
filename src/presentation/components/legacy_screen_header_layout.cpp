#include "presentation/components/legacy_screen_header_layout.h"

#include <algorithm>

namespace linecode::presentation {

huxerui::LayoutResult
LegacyScreenHeaderLayout::Measure(huxerui::LayoutContext &context,
                                  huxerui::ViewNode &node,
                                  huxerui::Constraints constraints) {
  huxerui::LayoutResult result;
  if (node.ChildCount() != 3) {
    return result.SetSize(constraints.Constrain({0.0F, 0.0F}));
  }

  auto loose = constraints.Loose();
  huxerui::ViewNode &left = node.ChildAt(0);
  huxerui::ViewNode &title = node.ChildAt(1);
  huxerui::ViewNode &right = node.ChildAt(2);
  const huxerui::Size left_size = context.Measure(left, loose);
  const huxerui::Size right_size = context.Measure(right, loose);

  const float frame_width = constraints.HasBoundedWidth()
                                ? constraints.max_width
                                : left_size.width + right_size.width;
  const float title_width =
      std::max(0.0F, frame_width - left_size.width - right_size.width);
  auto title_constraints = loose;
  title_constraints.min_width = title_width;
  title_constraints.max_width = title_width;
  const huxerui::Size title_size = context.Measure(title, title_constraints);

  const float frame_height =
      std::max({left_size.height, title_size.height, right_size.height});
  const huxerui::Size size = constraints.Constrain({frame_width, frame_height});
  const auto centered_y = [height = size.height](float child_height) {
    return std::max(0.0F, (height - child_height) * 0.5F);
  };

  return result.Place(left, {0.0F, centered_y(left_size.height)})
      .Place(title, {left_size.width, centered_y(title_size.height)})
      .Place(right, {std::max(0.0F, size.width - right_size.width),
                     centered_y(right_size.height)})
      .SetSize(size);
}

} // namespace linecode::presentation
