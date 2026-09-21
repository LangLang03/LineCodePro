#include "presentation/screens/image_model_picker_screen.h"

#include <algorithm>
#include <functional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "domain/model_config.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/image_model_presentation.h"
#include "presentation/line_theme.h"
#include "presentation/model_protocol_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct PickerState final {
  std::vector<domain::ModelConfig> models;
  std::string selected_id;
  std::string error;
  bool loading{true};
  bool saving{};
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Header(StringResource title,
            const RouteNavigationController<domain::AppRoute>& navigation) {
  return LegacyScreenHeaderLayout {
    Stack {
      Image(app::images::chevron_left)
          .Tint(colors::text)
          .With(Frame{.width = 22.0F, .height = 22.0F}),
    }.OnClick([navigation] { navigation.Pop(); })
        .With(Frame{.width = 36.0F, .height = 36.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Focusable(), PointerCursor(PointerCursorKind::Hand)),
    Stack {
      Text(title).Style(Label(17.0F, FontWeight::Bold)),
    }.With(Grow(),
           Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
    Stack {}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F},
         Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

View ModelCard(const domain::ModelConfig& model, bool selected, bool enabled,
               std::function<void()> select) {
  std::vector<View> content;
  content.reserve(selected ? 3 : 2);
  const auto provider = model.provider_label.empty()
                            ? StringVariant{ModelProtocolPresentationFor(
                                                model.protocol)
                                                .descriptive_name}
                            : StringVariant{model.provider_label};
  content.push_back(
    Text(provider)
        .Style(Label(11.0F, FontWeight::Bold, colors::text_on_color))
        .With(Padding(EdgeInsets::Symmetric(8.0F, 4.0F)),
              Background(ModelProtocolPresentationFor(model.protocol)
                             .badge_color),
              CornerRadius(8.0F)));
  content.push_back(
    Column {
      Text(model.name).Style(Label(16.0F, FontWeight::Medium)),
      Text(model.model_id)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 2.0F})),
    }.With(Grow()));
  if (selected) {
    content.push_back(
        Stack {}.With(Frame{.width = 8.0F, .height = 8.0F},
                      Background(colors::accent), CornerRadius(4.0F)));
  }
  return Row(content).OnClick(std::move(select))
      .With(Spacing(12.0F), Padding(12.0F),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::background),
            Border(selected ? colors::accent : Color::Transparent(), 1.0F),
            CornerRadius(12.0F), Enabled{enabled}, Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

Task<void> Load(
    domain::ImageModelPurpose purpose,
    std::shared_ptr<application::ToolSettingsService> settings,
    std::shared_ptr<application::ModelStore> models,
    State<PickerState> state) {
  auto stored_settings = co_await settings->Load();
  if (!stored_settings) {
    auto next = state.Get();
    next.loading = false;
    next.error = stored_settings.error().message;
    state = std::move(next);
    co_return;
  }
  auto stored_models = co_await models->List();
  if (!stored_models) {
    auto next = state.Get();
    next.loading = false;
    next.error = stored_models.error().message;
    state = std::move(next);
    co_return;
  }
  const auto &setting = application::ImageModelSettingInfo(purpose);
  state = PickerState{
      .models = std::move(*stored_models),
      .selected_id = (*stored_settings).*setting.state_member,
      .error = {},
      .loading = false,
      .saving = false,
  };
}

} // namespace

[[huxerui::composable]] View ImageModelPickerScreen(
    domain::ImageModelPurpose purpose,
    std::shared_ptr<application::ToolSettingsService> settings,
    std::shared_ptr<application::ModelStore> models,
    std::function<void()> on_selected) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(PickerState{});

  Lifecycle([tasks, purpose, settings, models, state] {
    tasks.Launch([purpose, settings, models, state] {
      return Load(purpose, settings, models, state);
    });
  });

  std::vector<View> content;
  if (state->loading) {
    content.push_back(
        Stack {ProgressCircle().With(Frame{.width = 24.0F, .height = 24.0F})}
            .With(Padding(24.0F),
                  Align(HorizontalAlignment::Center,
                        VerticalAlignment::Center)));
  } else if (!state->error.empty()) {
    content.push_back(
        Text(state->error)
            .Style(Label(13.0F, FontWeight::Regular, colors::danger)));
  } else if (state->models.empty()) {
    content.push_back(
        Text(app::strings::screen_models_empty_readonly)
            .Style(Label(13.0F, FontWeight::Regular, colors::tertiary)));
  }

  for (const auto& model : state->models) {
    const bool selected = state->selected_id == model.id;
    content.push_back(
        ModelCard(model, selected, !state->saving,
                  [tasks, settings, state, toast, on_selected, navigation,
                   purpose, id = model.id] {
                    auto next = state.Get();
                    next.saving = true;
                    state = std::move(next);
                    tasks.Launch([settings, state, toast, on_selected,
                                  navigation, purpose, id]() -> Task<void> {
                      auto saved = co_await settings->Persist(
                          application::ImageModelSelectionChange{
                              .purpose = purpose,
                              .model_id = id,
                          });
                      if (!saved) {
                        auto failed = state.Get();
                        failed.saving = false;
                        failed.error = saved.error().message;
                        state = std::move(failed);
                        toast.Show(saved.error().message);
                        co_return;
                      }
                      if (on_selected)
                        std::invoke(on_selected);
                      auto selected = state.Get();
                      selected.selected_id = id;
                      selected.saving = false;
                      state = std::move(selected);
                      navigation.Pop();
                    });
                  })
            .Key(model.id));
    content.push_back(Stack {}.With(Frame{.height = 8.0F}));
  }

  return Column {
    Header(ImageModelPresentationFor(purpose).picker_title, navigation),
    LegacyScreenHeaderDivider(),
    ScrollView(Column(content).With(
                   Padding(EdgeInsets{.top = 16.0F,
                                      .right = 16.0F,
                                      .bottom = 100.0F,
                                      .left = 16.0F}),
                   CrossAlign(CrossAxisAlignment::Stretch)))
        .ScrollAxis(Axis::Vertical)
        .With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch),
         Background(colors::background), SafeAreaPadding {});
}

} // namespace linecode::presentation
