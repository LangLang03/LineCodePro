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
            Padding(EdgeInsets::Symmetric(16.0F, 14.0F)),
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
      Frame{.max_width = kSheetHostMaximumWidth});
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
                                           .bottom = 20.0F,
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
                CornerRadius(CornerRadii::Top(kSheetRadius)), ClipChildren());
  return InsetSheet(std::move(panel));
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

} // namespace linecode::presentation
