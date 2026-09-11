#include "presentation/screens/skill_hub_screens.h"

#include <algorithm>
#include <array>
#include <format>
#include <functional>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/app.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "presentation/components/skill_hub_components.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class LoadPhase : std::uint8_t { loading, ready, failed };

struct StoreState final {
  domain::SkillHubPage page;
  std::optional<domain::SkillHubSession> session;
  std::string error;
  std::string account_error;
  std::string sort_by{"downloads"};
  int page_number{1};
  std::uint64_t generation{};
  LoadPhase phase{LoadPhase::loading};
  bool account_loading{true};
  bool logging_out{};
};

struct SortPresentation final {
  std::string_view value;
  StringResource label;
};

const std::array kSorts{
    SortPresentation{"downloads", app::strings::skillhub_filter_hot_downloads},
    SortPresentation{"stars", app::strings::skillhub_filter_most_stars},
    SortPresentation{"updated_at", app::strings::skillhub_filter_recent_update},
};

StringVariant Count(const std::int64_t value) {
  if (value >= 10'000)
    return StringVariant::Format(
        app::strings::skillhub_count_wan,
        std::format("{:.1f}", static_cast<double>(value) / 10'000.0));
  return std::to_string(value);
}

Task<void> LoadStore(const SkillHubScreenServices services,
                     const State<StoreState> state, std::string keyword) {
  const auto generation = state->generation + 1;
  state.Update([generation](StoreState &next) {
    next.generation = generation;
    next.phase = LoadPhase::loading;
    next.error.clear();
    next.page.skills.clear();
  });
  auto loaded = co_await services.catalog->List({
      .page = state->page_number,
      .page_size = 20,
      .keyword = std::move(keyword),
      .category = {},
      .source = "all",
      .sort_by = state->sort_by,
      .order = "desc",
  });
  if (generation != state->generation)
    co_return;
  state.Update([loaded = std::move(loaded)](StoreState &next) mutable {
    if (!loaded) {
      next.phase = LoadPhase::failed;
      next.error = std::move(loaded.error().message);
      return;
    }
    next.phase = LoadPhase::ready;
    next.page = std::move(*loaded);
  });
}

Task<void> LoadAccount(const SkillHubScreenServices services,
                       const State<StoreState> state, std::string cookie) {
  state.Update([](StoreState &next) {
    next.account_loading = true;
    next.account_error.clear();
  });
  auto loaded = co_await services.session->CurrentSession(std::move(cookie));
  state.Update([loaded = std::move(loaded)](StoreState &next) mutable {
    next.account_loading = false;
    if (!loaded) {
      next.session.reset();
      next.account_error = std::move(loaded.error().message);
      return;
    }
    next.session = std::move(*loaded);
  });
}

void RequestAccount(const SkillHubScreenServices &services,
                    const State<StoreState> state, const TaskScope &tasks) {
  services.platform->ReadSessionCookie(
      [services, state, tasks](application::SkillHubCookieResult result) {
        if (!result.Succeeded()) {
          state.Update(
              [message = std::move(result.error)](StoreState &next) mutable {
                next.account_loading = false;
                next.session.reset();
                next.account_error = std::move(message);
              });
          return;
        }
        tasks.Launch(LoadAccount(services, state, std::move(result.cookie)));
      });
}

Task<void> Logout(const SkillHubScreenServices services,
                  const State<StoreState> state, const ToastHandle toast,
                  const DialogContext dialog, std::string cookie) {
  state.Update([](StoreState &next) { next.logging_out = true; });
  auto result = co_await services.session->Logout(std::move(cookie));
  state.Update([&](StoreState &next) {
    next.logging_out = false;
    if (result) {
      next.session = domain::SkillHubSession{};
      next.account_error.clear();
    } else {
      next.account_error = result.error().message;
    }
  });
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  services.platform->ClearSessionCookies();
  dialog.Dismiss();
  toast.Show(app::strings::skillhub_logged_out_success);
}

View AccountDialogDangerButton(StringVariant text,
                               std::function<void()> on_click,
                               const bool enabled) {
  return Stack{
      Text(std::move(text))
          .Style(SkillHubLabel(13.0F, FontWeight::Medium, colors::danger))
          .Align(TextAlign::Center)}
      .OnClick(std::move(on_click))
      .With(Frame{.min_height = 44.0F}, Padding(12.0F), Grow(),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::danger_muted),
            Border{.color = colors::danger, .width = 1.0F}, CornerRadius(10.0F),
            Enabled(enabled), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

void ShowAccountDialog(const SkillHubScreenServices &services,
                       const State<StoreState> state,
                       const DialogHandle &dialogs, const TaskScope &tasks,
                       const ToastHandle &toast) {
  const auto account = state->session->account;
  dialogs.Show([services, state, tasks, toast, account](DialogContext dialog) {
    std::vector<View> identity{
        Stack{SkillHubGlyph(app::images::user, 27.0F, colors::accent)}.With(
            Frame{.width = 54.0F, .height = 54.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::accent_muted), CornerRadius(14.0F)),
        Text(account.display_name)
            .Style(SkillHubLabel(17.0F, FontWeight::Medium))
            .Align(TextAlign::Center)
            .With(Padding(EdgeInsets{.top = 12.0F})),
    };
    if (!account.handle.empty()) {
      identity.push_back(Text("@" + account.handle)
                             .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                                  colors::tertiary))
                             .Align(TextAlign::Center));
    }
    identity.push_back(
        Text(app::strings::skillhub_account_connected)
            .Style(SkillHubLabel(13.0F, FontWeight::Medium, colors::accent))
            .Align(TextAlign::Center)
            .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                  Background(colors::accent_muted), CornerRadius(9.0F)));
    identity.push_back(
        Row{
            SkillHubDialogButton(
                app::strings::skillhub_continue, false,
                [dialog] { dialog.Dismiss(); }, !state->logging_out),
            AccountDialogDangerButton(
                state->logging_out
                    ? StringVariant{app::strings::skillhub_logging_out}
                    : StringVariant{app::strings::skillhub_logout},
                [services, state, tasks, toast, dialog] {
                  services.platform->ReadSessionCookie(
                      [services, state, tasks, toast,
                       dialog](application::SkillHubCookieResult result) {
                        if (!result.Succeeded()) {
                          state.Update([message = std::move(result.error)](
                                           StoreState &next) mutable {
                            next.account_error = std::move(message);
                          });
                          toast.Show(state->account_error);
                          return;
                        }
                        tasks.Launch(Logout(services, state, toast, dialog,
                                            std::move(result.cookie)));
                      });
                },
                !state->logging_out),
        }
            .With(Spacing(8.0F), Padding(EdgeInsets{.top = 16.0F})));
    return SkillHubDialogPanel(std::move(identity));
  });
}

[[huxerui::composable]] View
SkillCard(const domain::SkillHubSummary &skill,
          const std::shared_ptr<application::SkillHubCatalog> &catalog,
          std::function<void()> open) {
  auto icon = UseState(std::optional<ImageAsset>{});
  const auto tasks = UseTaskScope();
  if (!skill.icon_url.empty()) {
    Lifecycle(
        [tasks, catalog, url = skill.icon_url, icon] {
          tasks.Launch([catalog, url, icon]() -> Task<void> {
            auto bytes = co_await catalog->Icon(url);
            if (!bytes)
              co_return;
            try {
              icon = ImageAsset::FromEncoded(std::move(*bytes));
            } catch (const std::invalid_argument &) {
            }
          });
        },
        skill.icon_url);
  }

  View artwork =
      icon->has_value()
          ? Image(icon.Get().value())
                .Fit(ImageFit::Cover)
                .With(Frame{.width = 48.0F, .height = 48.0F},
                      CornerRadius(12.0F), ClipChildren())
          : Stack{SkillHubGlyph(app::images::package, 25.0F, colors::accent)}
                .With(Frame{.width = 48.0F, .height = 48.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Background(colors::accent_muted), CornerRadius(12.0F));

  std::vector<View> title_children{
      Text(skill.name)
          .Style(SkillHubLabel(16.0F, FontWeight::Medium))
          .With(Frame{.max_height = 20.0F}, ClipChildren(), Grow()),
  };
  if (skill.verified)
    title_children.push_back(SkillHubTag(app::strings::skillhub_verified,
                                         colors::accent, colors::accent_muted));

  std::vector<View> labels{
      Row(std::move(title_children))
          .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Center)),
      Text(skill.owner.empty() ? "SkillHub" : skill.owner)
          .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
  };
  if (!skill.category.empty() || skill.requires_api_key) {
    std::vector<View> tags;
    if (!skill.category.empty())
      tags.push_back(SkillHubTag(skill.category, colors::secondary,
                                 colors::surface_light));
    if (skill.requires_api_key)
      tags.push_back(SkillHubTag(app::strings::skillhub_requires_api_key,
                                 colors::warning, colors::surface_light));
    labels.push_back(Row(std::move(tags)).With(Spacing(4.0F)));
  }
  labels.push_back(
      Text(skill.description)
          .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::secondary))
          .With(Frame{.max_height = 34.0F}, ClipChildren()));
  const std::string version =
      skill.version.empty() ? std::string{} : "  ·  v" + skill.version;
  labels.push_back(
      Text("↓ " + UseString(Count(skill.downloads)) + "   ☆ " +
           UseString(Count(skill.stars)) + version)
          .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)));

  return Row{
      artwork,
      Column(std::move(labels))
          .With(Spacing(4.0F),
                Padding(EdgeInsets{.right = 4.0F, .left = 12.0F}), Grow()),
      SkillHubIconSlot(app::images::chevron_right, 16.0F, 24.0F,
                       colors::tertiary),
  }
      .OnClick(std::move(open))
      .With(Padding(EdgeInsets{
                .top = 12.0F, .right = 8.0F, .bottom = 12.0F, .left = 12.0F}),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated),
            Border{.color = colors::border, .width = 1.0F}, CornerRadius(12.0F),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View FilterChip(const SortPresentation &sort, const State<StoreState> state,
                const TaskScope &tasks, const SkillHubScreenServices &services,
                const State<TextEditingValue> search) {
  const bool selected = state->sort_by == sort.value;
  return Stack{Text(sort.label)
                   .Style(SkillHubLabel(11.0F, FontWeight::Medium,
                                        selected ? Color(colors::accent)
                                                 : Color(colors::secondary)))
                   .Align(TextAlign::Center)}
      .OnClick([sort, state, tasks, services, search] {
        if (state->sort_by == sort.value)
          return;
        state.Update([&](StoreState &next) {
          next.sort_by = sort.value;
          next.page_number = 1;
        });
        tasks.Launch(LoadStore(services, state, search->text));
      })
      .With(Padding(8.0F), Grow(),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(selected ? Color(colors::accent_muted)
                                : Color(colors::elevated)),
            Border{.color =
                       selected ? Color(colors::accent) : Color(colors::border),
                   .width = 1.0F},
            CornerRadius(10.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

} // namespace

[[huxerui::composable]] View
SkillStoreScreen(const SkillHubScreenServices &services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  auto state = UseState(StoreState{});
  auto search = UseState(TextEditingValue::FromText(""));

  Lifecycle([services, tasks, state, search] {
    tasks.Launch(LoadStore(services, state, search->text));
    RequestAccount(services, state, tasks);
  });

  View intro =
      Row{
          Column{
              Text(app::strings::skillhub_discover_community_skills)
                  .Style(SkillHubLabel(20.0F, FontWeight::Medium)),
              Text(app::strings::skillhub_browse_install_desc)
                  .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                       colors::tertiary)),
          }
              .With(Spacing(4.0F), Grow()),
          Stack{SkillHubGlyph(app::images::sparkles, 24.0F, colors::accent)}
              .With(
                  Frame{.width = 44.0F, .height = 44.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(colors::accent_muted), CornerRadius(12.0F)),
      }
          .With(CrossAlign(CrossAxisAlignment::Center));

  TextInputConfiguration search_input;
  search_input.type = TextInputType::Text;
  search_input.action = TextInputAction::Search;
  View search_box =
      Row{
          Stack{SkillHubGlyph(app::images::search, 18.0F, colors::tertiary)}
              .With(Frame{.width = 36.0F, .height = 44.0F},
                    Align(HorizontalAlignment::Center,
                          VerticalAlignment::Center)),
          TextField(search.Get())
              .Placeholder(app::strings::skillhub_search_hint)
              .InputConfiguration(search_input)
              .LineLimits(TextFieldLineLimits::SingleLine())
              .OnChanged([search](TextEditingValue value) {
                search = std::move(value);
              })
              .OnSubmitted([services, state, search, tasks] {
                state.Update([](StoreState &next) { next.page_number = 1; });
                tasks.Launch(LoadStore(services, state, search->text));
              })
              .With(Frame{.height = 48.0F}, Grow()),
      }
          .With(Frame{.min_height = 48.0F},
                Padding(EdgeInsets{.right = 8.0F, .left = 8.0F}),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::input),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F));

  std::vector<View> filters;
  filters.reserve(kSorts.size());
  for (const auto &sort : kSorts)
    filters.push_back(FilterChip(sort, state, tasks, services, search));

  StringVariant account_title = app::strings::skillhub_checking_account;
  StringVariant account_subtitle = app::strings::skillhub_login_via_official;
  ImageResource account_action = app::images::external_link;
  if (!state->account_loading && !state->account_error.empty()) {
    account_title = app::strings::skillhub_account_check_failed;
    account_subtitle = UseString(app::strings::skillhub_retry_here) + " · " +
                       state->account_error;
    account_action = app::images::refresh_cw;
  } else if (state->session && state->session->authenticated) {
    account_title = state->session->account.display_name;
    account_subtitle =
        state->session->account.handle.empty()
            ? UseString(app::strings::skillhub_logged_in)
            : "@" + state->session->account.handle + " · " +
                  UseString(app::strings::skillhub_logged_in_suffix);
    account_action = app::images::chevron_right;
  } else if (!state->account_loading) {
    account_title = app::strings::skillhub_login_account;
    account_subtitle = app::strings::skillhub_login_desc;
  }

  View account =
      Row{
          Stack{SkillHubGlyph(app::images::user, 19.0F, colors::accent)}.With(
              Frame{.width = 36.0F, .height = 36.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Background(colors::accent_muted), CornerRadius(9.0F)),
          Column{
              Text(std::move(account_title))
                  .Style(SkillHubLabel(13.0F, FontWeight::Medium)),
              Text(std::move(account_subtitle))
                  .Style(SkillHubLabel(11.0F, FontWeight::Regular,
                                       colors::tertiary)),
          }
              .With(Spacing(2.0F), Padding(EdgeInsets{.left = 8.0F}), Grow()),
          SkillHubIconSlot(account_action, 16.0F, 28.0F, colors::tertiary),
      }
          .OnClick([services, state, tasks, navigation, dialogs, toast] {
            if (state->account_loading)
              return;
            if (!state->account_error.empty()) {
              RequestAccount(services, state, tasks);
            } else if (state->session && state->session->authenticated) {
              ShowAccountDialog(services, state, dialogs, tasks, toast);
            } else {
              navigation.Push(domain::AppRoute::skill_hub_login);
            }
          })
          .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::elevated), CornerRadius(12.0F),
                Enabled(!state->account_loading), Focusable(),
                PointerCursor(PointerCursorKind::Hand));

  std::vector<View> content{
      intro,
      SkillHubTopMargin(std::move(search_box), 16.0F),
      SkillHubTopMargin(Row(std::move(filters)).With(Spacing(4.0F)), 8.0F),
      SkillHubTopMargin(std::move(account), 8.0F),
  };
  if (state->session && state->session->authenticated) {
    content.push_back(SkillHubTopMargin(
        Stack{
            Text(app::strings::skillhub_feature_center)
                .Style(SkillHubLabel(13.0F, FontWeight::Medium, colors::accent))
                .Align(TextAlign::Center)}
            .OnClick([navigation] {
              navigation.Push(domain::AppRoute::skill_hub_center);
            })
            .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                  Background(colors::elevated),
                  Border{.color = colors::accent, .width = 1.0F},
                  CornerRadius(12.0F), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
        8.0F));
  }

  if (state->phase == LoadPhase::loading) {
    content.push_back(Stack{ProgressCircle()}.With(
        Padding(16.0F),
        Align(HorizontalAlignment::Center, VerticalAlignment::Center)));
    content.push_back(
        Text(StringVariant::Format(app::strings::skillhub_loading_page,
                                   state->page_number))
            .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::tertiary))
            .Align(TextAlign::Center));
  } else if (state->phase == LoadPhase::failed) {
    content.push_back(
        Text(UseString(app::strings::skillhub_load_failed_retry) + "\n" +
             state->error)
            .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::tertiary))
            .Align(TextAlign::Center)
            .OnClick([services, state, search, tasks] {
              tasks.Launch(LoadStore(services, state, search->text));
            })
            .With(Padding(12.0F), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)));
  } else {
    const std::string status =
        state->page.skills.empty()
            ? UseString(app::strings::skillhub_no_matching_skills)
            : UseString(app::strings::skillhub_page_count, state->page_number,
                        UseString(Count(state->page.total)));
    content.push_back(
        Text(status)
            .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::tertiary))
            .Align(TextAlign::Center)
            .With(Padding(EdgeInsets{.top = 12.0F})));
    for (const auto &skill : state->page.skills) {
      content.push_back(
          SkillHubTopMargin(SkillCard(skill, services.catalog,
                                      [navigation, slug = skill.slug] {
                                        navigation.Push(
                                            domain::AppRoute::SkillStoreDetail(
                                                slug));
                                      }),
                            8.0F)
              .Key(skill.slug));
    }
  }

  content.push_back(
      Row{
          SkillHubDialogButton(
              app::strings::skillhub_previous_page, false,
              [services, state, search, tasks] {
                if (state->page_number <= 1)
                  return;
                state.Update([](StoreState &next) { --next.page_number; });
                tasks.Launch(LoadStore(services, state, search->text));
              },
              state->page_number > 1),
          SkillHubDialogButton(
              app::strings::skillhub_next_page, false,
              [services, state, search, tasks] {
                state.Update([](StoreState &next) { ++next.page_number; });
                tasks.Launch(LoadStore(services, state, search->text));
              }),
      }
          .With(Spacing(8.0F), Padding(EdgeInsets{.top = 12.0F})));

  return SkillHubScrollablePage(
      app::strings::skillhub_title_store, [navigation] { navigation.Pop(); },
      std::move(content));
}

} // namespace linecode::presentation
