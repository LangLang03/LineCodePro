#include "presentation/components/chat_timeline_view.h"

#include <algorithm>
#include <array>
#include <iterator>
#include <ranges>
#include <span>
#include <utility>

#include <app_resources.h>

#include "application/agent_result_registry.h"
#include "domain/compaction_progress.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/chat_timeline_presentation.h"
#include "presentation/compaction_progress_presentation.h"
#include "presentation/components/chat_reasoning_view.h"
#include "presentation/line_theme.h"

namespace linecode::presentation::chat_timeline {
namespace {

using namespace huxerui;

template <class... Visitors> struct Overloaded final : Visitors... {
  using Visitors::operator()...;
};

TextStyle ChatTextStyle(float size, FontWeight weight = FontWeight::Regular,
                        Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

} // namespace

using namespace huxerui;

struct ToolStatusPolicy final {
  domain::ToolCallStatus status;
  StringResource label;
};

const std::array kToolStatusPolicies{
    ToolStatusPolicy{domain::ToolCallStatus::requested,
                     app::strings::chat_tool_requested},
    ToolStatusPolicy{domain::ToolCallStatus::awaiting_review,
                     app::strings::chat_tool_awaiting_review},
    ToolStatusPolicy{domain::ToolCallStatus::running,
                     app::strings::chat_tool_running},
    ToolStatusPolicy{domain::ToolCallStatus::completed,
                     app::strings::chat_tool_completed},
    ToolStatusPolicy{domain::ToolCallStatus::failed,
                     app::strings::chat_tool_failed},
    ToolStatusPolicy{domain::ToolCallStatus::rejected,
                     app::strings::chat_tool_rejected},
};

StringResource ToolStatusLabel(domain::ToolCallStatus status) {
  const auto found =
      std::ranges::find(kToolStatusPolicies, status, &ToolStatusPolicy::status);
  return found == kToolStatusPolicies.end() ? app::strings::chat_tool_failed
                                            : found->label;
}

struct TimelineIconPresentation final {
  ToolTimelineIconKind kind;
  ImageResource image;
};

const std::array kTimelineIcons{
    TimelineIconPresentation{ToolTimelineIconKind::file, app::images::file},
    TimelineIconPresentation{ToolTimelineIconKind::folder,
                             app::images::folder_open},
    TimelineIconPresentation{ToolTimelineIconKind::search, app::images::search},
    TimelineIconPresentation{ToolTimelineIconKind::globe, app::images::globe},
    TimelineIconPresentation{ToolTimelineIconKind::paintbrush,
                             app::images::paintbrush},
    TimelineIconPresentation{ToolTimelineIconKind::sparkles,
                             app::images::sparkles},
    TimelineIconPresentation{ToolTimelineIconKind::bot, app::images::bot},
    TimelineIconPresentation{ToolTimelineIconKind::book_open,
                             app::images::book_open},
};

ImageResource TimelineIcon(ToolTimelineIconKind kind) {
  const auto found =
      std::ranges::find(kTimelineIcons, kind, &TimelineIconPresentation::kind);
  return found == kTimelineIcons.end() ? app::images::file : found->image;
}

struct ReadStatusPresentation final {
  domain::ToolCallStatus status;
  StringResource summary;
};

const std::array kReadStatusPresentations{
    ReadStatusPresentation{domain::ToolCallStatus::requested,
                           app::strings::chat_tool_read_running},
    ReadStatusPresentation{domain::ToolCallStatus::awaiting_review,
                           app::strings::chat_tool_read_running},
    ReadStatusPresentation{domain::ToolCallStatus::running,
                           app::strings::chat_tool_read_running},
    ReadStatusPresentation{domain::ToolCallStatus::completed,
                           app::strings::chat_tool_read_completed},
    ReadStatusPresentation{domain::ToolCallStatus::failed,
                           app::strings::chat_tool_read_failed},
    ReadStatusPresentation{domain::ToolCallStatus::rejected,
                           app::strings::chat_tool_read_failed},
};

StringResource ReadStatusSummary(domain::ToolCallStatus status) {
  const auto found = std::ranges::find(kReadStatusPresentations, status,
                                       &ReadStatusPresentation::status);
  return found == kReadStatusPresentations.end()
             ? app::strings::chat_tool_read_failed
             : found->summary;
}

View LegacyReadHeader(const ToolTimelinePresentation &presentation) {
  const auto metrics = ToolTimelineMetrics(presentation.visual);
  const auto color = presentation.failed ? colors::danger : colors::secondary;
  return Row{
      Stack{Image(TimelineIcon(presentation.icon))
                .Tint(color)
                .With(Frame{.width = metrics.icon_width,
                            .height = metrics.icon_height})}
          .With(Frame{.width = metrics.icon_slot_width,
                      .height = metrics.icon_slot_height},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Text::Format(ReadStatusSummary(presentation.status), presentation.title)
          .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular, color))
          .With(Grow()),
  }
      .With(Frame{.min_height = metrics.header_height},
            Spacing(metrics.title_leading_margin),
            CrossAlign(CrossAxisAlignment::Center));
}

View LegacyToolHeader(const ToolTimelinePresentation &presentation,
                      ImageResource icon, bool expanded,
                      std::function<void()> toggle) {
  const auto metrics = ToolTimelineMetrics(presentation.visual);
  const auto color = presentation.failed ? colors::danger : colors::secondary;
  std::vector<View> children;
  children.reserve(presentation.expandable ? 5U : 4U);
  children.push_back(
      Stack{Image(std::move(icon))
                .Tint(color)
                .With(Frame{.width = metrics.icon_width,
                            .height = metrics.icon_height})}
          .With(Frame{.width = metrics.icon_slot_width,
                      .height = metrics.icon_slot_height},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)));
  children.push_back(Text(ToolStatusLabel(presentation.status))
                         .Style(ChatTextStyle(metrics.title_size,
                                              FontWeight::Regular, color)));
  children.push_back(
      Text(presentation.title)
          .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular, color))
          .With(Grow()));
  if (presentation.visual == ToolTimelineVisualKind::remove &&
      presentation.item_count > 0) {
    children.push_back(Text("· " + std::to_string(presentation.item_count))
                           .Style(ChatTextStyle(metrics.title_size,
                                                FontWeight::Regular, color)));
  }
  if (presentation.expandable) {
    children.push_back(Stack{
        Image(expanded ? app::images::chevron_down : app::images::chevron_right)
            .Tint(colors::secondary)
            .With(Frame{.width = 24.0F, .height = 14.0F})}
                           .With(Frame{.width = 24.0F, .height = 32.0F},
                                 Align(HorizontalAlignment::Center,
                                       VerticalAlignment::Center)));
  }
  View header = Row(std::move(children))
                    .With(Frame{.min_height = metrics.header_height},
                          Spacing(metrics.title_leading_margin),
                          CrossAlign(CrossAxisAlignment::Center));
  if (presentation.expandable) {
    header = std::move(header)
                 .OnClick(std::move(toggle))
                 .With(Focusable(), PointerCursor(PointerCursorKind::Hand));
  }
  return header;
}

