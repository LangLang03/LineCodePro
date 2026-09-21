#include "presentation/components/chat_screen.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cmath>
#include <functional>
#include <iterator>
#include <memory>
#include <numbers>
#include <optional>
#include <ranges>
#include <set>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/agent_result_registry.h"
#include "application/auto_compaction_service.h"
#include "application/behavior_settings_repository.h"
#include "application/chat_export.h"
#include "application/chat_session.h"
#include "application/composer_image_input.h"
#include "application/context_compaction.h"
#include "application/diff_review_service.h"
#include "application/generation_controller.h"
#include "application/mcp_completion_loop.h"
#include "application/mcp_execution_settings.h"
#include "application/memory_context_service.h"
#include "application/memory_conversation_snapshot.h"
#include "application/output_settings.h"
#include "application/pending_message_queue.h"
#include "application/ports/diff_store.h"
#include "application/ports/external_link.h"
#include "application/ports/model_store.h"
#include "application/ports/share_text.h"
#include "application/ports/storage_permission.h"
#include "application/ports/todo_state_store.h"
#include "application/prompt_request_composer.h"
#include "application/skill_repository.h"
#include "application/slash_command_catalog.h"
#include "application/token_usage_tracker.h"
#include "application/tool_permission_service.h"
#include "application/tool_review_coordinator.h"
#include "domain/compaction_progress.h"
#include "domain/context_usage.h"
#include "presentation/chat_timeline_presentation.h"
#include "presentation/compaction_progress_presentation.h"
#include "presentation/components/chat_composer.h"
#include "presentation/components/chat_generation_runner.h"
#include "presentation/components/chat_generation_state.h"
#include "presentation/components/chat_message_view.h"
#include "presentation/components/chat_overlays.h"
#include "presentation/components/chat_tail_scroll.h"
#include "presentation/components/chat_timeline_view.h"
#include "presentation/components/tool_approval_view.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"
#include "presentation/working_status_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

using ChatTimelineSettings = chat_timeline::Settings;
using ComposerImageSelection = chat_composer::ImageSelection;
using MessageActionCallbacks = chat_message::ActionCallbacks;
using chat_composer::Composer;
using chat_message::MessageBubble;
using chat_timeline::AssistantMarkdown;
using chat_timeline::AssistantTimeline;
using chat_timeline::ChangedFilesBlock;
using chat_timeline::CompactProgressBlock;
using chat_timeline::DiffCache;
using chat_timeline::HasAssistantTurnProcess;
using chat_timeline::LoadDiffs;
using chat_timeline::ToolRendererContext;

template <class... Visitors> struct Overloaded final : Visitors... {
  using Visitors::operator()...;
};

