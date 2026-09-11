#include "presentation/components/chat_screen.h"

#include <algorithm>
#include <atomic>
#include <array>
#include <cctype>
#include <chrono>
#include <functional>
#include <iterator>
#include <map>
#include <set>
#include <memory>
#include <numbers>
#include <optional>
#include <ranges>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/behavior_settings_repository.h"
#include "application/chat_session.h"
#include "application/context_compaction.h"
#include "application/diff_review_service.h"
#include "application/ports/diff_store.h"
#include "application/chat_export.h"
#include "application/generation_controller.h"
#include "application/memory_context_service.h"
#include "application/mcp_completion_loop.h"
#include "application/output_settings.h"
#include "application/ports/external_link.h"
#include "application/pending_message_queue.h"
#include "application/ports/model_store.h"
#include "application/ports/share_text.h"
#include "application/ports/storage_permission.h"
#include "application/prompt_request_composer.h"
#include "application/ports/todo_state_store.h"
#include "application/skill_repository.h"
#include "application/slash_command_catalog.h"
#include "application/tool_permission_service.h"
#include "domain/context_usage.h"
#include "domain/diff_lines.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/components/chat_overlays.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/components/tool_approval_view.h"
#include "presentation/chat_timeline_presentation.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

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

View ComposerAction(ImageResource icon, Color tint, Color background,
                    bool enabled, std::function<void()> action) {
  return Stack{
      Image(std::move(icon))
          .Tint(tint)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 44.0F, .height = 44.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(background), CornerRadius(22.0F), Enabled{enabled},
            Focusable(enabled),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

bool HasVisibleText(const std::string &text) {
  return std::ranges::any_of(
      text, [](unsigned char value) { return std::isspace(value) == 0; });
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

struct IndexableRole final {
  domain::MessageRole role;
  std::string_view storage_name;
};

constexpr std::array kIndexableRoles{
    IndexableRole{domain::MessageRole::user, "user"},
    IndexableRole{domain::MessageRole::assistant, "assistant"},
};

std::optional<std::string_view>
MemoryRoleName(domain::MessageRole role) noexcept {
  const auto found =
      std::ranges::find(kIndexableRoles, role, &IndexableRole::role);
  if (found == kIndexableRoles.end())
    return std::nullopt;
  return found->storage_name;
}

domain::MemoryConversationTurn
SnapshotConversation(const application::ChatSession &session,
                     std::string project_id, std::string conversation_id) {
  const auto now = NowMilliseconds();
  std::string title;
  const auto summary =
      std::ranges::find(session.Conversations(), conversation_id,
                        &application::ConversationSummary::id);
  if (summary != session.Conversations().end())
    title = summary->title;

  std::vector<domain::MemoryConversationMessage> messages;
  messages.reserve(session.Messages().size());
  for (const auto &message : session.Messages()) {
    const auto role = MemoryRoleName(message.role);
    if (!role || message.content.empty())
      continue;
    messages.push_back(domain::MemoryConversationMessage{
        .id = std::to_string(message.id),
        .role = std::string{*role},
        .content = message.content,
        .timestamp = now,
    });
    if (title.empty() && message.role == domain::MessageRole::user)
      title = domain::PreviewMemoryText(message.content, 28);
  }
  return domain::MemoryConversationTurn{
      .project_id = std::move(project_id),
      .conversation_id = std::move(conversation_id),
      .title = std::move(title),
      .messages = std::move(messages),
      .updated_at = now,
  };
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

std::vector<std::string> AttachmentPaths(
    std::span<const domain::InputAttachment> attachments) {
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
};

// Port of `ContextCompactionController.startManualContextCompaction()`: refuse
// while streaming, require a model and enough history, then compact and write
// the summary back. Returns false when a guard rejected the request so the
// caller can skip its revision bump.
bool StartManualContextCompaction(
    const std::shared_ptr<application::ContextCompactionService> &service,
    const std::shared_ptr<application::ChatSession> &session,
    TaskScope tasks, ToastHandle toast,
    std::shared_ptr<std::atomic<bool>> busy,
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
    session->ApplyCompaction(std::move(excluded),
                             compacted->summary_content);
    toast.Show(labels.done);
  });
  return true;
}

struct PendingToolReview final {
  std::optional<application::CompletionObserver::ToolReviewRequest> request;
  std::optional<application::CompletionObserver::ToolReviewDecision> decision;
  bool submitted{};
};

void ResolveToolReview(
    const std::shared_ptr<PendingToolReview> &pending,
    std::string_view tool_call_id,
    application::CompletionObserver::ToolReviewDecision decision,
    State<std::size_t> revision) {
  if (!pending->request || pending->submitted ||
      pending->request->call.id != tool_call_id)
    return;
  pending->submitted = true;
  pending->decision = decision;
  revision += 1;
}

// Legacy `ContextUsageIndicatorView`: a 2dp-stroke ring whose sweep is the
// used percentage, turning WARNING at 80% and above.
View ContextUsageIndicator(int percent, std::function<void()> on_click) {
  // Theme tokens are composition-bound: resolve them here and capture the
  // plain colors, because the paint callback runs at draw time.
  const Color track = colors::border;
  const Color progress =
      percent >= 80 ? static_cast<Color>(colors::warning)
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
            Semantics{.label = StringVariant::Format(
                          app::strings::context_usage_accessibility,
                          percent)},
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
      .With(Frame{.height = 42.0F},
            CrossAlign(CrossAxisAlignment::Center));
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
    session->Clear();
    revision += 1;
  };

  return Row{
      HeaderAction(app::images::menu, 19.0F, std::move(open_drawer)),
      Row{
          Text(project_label.empty() ? StringVariant{app::strings::header_project_default}
                                     : StringVariant{std::move(project_label)})
              .Style(ChatTextStyle(16.0F, FontWeight::Medium)),
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
    const RouteNavigationController<domain::AppRoute> &navigation) {
  return Column{
      Text(app::strings::chat_empty_title)
          .Style(ChatTextStyle(28.0F, FontWeight::Regular)),
      Text(app::strings::message_list_configure_desc)
          .Style(ChatTextStyle(15.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets{.top = 20.0F})),
      Column{
          Stack{
              Text(app::strings::empty_state_add_model)
                  .Style(ChatTextStyle(16.0F, FontWeight::Regular,
                                       colors::text_on_color)),
          }
              .OnClick(
                  [navigation] { navigation.Push(domain::AppRoute::models); })
              .With(
                  Frame{.height = 48.2F},
                  Padding(EdgeInsets::Symmetric(16.2F, 0.0F)),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(colors::accent), CornerRadius(22.0F), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
      }
          .With(Padding(EdgeInsets{.top = 40.0F})),
  }
      .With(
          CrossAlign(CrossAxisAlignment::Start), Grow(),
          Padding(EdgeInsets{
              .top = 104.0F, .right = 28.0F, .bottom = 64.0F, .left = 28.0F}));
}

struct MessageActionCallbacks final {
  std::function<void(const domain::ChatMessage &)> copy;
  std::function<void(const domain::ChatMessage &)> quote;
  std::function<void(const domain::ChatMessage &)> share;
  std::function<void(const domain::ChatMessage &)> select_text;
  std::function<void()> enter_multi_select;
  std::function<void(const domain::ChatMessage &)> recall;
  std::function<void()> export_selected;
};

View MessageActionButton(ImageResource icon, StringResource label,
                         std::function<void()> action) {
  return Stack{
      Image(std::move(icon))
          .Tint(colors::tertiary)
          .With(Frame{.width = 16.0F, .height = 16.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 40.0F, .height = 44.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Semantics{.label = std::move(label)}, Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View MessageActionBar(const domain::ChatMessage &message,
                      const MessageActionCallbacks &callbacks) {
  const bool user = message.role == domain::MessageRole::user;
  std::vector<View> actions{
      MessageActionButton(app::images::copy,
                          app::strings::message_action_copy_desc,
                          [callbacks, message] {
                            if (callbacks.copy)
                              callbacks.copy(message);
                          }),
      MessageActionButton(app::images::quote,
                          app::strings::message_action_quote_desc,
                          [callbacks, message] {
                            if (callbacks.quote)
                              callbacks.quote(message);
                          }),
      MessageActionButton(app::images::share_2,
                          app::strings::message_action_share_desc,
                          [callbacks, message] {
                            if (callbacks.share)
                              callbacks.share(message);
                          }),
      MessageActionButton(app::images::text_cursor,
                          app::strings::message_action_select_desc,
                          [callbacks, message] {
                            if (callbacks.select_text)
                              callbacks.select_text(message);
                          }),
      MessageActionButton(app::images::check_square,
                          app::strings::message_action_multi_select_desc,
                          [callbacks] {
                            if (callbacks.enter_multi_select)
                              callbacks.enter_multi_select();
                          }),
  };
  if (user) {
    actions.push_back(MessageActionButton(
        app::images::rotate_ccw, app::strings::message_action_recall_desc,
        [callbacks, message] {
          if (callbacks.recall)
            callbacks.recall(message);
        }));
  }
  View bar = Row(std::move(actions)).With(
      Frame{.height = 44.0F}, Spacing(4.0F),
      CrossAlign(CrossAxisAlignment::Center));
  return user ? Row{Spacer(), bar} : Row{bar, Spacer()};
}

View MessageAttachments(const domain::ChatMessage &message, bool user) {
  std::vector<View> chips;
  chips.reserve(message.attachments.size());
  for (const auto &attachment : message.attachments) {
    chips.push_back(
        Text(attachment.Name())
            .Style(ChatTextStyle(12.0F, FontWeight::Medium,
                                 colors::secondary))
            .With(Frame{.max_width = 220.0F},
                  Padding(EdgeInsets::Symmetric(8.0F, 4.0F)),
                  Background(colors::surface_light),
                  Border(colors::border_light, 1.0F), CornerRadius(14.0F),
                  ClipChildren())
            .Key(attachment.Source() + ":" + attachment.Path()));
  }
  if (chips.empty())
    return Stack{}.With(Frame{.height = 0.0F});
  View column = Column(std::move(chips)).With(
      Spacing(4.0F), user ? CrossAlign(CrossAxisAlignment::End)
                          : CrossAlign(CrossAxisAlignment::Start));
  return user ? Row{Spacer(), column} : Row{column, Spacer()};
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

struct ChatTimelineSettings final {
  bool code_wrap_enabled{};
  bool process_auto_expand{};
  bool thinking_auto_expand{};
  bool thinking_scroll{true};
  // Legacy ContextManager excluded reasoning from the estimate unless the
  // behaviour setting kept it in the request.
  bool preserve_reasoning{};
  application::BrowserMode browser_mode{application::BrowserMode::builtin};
  bool browser_javascript_enabled{};
};

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
  children.emplace_back(
      Text(std::move(title))
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

bool ToggleState(std::span<const std::string> toggled,
                 std::string_view key, bool default_value) {
  return std::ranges::contains(toggled, key) ? !default_value : default_value;
}

void ToggleKey(State<std::vector<std::string>> toggled, std::string key) {
  toggled.Update([key = std::move(key)](auto &keys) {
    const auto found = std::ranges::find(keys, key);
    if (found == keys.end())
      keys.push_back(key);
    else
      keys.erase(found);
  });
}

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
  const auto found = std::ranges::find(kToolStatusPolicies, status,
                                       &ToolStatusPolicy::status);
  return found == kToolStatusPolicies.end() ? app::strings::chat_tool_failed
                                             : found->label;
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
                Align(HorizontalAlignment::Center,
                      VerticalAlignment::Center)));
  children.push_back(
      Text(ToolStatusLabel(presentation.status))
          .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular,
                               color)));
  children.push_back(
      Text(presentation.title)
          .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular,
                               color))
          .With(Grow()));
  if (presentation.visual == ToolTimelineVisualKind::remove &&
      presentation.item_count > 0) {
    children.push_back(
        Text("· " + std::to_string(presentation.item_count))
            .Style(ChatTextStyle(metrics.title_size, FontWeight::Regular,
                                 color)));
  }
  if (presentation.expandable) {
    children.push_back(
        Stack{Image(expanded ? app::images::chevron_down
                             : app::images::chevron_right)
                  .Tint(colors::secondary)
                  .With(Frame{.width = 24.0F, .height = 14.0F})}
            .With(Frame{.width = 24.0F, .height = 32.0F},
                  Align(HorizontalAlignment::Center,
                        VerticalAlignment::Center)));
  }
  View header = Row(std::move(children))
                    .With(Frame{.min_height = metrics.header_height},
                          Spacing(metrics.title_leading_margin));
  if (presentation.expandable) {
    header = std::move(header)
                 .OnClick(std::move(toggle))
                 .With(Focusable(), PointerCursor(PointerCursorKind::Hand));
  }
  return header;
}

View ToolCodeCard(std::string text, const ToolTimelinePresentation &presentation,
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
                       const TutorialMarkdownLinkHandler &on_link = {},
                       const TutorialMarkdownCopyHandler &on_copy = {});

// Everything a write card needs about one recorded change. The review state
// travels with the body so the card can overlay the latest decision, which is
// what the legacy `ToolReviewController.applyLocalReviews` did at render time.
struct DiffEntry final {
  domain::DiffLines lines;
  std::string review_state;
  std::string review_message;
};

using DiffCache = std::map<std::string, DiffEntry>;

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
    if (record) {
      loaded->insert_or_assign(
          id, DiffEntry{.lines = domain::CalculateDiffLines(
                            record->old_content, record->new_content),
                        .review_state = record->EffectiveReviewState(),
                        .review_message = record->review_message});
      changed = true;
    }
    pending->erase(id);
  }
  if (changed)
    cache = loaded;
}

// Everything a tool card needs beyond its own presentation: the recorded
// change behind a write tool and the review action that accepts or reverts it.
struct ToolRendererContext final {
  // Diff bodies keyed by record id. Cards look themselves up, because one
  // conversation can contain several recorded changes.
  std::shared_ptr<const DiffCache> diff_cache;
  // Fetches one record's body. Called from an event handler (expanding the
  // card), never during composition, because launching a task is forbidden
  // while composing.
  std::function<void(std::string diff_id)> on_request_diff;
  std::function<void(std::string tool_call_id, std::string diff_id,
                     std::string state)>
      on_review;
};

View ShellToolRenderer(const ToolTimelinePresentation &presentation,
                       bool expanded, std::function<void()> toggle,
                       const TutorialMarkdownLinkHandler &,
                       const TutorialMarkdownCopyHandler &,
                       const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::terminal,
                                  expanded, std::move(toggle)));
  if (expanded && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 240.0F));
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View ReadToolRenderer(const ToolTimelinePresentation &presentation,
                      bool expanded, std::function<void()> toggle,
                      const TutorialMarkdownLinkHandler &,
                      const TutorialMarkdownCopyHandler &,
                      const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::file_text,
                                  expanded, std::move(toggle)));
  if (presentation.failed && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 240.0F));
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
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
      rows.push_back(
          Text(StringVariant::Format(app::strings::tool_call_diff_truncated,
                                     static_cast<std::int64_t>(
                                         diff.lines.size())))
              .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                   colors::tertiary))
              .With(Padding(EdgeInsets{.top = 12.0F,
                                       .right = 14.0F,
                                       .bottom = 12.0F,
                                       .left = 14.0F})));
      break;
    }
    if (omitted) {
      rows.push_back(Text("⋯")
                         .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                              colors::tertiary))
                         .With(Padding(EdgeInsets{.top = 4.0F,
                                                  .right = 0.0F,
                                                  .bottom = 4.0F,
                                                  .left = 18.0F})));
      omitted = false;
    }
    const auto &line = diff.lines[index];
    const bool added = line.kind == domain::DiffLine::Kind::added;
    const bool removed = line.kind == domain::DiffLine::Kind::removed;
    const Color text_color = added ? colors::diff_add_text
                              : removed ? colors::diff_delete_text
                                        : colors::secondary;
    rows.push_back(
        Row{
            Stack{}.With(Frame{.width = 3.0F}, Grow(),
                         Background(added ? colors::success
                                     : removed ? colors::danger
                                               : Color::Transparent())),
            Text(std::to_string(line.number))
                .Style(TextStyle{Font::Monospace(13.0F), text_color})
                .Align(TextAlign::Trailing)
                .With(Frame{.width = 42.0F},
                      Padding(EdgeInsets{.top = 3.0F,
                                         .right = 10.0F,
                                         .bottom = 3.0F,
                                         .left = 2.0F})),
            Text(line.text)
                .Style(TextStyle{Font::Monospace(13.0F), text_color})
                .With(Padding(EdgeInsets{.top = 3.0F,
                                         .right = 14.0F,
                                         .bottom = 3.0F,
                                         .left = 4.0F})),
        }
            .With(Frame{.min_height = 26.0F},
                  Background(added   ? colors::diff_add_background
                             : removed ? colors::diff_delete_background
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
    rows.push_back(Text("⋯")
                       .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                            colors::tertiary))
                       .With(Padding(EdgeInsets{.top = 4.0F,
                                                .right = 0.0F,
                                                .bottom = 4.0F,
                                                .left = 18.0F})));
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
  std::vector<View> rows;
  const auto diff_id = presentation.diff_id;
  const auto request_diff = context.on_request_diff;
  // Expanding asks for the body first; the card then renders it once the
  // request publishes the loaded lines.
  rows.push_back(LegacyToolHeader(
      presentation, app::images::file_pen_line, expanded,
      [toggle = std::move(toggle), diff_id, request_diff] {
        std::invoke(toggle);
        if (request_diff && !diff_id.empty())
          std::invoke(request_diff, diff_id);
      }));
  if (!expanded)
    return Column(std::move(rows))
        .With(CrossAlign(CrossAxisAlignment::Stretch));

  const DiffEntry *entry = nullptr;
  if (context.diff_cache) {
    const auto found = context.diff_cache->find(presentation.diff_id);
    if (found != context.diff_cache->end())
      entry = &found->second;
  }
  // The store is authoritative: the transcript copy predates the decision.
  const std::string review_state =
      entry != nullptr && !entry->review_state.empty()
          ? entry->review_state
          : presentation.review_state;
  const std::string review_message =
      entry != nullptr && !entry->review_message.empty()
          ? entry->review_message
          : presentation.review_message;
  const bool reverted = review_state == "rejected";
  const bool accepted = review_state == "accepted";
  const bool awaiting_review =
      !presentation.diff_id.empty() && !reverted && !accepted;

  // The legacy card derived its label from the review state, falling back to
  // "Created" when the write produced a file that did not exist before.
  StringResource status = app::strings::tool_call_write_done;
  if (presentation.failed)
    status = app::strings::chat_tool_failed;
  else if (reverted)
    status = app::strings::tool_call_write_reverted;
  else if (awaiting_review)
    status = app::strings::tool_call_status_pending_review;

  std::vector<View> detail_children;
  detail_children.push_back(
      Row{
          Text(StringVariant{status})
              .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                   presentation.failed ? colors::danger
                                                       : colors::secondary)),
          Text(presentation.title)
              .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                   colors::secondary))
              .With(Padding(EdgeInsets{.left = 4.0F})),
          Spacer(),
          Stack{Image(app::images::copy)
                    .Tint(colors::secondary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .OnClick([on_copy, detail = presentation.detail] {
                if (!detail.empty())
                  std::invoke(on_copy, detail);
              })
              .With(Frame{.width = 44.0F, .height = 44.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center), Focusable(),
                    PointerCursor(PointerCursorKind::Hand),
                    Semantics{.label = app::strings::tool_call_copy_file}),
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
        Text(presentation.diff_id.empty()
                 ? app::strings::tool_call_diff_unavailable
                 : app::strings::tool_call_diff_loading)
            .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                 colors::tertiary))
            .With(Padding(EdgeInsets{.top = 10.0F,
                                     .right = 14.0F,
                                     .bottom = 10.0F,
                                     .left = 14.0F})));
  }

  const std::string message = presentation.failed
                                  ? presentation.detail
                                  : review_message;
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
    detail_children.push_back(
        Row{
            Text(app::strings::tool_call_write_revert)
                .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::text))
                .Align(TextAlign::Center)
                .OnClick([on_review, call_id, diff_id] {
                  std::invoke(on_review, call_id, diff_id,
                              std::string{"rejected"});
                })
                .With(Frame{.min_height = 48.0F},
                      Padding(EdgeInsets::Symmetric(14.0F, 0.0F)), Focusable(),
                      PointerCursor(PointerCursorKind::Hand)),
            Text(app::strings::tool_call_write_accept)
                .Style(ChatTextStyle(13.0F, FontWeight::Regular, colors::text))
                .Align(TextAlign::Center)
                .OnClick([on_review, call_id, diff_id] {
                  std::invoke(on_review, call_id, diff_id,
                              std::string{"accepted"});
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
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View DeleteToolRenderer(const ToolTimelinePresentation &presentation,
                        bool expanded, std::function<void()> toggle,
                        const TutorialMarkdownLinkHandler &,
                        const TutorialMarkdownCopyHandler &,
                       const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::trash_2,
                                  expanded, std::move(toggle)));
  if (expanded && !presentation.detail.empty())
    rows.push_back(ToolCodeCard(presentation.detail, presentation, 200.0F));
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
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
    TextStyle style = ChatTextStyle(
        14.0F, FontWeight::Regular,
        item.state == ToolTimelineTodoItem::State::completed
            ? static_cast<Color>(colors::tertiary)
            : static_cast<Color>(colors::text));
    if (item.state == ToolTimelineTodoItem::State::completed)
      style.decoration = TextDecoration::StrikeThrough;
    rows.push_back(Row{TodoIndicator(item.state), Text(item.content).Style(style)}
                       .With(Frame{.min_height = 44.0F}, Spacing(8.0F),
                             Padding(EdgeInsets::Symmetric(0.0F, 4.0F))));
  }
  if (rows.empty()) {
    rows.push_back(Text("No TODO items")
                       .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                            colors::tertiary))
                       .With(Padding(EdgeInsets{.top = 8.0F,
                                                .right = 16.0F,
                                                .bottom = 16.0F,
                                                .left = 16.0F})));
  }
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Padding(EdgeInsets::Symmetric(0.0F, 12.0F)));
}

