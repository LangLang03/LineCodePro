#include "presentation/screens/model_list_screen.h"

#include <algorithm>
#include <functional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct ModelListState final {
  std::vector<domain::ModelConfig> models;
  std::vector<std::string> marked;
  std::string selected_id;
  std::string error;
  bool loading{true};
  bool multi_select{};
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

bool Marked(const ModelListState &state, std::string_view id) {
  return std::ranges::find(state.marked, id) != state.marked.end();
}

Color ProtocolColor(domain::ModelProtocol protocol) {
  switch (protocol) {
  case domain::ModelProtocol::openai_compatible:
    return Color::Rgb(16, 163, 127);
  case domain::ModelProtocol::codex_responses:
    return Color::Rgb(75, 139, 255);
  case domain::ModelProtocol::anthropic_messages:
    return Color::Rgb(184, 111, 80);
  case domain::ModelProtocol::local_gguf:
    return Color::Rgb(46, 125, 98);
  }
  return colors::accent;
}

Task<void> Reload(const std::shared_ptr<application::ModelStore> &store,
                  State<ModelListState> state) {
  auto loaded = co_await store->List();
  auto selected = co_await store->SelectedId();
  ModelListState next = state.Get();
  next.loading = false;
  if (!loaded) {
    next.error = loaded.error().message;
  } else {
    next.models = std::move(*loaded);
    next.error.clear();
  }
  if (selected)
    next.selected_id = std::move(*selected);
  state = std::move(next);
}

Task<void> SelectModel(const std::shared_ptr<application::ModelStore> &store,
                       State<ModelListState> state, std::string id) {
  auto result = co_await store->Select(id);
  if (!result) {
    auto next = state.Get();
    next.error = result.error().message;
    state = std::move(next);
    co_return;
  }
  co_await Reload(store, state);
}

Task<void> DeleteMarked(const std::shared_ptr<application::ModelStore> &store,
                        State<ModelListState> state) {
  auto ids = state->marked;
  auto result = co_await store->Delete(std::move(ids));
  if (!result) {
    auto next = state.Get();
    next.error = result.error().message;
    state = std::move(next);
    co_return;
  }
  auto next = state.Get();
  next.marked.clear();
  next.multi_select = false;
  state = std::move(next);
  co_await Reload(store, state);
}

