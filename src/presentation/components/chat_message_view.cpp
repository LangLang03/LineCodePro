#include "presentation/components/chat_message_view.h"

#include "presentation/components/chat_reasoning_view.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <numbers>
#include <ranges>
#include <string>
#include <utility>

#include <app_resources.h>

#include "domain/compaction_progress.h"
#include "presentation/chat_timeline_presentation.h"
#include "presentation/compaction_progress_presentation.h"
#include "presentation/line_theme.h"
#include "presentation/working_status_presentation.h"

namespace linecode::presentation::chat_message {

using namespace huxerui;
using chat_timeline::AssistantMarkdown;
using chat_timeline::AssistantTimeline;
using chat_timeline::ChangedFilesBlock;
using chat_timeline::CompactProgressBlock;
using chat_timeline::HasAssistantTurnProcess;
using chat_timeline::ReasoningTimelineBlock;

TextStyle ChatTextStyle(float size, FontWeight weight = FontWeight::Regular,
                        Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

// `UserMessageView.onMeasure()` caps the complete bubble, including its own
// padding, at 90% of the message row's available width. A fixed Frame width
// cannot preserve that rule across phones, tablets, and desktop windows.
class LegacyUserBubbleWidth final : public Layout<LegacyUserBubbleWidth> {
public:
  using Layout::Layout;

  static LayoutResult Measure(LayoutContext &context, ViewNode &node,
                              Constraints constraints) {
    LayoutResult result;
    if (node.ChildCount() == 0)
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));

    Constraints bubble_constraints = constraints.Loose();
    if (constraints.HasBoundedWidth())
      bubble_constraints.max_width = constraints.max_width * 0.90F;
    auto &bubble = node.ChildAt(0);
    const Size size = context.Measure(bubble, bubble_constraints);
    return result.Place(bubble, {}).SetSize(constraints.Constrain(size));
  }
};

