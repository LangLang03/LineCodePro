#include "presentation/screens/termux_integration_screen.h"

#include <array>
#include <chrono>
#include <functional>
#include <memory>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/clipboard.h>
#include <huxerui/huxerui.h>

#include "application/ports/termux_integration.h"
#include "application/ssh_settings_service.h"
#include "domain/app_state.h"
#include "domain/termux_integration.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using namespace std::chrono_literals;

struct TermuxStatus final {
  std::string title;
  std::string message;
  bool error{};
  bool visible{};

  bool operator==(const TermuxStatus &) const = default;
};

struct TermuxPageState final {
  TermuxStatus status;
  bool setup_running{};

  bool operator==(const TermuxPageState &) const = default;
};

struct ResolvedStrings final {
  std::string copied_title;
  std::string copied_message;
  std::string requested_title;
  std::string requested_message;
  std::string opened_title;
  std::string opened_message;
  std::string setup_title;
  std::string setup_message;
  std::string setup_done_title;
  std::string setup_failed_title;
  std::string shell_prefix;
  std::string rc_prefix;
  std::string unknown;
  std::string private_key_replacement;
  std::string error_not_installed;
  std::string error_permission;
  std::string error_no_result;
  std::string error_timeout;
  std::string error_parse;
  std::string error_bridge;
  std::string error_persist;
  std::string error_verification;
  std::string error_clipboard;
};

struct TextGeometry final {
  float size;
  float minimum_height;
};

// Android TextView and HuxerUI expose different intrinsic font boxes and line
// breaking.  Keep the legacy geometry as component-level presentation data so
// every instance of a component follows the same policy.
struct TermuxTypography final {
  TextGeometry section_title{.size = 16.0F, .minimum_height = 23.25F};
  TextGeometry description{.size = 11.0F, .minimum_height = 15.25F};
  TextGeometry step_description{.size = 10.5F, .minimum_height = 15.25F};
  TextGeometry command{.size = 11.0F, .minimum_height = 149.333F};
};

inline constexpr TermuxTypography kTypography{};

using ResolvedStringMember = std::string ResolvedStrings::*;

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

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Glyph(app::images::chevron_left, 22.0F, colors::text),
      }.OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{
          Text(app::strings::screen_termux_title)
              .Style(Label(17.0F, FontWeight::Bold)),
      }.With(Grow(),
             Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F},
         Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

View Card(std::vector<View> children) {
  return Column(std::move(children))
      .With(CrossAlign(CrossAxisAlignment::Stretch), Padding(16.0F),
            Background(colors::elevated), CornerRadius(12.0F));
}

View SectionTitle(StringVariant text) {
  return Text(std::move(text))
      .Style(Label(kTypography.section_title.size, FontWeight::Bold))
      .With(Frame{.min_height = kTypography.section_title.minimum_height});
}

View Description(StringVariant text) {
  return Text(std::move(text))
      .Style(Label(kTypography.description.size, FontWeight::Regular,
                   colors::tertiary))
      .With(Frame{.min_height = kTypography.description.minimum_height});
}

View StepDescription(StringVariant text) {
  return Text(std::move(text))
      .Style(Label(kTypography.step_description.size, FontWeight::Regular,
                   colors::tertiary))
      .With(Frame{.min_height = kTypography.step_description.minimum_height},
            Grow());
}

View Step(std::string number, StringVariant text) {
  return Row{
      Stack{
          Text(std::move(number))
              .Style(Label(11.0F, FontWeight::Bold, colors::text_on_color))
              .Align(TextAlign::Center),
      }.With(Frame{.width = 24.0F, .height = 24.0F},
             Align(HorizontalAlignment::Center, VerticalAlignment::Center),
             Background(colors::accent), CornerRadius(12.0F)),
      StepDescription(std::move(text)),
  }.With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center));
}

void SetStatus(State<TermuxPageState> state, std::string title,
               std::string message, bool error) {
  state.Update([&](TermuxPageState &next) {
    next.status = TermuxStatus{.title = std::move(title),
                               .message = std::move(message),
                               .error = error,
                               .visible = true};
  });
}

std::string AppendDetail(std::string message, const domain::TermuxError &error) {
  if (!error.detail.empty()) {
    message.push_back('\n');
    message.append(error.detail);
  }
  return message;
}

std::string LocalizedOnly(const ResolvedStrings &strings,
                          ResolvedStringMember member,
                          const domain::TermuxError &) {
  return strings.*member;
}

std::string LocalizedWithDetail(const ResolvedStrings &strings,
                                ResolvedStringMember member,
                                const domain::TermuxError &error) {
  return AppendDetail(strings.*member, error);
}

