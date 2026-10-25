#include "presentation/components/chat_tail_scroll.h"

#include <algorithm>
#include <cmath>
#include <utility>

namespace linecode::presentation {
namespace {

// How far the trailing edge may sit from the anchored bottom before the follow
// re-pins it. Half a pixel absorbs float rounding from extent refinement
// without turning the anchor into a per-frame busy loop.
constexpr float kTailAnchorEpsilon = 0.5F;
// Bounds the drift corrections issued between two content revisions. Extent
// estimates and measured heights can disagree while a virtual layout realizes
// items around the tail; correcting that drift is necessary, but an unbounded
// correction loop would schedule frames forever.
constexpr int kTailAnchorBudget = 3;
// Upper bound on the frames spent observing one trailing-item alignment before
// it is retired forcibly. A healthy alignment converges in one or two frames.
constexpr int kTailSettleFrameCap = 30;

class ChatTailScrollExtension final : public huxerui::NodeExtension {
public:
  ChatTailScrollExtension(huxerui::ViewNode &node,
                          const ChatTailScroll &value) {
    Update(node, value);
  }

  void Update(huxerui::ViewNode &, const ChatTailScroll &value) {
    controller_ = value.controller;
    item_count_ = value.item_count;
    if (!initialized_) {
      initialized_ = true;
      conversation_id_ = value.conversation_id;
      content_revision_ = value.content_revision;
      smooth_request_ = value.smooth_request;
      turn_id_ = value.turn_id;
      snap_pending_ = true;
      return;
    }

    const bool conversation_changed = conversation_id_ != value.conversation_id;
    // A new turn means the user just sent a message, so showing its answer is
    // what they asked for even when they had scrolled up to read something
    // older. Content arriving inside the turn they are reading must not move
    // them, so only a changed turn identity re-arms the follow.
    const bool turn_started = value.turn_id != 0 && turn_id_ != value.turn_id;
    const bool smooth_requested = smooth_request_ != value.smooth_request;
    const bool content_changed = content_revision_ != value.content_revision;
    conversation_id_ = value.conversation_id;
    content_revision_ = value.content_revision;
    turn_id_ = value.turn_id;
    smooth_request_ = value.smooth_request;

    if (conversation_changed || turn_started) {
      follow_tail_ = true;
      smooth_pending_ = false;
      snap_pending_ = true;
      motion_.Set(controller_.Offset());
    }
    if (smooth_requested) {
      follow_tail_ = true;
      snap_pending_ = false;
      smooth_pending_ = true;
    }
    // A streaming revision re-arms exactly one tail alignment; the virtual
    // layout refines that alignment against measured extents on its own
    // frames, and the settle observer below retires it once the position is
    // stable again. Chasing every revision with repeated scroll requests used
    // to restart that refinement endlessly, and letting revisions go
    // unanswered left the tail below the fold while tokens kept arriving.
    if (content_changed && follow_tail_) {
      align_pending_ = true;
      anchor_budget_ = kTailAnchorBudget;
    }
  }