TextStyle ChatTextStyle(float size, FontWeight weight = FontWeight::Regular,
                        Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View HeaderAction(ImageResource icon, float icon_size,
                  std::function<void()> action) {
  return Stack{
      Image(std::move(icon))
          .Tint(colors::secondary)
          .With(Frame{.width = icon_size, .height = icon_size}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 40.0F, .height = 48.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View ScrollToBottomButton(std::function<void()> action) {
  return Stack{
      Image(app::images::chevron_down)
          .Tint(colors::text_on_color)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 44.0F, .height = 44.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::accent), Border(colors::accent, 1.0F),
            CornerRadius(22.0F),
            Shadow{.color = Color::Rgb(0, 0, 0, 0.24F), .blur_radius = 8.0F},
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

ChatPermissionMode
PresentPermissionMode(domain::ToolPermissionMode mode) noexcept {
  switch (mode) {
  case domain::ToolPermissionMode::automatic:
    return ChatPermissionMode::automatic;
  case domain::ToolPermissionMode::confirm:
    return ChatPermissionMode::confirm;
  case domain::ToolPermissionMode::read_only:
    return ChatPermissionMode::read_only;
  }
  return ChatPermissionMode::automatic;
}

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

ChatAttachmentNode ToAttachmentNode(const DrawerFileNode &node) {
  std::vector<ChatAttachmentNode> children;
  children.reserve(node.children.size());
  std::ranges::transform(node.children, std::back_inserter(children),
                         ToAttachmentNode);
  return ChatAttachmentNode{
      .name = node.name,
      .path = node.path,
      .directory = node.directory,
      .expanded = node.expanded,
      .children = std::move(children),
  };
}

void CollectExpandedDirectories(const ChatAttachmentNode &node,
                                std::vector<std::string> &paths) {
  if (node.directory && node.expanded) {
    paths.emplace_back(node.path);
  }
  for (const auto &child : node.children) {
    CollectExpandedDirectories(child, paths);
  }
}

void TogglePath(std::vector<std::string> &paths, std::string_view path) {
  const auto found = std::ranges::find(paths, path);
  if (found == paths.end()) {
    paths.emplace_back(path);
  } else {
    paths.erase(found);
  }
}

std::vector<std::string>
AttachmentPaths(std::span<const domain::InputAttachment> attachments) {
  std::vector<std::string> paths;
  paths.reserve(attachments.size());
  std::ranges::transform(attachments, std::back_inserter(paths),
                         [](const domain::InputAttachment &attachment) {
                           return attachment.Path();
                         });
  return paths;
}

void ToggleAttachment(std::vector<domain::InputAttachment> &attachments,
                      const ChatAttachmentFile &file) {
  const auto found = std::ranges::find_if(
      attachments, [&file](const domain::InputAttachment &attachment) {
        return attachment.Matches(file.path, file.source);
      });
  if (found == attachments.end()) {
    attachments.emplace_back(file.name, file.path, file.source);
  } else {
    attachments.erase(found);
  }
}

class ControlledBottomSheet final {
public:
  ControlledBottomSheet(BottomSheetHandle handle, State<bool> visible,
                        State<std::optional<LayerId>> layer)
      : handle_(std::move(handle)), visible_(visible), layer_(layer) {}

  void Dismiss() const {
    const auto active_layer = layer_.Get();
    visible_ = false;
    layer_ = std::nullopt;
    if (active_layer.has_value()) {
      handle_.Dismiss(*active_layer);
    }
  }

  template <typename Factory>
    requires std::invocable<Factory, bool, std::function<void()>>
  void Show(Factory factory) const {
    Dismiss();
    visible_ = true;
    auto dismiss = [sheet = *this] { sheet.Dismiss(); };
    const auto id = handle_.Show(
        [factory = std::move(factory), dismiss, visible = visible_]() mutable {
          return std::invoke(factory, visible.Get(), dismiss);
        },
        BottomSheetOptions{
            .dismiss_on_outside_press = true,
            .dismiss_on_cancel = true,
            .on_dismiss_request = dismiss,
        });
    layer_ = id;
  }

private:
  BottomSheetHandle handle_;
  State<bool> visible_;
  State<std::optional<LayerId>> layer_;
};

// Labels resolved during composition: the compaction coroutine is not a
// composition scope and therefore cannot resolve resources itself.
struct CompactionLabels final {
  std::string failed_prefix;
  std::string done;
  // `context_compact_label` ("Compacting" / "压缩"), rendered by the progress
  // block row.
  std::string progress;
};

// Port of `ContextCompactionController.startManualContextCompaction()`: refuse
// while streaming, require a model and enough history, then compact and write
// the summary back. Returns false when a guard rejected the request so the
// caller can skip its revision bump.
bool StartManualContextCompaction(
    const std::shared_ptr<application::ContextCompactionService> &service,
    const std::shared_ptr<application::ChatSession> &session, TaskScope tasks,
    ToastHandle toast, std::shared_ptr<std::atomic<bool>> busy,
    CompactionLabels labels) {
  if (!service) {
    toast.Show(app::strings::context_compact_failed);
    return false;
  }
  if (busy->exchange(true)) {
    return false;
  }
  if (session->Messages().size() < 4) {
    busy->store(false);
    toast.Show(app::strings::context_compact_insufficient);
    return false;
  }
  auto messages = std::vector<domain::ChatMessage>{session->Messages().begin(),
                                                   session->Messages().end()};
  tasks.Launch([service, session, toast, messages = std::move(messages), labels,
                busy]() mutable -> Task<void> {
    auto compacted =
        co_await service->Compact(domain::ModelConfig{}, std::move(messages));
    busy->store(false);
    if (!compacted) {
      toast.Show(StringVariant::Format(app::strings::context_compact_failed,
                                       compacted.error().message));
      co_return;
    }
    if (compacted->Empty()) {
      co_return; // Cancelled: the legacy controller stayed silent.
    }
    std::vector<std::uint64_t> excluded;
    for (const auto &message : session->Messages()) {
      if (!message.hidden && !message.exclude_from_context)
        excluded.push_back(message.id);
    }
    // Keep the most recent exchange verbatim, like the legacy selectRecent
    // messages path, so the user's latest turn is never summarized away.
    if (excluded.size() > 2)
      excluded.resize(excluded.size() - 2);
    session->ApplyCompaction(std::move(excluded), compacted->summary_content);
    toast.Show(labels.done);
  });
  return true;
}

// Legacy `ContextUsageIndicatorView`: a 2dp-stroke ring whose sweep is the
// used percentage, turning WARNING at 80% and above.
View ContextUsageIndicator(int percent, std::function<void()> on_click) {
  // Theme tokens are composition-bound: resolve them here and capture the
  // plain colors, because the paint callback runs at draw time.
  const Color track = colors::border;
  const Color progress = percent >= 80 ? static_cast<Color>(colors::warning)
                                       : static_cast<Color>(colors::secondary);
  // The ring lives in a Stack child: `Align` positions a container's content,
  // so a bare Canvas leaf would collapse to zero size.
  return Stack{
      Canvas([percent, track, progress](PaintContext &paint, Size) {
        constexpr float kPi = std::numbers::pi_v<float>;
        const Point center{10.0F, 10.0F};
        const StrokeStyle stroke{.width = 2.0F, .cap = StrokeCap::Round};
        paint.DrawArc(center, 8.0F, 0.0F, 2.0F * kPi, track, stroke);
        if (percent > 0) {
          paint.DrawArc(center, 8.0F, -kPi / 2.0F,
                        static_cast<float>(percent) * 3.6F * kPi / 180.0F,
                        progress, stroke);
        }
      }).With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(on_click))
      .With(Frame{.width = 40.0F, .height = 48.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

// Resolved during composition because a bottom-sheet factory is not a
// composition scope and therefore cannot resolve resources itself.
struct ContextUsageLabels final {
  std::string title;
  std::string used;
  std::string limit;
  std::string percent;
  std::string percent_value;
};

View ContextUsageRow(const std::string &label, std::string value) {
  return Row{
      Text(label)
          .Style(ChatTextStyle(14.0F, FontWeight::Regular, colors::secondary))
          .With(Grow()),
      Text(std::move(value))
          .Style(ChatTextStyle(14.0F, FontWeight::Medium, colors::text)),
  }
      .With(Frame{.height = 42.0F}, CrossAlign(CrossAxisAlignment::Center));
}

View ContextUsageSheet(domain::ContextSnapshot snapshot,
                       ContextUsageLabels labels) {
  return Column{
      Text(labels.title)
          .Style(ChatTextStyle(18.0F, FontWeight::Medium))
          .With(Frame{.height = 42.0F}),
      ContextUsageRow(labels.used,
                      domain::FormatGroupedTokens(snapshot.used_tokens)),
      ContextUsageRow(labels.limit,
                      domain::FormatGroupedTokens(snapshot.max_tokens)),
      ContextUsageRow(labels.percent, labels.percent_value),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View Header(
    std::function<void()> open_drawer,
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    const std::shared_ptr<application::PendingMessageQueue> &pending_messages,
    State<TaskHandle> active_generation, State<std::size_t> revision,
    std::function<void()> show_permissions, std::function<void()> show_more,
    std::string project_label, std::function<void()> show_project_picker,
    const domain::ContextSnapshot &context_usage,
    std::function<void()> show_context_usage) {
  auto reset_conversation = [session, generation, pending_messages,
                             active_generation, revision] {
    active_generation.Get().Cancel();
    generation->Reset();
    pending_messages->Clear();
    session->StartNewConversation();
    revision += 1;
  };

  const StringVariant brand_label =
      project_label.empty()
          ? StringVariant{app::strings::header_project_default}
          : StringVariant{project_label};
  return Row{
      HeaderAction(app::images::menu, 19.0F, std::move(open_drawer)),
      Row{
          // The legacy brand row gives the row the remaining width, but keeps
          // the chevron immediately after the wrap-content title. The title's
          // viewport still prevents a long project name from changing height.
          Text(brand_label)
              .Style(ChatTextStyle(16.0F, FontWeight::Medium))
              .With(Frame{.max_height = 21.0F}, ClipChildren()),
          Stack{
              Image(app::images::chevron_down)
                  .Tint(colors::secondary)
                  .With(Frame{.width = 14.0F, .height = 14.0F}),
          }
              .With(Frame{.width = 24.0F, .height = 32.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
      }
          .OnClick(std::move(show_project_picker))
          .With(Frame{.min_height = 48.0F},
                CrossAlign(CrossAxisAlignment::Center), Grow(), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
      ContextUsageIndicator(context_usage.percent,
                            std::move(show_context_usage)),
      HeaderAction(app::images::shield, 19.0F, std::move(show_permissions)),
      HeaderAction(app::images::plus, 19.0F, reset_conversation),
      HeaderAction(app::images::more_vertical, 19.0F, std::move(show_more)),
  }
      .With(Frame{.min_height = 56.0F},
            Padding(EdgeInsets{
                .top = 2.0F, .right = 8.0F, .bottom = 2.0F, .left = 4.0F}),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::background));
}

View EmptyConversation(
    const RouteNavigationController<domain::AppRoute> &navigation,
    bool configure_model) {
  // Spacing between the three rows comes from wrapping containers rather than
  // per-child padding: the legacy layout used `topMargin` (20dp then 28dp),
  // and a child's own padding did not shift its siblings here.
  std::vector<View> content;
  content.reserve(configure_model ? 3U : 2U);
  content.push_back(Text(app::strings::chat_empty_title)
                        .Style(ChatTextStyle(28.0F, FontWeight::Regular)));
  content.push_back(Column{
      Text(configure_model
               ? StringVariant{app::strings::message_list_configure_desc}
               : StringVariant{app::strings::chat_empty_message})
          .Style(ChatTextStyle(15.0F, FontWeight::Regular, colors::secondary)),
  }
                        .With(Padding(EdgeInsets{.top = 20.0F}),
                              CrossAlign(CrossAxisAlignment::Start)));
  if (configure_model) {
    content.push_back(Column{
        Stack{
            Text(app::strings::empty_state_add_model)
                .Style(ChatTextStyle(16.0F, FontWeight::Regular,
                                     colors::text_on_color)),
        }
            .OnClick(
                [navigation] { navigation.Push(domain::AppRoute::models); })
            .With(Frame{.height = 48.2F},
                  Padding(EdgeInsets::Symmetric(16.2F, 0.0F)),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(colors::accent), CornerRadius(22.0F), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
    } // 40dp, not the legacy 28dp: the description above renders shorter
      // here (no line spacing support), so the button needs the extra
      // margin to land on the same baseline as the legacy layout.
                          .With(Padding(EdgeInsets{.top = 40.0F}),
                                CrossAlign(CrossAxisAlignment::Start)));
  }
  return Column(std::move(content))
      .With(
          CrossAlign(CrossAxisAlignment::Start), Grow(),
          Padding(EdgeInsets{
              .top = 104.0F, .right = 28.0F, .bottom = 64.0F, .left = 28.0F}));
}

// Legacy `ShareController.showFormatPicker` presented the export formats as a
// simple titled list of choices. The panel below keeps that shape while the
// actual formats stay behind `application::ChatExportService`.
View ExportFormatRow(DialogContext dialog, StringVariant label,
                     std::function<void()> on_select) {
  return Text(std::move(label))
      .Style(ChatTextStyle(16.0F))
      .VerticalAlign(TextVerticalAlign::Center)
      .OnClick([dialog, on_select = std::move(on_select)] {
        dialog.Dismiss();
        std::invoke(on_select);
      })
      .With(Frame{.min_height = 52.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 0.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View ExportFormatDialog(DialogContext dialog, StringVariant title,
                        std::vector<application::ChatExportOption> options,
                        std::function<void(std::string)> on_select) {
  std::vector<View> children;
  children.reserve(options.size() + 1);
  children.emplace_back(Text(std::move(title))
                            .Style(ChatTextStyle(17.0F, FontWeight::Medium))
                            .With(Padding(EdgeInsets{.bottom = 4.0F})));
  for (auto &option : options) {
    auto id = option.id;
    children.emplace_back(ExportFormatRow(
        dialog, std::move(option.display_name),
        [on_select, id = std::move(id)] { std::invoke(on_select, id); }));
  }
  return Column(std::move(children))
      .With(Frame{.min_width = 280.0F, .max_width = 560.0F},
            Padding(EdgeInsets{.top = 8.0F, .bottom = 8.0F}),
            CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(16.0F), ClipChildren());
}

View Conversation(
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    std::size_t revision,
    const RouteNavigationController<domain::AppRoute> &navigation,
    State<std::optional<std::uint64_t>> action_message, bool multi_select,
    State<std::vector<std::uint64_t>> selected_messages,
    MessageActionCallbacks callbacks,
    const ChatTimelineSettings &timeline_settings,
    State<std::vector<std::string>> toggled_timeline,
    const TutorialMarkdownLinkHandler &on_link,
    const TutorialMarkdownCopyHandler &on_copy,
    const ToolRendererContext &context,
    const std::shared_ptr<AutoCompactionUiState> &auto_compaction,
    std::string compact_label, ScrollController conversation_scroll,
    State<std::uint64_t> scroll_to_bottom_request,
    std::optional<bool> has_selected_model) {
  const auto messages = session->Messages();
  if (messages.empty()) {
    return EmptyConversation(navigation, !has_selected_model.value_or(true));
  }
  const auto presentation_messages =
      BuildConversationPresentationMessages(messages);

  const auto &generation_state = generation->State();
  domain::ChatMessage streaming_message{};
  streaming_message.id = generation_state.generation_id;
  streaming_message.role = domain::MessageRole::assistant;
  streaming_message.content = generation_state.promoted_content;
  if (!generation_state.streamed_text.empty()) {
    if (!streaming_message.content.empty())
      streaming_message.content += "\n\n";
    streaming_message.content += generation_state.streamed_text;
  }
  streaming_message.reasoning_content = generation_state.streamed_reasoning;
  streaming_message.timeline = generation_state.timeline;
  streaming_message.streaming = true;
  streaming_message.processing_started_at = generation_state.started_at_millis;
  View streaming =
      generation_state.phase == application::GenerationPhase::running
          ? MessageBubble(streaming_message, action_message, false,
                          selected_messages, {}, timeline_settings,
                          toggled_timeline, on_link, on_copy, context,
                          compact_label, true)
          : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  // While an automatic compaction runs, its progress block sits at the end of
  // the transcript as a block of its own; after the write-back the persisted
  // block carries the final status, exactly like the legacy controller that
  // re-rendered between `running` and `done`/`error`.
  View compaction = Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  if (auto_compaction &&
      auto_compaction->RunningFor(generation_state.generation_id)) {
    compaction = MessageBubble(
        domain::CompactProgressMessage(0U, auto_compaction->status),
        action_message, false, selected_messages, {}, timeline_settings,
        toggled_timeline, on_link, on_copy, context, compact_label);
  }
  const ScrollMetrics metrics = conversation_scroll.Metrics();
  const bool at_bottom = metrics.maximum_offset <= metrics.offset + 2.0F;
  View list =
      ScrollView(
          Column{
              ForEach(presentation_messages,
                      [action_message, multi_select, selected_messages,
                       callbacks, timeline_settings, toggled_timeline, on_link,
                       on_copy, context, compact_label](const auto &message) {
                        return MessageBubble(message, action_message,
                                             multi_select, selected_messages,
                                             callbacks, timeline_settings,
                                             toggled_timeline, on_link, on_copy,
                                             context, compact_label)
                            .Key(message.id);
                      }),
              std::move(compaction),
              std::move(streaming),
          }
              .With(CrossAlign(CrossAxisAlignment::Stretch),
                    Padding(EdgeInsets{.top = 8.0F,
                                       .bottom = multi_select ? 72.0F : 8.0F})))
          .ScrollAxis(Axis::Vertical)
          .Controller(conversation_scroll)
          .With(Grow(), ScrollBar(),
                ChatTailScroll{
                    .controller = conversation_scroll,
                    .conversation_id =
                        std::string{session->CurrentConversationId()},
                    .content_revision = revision,
                    .smooth_request = scroll_to_bottom_request.Get(),
                });

  View selection_bar = Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  if (multi_select) {
    selection_bar =
        Row{
            Text::Format(app::strings::model_list_selected_count,
                         selected_messages->size())
                .Style(ChatTextStyle(16.0F)),
            Spacer(),
            Row{
                Stack{Image(app::images::download)
                          .Tint(colors::text_on_color)
                          .With(Frame{.width = 20.0F, .height = 20.0F})}
                    .OnClick(callbacks.export_selected)
                    .With(Frame{.width = 44.0F, .height = 44.0F},
                          Align(HorizontalAlignment::Center,
                                VerticalAlignment::Center),
                          Background(colors::accent), CornerRadius(22.0F),
                          Focusable(), PointerCursor(PointerCursorKind::Hand)),
                Stack{Image(app::images::x)
                          .Tint(colors::secondary)
                          .With(Frame{.width = 20.0F, .height = 20.0F})}
                    .OnClick([selected_messages, action_message,
                              exit = callbacks.enter_multi_select] {
                      selected_messages = std::vector<std::uint64_t>{};
                      action_message = std::nullopt;
                      if (exit)
                        exit();
                    })
                    .With(Frame{.width = 44.0F, .height = 44.0F},
                          Align(HorizontalAlignment::Center,
                                VerticalAlignment::Center),
                          Focusable(), PointerCursor(PointerCursorKind::Hand)),
            }
                .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
        }
            .With(Padding(EdgeInsets::Symmetric(16.0F, 8.0F)),
                  CrossAlign(CrossAxisAlignment::Center),
                  Background(colors::elevated),
                  Shadow{.color = Color::Rgb(0, 0, 0, 0.18F),
                         .blur_radius = 8.0F});
  }

  View scroll_button = Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  if (!multi_select && !at_bottom) {
    scroll_button = Column{
        Spacer(),
        Row{
            Spacer(),
            ScrollToBottomButton(
                [scroll_to_bottom_request] { scroll_to_bottom_request += 1U; }),
        }
            .With(Padding(EdgeInsets{.right = 16.0F, .bottom = 16.0F})),
    };
  }
  return Stack{std::move(list), std::move(scroll_button),
               Column{Spacer(), std::move(selection_bar)}}
      .With(Grow(),
            Align(HorizontalAlignment::Stretch, VerticalAlignment::Stretch));
}

void ShowTextSelectionDialog(const DialogHandle &dialogs, std::string content) {
  dialogs.Show([content = std::move(content)](DialogContext dialog) {
    return Column{
        Text(app::strings::dialog_select_text_title)
            .Style(ChatTextStyle(20.0F, FontWeight::Bold)),
        ScrollView(SelectionArea(Text(content).Style(ChatTextStyle(15.0F))))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 500.0F}, Padding(24.0F),
                  Background(colors::background)),
        Row{
            Spacer(),
            Text(app::strings::common_close)
                .Style(ChatTextStyle(14.0F))
                .OnClick([dialog] { dialog.Dismiss(); })
                .With(Frame{.min_height = 48.0F},
                      Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Focusable(), PointerCursor(PointerCursorKind::Hand)),
        },
    }
        .With(
            Frame{
                .min_width = 280.0F, .max_width = 560.0F, .max_height = 640.0F},
            Padding(24.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), CornerRadius(24.0F),
            Shadow{.color = Color::Rgb(0, 0, 0, 0.28F), .blur_radius = 18.0F});
  });
}

} // namespace

[[huxerui::composable]] View ChatScreen(ChatScreenServices services,
                                        ChatScreenState state,
                                        ChatScreenActions actions) {
  const auto &session = services.session;
  const auto &generation = services.generation;
  const auto &model_store = services.model_store;
  const auto &completion_loop = services.completion_loop;
  const auto &agent_results = services.agent_results;
  const auto &storage_permission = services.storage_permission;
  const auto &pending_messages = services.pending_messages;
  const auto &memory_context = services.memory_context;
  const auto &behavior_settings = services.behavior_settings;
  const auto &todo_state = services.todo_state;
  const auto &skills = services.skills;
  const auto &execution_settings = services.execution_settings;
  const auto &compaction_service = services.compaction_service;
  const auto &diff_store = services.diff_store;
  const auto &diff_review = services.diff_review;
  const auto &output_settings = services.output_settings;
  const auto &tool_permissions = services.tool_permissions;
  const auto &tool_reviews = services.tool_reviews;
  const auto &chat_modes = services.chat_modes;
  auto draft = state.draft;
  const auto has_selected_model = state.has_selected_model;
  auto active_generation = state.active_generation;
  auto revision = state.revision;
  auto workspace = state.workspace;
  auto interaction_mode = state.interaction_mode;
  auto input_settings = state.input_settings;
  auto current_project_id = std::move(state.current_project_id);
  auto prompt_context = std::move(state.prompt_context);
  auto project_label = std::move(state.project_label);
  auto open_drawer = std::move(actions.open_drawer);
  auto show_project_picker = std::move(actions.show_project_picker);
  auto refresh_workspace = std::move(actions.refresh_workspace);
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto bottom_sheets = UseBottomSheet();
  const auto dialogs = UseDialog();
  const auto clipboard = UseApplication().Clipboard();
  const auto share_text = UseService<application::ShareTextService>();
  const auto external_link = UseService<application::ExternalLinkService>();
  const auto export_service = UseService<application::ChatExportService>();
  const auto image_picker = UseService<FilePicker>();
  auto compaction_busy = UseState(std::make_shared<std::atomic<bool>>(false));
  auto attachment_visible = UseState(false);
  auto more_visible = UseState(false);
  auto compaction_confirm_visible = UseState(false);
  auto permission_visible = UseState(false);
  auto attachment_layer = UseState(std::optional<LayerId>{});
  auto more_layer = UseState(std::optional<LayerId>{});
  auto compaction_confirm_layer = UseState(std::optional<LayerId>{});
  auto permission_layer = UseState(std::optional<LayerId>{});
  auto permission_state = UseState(application::ToolPermissionState{});
  auto external_storage_granted = UseState(false);
  auto selected_attachments = UseState(std::vector<domain::InputAttachment>{});
  auto selected_image = UseState(std::optional<ComposerImageSelection>{});
  auto expanded_attachment_directories = UseState(std::vector<std::string>{});
  auto review_coordinator =
      UseState(std::make_shared<application::ToolReviewCoordinator>(
          [revision] { revision += 1; }));
  auto quote_text = UseState(std::optional<std::string>{});
  auto action_message = UseState(std::optional<std::uint64_t>{});
  auto multi_select = UseState(false);
  auto selected_messages = UseState(std::vector<std::uint64_t>{});
  auto slash_models = UseState(std::vector<domain::ModelConfig>{});
  auto slash_selected_model_id = UseState(std::string{});
  auto timeline_settings = UseState(ChatTimelineSettings{});
  auto toggled_timeline = UseState(std::vector<std::string>{});
  auto timeline_clock = UseState(NowMilliseconds());
  const auto conversation_scroll = UseScrollController();
  auto scroll_to_bottom_request = UseState(std::uint64_t{0});
  const auto tasks = UseTaskScope();
  const auto generation_phase = generation->State().phase;
  Lifecycle(
      [tasks, generation, timeline_clock, generation_phase] {
        if (generation_phase != application::GenerationPhase::running)
          return;
        tasks.Launch([generation, timeline_clock]() -> Task<void> {
          while (generation->State().phase ==
                 application::GenerationPhase::running) {
            timeline_clock = NowMilliseconds();
            co_await Delay(std::chrono::seconds{1});
          }
        });
      },
      generation_phase);
  (void)timeline_clock.Get();
  // Diff bodies live in the store, not in the transcript. `Lifecycle` runs
  // outside composition (the task scope forbids launching during composition)
  // and re-runs whenever `revision` moves on, which is also when a new tool
  // result may have brought a fresh record id.
  auto diff_cache = UseState(std::make_shared<DiffCache>());
  const auto diff_pending =
      UseState(std::make_shared<std::set<std::string>>()).Get();
  Lifecycle(
      [tasks, session, revision, diff_store, diff_cache, diff_pending] {
        if (!diff_store)
          return;
        std::vector<std::string> wanted;
        for (const auto &message : session->Messages()) {
          for (const auto &event : message.timeline) {
            const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
            if (tool == nullptr || !tool->result.has_value())
              continue;
            const auto &id = tool->result->diff_id;
            if (id.empty() || diff_cache.Get()->contains(id) ||
                diff_pending->contains(id))
              continue;
            diff_pending->insert(id);
            wanted.push_back(id);
          }
        }
        if (wanted.empty())
          return;
        tasks.Launch(
            LoadDiffs(diff_store, diff_cache, diff_pending, std::move(wanted)));
      },
      revision);

  // Same loader, driven by the card's expand event.
  auto request_diff = [tasks, diff_store, diff_cache,
                       diff_pending](std::string diff_id) {
    if (!diff_store || diff_id.empty() || diff_pending->contains(diff_id))
      return;
    const auto cached = diff_cache.Get()->find(diff_id);
    if (cached != diff_cache.Get()->end() && cached->second.available)
      return;
    diff_pending->insert(diff_id);
    tasks.Launch(LoadDiffs(diff_store, diff_cache, diff_pending,
                           std::vector<std::string>{std::move(diff_id)}));
  };

  const RetryLabels retry_labels{
      .attempt = UseString(
          app::strings::chat_retry_attempt, std::string{kAttemptMarker},
          std::to_string(kMaxGenerationAttempts), std::string{kErrorMarker}),
      .failed =
          UseString(app::strings::chat_model_failed, std::string{kErrorMarker}),
      .no_model = UseString(app::strings::chat_error_no_model),
      .model_missing = UseString(app::strings::chat_error_model_missing),
  };
  const CompactionLabels compaction_labels{
      .failed_prefix = UseString(app::strings::context_compact_failed, ""),
      .done = UseString(app::strings::context_compact_done),
      // `context_compact_label` ("Compacting" / "压缩"), the progress block row
      // label. Resolved here because the compaction coroutine is not a
      // composition scope.
      .progress = UseString(app::strings::context_compact_label),
  };
  auto auto_compaction = UseState(std::make_shared<AutoCompactionUiState>());
  const auto toast = UseToast();

  Lifecycle([reviews = tool_reviews, coordinator = review_coordinator.Get()] {
    if (reviews)
      reviews->SetHandler(coordinator->ReviewHandler());
    return [reviews, coordinator] {
      coordinator->Close();
      if (reviews)
        reviews->SetHandler({});
    };
  });
  Lifecycle([tasks, tool_permissions, permission_state, toast] {
    tasks.Launch([tool_permissions, permission_state, toast]() -> Task<void> {
      auto loaded = co_await tool_permissions->Load();
      if (loaded)
        permission_state = *loaded;
      else
        toast.Show(loaded.error().message);
    });
  });
  Lifecycle([tasks, behavior_settings, output_settings, timeline_settings,
             toast] {
    tasks.Launch([behavior_settings, timeline_settings, toast]() -> Task<void> {
      auto loaded = co_await behavior_settings->Load();
      if (!loaded) {
        toast.Show(loaded.error().message);
        co_return;
      }
      timeline_settings.Update([&loaded](auto &settings) {
        settings.thinking_auto_expand = loaded->thinking_auto_expand;
        settings.thinking_scroll = loaded->thinking_scroll;
        settings.preserve_reasoning = loaded->preserve_reasoning;
      });
    });
    tasks.Launch([output_settings, timeline_settings, toast]() -> Task<void> {
      auto loaded = co_await output_settings->Load();
      if (!loaded) {
        toast.Show(loaded.error().message);
        co_return;
      }
      timeline_settings.Update([&loaded](auto &settings) {
        settings.code_wrap_enabled = loaded->code_wrap_enabled;
        settings.process_auto_expand = loaded->process_auto_expand_enabled;
        settings.browser_mode = loaded->browser_mode;
        settings.browser_javascript_enabled =
            loaded->browser_javascript_enabled;
        settings.allow_any_http = loaded->allow_any_http;
      });
    });
  });
  Lifecycle([tasks, model_store, slash_models, slash_selected_model_id, toast] {
    tasks.Launch([model_store, slash_models, slash_selected_model_id,
                  toast]() -> Task<void> {
      auto models = co_await model_store->List();
      if (!models) {
        toast.Show(models.error().message);
        co_return;
      }
      slash_models = std::move(*models);
      auto selected = co_await model_store->SelectedId();
      if (!selected) {
        toast.Show(selected.error().message);
        co_return;
      }
      slash_selected_model_id = std::move(*selected);
    });
  });

  const ControlledBottomSheet attachment_sheet(
      bottom_sheets, attachment_visible, attachment_layer);
  const ControlledBottomSheet more_sheet(bottom_sheets, more_visible,
                                         more_layer);
  const ControlledBottomSheet compaction_confirm_sheet(
      bottom_sheets, compaction_confirm_visible, compaction_confirm_layer);
  auto context_usage_visible = UseState(false);
  auto context_usage_layer = UseState(std::optional<LayerId>{});
  const ControlledBottomSheet context_usage_sheet(
      bottom_sheets, context_usage_visible, context_usage_layer);
  const ControlledBottomSheet permission_sheet(
      bottom_sheets, permission_visible, permission_layer);

  const auto storage_permission_description =
      UseString(app::strings::permission_mode_storage_required);

  const auto set_permission_mode = [tasks, chat_modes, permission_state,
                                    interaction_mode,
                                    toast](domain::ToolPermissionMode mode) {
    tasks.Launch([chat_modes, permission_state, interaction_mode, mode,
                  toast]() -> Task<void> {
      auto saved = co_await chat_modes->SetPermissionMode(mode);
      if (!saved) {
        toast.Show(saved.error().message);
        co_return;
      }
      interaction_mode = *saved;
      permission_state.Update(
          [&saved](auto &state) { state.mode = saved->permission_mode; });
    });
  };

  const auto set_chat_mode = [tasks, chat_modes, permission_state,
                              interaction_mode, toast](domain::ChatMode mode) {
    tasks.Launch([chat_modes, permission_state, interaction_mode, mode,
                  toast]() -> Task<void> {
      auto saved = co_await chat_modes->SetChatMode(mode);
      if (!saved) {
        toast.Show(saved.error().message);
        co_return;
      }
      interaction_mode = *saved;
      permission_state.Update(
          [&saved](auto &state) { state.mode = saved->permission_mode; });
    });
  };

  const auto handle_slash_command = [tasks, set_chat_mode, model_store,
                                     behavior_settings, revision,
                                     slash_selected_model_id,
                                     toast](std::string_view input) {
    auto parsed = application::ParseSlashCommand(input);
    if (!parsed)
      return false;
    std::visit(Overloaded{
                   [set_chat_mode](application::ChatModeSlashCommand command) {
                     set_chat_mode(command.mode);
                   },
                   [tasks, model_store, behavior_settings, revision,
                    slash_selected_model_id,
                    toast](application::ModelSlashCommand command) {
                     tasks.Launch([model_store, behavior_settings,
                                   command = std::move(command), revision,
                                   slash_selected_model_id,
                                   toast]() mutable -> Task<void> {
                       auto selected =
                           co_await model_store->Select(command.model_id);
                       if (!selected) {
                         toast.Show(selected.error().message);
                         co_return;
                       }
                       slash_selected_model_id = command.model_id;
                       if (command.reasoning) {
                         auto reasoning =
                             co_await behavior_settings->SetReasoning(
                                 *command.reasoning);
                         if (!reasoning) {
                           toast.Show(reasoning.error().message);
                           co_return;
                         }
                       }
                       revision += 1;
                     });
                   },
               },
               std::move(*parsed));
    return true;
  };

  const auto clear_command_grants = [tasks, tool_permissions, permission_state,
                                     toast] {
    tasks.Launch([tool_permissions, permission_state, toast]() -> Task<void> {
      auto cleared = co_await tool_permissions->ClearPermanentGrants();
      if (!cleared) {
        toast.Show(cleared.error().message);
        co_return;
      }
      permission_state.Update(
          [](auto &state) { state.has_permanent_grants = false; });
      toast.Show(app::strings::chat_permissions_cleared);
    });
  };

  auto show_permission = [permission_sheet, permission_state,
                          external_storage_granted,
                          storage_permission_description, storage_permission,
                          tool_permissions, tasks, set_permission_mode,
                          clear_command_grants, toast] {
    tasks.Launch([tool_permissions, permission_state, toast]() -> Task<void> {
      auto loaded = co_await tool_permissions->Load();
      if (loaded)
        permission_state = *loaded;
      else
        toast.Show(loaded.error().message);
    });
    if (storage_permission) {
      storage_permission->Query(
          [external_storage_granted,
           toast](application::StoragePermissionResult result) {
            if (!result.Succeeded()) {
              toast.Show(result.error);
              return;
            }
            external_storage_granted = result.granted;
          });
    }
    permission_sheet.Show([permission_state, external_storage_granted,
                           storage_permission_description, storage_permission,
                           set_permission_mode, clear_command_grants,
                           toast](bool visible, std::function<void()> dismiss) {
      return ChatPermissionMenu(
          ChatPermissionMenuState{
              .visible = visible,
              .mode = PresentPermissionMode(permission_state->mode),
              .manage_all_files_available =
                  FeatureAvailable<PlatformFeature::android_storage_permission>,
              .external_storage_granted = external_storage_granted.Get(),
              .has_saved_command_permissions =
                  permission_state->has_permanent_grants,
              .storage_permission_description = storage_permission_description,
          },
          ChatOverlayCallbacks<ChatPermissionAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [set_permission_mode, clear_command_grants,
                   external_storage_granted, storage_permission,
                   toast](ChatPermissionAction action) {
                    switch (action) {
                    case ChatPermissionAction::automatic:
                      set_permission_mode(
                          domain::ToolPermissionMode::automatic);
                      break;
                    case ChatPermissionAction::confirm:
                      set_permission_mode(domain::ToolPermissionMode::confirm);
                      break;
                    case ChatPermissionAction::read_only:
                      set_permission_mode(
                          domain::ToolPermissionMode::read_only);
                      break;
                    case ChatPermissionAction::manage_all_files:
                      if (storage_permission) {
                        storage_permission->OpenManagementSettings(
                            [external_storage_granted, toast](
                                application::StoragePermissionResult result) {
                              if (!result.Succeeded()) {
                                toast.Show(result.error);
                                return;
                              }
                              external_storage_granted = result.granted;
                            });
                      }
                      break;
                    case ChatPermissionAction::revoke_saved_commands:
                      clear_command_grants();
                      break;
                    }
                  },
          });
    });
  };

  auto show_more = [more_sheet, compaction_confirm_sheet, navigation, session,
                    generation, pending_messages, active_generation, revision,
                    dialogs, toast, export_service, multi_select,
                    selected_messages, action_message, compaction_service,
                    tasks, compaction_busy, compaction_labels] {
    more_sheet.Show([compaction_confirm_sheet, navigation, session, generation,
                     pending_messages, active_generation, revision, dialogs,
                     toast, export_service, multi_select, selected_messages,
                     action_message, compaction_service, tasks, compaction_busy,
                     compaction_labels](bool visible,
                                        std::function<void()> dismiss) {
      return ChatMoreMenu(
          ChatMoreMenuState{.visible = visible, .available = {}},
          ChatOverlayCallbacks<ChatMoreAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [compaction_confirm_sheet, navigation, session, generation,
                   pending_messages, active_generation, revision, dialogs,
                   toast, export_service, multi_select, selected_messages,
                   compaction_service, tasks, compaction_busy,
                   compaction_labels, action_message](ChatMoreAction action) {
                    switch (action) {
                    case ChatMoreAction::tutorial:
                      navigation.Push(domain::AppRoute::tutorial);
                      break;
                    case ChatMoreAction::settings:
                      navigation.Push(domain::AppRoute::settings);
                      break;
                    case ChatMoreAction::clear_chat:
                      active_generation.Get().Cancel();
                      generation->Reset();
                      pending_messages->Clear();
                      session->DeleteCurrentConversation();
                      revision += 1;
                      break;
                    case ChatMoreAction::export_chat: {
                      const auto messages = session->Messages();
                      if (messages.empty()) {
                        toast.Show(app::strings::toast_chat_empty_export);
                        break;
                      }
                      dialogs.Show(
                          ExportFormatDialog,
                          StringVariant{
                              app::strings::dialog_export_format_title},
                          export_service->Options(),
                          [export_service, messages, toast](std::string id) {
                            const auto result = export_service->Export(
                                id,
                                std::span<const domain::ChatMessage>{messages});
                            if (!result.Succeeded()) {
                              toast.Show(
                                  app::strings::toast_chat_export_failed);
                              return;
                            }
                            if (result.warn_large_clipboard)
                              toast.Show(app::strings::toast_clipboard_large);
                          });
                      break;
                    }
                    case ChatMoreAction::select_messages_to_export:
                      // Legacy OverlayActionController routed this straight
                      // into message multi-select mode.
                      multi_select = true;
                      selected_messages = std::vector<std::uint64_t>{};
                      action_message = std::nullopt;
                      break;
                    case ChatMoreAction::compact_context:
                      compaction_confirm_sheet.Show(
                          [compaction_service, session, generation, revision,
                           toast, tasks, compaction_busy, compaction_labels](
                              bool visible, std::function<void()> dismiss) {
                            return ChatCompactionMenu(
                                visible,
                                ChatOverlayCallbacks<ChatCompactionAction>{
                                    .on_dismiss_request = std::move(dismiss),
                                    .on_action =
                                        [compaction_service, session,
                                         generation, revision, toast, tasks,
                                         compaction_busy, compaction_labels](
                                            ChatCompactionAction action) {
                                          if (action ==
                                              ChatCompactionAction::cancel) {
                                            return;
                                          }
                                          if (generation->State().phase ==
                                              application::GenerationPhase::
                                                  running) {
                                            return;
                                          }
                                          const bool started =
                                              StartManualContextCompaction(
                                                  compaction_service, session,
                                                  tasks, toast, compaction_busy,
                                                  compaction_labels);
                                          if (started)
                                            revision += 1;
                                        },
                                });
                          });
                      break;
                    }
                  },
          });
    });
  };

  // Legacy `AttachmentPickerCoordinator.onAttachmentPickerRequested()`: the
  // browsed files' source follows the configured execution mode.
  auto attachment_source =
      UseState(std::string{domain::InputAttachment::source_local});
  Lifecycle([tasks, execution_settings, attachment_source] {
    if (!execution_settings)
      return;
    tasks.Launch([execution_settings, attachment_source]() -> Task<void> {
      auto settings = co_await execution_settings->Load();
      if (!settings)
        co_return;
      switch (settings->mode) {
      case domain::McpExecutionMode::ssh:
        attachment_source = std::string{domain::InputAttachment::source_ssh};
        break;
      case domain::McpExecutionMode::terminal_provider:
        attachment_source =
            std::string{domain::InputAttachment::source_terminal_provider};
        break;
      case domain::McpExecutionMode::local:
        attachment_source = std::string{domain::InputAttachment::source_local};
        break;
      }
    });
  });

  auto show_attachments = [attachment_sheet, workspace, selected_attachments,
                           expanded_attachment_directories, attachment_source,
                           refresh_workspace] {
    if (!workspace->file_tree.has_value() && refresh_workspace)
      std::invoke(refresh_workspace);
    std::optional<ChatAttachmentNode> initial_tree;
    if (workspace->file_tree.has_value()) {
      initial_tree = ToAttachmentNode(*workspace->file_tree);
      std::vector<std::string> expanded;
      CollectExpandedDirectories(*initial_tree, expanded);
      expanded_attachment_directories = std::move(expanded);
    }

    attachment_sheet.Show([workspace, selected_attachments,
                           expanded_attachment_directories, attachment_source](
                              bool visible, std::function<void()> dismiss) {
      std::optional<ChatAttachmentNode> tree;
      if (workspace->file_tree.has_value()) {
        tree = ToAttachmentNode(*workspace->file_tree);
      }
      return ChatAttachmentPicker(
          ChatAttachmentPickerState{
              .visible = visible,
              // Legacy AttachmentPickerCoordinator derived this from the
              // execution mode, so remote attachments are labelled right.
              .source = attachment_source.Get(),
              .tree = std::move(tree),
              .selected_paths = AttachmentPaths(selected_attachments.Get()),
              .expanded_directories = expanded_attachment_directories.Get(),
              .loading = !workspace->file_tree.has_value(),
              .message = {},
          },
          ChatAttachmentPickerCallbacks{
              .on_dismiss_request = std::move(dismiss),
              .on_directory_toggled =
                  [expanded_attachment_directories](std::string path) {
                    expanded_attachment_directories.Update(
                        [&path](std::vector<std::string> &paths) {
                          TogglePath(paths, path);
                        });
                  },
              .on_file_toggled =
                  [selected_attachments](ChatAttachmentFile file) {
                    selected_attachments.Update(
                        [&file](
                            std::vector<domain::InputAttachment> &attachments) {
                          ToggleAttachment(attachments, file);
                        });
                  },
          });
    });
  };

  auto show_image_picker = [image_picker, tasks, selected_image, generation,
                            toast] {
    if (generation->State().phase == application::GenerationPhase::running)
      return;
    if (!image_picker || !image_picker->CanOpenFiles()) {
      toast.Show(app::strings::composer_image_picker_unavailable);
      return;
    }
    tasks.Launch([image_picker, selected_image, toast]() -> Task<void> {
      auto selected = co_await image_picker->OpenFileAsync(FilePickerFilter{
          .name = "Image",
          .extensions = {"png", "jpg", "jpeg"},
          .content_types = {"image/png", "image/jpeg"},
      });
      if (!selected)
        co_return;
      if (selected->Size().value_or(0U) >
          application::max_composer_image_input_bytes) {
        toast.Show(app::strings::composer_image_load_failed);
        co_return;
      }
      toast.Show(app::strings::composer_image_loading);
      auto bytes = co_await selected->ReadBytesAsync();
      if (!bytes.Succeeded()) {
        toast.Show(app::strings::composer_image_load_failed);
        co_return;
      }
      Bytes owned = std::move(bytes).Value();
      auto message = application::EncodeComposerImage(selected->Name(), owned);
      if (!message) {
        toast.Show(app::strings::composer_image_load_failed);
        co_return;
      }
      try {
        selected_image = ComposerImageSelection{
            .message = std::move(*message),
            .preview = ImageAsset::FromEncoded(std::move(owned)),
        };
      } catch (const std::invalid_argument &) {
        toast.Show(app::strings::composer_image_load_failed);
      }
    });
  };

  const MessageActionCallbacks message_actions{
      .copy =
          [clipboard, toast](const domain::ChatMessage &message) {
            if (clipboard && clipboard->WriteText(message.content))
              toast.Show(app::strings::toast_copied);
          },
      .quote =
          [quote_text, action_message](const domain::ChatMessage &message) {
            quote_text = message.content;
            action_message = std::nullopt;
          },
      .share =
          [dialogs, export_service, toast](const domain::ChatMessage &message) {
            dialogs.Show(
                ExportFormatDialog,
                StringVariant{app::strings::dialog_export_format_title},
                export_service->Options(),
                [export_service, message, toast](std::string id) {
                  const std::array messages{message};
                  const auto result = export_service->Export(
                      id, std::span<const domain::ChatMessage>{messages});
                  if (!result.Succeeded()) {
                    toast.Show(app::strings::toast_chat_export_failed);
                    return;
                  }
                  if (result.warn_large_clipboard)
                    toast.Show(app::strings::toast_clipboard_large);
                });
          },
      .select_text =
          [dialogs](const domain::ChatMessage &message) {
            ShowTextSelectionDialog(dialogs, message.content);
          },
      .enter_multi_select =
          [multi_select, selected_messages, action_message] {
            multi_select = !multi_select.Get();
            selected_messages = std::vector<std::uint64_t>{};
            action_message = std::nullopt;
          },
      .recall =
          [session, generation, draft, selected_attachments, selected_image,
           quote_text, action_message,
           revision](const domain::ChatMessage &message) {
            if (generation->State().phase ==
                application::GenerationPhase::running) {
              return;
            }
            auto recalled = session->RecallUserMessage(message.id);
            if (!recalled)
              return;
            draft = TextEditingValue::FromText(recalled->content);
            selected_attachments = recalled->attachments;
            selected_image = std::nullopt;
            quote_text = std::nullopt;
            action_message = std::nullopt;
            revision += 1;
          },
      .export_selected =
          [session, selected_messages, dialogs, toast, export_service] {
            std::vector<domain::ChatMessage> messages;
            for (const auto &message : session->Messages()) {
              if (std::ranges::contains(selected_messages.Get(), message.id))
                messages.push_back(message);
            }
            if (messages.empty()) {
              toast.Show(app::strings::toast_chat_empty_export);
              return;
            }
            // Legacy `ShareController.showFormatPicker` served both the
            // whole-chat export and the multi-select export, so both entry
            // points share the same format list.
            dialogs.Show(
                ExportFormatDialog,
                StringVariant{app::strings::dialog_export_format_title},
                export_service->Options(),
                [export_service, messages, toast](std::string id) {
                  const auto result = export_service->Export(
                      id, std::span<const domain::ChatMessage>{messages});
                  if (!result.Succeeded()) {
                    toast.Show(app::strings::toast_chat_export_failed);
                    return;
                  }
                  if (result.warn_large_clipboard)
                    toast.Show(app::strings::toast_clipboard_large);
                });
          },
  };

  View composer_or_review;
  const auto pending_review = review_coordinator.Get()->Current();
  if (pending_review) {
    const auto &request = pending_review->request;
    composer_or_review = ToolApprovalView(
        ToolApprovalViewState{
            .tool_call_id = request.call.id,
            .tool_name = request.call.name,
            .arguments = request.call.arguments_json,
            .can_allow_permanently = request.can_allow_always,
            .submitted = pending_review->submitted,
        },
        ToolApprovalCallbacks{
            .on_reject =
                [coordinator = review_coordinator.Get()](std::string id) {
                  coordinator->Resolve(id, application::CompletionObserver::
                                               ToolReviewDecision::reject);
                },
            .on_allow_once =
                [coordinator = review_coordinator.Get()](std::string id) {
                  coordinator->Resolve(id, application::CompletionObserver::
                                               ToolReviewDecision::allow_once);
                },
            .on_allow_always =
                [coordinator = review_coordinator.Get()](std::string id) {
                  coordinator->Resolve(id,
                                       application::CompletionObserver::
                                           ToolReviewDecision::allow_always);
                },
        });
  } else {
    composer_or_review = Composer(
        chat_composer::Services{
            .session = session,
            .generation = generation,
            .model_store = model_store,
            .completion_loop = completion_loop,
            .memory_context = memory_context,
            .behavior_settings = behavior_settings,
            .todo_state = todo_state,
            .skills = skills,
            .tool_reviews = review_coordinator.Get(),
            .pending_messages = pending_messages,
            .compaction = compaction_service,
        },
        chat_composer::ViewState{
            .draft = draft,
            .has_selected_model = has_selected_model,
            .tasks = tasks,
            .active_generation = active_generation,
            .revision = revision,
            .attachments = selected_attachments,
            .image = selected_image,
            .quote = quote_text,
            .chat_mode = interaction_mode->chat_mode,
            .slash_models = slash_models,
            .selected_model_id = slash_selected_model_id,
            .input_settings = input_settings,
            .current_project_id = current_project_id,
            .prompt_context = std::move(prompt_context),
            .permission_mode = permission_state->mode,
            .toast = toast,
            .auto_compaction = auto_compaction.Get(),
            .retry_labels = retry_labels,
        },
        chat_composer::Actions{
            .show_attachment_picker = show_attachments,
            .show_image_picker = show_image_picker,
            .handle_slash_command = handle_slash_command,
        });
  }

  const TutorialMarkdownLinkHandler open_markdown_link =
      [navigation, external_link,
       browser_mode = timeline_settings->browser_mode,
       javascript_enabled = timeline_settings->browser_javascript_enabled,
       allow_any_http = timeline_settings->allow_any_http](const Uri &target) {
        if (browser_mode == application::BrowserMode::external) {
          external_link->Open(target.ToString());
          return;
        }
        navigation.Push(domain::AppRoute::Browser(
            target.ToString(), javascript_enabled, allow_any_http));
      };
  // Legacy code blocks copy their own source and confirm with a toast.
  auto copy_code = [clipboard, toast](std::string code) {
    if (clipboard)
      clipboard->WriteText(code);
    toast.Show(app::strings::markdown_code_copied);
  };

  // Local estimate over the live history; the legacy header recomputed the
  // same snapshot on every render instead of caching it.
  int context_tokens = domain::default_context_tokens;
  for (const auto &model : slash_models.Get()) {
    if (model.id == slash_selected_model_id.Get()) {
      context_tokens = domain::ResolveModelContext(model).context_tokens;
      break;
    }
  }
  const auto context_snapshot = domain::SnapshotContext(
      std::vector<domain::ChatMessage>{session->Messages().begin(),
                                       session->Messages().end()},
      context_tokens, timeline_settings.Get().preserve_reasoning);
  // The write card asks for a decision by identifier; accepting only records
  // the state while rejecting also restores the previous file contents.
  auto review_change = [diff_review, diff_store, tasks, revision, diff_cache,
                        diff_pending,
                        toast](std::string tool_call_id, std::string diff_id,
                               std::string state) {
    if (!diff_review || tool_call_id.empty())
      return;
    tasks.Launch([diff_review, diff_store,
                  tool_call_id = std::move(tool_call_id),
                  diff_id = std::move(diff_id), state = std::move(state),
                  revision, diff_cache, diff_pending, toast]() -> Task<void> {
      auto reviewed = co_await diff_review->Review(std::move(tool_call_id),
                                                   std::move(state), diff_id);
      if (!reviewed) {
        toast.Show(reviewed.error().message);
        co_return;
      }
      // Re-read the record so the card shows the decision that was just made
      // instead of the transcript's stale copy.
      if (diff_store && !diff_id.empty()) {
        diff_pending->insert(diff_id);
        co_await LoadDiffs(diff_store, diff_cache, diff_pending,
                           std::vector<std::string>{std::move(diff_id)});
      }
      revision += 1;
    });
  };

  const ContextUsageLabels context_usage_labels{
      .title = UseString(app::strings::context_usage_title),
      .used = UseString(app::strings::context_usage_used),
      .limit = UseString(app::strings::context_usage_limit),
      .percent = UseString(app::strings::context_usage_percent),
      .percent_value = UseString(app::strings::context_usage_percent_value,
                                 context_snapshot.percent),
  };
  auto show_context_usage = [context_usage_sheet, context_snapshot,
                             context_usage_labels] {
    context_usage_sheet.Show(
        [snapshot = context_snapshot, labels = context_usage_labels](
            bool visible, std::function<void()> dismiss) -> View {
          if (!visible)
            return Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
          return ChatSheetPanel(
              Column{
                  ContextUsageSheet(snapshot, labels),
              }
                  .With(Padding(EdgeInsets{.top = 22.0F,
                                           .right = 24.0F,
                                           .bottom = 24.0F,
                                           .left = 24.0F}),
                        CrossAlign(CrossAxisAlignment::Stretch),
                        Background(colors::background), CornerRadius(24.0F)));
        });
  };

  return Column{
      Header(std::move(open_drawer), session, generation, pending_messages,
             active_generation, revision, show_permission, show_more,
             std::move(project_label), std::move(show_project_picker),
             context_snapshot, std::move(show_context_usage)),
      Conversation(session, generation, revision.Get(), navigation,
                   action_message, multi_select.Get(), selected_messages,
                   message_actions, timeline_settings.Get(), toggled_timeline,
                   open_markdown_link, copy_code,
                   ToolRendererContext{.diff_cache = diff_cache.Get(),
                                       .on_request_diff = request_diff,
                                       .on_review = review_change,
                                       .agent_results = agent_results,
                                       .toggled_timeline = toggled_timeline},
                   auto_compaction.Get(), compaction_labels.progress,
                   conversation_scroll, scroll_to_bottom_request,
                   has_selected_model),
      std::move(composer_or_review),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background));
}

} // namespace linecode::presentation
