#include "presentation/components/chat_screen.h"

#include <algorithm>
#include <cctype>
#include <functional>
#include <iterator>
#include <optional>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/chat_session.h"
#include "presentation/components/chat_overlays.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

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

View Header(std::function<void()> open_drawer,
            const std::shared_ptr<application::ChatSession> &session,
            State<std::size_t> revision, std::function<void()> show_permissions,
            std::function<void()> show_more) {
  auto reset_conversation = [session, revision] {
    session->Clear();
    revision += 1;
  };

  return Row{
      HeaderAction(app::images::menu, 19.0F, std::move(open_drawer)),
      Row{
          Text(app::strings::header_project_default)
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
          .OnClick([] {})
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

View MessageBubble(const domain::ChatMessage &message) {
  const bool user = message.role == domain::MessageRole::user;
  View bubble =
      Text(message.content)
          .With(FontSize(13.0F),
                Foreground(user ? colors::text_on_color : colors::text),
                Padding(EdgeInsets::Symmetric(14.0F, 10.0F)),
                Background(user ? colors::user_bubble : colors::ai_bubble),
                CornerRadius(18.0F), Frame{.max_width = 620.0F});

  if (user) {
    return Row{Spacer(), bubble}.With(
        Padding(EdgeInsets::Symmetric(16.0F, 6.0F)));
  }
  return Row{bubble, Spacer()}.With(
      Padding(EdgeInsets::Symmetric(16.0F, 6.0F)));
}

View Conversation(
    const std::shared_ptr<application::ChatSession> &session,
    std::size_t revision,
    const RouteNavigationController<domain::AppRoute> &navigation) {
  static_cast<void>(revision);
  const auto messages = session->Messages();
  if (messages.empty()) {
    return EmptyConversation(navigation);
  }

  return ScrollView(Column{
                        ForEach(messages,
                                [](const domain::ChatMessage &message) {
                                  return MessageBubble(message).Key(message.id);
                                }),
                    }
                        .With(CrossAlign(CrossAxisAlignment::Stretch)))
      .ScrollAxis(Axis::Vertical)
      .With(Grow(), ScrollBar());
}

View Composer(State<TextEditingValue> draft,
              const std::shared_ptr<application::ChatSession> &session,
              State<std::size_t> revision,
              std::function<void()> show_attachment_picker) {
  auto send = [draft, session, revision] {
    auto result = session->Send(draft->text);
    if (result.has_value()) {
      draft = TextEditingValue::FromText("");
      revision += 1;
    }
  };

  const bool can_send = HasVisibleText(draft->text);

  return Column{
      Row{
          ComposerAction(app::images::plus, colors::secondary,
                         Color::Transparent(), true,
                         std::move(show_attachment_picker)),
          TextField(draft)
              .Placeholder(app::strings::composer_hint_no_model)
              .Variant(TextFieldVariant::Standard)
              .LineLimits(TextFieldLineLimits::MultiLine(1, 3))
              .VerticalAlign(TextVerticalAlign::Center)
              .OnChanged(
                  [draft](const TextEditingValue &value) { draft = value; })
              .OnSubmitted(send)
              .With(Frame{.min_height = 44.0F}, Grow()),
          ComposerAction(app::images::arrow_up,
                         can_send ? colors::text_on_color : colors::secondary,
                         can_send ? colors::accent : Color::Transparent(),
                         can_send, send),
      }
          // Match the legacy 148px composer body at the 420dpi reference
          // density.  A 56dp minimum rasterizes two pixels short here and
          // makes both the editor text and circular actions look low.
          .With(Frame{.min_height = 56.76F},
                Padding(EdgeInsets::Symmetric(8.0F, 6.0F)),
                CrossAlign(CrossAxisAlignment::End), Background(colors::input),
                CornerRadius(20.0F)),
  }
      .With(Padding(EdgeInsets{
                .top = 14.0F, .right = 20.0F, .bottom = 20.0F, .left = 20.0F}),
            Background(colors::background));
}

} // namespace

[[huxerui::composable]] View
ChatScreen(std::function<void()> open_drawer, State<TextEditingValue> draft,
           const std::shared_ptr<application::ChatSession> &session,
           State<std::size_t> revision, State<DrawerModel> workspace) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto bottom_sheets = UseBottomSheet();
  auto attachment_visible = UseState(false);
  auto more_visible = UseState(false);
  auto permission_visible = UseState(false);
  auto attachment_layer = UseState(std::optional<LayerId>{});
  auto more_layer = UseState(std::optional<LayerId>{});
  auto permission_layer = UseState(std::optional<LayerId>{});
  auto permission_mode = UseState(ChatPermissionMode::automatic);
  auto external_storage_granted = UseState(false);
  auto has_saved_command_permissions = UseState(false);
  auto selected_attachment_paths = UseState(std::vector<std::string>{});
  auto expanded_attachment_directories = UseState(std::vector<std::string>{});

  const ControlledBottomSheet attachment_sheet(
      bottom_sheets, attachment_visible, attachment_layer);
  const ControlledBottomSheet more_sheet(bottom_sheets, more_visible,
                                         more_layer);
  const ControlledBottomSheet permission_sheet(
      bottom_sheets, permission_visible, permission_layer);

  const auto storage_permission_description =
      UseString(app::strings::permission_mode_storage_required);

  auto show_permission = [permission_sheet, permission_mode,
                          external_storage_granted,
                          has_saved_command_permissions,
                          storage_permission_description] {
    permission_sheet.Show([permission_mode, external_storage_granted,
                           has_saved_command_permissions,
                           storage_permission_description](
                              bool visible, std::function<void()> dismiss) {
      return ChatPermissionMenu(
          ChatPermissionMenuState{
              .visible = visible,
              .mode = permission_mode.Get(),
              .manage_all_files_available =
                  FeatureAvailable<PlatformFeature::android_storage_permission>,
              .external_storage_granted = external_storage_granted.Get(),
              .has_saved_command_permissions =
                  has_saved_command_permissions.Get(),
              .storage_permission_description = storage_permission_description,
          },
          ChatOverlayCallbacks<ChatPermissionAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [permission_mode,
                   has_saved_command_permissions](ChatPermissionAction action) {
                    switch (action) {
                    case ChatPermissionAction::automatic:
                      permission_mode = ChatPermissionMode::automatic;
                      break;
                    case ChatPermissionAction::confirm:
                      permission_mode = ChatPermissionMode::confirm;
                      break;
                    case ChatPermissionAction::read_only:
                      permission_mode = ChatPermissionMode::read_only;
                      break;
                    case ChatPermissionAction::manage_all_files:
                      // The Android permission port is injected by the platform
                      // integration layer.
                      break;
                    case ChatPermissionAction::revoke_saved_commands:
                      has_saved_command_permissions = false;
                      break;
                    }
                  },
          });
    });
  };

  auto show_more = [more_sheet, navigation, session, revision] {
    more_sheet.Show([navigation, session,
                     revision](bool visible, std::function<void()> dismiss) {
      return ChatMoreMenu(
          ChatMoreMenuState{.visible = visible},
          ChatOverlayCallbacks<ChatMoreAction>{
              .on_dismiss_request = std::move(dismiss),
              .on_action =
                  [navigation, session, revision](ChatMoreAction action) {
                    switch (action) {
                    case ChatMoreAction::tutorial:
                      navigation.Push(domain::AppRoute::tutorial);
                      break;
                    case ChatMoreAction::settings:
                      navigation.Push(domain::AppRoute::settings);
                      break;
                    case ChatMoreAction::clear_chat:
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

  auto show_attachments = [attachment_sheet, workspace,
                           selected_attachment_paths,
                           expanded_attachment_directories] {
    std::optional<ChatAttachmentNode> initial_tree;
    if (workspace->file_tree.has_value()) {
      initial_tree = ToAttachmentNode(*workspace->file_tree);
      std::vector<std::string> expanded;
      CollectExpandedDirectories(*initial_tree, expanded);
      expanded_attachment_directories = std::move(expanded);
    }

    attachment_sheet.Show(
        [workspace, selected_attachment_paths, expanded_attachment_directories](
            bool visible, std::function<void()> dismiss) {
          std::optional<ChatAttachmentNode> tree;
          if (workspace->file_tree.has_value()) {
            tree = ToAttachmentNode(*workspace->file_tree);
          }
          return ChatAttachmentPicker(
              ChatAttachmentPickerState{
                  .visible = visible,
                  .tree = std::move(tree),
                  .selected_paths = selected_attachment_paths.Get(),
                  .expanded_directories = expanded_attachment_directories.Get(),
                  .loading = !workspace->file_tree.has_value(),
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
                      [selected_attachment_paths](ChatAttachmentFile file) {
                        selected_attachment_paths.Update(
                            [&file](std::vector<std::string> &paths) {
                              TogglePath(paths, file.path);
                            });
                      },
              });
        });
  };

  return Column{
      Header(std::move(open_drawer), session, revision, show_permission,
             show_more),
      Conversation(session, revision.Get(), navigation),
      Composer(draft, session, revision, show_attachments),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background));
}

} // namespace linecode::presentation
