#include "presentation/components/legacy_settings_card_frame.h"

#include <algorithm>

namespace linecode::presentation {

huxerui::LayoutResult
LegacySettingsCardFrame::Measure(huxerui::LayoutContext &context,
                                 huxerui::ViewNode &node,
                                 huxerui::Constraints constraints) {
  constexpr float kHorizontalGutter = 16.0F;
  constexpr float kMaximumCardWidth = 760.0F;

  huxerui::LayoutResult result;
  if (node.ChildCount() == 0) {
    return result.SetSize(constraints.Constrain({0.0F, 0.0F}));
  }

  auto child_constraints = constraints.Loose();
  if (constraints.HasBoundedWidth()) {
    const float available =
        std::max(0.0F, constraints.max_width - (kHorizontalGutter * 2.0F));
    const float card_width = std::min(kMaximumCardWidth, available);
    child_constraints.min_width = card_width;
    child_constraints.max_width = card_width;
  } else {
    child_constraints.max_width = kMaximumCardWidth;
  }

  huxerui::ViewNode &card = node.ChildAt(0);
  const huxerui::Size card_size = context.Measure(card, child_constraints);
  const float desired_width = card_size.width + (kHorizontalGutter * 2.0F);
  const float frame_width =
      constraints.HasBoundedWidth() ? constraints.max_width : desired_width;
  const huxerui::Size frame_size =
      constraints.Constrain({frame_width, card_size.height});
  const float card_x =
      std::max(0.0F, (frame_size.width - card_size.width) * 0.5F);
  return result.Place(card, {card_x, 0.0F}).SetSize(frame_size);
}

} // namespace linecode::presentation
