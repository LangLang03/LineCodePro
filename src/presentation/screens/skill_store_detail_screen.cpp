#include "presentation/screens/skill_hub_screens.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <format>
#include <functional>
#include <optional>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/app.h>
#include <huxerui/huxerui.h>

#include "application/ports/external_link.h"
#include "domain/app_state.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/components/skill_hub_components.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/line_theme.h"
#include "presentation/markdown_link_policy.h"
#include "presentation/skill_hub_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class DetailTab : std::uint8_t {
  overview,
  files,
  comments,
  versions,
  evaluation,
  preview,
};

enum class DetailPhase : std::uint8_t { loading, ready, failed };

struct DetailState final {
  std::optional<domain::SkillHubDetail> detail;
  std::optional<bool> starred;
  std::string error;
  DetailTab tab{DetailTab::overview};
  DetailPhase phase{DetailPhase::loading};
  bool star_busy{};
  bool installed{};
};

struct TabPresentation final {
  DetailTab tab;
  StringResource label;
};

const std::array kTabs{
    TabPresentation{DetailTab::overview, app::strings::skillhub_tab_overview},
    TabPresentation{DetailTab::files, app::strings::skillhub_tab_files},
    TabPresentation{DetailTab::comments, app::strings::skillhub_tab_comments},
    TabPresentation{DetailTab::versions, app::strings::skillhub_tab_versions},
    TabPresentation{DetailTab::evaluation,
                    app::strings::skillhub_tab_evaluation},
    TabPresentation{DetailTab::preview, app::strings::skillhub_tab_preview},
};

struct FileIconPresentation final {
  SkillHubFileKind kind;
  ImageResource icon;
};

const std::array kFileIcons{
    FileIconPresentation{SkillHubFileKind::text, app::images::file_text},
    FileIconPresentation{SkillHubFileKind::code, app::images::file_code},
    FileIconPresentation{SkillHubFileKind::other, app::images::file},
};

const std::array kDateMonths{
    app::strings::skillhub_date_01, app::strings::skillhub_date_02,
    app::strings::skillhub_date_03, app::strings::skillhub_date_04,
    app::strings::skillhub_date_05, app::strings::skillhub_date_06,
    app::strings::skillhub_date_07, app::strings::skillhub_date_08,
    app::strings::skillhub_date_09, app::strings::skillhub_date_10,
    app::strings::skillhub_date_11, app::strings::skillhub_date_12,
};

ImageResource FileIcon(std::string_view path) {
  const auto kind = SkillHubFileKindForPath(path);
  return std::ranges::find(kFileIcons, kind, &FileIconPresentation::kind)->icon;
}

