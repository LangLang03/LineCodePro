#include "presentation/screens/input_settings_screen.h"

#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/behavior_settings_repository.h"
#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon)).Tint(tint).With(Frame{.width = size, .height = size});
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_input_title).Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(), Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F}, Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

StringResource BehaviorLabel(domain::EnterKeyBehavior value) {
  return value == domain::EnterKeyBehavior::newline
             ? app::strings::screen_input_enter_newline
             : app::strings::screen_input_enter_send;
}

View PopupItem(PopupContext popup, StringResource label,
               domain::EnterKeyBehavior value,
               State<domain::InputSettings> settings,
               std::shared_ptr<application::InputSettingsRepository> repository,
               TaskScope tasks, ToastHandle toast) {
  const bool selected = settings->enter_key == value;
  return Stack{Text(label).Style(Label(13.0F, FontWeight::Medium,
                                      selected ? colors::text_on_color : colors::secondary))}
      .OnClick([popup, settings, repository, tasks, toast, value] {
        popup.Dismiss();
        if (settings->enter_key == value) return;
        settings.Update([value](auto &next) { next.enter_key = value; });
        tasks.Launch([repository, value, toast]() -> Task<void> {
          auto result = co_await repository->SetEnterKeyBehavior(value);
          if (!result) toast.Show(result.error().message);
        });
      })
      .With(Frame{.width = 98.0F, .height = 38.0F},
            Padding(EdgeInsets::Symmetric(12.0F, 0.0F)),
            Align(HorizontalAlignment::Start, VerticalAlignment::Center),
            Background(selected ? colors::accent : Color::Transparent()),
            CornerRadius(9.0F), Focusable(), PointerCursor(PointerCursorKind::Hand));
}
} // namespace

[[huxerui::composable]] View InputSettingsScreen(
    std::shared_ptr<application::InputSettingsRepository> repository) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const auto popup = UsePopup();
  auto settings = UseState(domain::InputSettings{});
  Lifecycle([tasks, repository, settings, toast] {
    tasks.Launch([repository, settings, toast]() -> Task<void> {
      auto loaded = co_await repository->Load();
      if (loaded) settings = *loaded;
      else toast.Show(loaded.error().message);
    });
  });

  auto selector = Row{
      Text(BehaviorLabel(settings->enter_key)).Style(Label(13.0F, FontWeight::Medium)),
      Glyph(app::images::chevron_down, 13.0F, colors::secondary)
          .With(Frame{.width = 18.0F, .height = 18.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
  }.With(popup.Anchor())
      .OnClick([popup, settings, repository, tasks, toast] {
        popup.Show(
            [settings, repository, tasks, toast](PopupContext context) {
              return Column{
                  PopupItem(context, app::strings::screen_input_enter_send,
                            domain::EnterKeyBehavior::send, settings, repository, tasks, toast),
                  PopupItem(context, app::strings::screen_input_enter_newline,
                            domain::EnterKeyBehavior::newline, settings, repository, tasks, toast),
              }.With(Frame{.width = 104.0F, .height = 82.0F}, Padding(3.0F),
                     Background(colors::surface_light), CornerRadius(12.0F),
                     Border{colors::border_light, 1.0F});
            },
            PopupOptions{.placement = {AnchorSide::Below, AnchorAlignment::End},
                         .gap = 4.0F});
      })
      .With(Frame{.height = 34.0F}, Spacing(2.0F),
            Padding(EdgeInsets{.right = 8.0F, .left = 12.0F}),
            CrossAlign(CrossAxisAlignment::Center), Background(colors::surface_light),
            CornerRadius(8.0F), Border{colors::border_light, 1.0F},
            Focusable(), PointerCursor(PointerCursorKind::Hand));

  auto row = Row{
      Column{
          Text(app::strings::screen_input_enter_behavior_label)
              .Style(Label(16.0F, FontWeight::Medium)),
          Text(app::strings::screen_input_enter_behavior_desc)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }.With(Spacing(2.0F), Grow()),
      std::move(selector),
  }.With(Spacing(12.0F), Padding(EdgeInsets::All(16.0F)),
         CrossAlign(CrossAxisAlignment::Center));

  auto section = Column{
      Text(app::strings::screen_input_section_input)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 20.0F, .right = 16.0F,
                                   .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{std::move(row).With(
          CornerRadius(12.0F), Background(colors::elevated))},
  }.With(CrossAlign(CrossAxisAlignment::Stretch));

  return Column{
      Header(navigation), Divider(),
      ScrollView(Column{std::move(section),
                        Stack{}.With(Frame{.width = 1.0F, .height = 100.0F})}
          .With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background)))
          .ScrollAxis(Axis::Vertical).With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background),
         SafeAreaPadding{});
}

} // namespace linecode::presentation