View ToolCodeCard(std::string text,
                  const ToolTimelinePresentation &presentation,
                  float maximum_height) {
  const auto metrics = ToolTimelineMetrics(presentation.visual);
  return ScrollView(
             SelectionArea(
                 Text(std::move(text))
                     .Style(TextStyle{Font::Monospace(metrics.detail_text_size),
                                      presentation.failed ? colors::danger
                                                          : colors::secondary})
                     .With(Padding(EdgeInsets{
                         .top = metrics.detail_vertical_padding,
                         .right = metrics.detail_horizontal_padding,
                         .bottom = metrics.detail_vertical_padding,
                         .left = metrics.detail_horizontal_padding}))))
      .ScrollAxis(Axis::Vertical)
      .With(Frame{.max_height = maximum_height}, Background(colors::code),
            Border(colors::code_border, 1.0F),
            CornerRadius(metrics.detail_radius), ClipChildren(), ScrollBar());
}

View AssistantMarkdown(std::string_view markdown, bool code_wrap,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy);

// Loads the requested records and publishes them in one update, so the cards
// recompose once instead of per record.
Task<void> LoadDiffs(std::shared_ptr<application::DiffStore> store,
                     State<std::shared_ptr<DiffCache>> cache,
                     std::shared_ptr<std::set<std::string>> pending,
                     std::vector<std::string> wanted) {
  auto loaded = std::make_shared<DiffCache>(*cache.Get());
  bool changed = false;
  for (const auto &id : wanted) {
    auto record = co_await store->Find(id);
    if (!record) {
      loaded->insert_or_assign(
          id, DiffEntry{.review_message = record.error().message,
                        .available = false});
    } else if (*record) {
      loaded->insert_or_assign(
          id, DiffEntry{.lines = domain::CalculateDiffLines(
                            (*record)->old_content, (*record)->new_content),
                        .review_state = (*record)->EffectiveReviewState(),
                        .review_message = (*record)->review_message,
                        .created = (*record)->old_content.empty()});
    } else {
      // A completed lookup must not leave the card looking busy forever.  An
      // unavailable entry can still be retried when the user taps its row.
      loaded->insert_or_assign(id, DiffEntry{.available = false});
    }
    changed = true;
    pending->erase(id);
  }
  if (changed)
    cache = loaded;
}

View ToolTimelineCard(const domain::AssistantToolEvent &event, std::string key,
                      State<std::vector<std::string>> toggled,
                      const TutorialMarkdownLinkHandler &on_link,
                      const TutorialMarkdownCopyHandler &on_copy,
                      const ToolRendererContext &context);

View ThinkingDisclosureBlock(std::string_view text, StringVariant label,
                             std::string key, bool auto_expand, bool scrollable,
                             State<std::vector<std::string>> toggled);

View ShellToolRenderer(const ToolTimelinePresentation &presentation,
                       bool expanded, std::function<void()> toggle,
                       const TutorialMarkdownLinkHandler &,
                       const TutorialMarkdownCopyHandler &,
                       const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::terminal, expanded,
                                  std::move(toggle)));
  if (expanded && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 240.0F));
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

View ReadToolRenderer(const ToolTimelinePresentation &presentation, bool,
                      std::function<void()>,
                      const TutorialMarkdownLinkHandler &,
                      const TutorialMarkdownCopyHandler &,
                      const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyReadHeader(presentation));
  if (presentation.failed && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 240.0F));
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

// Legacy `DiffView`: a horizontally scrollable unified diff. Context lines
// around every change stay visible while distant unchanged regions collapse to
// a single gap marker, and rendering stops after 200 lines.
View DiffView(const domain::DiffLines &diff) {
  std::vector<bool> visible(diff.lines.size(), false);
  for (std::size_t index = 0; index < diff.lines.size(); ++index) {
    if (diff.lines[index].kind == domain::DiffLine::Kind::unchanged)
      continue;
    const auto from = index >= 3 ? index - 3 : 0;
    const auto to = std::min(diff.lines.size(), index + 4);
    for (auto around = from; around < to; ++around)
      visible[around] = true;
  }

  std::vector<View> rows;
  std::size_t displayed = 0;
  bool omitted = false;
  for (std::size_t index = 0; index < diff.lines.size(); ++index) {
    if (!visible[index] && diff.added + diff.removed > 0) {
      omitted = true;
      continue;
    }
    if (displayed >= 200) {
      rows.push_back(Text(StringVariant::Format(
                              app::strings::tool_call_diff_truncated,
                              static_cast<std::int64_t>(diff.lines.size())))
                         .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                              colors::tertiary))
                         .With(Padding(EdgeInsets{.top = 12.0F,
                                                  .right = 14.0F,
                                                  .bottom = 12.0F,
                                                  .left = 14.0F})));
      break;
    }
    if (omitted) {
      rows.push_back(
          Text("⋯")
              .Style(
                  ChatTextStyle(13.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{
                  .top = 4.0F, .right = 0.0F, .bottom = 4.0F, .left = 18.0F})));
      omitted = false;
    }
    const auto &line = diff.lines[index];
    const bool added = line.kind == domain::DiffLine::Kind::added;
    const bool removed = line.kind == domain::DiffLine::Kind::removed;
    const Color text_color = added     ? colors::diff_add_text
                             : removed ? colors::diff_delete_text
                                       : colors::secondary;
    rows.push_back(Row{
        Stack{}.With(Frame{.width = 3.0F}, Grow(),
                     Background(added     ? colors::success
                                : removed ? colors::danger
                                          : Color::Transparent())),
        Text(std::to_string(line.number))
            .Style(TextStyle{Font::Monospace(13.0F), text_color})
            .Align(TextAlign::Trailing)
            .With(Frame{.width = 42.0F}, Padding(EdgeInsets{.top = 3.0F,
                                                            .right = 10.0F,
                                                            .bottom = 3.0F,
                                                            .left = 2.0F})),
        Text(line.text)
            .Style(TextStyle{Font::Monospace(13.0F), text_color})
            .With(Padding(EdgeInsets{
                .top = 3.0F, .right = 14.0F, .bottom = 3.0F, .left = 4.0F})),
    }
                       .With(Frame{.min_height = 26.0F},
                             Background(added ? colors::diff_add_background
                                        : removed
                                            ? colors::diff_delete_background
                                            : Color::Transparent()),
                             CrossAlign(CrossAxisAlignment::Stretch)));
    if (!line.terminated) {
      rows.push_back(Text(app::strings::tool_call_diff_no_newline)
                         .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                              colors::tertiary))
                         .With(Padding(EdgeInsets{.top = 4.0F,
                                                  .right = 14.0F,
                                                  .bottom = 4.0F,
                                                  .left = 14.0F})));
    }
    ++displayed;
  }
  if (omitted) {
    rows.push_back(
        Text("⋯")
            .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(EdgeInsets{
                .top = 4.0F, .right = 0.0F, .bottom = 4.0F, .left = 18.0F})));
  }
  return ScrollView(Column(std::move(rows))
                        .With(CrossAlign(CrossAxisAlignment::Stretch)))
      .ScrollAxis(Axis::Horizontal);
}

