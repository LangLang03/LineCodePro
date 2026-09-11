#include "presentation/screens/skill_hub_screens.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <functional>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>
#include <huxerui/webview.h>

#include "domain/app_state.h"
#include "presentation/components/skill_hub_components.h"
#include "presentation/line_theme.h"
#include "presentation/skill_hub_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

View Notice(const StringVariant &text, const float icon_size = 17.0F,
            const float slot_size = 28.0F) {
  return Row{
      SkillHubIconSlot(app::images::shield_check, icon_size, slot_size,
                       colors::accent),
      Text(text).Style(
          SkillHubLabel(11.0F, FontWeight::Regular, colors::secondary)),
  }
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Spacing(8.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::accent_muted), CornerRadius(10.0F));
}

View CenterEntry(
    const SkillHubDestinationPresentation &item,
    const RouteNavigationController<domain::AppRoute> &navigation) {
  return Row{
      Column{
          Text(item.entry_title)
              .Style(SkillHubLabel(13.0F, FontWeight::Medium)),
          Text(item.description)
              .Style(
                  SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      SkillHubIconSlot(app::images::chevron_right, 16.0F, 26.0F,
                       colors::tertiary),
  }
      .OnClick([navigation, destination = item.destination] {
        navigation.Push(domain::AppRoute::SkillHubSite(destination));
      })
      .With(Padding(EdgeInsets{
                .top = 8.0F, .right = 8.0F, .bottom = 8.0F, .left = 12.0F}),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), CornerRadius(11.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

Task<void> CheckLogin(
    const SkillHubScreenServices services, const State<bool> authenticated,
    const State<std::string> account_name, const State<bool> check_in_flight,
    const RouteNavigationController<domain::AppRoute> navigation,
    const ToastHandle toast, std::string cookie) {
  if (authenticated.Get()) {
    check_in_flight = false;
    co_return;
  }
  auto session = co_await services.session->CurrentSession(std::move(cookie));
  check_in_flight = false;
  if (session && session->authenticated) {
    authenticated = true;
    account_name = session->account.display_name;
    toast.Show(app::strings::skillhub_login_success);
    co_await Delay(std::chrono::milliseconds{150});
    navigation.Pop();
  }
}

void RequestLoginCheck(
    const SkillHubScreenServices &services, const TaskScope &tasks,
    const State<bool> authenticated, const State<std::string> account_name,
    const State<std::string> bridge_error, const State<bool> check_in_flight,
    const RouteNavigationController<domain::AppRoute> &navigation,
    const ToastHandle &toast, std::string unavailable_message) {
  if (authenticated.Get() || check_in_flight.Get())
    return;
  check_in_flight = true;
  services.platform->ReadSessionCookie(
      [services, tasks, authenticated, account_name, bridge_error,
       check_in_flight, navigation, toast,
       unavailable_message = std::move(unavailable_message)](
          application::SkillHubCookieResult result) mutable {
        if (!result.Succeeded()) {
          check_in_flight = false;
          bridge_error = std::move(unavailable_message);
          return;
        }
        bridge_error = std::string{};
        tasks.Launch(CheckLogin(services, authenticated, account_name,
                                check_in_flight, navigation, toast,
                                std::move(result.cookie)));
      });
}

Task<void> PollLogin(
    const SkillHubScreenServices services, const TaskScope tasks,
    const State<bool> authenticated, const State<std::string> account_name,
    const State<std::string> bridge_error, const State<bool> check_in_flight,
    const RouteNavigationController<domain::AppRoute> navigation,
    const ToastHandle toast, std::string unavailable_message) {
  while (!authenticated.Get() && bridge_error->empty()) {
    RequestLoginCheck(services, tasks, authenticated, account_name,
                      bridge_error, check_in_flight, navigation, toast,
                      unavailable_message);
    co_await Delay(std::chrono::seconds{1});
  }
}

struct PublishState final {
  std::vector<domain::SkillRecord> skills;
  std::string error;
  std::size_t selected{};
  bool loading{true};
  bool busy{};
};

std::string SlugFor(std::string_view value) {
  std::string result;
  bool previous_dash = false;
  for (const unsigned char character : value) {
    const char lower = static_cast<char>(std::tolower(character));
    const bool safe = std::isalnum(character) != 0 || lower == '.' ||
                      lower == '_' || lower == '-';
    if (safe) {
      result.push_back(lower);
      previous_dash = lower == '-';
    } else if (!result.empty() && !previous_dash) {
      result.push_back('-');
      previous_dash = true;
    }
  }
  while (!result.empty() && result.front() == '-')
    result.erase(result.begin());
  while (!result.empty() && result.back() == '-')
    result.pop_back();
  return result;
}

Task<void> LoadPublishSkills(const SkillHubScreenServices services,
                             const State<PublishState> state,
                             const State<TextEditingValue> slug,
                             const State<TextEditingValue> display_name,
                             const State<TextEditingValue> version) {
  auto result = co_await services.repository->List(services.roots);
  state.Update([&](PublishState &next) {
    next.loading = false;
    if (!result) {
      next.error = result.error().message;
      return;
    }
    next.skills = std::move(*result);
    std::erase_if(next.skills, [](const domain::SkillRecord &skill) {
      return skill.location == domain::SkillLocation::ssh;
    });
  });
  if (state->skills.empty())
    co_return;
  const auto &skill = state->skills.front();
  slug = TextEditingValue::FromText(SlugFor(skill.name));
  display_name = TextEditingValue::FromText(skill.name);
  if (version->text.empty())
    version = TextEditingValue::FromText("1.0.0");
}

void ApplySelectedSkill(const State<PublishState> state,
                        const State<TextEditingValue> slug,
                        const State<TextEditingValue> display_name,
                        const State<TextEditingValue> version) {
  if (state->skills.empty())
    return;
  const auto &skill = state->skills[state->selected];
  slug = TextEditingValue::FromText(SlugFor(skill.name));
  display_name = TextEditingValue::FromText(skill.name);
  if (version->text.empty())
    version = TextEditingValue::FromText("1.0.0");
}

Task<void>
PublishSkill(const SkillHubScreenServices services,
             const State<PublishState> state,
             const State<TextEditingValue> slug,
             const State<TextEditingValue> display_name,
             const State<TextEditingValue> version,
             const RouteNavigationController<domain::AppRoute> navigation,
             const ToastHandle toast, const std::string login_required,
             std::string cookie) {
  if (state->skills.empty() || state->busy)
    co_return;
  state.Update([](PublishState &next) {
    next.busy = true;
    next.error.clear();
  });
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session || !session->authenticated) {
    const std::string message =
        !session ? session.error().message : login_required;
    state.Update([&](PublishState &next) {
      next.busy = false;
      next.error = message;
    });
    toast.Show(message);
    co_return;
  }
  auto published = co_await services.session->Publish(
      cookie, {.skill = state->skills[state->selected],
               .slug = slug->text,
               .display_name = display_name->text,
               .version = version->text});
  state.Update([&](PublishState &next) {
    next.busy = false;
    next.error = published ? std::string{} : published.error().message;
  });
  if (!published) {
    toast.Show(published.error().message);
    co_return;
  }
  toast.Show(app::strings::skillhub_publish_success);
  navigation.Pop();
}

View PublishTextField(StringResource label, StringResource placeholder,
                      State<TextEditingValue> value, const bool enabled) {
  return Column{
      Text(label).Style(
          SkillHubLabel(13.0F, FontWeight::Medium, colors::secondary)),
      TextField(value.Get())
          .Placeholder(placeholder)
          .LineLimits(TextFieldLineLimits::SingleLine())
          .OnChanged(
              [value](TextEditingValue next) { value = std::move(next); })
          .With(Frame{.min_height = 44.0F},
                Padding(EdgeInsets{.right = 9.0F, .left = 9.0F}),
                Background(colors::input),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F), Enabled(enabled)),
  }
      .With(Spacing(0.0F));
}

} // namespace

[[huxerui::composable]] View SkillHubCenterScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> content{
      Text(app::strings::skillhub_center_notice)
          .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(12.0F), Background(colors::elevated),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F)),
  };
  std::optional<StringResource> section;
  for (const auto &item : SkillHubDestinations()) {
    if (!section || *section != item.section) {
      section = item.section;
      content.push_back(
          Text(item.section)
              .Style(SkillHubLabel(16.0F, FontWeight::Medium))
              .With(Padding(EdgeInsets{.top = 16.0F, .bottom = 4.0F})));
    }
    content.push_back(
        SkillHubTopMargin(CenterEntry(item, navigation)
                              .Key(static_cast<std::uint8_t>(item.destination)),
                          8.0F));
  }
  return SkillHubScrollablePage(
      app::strings::skillhub_center_title, [navigation] { navigation.Pop(); },
      std::move(content));
}