using ErrorFormatter = std::string (*)(const ResolvedStrings &,
                                       ResolvedStringMember,
                                       const domain::TermuxError &);

struct ErrorPresentation final {
  domain::TermuxErrorCode code;
  ResolvedStringMember message;
  ErrorFormatter format;
};

const std::array kErrorPresentations{
    ErrorPresentation{domain::TermuxErrorCode::unsupported,
                      &ResolvedStrings::error_bridge, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::not_installed,
                      &ResolvedStrings::error_not_installed, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::permission_denied,
                      &ResolvedStrings::error_permission, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::no_result,
                      &ResolvedStrings::error_no_result, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::timeout,
                      &ResolvedStrings::error_timeout, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::parse_failed,
                      &ResolvedStrings::error_parse, &LocalizedWithDetail},
    ErrorPresentation{domain::TermuxErrorCode::bridge_closed,
                      &ResolvedStrings::error_bridge, &LocalizedOnly},
    ErrorPresentation{domain::TermuxErrorCode::persistence_failed,
                      &ResolvedStrings::error_persist, &LocalizedWithDetail},
    ErrorPresentation{domain::TermuxErrorCode::verification_failed,
                      &ResolvedStrings::error_verification,
                      &LocalizedWithDetail},
    ErrorPresentation{domain::TermuxErrorCode::command_failed,
                      &ResolvedStrings::setup_failed_title,
                      &LocalizedWithDetail},
    ErrorPresentation{domain::TermuxErrorCode::platform_error,
                      &ResolvedStrings::setup_failed_title,
                      &LocalizedWithDetail},
};

std::string DescribeError(const domain::TermuxError &error,
                          const ResolvedStrings &strings) {
  const auto found = std::ranges::find(kErrorPresentations, error.code,
                                       &ErrorPresentation::code);
  const auto &presentation =
      found == kErrorPresentations.end() ? kErrorPresentations.back() : *found;
  return std::invoke(presentation.format, strings, presentation.message, error);
}

void ReportFailure(State<TermuxPageState> state, const ResolvedStrings &strings,
                   const domain::TermuxError &error) {
  state.Update([](TermuxPageState &next) { next.setup_running = false; });
  SetStatus(state, strings.setup_failed_title,
            DescribeError(error, strings), true);
}

struct ActionContext final {
  std::shared_ptr<application::TermuxIntegrationGateway> gateway;
  std::shared_ptr<application::SshSettingsService> ssh_settings;
  std::shared_ptr<Clipboard> clipboard;
  State<TermuxPageState> state;
  TaskScope tasks;
  ResolvedStrings strings;
};

void CopyCommand(const ActionContext &context) {
  if (!context.clipboard ||
      !context.clipboard->WriteText(
          application::kTermuxAllowExternalAppsCommand)) {
    SetStatus(context.state, context.strings.setup_failed_title,
              context.strings.error_clipboard, true);
    return;
  }
  SetStatus(context.state, context.strings.copied_title,
            context.strings.copied_message, false);
}

void RequestPermission(const ActionContext &context) {
  context.gateway->RequestRunCommandPermission(
      [tasks = context.tasks, state = context.state,
       strings = context.strings](domain::TermuxResult<void> result) mutable {
        tasks.Post([state, strings = std::move(strings),
                    result = std::move(result)]() mutable {
          if (!result) {
            ReportFailure(state, strings, result.error());
            return;
          }
          SetStatus(state, strings.requested_title, strings.requested_message,
                    false);
        });
      });
}

void OpenTermux(const ActionContext &context) {
  context.gateway->OpenTermux(
      [tasks = context.tasks, state = context.state,
       strings = context.strings](domain::TermuxResult<void> result) mutable {
        tasks.Post([state, strings = std::move(strings),
                    result = std::move(result)]() mutable {
          if (!result) {
            ReportFailure(state, strings, result.error());
            return;
          }
          SetStatus(state, strings.opened_title, strings.opened_message, false);
        });
      });
}

std::string ValueOrUnknown(std::string_view value,
                           const ResolvedStrings &strings) {
  return value.empty() ? strings.unknown : std::string(value);
}

std::string SetupDoneMessage(const domain::TermuxSetupResult &setup,
                             const ResolvedStrings &strings) {
  return strings.shell_prefix + ValueOrUnknown(setup.shell, strings) + '\n' +
         strings.rc_prefix + ValueOrUnknown(setup.rc_path, strings) + '\n' +
         setup.verification_output;
}