View WriteToolRenderer(const ToolTimelinePresentation &presentation,
                       bool expanded, std::function<void()> toggle,
                       const TutorialMarkdownLinkHandler &,
                       const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context) {
  const DiffEntry *cached_entry = nullptr;
  if (context.diff_cache) {
    const auto found = context.diff_cache->find(presentation.diff_id);
    if (found != context.diff_cache->end())
      cached_entry = &found->second;
  }
  const bool load_finished = cached_entry != nullptr;
  const DiffEntry *entry =
      load_finished && cached_entry->available ? cached_entry : nullptr;
  // The store is authoritative: the transcript copy predates the decision.
  const std::string review_state =
      entry != nullptr && !entry->review_state.empty()
          ? entry->review_state
          : presentation.review_state;
  const std::string review_message =
      cached_entry != nullptr && !cached_entry->review_message.empty()
          ? cached_entry->review_message
          : presentation.review_message;
  const bool reverted = review_state == "rejected";
  const bool accepted = review_state == "accepted";
  const bool explicitly_pending = review_state == "pending";
  const bool awaiting_review =
      !presentation.diff_id.empty() && !reverted && !accepted;

  // Legacy `ToolCallWriteView.render()` keeps an empty review state reviewable,
  // but does not call it pending. Only the explicit "pending" marker owns the
  // "Needs confirmation" label; otherwise a new file reads "Created".
  StringResource status = app::strings::tool_call_write_done;
  if (presentation.running)
    status = app::strings::tool_call_status_running;
  else if (presentation.failed)
    status = app::strings::chat_tool_failed;
  else if (reverted)
    status = app::strings::tool_call_write_reverted;
  else if (explicitly_pending)
    status = app::strings::tool_call_status_pending_review;
  else if (entry != nullptr && entry->created)
    status = app::strings::tool_call_write_created;

  const auto diff_label = [entry](std::string prefix, std::string_view title) {
    std::vector<TextSpan> spans;
    spans.emplace_back(std::move(prefix) + std::string{title});
    if (entry != nullptr) {
      spans.push_back(TextSpan(" +" + std::to_string(entry->lines.added))
                          .Style(TextSpanStyle{.foreground = colors::success}));
      spans.push_back(TextSpan(" −" + std::to_string(entry->lines.removed))
                          .Style(TextSpanStyle{.foreground = colors::danger}));
    }
    return AttributedText(std::span<const TextSpan>(spans));
  };

  std::vector<View> rows;
  const auto metrics = ToolTimelineMetrics(presentation.visual);
  const auto header_color =
      presentation.failed ? colors::danger : colors::secondary;
  const auto diff_id = presentation.diff_id;
  const auto request_diff = context.on_request_diff;
  View header =
      Row{
          Stack{Image(app::images::file_pen_line)
                    .Tint(header_color)
                    .With(Frame{.width = metrics.icon_width,
                                .height = metrics.icon_height})}
              .With(Frame{.width = metrics.icon_slot_width,
                          .height = metrics.icon_slot_height},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
          Text(status).Style(ChatTextStyle(metrics.title_size,
                                           FontWeight::Regular, header_color)),
          Text(diff_label({}, presentation.title))
              .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular,
                                   header_color))
              .With(Grow()),
          Stack{Image(expanded ? app::images::chevron_down
                               : app::images::chevron_right)
                    .Tint(colors::secondary)
                    .With(Frame{.width = 24.0F, .height = 14.0F})}
              .With(Frame{.width = 24.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
      }
          .OnClick([toggle = std::move(toggle), diff_id, request_diff] {
            std::invoke(toggle);
            if (request_diff && !diff_id.empty())
              std::invoke(request_diff, diff_id);
          })
          .With(Frame{.min_height = metrics.header_height},
                Spacing(metrics.title_leading_margin),
                CrossAlign(CrossAxisAlignment::Center), Focusable(),
                PointerCursor(PointerCursorKind::Hand));
  rows.push_back(std::move(header));
  if (!expanded)
    return Column(std::move(rows))
        .With(CrossAlign(CrossAxisAlignment::Stretch));

  std::vector<View> detail_children;
  detail_children.push_back(Row{
      Text(diff_label({}, presentation.title))
          .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary)),
      Spacer(),
      Stack{Image(app::images::copy)
                .Tint(colors::secondary)
                .With(Frame{.width = 16.0F, .height = 16.0F})}
          .OnClick([on_copy, detail = presentation.detail] {
            if (!detail.empty())
              std::invoke(on_copy, detail);
          })
          .With(Frame{.width = 44.0F, .height = 44.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
                                .With(Padding(EdgeInsets{.left = 12.0F}),
                                      CrossAlign(CrossAxisAlignment::Center)));

  if (entry != nullptr) {
    detail_children.push_back(
        ScrollView(DiffView(entry->lines))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 224.0F}, ScrollBar()));
  } else {
    detail_children.push_back(
        Text(presentation.diff_id.empty() || load_finished
                 ? app::strings::tool_call_diff_unavailable
                 : app::strings::tool_call_diff_loading)
            .Style(ChatTextStyle(12.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(EdgeInsets{.top = 10.0F,
                                     .right = 14.0F,
                                     .bottom = 10.0F,
                                     .left = 14.0F})));
  }

  const std::string message =
      presentation.failed ? presentation.detail : review_message;
  if (!message.empty()) {
    detail_children.push_back(
        Text(message)
            .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                 presentation.failed ? colors::danger
                                                     : colors::secondary))
            .With(Padding(EdgeInsets{.top = 10.0F,
                                     .right = 14.0F,
                                     .bottom = 10.0F,
                                     .left = 14.0F})));
  }

  if (awaiting_review && context.on_review) {
    const auto diff_id = presentation.diff_id;
    const auto call_id = presentation.tool_call_id;
    const auto on_review = context.on_review;
    detail_children.push_back(Row{
        Text(app::strings::tool_call_write_revert)
            .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::text))
            .Align(TextAlign::Center)
            .OnClick([on_review, call_id, diff_id] {
              std::invoke(on_review, call_id, diff_id, std::string{"rejected"});
            })
            .With(Frame{.min_height = 48.0F},
                  Padding(EdgeInsets::Symmetric(14.0F, 0.0F)), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
        Text(app::strings::tool_call_write_accept)
            .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::text))
            .Align(TextAlign::Center)
            .OnClick([on_review, call_id, diff_id] {
              std::invoke(on_review, call_id, diff_id, std::string{"accepted"});
            })
            .With(Frame{.min_height = 48.0F},
                  Padding(EdgeInsets::Symmetric(14.0F, 0.0F)), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
    }
                                  .With(MainAlign(MainAxisAlignment::End),
                                        CrossAlign(CrossAxisAlignment::Center),
                                        Padding(EdgeInsets{.top = 6.0F,
                                                           .right = 8.0F,
                                                           .bottom = 6.0F,
                                                           .left = 8.0F})));
  }

  rows.push_back(Column(std::move(detail_children))
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::code),
                           Border(colors::code_border, 1.0F),
                           CornerRadius(12.0F), ClipChildren()));
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

View DeleteToolRenderer(const ToolTimelinePresentation &presentation,
                        bool expanded, std::function<void()> toggle,
                        const TutorialMarkdownLinkHandler &,
                        const TutorialMarkdownCopyHandler &,
                        const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::trash_2, expanded,
                                  std::move(toggle)));
  if (expanded && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 200.0F));
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

View TodoIndicator(ToolTimelineTodoItem::State state) {
  using State = ToolTimelineTodoItem::State;
  if (state == State::completed) {
    return Image(app::images::check)
        .Tint(colors::success)
        .With(Frame{.width = 14.0F, .height = 14.0F});
  }
  return Stack{}.With(Frame{.width = 14.0F, .height = 14.0F},
                      Border(state == State::in_progress
                                 ? static_cast<Color>(colors::accent)
                                 : static_cast<Color>(colors::tertiary),
                             2.0F),
                      CornerRadius(7.0F));
}