StringVariant Count(const std::int64_t value) {
  if (value >= 10'000)
    return StringVariant::Format(
        app::strings::skillhub_count_wan,
        std::format("{:.1f}", static_cast<double>(value) / 10'000.0));
  return std::to_string(value);
}

std::string Bytes(const std::int64_t value) {
  if (value >= 1024 * 1024)
    return std::format("{:.1f} MB", static_cast<double>(value) / 1048576.0);
  if (value >= 1024)
    return std::format("{:.1f} KB", static_cast<double>(value) / 1024.0);
  return std::to_string(value) + " B";
}

std::string Join(const std::vector<std::string> &values) {
  std::string result;
  for (const auto &value : values) {
    if (!result.empty())
      result += " · ";
    result += value;
  }
  return result;
}

StringVariant FormatDate(const std::int64_t value) {
  if (value <= 0)
    return app::strings::skillhub_dash;
  const auto instant = std::chrono::sys_time<std::chrono::milliseconds>{
      std::chrono::milliseconds{value}};
  const std::chrono::year_month_day date{
      std::chrono::floor<std::chrono::days>(instant)};
  const unsigned month = static_cast<unsigned>(date.month());
  if (!date.ok() || month == 0 || month > kDateMonths.size())
    return app::strings::skillhub_dash;
  return StringVariant::Format(kDateMonths[month - 1],
                               static_cast<unsigned>(date.day()),
                               static_cast<int>(date.year()));
}

Task<void> LoadReadingScale(
    const std::shared_ptr<application::SkillHubReadingSettings> settings,
    const State<float> scale, const std::optional<float> legacy_scale) {
  if (settings)
    scale = co_await settings->LoadScale(legacy_scale);
}

void RequestReadingScale(const SkillHubScreenServices &services,
                         const State<float> scale, const TaskScope &tasks) {
  const auto load = [settings = services.reading, scale, tasks](
                        const std::optional<float> legacy_scale) {
    tasks.Launch(LoadReadingScale(settings, scale, legacy_scale));
  };
  if (!services.platform) {
    load(std::nullopt);
    return;
  }
  services.platform->ReadLegacyMarkdownTextScale(load);
}

void PersistReadingScale(
    const std::shared_ptr<application::SkillHubReadingSettings> &settings,
    const TaskScope &tasks, const State<float> &scale) {
  if (settings)
    tasks.Launch(settings->SaveScale(scale.Get()));
}

View MarkdownDocument(
    const std::string_view source, const State<float> scale,
    const TaskScope tasks,
    std::shared_ptr<application::SkillHubReadingSettings> settings,
    TutorialMarkdownLinkHandler on_link) {
  const infrastructure::TutorialMarkdownParser parser;
  return TutorialMarkdownDocumentView(parser.Parse(source), true, scale.Get(),
                                      std::move(on_link))
      .On<TransformEvents::Changed>([scale](const TransformEvent &event) {
        scale = application::SkillHubReadingSettings::NormalizeScale(
            scale.Get() * event.scale);
      })
      .On<TransformEvents::Ended>(
          [settings, tasks, scale](const TransformEvent &) {
            PersistReadingScale(settings, tasks, scale);
          })
      .On<TransformEvents::Canceled>(
          [settings, tasks, scale](const TransformEvent &) {
            PersistReadingScale(settings, tasks, scale);
          })
      .With(TransformGesture{});
}

// The legacy preview tab renders expected answers at the normal document
// scale. Only the main README and file-preview readers opt into pinch zoom.
View StaticMarkdownDocument(const std::string_view source) {
  const infrastructure::TutorialMarkdownParser parser;
  return TutorialMarkdownDocumentView(parser.Parse(source), true);
}

void ReadSessionCookie(
    const SkillHubScreenServices &services,
    application::SkillHubPlatformService::CookieCompletion completion) {
  if (!services.platform) {
    completion({.error = "SkillHub platform service is unavailable"});
    return;
  }
  services.platform->ReadSessionCookie(std::move(completion));
}

Task<void> LoadStarred(SkillHubScreenServices services,
                       State<DetailState> state, std::string cookie);

Task<void> ReloadDetail(const SkillHubScreenServices services,
                        const std::string slug, const State<DetailState> state,
                        std::string cookie) {
  state.Update([](DetailState &next) {
    next.phase = DetailPhase::loading;
    next.error.clear();
    next.detail.reset();
    next.starred.reset();
    next.star_busy = false;
    next.installed = false;
  });
  auto loaded = co_await services.catalog->Detail(slug);
  if (!loaded) {
    state.Update([message = std::move(loaded.error().message)](
                     DetailState &next) mutable {
      next.phase = DetailPhase::failed;
      next.error = std::move(message);
    });
    co_return;
  }
  state.Update([detail = std::move(*loaded)](DetailState &next) mutable {
    next.phase = DetailPhase::ready;
    next.detail = std::move(detail);
    next.star_busy = true;
  });
  co_await LoadStarred(services, state, std::move(cookie));
  state.Update([&](DetailState &next) {
    if (next.detail && next.detail->slug == slug)
      next.star_busy = false;
  });
}

void SelectTab(const State<DetailState> state, const DetailTab tab) {
  state.Update([tab](DetailState &next) { next.tab = tab; });
}

Task<void> LoadStarred(const SkillHubScreenServices services,
                       const State<DetailState> state, std::string cookie) {
  if (!state->detail)
    co_return;
  const std::string slug = state->detail->slug;
  const std::string name_space = state->detail->namespace_handle;
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session || !session->authenticated) {
    state.Update([&](DetailState &next) {
      if (next.detail && next.detail->slug == slug)
        next.starred = false;
    });
    co_return;
  }
  auto result = co_await services.session->Starred(cookie, slug, name_space);
  state.Update([&](DetailState &next) {
    if (next.detail && next.detail->slug == slug) {
      next.starred =
          result ? std::optional<bool>{*result} : std::optional<bool>{false};
    }
  });
}

Task<void>
ToggleStar(const SkillHubScreenServices services,
           const State<DetailState> state,
           const RouteNavigationController<domain::AppRoute> navigation,
           const ToastHandle toast, std::string cookie) {
  if (!state->detail || state->star_busy)
    co_return;
  state.Update([](DetailState &next) { next.star_busy = true; });
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session || !session->authenticated) {
    state.Update([](DetailState &next) { next.star_busy = false; });
    toast.Show(app::strings::skillhub_login_to_star);
    navigation.Push(domain::AppRoute::skill_hub_login);
    co_return;
  }
  const bool next_value = !state->starred.value_or(false);
  auto result = co_await services.session->SetStarred(
      cookie, state->detail->slug, state->detail->namespace_handle, next_value);
  state.Update([&](DetailState &next) {
    next.star_busy = false;
    if (result)
      next.starred = next_value;
    else
      next.error = result.error().message;
  });
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  toast.Show(next_value ? StringVariant{app::strings::skillhub_star_success}
                        : StringVariant{app::strings::skillhub_unstar_success});
}

View DetailAction(ImageResource icon, StringVariant label,
                  std::function<void()> action, const bool enabled = true) {
  return Row{
      SkillHubIconSlot(icon, 16.0F, 20.0F, colors::accent),
      Text(std::move(label))
          .Style(SkillHubLabel(13.0F, FontWeight::Medium))
          .Align(TextAlign::Center)
          .With(Grow()),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 40.0F}, Padding(8.0F), Spacing(4.0F), Grow(),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(10.0F), Enabled(enabled), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View MetadataCard(StringVariant label, std::string value) {
  if (value.empty())
    value = "—";
  return Column{
      Text(std::move(label))
          .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
      Text(std::move(value)).Style(SkillHubLabel(13.0F, FontWeight::Medium)),
  }
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Spacing(3.0F), Grow(),
            Background(colors::surface_light), CornerRadius(8.0F));
}

[[huxerui::composable]] View MetadataRow(StringVariant label,
                                         const std::string &value) {
  return Text(UseString(std::move(label)) + "  ·  " + value)
      .Style(SkillHubLabel(13.0F, FontWeight::Regular, colors::secondary))
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
            Background(colors::surface_light), CornerRadius(8.0F));
}

[[huxerui::composable]] View
Hero(const domain::SkillHubDetail &detail,
     const std::shared_ptr<application::SkillHubCatalog> &catalog) {
  auto icon = UseState(std::optional<ImageAsset>{});
  const auto tasks = UseTaskScope();
  if (!detail.icon_url.empty()) {
    Lifecycle(
        [tasks, catalog, url = detail.icon_url, icon] {
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
        detail.icon_url);
  }

  View artwork =
      icon->has_value()
          ? Image(icon.Get().value())
                .Fit(ImageFit::Cover)
                .With(Frame{.width = 64.0F, .height = 64.0F},
                      CornerRadius(14.0F), ClipChildren())
          : Stack{SkillHubGlyph(app::images::package, 32.0F, colors::accent)}
                .With(Frame{.width = 64.0F, .height = 64.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Background(colors::accent_muted), CornerRadius(14.0F));
  std::vector<View> title{
      Text(detail.name)
          .Style(SkillHubLabel(20.0F, FontWeight::Medium))
          .With(Grow()),
  };
  if (detail.verified)
    title.push_back(SkillHubTag(app::strings::skillhub_verified, colors::accent,
                                colors::accent_muted));
  std::string identity =
      detail.canonical_name.empty() ? detail.owner : detail.canonical_name;
  if (!detail.publisher.empty())
    identity += (identity.empty() ? "" : " · ") + detail.publisher;
  std::vector<View> copy{
      Row(std::move(title))
          .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Center)),
  };
  if (!identity.empty())
    copy.push_back(Text(identity).Style(
        SkillHubLabel(13.0F, FontWeight::Regular, colors::tertiary)));

  std::vector<View> badges{
      SkillHubTag("↓ " + UseString(Count(detail.downloads)), colors::secondary,
                  colors::surface_light),
      SkillHubTag("★ " + UseString(Count(detail.stars)), colors::secondary,
                  colors::surface_light),
      SkillHubTag("v" + detail.version, colors::secondary,
                  colors::surface_light),
  };
  if (detail.security_status == "benign")
    badges.push_back(SkillHubTag(app::strings::skillhub_safe, colors::accent,
                                 colors::surface_light));

  return Column{
      Row{
          std::move(artwork),
          Column(std::move(copy)).With(Spacing(4.0F), Grow()),
      }
          .With(Spacing(16.0F), CrossAlign(CrossAxisAlignment::Center)),
      Text(detail.description)
          .Style(SkillHubLabel(16.0F, FontWeight::Regular, colors::secondary)),
      Row(std::move(badges)).With(Spacing(4.0F)),
  }
      .With(Padding(16.0F), Spacing(12.0F), Background(colors::elevated),
            Border{.color = colors::border, .width = 1.0F}, CornerRadius(14.0F),
            CrossAlign(CrossAxisAlignment::Stretch));
}