void SetupOpenSsh(const ActionContext &context) {
  context.state.Update(
      [](TermuxPageState &next) { next.setup_running = true; });
  SetStatus(context.state, context.strings.setup_title,
            context.strings.setup_message, false);
  context.gateway->SetupOpenSsh(
      std::string(application::kTermuxOpenSshSetupScript), 15min,
      [tasks = context.tasks, state = context.state,
       settings = context.ssh_settings,
       strings = context.strings](domain::TermuxResult<std::string> output) mutable {
        tasks.Post([tasks, state, settings, strings = std::move(strings),
                    output = std::move(output)]() mutable {
          if (!output) {
            ReportFailure(state, strings, output.error());
            return;
          }
          auto setup = domain::ParseTermuxSetupOutput(*output);
          if (!setup) {
            auto error = std::move(setup.error());
            error.detail = domain::RedactTermuxPrivateKey(
                error.detail, strings.private_key_replacement);
            ReportFailure(state, strings, error);
            return;
          }
          tasks.Launch([state, settings, strings = std::move(strings),
                        setup = std::move(*setup)]() mutable -> Task<void> {
            auto saved = co_await settings->Save(setup.config);
            if (!saved) {
              ReportFailure(
                  state, strings,
                  domain::TermuxError{
                      .code = domain::TermuxErrorCode::persistence_failed,
                      .detail = saved.error().message,
                  });
              co_return;
            }
            state.Update(
                [](TermuxPageState &next) { next.setup_running = false; });
            if (!setup.ConnectionVerified()) {
              ReportFailure(
                  state, strings,
                  domain::TermuxError{
                      .code = domain::TermuxErrorCode::verification_failed,
                      .detail = setup.verification_output,
                  });
              co_return;
            }
            SetStatus(state, strings.setup_done_title,
                      SetupDoneMessage(setup, strings), false);
          });
        });
      });
}

using ActionInvoke = void (*)(const ActionContext &);

struct ActionVisual final {
  Color background;
  Color border;
  Color foreground;
};

struct ActionSpec final {
  ImageResource icon;
  StringVariant label;
  ActionVisual visual;
  bool enabled;
  ActionInvoke invoke;
};

View ActionButton(const ActionSpec &spec, const ActionContext &context) {
  return Row{
      Glyph(spec.icon, 15.0F, spec.visual.foreground),
      Text(spec.label)
          .Style(Label(11.0F, FontWeight::Bold, spec.visual.foreground)),
  }.OnClick([context, invoke = spec.invoke] { std::invoke(invoke, context); })
      .With(Frame{.height = 38.0F}, Grow(), Spacing(6.0F),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Padding(EdgeInsets::Symmetric(8.0F, 0.0F)),
            Background(spec.visual.background),
            Border(spec.visual.border, 1.0F), CornerRadius(8.0F),
            Enabled{spec.enabled}, Opacity(spec.enabled ? 1.0F : 0.65F),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View StatusView(const TermuxStatus &status) {
  const Color foreground = status.error ? colors::danger : colors::secondary;
  return Text(status.title + '\n' + status.message)
      .Style(TextStyle{Font::Monospace(11.0F), foreground})
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
            Background(status.error ? colors::danger_muted : colors::code),
            Border(status.error ? colors::danger : colors::code_border, 1.0F),
            CornerRadius(8.0F));
}

} // namespace

