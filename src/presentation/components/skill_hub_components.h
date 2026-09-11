#pragma once

#include <functional>
#include <optional>
#include <vector>

#include <huxerui/resource.h>
#include <huxerui/view.h>

namespace linecode::presentation {

[[nodiscard]] huxerui::TextStyle
SkillHubLabel(float size,
              huxerui::FontWeight weight = huxerui::FontWeight::Regular,
              huxerui::Color color = {});

[[nodiscard]] huxerui::View SkillHubGlyph(huxerui::ImageVariant icon,
                                          float size, huxerui::Color tint);

[[nodiscard]] huxerui::View SkillHubHeader(huxerui::StringVariant title,
                                           std::function<void()> on_back);

[[nodiscard]] huxerui::View
SkillHubScrollablePage(huxerui::StringVariant title,
                       std::function<void()> on_back,
                       std::vector<huxerui::View> content);

[[nodiscard]] huxerui::View SkillHubTag(huxerui::StringVariant text,
                                        huxerui::Color color,
                                        huxerui::Color background);

// HuxerUI Padding is content padding, not an Android-style LayoutParams
// margin. Keep legacy top margins on a transparent wrapper so a later spacing
// declaration cannot replace the child's own card/input padding.
[[nodiscard]] huxerui::View SkillHubMargin(huxerui::View child,
                                           huxerui::EdgeInsets insets);

[[nodiscard]] huxerui::View SkillHubTopMargin(huxerui::View child,
                                              float amount);

[[nodiscard]] huxerui::View SkillHubSection(huxerui::StringVariant title,
                                            huxerui::ImageResource icon,
                                            std::vector<huxerui::View> content);

[[nodiscard]] huxerui::View
SkillHubDialogPanel(std::vector<huxerui::View> content);

[[nodiscard]] huxerui::View SkillHubDialogButton(huxerui::StringVariant text,
                                                 bool primary,
                                                 std::function<void()> on_click,
                                                 bool enabled = true);

} // namespace linecode::presentation
