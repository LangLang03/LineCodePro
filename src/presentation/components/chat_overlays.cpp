#include "presentation/components/chat_overlays.h"

#include <algorithm>
#include <array>
#include <functional>
#include <optional>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

constexpr float kSheetRadius = 24.0F;
constexpr float kSheetMaximumWidth = 560.0F;
constexpr float kSheetHorizontalInset = 16.0F;
constexpr float kSheetBottomInset = 0.0F;
constexpr float kSheetHostMaximumWidth =
    kSheetMaximumWidth + (kSheetHorizontalInset * 2.0F);
constexpr float kMinimumRowHeight = 52.0F;
constexpr float kAttachmentPanelHeight = 640.0F;
constexpr float kAttachmentTreeIndent = 18.0F;
constexpr std::size_t kMaximumAttachmentTreeDepth = 24;

class InsetSheetFrame final : public Layout<InsetSheetFrame> {
public:
  using Layout::Layout;

  static LayoutResult Measure(LayoutContext &context, ViewNode &node,
                              Constraints constraints) {
    LayoutResult result;
    if (node.ChildCount() == 0) {
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));
    }

    auto panel_constraints = constraints.Loose();
    if (constraints.HasBoundedWidth()) {
      const float available_width = std::max(
          0.0F, constraints.max_width - (kSheetHorizontalInset * 2.0F));
      const float panel_width = std::min(kSheetMaximumWidth, available_width);
      panel_constraints.min_width = panel_width;
      panel_constraints.max_width = panel_width;
    } else {
      panel_constraints.max_width = kSheetMaximumWidth;
    }
    if (constraints.HasBoundedHeight()) {
      panel_constraints.max_height =
          std::max(0.0F, constraints.max_height - kSheetBottomInset);
    }

    ViewNode &panel = node.ChildAt(0);
    const Size panel_size = context.Measure(panel, panel_constraints);
    const float desired_width =
        panel_size.width + (kSheetHorizontalInset * 2.0F);
    const float frame_width =
        constraints.HasBoundedWidth() ? constraints.max_width : desired_width;
    const Size frame_size = constraints.Constrain(
        {frame_width, panel_size.height + kSheetBottomInset});
    const float panel_x =
        std::max(0.0F, (frame_size.width - panel_size.width) * 0.5F);
    const float panel_y = std::max(0.0F, frame_size.height - panel_size.height -
                                             kSheetBottomInset);
    return result.Place(panel, {panel_x, panel_y}).SetSize(frame_size);
  }
};

template <typename Action> struct MenuItem final {
  Action action;
  StringVariant label;
  std::optional<StringVariant> description;
  bool available;
  bool selected = false;
};

template <typename Action>
void SelectAction(const ChatOverlayCallbacks<Action> &callbacks,
                  Action action) {
  if (callbacks.on_dismiss_request) {
    std::invoke(callbacks.on_dismiss_request);
  }
  if (callbacks.on_action) {
    std::invoke(callbacks.on_action, action);
  }
}

