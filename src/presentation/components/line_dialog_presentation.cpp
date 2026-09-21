#include "presentation/components/line_dialog_presentation.h"

#include <algorithm>
#include <functional>
#include <iterator>
#include <ranges>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle ActionTextStyle(const DialogStyle &style, bool positive,
                          LineDialogActionTone tone) {
  auto text_style =
      positive ? style.positive_action_style : style.negative_action_style;
  if (tone == LineDialogActionTone::danger)
    text_style.foreground = colors::danger;
  return text_style;
}

View DialogActionView(DialogContext dialog, LineDialogAction action,
                      const DialogStyle &style, bool positive) {
  auto activate = std::move(action.activate);
  return Stack{
      Text(std::move(action.label))
          .Style(ActionTextStyle(style, positive, action.tone))
          .Align(TextAlign::Center),
  }
      .OnClick([dialog, activate = std::move(activate)] {
        dialog.Dismiss();
        if (activate)
          std::invoke(activate);
      })
      .With(Frame{.min_height = style.minimum_action_height},
            Padding(style.action_padding),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(positive ? style.positive_action_background
                                : style.negative_action_background),
            CornerRadius(style.action_corner_radii), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DialogActions(DialogContext dialog, const DialogStyle& style,
                   LineDialogAction positive,
                   std::optional<LineDialogAction> negative) {
  std::vector<View> actions;
  actions.reserve(negative ? 2U : 1U);
  if (negative) {
    actions.push_back(
        DialogActionView(dialog, std::move(*negative), style, false));
  }
  actions.push_back(
      DialogActionView(dialog, std::move(positive), style, true));
  return Row(std::move(actions))
      .With(Spacing(style.action_spacing), MainAlign(MainAxisAlignment::End),
            CrossAlign(CrossAxisAlignment::Center),
            Padding(EdgeInsets{
                .right = 8.0F, .bottom = 8.0F, .left = 8.0F}));
}

View DialogPanel(const DialogStyle& style, StringVariant title,
                 std::vector<View> content, View actions) {
  std::vector<View> body;
  body.reserve(content.size() + 1U);
  body.push_back(Text(std::move(title)).Style(style.title_style));
  std::ranges::move(content, std::back_inserter(body));

  std::vector<View> panel{
      Column(std::move(body))
          .With(Spacing(style.content_spacing), Padding(style.content_padding),
                CrossAlign(CrossAxisAlignment::Stretch)),
  };
  if (style.action_separator_thickness > 0.0F) {
    panel.push_back(
        Stack{}.With(Frame{.height = style.action_separator_thickness},
                     Background(style.action_separator_color)));
  }
  panel.push_back(std::move(actions));

  return Column(std::move(panel))
      .With(Frame{.max_width = style.maximum_width},
            Background(style.background), CornerRadius(style.corner_radii),
            ClipChildren(), CrossAlign(CrossAxisAlignment::Stretch));
}

TextFieldStyle InputTextFieldStyle(TextFieldStyle style) {
  style.variant = TextFieldVariant::Standard;
  style.show_label = false;
  style.standard.background = Color::Transparent();
  style.standard.border = colors::border;
  style.standard.hovered_border = colors::border;
  style.standard.focused_border = colors::accent;
  style.standard.corner_radii = {};
  style.standard.minimum_height = 48.0F;
  style.text_style = TextStyle{Font::System(16.0F), colors::text};
  style.placeholder_style =
      TextStyle{Font::System(16.0F), colors::tertiary};
  style.padding = EdgeInsets::Symmetric(16.0F, 8.0F);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  return style;
}

} // namespace

[[huxerui::composable]] View
LineConfirmationDialog(DialogContext dialog, StringVariant title,
                       StringVariant message, LineDialogAction positive,
                       std::optional<LineDialogAction> negative) {
  const auto style = UseEnvironment<DialogStyle>();
  return DialogPanel(
      style, std::move(title),
      {Text(std::move(message)).Style(style.message_style)},
      DialogActions(dialog, style, std::move(positive), std::move(negative)));
}

[[huxerui::composable]] View
LineInputDialog(DialogContext dialog, StringVariant title,
                std::optional<StringVariant> message,
                StringVariant placeholder, std::string initial_value,
                std::function<void(std::string)> submit) {
  const auto dialog_style = UseEnvironment<DialogStyle>();
  const auto input_style =
      InputTextFieldStyle(UseEnvironment<TextFieldStyle>());
  auto value =
      UseState(TextEditingValue::FromText(std::move(initial_value)));

  ThemeDefinition overrides;
  overrides.Set(input_style);
  View field = Theme(
      overrides,
      TextField(value)
          .Variant(TextFieldVariant::Standard)
          .Placeholder(std::move(placeholder))
          .InputConfiguration(TextInputConfiguration{
              .type = TextInputType::Text,
              .capitalization = TextCapitalization::None,
              .action = TextInputAction::Done,
              .multiline = false,
              .secure = false,
              .autocorrect = false,
          })
          .OnChanged([value](const TextEditingValue& next) { value = next; })
          .OnSubmitted([dialog, value, submit] {
            dialog.Dismiss();
            if (submit) {
              std::invoke(submit, value->text);
            }
          }));

  std::vector<View> content;
  content.reserve(message ? 2U : 1U);
  if (message) {
    content.push_back(
        Text(std::move(*message)).Style(dialog_style.message_style));
  }
  content.push_back(std::move(field));

  return DialogPanel(
      dialog_style, std::move(title), std::move(content),
      DialogActions(
          dialog, dialog_style,
          LineDialogAction{
              .label = app::strings::common_confirm,
              .activate = [value, submit] {
                if (submit) {
                  std::invoke(submit, value->text);
                }
              },
          },
          LineDialogAction{
              .label = app::strings::common_cancel,
              .activate = {},
          }));
}

} // namespace linecode::presentation