View AgentToolRenderer(const ToolTimelinePresentation &presentation,
                       bool expanded, std::function<void()> toggle,
                       const TutorialMarkdownLinkHandler &on_link,
                       const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context) {
  std::vector<View> title_rows;
  title_rows.push_back(
      Row{Text(presentation.title)
              .Style(ChatTextStyle(14.0F, FontWeight::Bold, colors::text)),
          Spacer(),
          presentation.running
              ? View{ProgressCircle().With(
                    Frame{.width = 18.0F, .height = 18.0F})}
              : View{Image(presentation.failed ? app::images::x
                                               : app::images::check)
                         .Tint(presentation.failed ? colors::danger
                                                   : colors::success)
                         .With(Frame{.width = 18.0F, .height = 13.0F})},
          Image(expanded ? app::images::chevron_down
                         : app::images::chevron_right)
              .Tint(colors::tertiary)
              .With(Frame{.width = 16.0F, .height = 12.0F})}
          .OnClick(std::move(toggle))
          .With(Frame{.min_height = 48.0F}, Spacing(8.0F),
                Padding(EdgeInsets::Symmetric(16.0F, 8.0F)),
                PointerCursor(PointerCursorKind::Hand)));
  if (expanded) {
    std::vector<View> content;
    if (!presentation.input_detail.empty())
      content.push_back(Text(presentation.input_detail)
                            .Style(ChatTextStyle(13.0F, FontWeight::Regular,
                                                 colors::tertiary)));
    if (!presentation.output_detail.empty())
      content.push_back(
          AssistantMarkdown(presentation.output_detail, true, on_link,
                            on_copy));
    if (content.empty())
      content.push_back(Text(presentation.running ? "Running…" : "Done")
                            .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                                 colors::tertiary)));
    title_rows.push_back(
        ScrollView(Column(std::move(content))
                       .With(CrossAlign(CrossAxisAlignment::Stretch),
                             Spacing(8.0F), Padding(EdgeInsets{
                                               .top = 8.0F,
                                               .right = 16.0F,
                                               .bottom = 16.0F,
                                               .left = 16.0F})))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 400.0F},
                  Border(colors::code_border, 1.0F), ScrollBar()));
  }
  return Column(std::move(title_rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), ClipChildren());
}