  FrameResult OnFrame(huxerui::ViewNode &,
                      const huxerui::FrameInfo &frame) override {
    if (!controller_.IsConnected())
      return {};

    if (smooth_pending_) {
      smooth_pending_ = false;
      motion_.Set(controller_.Offset());
      pursuit_target_ = controller_.MaxOffset();
      motion_.AnimateTo(
          pursuit_target_,
          huxerui::TweenSpec{.duration = 0.180,
                             .easing = huxerui::Easing::EaseOut});
      return {.needs_frame = true};
    }
    if (motion_.IsRunning()) {
      // Streaming keeps growing the tail while the pursuit animation runs.
      // Retargeting from the current value and velocity lets the animation
      // land on the real bottom instead of finishing short and inviting
      // another explicit request.
      const float maximum = controller_.MaxOffset();
      if (maximum != pursuit_target_) {
        pursuit_target_ = maximum;
        motion_.AnimateTo(pursuit_target_,
                          huxerui::TweenSpec{.duration = 0.180,
                                             .easing = huxerui::Easing::EaseOut});
      }
      const auto advanced = motion_.Advance(frame);
      static_cast<void>(controller_.ScrollTo(
          std::clamp(motion_.Value(), 0.0F, controller_.MaxOffset())));
      if (!motion_.IsRunning())
        anchored_max_ = motion_.Target();
      return {.needs_frame = advanced.needs_frame,
              .wake_after = advanced.wake_after};
    }
    if (snap_pending_ || align_pending_) {
      snap_pending_ = false;
      align_pending_ = false;
      anchor_budget_ = kTailAnchorBudget;
      AlignTail();
      // Observe the alignment until the offset rests on the bottom, then
      // retire it. A virtual layout keeps a pending alignment armed for
      // post-measurement refinement, and an already-satisfied alignment never
      // retires on its own - it would schedule frames forever.
      settling_ = true;
      settle_frames_left_ = kTailSettleFrameCap;
      last_seen_max_ = controller_.MaxOffset();
      return {.needs_frame = true};
    }
    if (settling_) {
      const float maximum = controller_.MaxOffset();
      const float offset = controller_.Offset();
      const bool stable =
          std::abs(maximum - last_seen_max_) <= kTailAnchorEpsilon &&
          std::abs(offset - maximum) <= kTailAnchorEpsilon;
      last_seen_max_ = maximum;
      if (stable || --settle_frames_left_ <= 0) {
        settling_ = false;
        anchored_max_ = maximum;
        if (!pointer_down_)
          RetireAlignment(offset);
        return {};
      }
      return {.needs_frame = true};
    }
    if (follow_tail_ && anchor_budget_ > 0) {
      const float maximum = controller_.MaxOffset();
      if (std::abs(maximum - anchored_max_) > kTailAnchorEpsilon) {
        --anchor_budget_;
        anchored_max_ = maximum;
        static_cast<void>(controller_.ScrollTo(maximum));
        return {.needs_frame = true};
      }
    }
    return {};
  }

  [[nodiscard]] bool HitTest(huxerui::ViewNode &node,
                             huxerui::Point point) const override {
    return node.Bounds().Contains(point);
  }

  PointerResult OnPointer(huxerui::ViewNode &,
                          const huxerui::PointerEvent &event) override {
    if (event.type == huxerui::PointerEventType::Down) {
      pointer_down_ = true;
      PauseFollowing();
    } else if (event.type == huxerui::PointerEventType::Up ||
               event.type == huxerui::PointerEventType::Cancel) {
      pointer_down_ = false;
    }
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

  // Bottom anchoring goes through the trailing item alignment so the virtual
  // layout refines the offset against measured extents. Only when no virtual
  // layout accepts the request does it fall back to the raw maximum offset,
  // which is computed from extent estimates for unrealized items.
  void AlignTail() {
    if (item_count_ > 0U &&
        controller_.ScrollToItem(item_count_ - 1U,
                                 huxerui::ScrollAlignment::End))
      return;
    static_cast<void>(controller_.ScrollTo(controller_.MaxOffset()));
  }

  // A pending trailing-item alignment stays armed until direct scrolling or a
  // newer request replaces it, and an already-satisfied alignment never
  // retires by itself - it would keep scheduling frames forever. A real
  // one-pixel detour and back registers as direct scrolling and ends at the
  // same offset, so the alignment is canceled without moving the viewport.
  void RetireAlignment(float offset) {
    static_cast<void>(controller_.ScrollTo(offset - 1.0F));
    static_cast<void>(controller_.ScrollTo(offset));
  }

  void PauseFollowing() {
    follow_tail_ = false;
    snap_pending_ = false;
    smooth_pending_ = false;
    align_pending_ = false;
    // `settling_` deliberately stays: an alignment this extension issued is
    // still pending, and its observer must retire it even though the reader
    // paused the follow.
    motion_.Set(controller_.Offset());
    pursuit_target_ = controller_.Offset();
  }

  huxerui::ScrollController controller_;
  huxerui::MotionController motion_;
  std::string conversation_id_;
  std::size_t content_revision_ = 0;
  std::size_t item_count_ = 0;
  std::uint64_t smooth_request_ = 0;
  std::uint64_t turn_id_ = 0;
  float anchored_max_ = 0.0F;
  float pursuit_target_ = 0.0F;
  float last_seen_max_ = 0.0F;
  int anchor_budget_ = 0;
  int settle_frames_left_ = 0;
  bool initialized_ = false;
  bool follow_tail_ = true;
  bool snap_pending_ = false;
  bool smooth_pending_ = false;
  bool align_pending_ = false;
  bool settling_ = false;
  bool pointer_down_ = false;
};

} // namespace

const huxerui::detail::ModifierDescriptor &ChatTailScroll::Descriptor() {
  return huxerui::detail::ModifierDescriptorFor<ChatTailScroll,
                                                 ChatTailScrollExtension>();
}

} // namespace linecode::presentation