[[huxerui::composable]] View TermuxIntegrationScreen(
    std::shared_ptr<application::TermuxIntegrationGateway> gateway,
    std::shared_ptr<application::SshSettingsService> ssh_settings) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto clipboard = UseApplication().Clipboard();
  const auto tasks = UseTaskScope();
  auto state = UseState(TermuxPageState{});
  const ResolvedStrings strings{
      .copied_title = UseString(app::strings::screen_termux_status_copied_title),
      .copied_message =
          UseString(app::strings::screen_termux_status_copied_message),
      .requested_title =
          UseString(app::strings::screen_termux_status_requested_title),
      .requested_message =
          UseString(app::strings::screen_termux_status_requested_message),
      .opened_title = UseString(app::strings::screen_termux_status_opened_title),
      .opened_message =
          UseString(app::strings::screen_termux_status_opened_message),
      .setup_title = UseString(app::strings::screen_termux_status_setup_title),
      .setup_message =
          UseString(app::strings::screen_termux_status_setup_message),
      .setup_done_title =
          UseString(app::strings::screen_termux_status_setup_done_title),
      .setup_failed_title =
          UseString(app::strings::screen_termux_status_setup_failed_title),
      .shell_prefix =
          UseString(app::strings::screen_termux_status_setup_done_shell),
      .rc_prefix = UseString(app::strings::screen_termux_status_setup_done_rc),
      .unknown = UseString(app::strings::screen_termux_unknown),
      .private_key_replacement =
          UseString(app::strings::screen_termux_redact_replacement),
      .error_not_installed =
          UseString(app::strings::screen_termux_error_not_installed),
      .error_permission =
          UseString(app::strings::screen_termux_error_permission),
      .error_no_result = UseString(app::strings::screen_termux_error_no_result),
      .error_timeout = UseString(app::strings::screen_termux_error_timeout),
      .error_parse = UseString(app::strings::screen_termux_error_parse),
      .error_bridge = UseString(app::strings::screen_termux_error_bridge),
      .error_persist = UseString(app::strings::screen_termux_error_persist),
      .error_verification =
          UseString(app::strings::screen_termux_error_verification),
      .error_clipboard =
          UseString(app::strings::screen_termux_error_clipboard),
  };

  const bool actions_enabled =
      gateway && gateway->Capabilities().integration;
  const TermuxStatus visible_status = actions_enabled
      ? state->status
      : TermuxStatus{.title = strings.setup_failed_title,
                     .message = strings.error_bridge,
                     .error = true,
                     .visible = true};

  const ActionContext context{
      .gateway = gateway,
      .ssh_settings = std::move(ssh_settings),
      .clipboard = clipboard,
      .state = state,
      .tasks = tasks,
      .strings = strings,
  };
  const ActionVisual secondary{
      .background = colors::surface_light,
      .border = colors::border_light,
      .foreground = colors::secondary,
  };
  const ActionVisual primary{
      .background = colors::accent,
      .border = colors::accent,
      .foreground = colors::text_on_color,
  };
  const std::array action_catalog{
      std::array{
          ActionSpec{app::images::copy,
                     app::strings::screen_termux_copy_intent, secondary,
                     clipboard && clipboard->IsAvailable(), &CopyCommand},
          ActionSpec{app::images::shield_check,
                     app::strings::screen_termux_run_command_perm, secondary,
                     actions_enabled, &RequestPermission},
      },
      std::array{
          ActionSpec{app::images::external_link,
                     app::strings::screen_termux_open_termux, secondary,
                     actions_enabled, &OpenTermux},
          ActionSpec{app::images::download,
                     app::strings::screen_termux_auto_ssh, primary,
                     actions_enabled && !state->setup_running, &SetupOpenSsh},
      },
  };

  std::vector<View> action_rows;
  action_rows.reserve(action_catalog.size());
  for (const auto &row : action_catalog) {
    action_rows.push_back(Row{
        ActionButton(row[0], context),
        ActionButton(row[1], context),
    }.With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch)));
  }
  View action_grid =
      Column(std::move(action_rows))
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch));

  std::vector<View> action_card{
      SectionTitle(app::strings::screen_termux_actions_title),
      Gap(8.0F),
      action_grid,
  };
  if (visible_status.visible) {
    action_card.push_back(Gap(8.0F));
    action_card.push_back(StatusView(visible_status));
  }

  return Column{
      Header(navigation),
      Divider(),
      ScrollView(
          Column{
              Card({
                  SectionTitle(app::strings::screen_termux_section_use),
                  Gap(4.0F),
                  Description(app::strings::screen_termux_use_desc),
              }),
              Card({
                  SectionTitle(app::strings::screen_termux_section_steps),
                  Gap(8.0F),
                  Step("1", app::strings::screen_termux_step_1),
                  Gap(8.0F),
                  Step("2", app::strings::screen_termux_step_2),
                  Gap(8.0F),
                  Step("3", app::strings::screen_termux_step_3),
              }),
              Card({
                  SectionTitle(app::strings::screen_termux_section_intent),
                  Gap(8.0F),
                  SelectionArea(
                      Text(std::string(
                               application::kTermuxAllowExternalAppsCommand))
                          .Style(TextStyle{Font::Monospace(
                                               kTypography.command.size),
                                           colors::secondary}))
                      .With(Frame{.min_height =
                                      kTypography.command.minimum_height},
                            Padding(12.0F), Background(colors::code),
                            Border(colors::code_border, 1.0F),
                            CornerRadius(8.0F)),
              }),
              Card(std::move(action_card)),
          }.With(Spacing(12.0F),
                 Padding(EdgeInsets{.top = 16.0F,
                                    .right = 16.0F,
                                    .bottom = 112.0F,
                                    .left = 16.0F}),
                 CrossAlign(CrossAxisAlignment::Stretch),
                 Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch),
         Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