[[huxerui::composable]] View
Overview(const domain::SkillHubDetail &detail, const State<float> reading_scale,
         const TaskScope tasks,
         std::shared_ptr<application::SkillHubReadingSettings> reading,
         const TutorialMarkdownLinkHandler &on_link) {
  std::vector<View> info{
      Row{
          MetadataCard(app::strings::skillhub_category, detail.category),
          MetadataCard(app::strings::skillhub_source,
                       detail.source == "community"
                           ? UseString(app::strings::skillhub_source_community)
                           : detail.source),
      }
          .With(Spacing(8.0F)),
      Row{
          MetadataCard(app::strings::skillhub_tab_versions,
                       "v" + detail.version),
          MetadataCard(app::strings::skillhub_updated,
                       UseString(FormatDate(detail.updated_at))),
      }
          .With(Spacing(8.0F)),
  };
  if (!detail.subcategories.empty())
    info.push_back(MetadataRow(app::strings::skillhub_subcategories,
                               Join(detail.subcategories)));
  if (!detail.tags.empty())
    info.push_back(MetadataRow(app::strings::skillhub_tags, Join(detail.tags)));

  const bool unsafe =
      detail.HasScripts() ||
      (!detail.security_status.empty() && detail.security_status != "benign");
  std::string safety = detail.security_status_text;
  if (safety.empty())
    safety = UseString(unsafe ? app::strings::skillhub_check_before_use
                              : app::strings::skillhub_no_obvious_risk);
  if (detail.HasScripts())
    safety += "\n" + UseString(app::strings::skillhub_contains_scripts);
  if (detail.requires_api_key)
    safety += "\n" + UseString(app::strings::skillhub_requires_api_key_warning);

  View markdown =
      detail.markdown.empty()
          ? Text(app::strings::skillhub_no_public_doc)
                .Style(
                    SkillHubLabel(13.0F, FontWeight::Regular, colors::tertiary))
          : MarkdownDocument(detail.markdown, reading_scale, tasks,
                             std::move(reading), on_link)
                .With(Padding(12.0F), Background(colors::surface_light),
                      CornerRadius(10.0F));

  return Column{
      SkillHubSection(app::strings::skillhub_skill_info, app::images::boxes,
                      std::move(info)),
      SkillHubSection(
          app::strings::skillhub_safety_check,
          unsafe ? app::images::circle_alert : app::images::shield_check,
          {Text(safety).Style(SkillHubLabel(
              13.0F, FontWeight::Regular,
              unsafe ? Color(colors::warning) : Color(colors::secondary)))}),
      SkillHubSection(app::strings::skillhub_skill_md, app::images::file_text,
                      {Text(app::strings::skillhub_pinch_to_zoom)
                           .Style(SkillHubLabel(11.0F, FontWeight::Regular,
                                                colors::tertiary)),
                       markdown}),
  }
      .With(Spacing(12.0F), Padding(EdgeInsets{.top = 12.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

void ShowFileDialog(const DialogHandle dialogs, const std::string &path,
                    const std::string &content,
                    const std::shared_ptr<Clipboard> &clipboard,
                    const ToastHandle toast, const State<float> reading_scale,
                    const TaskScope tasks,
                    std::shared_ptr<application::SkillHubReadingSettings>
                        reading,
                    TutorialMarkdownLinkHandler on_link) {
  dialogs.Show([path, content, clipboard, toast, reading_scale, tasks,
                reading = std::move(reading),
                on_link = std::move(on_link)](DialogContext dialog) {
    const bool markdown = IsSkillHubMarkdownPath(path);
    View preview = markdown ? MarkdownDocument(content, reading_scale, tasks,
                                                reading, on_link)
                            : SelectionArea(Text(content).Style(TextStyle{
                                  Font::Monospace(13.0F), colors::secondary}));
    std::vector<View> dialog_content{
        Text(path).Style(SkillHubLabel(17.0F, FontWeight::Medium)),
    };
    if (markdown) {
      dialog_content.push_back(
          Text(app::strings::skillhub_pinch_to_zoom)
              .Style(
                  SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)));
    }
    dialog_content.push_back(ScrollView(std::move(preview))
                                 .ScrollAxis(Axis::Vertical)
                                 .With(Frame{.max_height = 520.0F},
                                       Padding(EdgeInsets{.top = 12.0F})));
    dialog_content.push_back(Row{
        SkillHubDialogButton(app::strings::skillhub_close, false,
                             [dialog] { dialog.Dismiss(); }),
        SkillHubDialogButton(app::strings::skillhub_copy, true,
                             [clipboard, toast, content] {
                               if (clipboard && clipboard->WriteText(content))
                                 toast.Show(app::strings::skillhub_file_copied);
                             }),
    }
                                 .With(Spacing(8.0F),
                                       Padding(EdgeInsets{.top = 16.0F})));
    return SkillHubDialogPanel(std::move(dialog_content));
  });
}

Task<void> LoadFile(const SkillHubScreenServices services,
                    const domain::SkillHubDetail detail,
                    const domain::SkillHubFileEntry file,
                    const DialogHandle dialogs,
                    const std::shared_ptr<Clipboard> clipboard,
                    const ToastHandle toast, const State<float> reading_scale,
                    const TaskScope tasks,
                    TutorialMarkdownLinkHandler on_link) {
  if (!IsSkillHubTextPreviewable(file.path)) {
    toast.Show(app::strings::skillhub_file_not_supported);
    co_return;
  }
  toast.Show(app::strings::skillhub_loading_file);
  auto loaded = co_await services.catalog->FileContent(
      detail.slug, detail.version, file.path);
  if (!loaded) {
    toast.Show(loaded.error().message);
    co_return;
  }
  ShowFileDialog(dialogs, file.path, *loaded, clipboard, toast, reading_scale,
                 tasks, services.reading, std::move(on_link));
}

[[huxerui::composable]] View Files(const SkillHubScreenServices &services,
                                   const domain::SkillHubDetail &detail,
                                   const TaskScope &tasks,
                                   const DialogHandle &dialogs,
                                   const std::shared_ptr<Clipboard> &clipboard,
                                   const ToastHandle &toast,
                                   const State<float> reading_scale,
                                   const TutorialMarkdownLinkHandler &on_link) {
  std::vector<View> rows{
      Text(app::strings::skillhub_click_to_preview)
          .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
  };
  if (detail.files.empty()) {
    rows.push_back(Text(app::strings::skillhub_no_public_files)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::tertiary)));
  } else {
    for (const auto &file : detail.files) {
      const auto slash = file.path.rfind('/');
      const std::string name =
          slash == std::string::npos ? file.path : file.path.substr(slash + 1);
      const std::string directory =
          slash == std::string::npos
              ? UseString(app::strings::skillhub_root_dir)
              : file.path.substr(0, slash);
      rows.push_back(Row{
          Stack{SkillHubGlyph(FileIcon(file.path), 18.0F, colors::accent)}.With(
              Frame{.width = 32.0F, .height = 32.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
          Column{
              Text(name).Style(SkillHubLabel(13.0F, FontWeight::Medium)),
              Text(directory + " · " + Bytes(file.size))
                  .Style(SkillHubLabel(11.0F, FontWeight::Regular,
                                       colors::tertiary)),
          }
              .With(Spacing(2.0F), Padding(EdgeInsets{.left = 8.0F}), Grow()),
          SkillHubIconSlot(app::images::chevron_right, 16.0F, 24.0F,
                           colors::tertiary),
      }
                         .OnClick([services, detail, file, tasks, dialogs,
                                   clipboard, toast, reading_scale, on_link] {
                           tasks.Launch(LoadFile(services, detail, file,
                                                 dialogs, clipboard, toast,
                                                 reading_scale, tasks,
                                                 on_link));
                         })
                         .With(Padding(EdgeInsets{.top = 8.0F,
                                                  .right = 8.0F,
                                                  .bottom = 8.0F,
                                                  .left = 12.0F}),
                               CrossAlign(CrossAxisAlignment::Center),
                               Background(colors::surface_light),
                               CornerRadius(9.0F), Focusable(),
                               PointerCursor(PointerCursorKind::Hand)));
    }
  }
  return SkillHubTopMargin(
      SkillHubSection(StringVariant::Format(app::strings::skillhub_file_list,
                                            detail.files.size()),
                      app::images::file_text, std::move(rows)),
      12.0F);
}

Task<void> RefreshAfterComment(const SkillHubScreenServices services,
                               const State<DetailState> state) {
  if (!state->detail)
    co_return;
  const std::string slug = state->detail->slug;
  auto loaded = co_await services.catalog->Detail(slug);
  if (!loaded)
    co_return;
  state.Update([detail = std::move(*loaded)](DetailState &next) mutable {
    next.detail = std::move(detail);
    next.tab = DetailTab::comments;
  });
}

Task<void> SubmitComment(const SkillHubScreenServices services,
                         const State<DetailState> state,
                         const std::optional<std::int64_t> parent,
                         const State<TextEditingValue> content,
                         const State<bool> busy, const DialogContext dialog,
                         const ToastHandle toast, std::string cookie) {
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session || !session->authenticated) {
    busy = false;
    toast.Show(app::strings::skillhub_login_to_star);
    co_return;
  }
  auto posted = co_await services.session->PostComment(
      cookie, state->detail->slug, state->detail->namespace_handle,
      content->text, parent);
  busy = false;
  if (!posted) {
    toast.Show(posted.error().message);
    co_return;
  }
  dialog.Dismiss();
  toast.Show(app::strings::skillhub_comment_submitted);
  co_await RefreshAfterComment(services, state);
}

[[huxerui::composable]] View
CommentDialog(const DialogContext dialog, const SkillHubScreenServices services,
              const State<DetailState> state,
              const std::optional<domain::SkillHubComment> parent,
              const ToastHandle toast) {
  const auto tasks = UseTaskScope();
  auto content = UseState(TextEditingValue::FromText(""));
  auto busy = UseState(false);
  const StringVariant title =
      parent ? StringVariant::Format(app::strings::skillhub_reply_to,
                                     parent->author)
             : StringVariant{app::strings::skillhub_post_comment};
  return SkillHubDialogPanel({
      Text(title).Style(SkillHubLabel(17.0F, FontWeight::Medium)),
      Text(app::strings::skillhub_comment_review_notice)
          .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
      TextField(content.Get())
          .Placeholder(parent ? app::strings::skillhub_write_reply_hint
                              : app::strings::skillhub_share_experience_hint)
          .LineLimits(TextFieldLineLimits::MultiLine(4, 8))
          .MaxLength(500)
          .OnChanged(
              [content](TextEditingValue next) { content = std::move(next); })
          .With(Frame{.min_height = 116.0F}, Padding(12.0F),
                Background(colors::input),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(10.0F), Enabled(!busy.Get())),
      Row{
          SkillHubDialogButton(
              app::strings::skillhub_cancel, false,
              [dialog] { dialog.Dismiss(); }, !busy.Get()),
          SkillHubDialogButton(
              busy.Get() ? StringVariant{app::strings::skillhub_submitting}
                         : StringVariant{app::strings::skillhub_submit_comment},
              true,
              [services, state, parent, content, busy, dialog, tasks, toast] {
                if (content->text.empty() || busy.Get())
                  return;
                busy = true;
                services.platform->ReadSessionCookie(
                    [services, state, parent, content, busy, dialog, tasks,
                     toast](application::SkillHubCookieResult result) {
                      if (!result.Succeeded()) {
                        busy = false;
                        toast.Show(result.error);
                        return;
                      }
                      tasks.Launch(SubmitComment(
                          services, state,
                          parent ? std::optional<std::int64_t>{parent->id}
                                 : std::nullopt,
                          content, busy, dialog, toast,
                          std::move(result.cookie)));
                    });
              },
              !busy.Get() && !content->text.empty()),
      }
          .With(Spacing(8.0F)),
  });
}

void ShowCommentDialog(const DialogHandle dialogs,
                       const SkillHubScreenServices &services,
                       const State<DetailState> state,
                       std::optional<domain::SkillHubComment> parent,
                       const ToastHandle toast) {
  dialogs.Show(CommentDialog, services, state, std::move(parent), toast);
}

Task<void>
OpenCommentDialog(const SkillHubScreenServices services,
                  const State<DetailState> state,
                  const std::optional<domain::SkillHubComment> parent,
                  const DialogHandle dialogs,
                  const RouteNavigationController<domain::AppRoute> navigation,
                  const ToastHandle toast, std::string cookie) {
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session) {
    toast.Show(session.error().message);
    co_return;
  }
  if (!session->authenticated) {
    navigation.Push(domain::AppRoute::skill_hub_login);
    co_return;
  }
  ShowCommentDialog(dialogs, services, state, parent, toast);
}

Task<void>
SetLiked(const SkillHubScreenServices services, const State<DetailState> state,
         const domain::SkillHubComment comment,
         const RouteNavigationController<domain::AppRoute> navigation,
         const ToastHandle toast, std::string cookie) {
  auto session = co_await services.session->CurrentSession(cookie);
  if (!session) {
    toast.Show(session.error().message);
    co_return;
  }
  if (!session->authenticated) {
    navigation.Push(domain::AppRoute::skill_hub_login);
    co_return;
  }
  auto result = co_await services.session->SetCommentLiked(
      cookie, state->detail->slug, comment.id, state->detail->namespace_handle,
      !comment.liked);
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  co_await RefreshAfterComment(services, state);
}

Task<void> DeleteComment(const SkillHubScreenServices services,
                         const State<DetailState> state,
                         const domain::SkillHubComment comment,
                         const ToastHandle toast, std::string cookie) {
  auto result = co_await services.session->DeleteComment(
      cookie, state->detail->slug, comment.id, state->detail->namespace_handle);
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  co_await RefreshAfterComment(services, state);
}

domain::SkillHubComment *
FindComment(std::vector<domain::SkillHubComment> &comments,
            const std::int64_t comment_id) {
  for (auto &comment : comments) {
    if (comment.id == comment_id)
      return &comment;
    if (auto *found = FindComment(comment.replies, comment_id))
      return found;
  }
  return nullptr;
}

Task<void> LoadCommentReplies(const SkillHubScreenServices services,
                              const State<DetailState> state,
                              const domain::SkillHubComment comment,
                              const ToastHandle toast) {
  if (!state->detail)
    co_return;
  auto replies = co_await services.catalog->CommentReplies(
      state->detail->slug, comment.id, state->detail->namespace_handle);
  if (!replies) {
    toast.Show(replies.error().message);
    co_return;
  }
  state.Update([comment_id = comment.id,
                replies = std::move(*replies)](DetailState &next) mutable {
    if (next.detail) {
      if (auto *target = FindComment(next.detail->comments, comment_id))
        target->replies = std::move(replies);
    }
  });
}

[[huxerui::composable]] View
CommentCard(const SkillHubScreenServices &services,
            const State<DetailState> state,
            const domain::SkillHubComment &comment, const int depth,
            const TaskScope &tasks, const DialogHandle &dialogs,
            const RouteNavigationController<domain::AppRoute> &navigation,
            const ToastHandle &toast) {
  const std::string author = comment.author.empty()
                                 ? UseString(app::strings::skillhub_user)
                                 : comment.author;
  std::vector<View> actions{
      Text((comment.liked ? UseString(app::strings::skillhub_unlike)
                          : UseString(app::strings::skillhub_like)) +
           (comment.like_count > 0 ? " " + std::to_string(comment.like_count)
                                   : ""))
          .Style(SkillHubLabel(11.0F, FontWeight::Medium, colors::accent))
          .OnClick([services, state, comment, navigation, tasks, toast] {
            ReadSessionCookie(
                services, [services, state, comment, navigation, tasks,
                           toast](application::SkillHubCookieResult result) {
                  if (!result.Succeeded()) {
                    toast.Show(result.error);
                    return;
                  }
                  tasks.Launch(SetLiked(services, state, comment, navigation,
                                        toast, std::move(result.cookie)));
                });
          })
          .With(
              Padding(EdgeInsets{.top = 4.0F, .right = 12.0F, .bottom = 4.0F}),
              Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Text(app::strings::skillhub_reply)
          .Style(SkillHubLabel(11.0F, FontWeight::Medium, colors::accent))
          .OnClick([dialogs, services, state, comment, navigation, tasks,
                    toast] {
            ReadSessionCookie(
                services, [dialogs, services, state, comment, navigation, tasks,
                           toast](application::SkillHubCookieResult result) {
                  if (!result.Succeeded()) {
                    toast.Show(result.error);
                    return;
                  }
                  tasks.Launch(OpenCommentDialog(services, state, comment,
                                                 dialogs, navigation, toast,
                                                 std::move(result.cookie)));
                });
          })
          .With(
              Padding(EdgeInsets{.top = 4.0F, .right = 12.0F, .bottom = 4.0F}),
              Focusable(), PointerCursor(PointerCursorKind::Hand)),
  };
  if (comment.reply_count > static_cast<std::int64_t>(comment.replies.size())) {
    actions.push_back(
        Text(StringVariant::Format(app::strings::skillhub_all_replies,
                                   comment.reply_count))
            .Style(SkillHubLabel(11.0F, FontWeight::Medium, colors::accent))
            .OnClick([services, state, comment, tasks, toast] {
              tasks.Launch(LoadCommentReplies(services, state, comment, toast));
            })
            .With(Padding(
                      EdgeInsets{.top = 4.0F, .right = 12.0F, .bottom = 4.0F}),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)));
  }
  actions.push_back(
      Text(app::strings::skillhub_delete)
          .Style(SkillHubLabel(11.0F, FontWeight::Medium, colors::danger))
          .OnClick([services, state, comment, tasks, toast] {
            ReadSessionCookie(
                services, [services, state, comment, tasks,
                           toast](application::SkillHubCookieResult result) {
                  if (!result.Succeeded()) {
                    toast.Show(result.error);
                    return;
                  }
                  tasks.Launch(DeleteComment(services, state, comment, toast,
                                             std::move(result.cookie)));
                });
          })
          .With(Padding(4.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)));
  std::vector<View> children{
      Text(author + " · " + UseString(FormatDate(comment.created_at)))
          .Style(SkillHubLabel(11.0F, FontWeight::Medium, colors::tertiary)),
      Text(comment.content)
          .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                               depth == 0 ? Color(colors::secondary)
                                          : Color(colors::tertiary))),
      Row(std::move(actions)).With(Padding(EdgeInsets{.top = 4.0F})),
  };
  for (const auto &reply : comment.replies)
    children.push_back(CommentCard(services, state, reply, depth + 1, tasks,
                                   dialogs, navigation, toast));
  return Column(std::move(children))
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Spacing(4.0F),
            Background(colors::surface_light), CornerRadius(8.0F),
            Offset(Point{static_cast<float>(depth * 16), 0.0F}));
}

