#include "presentation/components/legacy_switch.h"

#include <utility>

#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
LegacySwitch(bool checked, std::function<void(bool)> on_changed) {
  using namespace huxerui;
  const auto &colors = UseLineColors();
  return Stack{
             Stack{}.With(Frame{.width = 24.0F, .height = 14.0F},
                          // The legacy Android Switch applies these opaque
                          // tint tokens directly. An extra alpha blend makes
                          // every settings track much darker than the source.
                          Background(checked ? colors.accent_dim
                                             : colors.surface_light),
                          CornerRadius(7.0F),
                          Align(HorizontalAlignment::Center,
                                VerticalAlignment::Center)),
             Switch(checked).OnChanged(std::move(on_changed)),
         }
      .With(Frame{.width = 46.5F, .height = 27.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

} // namespace linecode::presentation