[[huxerui::composable]] View
SkillHubWebScreen(const domain::SkillHubSiteRoute &route) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  auto requested_url =
      UseState("https://skillhub.cn" + SkillHubSitePath(route));
  const auto controller = UseWebViewController();
  StringVariant title = app::strings::skillhub_full_detail;
  if (const auto *destination =
          std::get_if<domain::SkillHubSiteRoute::Destination>(&route.target))
    title = SkillHubDestinationFor(destination->value).web_title;

  return Column{
      SkillHubHeader(std::move(title), [navigation] { navigation.Pop(); }),
      Divider(),
      SkillHubMargin(Notice(app::strings::skillhub_official_notice, 16.0F,
                            26.0F),
                     EdgeInsets::Symmetric(12.0F, 8.0F)),
      WebView({.url = requested_url.Get(), .java_script_enabled = true},
              controller)
          .On<WebViewEvents::NavigationRequested>(
              [](const WebViewNavigationRequest &request) {
                return !request.is_main_frame ||
                       IsAllowedSkillHubUrl(request.url);
              })
          .On<WebViewEvents::NavigationChanged>(
              [requested_url](const WebViewNavigationState &next) {
                if (!next.url.empty())
                  requested_url = next.url;
              })
          .With(Grow(), Frame{.min_height = 1.0F}, ClipChildren()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

[[huxerui::composable]] View
SkillHubLoginScreen(const SkillHubScreenServices &services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto authenticated = UseState(false);
  auto account_name = UseState(std::string{});
  auto bridge_error = UseState(std::string{});
  auto check_in_flight = UseState(false);
  auto requested_url = UseState(std::string{"https://skillhub.cn/"});
  const auto controller = UseWebViewController();
  const std::string session_unavailable =
      UseString(app::strings::skillhub_session_bridge_unavailable);
  Lifecycle([services, tasks, authenticated, account_name, bridge_error,
             check_in_flight, navigation, toast, session_unavailable] {
    tasks.Launch(PollLogin(services, tasks, authenticated, account_name,
                           bridge_error, check_in_flight, navigation, toast,
                           session_unavailable));
  });

  const StringVariant status =
      !bridge_error->empty() ? StringVariant{bridge_error.Get()}
      : authenticated.Get()
          ? StringVariant{UseString(app::strings::skillhub_logged_in) +
                          (account_name->empty() ? ""
                                                 : "：" + account_name.Get())}
          : StringVariant{app::strings::skillhub_auto_return_notice};
  return Column{
      SkillHubHeader(app::strings::skillhub_official_login,
                     [navigation] { navigation.Pop(); }),
      Divider(),
      SkillHubMargin(Notice(app::strings::skillhub_credential_notice),
                     EdgeInsets{.top = 8.0F, .right = 16.0F, .left = 16.0F}),
      Text(status)
          .Style(SkillHubLabel(11.0F, FontWeight::Regular,
                               authenticated.Get() ? Color(colors::accent)
                                                   : Color(colors::tertiary)))
          .Align(TextAlign::Center)
          .With(Padding(EdgeInsets::Symmetric(16.0F, 8.0F))),
      WebView({.url = requested_url.Get(), .java_script_enabled = true},
              controller)
          .On<WebViewEvents::NavigationRequested>(
              [](const WebViewNavigationRequest &request) {
                return !request.is_main_frame ||
                       IsAllowedSkillHubUrl(request.url);
              })
          .On<WebViewEvents::NavigationChanged>(
              [requested_url](const WebViewNavigationState &next) {
                if (!next.url.empty())
                  requested_url = next.url;
              })
          .On<WebViewEvents::LoadFinished>(
              [services, tasks, authenticated, account_name, bridge_error,
               check_in_flight, navigation, toast,
               session_unavailable](const WebViewNavigationState &) {
                RequestLoginCheck(services, tasks, authenticated, account_name,
                                  bridge_error, check_in_flight, navigation,
                                  toast, session_unavailable);
              })
          .With(Grow(), Frame{.min_height = 1.0F}, ClipChildren(),
                Semantics{.label = app::strings::skillhub_login_page_desc}),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

[[huxerui::composable]] View
SkillHubPublishScreen(const SkillHubScreenServices &services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const std::string login_required =
      UseString(app::strings::skillhub_login_to_star);
  auto state = UseState(PublishState{});
  auto slug = UseState(TextEditingValue::FromText(""));
  auto display_name = UseState(TextEditingValue::FromText(""));
  auto version = UseState(TextEditingValue::FromText(""));
  Lifecycle([services, state, slug, display_name, version, tasks] {
    tasks.Launch(
        LoadPublishSkills(services, state, slug, display_name, version));
  });

  std::vector<View> content{
      Text(app::strings::skillhub_publish_notice)
          .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(12.0F), Background(colors::elevated),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F)),
  };
  if (state->loading) {
    content.push_back(Stack{ProgressCircle()}.With(
        Padding(16.0F),
        Align(HorizontalAlignment::Center, VerticalAlignment::Center)));
  } else {
    const StringVariant selected =
        state->skills.empty()
            ? StringVariant{app::strings::skillhub_no_publishable_skills}
            : StringVariant{state->skills[state->selected].name + " · " +
                            std::string{domain::SkillLocationLabel(
                                state->skills[state->selected].location)}};
    content.push_back(SkillHubTopMargin(
        Row{
            Text(selected)
                .Style(SkillHubLabel(16.0F, FontWeight::Medium))
                .With(Grow()),
        }
            .OnClick([state, slug, display_name, version] {
              if (state->skills.empty())
                return;
              state.Update([](PublishState &next) {
                next.selected = (next.selected + 1) % next.skills.size();
              });
              ApplySelectedSkill(state, slug, display_name, version);
            })
            .With(Frame{.min_height = 48.0F},
                  Padding(EdgeInsets::Symmetric(12.0F, 12.0F)),
                  CrossAlign(CrossAxisAlignment::Center),
                  Background(colors::input),
                  Border{.color = colors::border_light, .width = 1.0F},
                  CornerRadius(12.0F),
                  Enabled(!state->skills.empty() && !state->busy), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
        16.0F));
    content.push_back(SkillHubTopMargin(
        PublishTextField(app::strings::skillhub_slug_label,
                         app::strings::skillhub_slug_hint, slug, !state->busy),
        12.0F));
    content.push_back(SkillHubTopMargin(
        PublishTextField(app::strings::skillhub_display_name_label,
                         app::strings::skillhub_display_name_hint, display_name,
                         !state->busy),
        12.0F));
    content.push_back(
        SkillHubTopMargin(PublishTextField(app::strings::skillhub_version_label,
                                           app::strings::skillhub_version_hint,
                                           version, !state->busy),
                          12.0F));
    content.push_back(SkillHubTopMargin(
        Stack{Text(state->busy
                       ? StringVariant{app::strings::skillhub_publishing}
                       : StringVariant{app::strings::skillhub_publish_to_hub})
                  .Style(SkillHubLabel(16.0F, FontWeight::Medium,
                                       colors::text_on_color))
                  .Align(TextAlign::Center)}
            .OnClick([services, state, slug, display_name, version, navigation,
                      tasks, toast, login_required] {
              services.platform->ReadSessionCookie(
                  [services, state, slug, display_name, version, navigation,
                   tasks, toast,
                   login_required](application::SkillHubCookieResult result) {
                    if (!result.Succeeded()) {
                      toast.Show(result.error);
                      return;
                    }
                    tasks.Launch(PublishSkill(services, state, slug,
                                              display_name, version, navigation,
                                              toast, login_required,
                                              std::move(result.cookie)));
                  });
            })
            .With(Frame{.min_height = 48.0F}, Padding(12.0F),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(colors::accent), CornerRadius(12.0F),
                  Enabled(!state->busy && !state->skills.empty()), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
        16.0F));
    if (state->busy)
      content.push_back(Stack{ProgressCircle()}.With(
          Padding(12.0F),
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)));
  }
  if (!state->error.empty())
    content.push_back(
        Text(state->error)
            .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::danger))
            .With(Padding(EdgeInsets{.top = 12.0F})));
  return SkillHubScrollablePage(
      app::strings::skillhub_publish_title, [navigation] { navigation.Pop(); },
      std::move(content));
}

} // namespace linecode::presentation