[[huxerui::composable]] View
Comments(const SkillHubScreenServices &services, const State<DetailState> state,
         const domain::SkillHubDetail &detail, const TaskScope &tasks,
         const DialogHandle &dialogs, const ToastHandle &toast) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> rows{
      Stack{Text(app::strings::skillhub_post_comment)
                .Style(SkillHubLabel(13.0F, FontWeight::Medium,
                                     colors::text_on_color))
                .Align(TextAlign::Center)}
          .OnClick([dialogs, services, state, navigation, tasks, toast] {
            ReadSessionCookie(
                services, [dialogs, services, state, navigation, tasks,
                           toast](application::SkillHubCookieResult result) {
                  if (!result.Succeeded()) {
                    toast.Show(result.error);
                    return;
                  }
                  tasks.Launch(OpenCommentDialog(services, state, std::nullopt,
                                                 dialogs, navigation, toast,
                                                 std::move(result.cookie)));
                });
          })
          .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                Background(colors::accent), CornerRadius(9.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
  };
  if (detail.comments.empty()) {
    rows.push_back(Text(app::strings::skillhub_no_comments_login_first)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::tertiary)));
  } else {
    for (const auto &comment : detail.comments)
      rows.push_back(CommentCard(services, state, comment, 0, tasks, dialogs,
                                 navigation, toast));
  }
  return SkillHubTopMargin(
      SkillHubSection(app::strings::skillhub_community_comments,
                      app::images::message_circle, std::move(rows)),
      12.0F);
}