View PipelineToolRenderer(const ToolTimelinePresentation &presentation,
                          bool expanded, std::function<void()> toggle,
                          const TutorialMarkdownLinkHandler &on_link,
                          const TutorialMarkdownCopyHandler &on_copy,
                       const ToolRendererContext &context) {
  auto copy = presentation;
  copy.title += "  " + std::to_string(copy.completed_count) + "/" +
                std::to_string(copy.item_count);
  return AgentToolRenderer(copy, expanded, std::move(toggle), on_link, on_copy,
                           context);
}

View GenericToolRenderer(const ToolTimelinePresentation &presentation,
                         bool expanded, std::function<void()> toggle,
                         const TutorialMarkdownLinkHandler &,
                         const TutorialMarkdownCopyHandler &,
                       const ToolRendererContext &context) {
  std::vector<View> rows;
  rows.push_back(LegacyToolHeader(presentation, app::images::mcp, expanded,
                                  std::move(toggle)));
  if (expanded &&
      (!presentation.input_detail.empty() || !presentation.output_detail.empty())) {
    std::vector<View> sections;
    const auto add_section = [&](std::string heading, const std::string &body,
                                 Color color) {
      if (body.empty())
        return;
      sections.push_back(
          Column{Text(std::move(heading))
                     .Style(ChatTextStyle(12.0F, FontWeight::Bold,
                                          colors::tertiary)),
                 SelectionArea(Text(body).Style(
                     TextStyle{Font::Monospace(14.0F), color}))}
              .With(CrossAlign(CrossAxisAlignment::Stretch), Spacing(4.0F),
                    Padding(EdgeInsets{.top = 12.0F,
                                       .right = 14.0F,
                                       .bottom = 12.0F,
                                       .left = 14.0F})));
    };
    add_section("Input", presentation.input_detail, colors::secondary);
    add_section("Output", presentation.output_detail,
                presentation.failed ? static_cast<Color>(colors::danger)
                                    : static_cast<Color>(colors::secondary));
    rows.push_back(ScrollView(Column(std::move(sections))
                                  .With(CrossAlign(
                                      CrossAxisAlignment::Stretch)))
                       .ScrollAxis(Axis::Vertical)
                       .With(Frame{.max_height = 240.0F},
                             Background(colors::code),
                             Border(colors::code_border, 1.0F),
                             CornerRadius(12.0F), ClipChildren(), ScrollBar()));
  }
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
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
  return TutorialMarkdownDocumentView(document, code_wrap, 1.0F, on_link,
                                     on_copy)
      .With(Frame{.max_width = 684.0F});
}

View ReasoningTimelineBlock(
    const domain::AssistantReasoningEvent &reasoning,
    std::string key, const ChatTimelineSettings &settings,
    State<std::vector<std::string>> toggled) {
  const bool expanded = ToggleState(toggled.Get(), key,
                                    settings.thinking_auto_expand);
  View header = Row{
      Text(reasoning.kind == domain::ReasoningKind::summary
               ? app::strings::chat_reasoning_summary
               : app::strings::chat_reasoning_thinking)
          .Style(ChatTextStyle(13.0F, FontWeight::Medium, colors::secondary)),
      Spacer(),
      Image(expanded ? app::images::chevron_down
                     : app::images::chevron_right)
          .Tint(colors::tertiary)
          .With(Frame{.width = 14.0F, .height = 14.0F}),
  };
  header = std::move(header)
               .OnClick([toggled, key] { ToggleKey(toggled, key); })
               .With(Frame{.height = 48.0F},
                     Align(HorizontalAlignment::Stretch,
                           VerticalAlignment::Center),
                     PointerCursor(PointerCursorKind::Hand));
  if (!expanded)
    return header;
  View body = SelectionArea(
      Text(reasoning.text)
          .Style(ChatTextStyle(14.0F, FontWeight::Regular, colors::tertiary)));
  if (settings.thinking_scroll) {
    body = ScrollView(std::move(body))
               .ScrollAxis(Axis::Vertical)
               .With(Frame{.max_height = 180.0F}, ScrollBar());
  }
  return Column{std::move(header), std::move(body)}
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Padding(EdgeInsets{.right = 4.0F, .bottom = 8.0F, .left = 4.0F}));
}

View ToolTimelineCard(const domain::AssistantToolEvent &event,
                      std::string key,
                      State<std::vector<std::string>> toggled,
                      const TutorialMarkdownLinkHandler &on_link,
                      const TutorialMarkdownCopyHandler &on_copy,
                      const ToolRendererContext &context) {
  const auto presentation = PresentToolTimeline(event);
  if (!presentation.visible)
    return Stack{}.With(Frame{.height = 0.0F});
  const bool expanded = ToggleState(toggled.Get(), key,
                                    presentation.initially_expanded);
  return RendererFor(presentation.visual)(
      presentation, expanded,
      [toggled, key = std::move(key)] { ToggleKey(toggled, key); }, on_link,
      on_copy, context);
}

