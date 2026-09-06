#include "presentation/screens/error_logs_screen.h"

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/error_log_service.h"
#include "domain/app_state.h"
#include "domain/error_log.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class LoadPhase : std::uint8_t { loading, ready, failed };

struct ErrorLogsViewState final {
  LoadPhase phase{LoadPhase::loading};
  bool clearing{};
  std::vector<domain::ErrorLogEntry> entries;
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
            bool clearing, std::function<void()> on_clear) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_error_logs_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{Glyph(app::images::trash_2, 20.0F, colors::danger)}
          .OnClick(std::move(on_clear))
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Enabled(!clearing),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::screen_error_logs_cleared},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View EmptyMessage(StringResource message) {
  return Text(message)
      .Style(Label(16.0F, FontWeight::Regular, colors::tertiary))
      .Align(TextAlign::Center)
      .With(Padding(EdgeInsets::Symmetric(16.0F, 24.0F)));
}

View ErrorState(std::function<void()> retry) {
  return Column{
      EmptyMessage(app::strings::screen_error_logs_load_failed),
      Text(app::strings::common_refresh)
          .Style(Label(13.0F, FontWeight::Medium, colors::accent))
          .OnClick(std::move(retry))
          .With(Frame{.height = 36.0F, .min_width = 68.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(CrossAlign(CrossAxisAlignment::Center));
}

View LogRow(const domain::ErrorLogEntry &entry,
            std::function<void()> on_open) {
  return Row{
      Stack{Glyph(app::images::file_text, 20.0F, colors::accent)}
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(entry.title).Style(Label(16.0F, FontWeight::Medium)),
          Text(entry.subtitle)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(on_open))
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View LogSection(const std::vector<domain::ErrorLogEntry> &entries,
                std::function<void(const domain::ErrorLogEntry &)> on_open) {
  std::vector<View> rows;
  rows.reserve(entries.size() * 2U);
  for (std::size_t index = 0; index < entries.size(); ++index) {
    const domain::ErrorLogEntry entry = entries[index];
    rows.push_back(LogRow(entry, [on_open, entry] { on_open(entry); })
                       .Key(entry.id));
    if (index + 1U < entries.size()) {
      rows.push_back(Row{
          Stack{}.With(Frame{.width = 68.0F, .height = 1.0F}),
          Divider().With(Grow()),
      });
    }
  }
  return Column{
      Text(app::strings::screen_error_logs_section_title)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(rows))
              .With(CrossAlign(CrossAxisAlignment::Stretch),
                    Background(colors::elevated), CornerRadius(12.0F)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

Task<void> RefreshLogs(std::shared_ptr<application::ErrorLogService> service,
                       State<ErrorLogsViewState> state) {
  state.Update([](ErrorLogsViewState &next) {
    next.phase = LoadPhase::loading;
  });
  auto loaded = co_await service->Refresh();
  if (!loaded) {
    state.Update([](ErrorLogsViewState &next) {
      next.phase = LoadPhase::failed;
    });
    co_return;
  }
  state.Update([entries = std::move(*loaded)](ErrorLogsViewState &next) mutable {
    next.phase = LoadPhase::ready;
    next.entries = std::move(entries);
  });
}

Task<void> ClearLogs(std::shared_ptr<application::ErrorLogService> service,
                     State<ErrorLogsViewState> state, ToastHandle toast) {
  state.Update([](ErrorLogsViewState &next) { next.clearing = true; });
  auto cleared = co_await service->ClearAndRefresh();
  if (!cleared) {
    state.Update([](ErrorLogsViewState &next) { next.clearing = false; });
    toast.Show(app::strings::screen_error_logs_clear_failed);
    co_return;
  }
  state.Update([entries = std::move(*cleared)](ErrorLogsViewState &next) mutable {
    next.phase = LoadPhase::ready;
    next.clearing = false;
    next.entries = std::move(entries);
  });
  toast.Show(app::strings::screen_error_logs_cleared);
}

Task<void> OpenLog(std::shared_ptr<application::ErrorLogService> service,
                   domain::ErrorLogEntry entry, std::string chooser_title,
                   ToastHandle toast) {
  auto opened = co_await service->Open(entry.id, entry.title, chooser_title);
  if (!opened) {
    toast.Show(app::strings::screen_error_logs_open_failed);
  }
}

} // namespace

[[huxerui::composable]] View
ErrorLogsScreen(std::shared_ptr<application::ErrorLogService> service) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(ErrorLogsViewState{});
  const std::string chooser_title =
      UseString(app::strings::screen_error_logs_open_with);

  Lifecycle([tasks, service, state] {
    tasks.Launch([service, state] { return RefreshLogs(service, state); });
  });

  View content;
  if (state->phase == LoadPhase::loading) {
    content = EmptyMessage(app::strings::screen_error_logs_loading);
  } else if (state->phase == LoadPhase::failed) {
    content = ErrorState([tasks, service, state] {
      tasks.Launch([service, state] { return RefreshLogs(service, state); });
    });
  } else if (state->entries.empty()) {
    content = EmptyMessage(app::strings::screen_error_logs_empty);
  } else {
    content = LogSection(
        state->entries,
        [tasks, service, chooser_title, toast](domain::ErrorLogEntry entry) {
          tasks.Launch([service, entry = std::move(entry), chooser_title,
                        toast]() mutable {
            return OpenLog(service, std::move(entry), chooser_title, toast);
          });
        });
  }

  return Column{
      Header(navigation, state->clearing, [tasks, service, state, toast] {
        if (state->clearing) {
          return;
        }
        tasks.Launch([service, state, toast] {
          return ClearLogs(service, state, toast);
        });
      }),
      Divider(),
      ScrollView(Column{
                     std::move(content),
                     Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}),
                 }
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