View TodoToolRenderer(const ToolTimelinePresentation &presentation, bool,
                      std::function<void()>,
                      const TutorialMarkdownLinkHandler &,
                      const TutorialMarkdownCopyHandler &,
                      const ToolRendererContext &context) {
  if (presentation.failed && !presentation.detail.empty())
    return ToolCodeCard(presentation.detail, presentation, 240.0F);
  std::vector<View> rows;
  rows.reserve(presentation.todo_items.size());
  for (const auto &item : presentation.todo_items) {
    TextStyle style =
        ChatTextStyle(14.0F, FontWeight::Regular,
                      item.state == ToolTimelineTodoItem::State::completed
                          ? static_cast<Color>(colors::tertiary)
                          : static_cast<Color>(colors::text));
    if (item.state == ToolTimelineTodoItem::State::completed)
      style.decoration = TextDecoration::StrikeThrough;
    rows.push_back(
        Row{TodoIndicator(item.state), Text(item.content).Style(style)}.With(
            Frame{.min_height = 44.0F}, Spacing(8.0F),
            Padding(EdgeInsets::Symmetric(0.0F, 4.0F))));
  }
  if (rows.empty()) {
    rows.push_back(
        Text(app::strings::toolcall_preview_todo_empty)
            .Style(ChatTextStyle(12.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(EdgeInsets{
                .top = 8.0F, .right = 16.0F, .bottom = 16.0F, .left = 16.0F})));
  }
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Padding(EdgeInsets::Symmetric(0.0F, 12.0F)));
}

enum class AgentTone : std::uint8_t { accent, warning, success, danger, muted };

Color AgentToneColor(AgentTone tone) {
  const std::array colors_by_tone{
      static_cast<Color>(colors::accent), static_cast<Color>(colors::warning),
      static_cast<Color>(colors::success), static_cast<Color>(colors::danger),
      static_cast<Color>(colors::tertiary)};
  return colors_by_tone[std::to_underlying(tone)];
}

struct AgentTypePolicy final {
  std::string_view id;
  StringResource label;
  AgentTone tone;
};

const AgentTypePolicy &AgentTypePolicyFor(std::string_view type) {
  static const std::array policies{
      AgentTypePolicy{"explore", app::strings::tool_call_agent_type_explore,
                      AgentTone::accent},
      AgentTypePolicy{"sub-coding", app::strings::tool_call_agent_type_coding,
                      AgentTone::danger},
      AgentTypePolicy{"sub_coding", app::strings::tool_call_agent_type_coding,
                      AgentTone::danger},
      AgentTypePolicy{"subcoding", app::strings::tool_call_agent_type_coding,
                      AgentTone::danger},
      AgentTypePolicy{"coding", app::strings::tool_call_agent_type_coding,
                      AgentTone::danger},
  };
  const auto found = std::ranges::find(policies, type, &AgentTypePolicy::id);
  // Legacy normalizes the known coding aliases and treats any other non-empty
  // type as coding; only an absent type is defaulted to explore upstream.
  return found == policies.end() ? policies[1] : *found;
}

View AgentPill(StringVariant label, Color tint, Color background) {
  return Text(std::move(label))
      .Style(ChatTextStyle(10.0F, FontWeight::Bold, tint))
      .With(Padding(EdgeInsets::Symmetric(4.0F, 1.0F)), Background(background),
            Border(colors::code_border, 1.0F), CornerRadius(999.0F));
}

struct AgentStatusPolicy final {
  domain::ToolCallStatus status;
  StringResource label;
  ImageResource icon;
  AgentTone tone;
  bool progress{};
};

const AgentStatusPolicy &AgentStatusPolicyFor(domain::ToolCallStatus status) {
  static const std::array policies{
      AgentStatusPolicy{domain::ToolCallStatus::requested,
                        app::strings::tool_call_status_running,
                        app::images::clock_3, AgentTone::accent, true},
      AgentStatusPolicy{domain::ToolCallStatus::awaiting_review,
                        app::strings::tool_call_status_pending_review,
                        app::images::clock_3, AgentTone::warning, false},
      AgentStatusPolicy{domain::ToolCallStatus::running,
                        app::strings::tool_call_status_running,
                        app::images::clock_3, AgentTone::accent, true},
      AgentStatusPolicy{domain::ToolCallStatus::completed,
                        app::strings::tool_call_status_done, app::images::check,
                        AgentTone::success, false},
      AgentStatusPolicy{domain::ToolCallStatus::failed,
                        app::strings::tool_call_status_failed, app::images::x,
                        AgentTone::danger, false},
      AgentStatusPolicy{domain::ToolCallStatus::rejected,
                        app::strings::tool_call_status_failed, app::images::x,
                        AgentTone::danger, false},
  };
  const auto found =
      std::ranges::find(policies, status, &AgentStatusPolicy::status);
  return found == policies.end() ? policies.front() : *found;
}

View AgentStatus(const AgentStatusPolicy &policy, bool expanded,
                 float icon_size = 18.0F, float chevron_size = 16.0F) {
  const Color tint = AgentToneColor(policy.tone);
  View icon =
      policy.progress
          ? View{ProgressCircle().With(
                Frame{.width = icon_size, .height = icon_size})}
          : View{Image(policy.icon)
                     .Tint(tint)
                     .With(Frame{.width = icon_size, .height = icon_size})};
  return Row{
      std::move(icon),
      Text(policy.label).Style(ChatTextStyle(11.0F, FontWeight::Bold, tint)),
      Image(expanded ? app::images::chevron_down : app::images::chevron_right)
          .Tint(colors::tertiary)
          .With(Frame{.width = chevron_size, .height = 12.0F}),
  }
      .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Center));
}