View AssistantTimeline(
    const domain::ChatMessage &message, bool live,
    const ChatTimelineSettings &settings,
    State<std::vector<std::string>> toggled,
    const TutorialMarkdownLinkHandler &on_link,
    const TutorialMarkdownCopyHandler &on_copy,
    const ToolRendererContext &context) {
  const auto presentation = PresentAssistantProcess(
      message, live, settings.process_auto_expand);
  if (!presentation.visible)
    return Stack{}.With(Frame{.height = 0.0F});
  const auto stable_turn_id = message.processing_started_at > 0
                                  ? message.processing_started_at
                                  : static_cast<std::int64_t>(message.id);
  const auto process_key = std::to_string(stable_turn_id) + ":process";
  const bool expanded = ToggleState(toggled.Get(), process_key,
                                    presentation.initially_expanded);
  const auto label = presentation.failed
                         ? app::strings::chat_process_failed
                     : presentation.running
                         ? app::strings::chat_process_working
                         : app::strings::chat_process_completed;
  std::string duration;
  if (presentation.duration_millis > 0) {
    duration = "  " +
               std::to_string(presentation.duration_millis / 1'000) + "." +
               std::to_string((presentation.duration_millis % 1'000) / 100) +
               "s";
  }
  View process_icon = presentation.running
                          ? ProgressCircle().With(
                                Frame{.width = 16.0F, .height = 16.0F})
                          : Image(app::images::brain)
                                .Tint(presentation.failed ? colors::danger
                                                          : colors::secondary)
                                .With(Frame{.width = 17.0F, .height = 17.0F});
  View header = Row{
      std::move(process_icon),
      Text(label).Style(ChatTextStyle(14.0F, FontWeight::Medium)),
      Text(duration).Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                         colors::tertiary)),
      Spacer(),
      Image(expanded ? app::images::chevron_down
                     : app::images::chevron_right)
          .Tint(colors::tertiary)
          .With(Frame{.width = 15.0F, .height = 15.0F}),
  };
  header = std::move(header)
               .OnClick([toggled, process_key] {
                 ToggleKey(toggled, process_key);
               })
               .With(Frame{.min_height = 48.0F}, Spacing(8.0F),
                     PointerCursor(PointerCursorKind::Hand));
  std::vector<View> rows{std::move(header)};
  if (expanded) {
    for (std::size_t index = 0; index < message.timeline.size(); ++index) {
      const auto key = std::to_string(stable_turn_id) + ":" +
                       std::to_string(index);
      std::visit(
          Overloaded{
              [&](const domain::AssistantReasoningEvent &reasoning) {
                rows.push_back(ReasoningTimelineBlock(reasoning, key,
                                                      settings, toggled));
              },
              [&](const domain::AssistantTextEvent &text) {
                if (!text.text.empty())
                  rows.push_back(AssistantMarkdown(
                      text.text, settings.code_wrap_enabled, on_link,
                      on_copy));
              },
              [&](const domain::AssistantToolEvent &tool) {
                rows.push_back(ToolTimelineCard(tool, key, toggled, on_link,
                                                on_copy, context));
              }},
          message.timeline[index]);
    }
    if (message.timeline.empty() && !message.reasoning_content.empty()) {
      rows.push_back(ReasoningTimelineBlock(
          domain::AssistantReasoningEvent{.turn_index = 0,
                                          .text = message.reasoning_content},
          process_key + ":legacy", settings, toggled));
    }
  }
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Padding(EdgeInsets{.right = 2.0F, .bottom = 12.0F, .left = 2.0F}),
            Border(colors::border_light, 0.5F), CornerRadius(10.0F));
}

View MessageBubble(const domain::ChatMessage &message,
                   State<std::optional<std::uint64_t>> action_message,
                   bool multi_select,
                   State<std::vector<std::uint64_t>> selected_messages,
                   MessageActionCallbacks callbacks,
                   const ChatTimelineSettings &timeline_settings,
                   State<std::vector<std::string>> toggled_timeline,
                   const TutorialMarkdownLinkHandler &on_link,
                   const TutorialMarkdownCopyHandler &on_copy,
                   const ToolRendererContext &context,
                   bool live = false) {
  if (message.role == domain::MessageRole::tool)
    return Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  const bool user = message.role == domain::MessageRole::user;
  const bool selected = std::ranges::contains(selected_messages.Get(),
                                              message.id);
  const auto bubble_color = static_cast<Color>(colors::user_bubble);
  const float luminance = bubble_color.red * 0.2126F +
                          bubble_color.green * 0.7152F +
                          bubble_color.blue * 0.0722F;
  const Color user_text = luminance > 0.55F ? static_cast<Color>(colors::text)
                                            : Color::Rgb(237, 240, 242);
  View assistant_text = message.content.empty()
                            ? Stack{}.With(Frame{.height = 0.0F})
                            : AssistantMarkdown(
                                  message.content,
                                  timeline_settings.code_wrap_enabled,
                                  on_link, on_copy);
  View assistant_error = Stack{}.With(Frame{.height = 0.0F});
  if (message.error && !message.error_message.empty()) {
    assistant_error = Text(message.error_message)
                          .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                               colors::danger));
  }
  View assistant = Column{
      AssistantTimeline(message, live, timeline_settings, toggled_timeline,
                        on_link, on_copy, context),
      std::move(assistant_text),
      std::move(assistant_error),
  }.With(CrossAlign(CrossAxisAlignment::Stretch));
  View bubble = user
                    ? Text(message.content)
                          .With(FontSize(16.0F), Foreground(user_text),
                                Padding(EdgeInsets::Symmetric(15.0F, 10.0F)),
                                Background(colors::user_bubble),
                                CornerRadius(18.0F),
                                Frame{.max_width = 684.0F})
                    : std::move(assistant);
  View aligned_bubble =
      user ? Row{Spacer(), bubble} : Row{bubble, Spacer()};
  std::vector<View> content{aligned_bubble,
                            MessageAttachments(message, user)};
  if (!multi_select && action_message.Get() == message.id)
    content.push_back(MessageActionBar(message, callbacks));

  return Column(std::move(content))
      .OnClick([multi_select, selected_messages, id = message.id] {
        if (multi_select)
          ToggleMessageSelection(selected_messages, id);
      })
      .On<LongPressEvents::Started>(
          [action_message, multi_select, selected_messages,
           id = message.id](const LongPressEvent &) {
            if (multi_select) {
              ToggleMessageSelection(selected_messages, id);
              return;
            }
            action_message = action_message.Get() == id
                                 ? std::optional<std::uint64_t>{}
                                 : std::optional<std::uint64_t>{id};
          })
      .With(LongPressGesture{},
            Padding(EdgeInsets{.top = user ? 16.0F : 0.0F,
                               .right = 16.0F,
                               .bottom = user ? 32.0F : 28.0F,
                               .left = 16.0F}),
            Background(selected ? colors::accent_muted
                                : Color::Transparent()),
            CornerRadius(selected ? 12.0F : 0.0F));
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
    const ToolRendererContext &context) {
  static_cast<void>(revision);
  const auto messages = session->Messages();
  if (messages.empty()) {
    return EmptyConversation(navigation);
  }

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
  View streaming = generation_state.phase ==
                           application::GenerationPhase::running
                       ? MessageBubble(streaming_message, action_message, false,
                                       selected_messages, {}, timeline_settings,
                                       toggled_timeline, on_link, on_copy,
                                       context, true)
                       : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  View list = ScrollView(Column{
                             ForEach(messages,
                                     [action_message, multi_select,
                                      selected_messages,
                                      callbacks, timeline_settings,
                                      toggled_timeline, on_link, on_copy,
                                      context](const auto &message) {
                                       return MessageBubble(
                                                  message, action_message,
                                                  multi_select,
                                                  selected_messages, callbacks,
                                                  timeline_settings,
                                                  toggled_timeline, on_link,
                                                  on_copy, context)
                                           .Key(message.id);
                                     }),
                             std::move(streaming),
                         }
                             .With(CrossAlign(CrossAxisAlignment::Stretch),
                                   Padding(EdgeInsets{
                                       .bottom = multi_select ? 60.0F : 0.0F})))
                  .ScrollAxis(Axis::Vertical)
                  .With(Grow(), ScrollBar());
  if (!multi_select)
    return list;

  View selection_bar =
      Row{
          Text::Format(app::strings::model_list_selected_count,
                       selected_messages->size())
              .Style(ChatTextStyle(16.0F)),
          Spacer(),
          MessageActionButton(app::images::download,
                              app::strings::export_button_label,
                              callbacks.export_selected),
          MessageActionButton(
              app::images::x, app::strings::common_close,
              [selected_messages, action_message,
               exit = callbacks.enter_multi_select] {
                selected_messages = std::vector<std::uint64_t>{};
                action_message = std::nullopt;
                if (exit)
                  exit();
              }),
      }
          .With(Frame{.min_height = 60.0F},
                Padding(EdgeInsets::Symmetric(16.0F, 8.0F)),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::elevated),
                Shadow{.color = Color::Rgb(0, 0, 0, 0.18F),
                       .blur_radius = 8.0F});
  return Stack{list, Column{Spacer(), selection_bar}}.With(Grow());
}

void ShowTextSelectionDialog(const DialogHandle &dialogs,
                             std::string content) {
  dialogs.Show([content = std::move(content)](DialogContext dialog) {
    return Column{
        Text(app::strings::dialog_select_text_title)
            .Style(ChatTextStyle(18.0F, FontWeight::Medium)),
        ScrollView(SelectionArea(
                       Text(content).Style(ChatTextStyle(15.0F))))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 500.0F},
                  Padding(EdgeInsets{.top = 16.0F, .bottom = 16.0F})),
        Row{
            Spacer(),
            Button(app::strings::common_close)
                .OnClick([dialog] { dialog.Dismiss(); }),
        },
    }
        .With(Frame{.width = 352.0F, .max_height = 640.0F},
              Padding(24.0F), CrossAlign(CrossAxisAlignment::Stretch),
              Background(colors::elevated), CornerRadius(18.0F),
              Shadow{.color = Color::Rgb(0, 0, 0, 0.28F),
                     .blur_radius = 18.0F});
  });
}

struct ComposerGenerationDependencies final {
  std::shared_ptr<application::ChatSession> session;
  std::shared_ptr<application::GenerationController> generation;
  std::shared_ptr<application::ModelStore> model_store;
  std::shared_ptr<application::McpCompletionLoop> completion_loop;
  std::shared_ptr<application::MemoryContextService> memory_context;
  std::shared_ptr<application::AiBehaviorSettingsRepository> behavior_settings;
  // Feeds {{TODO_STATE}} from the live task list. Legacy
  // `ModelPromptController.renderTodoStateForPrompt()` did the same, so the
  // projection has to be refreshed per request rather than captured once.
  std::shared_ptr<application::TodoStateStore> todo_state;
  // Installed Skill prompts. Legacy `ModelPromptController` folded these into
  // the system prompt on every request; the C++ port keeps that by refreshing
  // them here instead of capturing the text once.
  std::shared_ptr<application::SkillRepository> skills;
};