[[huxerui::composable]] View Versions(const domain::SkillHubDetail &detail) {
  std::vector<View> rows;
  if (detail.versions.empty()) {
    rows.push_back(Text(app::strings::skillhub_no_version_history)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::tertiary)));
  } else {
    for (const auto &version : detail.versions) {
      std::string text = "v" + version.version + " · " +
                         UseString(FormatDate(version.created_at));
      if (!version.security_status_text.empty())
        text += "\n" + version.security_status_text;
      if (!version.changelog.empty())
        text += "\n" + version.changelog;
      rows.push_back(Text(text).Style(
          SkillHubLabel(13.0F, FontWeight::Regular, colors::secondary)));
    }
  }
  return SkillHubTopMargin(
      SkillHubSection(app::strings::skillhub_version_history,
                      app::images::clock_3, std::move(rows)),
      12.0F);
}

View Evaluation(const domain::SkillHubDetail &detail) {
  std::vector<View> rows;
  if (detail.evaluation.status.empty()) {
    rows.push_back(Text(app::strings::skillhub_no_evaluation)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::tertiary)));
  } else {
    if (detail.evaluation.score > 0.0)
      rows.push_back(
          Text(StringVariant::Format(
                   app::strings::skillhub_overall_score,
                   std::format("{:.1f}", detail.evaluation.score)))
              .Style(SkillHubLabel(20.0F, FontWeight::Medium, colors::accent)));
    rows.push_back(Text(detail.evaluation.summary)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::secondary)));
    for (const auto &highlight :
         detail.evaluation.highlights | std::views::take(5))
      rows.push_back(Text("• " + highlight)
                         .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                              colors::tertiary)));
  }
  return SkillHubTopMargin(
      SkillHubSection(app::strings::skillhub_evaluation_report,
                      app::images::flask_conical, std::move(rows)),
      12.0F);
}

