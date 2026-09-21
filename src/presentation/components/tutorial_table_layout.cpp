#include "presentation/components/tutorial_table_layout.h"

#include <algorithm>
#include <numeric>
#include <vector>

namespace linecode::presentation {
namespace {

constexpr float kMinimumCellWidth = 84.0F;
constexpr float kMaximumNaturalCellWidth = 220.0F;
// Horizontal ScrollView currently measures its content with an unbounded main
// axis and a zero minimum. The parity device viewport is 411.43dp wide at
// 1080px/420dpi; after the settings-card and preview insets, Android's
// fillViewport contract is 347.43dp. Keep this compatibility floor until the
// public SDK exposes ScrollView::FillViewport. Wider intrinsic tables still
// remain horizontally scrollable.
constexpr float kLegacyReferenceViewportWidth = 347.43F;

struct MeasuredTable final {
  std::vector<float> column_widths;
  std::vector<float> row_heights;
};

MeasuredTable MeasureNaturalCells(huxerui::LayoutContext& context,
                                  huxerui::ViewNode& node,
                                  huxerui::Constraints constraints) {
  std::size_t row_count{};
  std::size_t column_count{};
  for (huxerui::ViewNode& child : node.Children()) {
    const auto position = child.LayoutValueOr<TutorialTableCellPosition>({});
    row_count = std::max(row_count, position.row + 1U);
    column_count = std::max(column_count, position.column + 1U);
  }

  MeasuredTable measured{
      .column_widths = std::vector<float>(column_count, kMinimumCellWidth),
      .row_heights = std::vector<float>(row_count, 0.0F),
  };
  auto natural = constraints.Loose();
  natural.max_width = kMaximumNaturalCellWidth;
  for (huxerui::ViewNode& child : node.Children()) {
    const auto position = child.LayoutValueOr<TutorialTableCellPosition>({});
    const auto size = context.Measure(child, natural);
    measured.column_widths[position.column] =
        std::max(measured.column_widths[position.column], size.width);
  }
  return measured;
}

void StretchColumnsToViewport(std::vector<float>& widths,
                              float minimum_width) {
  if (widths.empty())
    return;
  const float natural_width =
      std::accumulate(widths.begin(), widths.end(), 0.0F);
  if (natural_width >= minimum_width)
    return;
  const float extra =
      (minimum_width - natural_width) / static_cast<float>(widths.size());
  for (float& width : widths)
    width += extra;
}

} // namespace

huxerui::LayoutResult TutorialTableLayout::Measure(
    huxerui::LayoutContext& context, huxerui::ViewNode& node,
    huxerui::Constraints constraints) {
  huxerui::LayoutResult result;
  if (node.ChildCount() == 0U)
    return result.SetSize(constraints.Constrain({0.0F, 0.0F}));

  auto table = MeasureNaturalCells(context, node, constraints);
  StretchColumnsToViewport(
      table.column_widths,
      std::max(constraints.min_width, kLegacyReferenceViewportWidth));

  for (huxerui::ViewNode& child : node.Children()) {
    const auto position = child.LayoutValueOr<TutorialTableCellPosition>({});
    auto cell = constraints.Loose().TightWidth(
        table.column_widths[position.column]);
    const auto size = context.Measure(child, cell);
    table.row_heights[position.row] =
        std::max(table.row_heights[position.row], size.height);
  }

  std::vector<float> column_offsets(table.column_widths.size(), 0.0F);
  for (std::size_t column = 1U; column < column_offsets.size(); ++column) {
    column_offsets[column] =
        column_offsets[column - 1U] + table.column_widths[column - 1U];
  }
  std::vector<float> row_offsets(table.row_heights.size(), 0.0F);
  for (std::size_t row = 1U; row < row_offsets.size(); ++row)
    row_offsets[row] = row_offsets[row - 1U] + table.row_heights[row - 1U];

  for (huxerui::ViewNode& child : node.Children()) {
    const auto position = child.LayoutValueOr<TutorialTableCellPosition>({});
    auto cell = constraints.Loose()
                    .TightWidth(table.column_widths[position.column])
                    .TightHeight(table.row_heights[position.row]);
    [[maybe_unused]] const auto final_size = context.Measure(child, cell);
    result.Place(child, {column_offsets[position.column],
                         row_offsets[position.row]});
  }

  const float width = std::accumulate(table.column_widths.begin(),
                                      table.column_widths.end(), 0.0F);
  const float height = std::accumulate(table.row_heights.begin(),
                                       table.row_heights.end(), 0.0F);
  return result.SetSize(constraints.Constrain({width, height}));
}

} // namespace linecode::presentation