class ComposerGenerationRunner final
    : public std::enable_shared_from_this<ComposerGenerationRunner> {
public:
  ComposerGenerationRunner(
      ComposerGenerationDependencies dependencies,
      std::shared_ptr<PendingToolReview> pending_review,
      std::shared_ptr<application::PendingMessageQueue> pending_messages,
      TaskScope tasks, State<TaskHandle> active_generation,
      State<std::size_t> revision, std::string current_project_id,
      application::PromptAssemblyContext prompt_context,
      domain::ToolPermissionMode permission_mode, ToastHandle toast)
      : dependencies_(std::move(dependencies)),
        pending_review_(std::move(pending_review)),
        pending_messages_(std::move(pending_messages)), tasks_(std::move(tasks)),
        active_generation_(std::move(active_generation)),
        revision_(std::move(revision)),
        current_project_id_(std::move(current_project_id)),
        prompt_context_(std::move(prompt_context)),
        permission_mode_(permission_mode), toast_(std::move(toast)) {}

  [[nodiscard]] bool Start(application::PendingMessage message) {
    if (dependencies_.generation->State().phase ==
        application::GenerationPhase::running) {
      return false;
    }

    const std::string user_text = message.text;
    auto work = dependencies_.generation->Begin(
        user_text, std::move(message.attachments));
    if (!work)
      return false;

    revision_ += 1;
    const std::string conversation_id{
        dependencies_.session->CurrentConversationId()};
    auto turn = SnapshotConversation(*dependencies_.session,
                                     current_project_id_, conversation_id);
    auto self = shared_from_this();
    active_generation_ = tasks_.Launch(
        [self = std::move(self), conversation_id, user_text,
         turn = std::move(turn), work = std::move(*work)]() mutable {
          return self->Run(std::move(work), std::move(turn),
                           std::move(conversation_id), std::move(user_text));
        });
    return true;
  }

  void CancelAndContinue() {
    active_generation_.Get().Cancel();
    dependencies_.generation->Cancel();
    ResetToolReview();
    revision_ += 1;
    StartNext();
  }

private:
  void ResetToolReview() const {
    pending_review_->request.reset();
    pending_review_->decision.reset();
    pending_review_->submitted = false;
  }

  void FailAndContinue(std::uint64_t generation_id,
                       application::CompletionError error) {
    if (dependencies_.generation->Fail(generation_id, std::move(error)))
      revision_ += 1;
    StartNext();
  }

  void StartNext() {
    auto next = pending_messages_->TakeNext();
    if (!next)
      return;
    revision_ += 1;
    static_cast<void>(Start(std::move(*next)));
  }

  Task<void> Run(application::GenerationWork work,
                 domain::MemoryConversationTurn turn,
                 std::string conversation_id, std::string user_text) {
    const auto selected_id = co_await dependencies_.model_store->SelectedId();
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!selected_id) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = selected_id.error().message});
      co_return;
    }
    if (selected_id->empty()) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = "Please add and select a model first"});
      co_return;
    }

    auto selected_model =
        co_await dependencies_.model_store->Find(*selected_id);
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!selected_model) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = selected_model.error().message});
      co_return;
    }
    if (!selected_model->has_value()) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = "The selected model no longer exists"});
      co_return;
    }

    auto behavior = co_await dependencies_.behavior_settings->Load();
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!behavior) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = behavior.error().message});
      co_return;
    }

    auto context = co_await dependencies_.memory_context->Prepare(
        current_project_id_, user_text, conversation_id,
        behavior->learning_mode);
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!context) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::transport,
              .message = context.error().message});
      co_return;
    }

    auto prompt_context = prompt_context_;
    if (dependencies_.todo_state) {
      auto todo = co_await dependencies_.todo_state->Load();
      if (todo)
        prompt_context.todo_state = application::RenderTodoState(*todo);
    }
    prompt_context.learning_context = context->prompt;
    if (dependencies_.skills) {
      auto extensions = co_await dependencies_.skills->BuildExtensionPrompt();
      if (extensions && !extensions->empty()) {
        // Legacy `SystemPromptProvider.build(homePath, tone, chatMode,
        // extensionContext, ...)`: the extension block occupied the slot the
        // template renders as {{LEARNING_CONTEXT}}.
        if (!prompt_context.learning_context.empty())
          prompt_context.learning_context += "\n\n";
        prompt_context.learning_context += *extensions;
      }
    }
    prompt_context.permission_mode =
        domain::SerializeToolPermissionMode(permission_mode_);
    prompt_context.attachment_history.assign(
        dependencies_.session->Messages().begin(),
        dependencies_.session->Messages().end());
    auto response = co_await dependencies_.completion_loop->Complete(
        application::CompletionRequest{
            .model = std::move(**selected_model),
            .messages = std::move(work.messages),
            .tools = {},
            .reasoning_effort = behavior->reasoning,
            .preserve_reasoning = behavior->preserve_reasoning,
            .stream = true,
            .permission_scope = current_project_id_,
        },
        std::move(prompt_context),
        application::CompletionObserver{
            .on_event =
                [generation = dependencies_.generation,
                 generation_id = work.generation_id,
                 revision = revision_](const application::CompletionEvent &event) {
                  if (generation->Observe(generation_id, event))
                    revision += 1;
                },
            .on_tool_review =
                [pending_review = pending_review_, revision = revision_](
                    application::CompletionObserver::ToolReviewRequest request)
                -> Task<application::CompletionObserver::ToolReviewDecision> {
                  pending_review->request = std::move(request);
                  pending_review->decision.reset();
                  pending_review->submitted = false;
                  revision += 1;
                  while (!pending_review->decision.has_value())
                    co_await Delay(std::chrono::milliseconds{20});
                  const auto decision = *pending_review->decision;
                  pending_review->request.reset();
                  pending_review->decision.reset();
                  pending_review->submitted = false;
                  revision += 1;
                  co_return decision;
                },
        });
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;

    if (!response) {
      FailAndContinue(work.generation_id, std::move(response.error()));
      co_return;
    }

    const std::string assistant_text = response->text;
    const bool completed = dependencies_.generation->Complete(
        work.generation_id, std::move(*response));
    revision_ += 1;
    if (!completed)
      co_return;

    turn.messages.push_back(domain::MemoryConversationMessage{
        .id = "generation:" + std::to_string(work.generation_id),
        .role = "assistant",
        .content = assistant_text,
        .timestamp = NowMilliseconds(),
    });
    turn.updated_at = NowMilliseconds();
    StartNext();
    auto committed = co_await dependencies_.memory_context->CommitTurn(
        context->learning_enabled, std::move(turn), user_text);
    if (!committed)
      toast_.Show(committed.error().message);
  }

  ComposerGenerationDependencies dependencies_;
  std::shared_ptr<PendingToolReview> pending_review_;
  std::shared_ptr<application::PendingMessageQueue> pending_messages_;
  TaskScope tasks_;
  State<TaskHandle> active_generation_;
  State<std::size_t> revision_;
  std::string current_project_id_;
  application::PromptAssemblyContext prompt_context_;
  domain::ToolPermissionMode permission_mode_;
  ToastHandle toast_;
};

View PendingMessages(
    const std::shared_ptr<application::PendingMessageQueue> &queue,
    State<std::size_t> revision) {
  static_cast<void>(revision.Get());
  constexpr std::size_t kVisibleLimit = 4;
  std::vector<View> rows;
  const auto items = queue->Items();
  const auto visible = std::min(items.size(), kVisibleLimit);
  rows.reserve(visible + (items.size() > visible ? 1U : 0U));
  for (std::size_t index = 0; index < visible; ++index) {
    std::string preview = items[index].text;
    if (preview.size() > 30U)
      preview = preview.substr(0U, 30U) + "...";
    preview = std::to_string(index + 1U) + ". " + preview;
    rows.emplace_back(
        Row{
            Stack{}.With(Frame{.width = 3.0F, .height = 20.0F},
                         Background(colors::warning)),
            Text(std::move(preview))
                .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                     Color::Rgb(255, 170, 51)))
                .With(Grow(), ClipChildren()),
            Stack{Image(app::images::x)
                      .Tint(colors::tertiary)
                      .With(Frame{.width = 12.0F, .height = 12.0F})}
                .OnClick([queue, revision, index] {
                  if (queue->Remove(index))
                    revision += 1;
                })
                .With(Frame{.width = 20.0F, .height = 20.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Focusable(), PointerCursor(PointerCursorKind::Hand)),
        }
            .With(Padding(EdgeInsets{.top = 4.0F,
                                     .right = 8.0F,
                                     .bottom = 4.0F,
                                     .left = 16.0F}),
                  Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center),
                  Background(colors::input))
            .Key("pending:" + std::to_string(index)));
  }
  if (items.size() > visible) {
    rows.emplace_back(
        Text::Format(app::strings::common_more_queued, items.size() - visible)
            .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                 Color::Rgb(204, 136, 0)))
            .With(Padding(EdgeInsets{.top = 2.0F,
                                     .bottom = 4.0F,
                                     .left = 16.0F})));
  }
  if (rows.empty())
    return Stack{}.With(Frame{.height = 0.0F});
  return Column(std::move(rows))
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

std::string WithQuote(const std::optional<std::string> &quote,
                      std::string text) {
  if (!quote || quote->empty())
    return text;
  std::string quoted = "> ";
  for (const char character : *quote) {
    quoted += character;
    if (character == '\n')
      quoted += "> ";
  }
  quoted += "\n\n";
  quoted += text;
  return quoted;
}

View QuotePreview(State<std::optional<std::string>> quote) {
  if (!quote.Get())
    return Stack{}.With(Frame{.height = 0.0F});
  std::string preview = *quote.Get();
  if (preview.size() > 80U)
    preview = preview.substr(0U, 80U) + "...";
  return Row{
      Stack{}.With(Frame{.width = 3.0F, .height = 28.0F},
                   Background(colors::accent), CornerRadius(2.0F)),
      Text(std::move(preview))
          .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                               colors::secondary))
          .With(Grow(), ClipChildren()),
      Stack{Image(app::images::x)
                .Tint(colors::tertiary)
                .With(Frame{.width = 14.0F, .height = 14.0F})}
          .OnClick([quote] { quote = std::nullopt; })
          .With(Frame{.width = 24.0F, .height = 24.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 40.0F},
            Padding(EdgeInsets::Symmetric(12.0F, 6.0F)), Spacing(8.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::surface));
}

StringVariant SlashDescription(std::string_view token) {
  if (token == "/chat")
    return app::strings::slash_command_chat_desc;
  if (token == "/plan")
    return app::strings::slash_command_plan_desc;
  if (token == "/agent")
    return app::strings::slash_command_agent_desc;
  return app::strings::slash_command_model_desc;
}