View AgentToolRenderer(const ToolTimelinePresentation &presentation,
                       bool expanded, std::function<void()> toggle,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context) {
  const auto &type = AgentTypePolicyFor(presentation.auxiliary);
  const Color type_color = AgentToneColor(type.tone);
  const auto &status = AgentStatusPolicyFor(presentation.status);

  std::vector<View> metadata;
  metadata.push_back(AgentPill(type.label, type_color, colors::code));
  if (!presentation.agent_id.empty())
    metadata.push_back(AgentPill(presentation.agent_id, colors::tertiary,
                                 colors::surface_light));
  if (presentation.tool_call_count > 0) {
    metadata.push_back(AgentPill(
        StringVariant::Format(app::strings::tool_call_agent_tool_count,
                              presentation.tool_call_count),
        colors::tertiary, colors::surface_light));
  }

  std::vector<View> card;
  card.push_back(Row{
      Stack{Image(app::images::bot)
                .Tint(type_color)
                .With(Frame{.width = 14.0F, .height = 14.0F})}
          .With(Frame{.width = 28.0F, .height = 28.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Border(type_color, 1.0F), CornerRadius(14.0F)),
      Column{
          Text(presentation.title)
              .Style(ChatTextStyle(13.0F, FontWeight::Bold, colors::text)),
          // Legacy uses FlowLayoutView here so long agent ids and the
          // optional tool-count pill wrap instead of squeezing the status
          // controls off the card.
          Flow(std::move(metadata)).With(Spacing(4.0F)),
      }
          .With(Spacing(3.0F), Grow()),
      AgentStatus(status, expanded),
  }
                     .OnClick(std::move(toggle))
                     .With(Frame{.min_height = 48.0F}, Spacing(8.0F),
                           Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                           CrossAlign(CrossAxisAlignment::Center),
                           PointerCursor(PointerCursorKind::Hand)));

  if (expanded) {
    card.push_back(
        Stack{}.With(Frame{.height = 1.0F}, Background(colors::code_border)));
    std::vector<View> content;
    if (!presentation.input_detail.empty())
      content.push_back(ThinkingDisclosureBlock(
          presentation.input_detail,
          presentation.running
              ? StringVariant{app::strings::thinking_label}
              : StringVariant{app::strings::thinking_done_label},
          presentation.tool_call_id + ":agent-thinking", false, true,
          context.toggled_timeline));
    if (!presentation.output_detail.empty()) {
      content.push_back(AssistantMarkdown(presentation.output_detail, true,
                                          on_link, on_copy));
    }
    for (const auto &agent : presentation.agent_runs) {
      for (std::size_t index{}; index < agent.tool_calls.size(); ++index) {
        content.push_back(ToolTimelineCard(
            agent.tool_calls[index],
            presentation.tool_call_id + ":nested:" + agent.id + ":" +
                std::to_string(index),
            context.toggled_timeline, on_link, on_copy, context));
      }
    }
    if (content.empty()) {
      const StringResource empty_label =
          presentation.running
              ? app::strings::tool_call_agent_running
              : (presentation.failed ? app::strings::tool_call_agent_failed
                                     : app::strings::tool_call_agent_done);
      content.push_back(
          Text(empty_label)
              .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                   presentation.failed ? colors::danger
                                                       : colors::secondary)));
    }
    card.push_back(ScrollView(Column(std::move(content))
                                  .With(CrossAlign(CrossAxisAlignment::Stretch),
                                        Spacing(8.0F),
                                        Padding(EdgeInsets{.top = 8.0F,
                                                           .right = 12.0F,
                                                           .bottom = 12.0F,
                                                           .left = 12.0F})))
                       .ScrollAxis(Axis::Vertical)
                       .With(Frame{.max_height = 400.0F}, ScrollBar()));
  }
  return Column(std::move(card))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), ClipChildren());
}

struct AgentRunStatusPolicy final {
  std::string_view status;
  StringResource label;
  ImageResource icon;
  AgentTone tone;
  bool progress{};
};

const AgentRunStatusPolicy &AgentRunStatusPolicyFor(std::string_view status,
                                                    bool failed) {
  static const std::array policies{
      AgentRunStatusPolicy{"waiting",
                           app::strings::tool_call_pipeline_status_waiting,
                           app::images::clock_3, AgentTone::muted, false},
      AgentRunStatusPolicy{"requested",
                           app::strings::tool_call_pipeline_status_waiting,
                           app::images::clock_3, AgentTone::muted, false},
      AgentRunStatusPolicy{"running", app::strings::tool_call_status_running,
                           app::images::clock_3, AgentTone::accent, true},
      AgentRunStatusPolicy{"pending",
                           app::strings::tool_call_status_pending_review,
                           app::images::clock_3, AgentTone::warning, false},
      AgentRunStatusPolicy{"done", app::strings::tool_call_status_done,
                           app::images::check, AgentTone::success, false},
      AgentRunStatusPolicy{"error", app::strings::tool_call_status_failed,
                           app::images::x, AgentTone::danger, false},
  };
  const std::string_view effective =
      failed ? std::string_view{"error"} : status;
  const auto found =
      std::ranges::find(policies, effective, &AgentRunStatusPolicy::status);
  return found == policies.end() ? policies.front() : *found;
}

View PipelineSummaryItem(StringVariant label, ImageResource icon,
                         AgentTone tone) {
  const Color tint = AgentToneColor(tone);
  return Row{
      Image(std::move(icon))
          .Tint(tint)
          .With(Frame{.width = 10.0F, .height = 10.0F}),
      Text(std::move(label))
          .Style(ChatTextStyle(11.0F, FontWeight::Bold, tint)),
  }
      .With(Spacing(3.0F), CrossAlign(CrossAxisAlignment::Center));
}