[[huxerui::composable]] View Preview(const domain::SkillHubDetail &detail) {
  std::vector<View> rows;
  if (detail.test_cases.empty()) {
    rows.push_back(Text(app::strings::skillhub_no_preview)
                       .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                            colors::tertiary)));
  } else {
    for (const auto &test : detail.test_cases) {
      rows.push_back(
          Text(test.title + "\n" +
               UseString(app::strings::skillhub_user_colon) + test.prompt)
              .Style(SkillHubLabel(13.0F, FontWeight::Medium))
              .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                    Background(colors::accent_muted), CornerRadius(8.0F)));
      rows.push_back(StaticMarkdownDocument(test.expected));
    }
  }
  return SkillHubTopMargin(SkillHubSection(app::strings::skillhub_tab_preview,
                                           app::images::play, std::move(rows)),
                           12.0F);
}

Task<void> Install(const SkillHubScreenServices services,
                   const State<DetailState> state,
                   const State<domain::SkillLocation> location,
                   const State<bool> busy, const DialogContext dialog,
                   const ToastHandle toast) {
  auto result = co_await services.management->InstallSkillHub(
      services.roots, location.Get(), state->detail->slug,
      state->detail->version);
  busy = false;
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  state.Update([](DetailState &next) { next.installed = true; });
  dialog.Dismiss();
  toast.Show(app::strings::skillhub_install_success);
}