bool SlashModeSelected(std::string_view token, domain::ChatMode mode) {
  return (token == "/chat" && mode == domain::ChatMode::chat) ||
         (token == "/plan" && mode == domain::ChatMode::plan) ||
         (token == "/agent" && mode == domain::ChatMode::agent);
}

StringVariant ReasoningDescription(domain::ReasoningEffort effort) {
  switch (effort) {
  case domain::ReasoningEffort::off:
    return app::strings::slash_command_reasoning_off_desc;
  case domain::ReasoningEffort::automatic:
    return std::string{domain::SerializeReasoningEffort(effort)};
  case domain::ReasoningEffort::low:
    return app::strings::slash_command_reasoning_low_desc;
  case domain::ReasoningEffort::medium:
    return app::strings::slash_command_reasoning_medium_desc;
  case domain::ReasoningEffort::high:
    return app::strings::slash_command_reasoning_high_desc;
  case domain::ReasoningEffort::maximum:
    return app::strings::slash_command_reasoning_max_desc;
  }
  std::unreachable();
}

const domain::ModelConfig *FindSlashModel(
    std::span<const domain::ModelConfig> models, std::string_view id) {
  const auto found =
      std::ranges::find(models, id, &domain::ModelConfig::id);
  return found == models.end() ? nullptr : &*found;
}

std::string SlashModelName(const domain::ModelConfig &model) {
  if (!model.name.empty() && model.name != model.id)
    return model.name;
  if (!model.model_id.empty())
    return model.model_id;
  return model.provider_label;
}

std::string SlashModelDetail(const domain::ModelConfig &model) {
  const auto name = SlashModelName(model);
  if (!model.model_id.empty() && model.model_id != name)
    return model.provider_label + " · " + model.model_id;
  return model.provider_label;
}

struct SlashPopupRow final {
  std::string label;
  StringVariant description;
  bool selected{};
  std::string replacement;
};

[[huxerui::composable]] View SlashSuggestionsPopup(
    PopupContext context, application::SlashSuggestionState state,
    std::vector<domain::ModelConfig> models, std::string selected_model_id,
    domain::ChatMode chat_mode,
    std::function<void(std::string)> select_replacement) {
  StringVariant title = app::strings::slash_command_main_title;
  std::vector<SlashPopupRow> popup_rows;
  std::visit(
      Overloaded{
          [&](application::MainSlashSuggestions main) {
            popup_rows.reserve(main.commands.size());
            for (auto &suggestion : main.commands) {
              popup_rows.push_back(SlashPopupRow{
                  .label = suggestion.token,
                  .description = SlashDescription(suggestion.token),
                  .selected = SlashModeSelected(suggestion.token, chat_mode),
                  .replacement = suggestion.token + " ",
              });
            }
          },
          [&](application::ModelSlashSuggestions model_state) {
            title = app::strings::slash_command_model_title;
            popup_rows.reserve(model_state.model_ids.size());
            for (auto &id : model_state.model_ids) {
              const auto *model = FindSlashModel(models, id);
              if (!model)
                continue;
              popup_rows.push_back(SlashPopupRow{
                  .label = SlashModelName(*model),
                  .description = SlashModelDetail(*model),
                  .selected = id == selected_model_id,
                  .replacement = "/model " + id + " ",
              });
            }
          },
          [&](application::ReasoningSlashSuggestions reasoning_state) {
            title = UseString(app::strings::slash_command_reasoning_title,
                              reasoning_state.model_id);
            popup_rows.reserve(reasoning_state.levels.size());
            for (const auto effort : reasoning_state.levels) {
              const auto name =
                  std::string{domain::SerializeReasoningEffort(effort)};
              popup_rows.push_back(SlashPopupRow{
                  .label = name,
                  .description = ReasoningDescription(effort),
                  .selected = false,
                  .replacement =
                      "/model " + reasoning_state.model_id + " " + name,
              });
            }
          },
      },
      std::move(state));

  std::vector<View> rows;
  rows.reserve(popup_rows.size() + 1U);
  rows.emplace_back(
      Text(title)
          .Style(ChatTextStyle(13.0F, FontWeight::Medium))
          .With(Frame{.height = 22.0F},
                Padding(EdgeInsets{.top = 1.0F,
                                   .right = 16.0F,
                                   .left = 16.0F})));
  for (auto &row : popup_rows) {
    rows.emplace_back(
        Column{
            Row{
                Text(row.label)
                    .Style(ChatTextStyle(13.0F, FontWeight::Medium,
                                         colors::accent))
                    .With(Grow(), ClipChildren()),
                row.selected
                    ? Stack{}.With(Frame{.width = 7.0F, .height = 7.0F},
                                   Background(colors::accent),
                                   CornerRadius(4.0F))
                    : Stack{}.With(Frame{.width = 7.0F, .height = 7.0F}),
            }
                .With(CrossAlign(CrossAxisAlignment::Center)),
            Text(row.description)
                .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                     colors::tertiary))
                .With(Padding(EdgeInsets{.top = 1.0F}), ClipChildren()),
        }
            .OnClick([context, select_replacement,
                      replacement = row.replacement] {
              if (!select_replacement) {
                context.Dismiss();
                return;
              }
              select_replacement(replacement);
            })
            .With(Frame{.min_height = 52.0F},
                  Padding(EdgeInsets::Symmetric(16.0F, 14.0F)),
                  CrossAlign(CrossAxisAlignment::Stretch), Focusable(),
                  PointerCursor(PointerCursorKind::Hand))
            .Key(row.replacement));
  }
  return ScrollView(
             Column(std::move(rows))
                 .With(CrossAlign(CrossAxisAlignment::Stretch),
                       Padding(EdgeInsets::All(8.0F))))
      .ScrollAxis(Axis::Vertical)
      .With(Frame{.width = 328.0F, .max_height = 300.0F},
            Background(colors::input), Border(colors::border_light, 1.0F),
            CornerRadius(14.0F), ClipChildren(),
            Shadow{.color = Color::Rgb(0, 0, 0, 0.18F),
                   .offset = Point{.x = 0.0F, .y = 2.0F},
                   .blur_radius = 8.0F,
                   .spread = 0.0F});
}