View PipelineAgentRow(const AgentRunTimelinePresentation &agent,
                      std::string key,
                      const TutorialMarkdownLinkHandler &on_link,
                      const TutorialMarkdownCopyHandler &on_copy,
                      const ToolRendererContext &context) {
  const bool expanded = ToggleState(context.toggled_timeline.Get(), key, false);
  const auto &type = AgentTypePolicyFor(agent.type);
  const Color type_color = AgentToneColor(type.tone);
  const auto &status = AgentRunStatusPolicyFor(agent.status, agent.failed);
  const Color status_color = AgentToneColor(status.tone);

  std::vector<View> metadata;
  metadata.push_back(AgentPill(type.label, type_color, colors::code));
  if (!agent.id.empty())
    metadata.push_back(
        AgentPill(agent.id, colors::tertiary, colors::surface_light));
  for (const auto &dependency : agent.dependencies)
    metadata.push_back(
        AgentPill(dependency, colors::success, colors::surface_light));

  View status_icon =
      status.progress
          ? View{ProgressCircle().With(Frame{.width = 16.0F, .height = 16.0F})}
          : View{Image(status.icon)
                     .Tint(status_color)
                     .With(Frame{.width = 16.0F, .height = 16.0F})};
  std::vector<View> content;
  content.push_back(Row{
      Stack{Image(app::images::bot)
                .Tint(type_color)
                .With(Frame{.width = 12.0F, .height = 12.0F})}
          .With(Frame{.width = 24.0F, .height = 24.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Border(type_color, 1.0F), CornerRadius(12.0F)),
      Column{
          Text(agent.description.empty() ? agent.id : agent.description)
              .Style(ChatTextStyle(13.0F, FontWeight::Bold, colors::text)),
          Flow(std::move(metadata)).With(Spacing(4.0F)),
      }
          .With(Spacing(3.0F), Grow()),
      std::move(status_icon),
      Text(status.label)
          .Style(ChatTextStyle(11.0F, FontWeight::Bold, status_color)),
      Image(expanded ? app::images::chevron_down : app::images::chevron_right)
          .Tint(colors::tertiary)
          .With(Frame{.width = 14.0F, .height = 12.0F}),
  }
                        .OnClick([toggled = context.toggled_timeline, key] {
                          ToggleKey(toggled, key);
                        })
                        .With(Spacing(4.0F),
                              CrossAlign(CrossAxisAlignment::Center),
                              PointerCursor(PointerCursorKind::Hand)));
  if (expanded) {
    std::vector<View> details;
    if (!agent.thinking.empty())
      details.push_back(ThinkingDisclosureBlock(
          agent.thinking,
          agent.status == "done"
              ? StringVariant{app::strings::thinking_done_label}
              : StringVariant{app::strings::thinking_label},
          key + ":thinking", false, true, context.toggled_timeline));
    if (!agent.output.empty())
      details.push_back(
          AssistantMarkdown(agent.output, true, on_link, on_copy));
    for (std::size_t index{}; index < agent.tool_calls.size(); ++index) {
      details.push_back(ToolTimelineCard(
          agent.tool_calls[index], key + ":nested:" + std::to_string(index),
          context.toggled_timeline, on_link, on_copy, context));
    }
    if (!details.empty()) {
      content.push_back(
          ScrollView(Column(std::move(details))
                         .With(CrossAlign(CrossAxisAlignment::Stretch),
                               Spacing(8.0F), Padding(EdgeInsets{.top = 8.0F})))
              .ScrollAxis(Axis::Vertical)
              .With(Frame{.max_height = 280.0F}, ScrollBar()));
    }
  }
  return Column(std::move(content))
      .With(CrossAlign(CrossAxisAlignment::Stretch), Padding(8.0F),
            Background(colors::code), Border(colors::code_border, 1.0F),
            CornerRadius(8.0F));
}

View PipelineToolRenderer(const ToolTimelinePresentation &presentation,
                          bool expanded, std::function<void()> toggle,
                          const TutorialMarkdownLinkHandler &on_link,
                          const TutorialMarkdownCopyHandler &on_copy,
                          const ToolRendererContext &context) {
  const int pending = static_cast<int>(
      std::ranges::count(presentation.agent_runs, std::string{"pending"},
                         &AgentRunTimelinePresentation::status));
  const int waiting = std::max(
      0, presentation.item_count - presentation.completed_count -
             presentation.running_count - pending - presentation.failed_count);
  std::vector<View> summary;
  summary.push_back(PipelineSummaryItem(
      StringVariant::Format(app::strings::tool_call_pipeline_completed,
                            presentation.completed_count),
      app::images::check, AgentTone::success));
  if (presentation.running_count > 0)
    summary.push_back(PipelineSummaryItem(
        StringVariant::Format(app::strings::tool_call_pipeline_summary_running,
                              presentation.running_count),
        app::images::refresh_cw, AgentTone::accent));
  if (pending > 0)
    summary.push_back(PipelineSummaryItem(
        StringVariant::Format(
            app::strings::tool_call_pipeline_summary_pending_review, pending),
        app::images::clock_3, AgentTone::warning));
  if (waiting > 0)
    summary.push_back(PipelineSummaryItem(
        StringVariant::Format(app::strings::tool_call_pipeline_summary_waiting,
                              waiting),
        app::images::clock_3, AgentTone::muted));
  if (presentation.failed_count > 0)
    summary.push_back(PipelineSummaryItem(
        StringVariant::Format(app::strings::tool_call_pipeline_summary_failed,
                              presentation.failed_count),
        app::images::x, AgentTone::danger));

  const auto &status = AgentStatusPolicyFor(presentation.status);
  std::vector<View> card;
  card.push_back(Row{
      Stack{Image(app::images::git_branch)
                .Tint(colors::accent)
                .With(Frame{.width = 15.0F, .height = 15.0F})}
          .With(Frame{.width = 30.0F, .height = 30.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Border(colors::code_border, 1.0F), CornerRadius(15.0F)),
      Column{
          Text(app::strings::tool_call_pipeline_title)
              .Style(ChatTextStyle(13.0F, FontWeight::Bold, colors::text)),
          Flow(std::move(summary)).With(Spacing(8.0F)),
      }
          .With(Spacing(3.0F), Grow()),
      status.progress
          ? View{ProgressCircle().With(Frame{.width = 18.0F, .height = 18.0F})}
          : View{Image(status.icon)
                     .Tint(AgentToneColor(status.tone))
                     .With(Frame{.width = 18.0F, .height = 13.0F})},
      Image(expanded ? app::images::chevron_down : app::images::chevron_right)
          .Tint(colors::tertiary)
          .With(Frame{.width = 16.0F, .height = 12.0F}),
  }
                     .OnClick(std::move(toggle))
                     .With(Frame{.min_height = 48.0F}, Spacing(4.0F),
                           Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                           CrossAlign(CrossAxisAlignment::Center),
                           PointerCursor(PointerCursorKind::Hand)));
  if (expanded) {
    card.push_back(
        Stack{}.With(Frame{.height = 1.0F}, Background(colors::code_border)));
    std::vector<View> agents;
    agents.reserve(presentation.agent_runs.size());
    for (const auto &agent : presentation.agent_runs) {
      agents.push_back(PipelineAgentRow(
          agent, presentation.tool_call_id + ":pipeline:" + agent.id, on_link,
          on_copy, context));
    }
    if (agents.empty()) {
      if (!presentation.output_detail.empty())
        agents.push_back(AssistantMarkdown(presentation.output_detail, true,
                                           on_link, on_copy));
      else
        agents.push_back(Text(app::strings::tool_call_pipeline_running)
                             .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                                  colors::tertiary)));
    }
    card.push_back(Column(std::move(agents))
                       .With(CrossAlign(CrossAxisAlignment::Stretch),
                             Spacing(8.0F), Padding(8.0F)));
  }
  return Column(std::move(card))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), ClipChildren());
}

View GenericToolRenderer(const ToolTimelinePresentation &presentation,
                         bool expanded, std::function<void()> toggle,
                         const TutorialMarkdownLinkHandler &,
                         const TutorialMarkdownCopyHandler &,
                         const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::mcp, expanded,
                                  std::move(toggle)));
  if (expanded && (!presentation.input_detail.empty() ||
                   !presentation.output_detail.empty())) {
    std::vector<View> sections;
    // A resource key, not a literal: the heading is part of the card's
    // visible text (`sheet_title_input` / `sheet_title_output`).
    const auto add_section = [&](StringResource heading,
                                 const std::string &body, Color color) {
      if (body.empty())
        return;
      sections.push_back(Column{
          Text(std::move(heading))
              .Style(ChatTextStyle(11.0F, FontWeight::Bold, colors::tertiary)),
          SelectionArea(
              Text(body).Style(TextStyle{Font::Monospace(13.0F), color}))}
                             .With(CrossAlign(CrossAxisAlignment::Stretch),
                                   Spacing(4.0F),
                                   Padding(EdgeInsets{.top = 12.0F,
                                                      .right = 14.0F,
                                                      .bottom = 12.0F,
                                                      .left = 14.0F})));
    };
    add_section(app::strings::sheet_title_input, presentation.input_detail,
                colors::secondary);
    add_section(app::strings::sheet_title_output, presentation.output_detail,
                presentation.failed ? static_cast<Color>(colors::danger)
                                    : static_cast<Color>(colors::secondary));
    rows.push_back(
        ScrollView(Column(std::move(sections))
                       .With(CrossAlign(CrossAxisAlignment::Stretch)))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 240.0F}, Background(colors::code),
                  Border(colors::code_border, 1.0F), CornerRadius(12.0F),
                  ClipChildren(), ScrollBar()));
  }
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

using ToolRenderer = View (*)(const ToolTimelinePresentation &, bool,
                              std::function<void()>,
                              const TutorialMarkdownLinkHandler &,
                              const TutorialMarkdownCopyHandler &,
                              const ToolRendererContext &);

struct ToolRendererPolicy final {
  ToolTimelineVisualKind visual;
  ToolRenderer renderer;
};

const std::array kToolRendererPolicies{
    ToolRendererPolicy{ToolTimelineVisualKind::shell, &ShellToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::read, &ReadToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::write, &WriteToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::remove, &DeleteToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::todo, &TodoToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::agent, &AgentToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::agent_pipeline,
                       &PipelineToolRenderer},
    ToolRendererPolicy{ToolTimelineVisualKind::generic, &GenericToolRenderer},
};