View SheetPanel(StringVariant title, std::vector<View> rows) {
  rows.push_back(Spacer().With(Frame{.height = 16.0F}));
  return Column{
      Row{Spacer(),
          Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                       Background(colors::tertiary), CornerRadius(2.0F)),
          Spacer()}
          .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
      Text(std::move(title))
          .Style(Label(17.0F, FontWeight::Bold))
          .With(Padding(EdgeInsets{
              .top = 12.0F, .right = 24.0F, .bottom = 8.0F, .left = 24.0F})),
      Divider(),
      Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch)),
  }
      .With(Frame{.max_width = 560.0F}, Background(colors::background),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(24.0F), ClipChildren(),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View SheetRow(ImageResource icon, StringVariant text, Color tint,
              std::function<void()> action) {
  return Row{
      Glyph(std::move(icon), 20.0F, tint),
      Text(std::move(text)).Style(Label(15.0F, FontWeight::Medium, tint))}
      .OnClick([action = std::move(action)] {
        if (action)
          std::invoke(action);
      })
      .With(Frame{.min_height = 52.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(24.0F, 14.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

void ToggleMarked(State<ModelListState> state, std::string id) {
  auto next = state.Get();
  const auto found = std::ranges::find(next.marked, id);
  if (found == next.marked.end())
    next.marked.push_back(std::move(id));
  else
    next.marked.erase(found);
  state = std::move(next);
}

View ModelCard(const domain::ModelConfig &model, State<ModelListState> state,
               const std::shared_ptr<application::ModelStore> &store,
               const TaskScope &tasks, const BottomSheetHandle &sheets,
               const ModelListActions &actions) {
  const bool current = state->selected_id == model.id;
  const bool marked = Marked(state.Get(), model.id);
  std::vector<View> trailing;
  if (state->multi_select) {
    trailing.push_back(
        Stack{
            marked ? Glyph(app::images::check, 14.0F, colors::text_on_color)
                   : Spacer().With(Frame{.width = 0.0F, .height = 0.0F}),
        }
            .With(Frame{.width = 22.0F, .height = 22.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(marked ? colors::accent : Color::Transparent()),
                  Border{.color = marked ? colors::accent : colors::border,
                         .width = 1.0F},
                  CornerRadius(11.0F)));
  } else if (current) {
    trailing.push_back(Stack{}.With(Frame{.width = 8.0F, .height = 8.0F},
                                    Background(colors::accent),
                                    CornerRadius(4.0F)));
  }

  auto activate = [state, store, tasks, id = model.id] {
    if (state->multi_select) {
      ToggleMarked(state, id);
    } else {
      tasks.Launch([store, state, id]() -> Task<void> {
        co_await SelectModel(store, state, id);
      });
    }
  };
  auto show_actions = [sheets, state, model, actions](const LongPressEvent &) {
    sheets.Show([state, model, actions](BottomSheetContext sheet) {
      std::vector<View> rows;
      rows.push_back(SheetRow(app::images::sliders_horizontal,
                              app::strings::model_list_modify, colors::text,
                              [sheet, callback = actions.on_edit, model] {
                                sheet.Dismiss();
                                if (callback)
                                  std::invoke(callback, model);
                              }));
      rows.push_back(SheetRow(app::images::check,
                              app::strings::model_list_multiselect,
                              colors::text, [sheet, state, id = model.id] {
                                auto next = state.Get();
                                next.multi_select = true;
                                if (!Marked(next, id))
                                  next.marked.push_back(id);
                                state = std::move(next);
                                sheet.Dismiss();
                              }));
      return SheetPanel(model.name, std::move(rows));
    });
  };

  return Row{
      Column{
          Text(model.provider_label)
              .Style(Label(11.0F, FontWeight::Bold, colors::text_on_color))
              .With(Padding(EdgeInsets::Symmetric(8.0F, 3.0F)),
                    Background(ProtocolColor(model.protocol)),
                    CornerRadius(6.0F)),
          Text(model.name)
              .Style(Label(16.0F, FontWeight::Medium))
              .With(Padding(EdgeInsets{.top = 8.0F})),
          Text(model.model_id)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      Row(std::move(trailing)).With(CrossAlign(CrossAxisAlignment::Center)),
  }
      .OnClick(std::move(activate))
      .With(LongPressGesture{})
      .On<LongPressEvents::Started>(std::move(show_actions))
      .With(Spacing(12.0F), Padding(EdgeInsets::All(12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated),
            Border{.color = colors::border, .width = 1.0F}, CornerRadius(12.0F),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

} // namespace

[[huxerui::composable]] View
ModelListScreen(std::shared_ptr<application::ModelStore> store,
                ModelListActions actions) {
  auto state = UseState(ModelListState{});
  const auto tasks = UseTaskScope();
  const auto sheets = UseBottomSheet();
  Lifecycle([tasks, store, state] {
    tasks.Launch(
        [store, state]() -> Task<void> { co_await Reload(store, state); });
  });

  auto header_action = [state, actions, sheets, tasks, store] {
    if (!state->multi_select) {
      if (actions.on_add)
        std::invoke(actions.on_add);
      return;
    }
    if (state->marked.empty())
      return;
    sheets.Show([state, tasks, store](BottomSheetContext sheet) {
      std::vector<View> rows;
      rows.push_back(
          Text(app::strings::model_list_delete_detail)
              .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets::Symmetric(24.0F, 10.0F))));
      rows.push_back(SheetRow(app::images::trash_2,
                              app::strings::model_list_delete, colors::danger,
                              [sheet, state, tasks, store] {
                                sheet.Dismiss();
                                tasks.Launch([store, state]() -> Task<void> {
                                  co_await DeleteMarked(store, state);
                                });
                              }));
      return SheetPanel(app::strings::model_list_delete, std::move(rows));
    });
  };

  std::vector<View> cards;
  cards.reserve(state->models.size() * 2 + 2);
  if (!state->error.empty()) {
    cards.push_back(
        Text(state->error)
            .Style(Label(12.0F, FontWeight::Regular, colors::danger)));
  } else if (!state->loading && state->models.empty()) {
    cards.push_back(
        Text(app::strings::model_list_empty)
            .Style(Label(14.0F, FontWeight::Regular, colors::tertiary)));
  }
  for (const auto &model : state->models) {
    cards.push_back(
        ModelCard(model, state, store, tasks, sheets, actions).Key(model.id));
    cards.push_back(Spacer().With(Frame{.height = 8.0F}));
  }

  StringVariant title = app::strings::model_list_title;
  if (state->multi_select) {
    title = StringVariant::Format(app::strings::model_list_selected_count,
                                  state->marked.size());
  }
  return Column{
      LegacyScreenHeaderLayout{
          Stack{Glyph(state->multi_select ? app::images::x
                                          : app::images::chevron_left,
                      22.0F, colors::text)}
              .OnClick([state, callback = actions.on_back] {
                if (state->multi_select) {
                  auto next = state.Get();
                  next.multi_select = false;
                  next.marked.clear();
                  state = std::move(next);
                } else if (callback)
                  std::invoke(callback);
              })
              .With(
                  Frame{.width = 36.0F, .height = 36.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
          Stack{Text(title).Style(Label(17.0F, FontWeight::Bold))}.With(
              Grow(),
              Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
          Stack{Glyph(state->multi_select ? app::images::trash_2
                                          : app::images::plus,
                      22.0F,
                      state->multi_select && state->marked.empty()
                          ? colors::tertiary
                          : colors::text)}
              .OnClick(std::move(header_action))
              .With(
                  Frame{.width = 36.0F, .height = 36.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
      }
          .With(Frame{.min_height = 60.0F},
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
                Background(colors::background)),
      Divider(),
      ScrollView(Column(std::move(cards))
                     .With(Padding(EdgeInsets{.top = 16.0F,
                                              .right = 16.0F,
                                              .bottom = 100.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow(), ScrollBar()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