View MessageActionButton(ImageResource icon, std::function<void()> action) {
  return Stack{
      Image(std::move(icon))
          .Tint(colors::tertiary)
          .With(Frame{.width = 16.0F, .height = 16.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 40.0F, .height = 44.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View MessageActionBar(const domain::ChatMessage &message,
                      const ActionCallbacks &callbacks) {
  const bool user = message.role == domain::MessageRole::user;
  std::vector<View> actions{
      MessageActionButton(app::images::copy,
                          [callbacks, message] {
                            if (callbacks.copy)
                              callbacks.copy(message);
                          }),
      MessageActionButton(app::images::quote,
                          [callbacks, message] {
                            if (callbacks.quote)
                              callbacks.quote(message);
                          }),
      MessageActionButton(app::images::share_2,
                          [callbacks, message] {
                            if (callbacks.share)
                              callbacks.share(message);
                          }),
      MessageActionButton(app::images::text_cursor,
                          [callbacks, message] {
                            if (callbacks.select_text)
                              callbacks.select_text(message);
                          }),
      MessageActionButton(app::images::check_square,
                          [callbacks] {
                            if (callbacks.enter_multi_select)
                              callbacks.enter_multi_select();
                          }),
  };
  if (user) {
    actions.push_back(
        MessageActionButton(app::images::rotate_ccw, [callbacks, message] {
          if (callbacks.recall)
            callbacks.recall(message);
        }));
  }
  View bar = Row(std::move(actions))
                 .With(Frame{.height = 44.0F}, Spacing(4.0F),
                       CrossAlign(CrossAxisAlignment::Center));
  View aligned = user ? View{Row{Spacer(), bar}} : View{Row{bar, Spacer()}};
  return std::move(aligned).With(
      Padding(EdgeInsets{.top = 3.0F, .right = 4.0F}));
}

View MessageAttachments(const domain::ChatMessage &message, bool user,
                        float top_padding) {
  std::vector<View> chips;
  chips.reserve(message.attachments.size());
  for (const auto &attachment : message.attachments) {
    chips.push_back(
        Text(attachment.Name())
            .Style(ChatTextStyle(11.0F, FontWeight::Medium, colors::secondary))
            .With(Frame{.max_width = 220.0F},
                  Padding(EdgeInsets::Symmetric(8.0F, 4.0F)),
                  Background(colors::surface_light),
                  Border(colors::border_light, 1.0F), CornerRadius(14.0F),
                  ClipChildren())
            .Key(attachment.Source() + ":" + attachment.Path()));
  }
  if (chips.empty())
    return Stack{}.With(Frame{.height = 0.0F});
  View column =
      Column(std::move(chips))
          .With(Spacing(4.0F), user ? CrossAlign(CrossAxisAlignment::End)
                                    : CrossAlign(CrossAxisAlignment::Start));
  View aligned =
      user ? View{Row{Spacer(), column}} : View{Row{column, Spacer()}};
  return std::move(aligned).With(Padding(EdgeInsets{.top = top_padding}));
}

void ToggleMessageSelection(State<std::vector<std::uint64_t>> selected,
                            std::uint64_t id) {
  selected.Update([id](auto &ids) {
    const auto found = std::ranges::find(ids, id);
    if (found == ids.end())
      ids.push_back(id);
    else
      ids.erase(found);
  });
}

View MessageLongPressTarget(View content,
                            State<std::optional<std::uint64_t>> action_message,
                            bool multi_select,
                            State<std::vector<std::uint64_t>> selected_messages,
                            std::uint64_t message_id) {
  return std::move(content)
      .On<LongPressEvents::Started>([action_message, multi_select,
                                     selected_messages,
                                     message_id](const LongPressEvent &) {
        if (multi_select) {
          ToggleMessageSelection(selected_messages, message_id);
          return;
        }
        action_message = action_message.Get() == message_id
                             ? std::optional<std::uint64_t>{}
                             : std::optional<std::uint64_t>{message_id};
      })
      .With(LongPressGesture{});
}

struct WorkingStatusAnimation final {
  class Extension;

  std::string label;
  std::string locale;
  float text_width{};
  Color accent;
  Color secondary;

  bool operator==(const WorkingStatusAnimation &) const = default;
};

class WorkingStatusAnimation::Extension final : public NodeExtension {
public:
  Extension(ViewNode &node, const WorkingStatusAnimation &value) {
    Update(node, value);
  }

  void Update(ViewNode &, const WorkingStatusAnimation &value) {
    if (label_ == value.label && locale_ == value.locale &&
        text_width_ == value.text_width && accent_ == value.accent &&
        secondary_ == value.secondary)
      return;
    label_ = value.label;
    locale_ = value.locale;
    text_width_ = value.text_width;
    accent_ = value.accent;
    secondary_ = value.secondary;
    InvalidatePaint();
  }

  FrameResult OnFrame(ViewNode &, const FrameInfo &frame) override {
    if (frame.reduced_motion) {
      start_timestamp_.reset();
      if (progress_ != 0.0F) {
        progress_ = 0.0F;
        InvalidatePaint();
      }
      return {};
    }

    if (!start_timestamp_)
      start_timestamp_ = frame.timestamp;
    constexpr double kCycleSeconds =
        static_cast<double>(working_status_cycle_millis) / 1'000.0;
    const double elapsed = std::max(0.0, frame.timestamp - *start_timestamp_);
    const float progress =
        static_cast<float>(std::fmod(elapsed, kCycleSeconds) / kCycleSeconds);
    if (progress_ != progress) {
      progress_ = progress;
      InvalidatePaint();
    }
    return {.needs_frame = true};
  }

  void PaintAboveContent(const ViewNode &node,
                         PaintContext &paint) const override {
    constexpr float kMatrixSize = 16.0F;
    constexpr float kStep = kMatrixSize / 3.0F;
    constexpr float kRadius = 1.35F;
    constexpr float kHorizontalPadding = 1.0F;
    constexpr float kShimmerStripWidth = 2.0F;
    constexpr std::array<float, 3> kDotAlpha{0.18F, 0.52F, 1.0F};

    const auto frame = PresentWorkingStatusFrame(progress_, text_width_);
    const Rect bounds = node.Bounds();
    const float matrix_top = bounds.y + (bounds.height - kMatrixSize) / 2.0F;
    std::array<float, 9> dot_alpha;
    dot_alpha.fill(kDotAlpha[0]);
    dot_alpha[static_cast<std::size_t>(frame.trailing_dot)] = kDotAlpha[1];
    dot_alpha[static_cast<std::size_t>(frame.active_dot)] = kDotAlpha[2];
    for (int row = 0; row < 3; ++row) {
      for (int column = 0; column < 3; ++column) {
        const auto offset = static_cast<std::size_t>(row * 3 + column);
        Color dot = accent_;
        dot.alpha *= dot_alpha[offset];
        paint.DrawCircle(
            {bounds.x + kHorizontalPadding +
                 kStep * (static_cast<float>(column) + 0.5F),
             matrix_top + kStep * (static_cast<float>(row) + 0.5F)},
            kRadius, dot);
      }
    }

    if (label_.empty() || text_width_ <= 0.0F)
      return;
    const Rect text_rect{
        .x = bounds.x + working_status_text_start,
        .y = bounds.y,
        .width = text_width_,
        .height = bounds.height,
    };
    const TextLayoutOptions options{
        .shaping = {.locale = locale_},
        .align = TextAlign::Leading,
        .vertical_align = TextVerticalAlign::Center,
        .wrap = TextWrap::NoWrap,
    };
    const float shimmer_center = bounds.x + frame.shimmer_center;
    const Rect shimmer_rect{
        .x = shimmer_center - working_status_shimmer_half_width,
        .y = bounds.y,
        .width = working_status_shimmer_half_width * 2.0F,
        .height = bounds.height,
    };
    const Rect visible_shimmer = text_rect.Intersection(shimmer_rect);
    if (visible_shimmer.IsEmpty())
      return;

    const Color highlight{
        .red = std::lerp(secondary_.red, 1.0F, 0.72F),
        .green = std::lerp(secondary_.green, 1.0F, 0.72F),
        .blue = std::lerp(secondary_.blue, 1.0F, 0.72F),
        .alpha = secondary_.alpha,
    };
    const float shimmer_end = visible_shimmer.x + visible_shimmer.width;
    for (float strip_x = visible_shimmer.x; strip_x < shimmer_end;
         strip_x += kShimmerStripWidth) {
      const float strip_width =
          std::min(kShimmerStripWidth, shimmer_end - strip_x);
      const float sample_x = strip_x + strip_width / 2.0F;
      const float intensity =
          std::clamp(1.0F - std::abs(sample_x - shimmer_center) /
                                working_status_shimmer_half_width,
                     0.0F, 1.0F);
      const Color strip_color{
          .red = std::lerp(secondary_.red, highlight.red, intensity),
          .green = std::lerp(secondary_.green, highlight.green, intensity),
          .blue = std::lerp(secondary_.blue, highlight.blue, intensity),
          .alpha = secondary_.alpha,
      };
      paint.PushClip(Rect{.x = strip_x,
                          .y = bounds.y,
                          .width = strip_width,
                          .height = bounds.height});
      paint.DrawText(text_rect, label_,
                     TextStyle{Font::Monospace(13.0F), strip_color}, options);
      paint.PopClip();
    }
  }

private:
  std::string label_;
  std::string locale_;
  float text_width_{};
  Color accent_;
  Color secondary_;
  float progress_{};
  std::optional<double> start_timestamp_;
};

[[huxerui::composable]] View WorkingStatus(bool thinking) {
  const std::string label =
      UseString(thinking ? app::strings::chat_assistant_thinking
                         : app::strings::chat_assistant_working);
  const auto &locale = UseEnvironment<Locale>();
  const TextStyle style{Font::Monospace(13.0F), colors::secondary};
  const auto metrics = UseTextMeasurer().MeasureRun(
      label, style, {.locale = std::string(locale.LanguageTag())});
  return Row{
      Stack{}.With(Frame{.width = 16.0F, .height = 16.0F}),
      Text(label).Style(style),
  }
      .With(Frame{.min_height = 24.0F},
            Padding(EdgeInsets::Symmetric(1.0F, 0.0F)), Spacing(8.0F),
            CrossAlign(CrossAxisAlignment::Center),
            WorkingStatusAnimation{
                .label = label,
                .locale = std::string(locale.LanguageTag()),
                .text_width = metrics.advance,
                .accent = colors::accent,
                .secondary = colors::secondary,
            });
}

View MessageBubble(const domain::ChatMessage &message,
                   State<std::optional<std::uint64_t>> action_message,
                   bool multi_select,
                   State<std::vector<std::uint64_t>> selected_messages,
                   ActionCallbacks callbacks,
                   const chat_timeline::Settings &timeline_settings,
                   State<std::vector<std::string>> toggled_timeline,
                   const TutorialMarkdownLinkHandler &on_link,
                   const TutorialMarkdownCopyHandler &on_copy,
                   const chat_timeline::ToolRendererContext &context,
                   std::string_view compact_label, bool live) {
  if (message.role == domain::MessageRole::tool)
    return Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  // `ConversationTimeline.build` skips hidden messages: a compaction summary
  // stays in the context but never renders as a user bubble.
  if (message.hidden)
    return Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  // A compaction progress block flushes the surrounding group and becomes a
  // block of its own (ui/model ConversationTimeline.java:95-99).
  if (IsCompactTimelineBlock(message))
    return CompactProgressBlock(message, std::string{compact_label});
  const bool user = message.role == domain::MessageRole::user;
  const bool assistant_turn = !user && HasAssistantTurnProcess(message);
  const auto stable_turn_id = message.processing_started_at > 0
                                  ? message.processing_started_at
                                  : static_cast<std::int64_t>(message.id);
  const bool selected =
      std::ranges::contains(selected_messages.Get(), message.id);
  const auto bubble_color = static_cast<Color>(colors::user_bubble);
  const float luminance = bubble_color.red * 0.2126F +
                          bubble_color.green * 0.7152F +
                          bubble_color.blue * 0.0722F;
  const Color user_text = luminance > 0.55F ? static_cast<Color>(colors::text)
                                            : Color::Rgb(237, 240, 242);
  View assistant_text =
      message.content.empty()
          ? Stack{}.With(Frame{.height = 0.0F})
          : AssistantMarkdown(message.content,
                              timeline_settings.code_wrap_enabled, on_link,
                              on_copy);
  if (!user && !live && !message.content.empty()) {
    assistant_text =
        MessageLongPressTarget(assistant_text, action_message, multi_select,
                               selected_messages, message.id);
  }
  View process_or_reasoning = Stack{}.With(Frame{.height = 0.0F});
  if (assistant_turn) {
    process_or_reasoning =
        AssistantTimeline(message, live, timeline_settings, toggled_timeline,
                          on_link, on_copy, context);
  } else if (!message.reasoning_content.empty()) {
    process_or_reasoning =
        ReasoningTimelineBlock(
            domain::AssistantReasoningEvent{.turn_index = 0,
                                            .text = message.reasoning_content},
            std::to_string(stable_turn_id) + ":plain-reasoning", live,
            timeline_settings.thinking_auto_expand,
            timeline_settings.thinking_scroll, toggled_timeline)
            // HuxerUI's disclosure header measures slightly tighter than the
            // legacy LinearLayout. The 14dp trailing gap reproduces the old
            // collapsed title-to-answer baseline distance on Android.
            .With(Padding(EdgeInsets{.bottom = 14.0F}));
  }
  View working = live && !assistant_turn
                     ? WorkingStatus(!message.reasoning_content.empty() &&
                                     message.content.empty())
                     : Stack{}.With(Frame{.height = 0.0F});
  View changed_files =
      assistant_turn
          ? ChangedFilesBlock(message, stable_turn_id, toggled_timeline,
                              on_link, on_copy, context)
          : Stack{}.With(Frame{.height = 0.0F});
  View assistant =
      Column{
          std::move(process_or_reasoning),
          std::move(assistant_text),
          std::move(working),
          std::move(changed_files),
      }
          .With(CrossAlign(CrossAxisAlignment::Stretch));
  std::string_view trimmed_user_content = message.content;
  while (!trimmed_user_content.empty() &&
         std::isspace(
             static_cast<unsigned char>(trimmed_user_content.front())) != 0)
    trimmed_user_content.remove_prefix(1);
  while (!trimmed_user_content.empty() &&
         std::isspace(
             static_cast<unsigned char>(trimmed_user_content.back())) != 0)
    trimmed_user_content.remove_suffix(1);
  const bool legacy_attachment_placeholder =
      !message.attachments.empty() &&
      (trimmed_user_content == "已附加文件" ||
       trimmed_user_content == "Attached files");
  const bool user_content_visible =
      !message.content.empty() && !legacy_attachment_placeholder;
  View bubble = std::move(assistant);
  if (user) {
    bubble = user_content_visible
                 ? View{LegacyUserBubbleWidth{
                       Text(message.content)
                           .With(FontSize(16.0F), Foreground(user_text),
                                 // Android TextView's 16sp metrics are 9px
                                 // shorter than HuxerUI's at 420dpi. 8.25dp
                                 // restores the legacy 101px bubble height.
                                 Padding(EdgeInsets::Symmetric(16.3F, 8.25F)),
                                 Background(colors::user_bubble),
                                 CornerRadius(18.0F))}}
                 : View{Stack{}.With(Frame{.width = 0.0F, .height = 0.0F})};
    if (!live && user_content_visible) {
      bubble = MessageLongPressTarget(bubble, action_message, multi_select,
                                      selected_messages, message.id);
    }
  }
  View aligned_bubble = user ? Row{Spacer(), bubble} : Row{bubble, Spacer()};
  std::vector<View> content{
      aligned_bubble,
      MessageAttachments(message, user, 4.0F),
  };
  if (!live && !multi_select && action_message.Get() == message.id)
    content.push_back(MessageActionBar(message, callbacks));

  auto result = Column(std::move(content));
  // The old Markdown TextView row has an extra measured slot even though its
  // declared outer bottom padding is 28dp. Compensate for the backend font
  // metrics so a plain user/assistant turn remains 435px tall at 420dpi.
  const bool persisted_plain_assistant = !user && !assistant_turn && !live;
  const float top_padding = user                        ? 16.0F
                            : persisted_plain_assistant ? 1.9F
                                                        : 0.0F;
  const float bottom_padding = user || assistant_turn      ? 32.0F
                               : persisted_plain_assistant ? 35.25F
                                                           : 28.0F;
  // The legacy transient streaming row is display-only. It cannot enter the
  // persisted-message action or multi-select state before generation finishes.
  if (live)
    return std::move(result).With(
        Padding(EdgeInsets{.top = top_padding,
                           .right = 16.0F,
                           .bottom = bottom_padding,
                           .left = 16.0F}),
        Background(selected ? colors::accent_muted : Color::Transparent()),
        Border(selected ? colors::border_light : Color::Transparent(),
               selected ? 1.0F : 0.0F),
        CornerRadius(selected ? 12.0F : 0.0F));

  return std::move(result)
      .OnClick([multi_select, selected_messages, id = message.id] {
        if (multi_select)
          ToggleMessageSelection(selected_messages, id);
      })
      .With(Padding(EdgeInsets{.top = top_padding,
                               .right = 16.0F,
                               .bottom = bottom_padding,
                               .left = 16.0F}),
            Background(selected ? colors::accent_muted : Color::Transparent()),
            Border(selected ? colors::border_light : Color::Transparent(),
                   selected ? 1.0F : 0.0F),
            CornerRadius(selected ? 12.0F : 0.0F));
}

} // namespace linecode::presentation::chat_message
