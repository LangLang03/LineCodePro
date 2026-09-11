#include "presentation/components/chat_screen.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <functional>
#include <iterator>
#include <memory>
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
#include "application/generation_controller.h"
#include "application/memory_context_service.h"
#include "application/mcp_completion_loop.h"
#include "application/pending_message_queue.h"
#include "application/ports/model_store.h"
#include "application/ports/share_text.h"
#include "application/ports/storage_permission.h"
#include "application/prompt_request_composer.h"
#include "application/slash_command_catalog.h"
#include "application/tool_permission_service.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/components/chat_overlays.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/components/tool_approval_view.h"
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

View Header(
    std::function<void()> open_drawer,
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    const std::shared_ptr<application::PendingMessageQueue> &pending_messages,
    State<TaskHandle> active_generation, State<std::size_t> revision,
    std::function<void()> show_permissions, std::function<void()> show_more,
    std::string project_label, std::function<void()> show_project_picker) {
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

View AssistantMarkdown(std::string_view markdown) {
  infrastructure::TutorialMarkdownParser parser;
  const auto document = parser.Parse(markdown);
  return TutorialMarkdownDocumentView(document, true)
      .With(Frame{.max_width = 684.0F});
}

View MessageBubble(const domain::ChatMessage &message,
                   State<std::optional<std::uint64_t>> action_message,
                   bool multi_select,
                   State<std::vector<std::uint64_t>> selected_messages,
                   MessageActionCallbacks callbacks) {
  const bool user = message.role == domain::MessageRole::user;
  const bool selected = std::ranges::contains(selected_messages.Get(),
                                              message.id);
  const auto bubble_color = static_cast<Color>(colors::user_bubble);
  const float luminance = bubble_color.red * 0.2126F +
                          bubble_color.green * 0.7152F +
                          bubble_color.blue * 0.0722F;
  const Color user_text = luminance > 0.55F ? static_cast<Color>(colors::text)
                                            : Color::Rgb(237, 240, 242);
  View bubble = message.content.empty()
                    ? Stack{}.With(Frame{.height = 0.0F})
                : user
                    ? Text(message.content)
                          .With(FontSize(16.0F), Foreground(user_text),
                                Padding(EdgeInsets::Symmetric(15.0F, 10.0F)),
                                Background(colors::user_bubble),
                                CornerRadius(18.0F),
                                Frame{.max_width = 684.0F})
                    : AssistantMarkdown(message.content);
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
    MessageActionCallbacks callbacks) {
  static_cast<void>(revision);
  const auto messages = session->Messages();
  if (messages.empty()) {
    return EmptyConversation(navigation);
  }

  const auto &generation_state = generation->State();
  View streaming =
      generation_state.phase == application::GenerationPhase::running &&
              !generation_state.streamed_text.empty()
          ? MessageBubble(domain::ChatMessage{
                .id = generation_state.generation_id,
                .role = domain::MessageRole::assistant,
                .content = generation_state.streamed_text,
                .attachments = {},
            }, action_message, false, selected_messages, {})
          : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F});
  View list = ScrollView(Column{
                             ForEach(messages,
                                     [action_message, multi_select,
                                      selected_messages,
                                      callbacks](const auto &message) {
                                       return MessageBubble(
                                                  message, action_message,
                                                  multi_select,
                                                  selected_messages, callbacks)
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
    prompt_context.learning_context = context->prompt;
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
            .reasoning_effort = domain::ReasoningEffort::medium,
            .preserve_reasoning = false,
            .stream = true,
            .permission_scope = current_project_id_,
        },
        std::move(prompt_context),
        application::CompletionObserver{
            .on_text_delta =
                [generation = dependencies_.generation,
                 generation_id = work.generation_id,
                 revision = revision_](std::string delta) {
                  if (generation->AppendTextDelta(generation_id,
                                                  std::move(delta)))
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
  const auto tasks = UseTaskScope();
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
                    pending_messages, active_generation, revision] {
    more_sheet.Show([navigation, session, generation, pending_messages,
                     active_generation, revision](
                        bool visible, std::function<void()> dismiss) {
      return ChatMoreMenu(
          ChatMoreMenuState{.visible = visible, .available = {}},
          ChatOverlayCallbacks<ChatMoreAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [navigation, session, generation, pending_messages,
                   active_generation, revision](ChatMoreAction action) {
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
                    case ChatMoreAction::export_chat:
                    case ChatMoreAction::select_messages_to_export:
                    case ChatMoreAction::compact_context:
                      // These typed actions are ready for their
                      // application-service ports.
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
      .export_selected = [session, selected_messages, share_text, toast] {
        std::string text;
        for (const auto &message : session->Messages()) {
          if (!std::ranges::contains(selected_messages.Get(), message.id))
            continue;
          if (!text.empty())
            text += "\n\n";
          text += message.role == domain::MessageRole::user ? "User:\n"
                                                            : "Assistant:\n";
          text += message.content;
        }
        if (text.empty())
          return;
        if (!share_text || !share_text->Share(text))
          toast.Show(app::strings::toast_share_failed);
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
        behavior_settings, pending_review.Get(), pending_messages, quote_text,
        handle_slash_command, interaction_mode->chat_mode, slash_models,
        slash_selected_model_id, input_settings, current_project_id,
        std::move(prompt_context), permission_state->mode, toast);
  }

  return Column{
      Header(std::move(open_drawer), session, generation, pending_messages,
             active_generation, revision, show_permission, show_more,
             std::move(project_label), std::move(show_project_picker)),
      Conversation(session, generation, revision.Get(), navigation,
                   action_message, multi_select.Get(), selected_messages,
                   message_actions),
      GenerationError(*generation, revision.Get()),
      std::move(composer_or_review),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background));
}

} // namespace linecode::presentation
