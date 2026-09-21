#include "presentation/screens/ssh_settings_screen.h"

#include <array>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ssh_settings_service.h"
#include "domain/app_state.h"
#include "domain/ssh_config.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"
#include "presentation/ssh_settings_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct SshEditor final {
  TextEditingValue host{
      TextEditingValue::FromText(std::string{domain::kDefaultSshHost})};
  TextEditingValue port{
      TextEditingValue::FromText(std::to_string(domain::kDefaultSshPort))};
  TextEditingValue username;
  TextEditingValue password;
  TextEditingValue private_key;
  TextEditingValue passphrase;
  std::uint64_t edit_revision{};

  bool operator==(const SshEditor &) const = default;
};

struct SshStatus final {
  std::string title;
  std::string message;
  bool error{};
  bool visible{};

  bool operator==(const SshStatus &) const = default;
};

struct SshPageState final {
  SshEditor editor;
  SshStatus status;
  bool testing{};

  bool operator==(const SshPageState &) const = default;
};

struct ResolvedStatusStrings final {
  std::string saved_title;
  std::string saved_message;
  std::string testing_title;
  std::string testing_message;
  std::string success_title;
  std::string success_message;
  std::string failed_title;
};

using EditorMember = TextEditingValue SshEditor::*;
using PresentationMember = StringVariant SshSettingsPresentation::*;

struct FieldPresentation final {
  EditorMember editor;
  PresentationMember label;
  PresentationMember placeholder;
  TextInputType input_type;
  bool multiline;
  bool secure;
  std::string_view key;
};

constexpr std::array kFields{
    FieldPresentation{&SshEditor::host, &SshSettingsPresentation::host,
                      &SshSettingsPresentation::host_hint, TextInputType::Text,
                      false, false, "ssh-host"},
    FieldPresentation{&SshEditor::port, &SshSettingsPresentation::port,
                      &SshSettingsPresentation::port_hint,
                      TextInputType::Number, false, false, "ssh-port"},
    FieldPresentation{&SshEditor::username, &SshSettingsPresentation::username,
                      &SshSettingsPresentation::username_hint,
                      TextInputType::Text, false, false, "ssh-username"},
    FieldPresentation{&SshEditor::password, &SshSettingsPresentation::password,
                      &SshSettingsPresentation::password_hint,
                      TextInputType::Text, false, true, "ssh-password"},
    FieldPresentation{&SshEditor::private_key,
                      &SshSettingsPresentation::private_key,
                      &SshSettingsPresentation::private_key_hint,
                      TextInputType::Text, true, false, "ssh-private-key"},
    FieldPresentation{&SshEditor::passphrase,
                      &SshSettingsPresentation::passphrase,
                      &SshSettingsPresentation::passphrase_hint,
                      TextInputType::Text, false, true, "ssh-passphrase"},
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Gap(float height) {
  return Stack{}.With(Frame{.width = 1.0F, .height = height});
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon).Tint(tint).With(Frame{.width = size, .height = size});
}

View Header(StringVariant title,
            const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Glyph(app::images::chevron_left, 22.0F, colors::text),
      }
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{
          Text(title).Style(Label(17.0F, FontWeight::Bold)),
      }
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

TextFieldStyle SshFormFieldStyle() {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Outlined;
  style.show_label = false;
  style.outlined.background = colors::surface_light;
  style.outlined.border = colors::border_light;
  style.outlined.hovered_border = colors::border_light;
  style.outlined.focused_border = colors::accent;
  style.outlined.minimum_height = 44.0F;
  style.text_style = Label(16.0F);
  style.placeholder_style = Label(16.0F, FontWeight::Regular, colors::tertiary);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  style.border_width = 1.0F;
  style.focused_border_width = 1.0F;
  style.outlined.corner_radii = CornerRadii{8.0F};
  style.padding = EdgeInsets::Symmetric(12.0F, 8.0F);
  return style;
}

