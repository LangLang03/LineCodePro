#include "presentation/components/chat_composer_controls.h"

#include <utility>

namespace linecode::presentation::chat_composer {

using namespace huxerui;

View ComposerControlRow(View attach, View model_pill, View reasoning_pill,
                        View primary) {
  // The spacing between the row's siblings is also the gap the pills keep
  // between each other, so one constant describes the whole control strip.
  constexpr float kGap = 6.0F;
  return Row{
      std::move(attach),
      // Growing the pill group rather than a bare spacer is what keeps the
      // trailing action on screen: the row measures the fixed controls first
      // and hands the remainder to this group, which then clips instead of
      // growing past its share.
      Row{
          Spacer(),
          std::move(model_pill),
          std::move(reasoning_pill),
      }
          .With(Spacing(kGap), Grow(), ClipChildren()),
      std::move(primary),
  }
      .With(Spacing(kGap), CrossAlign(CrossAxisAlignment::Center));
}

} // namespace linecode::presentation::chat_composer
