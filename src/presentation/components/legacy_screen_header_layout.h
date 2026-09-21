#pragma once

#include <huxerui/layout.h>
#include <huxerui/view.h>

namespace linecode::presentation {

/// Three-slot header matching the legacy Android LinearLayout contract:
/// compact actions on both sides and a title owning all remaining width.
class LegacyScreenHeaderLayout final
    : public huxerui::Layout<LegacyScreenHeaderLayout> {
public:
  using huxerui::Layout<LegacyScreenHeaderLayout>::Layout;

  static huxerui::LayoutResult Measure(huxerui::LayoutContext &context,
                                       huxerui::ViewNode &node,
                                       huxerui::Constraints constraints);
};

/// One physical-pixel separator used only below legacy screen headers.
/// Card-row separators intentionally keep the lighter global Divider token.
[[nodiscard]] [[huxerui::composable]] huxerui::View
LegacyScreenHeaderDivider();

} // namespace linecode::presentation