[[huxerui::composable]] View Composer(
    State<TextEditingValue> draft,
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    const std::shared_ptr<application::ModelStore> &model_store,
    const std::shared_ptr<application::McpCompletionLoop> &completion_loop,
    std::optional<bool> has_selected_model, TaskScope tasks,
    State<TaskHandle> active_generation, State<std::size_t> revision,
    State<std::vector<domain::InputAttachment>> selected_attachments,
    std::function<void()> show_attachment_picker,
    const std::shared_ptr<application::MemoryContextService> &memory_context,
    const std::shared_ptr<application::AiBehaviorSettingsRepository>
        &behavior_settings,
    const std::shared_ptr<application::TodoStateStore> &todo_state,
    const std::shared_ptr<application::SkillRepository> &skills,
    const std::shared_ptr<PendingToolReview> &pending_review,
    const std::shared_ptr<application::PendingMessageQueue> &pending_messages,
    State<std::optional<std::string>> quote_text,
    std::function<bool(std::string_view)> handle_slash_command,
    domain::ChatMode chat_mode,
    State<std::vector<domain::ModelConfig>> slash_models,
    State<std::string> slash_selected_model_id,
    domain::InputSettings input_settings, std::string current_project_id,
    application::PromptAssemblyContext prompt_context,
    domain::ToolPermissionMode permission_mode, ToastHandle toast) {
  auto runner = std::make_shared<ComposerGenerationRunner>(
      ComposerGenerationDependencies{
          .session = session,
          .generation = generation,
          .model_store = model_store,
          .completion_loop = completion_loop,
          .memory_context = memory_context,
          .behavior_settings = behavior_settings,
          .todo_state = todo_state,
          .skills = skills,
      },
      pending_review, pending_messages, tasks, active_generation, revision,
      std::move(current_project_id), std::move(prompt_context), permission_mode,
      toast);
  const auto slash_popup = UsePopup();
  auto slash_layer = UseState(std::optional<LayerId>{});

  const bool generating =
      generation->State().phase == application::GenerationPhase::running;
  const bool has_content = HasVisibleText(draft->text) ||
                           !selected_attachments->empty();

  const auto dismiss_slash = [slash_popup, slash_layer] {
    if (slash_layer.Get())
      slash_popup.Dismiss(*slash_layer.Get());
    slash_layer = std::nullopt;
  };
  using SlashPresenter = std::function<void(std::string_view)>;
  const auto slash_presenter = std::make_shared<SlashPresenter>();
  const std::weak_ptr<SlashPresenter> weak_slash_presenter = slash_presenter;
  *slash_presenter =
      [slash_popup, slash_layer, draft, chat_mode, slash_models,
       slash_selected_model_id, generating,
       weak_slash_presenter](std::string_view text) {
        auto state = generating
                         ? std::optional<application::SlashSuggestionState>{}
                         : application::ResolveSlashSuggestionState(
                               text, slash_models.Get());
        if (!state) {
          if (slash_layer.Get())
            slash_popup.Dismiss(*slash_layer.Get());
          slash_layer = std::nullopt;
          return;
        }
        auto factory =
            [state = std::move(*state), models = slash_models.Get(),
             selected_model_id = slash_selected_model_id.Get(), chat_mode,
             draft, weak_slash_presenter](PopupContext context) mutable {
              return SlashSuggestionsPopup(
                  context, std::move(state), std::move(models),
                  std::move(selected_model_id), chat_mode,
                  [draft, weak_slash_presenter](std::string replacement) {
                    draft = TextEditingValue::FromText(replacement);
                    if (const auto presenter = weak_slash_presenter.lock())
                      (*presenter)(replacement);
                  });
            };
        if (slash_layer.Get() &&
            slash_popup.Update(*slash_layer.Get(), factory)) {
          return;
        }
        slash_layer = slash_popup.Show(
            std::move(factory),
            PopupOptions{
                .placement = {AnchorSide::Above, AnchorAlignment::Start},
                .gap = 8.0F,
                .viewport_margin = 16.0F,
                .offset = {},
                .dismiss_on_outside_press = true,
                .dismiss_on_cancel = true,
                .trap_focus = false,
                .retain_anchor_focus = true,
                .on_dismiss_request = {},
            });
      };

  auto send = [draft, selected_attachments, pending_messages, runner,
               handle_slash_command = std::move(handle_slash_command),
               quote_text, generating, has_content, revision, dismiss_slash] {
    dismiss_slash();
    const auto submitted_text = WithQuote(quote_text.Get(), draft->text);
    if (generating) {
      if (!has_content) {
        runner->CancelAndContinue();
        return;
      }
      auto queued = pending_messages->Enqueue(submitted_text,
                                              selected_attachments.Get());
      if (!queued)
        return;
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      quote_text = std::nullopt;
      revision += 1;
      return;
    }
    if (!has_content)
      return;
    if (handle_slash_command && handle_slash_command(submitted_text)) {
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      quote_text = std::nullopt;
      revision += 1;
      return;
    }
    if (runner->Start(application::PendingMessage{
            .text = submitted_text,
            .attachments = selected_attachments.Get(),
        })) {
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      quote_text = std::nullopt;
    }
  };

  const bool can_send = generating || has_content;

  std::vector<View> attachment_chips;
  attachment_chips.reserve(selected_attachments->size());
  for (const auto &attachment : selected_attachments.Get()) {
    attachment_chips.emplace_back(
        Row{
            Text(attachment.Name())
                .Style(ChatTextStyle(13.0F, FontWeight::Medium,
                                     colors::secondary))
                .With(Frame{.max_width = 170.0F, .max_height = 18.0F},
                      ClipChildren()),
            Stack{Image(app::images::x)
                      .Tint(colors::tertiary)
                      .With(Frame{.width = 12.0F, .height = 12.0F})}
                .OnClick([selected_attachments, path = attachment.Path(),
                          source = attachment.Source()] {
                  selected_attachments.Update(
                      [&](std::vector<domain::InputAttachment> &current) {
                        std::erase_if(current, [&](const auto &candidate) {
                          return candidate.Matches(path, source);
                        });
                      });
                })
                .With(Frame{.width = 18.0F, .height = 18.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Focusable(), PointerCursor(PointerCursorKind::Hand)),
        }
            .With(Frame{.height = 34.0F},
                  Padding(EdgeInsets{.right = 8.0F, .left = 12.0F}),
                  Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center),
                  Background(colors::input), Border(colors::border, 1.0F),
                  CornerRadius(17.0F))
            .Key(attachment.Source() + ":" + attachment.Path()));
  }

  View attachment_row = Stack{}.With(Frame{.height = 0.0F});
  if (!attachment_chips.empty()) {
    attachment_row = ScrollView(Row(std::move(attachment_chips))
                                    .With(Spacing(8.0F)))
                         .ScrollAxis(Axis::Horizontal)
                         .With(Frame{.height = 42.0F},
                               Padding(EdgeInsets{.bottom = 8.0F}));
  }

  return Column{
      std::move(attachment_row),
      Column{
          QuotePreview(quote_text),
          PendingMessages(pending_messages, revision),
          Row{
              ComposerAction(app::images::plus, colors::secondary,
                             Color::Transparent(), !generating,
                             std::move(show_attachment_picker)),
              TextField(draft)
                  .Placeholder(has_selected_model.value_or(true)
                                   ? app::strings::composer_hint_default
                                   : app::strings::composer_hint_no_model)
                  .Variant(TextFieldVariant::Standard)
                  .LineLimits(TextFieldLineLimits::MultiLine(1, 3))
                  .InputConfiguration(TextInputConfiguration{
                      .action = input_settings.enter_key ==
                                        domain::EnterKeyBehavior::send
                                    ? TextInputAction::Send
                                    : TextInputAction::Newline,
                      .multiline = true,
                  })
                  .VerticalAlign(TextVerticalAlign::Center)
                  .OnChanged([draft, slash_presenter](
                                 const TextEditingValue &value) {
                    draft = value;
                    (*slash_presenter)(value.text);
                  })
                  .OnSubmitted(send)
                  .With(Frame{.min_height = 44.0F}, Grow(),
                        slash_popup.Anchor()),
              ComposerAction(
                  generating && !has_content ? app::images::stop
                                             : app::images::arrow_up,
                  can_send ? colors::text_on_color : colors::secondary,
                  can_send ? colors::accent : Color::Transparent(), can_send,
                  send),
          } // Match the legacy 148px composer body at the 420dpi reference
            // density. A 56dp minimum rasterizes two pixels short here and
            // makes both the editor text and circular actions look low.
              .With(Frame{.min_height = 56.76F},
                    Padding(EdgeInsets::Symmetric(8.0F, 6.0F)),
                    CrossAlign(CrossAxisAlignment::End)),
      }
          .With(Frame{.min_height = 56.76F},
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::input), CornerRadius(20.0F)),
  }
      .With(Padding(EdgeInsets{
                .top = 14.0F, .right = 20.0F, .bottom = 20.0F, .left = 20.0F}),
            Background(colors::background));
}

View GenerationError(const application::GenerationController &generation,
                     std::size_t revision) {
  static_cast<void>(revision);
  if (generation.State().phase != application::GenerationPhase::failed ||
      generation.State().error.empty()) {
    return Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  }
  return Text(generation.State().error)
      .Style(ChatTextStyle(12.0F, FontWeight::Regular, colors::danger))
      .With(Padding(EdgeInsets{
          .top = 4.0F, .right = 20.0F, .bottom = 0.0F, .left = 20.0F}));
}

} // namespace