template <typename Action>
View PlainMenuRow(const MenuItem<Action> &item,
                  ChatOverlayCallbacks<Action> callbacks) {
  return Text(item.label)
      .Style(TextStyle{Font::System(15.0F), colors::text})
      .VerticalAlign(TextVerticalAlign::Center)
      .OnClick([callbacks = std::move(callbacks), action = item.action] {
        SelectAction(callbacks, action);
      })
      .With(Frame{.min_height = kMinimumRowHeight}, Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

template <typename Action>
View DescribedMenuRow(const MenuItem<Action> &item,
                      ChatOverlayCallbacks<Action> callbacks) {
  std::vector<View> labels;
  labels.emplace_back(
      Text(item.label).Style(TextStyle{Font::System(16.0F), colors::text}));
  if (item.description.has_value()) {
    labels.emplace_back(
        Text(*item.description)
            .Style(TextStyle{Font::System(11.0F), colors::tertiary}));
  }

  std::vector<View> content;
  content.emplace_back(Column(std::move(labels)).With(Spacing(2.0F), Grow()));
  if (item.selected) {
    content.emplace_back(Stack{
        Image(app::images::check)
            .Tint(colors::accent)
            .With(Frame{.width = 16.0F, .height = 16.0F}),
    }
                             .With(Frame{.width = 18.0F, .height = 18.0F},
                                   Align(HorizontalAlignment::Center,
                                         VerticalAlignment::Center)));
  }

  return Row(std::move(content))
      .OnClick([callbacks = std::move(callbacks), action = item.action] {
        SelectAction(callbacks, action);
      })
      .With(Frame{.min_height = kMinimumRowHeight},
            Padding(EdgeInsets::Symmetric(16.0F, 15.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(item.selected ? colors::accent_muted
                                     : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

template <typename Action, std::size_t Size, typename RowFactory>
std::vector<View>
BuildAvailableRows(const std::array<MenuItem<Action>, Size> &items,
                   const ChatOverlayCallbacks<Action> &callbacks,
                   RowFactory &&row_factory) {
  std::vector<View> rows;
  rows.reserve(Size);
  for (const auto &item : items) {
    if (item.available) {
      rows.emplace_back(std::invoke(row_factory, item, callbacks));
    }
  }
  return rows;
}

View EmptyOverlay() {
  return Spacer().With(Frame{.width = 0.0F, .height = 0.0F});
}

View InsetSheet(View panel) {
  return InsetSheetFrame{std::move(panel)}.With(
      Frame{.max_width = kSheetHostMaximumWidth},
      Offset(Point{0.0F, CurrentHostPlatform() == HostPlatform::android
                             ? 32.0F
                             : 0.0F}));
}

View StandardSheet(StringVariant title, std::vector<View> rows) {
  rows.emplace_back(Spacer().With(Frame{.height = 16.0F}));
  View panel =
      Column{
          Column{
              Row{
                  Spacer(),
                  Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                               Background(colors::tertiary),
                               CornerRadius(2.0F)),
                  Spacer(),
              }
                  .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
              Text(std::move(title))
                  .Style(TextStyle{
                      Font::System(17.0F).WithWeight(FontWeight::Bold),
                      colors::text})
                  .With(Padding(EdgeInsets{.top = 12.0F,
                                           .right = 24.0F,
                                           .bottom = 8.0F,
                                           .left = 24.0F})),
              Stack{}.With(Frame{.height = 1.0F},
                           Background(colors::border_light)),
          }
              .With(CrossAlign(CrossAxisAlignment::Stretch)),
          ScrollView(Column(std::move(rows))
                         .With(CrossAlign(CrossAxisAlignment::Stretch)))
              .ScrollAxis(Axis::Vertical),
      }
          .With(Frame{.max_width = kSheetMaximumWidth},
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(kSheetRadius), ClipChildren());
  return InsetSheet(std::move(panel));
}

bool ContainsPath(const std::vector<std::string> &paths,
                  std::string_view path) {
  return std::ranges::find(paths, path) != paths.end();
}

View AttachmentInlineIcon(ImageResource icon, Color tint, float size) {
  return Stack{
      Image(std::move(icon))
          .Tint(tint)
          .With(Frame{.width = size, .height = size}),
  }
      .With(Frame{.width = size, .height = size},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

View AttachmentStatus(StringVariant message) {
  return Stack{
      Text(std::move(message))
          .Style(TextStyle{Font::System(13.0F), colors::secondary}),
  }
      .With(Padding(20.0F), Grow(),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

View AttachmentFileSelection(bool selected) {
  return Stack{
      Image(selected ? app::images::check : app::images::plus)
          .Tint(selected ? colors::text_on_color : colors::secondary)
          .With(Frame{.width = 14.0F, .height = 14.0F}),
  }
      .With(Frame{.width = 26.0F, .height = 26.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(selected ? colors::accent : colors::surface_light),
            CornerRadius(13.0F));
}

void AppendAttachmentRows(std::vector<View> &rows,
                          const ChatAttachmentNode &node, std::size_t depth,
                          bool root, const ChatAttachmentPickerState &state,
                          const ChatAttachmentPickerCallbacks &callbacks) {
  const bool expanded =
      node.directory && ContainsPath(state.expanded_directories, node.path);
  const bool selected =
      !node.directory && ContainsPath(state.selected_paths, node.path);
  const float left =
      12.0F + static_cast<float>(std::min(depth, kMaximumAttachmentTreeDepth)) *
                  kAttachmentTreeIndent;

  std::vector<View> content;
  if (node.directory) {
    content.emplace_back(AttachmentInlineIcon(
        expanded ? app::images::chevron_down : app::images::chevron_right,
        colors::tertiary, 16.0F));
  } else {
    content.emplace_back(Spacer().With(Frame{.width = 16.0F, .height = 16.0F}));
  }
  content.emplace_back(
      AttachmentInlineIcon(
          node.directory
              ? (expanded ? app::images::folder_open : app::images::folder)
              : app::images::file,
          node.directory ? colors::accent : colors::secondary, 17.0F)
          .With(Padding(EdgeInsets{.left = 8.0F})));

  std::vector<View> labels;
  labels.emplace_back(
      Text(node.name)
          .Style(TextStyle{Font::System(16.0F).WithWeight(
                               root ? FontWeight::Bold : FontWeight::Regular),
                           root ? colors::text : colors::secondary})
          .With(Frame{.max_height = 22.0F}, ClipChildren()));
  if (root) {
    labels.emplace_back(
        Text(node.path)
            .Style(TextStyle{Font::System(11.0F), colors::tertiary})
            .With(Frame{.max_height = 16.0F}, Padding(EdgeInsets{.top = 2.0F}),
                  ClipChildren()));
  }
  content.emplace_back(Column(std::move(labels))
                           .With(Padding(EdgeInsets{.left = 8.0F}), Grow()));
  if (!node.directory) {
    content.emplace_back(AttachmentFileSelection(selected).With(
        Padding(EdgeInsets{.left = 8.0F})));
  }

  auto row =
      Row(std::move(content))
          .With(Frame{.min_height = kMinimumRowHeight},
                Padding(EdgeInsets{
                    .top = 8.0F, .right = 12.0F, .bottom = 8.0F, .left = left}),
                CrossAlign(CrossAxisAlignment::Center), Focusable(),
                PointerCursor(PointerCursorKind::Hand));
  if (node.directory) {
    row = std::move(row).OnClick(
        [callback = callbacks.on_directory_toggled, path = node.path] {
          if (callback) {
            std::invoke(callback, path);
          }
        });
  } else {
    row = std::move(row).OnClick(
        [callback = callbacks.on_file_toggled,
         file = ChatAttachmentFile{
             .path = node.path, .name = node.name, .source = "local"}] {
          if (callback) {
            std::invoke(callback, file);
          }
        });
  }
  rows.emplace_back(std::move(row).Key(node.path));

  if (!expanded) {
    return;
  }
  if (node.children.empty()) {
    const float empty_left =
        12.0F +
        static_cast<float>(std::min(depth + 1, kMaximumAttachmentTreeDepth)) *
            kAttachmentTreeIndent;
    rows.emplace_back(
        Text(app::strings::sheet_status_empty_dir)
            .Style(TextStyle{Font::System(11.0F), colors::tertiary})
            .With(Padding(EdgeInsets{.top = 8.0F,
                                     .right = 12.0F,
                                     .bottom = 8.0F,
                                     .left = empty_left}))
            .Key(node.path + "/__empty"));
    return;
  }
  for (const auto &child : node.children) {
    AppendAttachmentRows(rows, child, depth + 1, false, state, callbacks);
  }
}

} // namespace

View ChatContextMenu(const ChatContextMenuState &state,
                     ChatOverlayCallbacks<ChatContextAction> callbacks) {
  if (!state.visible) {
    return EmptyOverlay();
  }

  const std::array items{
      MenuItem<ChatContextAction>{ChatContextAction::files,
                                  app::strings::chat_context_files,
                                  std::nullopt, state.available.files},
      MenuItem<ChatContextAction>{ChatContextAction::image,
                                  app::strings::chat_context_image,
                                  std::nullopt, state.available.image},
      MenuItem<ChatContextAction>{ChatContextAction::model, state.model_label,
                                  std::nullopt, state.available.model},
      MenuItem<ChatContextAction>{
          ChatContextAction::mode,
          StringVariant::Format(app::strings::chat_context_mode,
                                state.mode_label),
          std::nullopt, state.available.mode},
      MenuItem<ChatContextAction>{ChatContextAction::workspace,
                                  app::strings::chat_context_workspace,
                                  std::nullopt, state.available.workspace},
      MenuItem<ChatContextAction>{ChatContextAction::permissions,
                                  app::strings::header_permission_desc,
                                  std::nullopt, state.available.permissions},
      MenuItem<ChatContextAction>{ChatContextAction::settings,
                                  app::strings::chat_context_settings,
                                  std::nullopt, state.available.settings},
      MenuItem<ChatContextAction>{ChatContextAction::more,
                                  app::strings::chat_context_more, std::nullopt,
                                  state.available.more},
  };

  auto rows =
      BuildAvailableRows(items, callbacks, PlainMenuRow<ChatContextAction>);
  std::vector<View> content;
  content.reserve(rows.size() + 1);
  content.emplace_back(
      Text(app::strings::chat_context_title)
          .Style(TextStyle{Font::System(22.0F).WithWeight(FontWeight::Medium),
                           colors::text})
          .VerticalAlign(TextVerticalAlign::Center)
          .With(Frame{.height = kMinimumRowHeight}));
  for (auto &row : rows) {
    content.emplace_back(std::move(row));
  }

  View panel =
      Column(std::move(content))
          .With(Frame{.max_width = kSheetMaximumWidth},
                Padding(EdgeInsets{.top = 24.0F,
                                   .right = 28.0F,
                                   .bottom = 28.0F,
                                   .left = 28.0F}),
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background),
                CornerRadius(CornerRadii::Top(kSheetRadius)), ClipChildren());
  return InsetSheet(std::move(panel));
}

View ChatMoreMenu(const ChatMoreMenuState &state,
                  ChatOverlayCallbacks<ChatMoreAction> callbacks) {
  if (!state.visible) {
    return EmptyOverlay();
  }

  const std::array items{
      MenuItem<ChatMoreAction>{
          ChatMoreAction::tutorial, app::strings::sheet_more_tutorial,
          app::strings::sheet_more_tutorial_desc, state.available.tutorial},
      MenuItem<ChatMoreAction>{
          ChatMoreAction::settings, app::strings::screen_settings_title,
          app::strings::sheet_more_settings_desc, state.available.settings},
      MenuItem<ChatMoreAction>{
          ChatMoreAction::export_chat, app::strings::sheet_more_export,
          app::strings::sheet_more_export_desc, state.available.export_chat},
      MenuItem<ChatMoreAction>{ChatMoreAction::select_messages_to_export,
                               app::strings::sheet_more_select_export,
                               app::strings::sheet_more_select_export_desc,
                               state.available.select_messages_to_export},
      MenuItem<ChatMoreAction>{ChatMoreAction::compact_context,
                               app::strings::sheet_more_compact,
                               app::strings::sheet_more_compact_desc,
                               state.available.compact_context},
      MenuItem<ChatMoreAction>{
          ChatMoreAction::clear_chat, app::strings::sheet_more_clear,
          app::strings::sheet_more_clear_desc, state.available.clear_chat},
  };

  auto rows =
      BuildAvailableRows(items, callbacks, DescribedMenuRow<ChatMoreAction>);
  return StandardSheet(app::strings::common_more, std::move(rows));
}

View ChatPermissionMenu(const ChatPermissionMenuState &state,
                        ChatOverlayCallbacks<ChatPermissionAction> callbacks) {
  if (!state.visible) {
    return EmptyOverlay();
  }

  const StringVariant storage_description =
      state.external_storage_granted
          ? StringVariant(app::strings::permission_mode_storage_granted)
      : state.storage_permission_description.empty()
          ? StringVariant(app::strings::permission_mode_storage_required)
          : StringVariant(state.storage_permission_description);
  const std::array items{
      MenuItem<ChatPermissionAction>{
          ChatPermissionAction::automatic, app::strings::permission_mode_auto,
          app::strings::permission_mode_auto_desc, true,
          state.mode == ChatPermissionMode::automatic},
      MenuItem<ChatPermissionAction>{
          ChatPermissionAction::confirm, app::strings::permission_mode_confirm,
          app::strings::permission_mode_confirm_desc, true,
          state.mode == ChatPermissionMode::confirm},
      MenuItem<ChatPermissionAction>{
          ChatPermissionAction::read_only,
          app::strings::permission_mode_readonly,
          app::strings::permission_mode_readonly_desc, true,
          state.mode == ChatPermissionMode::read_only},
      MenuItem<ChatPermissionAction>{
          ChatPermissionAction::manage_all_files,
          app::strings::permission_mode_manage_all_files, storage_description,
          state.manage_all_files_available, state.external_storage_granted},
      MenuItem<ChatPermissionAction>{
          ChatPermissionAction::revoke_saved_commands,
          app::strings::chat_permissions_clear, std::nullopt,
          state.has_saved_command_permissions},
  };

  auto rows = BuildAvailableRows(items, callbacks,
                                 DescribedMenuRow<ChatPermissionAction>);
  return StandardSheet(app::strings::sheet_title_permissions, std::move(rows));
}

View ChatAttachmentPicker(const ChatAttachmentPickerState &state,
                          ChatAttachmentPickerCallbacks callbacks) {
  if (!state.visible) {
    return EmptyOverlay();
  }

  View body;
  if (state.loading) {
    body = AttachmentStatus(
        state.message.empty()
            ? StringVariant(app::strings::sheet_status_reading_files)
            : StringVariant(state.message));
  } else if (!state.tree.has_value()) {
    body = AttachmentStatus(
        state.message.empty()
            ? StringVariant(app::strings::sheet_status_no_files)
            : StringVariant(state.message));
  } else {
    std::vector<View> rows;
    AppendAttachmentRows(rows, *state.tree, 0, true, state, callbacks);
    body = ScrollView(Column(std::move(rows))
                          .With(Padding(EdgeInsets{.top = 8.0F,
                                                   .right = 8.0F,
                                                   .bottom = 16.0F,
                                                   .left = 8.0F}),
                                CrossAlign(CrossAxisAlignment::Stretch)))
               .ScrollAxis(Axis::Vertical)
               .With(Grow(), ScrollBar());
  }

  View close =
      Stack{
          Image(app::images::x)
              .Tint(colors::secondary)
              .With(Frame{.width = 18.0F, .height = 18.0F}),
      }
          .OnClick([callback = callbacks.on_dismiss_request] {
            if (callback) {
              std::invoke(callback);
            }
          })
          .With(Frame{.width = 48.0F, .height = 48.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_close},
                Focusable(), PointerCursor(PointerCursorKind::Hand));

  View panel =
      Column{
          Row{
              Column{
                  Text(app::strings::attachment_picker_title_local)
                      .Style(TextStyle{
                          Font::System(17.0F).WithWeight(FontWeight::Bold),
                          colors::text}),
                  Text::Format(app::strings::sheet_attachment_selected_count,
                               state.selected_paths.size())
                      .Style(TextStyle{Font::System(11.0F), colors::tertiary})
                      .With(Padding(EdgeInsets{.top = 3.0F})),
              }
                  .With(Grow()),
              std::move(close).With(Padding(EdgeInsets{.left = 12.0F})),
          }
              .With(Padding(EdgeInsets{.top = 20.0F,
                                       .right = 20.0F,
                                       .bottom = 16.0F,
                                       .left = 20.0F}),
                    CrossAlign(CrossAxisAlignment::Center)),
          Stack{}.With(Frame{.height = 1.0F}, Background(colors::border_light)),
          std::move(body),
      }
          .With(Frame{.height = kAttachmentPanelHeight,
                      .max_width = kSheetMaximumWidth,
                      .min_height = 360.0F,
                      .max_height = kAttachmentPanelHeight},
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(kSheetRadius), ClipChildren());
  return InsetSheet(std::move(panel));
}

} // namespace linecode::presentation