View FormField(TextEditingValue value, StringVariant label,
               StringVariant placeholder,
               std::function<void(const TextEditingValue &)> changed,
               TextInputType input_type, bool multiline, bool secure,
               std::string_view key) {
  auto input = TextField(value)
                   .Label(label)
                   .Placeholder(placeholder)
                   .Variant(TextFieldVariant::Outlined)
                   .LineLimits(multiline ? TextFieldLineLimits::MultiLine(5)
                                         : TextFieldLineLimits::SingleLine())
                   .VerticalAlign(multiline ? TextVerticalAlign::Top
                                            : TextVerticalAlign::Center)
                   .InputConfiguration(TextInputConfiguration{
                       .type = input_type,
                       .capitalization = TextCapitalization::None,
                       .action = multiline ? TextInputAction::Newline
                                           : TextInputAction::Next,
                       .multiline = multiline,
                       .secure = secure,
                       .autocorrect = false,
                   })
                   .OnChanged(std::move(changed))
                   .With(Frame{.min_height = multiline ? 120.0F : 44.0F})
                   .Key(key);
  if (secure)
    input = std::move(input).Secure();

  ThemeDefinition definition;
  definition.Set(SshFormFieldStyle());
  return Column{
      Text(label).Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
      Theme(std::move(definition), std::move(input)),
  }
      .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View ActionButton(ImageResource icon, StringVariant label, bool primary,
                  bool enabled, std::function<void()> action) {
  return Row{
      Glyph(icon, 16.0F, primary ? colors::text_on_color : colors::secondary),
      Text(label).Style(
          Label(13.0F, FontWeight::Bold,
                primary ? colors::text_on_color : colors::secondary)),
  }
      .OnClick(std::move(action))
      .With(Frame{.height = 42.0F}, Grow(), Spacing(4.0F),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Background(primary ? colors::accent : colors::surface_light),
            Border(primary ? colors::accent : colors::border_light, 1.0F),
            CornerRadius(8.0F), Enabled{enabled},
            Opacity(enabled ? 1.0F : 0.65F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View Card(View content) {
  return Stack{content}.With(
      Padding(16.0F), Background(colors::elevated), CornerRadius(12.0F),
      Align(HorizontalAlignment::Stretch, VerticalAlignment::Stretch));
}

SshEditor EditorFromConfig(const domain::SshConfig &config) {
  return SshEditor{
      .host = TextEditingValue::FromText(config.host),
      .port = TextEditingValue::FromText(std::to_string(config.port)),
      .username = TextEditingValue::FromText(config.username),
      .password = TextEditingValue::FromText(config.password),
      .private_key = TextEditingValue::FromText(config.private_key),
      .passphrase = TextEditingValue::FromText(config.passphrase),
      .edit_revision = 0,
  };
}

domain::SshConfig ConfigFromEditor(const SshEditor &editor) {
  return domain::NormalizeSshConfig(domain::SshConfig{
      .host = editor.host.text,
      .port = domain::ParseSshPort(editor.port.text),
      .username = editor.username.text,
      .password = editor.password.text,
      .private_key = editor.private_key.text,
      .passphrase = editor.passphrase.text,
  });
}

void SetStatus(State<SshPageState> state, std::string title,
               std::string message, bool error) {
  state.Update([&](auto &next) {
    next.status = SshStatus{.title = std::move(title),
                            .message = std::move(message),
                            .error = error,
                            .visible = true};
  });
}

std::string TrimStatus(std::string value) {
  const auto whitespace = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && whitespace(value.front()))
    value.erase(value.begin());
  while (!value.empty() && whitespace(value.back()))
    value.pop_back();
  return value;
}

Task<void>
LoadSettings(std::shared_ptr<application::SshSettingsService> service,
             State<SshPageState> state) {
  auto loaded = co_await service->Load();
  if (!loaded) {
    SetStatus(state, "SSH", loaded.error().message, true);
    co_return;
  }
  state.Update([&](auto &next) {
    if (next.editor.edit_revision == 0)
      next.editor = EditorFromConfig(*loaded);
  });
}

View StatusCard(const SshStatus &status) {
  const auto foreground =
      static_cast<Color>(status.error ? colors::danger : colors::secondary);
  return Text(status.title + "\n" + status.message)
      .Style(TextStyle{Font::Monospace(11.0F), foreground})
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
            Background(status.error ? colors::danger_muted : colors::code),
            Border(status.error ? colors::danger : colors::code_border, 1.0F),
            CornerRadius(8.0F));
}

} // namespace

[[huxerui::composable]] View
SshSettingsScreen(std::shared_ptr<application::SshSettingsService> service,
                  bool termux_available, SshSettingsPresentation presentation) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(SshPageState{});
  const ResolvedStatusStrings status_strings{
      .saved_title = UseString(presentation.saved_title),
      .saved_message = UseString(presentation.saved_message),
      .testing_title = UseString(presentation.testing_title),
      .testing_message = UseString(presentation.testing_message),
      .success_title = UseString(presentation.success_title),
      .success_message = UseString(presentation.success_message),
      .failed_title = UseString(presentation.failed_title),
  };

  Lifecycle(
      [tasks, service, state] { tasks.Launch(LoadSettings(service, state)); });

  const auto edit = [state](EditorMember member,
                            const TextEditingValue &value) {
    state.Update([&](auto &next) {
      next.editor.*member = value;
      ++next.editor.edit_revision;
    });
  };

  std::vector<View> intro_content{
      Text(presentation.server_section).Style(Label(16.0F, FontWeight::Bold)),
      Gap(4.0F),
      Text(presentation.server_description)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
  };
  intro_content.push_back(Gap(12.0F));
  intro_content.push_back(ActionButton(
      app::images::smartphone, presentation.termux, false, true,
      [navigation, toast, action = ResolveTermuxEntryAction(termux_available)] {
        if (action == TermuxEntryAction::open_integration) {
          navigation.Push(domain::AppRoute::termux_integration);
          return;
        }
        toast.Show(app::strings::screen_ssh_termux_unavailable);
      }));
  View intro =
      Card(Column(intro_content).With(CrossAlign(CrossAxisAlignment::Stretch)));

  std::vector<View> form_content;
  form_content.reserve(20);
  form_content.push_back(
      Text(presentation.form_title).Style(Label(16.0F, FontWeight::Bold)));
  for (const auto &field : kFields) {
    form_content.push_back(Gap(12.0F));
    form_content.push_back(FormField(
        state->editor.*field.editor, presentation.*field.label,
        presentation.*field.placeholder,
        [edit, member = field.editor](const TextEditingValue &value) {
          edit(member, value);
        },
        field.input_type, field.multiline, field.secure, field.key));
  }

  const auto save = [tasks, service, state, status_strings] {
    const auto config = ConfigFromEditor(state->editor);
    tasks.Launch([service, state, status_strings, config]() -> Task<void> {
      auto result = co_await service->Save(config);
      if (result) {
        SetStatus(state, status_strings.saved_title,
                  status_strings.saved_message, false);
      } else {
        SetStatus(state, status_strings.failed_title, result.error().message,
                  true);
      }
    });
  };
  const auto test = [tasks, service, state, status_strings] {
    if (state->testing)
      return;
    const auto config = ConfigFromEditor(state->editor);
    state.Update([&](auto &next) { next.testing = true; });
    SetStatus(state, status_strings.testing_title,
              status_strings.testing_message, false);
    tasks.Launch([service, state, status_strings, config]() -> Task<void> {
      auto saved = co_await service->Save(config);
      if (!saved) {
        state.Update([](auto &next) { next.testing = false; });
        SetStatus(state, status_strings.failed_title, saved.error().message,
                  true);
        co_return;
      }
      auto tested = co_await service->Test(config);
      state.Update([](auto &next) { next.testing = false; });
      if (!tested) {
        SetStatus(state, status_strings.failed_title, tested.error().message,
                  true);
        co_return;
      }
      auto output = TrimStatus(std::move(*tested));
      SetStatus(state, status_strings.success_title,
                output.empty() ? status_strings.success_message
                               : std::move(output),
                false);
    });
  };

  form_content.push_back(Gap(12.0F));
  form_content.push_back(
      Row{
          ActionButton(app::images::save, presentation.save, false, true, save),
          ActionButton(app::images::terminal, presentation.test, true,
                       !state->testing, test),
      }
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch)));
  if (state->status.visible) {
    form_content.push_back(Gap(8.0F));
    form_content.push_back(StatusCard(state->status));
  }

  std::vector<View> content;
  content.reserve(6);
  content.push_back(intro);
  content.push_back(Gap(12.0F));
  content.push_back(
      Card(Column(form_content).With(CrossAlign(CrossAxisAlignment::Stretch))));
  content.push_back(Gap(12.0F));
  content.push_back(Gap(100.0F));

  return Column{
      Header(presentation.title, navigation),
      LegacyScreenHeaderDivider(),
      ScrollView(
          Column(content).With(
              Padding(EdgeInsets{
                  .top = 16.0F, .right = 16.0F, .bottom = 0.0F, .left = 16.0F}),
              CrossAlign(CrossAxisAlignment::Stretch),
              Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