[[huxerui::composable]] View ChatScreen(
    std::function<void()> open_drawer, State<TextEditingValue> draft,
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    const std::shared_ptr<application::ModelStore> &model_store,
    const std::shared_ptr<application::McpCompletionLoop> &completion_loop,
    const std::shared_ptr<application::StoragePermissionService>
        &storage_permission,
    std::optional<bool> has_selected_model, State<TaskHandle> active_generation,
    State<std::size_t> revision,
    const std::shared_ptr<application::PendingMessageQueue> &pending_messages,
    State<DrawerModel> workspace,
    const std::shared_ptr<application::MemoryContextService> &memory_context,
    const std::shared_ptr<application::AiBehaviorSettingsRepository>
        &behavior_settings,
    const std::shared_ptr<application::TodoStateStore> &todo_state,
    const std::shared_ptr<application::SkillRepository> &skills,
    const std::shared_ptr<application::ContextCompactionService>
        &compaction_service,
    const std::shared_ptr<application::DiffStore> &diff_store,
    const std::shared_ptr<application::DiffReviewService> &diff_review,
    const std::shared_ptr<application::OutputSettingsService> &output_settings,
    const std::shared_ptr<application::ToolPermissionService>
        &tool_permissions,
    const std::shared_ptr<application::ChatModeService> &chat_modes,
    State<application::ChatInteractionModeState> interaction_mode,
    domain::InputSettings input_settings,
    std::string current_project_id,
    application::PromptAssemblyContext prompt_context,
    std::string project_label,
    std::function<void()> show_project_picker) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto bottom_sheets = UseBottomSheet();
  const auto dialogs = UseDialog();
  const auto clipboard = UseApplication().Clipboard();
  const auto share_text = UseService<application::ShareTextService>();
  const auto external_link = UseService<application::ExternalLinkService>();
  const auto export_service = UseService<application::ChatExportService>();
  auto compaction_busy = UseState(std::make_shared<std::atomic<bool>>(false));
  auto attachment_visible = UseState(false);
  auto more_visible = UseState(false);
  auto permission_visible = UseState(false);
  auto attachment_layer = UseState(std::optional<LayerId>{});
  auto more_layer = UseState(std::optional<LayerId>{});
  auto permission_layer = UseState(std::optional<LayerId>{});
  auto permission_state = UseState(application::ToolPermissionState{});
  auto external_storage_granted = UseState(false);
  auto selected_attachments =
      UseState(std::vector<domain::InputAttachment>{});
  auto expanded_attachment_directories = UseState(std::vector<std::string>{});
  auto pending_review = UseState(std::make_shared<PendingToolReview>());
  auto quote_text = UseState(std::optional<std::string>{});
  auto action_message = UseState(std::optional<std::uint64_t>{});
  auto multi_select = UseState(false);
  auto selected_messages = UseState(std::vector<std::uint64_t>{});
  auto slash_models = UseState(std::vector<domain::ModelConfig>{});
  auto slash_selected_model_id = UseState(std::string{});
  auto timeline_settings = UseState(ChatTimelineSettings{});
  auto toggled_timeline = UseState(std::vector<std::string>{});
  const auto tasks = UseTaskScope();
  // Diff bodies live in the store, not in the transcript. `Lifecycle` runs
  // outside composition (the task scope forbids launching during composition)
  // and re-runs whenever `revision` moves on, which is also when a new tool
  // result may have brought a fresh record id.
  auto diff_cache = UseState(std::make_shared<DiffCache>());
  const auto diff_pending =
      UseState(std::make_shared<std::set<std::string>>()).Get();
  Lifecycle([tasks, session, revision, diff_store, diff_cache, diff_pending] {
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
    tasks.Launch(LoadDiffs(diff_store, diff_cache, diff_pending,
                           std::move(wanted)));
  });

  // Same loader, driven by the card's expand event.
  auto request_diff = [tasks, diff_store, diff_cache, diff_pending](
                          std::string diff_id) {
    if (!diff_store || diff_id.empty() || diff_cache.Get()->contains(diff_id) ||
        diff_pending->contains(diff_id))
      return;
    diff_pending->insert(diff_id);
    tasks.Launch(LoadDiffs(diff_store, diff_cache, diff_pending,
                           std::vector<std::string>{std::move(diff_id)}));
  };

  const CompactionLabels compaction_labels{
      .failed_prefix = UseString(app::strings::context_compact_failed, ""),
      .done = UseString(app::strings::context_compact_done),
  };
  const auto toast = UseToast();

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
      });
    });
  });
  Lifecycle([tasks, model_store, slash_models, slash_selected_model_id,
             toast] {
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
  auto context_usage_visible = UseState(false);
  auto context_usage_layer = UseState(std::optional<LayerId>{});
  const ControlledBottomSheet context_usage_sheet(
      bottom_sheets, context_usage_visible, context_usage_layer);
  const ControlledBottomSheet permission_sheet(
      bottom_sheets, permission_visible, permission_layer);

  const auto storage_permission_description =
      UseString(app::strings::permission_mode_storage_required);

  const auto set_permission_mode =
      [tasks, chat_modes, permission_state, interaction_mode,
       toast](domain::ToolPermissionMode mode) {
        tasks.Launch([chat_modes, permission_state, interaction_mode, mode,
                      toast]() -> Task<void> {
          auto saved = co_await chat_modes->SetPermissionMode(mode);
          if (!saved) {
            toast.Show(saved.error().message);
            co_return;
          }
          interaction_mode = *saved;
          permission_state.Update([&saved](auto &state) {
            state.mode = saved->permission_mode;
          });
        });
      };

  const auto set_chat_mode =
      [tasks, chat_modes, permission_state, interaction_mode,
       toast](domain::ChatMode mode) {
        tasks.Launch([chat_modes, permission_state, interaction_mode, mode,
                      toast]() -> Task<void> {
          auto saved = co_await chat_modes->SetChatMode(mode);
          if (!saved) {
            toast.Show(saved.error().message);
            co_return;
          }
          interaction_mode = *saved;
          permission_state.Update([&saved](auto &state) {
            state.mode = saved->permission_mode;
          });
        });
      };

  const auto handle_slash_command =
      [tasks, set_chat_mode, model_store, behavior_settings, revision,
       slash_selected_model_id, toast](std::string_view input) {
        auto parsed = application::ParseSlashCommand(input);
        if (!parsed)
          return false;
        std::visit(
            Overloaded{
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
                      auto reasoning = co_await behavior_settings->SetReasoning(
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

  const auto clear_command_grants =
      [tasks, tool_permissions, permission_state, toast] {
        tasks.Launch([tool_permissions, permission_state,
                      toast]() -> Task<void> {
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
                           toast](bool visible,
                                  std::function<void()> dismiss) {
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
                   external_storage_granted, storage_permission, toast](
                      ChatPermissionAction action) {
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

  auto show_more = [more_sheet, navigation, session, generation,
                    pending_messages, active_generation, revision, dialogs, toast,
                    export_service, multi_select, selected_messages,
                    action_message, compaction_service, tasks, compaction_busy,
                    compaction_labels] {
    more_sheet.Show([navigation, session, generation, pending_messages,
                     active_generation, revision, dialogs, toast, export_service,
                     multi_select, selected_messages, action_message,
                     compaction_service, tasks, compaction_busy,
                     compaction_labels](bool visible,
                                        std::function<void()> dismiss) {
      return ChatMoreMenu(
          ChatMoreMenuState{.visible = visible, .available = {}},
          ChatOverlayCallbacks<ChatMoreAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [navigation, session, generation, pending_messages,
                   active_generation, revision, dialogs, toast, export_service,
                   multi_select, selected_messages, compaction_service, tasks,
                   compaction_busy, compaction_labels,
                   action_message](ChatMoreAction action) {
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
                      session->Clear();
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
                          StringVariant{app::strings::dialog_export_format_title},
                          export_service->Options(),
                          [export_service, messages, toast](std::string id) {
                            const auto result = export_service->Export(
                                id, std::span<const domain::ChatMessage>{
                                        messages});
                            if (!result.Succeeded()) {
                              toast.Show(app::strings::toast_chat_export_failed);
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
                      // Legacy ContextCompactionController.showCompactConfirmation()
                      // confirms first, then runs the manual compaction.
                      dialogs.Show(
                          app::strings::sheet_more_compact,
                          app::strings::context_compact_confirm_desc,
                          app::strings::context_compact_confirm,
                          app::strings::common_cancel,
                          [compaction_service, session, generation, revision,
                           toast, tasks, compaction_busy,
                           compaction_labels] {
                            if (generation->State().phase ==
                                application::GenerationPhase::running)
                              return;
                            auto started = StartManualContextCompaction(
                                compaction_service, session, tasks, toast,
                                compaction_busy, compaction_labels);
                            if (started)
                              revision += 1;
                          },
                          [] {});
                      break;
                    }
                  },
          });
    });
  };

  auto show_attachments = [attachment_sheet, workspace, selected_attachments,
                           expanded_attachment_directories] {
    std::optional<ChatAttachmentNode> initial_tree;
    if (workspace->file_tree.has_value()) {
      initial_tree = ToAttachmentNode(*workspace->file_tree);
      std::vector<std::string> expanded;
      CollectExpandedDirectories(*initial_tree, expanded);
      expanded_attachment_directories = std::move(expanded);
    }

    attachment_sheet.Show(
        [workspace, selected_attachments, expanded_attachment_directories](
            bool visible, std::function<void()> dismiss) {
          std::optional<ChatAttachmentNode> tree;
          if (workspace->file_tree.has_value()) {
            tree = ToAttachmentNode(*workspace->file_tree);
          }
          return ChatAttachmentPicker(
              ChatAttachmentPickerState{
                  .visible = visible,
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
                            [&file](std::vector<domain::InputAttachment>
                                        &attachments) {
                              ToggleAttachment(attachments, file);
                            });
                      },
              });
        });
  };

  const MessageActionCallbacks message_actions{
      .copy = [clipboard, toast](const domain::ChatMessage &message) {
        if (clipboard && clipboard->WriteText(message.content))
          toast.Show(app::strings::toast_copied);
      },
      .quote = [quote_text, action_message](const domain::ChatMessage &message) {
        quote_text = message.content;
        action_message = std::nullopt;
      },
      .share = [share_text, toast](const domain::ChatMessage &message) {
        if (!share_text || !share_text->Share(message.content))
          toast.Show(app::strings::toast_share_failed);
      },
      .select_text = [dialogs](const domain::ChatMessage &message) {
        ShowTextSelectionDialog(dialogs, message.content);
      },
      .enter_multi_select = [multi_select, selected_messages,
                             action_message] {
        multi_select = !multi_select.Get();
        selected_messages = std::vector<std::uint64_t>{};
        action_message = std::nullopt;
      },
      .recall = [session, generation, draft, selected_attachments, quote_text,
                 action_message, revision](const domain::ChatMessage &message) {
        if (generation->State().phase ==
            application::GenerationPhase::running) {
          return;
        }
        auto recalled = session->RecallUserMessage(message.id);
        if (!recalled)
          return;
        draft = TextEditingValue::FromText(recalled->content);
        selected_attachments = recalled->attachments;
        quote_text = std::nullopt;
        action_message = std::nullopt;
        revision += 1;
      },
      .export_selected = [session, selected_messages, dialogs, toast,
                          export_service] {
        std::vector<domain::ChatMessage> messages;
        for (const auto &message : session->Messages()) {
          if (std::ranges::contains(selected_messages.Get(), message.id))
            messages.push_back(message);
        }
        if (messages.empty()) {
          toast.Show(app::strings::toast_chat_empty_export);
          return;
        }
        // Legacy `ShareController.showFormatPicker` served both the whole-chat
        // export and the multi-select export, so both entry points share the
        // same format list.
        dialogs.Show(
            ExportFormatDialog,
            StringVariant{app::strings::dialog_export_format_title},
            export_service->Options(),
            [export_service, messages,
             toast](std::string id) {
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
  if (generation->State().phase == application::GenerationPhase::running &&
      pending_review.Get()->request) {
    const auto &request = *pending_review.Get()->request;
    composer_or_review = ToolApprovalView(
        ToolApprovalViewState{
            .tool_call_id = request.call.id,
            .tool_name = request.call.name,
            .arguments = request.call.arguments_json,
            .can_allow_permanently = request.can_allow_always,
            .submitted = pending_review.Get()->submitted,
        },
        ToolApprovalCallbacks{
            .on_reject = [pending = pending_review.Get(), revision](
                             std::string id) {
              ResolveToolReview(
                  pending, id,
                  application::CompletionObserver::ToolReviewDecision::reject,
                  revision);
            },
            .on_allow_once = [pending = pending_review.Get(), revision](
                                 std::string id) {
              ResolveToolReview(
                  pending, id,
                  application::CompletionObserver::ToolReviewDecision::
                      allow_once,
                  revision);
            },
            .on_allow_always = [pending = pending_review.Get(), revision](
                                   std::string id) {
              ResolveToolReview(
                  pending, id,
                  application::CompletionObserver::ToolReviewDecision::
                      allow_always,
                  revision);
            },
        });
  } else {
    composer_or_review = Composer(
        draft, session, generation, model_store, completion_loop,
        has_selected_model, tasks, active_generation, revision,
        selected_attachments, show_attachments, memory_context,
        behavior_settings, todo_state, skills, pending_review.Get(),
        pending_messages,
        quote_text,
        handle_slash_command, interaction_mode->chat_mode, slash_models,
        slash_selected_model_id, input_settings, current_project_id,
        std::move(prompt_context), permission_state->mode, toast);
  }

  const TutorialMarkdownLinkHandler open_markdown_link =
      [navigation, external_link,
       browser_mode = timeline_settings->browser_mode,
       javascript_enabled = timeline_settings->browser_javascript_enabled](
          const Uri &target) {
        if (browser_mode == application::BrowserMode::external) {
          external_link->Open(target.ToString());
          return;
        }
        navigation.Push(domain::AppRoute::Browser(target.ToString(),
                                                  javascript_enabled));
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
                        diff_pending](std::string tool_call_id,
                                      std::string diff_id,
                                      std::string state) {
    if (!diff_review || tool_call_id.empty())
      return;
    tasks.Launch([diff_review, diff_store, tool_call_id = std::move(tool_call_id),
                  diff_id = std::move(diff_id), state = std::move(state),
                  revision, diff_cache,
                  diff_pending]() -> Task<void> {
      co_await diff_review->Review(std::move(tool_call_id), std::move(state),
                                   std::move(diff_id));
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
                  Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                               Background(colors::tertiary),
                               CornerRadius(2.0F)),
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
                   message_actions, timeline_settings.Get(),
                   toggled_timeline, open_markdown_link, copy_code,
                   ToolRendererContext{.diff_cache = diff_cache.Get(),
                                       .on_request_diff = request_diff,
                                       .on_review = review_change}),
      GenerationError(*generation, revision.Get()),
      std::move(composer_or_review),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background));
}

} // namespace linecode::presentation