View InstallLocation(domain::SkillLocation value, StringResource title,
                     StringResource description,
                     State<domain::SkillLocation> selected) {
  const bool active = selected.Get() == value;
  const ImageResource icon = value == domain::SkillLocation::app
                                 ? app::images::smartphone
                                 : app::images::folder;
  return Row{
      SkillHubGlyph(icon, 19.0F, colors::accent),
      Column{
          Text(title).Style(SkillHubLabel(13.0F, FontWeight::Medium)),
          Text(description)
              .Style(
                  SkillHubLabel(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Text(active ? "✓" : "")
          .Style(SkillHubLabel(17.0F, FontWeight::Medium, colors::accent)),
  }
      .OnClick([selected, value] { selected = value; })
      .With(Padding(12.0F), Spacing(8.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? Color(colors::accent_muted)
                              : Color(colors::surface_light)),
            Border{.color = active ? Color(colors::accent)
                                   : Color(colors::border_light),
                   .width = 1.0F},
            CornerRadius(10.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

[[huxerui::composable]] View
InstallDialog(const DialogContext dialog, const SkillHubScreenServices services,
              const State<DetailState> state, const ToastHandle toast) {
  const auto tasks = UseTaskScope();
  auto location = UseState(domain::SkillLocation::app);
  auto busy = UseState(false);
  const auto &detail = *state->detail;
  std::vector<View> content{
      Row{
          Stack{SkillHubGlyph(app::images::package, 22.0F, colors::accent)}
              .With(
                  Frame{.width = 42.0F, .height = 42.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(colors::accent_muted), CornerRadius(10.0F)),
          Column{
              Text(StringVariant::Format(app::strings::skillhub_install_skill,
                                         detail.name))
                  .Style(SkillHubLabel(17.0F, FontWeight::Medium)),
              Text(app::strings::skillhub_install_scope_desc)
                  .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                       colors::tertiary)),
          }
              .With(Spacing(3.0F), Grow()),
      }
          .With(Spacing(12.0F), CrossAlign(CrossAxisAlignment::Center)),
      Row{
          SkillHubTag("SkillHub", colors::secondary, colors::surface_light),
          SkillHubTag("v" + detail.version, colors::secondary,
                      colors::surface_light),
          SkillHubTag(StringVariant::Format(app::strings::skillhub_file_count,
                                            detail.files.size()),
                      colors::secondary, colors::surface_light),
      }
          .With(Spacing(4.0F)),
      Text(app::strings::skillhub_install_location)
          .Style(SkillHubLabel(13.0F, FontWeight::Medium, colors::secondary)),
      InstallLocation(domain::SkillLocation::app,
                      app::strings::skillhub_location_app_title,
                      app::strings::skillhub_location_app_desc, location),
      InstallLocation(domain::SkillLocation::project,
                      app::strings::skillhub_location_project_title,
                      app::strings::skillhub_location_project_desc, location),
  };
  if (detail.HasScripts() || detail.requires_api_key) {
    std::string warning =
        UseString(detail.HasScripts()
                      ? app::strings::skillhub_contains_scripts_confirm
                      : app::strings::skillhub_may_require_api_key_confirm);
    if (detail.HasScripts() && detail.requires_api_key)
      warning +=
          "\n" + UseString(app::strings::skillhub_may_also_require_api_key);
    content.push_back(Row{
        SkillHubGlyph(app::images::circle_alert, 16.0F, colors::warning),
        Text(warning)
            .Style(SkillHubLabel(11.0F, FontWeight::Regular, colors::warning))
            .With(Grow()),
    }
                          .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                                Spacing(8.0F),
                                CrossAlign(CrossAxisAlignment::Start),
                                Background(colors::accent_muted),
                                CornerRadius(9.0F)));
  }
  content.push_back(Row{
      SkillHubDialogButton(
          app::strings::skillhub_cancel, false, [dialog] { dialog.Dismiss(); },
          !busy.Get()),
      SkillHubDialogButton(
          busy.Get() ? StringVariant{app::strings::skillhub_installing}
                     : StringVariant{app::strings::skillhub_install_action},
          true,
          [services, state, location, busy, dialog, tasks, toast] {
            if (busy.Get())
              return;
            busy = true;
            tasks.Launch(
                Install(services, state, location, busy, dialog, toast));
          },
          !busy.Get()),
  }
                        .With(Spacing(8.0F)));
  return SkillHubDialogPanel(
      {Column(std::move(content))
           .With(Spacing(12.0F), CrossAlign(CrossAxisAlignment::Stretch))});
}

} // namespace

[[huxerui::composable]] View
SkillStoreDetailScreen(const SkillHubScreenServices &services,
                       const domain::SkillStoreDetailRoute &route) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  const auto clipboard = UseApplication().Clipboard();
  const auto external_link = UseService<application::ExternalLinkService>();
  const TutorialMarkdownLinkHandler markdown_link =
      [external_link, toast](const Uri &target) {
        if (!IsHttpsMarkdownLink(target)) {
          toast.Show(app::strings::skillhub_https_only);
          return;
        }
        if (external_link)
          external_link->Open(target.ToString());
      };
  auto state = UseState(DetailState{});
  auto reading_scale = UseState(1.0F);
  Lifecycle(
      [services, state, reading_scale, tasks, slug = route.slug] {
        RequestReadingScale(services, reading_scale, tasks);
        ReadSessionCookie(
            services, [services, state, tasks,
                       slug](application::SkillHubCookieResult result) {
              // Public detail loading remains available when the host cannot
              // expose its WebView cookie store.
              tasks.Launch(ReloadDetail(services, slug, state,
                                        std::move(result.cookie)));
            });
      },
      route.slug);

  std::vector<View> content;
  if (state->phase == DetailPhase::loading) {
    content.push_back(Stack{ProgressCircle()}.With(
        Padding(16.0F),
        Align(HorizontalAlignment::Center, VerticalAlignment::Center)));
  } else if (state->phase == DetailPhase::failed || !state->detail) {
    content.push_back(
        SkillHubSection(app::strings::skillhub_detail_load_failed,
                        app::images::circle_alert,
                        {Text(state->error + "\n" +
                              UseString(app::strings::skillhub_retry_here))
                             .Style(SkillHubLabel(13.0F, FontWeight::Regular,
                                                  colors::danger))})
            .OnClick([services, route, state, tasks] {
              ReadSessionCookie(
                  services, [services, route, state,
                             tasks](application::SkillHubCookieResult result) {
                    tasks.Launch(ReloadDetail(services, route.slug, state,
                                              std::move(result.cookie)));
                  });
            })
            .With(Focusable(), PointerCursor(PointerCursorKind::Hand)));
  } else {
    const auto &detail = *state->detail;
    content.push_back(Hero(detail, services.catalog));
    const std::string install_prompt =
        UseString(app::strings::skillhub_install_prompt, detail.canonical_name,
                  detail.version);
    const std::string share_text = detail.name + "\n" + detail.description +
                                   "\nhttps://skillhub.cn/skills/" +
                                   (detail.canonical_name.starts_with('@')
                                        ? detail.canonical_name.substr(1)
                                        : detail.canonical_name);
    content.push_back(SkillHubTopMargin(Row{
        DetailAction(app::images::copy, app::strings::skillhub_copy_prompt,
                     [clipboard, toast, install_prompt] {
                       if (clipboard && clipboard->WriteText(install_prompt))
                         toast.Show(app::strings::skillhub_prompt_copied);
                     }),
        DetailAction(
            app::images::save,
            state->starred.value_or(false)
                ? StringVariant{app::strings::skillhub_unstar}
                : StringVariant{app::strings::skillhub_star},
            [services, state, navigation, tasks, toast] {
              ReadSessionCookie(
                  services, [services, state, navigation, tasks,
                             toast](application::SkillHubCookieResult result) {
                    if (!result.Succeeded()) {
                      toast.Show(result.error);
                      return;
                    }
                    tasks.Launch(ToggleStar(services, state, navigation, toast,
                                            std::move(result.cookie)));
                  });
            },
            !state->star_busy),
        DetailAction(app::images::share_2, app::strings::skillhub_share,
                     [services, clipboard, toast, share_text] {
                       if ((!services.share ||
                            !services.share->Share(share_text)) &&
                           clipboard && clipboard->WriteText(share_text)) {
                         toast.Show(app::strings::skillhub_file_copied);
                       }
                     }),
        DetailAction(
            app::images::external_link, app::strings::skillhub_full_features,
            [navigation, detail] {
              navigation.Push(domain::AppRoute::SkillHubSkillSite(
                  detail.namespace_handle.empty() ? "official"
                                                  : detail.namespace_handle,
                  detail.slug));
            }),
    }.With(Spacing(8.0F)), 12.0F));
    content.push_back(SkillHubTopMargin(Stack{
        Text(
            state->installed
                ? StringVariant{app::strings::skillhub_installed}
                : StringVariant{app::strings::skillhub_select_location_install})
            .Style(
                SkillHubLabel(16.0F, FontWeight::Medium, colors::text_on_color))
            .Align(TextAlign::Center)}
                          .OnClick([dialogs, services, state, toast] {
                            dialogs.Show(InstallDialog, services, state, toast);
                          })
                          .With(Frame{.min_height = 48.0F}, Padding(12.0F),
                                Align(HorizontalAlignment::Center,
                                      VerticalAlignment::Center),
                                Background(colors::accent), CornerRadius(12.0F),
                                Enabled(!state->installed), Focusable(),
                                PointerCursor(PointerCursorKind::Hand)), 16.0F));

    std::vector<View> tabs;
    tabs.reserve(kTabs.size());
    for (const auto &tab : kTabs) {
      const bool selected = tab.tab == state->tab;
      std::string label = UseString(tab.label);
      if (tab.tab == DetailTab::files)
        label += " · " + std::to_string(detail.files.size());
      if (tab.tab == DetailTab::comments)
        label += " · " + std::to_string(detail.comments.size());
      tabs.push_back(
          Text(label)
              .Style(SkillHubLabel(
                  13.0F, selected ? FontWeight::Medium : FontWeight::Regular,
                  selected ? Color(colors::accent) : Color(colors::tertiary)))
              .OnClick([state, tab] { SelectTab(state, tab.tab); })
              .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                    Background(selected ? Color(colors::elevated)
                                        : Color::Transparent()),
                    CornerRadius(7.0F), Focusable(),
                    PointerCursor(PointerCursorKind::Hand)));
    }
    content.push_back(SkillHubTopMargin(
        ScrollView(Row(std::move(tabs))
                       .With(Padding(4.0F), Background(colors::surface_light),
                             CornerRadius(10.0F)))
            .ScrollAxis(Axis::Horizontal), 16.0F));

    switch (state->tab) {
    case DetailTab::overview:
      content.push_back(
          Overview(detail, reading_scale, tasks, services.reading,
                   markdown_link));
      break;
    case DetailTab::files:
      content.push_back(
          Files(services, detail, tasks, dialogs, clipboard, toast,
                reading_scale, markdown_link));
      break;
    case DetailTab::comments:
      content.push_back(
          Comments(services, state, detail, tasks, dialogs, toast));
      break;
    case DetailTab::versions:
      content.push_back(Versions(detail));
      break;
    case DetailTab::evaluation:
      content.push_back(Evaluation(detail));
      break;
    case DetailTab::preview:
      content.push_back(Preview(detail));
      break;
    }
  }

  return SkillHubScrollablePage(
      app::strings::skillhub_title_detail, [navigation] { navigation.Pop(); },
      std::move(content));
}

} // namespace linecode::presentation
