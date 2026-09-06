#include "presentation/screens/storage_screen.h"

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/storage_stats.h"
#include "application/storage_stats.h"
#include "domain/app_state.h"
#include "domain/storage_stats.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using domain::StorageCategoryStats;

struct StorageViewState final {
  domain::StorageStats stats;
  bool loaded{};

  bool operator==(const StorageViewState &) const = default;
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation,
            std::function<void()> refresh) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_storage_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{Glyph(app::images::refresh_cw, 18.0F, colors::text)}
          .OnClick(std::move(refresh))
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_refresh},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View Summary(const StorageViewState &state) {
  const StringVariant value =
      state.loaded
          ? StringVariant(application::FormatStorageSize(state.stats.TotalBytes()))
          : StringVariant(app::strings::screen_storage_calculating);
  return Column{
      Text(app::strings::screen_storage_counted)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary)),
      Text(value)
          .Style(Label(26.0F, FontWeight::Bold))
          .With(Padding(EdgeInsets{.top = 4.0F})),
      Text(app::strings::screen_storage_summary)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 4.0F})),
  }
      .With(Padding(16.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(12.0F));
}

View StorageRow(ImageResource icon, StringResource title,
                StringResource description, StorageCategoryStats stats,
                bool loaded, const std::string &item_unit) {
  const StringVariant size =
      loaded ? StringVariant(application::FormatStorageSize(stats.bytes))
             : StringVariant("-");
  const StringVariant count =
      loaded ? StringVariant(std::to_string(stats.count) + item_unit)
             : StringVariant("-");
  return Row{
      Stack{Glyph(std::move(icon), 19.0F, colors::accent)}.With(
          Frame{.width = 38.0F, .height = 38.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(19.0F)),
      Column{
          Text(title).Style(Label(16.0F, FontWeight::Bold)),
          Text(description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      Column{
          Text(size)
              .Style(Label(16.0F, FontWeight::Bold))
              .Align(TextAlign::Trailing),
          Text(count)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .Align(TextAlign::Trailing)
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }.With(CrossAlign(CrossAxisAlignment::End)),
  }
      .With(Frame{.min_height = 62.0F}, Spacing(12.0F), Padding(12.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), CornerRadius(12.0F));
}

Task<void> LoadStats(
    const std::shared_ptr<application::StorageStatsRepository> &repository,
    State<StorageViewState> state) {
  if (!repository) {
    state = StorageViewState{.loaded = true};
    co_return;
  }
  auto loaded = co_await repository->Load();
  if (loaded) {
    state = StorageViewState{.stats = std::move(*loaded), .loaded = true};
  }
}

} // namespace

[[huxerui::composable]] View StorageScreen(
    std::shared_ptr<application::StorageStatsRepository> repository) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  auto state = UseState(StorageViewState{});
  const auto item_unit = UseString(app::strings::screen_storage_unit_items);
  auto refresh = [tasks, repository, state] {
    state = StorageViewState{};
    tasks.Launch([repository, state] { return LoadStats(repository, state); });
  };
  Lifecycle([refresh] { refresh(); });

  std::vector<View> content;
  content.reserve(10);
  content.push_back(Summary(state.Get()));
  content.push_back(Stack{}.With(Frame{.height = 12.0F}));
  content.push_back(StorageRow(
      app::images::git_compare, app::strings::screen_storage_row_diff_cache,
      app::strings::screen_storage_desc_diff, state->stats.diff_cache,
      state->loaded, item_unit));
  content.push_back(Stack{}.With(Frame{.height = 8.0F}));
  content.push_back(StorageRow(
      app::images::message_square, app::strings::screen_storage_row_chat,
      app::strings::screen_storage_desc_chat, state->stats.chat, state->loaded,
      item_unit));
  content.push_back(Stack{}.With(Frame{.height = 8.0F}));
  content.push_back(StorageRow(
      app::images::settings, app::strings::screen_storage_row_config,
      app::strings::screen_storage_desc_config, state->stats.config,
      state->loaded, item_unit));
  content.push_back(Stack{}.With(Frame{.height = 8.0F}));
  content.push_back(StorageRow(
      app::images::folder, app::strings::screen_storage_row_home,
      app::strings::screen_storage_desc_home, state->stats.home, state->loaded,
      item_unit));

  return Column{
      Header(navigation, std::move(refresh)),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(EdgeInsets{.top = 16.0F,
                                              .right = 16.0F,
                                              .bottom = 100.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
