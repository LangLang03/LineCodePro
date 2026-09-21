#pragma once

#include <huxerui/layout.h>
#include <huxerui/view.h>

namespace linecode::presentation {

/// Reproduces the legacy settings group's 16dp horizontal gutter while
/// respecting the 760dp desktop cap.
class LegacySettingsCardFrame final
    : public huxerui::Layout<LegacySettingsCardFrame> {
public:
  using huxerui::Layout<LegacySettingsCardFrame>::Layout;

  static huxerui::LayoutResult Measure(huxerui::LayoutContext &context,
                                       huxerui::ViewNode &node,
                                       huxerui::Constraints constraints);
};

} // namespace linecode::presentation
