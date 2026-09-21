#include "presentation/components/chat_tail_scroll.h"

#include <algorithm>
#include <utility>

namespace linecode::presentation {
namespace {

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
      snap_pending_ = true;
      return;
    }

    const bool conversation_changed = conversation_id_ != value.conversation_id;
    const bool content_changed = content_revision_ != value.content_revision;
    const bool smooth_requested = smooth_request_ != value.smooth_request;
    conversation_id_ = value.conversation_id;
    content_revision_ = value.content_revision;
    smooth_request_ = value.smooth_request;

    if (conversation_changed) {
      follow_tail_ = true;
      smooth_pending_ = false;
      snap_pending_ = true;
      motion_.Set(controller_.Offset());
    }
    if (smooth_requested) {
      follow_tail_ = true;
      snap_pending_ = false;
      smooth_pending_ = true;
    } else if (content_changed && follow_tail_) {
      snap_pending_ = true;
    }
  }

  FrameResult OnFrame(huxerui::ViewNode &,
                      const huxerui::FrameInfo &frame) override {
    if (!controller_.IsConnected())
      return {};

    if (smooth_pending_) {
      smooth_pending_ = false;
      motion_.Set(controller_.Offset());
      motion_.AnimateTo(
          controller_.MaxOffset(),
          huxerui::TweenSpec{.duration = 0.180,
                             .easing = huxerui::Easing::EaseOut});
    }
    if (snap_pending_) {
      snap_pending_ = false;
      motion_.Set(controller_.MaxOffset());
      static_cast<void>(controller_.ScrollTo(controller_.MaxOffset()));
      return {};
    }
    if (!motion_.IsRunning())
      return {};

    const auto advanced = motion_.Advance(frame);
    static_cast<void>(controller_.ScrollTo(
        std::clamp(motion_.Value(), 0.0F, controller_.MaxOffset())));
    return {.needs_frame = advanced.needs_frame,
            .wake_after = advanced.wake_after};
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

  void PauseFollowing() {
    follow_tail_ = false;
    snap_pending_ = false;
    smooth_pending_ = false;
    motion_.Set(controller_.Offset());
  }

  huxerui::ScrollController controller_;
  huxerui::MotionController motion_;
  std::string conversation_id_;
  std::size_t content_revision_ = 0;
  std::uint64_t smooth_request_ = 0;
  bool initialized_ = false;
  bool follow_tail_ = true;
  bool snap_pending_ = false;
  bool smooth_pending_ = false;
};

} // namespace

const huxerui::detail::ModifierDescriptor &ChatTailScroll::Descriptor() {
  return huxerui::detail::ModifierDescriptorFor<ChatTailScroll,
                                                 ChatTailScrollExtension>();
}

} // namespace linecode::presentation
