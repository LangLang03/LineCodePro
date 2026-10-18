#include "presentation/components/chat_tail_scroll.h"

#include <algorithm>
#include <cmath>

namespace linecode::presentation {
namespace {

// A virtual list only knows the extents it has measured; every row below the
// viewport still carries its estimate, and a conversation row is routinely
// taller than that estimate. `MaxOffset()` therefore reports an end that is
// short of the real transcript, so a single scroll request to it leaves the
// newest output below the fold -- with no further revision to trigger another
// request, nothing corrected it and the reader had to scroll down by hand.
//
// The follow therefore re-issues the request on every frame it is allowed to
// run, so each pass scrolls to the end the layout now knows about and mounts
// the rows after it for measurement; that end grows until it settles. This
// covers the rows the layout is still measuring, a response whose last block
// lands after the final revision, and a list that re-enters with fresh
// estimates. Once the measured extent stops moving, the follow goes idle
// instead of driving frames forever.
constexpr int kTailSettleFrames = 24;
// Frames of unchanged measured extent needed before the catch-up is treated as
// finished. One frame is not enough: a request and the layout pass answering it
// are observed in different frames.
constexpr int kStableFramesToStop = 2;

class ChatTailScrollExtension final : public huxerui::NodeExtension {
public:
  ChatTailScrollExtension(huxerui::ViewNode &node,
                          const ChatTailScroll &value) {
    Update(node, value);
  }

  void Update(huxerui::ViewNode &, const ChatTailScroll &value) {
    controller_ = value.controller;
    if (!initialized_) {
      initialized_ = true;
      conversation_id_ = value.conversation_id;
      content_revision_ = value.content_revision;
      smooth_request_ = value.smooth_request;
      turn_id_ = value.turn_id;
      RequestTail(false);
      return;
    }

    const bool conversation_changed = conversation_id_ != value.conversation_id;
    const bool content_changed = content_revision_ != value.content_revision;
    // A new turn means the user just sent a message: showing its answer is what
    // they asked for, even if they had scrolled up to read something older.
    // Content arriving inside the turn they are already reading must not move
    // them, so only a changed turn identity re-arms the follow.
    const bool turn_started = value.turn_id != 0 && turn_id_ != value.turn_id;
    const bool smooth_requested = smooth_request_ != value.smooth_request;
    conversation_id_ = value.conversation_id;
    content_revision_ = value.content_revision;
    turn_id_ = value.turn_id;
    smooth_request_ = value.smooth_request;

    if (conversation_changed || turn_started) {
      follow_tail_ = true;
      RequestTail(false);
    }
    if (smooth_requested) {
      follow_tail_ = true;
      RequestTail(true);
    } else if (content_changed && follow_tail_) {
      RequestTail(false);
    }
  }

  FrameResult OnFrame(huxerui::ViewNode &,
                      const huxerui::FrameInfo &frame) override {
    // The list may not be mounted on the first frames of a conversation. Keep
    // the pending catch-up instead of dropping it, so mounting the list still
    // lands on the newest output.
    if (!controller_.IsConnected())
      return {};
    if (settle_frames_ <= 0)
      return {};
    --settle_frames_;

    if (animating_) {
      const float maximum = controller_.MaxOffset();
      const bool retarget = !motion_.IsRunning() ||
                            std::abs(motion_.Target() - maximum) > 0.5F;
      if (retarget) {
        motion_.AnimateTo(
            maximum, huxerui::TweenSpec{.duration = 0.180,
                                        .easing = huxerui::Easing::EaseOut});
      }
      const auto advanced = motion_.Advance(frame);
      static_cast<void>(controller_.ScrollTo(
          std::clamp(motion_.Value(), 0.0F, maximum)));
      if (motion_.IsRunning())
        return {.needs_frame = true, .wake_after = advanced.wake_after};
      animating_ = false;
    }

    // Re-issue the request: the layout answers it on a later pass, once it has
    // measured the rows between here and the end.
    static_cast<void>(controller_.ScrollTo(controller_.MaxOffset()));

    const auto metrics = controller_.Metrics();
    const bool at_end = metrics.maximum_offset <= metrics.offset + 0.5F;
    const bool extent_settled =
        std::abs(metrics.content_extent - measured_extent_) <= 0.5F;
    if (at_end && extent_settled) {
      if (++stable_frames_ >= kStableFramesToStop)
        settle_frames_ = 0;
    } else {
      stable_frames_ = 0;
    }
    measured_extent_ = metrics.content_extent;
    return {.needs_frame = true, .wake_after = std::nullopt};
  }

  [[nodiscard]] bool HitTest(huxerui::ViewNode &node,
                             huxerui::Point point) const override {
    return node.Bounds().Contains(point);
  }

  PointerResult OnPointer(huxerui::ViewNode &,
                          const huxerui::PointerEvent &event) override {
    if (event.type == huxerui::PointerEventType::Down)
      PauseFollowing();
    return event.type == huxerui::PointerEventType::Down
               ? PointerResult::Observe
               : PointerResult::Ignored;
  }

  void OnScrollActivity(
      huxerui::ViewNode &,
      const huxerui::ScrollActivity &activity) override {
    if ((activity.phase == huxerui::ScrollPhase::Begin ||
         activity.phase == huxerui::ScrollPhase::Update) &&
        IsManualSource(activity.source)) {
      PauseFollowing();
    }
  }

private:
  static bool IsManualSource(huxerui::ScrollSource source) noexcept {
    switch (source) {
    case huxerui::ScrollSource::Drag:
    case huxerui::ScrollSource::Wheel:
    case huxerui::ScrollSource::Momentum:
    case huxerui::ScrollSource::Scrollbar:
    case huxerui::ScrollSource::Accessibility:
      return true;
    case huxerui::ScrollSource::Overscroll:
    case huxerui::ScrollSource::Programmatic:
    case huxerui::ScrollSource::FocusReveal:
    case huxerui::ScrollSource::DragDrop:
      return false;
    }
    return false;
  }

  void RequestTail(bool smooth) {
    settle_frames_ = kTailSettleFrames;
    stable_frames_ = 0;
    measured_extent_ = -1.0F;
    animating_ = smooth;
    if (smooth && controller_.IsConnected())
      motion_.Set(controller_.Offset());
  }

  void PauseFollowing() {
    follow_tail_ = false;
    settle_frames_ = 0;
    stable_frames_ = 0;
    animating_ = false;
    motion_.Set(controller_.Offset());
  }

  huxerui::ScrollController controller_;
  huxerui::MotionController motion_;
  std::string conversation_id_;
  std::size_t content_revision_ = 0;
  std::uint64_t smooth_request_ = 0;
  std::uint64_t turn_id_ = 0;
  float measured_extent_ = -1.0F;
  int settle_frames_ = 0;
  int stable_frames_ = 0;
  bool initialized_ = false;
  bool follow_tail_ = true;
  bool animating_ = false;
};

} // namespace

const huxerui::detail::ModifierDescriptor &ChatTailScroll::Descriptor() {
  return huxerui::detail::ModifierDescriptorFor<ChatTailScroll,
                                                 ChatTailScrollExtension>();
}

} // namespace linecode::presentation
