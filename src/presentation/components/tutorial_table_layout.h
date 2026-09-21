#pragma once

#include <cstddef>

#include <huxerui/view.h>

namespace linecode::presentation {

struct TutorialTableCellCoordinates final {
  std::size_t row{};
  std::size_t column{};

  bool operator==(const TutorialTableCellCoordinates&) const = default;
};

struct TutorialTableCellPosition final {
  using Value = TutorialTableCellCoordinates;
};

// TableLayout's stretchAllColumns behavior is distinct from an ordinary Row:
// every row shares column widths, short tables fill the horizontal viewport,
// and wide tables keep an intrinsic extent for horizontal scrolling.
class TutorialTableLayout final
    : public huxerui::Layout<TutorialTableLayout> {
public:
  using Layout::Layout;

  static huxerui::LayoutResult Measure(huxerui::LayoutContext& context,
                                       huxerui::ViewNode& node,
                                       huxerui::Constraints constraints);
};

} // namespace linecode::presentation