ToolRenderer RendererFor(ToolTimelineVisualKind visual) {
  const auto found = std::ranges::find(kToolRendererPolicies, visual,
                                       &ToolRendererPolicy::visual);
  return found == kToolRendererPolicies.end() ? &GenericToolRenderer
                                              : found->renderer;
}

View AssistantMarkdown(std::string_view markdown, bool code_wrap,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy) {
  infrastructure::TutorialMarkdownParser parser;
  const auto document = parser.Parse(markdown);
  // Legacy MarkdownView is MATCH_PARENT inside the assistant bubble. A fixed
  // desktop-oriented cap here makes short Android replies wrap one line early.
  return TutorialMarkdownDocumentView(document, code_wrap, 1.0F, on_link,
                                      on_copy);
}

// Port of `cn.lineai.ui.component.ContextCompactBlockView`: one horizontal row
// with an archive icon, the "Compacting" label, a weight-1 spacer and either an
// indeterminate progress circle (running) or a status icon (done = CHECK,
// error = CLOSE). The geometry comes from `CompactBlockMetrics()` so the
// numbers stay next to the legacy references in the presentation header.
View CompactProgressBlock(const domain::ChatMessage &message,
                          const std::string &label) {
  const auto presentation = PresentCompactProgress(message);
  const auto metrics = CompactBlockMetricsDefault();
  const Color tint = presentation.danger ? static_cast<Color>(colors::danger)
                                         : static_cast<Color>(colors::tertiary);
  View status =
      presentation.show_progress_bar
          ? View{ProgressCircle().With(Frame{.width = metrics.progress_size,
                                             .height = metrics.progress_size})}
          : View{Image(presentation.status_icon == CompactStatusIcon::close
                           ? app::images::x
                           : app::images::check)
                     .Tint(tint)
                     .With(Frame{.width = metrics.icon_slot,
                                 .height = metrics.status_icon_size})};
  return Row{
      Stack{Image(app::images::archive)
                .Tint(tint)
                .With(Frame{.width = metrics.archive_icon_size,
                            .height = metrics.archive_icon_size})}
          .With(Frame{.width = metrics.icon_slot, .height = metrics.icon_slot},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Text(label).Style(
          ChatTextStyle(metrics.label_size, FontWeight::Regular, tint)),
      Spacer(),
      std::move(status),
  }
      .With(Frame{.min_height = metrics.min_height},
            Padding(EdgeInsets{.top = metrics.vertical_padding,
                               .bottom = metrics.vertical_padding}),
            Spacing(metrics.label_left_margin),
            CrossAlign(CrossAxisAlignment::Center));
}

View ToolTimelineCard(const domain::AssistantToolEvent &event, std::string key,
                      State<std::vector<std::string>> toggled,
                      const TutorialMarkdownLinkHandler &on_link,
                      const TutorialMarkdownCopyHandler &on_copy,
                      const ToolRendererContext &context) {
  const auto presentation =
      PresentToolTimeline(event, context.agent_results.get());
  if (!presentation.visible)
    return Stack{}.With(Frame{.height = 0.0F});
  const bool expanded =
      ToggleState(toggled.Get(), key, presentation.initially_expanded);
  return RendererFor(presentation.visual)(
      presentation, expanded,
      [toggled, key = std::move(key)] { ToggleKey(toggled, key); }, on_link,
      on_copy, context);
}

// Port of `AssistantTurnView.renderFiles()`: a collapsible "Edited N files"
// row listing every recorded change of the turn.
//
// Legacy showed it only when the turn both produced diffs and finished with an
// answer (`edits.isEmpty() || row.answer == null ? GONE : VISIBLE`), and it
// counted *distinct paths* while keeping one entry per diff id, so repeated
// edits to the same file stay individually reviewable.
View ChangedFilesBlock(const domain::ChatMessage &message,
                       std::int64_t stable_turn_id,
                       State<std::vector<std::string>> toggled,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context) {
  const auto hidden = Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  if (message.content.empty())
    return hidden;
  std::vector<const domain::AssistantToolEvent *> edits;
  std::vector<std::string> seen_ids;
  std::vector<std::string> paths;
  for (const auto &event : message.timeline) {
    const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
    if (tool == nullptr || !tool->result.has_value())
      continue;
    const auto &diff_id = tool->result->diff_id;
    if (diff_id.empty() || std::ranges::contains(seen_ids, diff_id))
      continue;
    seen_ids.push_back(diff_id);
    edits.push_back(tool);
    const auto path = ToolCallTargetPath(tool->call.arguments_json, diff_id);
    if (!std::ranges::contains(paths, path))
      paths.push_back(path);
  }
  if (edits.empty())
    return hidden;
  const auto block_key = std::to_string(stable_turn_id) + ":files";
  const bool expanded = ToggleState(toggled.Get(), block_key, false);
  auto header =
      Row{
          Stack{Image(app::images::file_pen_line)
                    .Tint(colors::secondary)
                    .With(Frame{.width = 18.0F, .height = 18.0F})}
              .With(Frame{.width = 26.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
          Text::Format(app::strings::chat_files_changed, paths.size())
              .Style(ChatTextStyle(14.0F, FontWeight::Regular, colors::text))
              .With(Padding(EdgeInsets{.left = 6.0F}), Grow()),
          Text(app::strings::chat_review_changes)
              .Style(ChatTextStyle(14.0F, FontWeight::Regular, colors::text))
              .VerticalAlign(TextVerticalAlign::Center)
              .OnClick([toggled, block_key, edit_count = edits.size()] {
                auto next = toggled.Get();
                if (!std::ranges::contains(next, block_key))
                  next.push_back(block_key);
                for (std::size_t index = 0; index < edit_count; ++index) {
                  const auto key = block_key + ":" + std::to_string(index);
                  if (!std::ranges::contains(next, key))
                    next.push_back(key);
                }
                toggled = std::move(next);
              })
              .With(Frame{.min_height = 48.0F},
                    Padding(EdgeInsets{.left = 12.0F}), Focusable(),
                    PointerCursor(PointerCursorKind::Hand)),
          Stack{Image(expanded ? app::images::chevron_down
                               : app::images::chevron_right)
                    .Tint(colors::tertiary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .With(Frame{.width = 24.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
      }
          .With(CrossAlign(CrossAxisAlignment::Center),
                Frame{.min_height = 64.0F})
          .OnClick([toggled, block_key] {
            auto next = toggled.Get();
            if (std::ranges::contains(next, block_key))
              std::erase(next, block_key);
            else
              next.push_back(block_key);
            toggled = std::move(next);
          })
          .With(PointerCursor(PointerCursorKind::Hand));
  std::vector<View> children;
  if (expanded) {
    for (std::size_t index = 0; index < edits.size(); ++index) {
      children.push_back(ToolTimelineCard(
          *edits[index], block_key + ":" + std::to_string(index), toggled,
          on_link, on_copy, context));
    }
  }
  View body =
      children.empty()
          ? View{Stack{}.With(Frame{.height = 0.0F})}
          : View{Column(std::move(children))
                     .With(Spacing(12.0F), Padding(EdgeInsets{.bottom = 12.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch))};
  return Column{Divider(), std::move(header), std::move(body)}.With(
      CrossAlign(CrossAxisAlignment::Stretch),
      Padding(EdgeInsets{.top = 24.0F}));
}

View ToolTimelineGroup(
    const std::vector<const domain::AssistantToolEvent *> &tools,
    const std::string &group_key, State<std::vector<std::string>> toggled,
    const TutorialMarkdownLinkHandler &on_link,
    const TutorialMarkdownCopyHandler &on_copy,
    const ToolRendererContext &context) {
  const bool expanded = ToggleState(toggled.Get(), group_key, false);
  View header =
      Row{
          Stack{Image(app::images::terminal)
                    .Tint(colors::secondary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .With(Frame{.width = 24.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
          Text::Format(app::strings::chat_tools_count, tools.size())
              .Style(
                  ChatTextStyle(14.0F, FontWeight::Regular, colors::secondary)),
          Stack{Image(expanded ? app::images::chevron_down
                               : app::images::chevron_right)
                    .Tint(colors::tertiary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .With(Frame{.width = 28.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
      }
          .OnClick([toggled, group_key] { ToggleKey(toggled, group_key); })
          .With(Frame{.min_height = 48.0F}, Spacing(6.0F),
                CrossAlign(CrossAxisAlignment::Center), Focusable(),
                PointerCursor(PointerCursorKind::Hand));
  std::vector<View> cards;
  if (expanded) {
    cards.reserve(tools.size());
    for (std::size_t index = 0; index < tools.size(); ++index) {
      cards.push_back(ToolTimelineCard(*tools[index],
                                       group_key + ":" + std::to_string(index),
                                       toggled, on_link, on_copy, context));
    }
  }
  auto card_list =
      Column(std::move(cards))
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch));
  if (expanded && !tools.empty())
    card_list = std::move(card_list).With(Padding(EdgeInsets{.bottom = 8.0F}));
  return Column{
      std::move(header),
      std::move(card_list),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View AssistantTimeline(const domain::ChatMessage &message, bool live,
                       const Settings &settings,
                       State<std::vector<std::string>> toggled,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context,
                       const std::string_view compact_label) {
  const auto presentation =
      PresentAssistantProcess(message, live, settings.process_auto_expand);
  if (!presentation.visible)
    return Stack{}.With(Frame{.height = 0.0F});
  const auto stable_turn_id = message.processing_started_at > 0
                                  ? message.processing_started_at
                                  : static_cast<std::int64_t>(message.id);
  const auto process_key = std::to_string(stable_turn_id) + ":process";
  const bool expanded =
      ToggleState(toggled.Get(), process_key, presentation.initially_expanded);
  // Legacy AssistantTurnView keeps the process label secondary-colored even
  // when a nested tool fails; the state text is pending / processing / done.
  const auto label = presentation.pending_review && presentation.running
                         ? app::strings::chat_process_pending
                     : presentation.running
                         ? app::strings::chat_process_working
                         : app::strings::chat_process_completed;
  std::string duration;
  if (presentation.duration_millis > 0)
    duration = " " + FormatProcessingDuration(presentation.duration_millis);
  View header = Row{
      Text(label).Style(
          ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary)),
      Text(duration).Style(
          ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary)),
      Stack{Image(expanded ? app::images::chevron_down
                           : app::images::chevron_right)
                .Tint(colors::tertiary)
                .With(Frame{.width = 16.0F, .height = 16.0F})}
          .With(Frame{.width = 28.0F, .height = 32.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
  };
  header =
      std::move(header)
          .OnClick([toggled, process_key] { ToggleKey(toggled, process_key); })
          .With(Frame{.min_height = 48.0F},
                CrossAlign(CrossAxisAlignment::Center),
                PointerCursor(PointerCursorKind::Hand));
  std::vector<View> process_rows;
  if (expanded) {
    for (std::size_t index = 0; index < message.timeline.size();) {
      const auto key =
          std::to_string(stable_turn_id) + ":" + std::to_string(index);
      if (const auto *tool = std::get_if<domain::AssistantToolEvent>(
              &message.timeline[index])) {
        const auto card = PresentToolTimeline(*tool);
        if (card.visual != ToolTimelineVisualKind::agent &&
            card.visual != ToolTimelineVisualKind::agent_pipeline) {
          std::vector<const domain::AssistantToolEvent *> tools;
          std::size_t cursor = index;
          for (; cursor < message.timeline.size(); ++cursor) {
            const auto *candidate = std::get_if<domain::AssistantToolEvent>(
                &message.timeline[cursor]);
            if (candidate == nullptr)
              break;
            const auto candidate_card = PresentToolTimeline(*candidate);
            if (candidate_card.visual == ToolTimelineVisualKind::agent ||
                candidate_card.visual == ToolTimelineVisualKind::agent_pipeline)
              break;
            tools.push_back(candidate);
          }
          process_rows.push_back(ToolTimelineGroup(
              tools, key + ":tools", toggled, on_link, on_copy, context));
          index = cursor;
          continue;
        }
      }
      std::visit(
          Overloaded{[&](const domain::AssistantReasoningEvent &reasoning) {
                       process_rows.push_back(ReasoningTimelineBlock(
                           reasoning, key, live, settings.thinking_auto_expand,
                           settings.thinking_scroll, toggled));
                     },
                     [&](const domain::AssistantTextEvent &text) {
                       if (!text.text.empty())
                         process_rows.push_back(AssistantMarkdown(
                             text.text, settings.code_wrap_enabled, on_link,
                             on_copy));
                     },
                     [&](const domain::AssistantCompactEvent &compact) {
                       process_rows.push_back(CompactProgressBlock(
                           domain::CompactProgressMessage(0U, compact.status),
                           std::string{compact_label}));
                     },
                     [&](const domain::AssistantToolEvent &tool) {
                       process_rows.push_back(ToolTimelineCard(
                           tool, key, toggled, on_link, on_copy, context));
                     }},
          message.timeline[index]);
      ++index;
    }
    if (message.timeline.empty() && !message.reasoning_content.empty()) {
      process_rows.push_back(ReasoningTimelineBlock(
          domain::AssistantReasoningEvent{.turn_index = 0,
                                          .text = message.reasoning_content},
          process_key + ":legacy", live, settings.thinking_auto_expand,
          settings.thinking_scroll, toggled));
    }
    if (message.error && !message.error_message.empty()) {
      // Legacy failGeneration stores the formatted failure as the final prose
      // block of the assistant process. It uses normal Markdown typography
      // and follows the disclosure state; it is not a second red banner below
      // the process section.
      process_rows.push_back(AssistantMarkdown(
          message.error_message, settings.code_wrap_enabled, on_link, on_copy));
    }
  }
  auto process_row_list =
      Column(std::move(process_rows))
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch));
  if (expanded && (!message.timeline.empty() || message.error))
    process_row_list =
        std::move(process_row_list).With(Padding(EdgeInsets{.bottom = 8.0F}));
  View process =
      Column{
          std::move(header),
          Column{Divider()}.With(Padding(EdgeInsets{.bottom = 20.0F})),
          std::move(process_row_list),
      }
          .With(CrossAlign(CrossAxisAlignment::Stretch));
  return process;
}

bool HasAssistantTurnProcess(const domain::ChatMessage &message) {
  if (message.error || message.retry_notice || !message.compact_status.empty())
    return true;
  return std::ranges::any_of(message.timeline, [](const auto &event) {
    return std::holds_alternative<domain::AssistantToolEvent>(event) ||
           std::holds_alternative<domain::AssistantCompactEvent>(event);
  });
}

} // namespace linecode::presentation::chat_timeline
