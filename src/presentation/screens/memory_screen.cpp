#include "presentation/screens/memory_screen.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cstdint>
#include <ctime>
#include <functional>
#include <iomanip>
#include <memory>
#include <optional>
#include <ranges>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/memory_store.h"
#include "domain/app_state.h"
#include "domain/memory.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class LoadPhase : std::uint8_t { loading, ready, failed };

struct MemoryPageState final {
  LoadPhase phase{LoadPhase::loading};
  domain::MemoryOverview overview;
  std::vector<std::string> selected;
  std::string error;
  bool deleting{};
};

struct MemoryEditorState final {
  domain::MemoryScope scope{domain::MemoryScope::user};
  TextEditingValue content;
  bool saving{};
};

struct ResolvedMemoryStrings final {
  std::string title;
  std::string long_term;
  std::string project;
  std::string environment;
  std::string short_term;
  std::string chat_index;
  std::string delete_title;
  std::string delete_prompt;
  std::string batch_delete_prefix;
  std::string batch_delete_suffix;
  std::string editor_add;
  std::string editor_edit;
  std::string scope_user;
  std::string scope_project;
  std::string scope_environment;
  std::string input_hint;
  std::string empty_toast;
  std::string action_title;
  std::string action_edit;
  std::string action_delete;
  std::string action_multi_select;
  std::string selected_prefix;
  std::string selected_suffix;
  std::string current_project;
  std::string project_unselected;
  std::string empty;
  std::string source;
  std::string used_prefix;
  std::string used_suffix;
  std::string scope;
  std::string project_field;
  std::string confidence;
  std::string use_count;
  std::string created;
  std::string updated;
  std::string last_used;
  std::string expires;
  std::string title_field;
  std::string conversation;
  std::string message;
  std::string global;
  std::string not_used;
  std::string no_expiry;
  std::string empty_value;
  std::string unknown_time;
  std::string loading;
  std::string retry;
  std::string save;
  std::string cancel;
  std::string close;
};

struct MemorySectionSpec final {
  std::string ResolvedMemoryStrings::*title;
  std::vector<domain::MemoryRecord> domain::MemoryOverview::*rows;
  ImageResource icon;
};

struct ScopePresentationSpec final {
  domain::MemoryScope value;
  std::string ResolvedMemoryStrings::*label;
};

inline constexpr std::array scope_presentation_catalog{
    ScopePresentationSpec{domain::MemoryScope::user,
                          &ResolvedMemoryStrings::scope_user},
    ScopePresentationSpec{domain::MemoryScope::project,
                          &ResolvedMemoryStrings::scope_project},
    ScopePresentationSpec{domain::MemoryScope::environment,
                          &ResolvedMemoryStrings::scope_environment},
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

bool Selected(const MemoryPageState &state, std::string_view id) {
  return std::ranges::find(state.selected, id) != state.selected.end();
}

void ToggleSelection(State<MemoryPageState> state, std::string id) {
  state.Update([&](MemoryPageState &next) {
    const auto found = std::ranges::find(next.selected, id);
    if (found == next.selected.end())
      next.selected.push_back(std::move(id));
    else
      next.selected.erase(found);
  });
}

std::string Upper(std::string value) {
  std::ranges::transform(value, value.begin(), [](unsigned char byte) {
    return static_cast<char>(std::toupper(byte));
  });
  return value;
}

std::string Time(std::int64_t value, const ResolvedMemoryStrings &strings) {
  if (value <= 0)
    return strings.unknown_time;
  const auto seconds = static_cast<std::time_t>(value / 1000);
  std::tm local{};
#ifdef _WIN32
  localtime_s(&local, &seconds);
#else
  localtime_r(&seconds, &local);
#endif
  std::ostringstream output;
  output << std::put_time(&local, "%x %H:%M");
  return output.str();
}

std::string Preview(std::string_view value, std::size_t size,
                    const ResolvedMemoryStrings &strings) {
  auto preview = domain::PreviewMemoryText(value, size);
  return preview.empty() ? strings.empty_value : preview;
}

std::string MemoryDescription(const domain::MemoryRecord &memory,
                              const ResolvedMemoryStrings &strings) {
  return strings.source + memory.source + " · " + strings.used_prefix +
         std::to_string(memory.use_count) + " " + strings.used_suffix + " · " +
         Time(memory.updated_at, strings);
}

std::string MemoryDetail(const domain::MemoryRecord &memory,
                         const ResolvedMemoryStrings &strings) {
  std::ostringstream confidence;
  confidence << std::fixed << std::setprecision(2) << memory.confidence;
  return memory.content + "\n\n" + strings.scope +
         std::string{domain::MemoryScopeDefinition(memory.scope).storage_name} +
         "\n" + strings.source + memory.source + "\n" + strings.project_field +
         (memory.project_id.empty() ? strings.global : memory.project_id) +
         "\n" + strings.confidence + confidence.str() + "\n" +
         strings.use_count + std::to_string(memory.use_count) + "\n" +
         strings.created + Time(memory.created_at, strings) + "\n" +
         strings.updated + Time(memory.updated_at, strings) + "\n" +
         strings.last_used +
         (memory.last_used_at > 0 ? Time(memory.last_used_at, strings)
                                  : strings.not_used);
}

std::string WorkingDetail(const domain::WorkingMemoryRecord &memory,
                          const ResolvedMemoryStrings &strings) {
  return memory.content + "\n\n" + strings.source + memory.source + "\n" +
         strings.project_field +
         (memory.project_id.empty() ? strings.global : memory.project_id) +
         "\n" + strings.created + Time(memory.created_at, strings) + "\n" +
         strings.updated + Time(memory.updated_at, strings) + "\n" +
         strings.expires +
         (memory.expires_at > 0 ? Time(memory.expires_at, strings)
                                : strings.no_expiry);
}

std::string HistoryDescription(const domain::ConversationIndexRecord &entry,
                               const ResolvedMemoryStrings &strings) {
  const auto &title = entry.title.empty() ? entry.conversation_id : entry.title;
  return title + " · " + entry.role + " · " + Time(entry.updated_at, strings);
}

std::string HistoryDetail(const domain::ConversationIndexRecord &entry,
                          const ResolvedMemoryStrings &strings) {
  return (entry.title.empty() ? std::string{}
                              : strings.title_field + entry.title + "\n") +
         entry.role + ": " + entry.text + "\n\n" + strings.conversation +
         entry.conversation_id + "\n" + strings.message +
         (entry.message_id.empty() ? "-" : entry.message_id) + "\n" +
         strings.created + Time(entry.created_at, strings) + "\n" +
         strings.updated + Time(entry.updated_at, strings);
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation,
            State<MemoryPageState> state, const ResolvedMemoryStrings &strings,
            std::function<void()> add, std::function<void()> delete_selected) {
  const bool selecting = !state->selected.empty();
  View left =
      Stack{Glyph(selecting ? app::images::x : app::images::chevron_left,
                  selecting ? 20.0F : 22.0F, colors::text)}
          .OnClick([navigation, state, selecting] {
            if (selecting)
              state.Update(
                  [](MemoryPageState &next) { next.selected.clear(); });
            else
              navigation.Pop();
          })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand));
  View right =
      Stack{Glyph(selecting ? app::images::trash_2 : app::images::plus, 20.0F,
                  selecting ? colors::danger : colors::accent)}
          .OnClick(selecting ? std::move(delete_selected) : std::move(add))
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Enabled{!state->deleting}, Focusable(),
                PointerCursor(PointerCursorKind::Hand));
  const std::string title = selecting
                                ? strings.selected_prefix +
                                      std::to_string(state->selected.size()) +
                                      strings.selected_suffix
                                : strings.title;
  return LegacyScreenHeaderLayout{
      left,
      Stack{Text(title).Style(Label(17.0F, FontWeight::Bold))}.With(
          Grow(),
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      right,
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View EmptyRow(const ResolvedMemoryStrings &strings) {
  return Text(strings.empty)
      .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))
      .With(Padding(16.0F));
}

View RowDivider() { return Divider(); }

View Section(std::string title, std::vector<View> rows) {
  if (rows.empty())
    rows.push_back(Text(title));
  return Column{
      Text(Upper(std::move(title)))
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Frame{.height = 47.625F}, Padding(EdgeInsets{.top = 20.0F,
                                                             .right = 16.0F,
                                                             .bottom = 12.0F,
                                                             .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(rows))
              .With(CrossAlign(CrossAxisAlignment::Stretch),
                    Background(colors::elevated), CornerRadius(12.0F)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View MemoryRow(const domain::MemoryRecord &memory, ImageResource icon,
               bool multi_select, bool selected,
               const ResolvedMemoryStrings &strings,
               std::function<void()> activate,
               std::function<void()> long_press) {
  return Row{
      Stack{Glyph(icon, 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(Preview(memory.content, 80, strings))
              .Style(Label(16.0F, FontWeight::Medium)),
          Text(MemoryDescription(memory, strings))
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      multi_select ? Stack{}.With(Frame{.width = 0.0F, .height = 0.0F})
                   : Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
                         .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(activate))
      .With(LongPressGesture{})
      .On<LongPressEvents::Started>(
          [action = std::move(long_press)](const LongPressEvent &) {
            std::invoke(action);
          })
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(selected ? colors::accent_muted : colors::elevated),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

template <typename Record, typename Title, typename Description>
View ReadOnlyRow(const Record &record, ImageResource icon, Title title,
                 Description description, std::function<void()> activate) {
  return Row{
      Stack{Glyph(icon, 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(std::invoke(title, record))
              .Style(Label(16.0F, FontWeight::Medium)),
          Text(std::invoke(description, record))
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(activate))
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DialogPanel(std::string title, std::vector<View> content) {
  content.insert(content.begin(),
                 Text(std::move(title))
                     .Style(Label(17.0F, FontWeight::Bold))
                     // HuxerUI's 17sp system-font box is about 1.5dp shorter
                     // than the legacy TextView with font padding disabled.
                     .With(Padding(EdgeInsets{.bottom = 13.5F})));
  return ScrollView(Column(std::move(content))
                        .With(CrossAlign(CrossAxisAlignment::Stretch),
                              Padding(16.0F), Background(colors::elevated),
                              CornerRadius(12.0F)))
      .ScrollAxis(Axis::Vertical)
      .With(Frame{.max_width = 560.0F});
}

View DialogAction(std::string text, Color color, std::function<void()> action) {
  return Text(std::move(text))
      .Style(Label(16.0F, FontWeight::Bold, color))
      .Align(TextAlign::Center)
      .OnClick(std::move(action))
      .With(Frame{.min_height = 39.25F},
            Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DialogActions(std::vector<View> actions) {
  return Row(std::move(actions))
      .With(MainAlign(MainAxisAlignment::End),
            CrossAlign(CrossAxisAlignment::Center),
            Padding(EdgeInsets{.top = 16.0F}));
}

View DialogBody(std::string body) {
  return Text(std::move(body))
      .Style(Label(13.0F, FontWeight::Regular, colors::secondary));
}

void ShowTextDialog(const DialogHandle &dialogs, std::string title,
                    std::string body, std::string close) {
  dialogs.Show([title = std::move(title), body = std::move(body),
                close = std::move(close)](DialogContext dialog) {
    return DialogPanel(
        title, {DialogBody(body),
                DialogActions({DialogAction(close, colors::accent,
                                            [dialog] { dialog.Dismiss(); })})});
  });
}

TextFieldStyle MemoryEditorFieldStyle() {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Outlined;
  style.show_label = false;
  style.outlined.background = colors::input;
  style.outlined.border = colors::border_light;
  style.outlined.hovered_border = colors::border_light;
  style.outlined.focused_border = colors::border_light;
  style.outlined.minimum_height = 132.25F;
  style.text_style = Label(16.0F);
  style.placeholder_style = Label(16.0F, FontWeight::Regular, colors::tertiary);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  style.border_width = 1.0F;
  style.focused_border_width = 1.0F;
  style.outlined.corner_radii = CornerRadii{8.0F};
  style.padding = EdgeInsets::All(12.0F);
  return style;
}

Task<void> Reload(std::shared_ptr<application::MemoryStore> store,
                  std::string project_id, State<MemoryPageState> state) {
  if (!store) {
    state.Update([](MemoryPageState &next) {
      next.phase = LoadPhase::failed;
      next.error = "memory store unavailable";
    });
    co_return;
  }
  auto loaded = co_await store->LoadOverview(std::move(project_id));
  if (!loaded) {
    state.Update([message = loaded.error().message](MemoryPageState &next) {
      next.phase = LoadPhase::failed;
      next.error = message;
    });
    co_return;
  }
  state.Update([overview = std::move(*loaded)](MemoryPageState &next) mutable {
    next.phase = LoadPhase::ready;
    next.overview = std::move(overview);
    std::erase_if(next.selected, [&](const std::string &id) {
      const auto contains = [&](const auto &records) {
        return std::ranges::find(records, id, &domain::MemoryRecord::id) !=
               records.end();
      };
      return !contains(next.overview.long_term) &&
             !contains(next.overview.project) &&
             !contains(next.overview.environment);
    });
    next.error.clear();
  });
}

Task<void> DeleteMemories(std::shared_ptr<application::MemoryStore> store,
                          std::vector<std::string> ids, std::string project_id,
                          State<MemoryPageState> state, ToastHandle toast) {
  state.Update([](MemoryPageState &next) { next.deleting = true; });
  auto deleted = co_await store->Delete(std::move(ids));
  if (!deleted) {
    state.Update([](MemoryPageState &next) { next.deleting = false; });
    toast.Show(deleted.error().message);
    co_return;
  }
  state.Update([](MemoryPageState &next) {
    next.deleting = false;
    next.selected.clear();
  });
  co_await Reload(store, std::move(project_id), state);
}

[[huxerui::composable]] View MemoryEditorDialog(
    DialogContext dialog, std::shared_ptr<application::MemoryStore> store,
    domain::MemoryRecord memory, std::string project_id,
    ResolvedMemoryStrings strings, std::function<void()> on_saved) {
  auto state = UseState(MemoryEditorState{
      .scope = memory.scope,
      .content = TextEditingValue::FromText(memory.content),
  });
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const bool editing = !memory.id.empty();

  std::vector<View> scopes;
  scopes.reserve(scope_presentation_catalog.size());
  for (const auto &spec : scope_presentation_catalog) {
    scopes.push_back(
        RadioButton(strings.*(spec.label), state->scope == spec.value)
            .OnChanged([state, scope = spec.value](bool selected) {
              if (selected)
                state.Update(
                    [scope](MemoryEditorState &next) { next.scope = scope; });
            })
            .With(Enabled(!state->saving))
            .Key(domain::MemoryScopeDefinition(spec.value).storage_name));
  }

  ThemeDefinition definition;
  definition.Set(MemoryEditorFieldStyle());
  View input = Theme(
      definition, TextField(state->content)
                      .Label(strings.input_hint)
                      .Placeholder(strings.input_hint)
                      .Variant(TextFieldVariant::Outlined)
                      .LineLimits(TextFieldLineLimits::MultiLine(5))
                      .VerticalAlign(TextVerticalAlign::Top)
                      .InputConfiguration(TextInputConfiguration{
                          .type = TextInputType::Text,
                          .capitalization = TextCapitalization::Sentences,
                          .action = TextInputAction::Newline,
                          .multiline = true,
                          .secure = false,
                          .autocorrect = true,
                      })
                      .OnChanged([state](const TextEditingValue &next) {
                        state.Update([&](MemoryEditorState &editor) {
                          editor.content = next;
                        });
                      })
                      .With(Frame{.height = 132.25F}, Enabled(!state->saving)));

  auto save = [tasks, store, state, dialog, memory = std::move(memory),
               project_id = std::move(project_id), strings, toast,
               on_saved = std::move(on_saved)]() mutable {
    if (state->saving)
      return;
    auto content = domain::NormalizeMemoryContent(state->content.text);
    if (content.empty()) {
      toast.Show(strings.empty_toast);
      return;
    }
    state.Update([](MemoryEditorState &next) { next.saving = true; });
    auto pending = memory;
    pending.scope = state->scope;
    pending.project_id = project_id;
    pending.content = std::move(content);
    tasks.Launch([store, state, dialog, pending = std::move(pending), toast,
                  on_saved]() mutable -> Task<void> {
      auto saved = co_await store->SaveManual(std::move(pending));
      if (!saved) {
        state.Update([](MemoryEditorState &next) { next.saving = false; });
        toast.Show(saved.error().message);
        co_return;
      }
      dialog.Dismiss();
      if (on_saved)
        std::invoke(on_saved);
    });
  };

  return DialogPanel(
      editing ? strings.editor_edit : strings.editor_add,
      {Row(std::move(scopes))
           .With(Frame{.min_height = 32.0F},
                 CrossAlign(CrossAxisAlignment::Center)),
       Gap(12.0F), input,
       DialogActions(
           {DialogAction(strings.cancel, colors::secondary,
                         [dialog] { dialog.Dismiss(); })
                .With(Enabled(!state->saving)),
            DialogAction(strings.save, colors::accent, std::move(save))
                .With(Enabled(!state->saving))})});
}

void ShowEditor(const DialogHandle &dialogs,
                std::shared_ptr<application::MemoryStore> store,
                domain::MemoryRecord memory, std::string project_id,
                const ResolvedMemoryStrings &strings,
                std::function<void()> on_saved) {
  dialogs.Show([store = std::move(store), memory = std::move(memory),
                project_id = std::move(project_id), strings,
                on_saved = std::move(on_saved)](DialogContext dialog) mutable {
    return MemoryEditorDialog(dialog, store, memory, project_id, strings,
                              on_saved);
  });
}

void ShowDeleteConfirmation(
    const DialogHandle &dialogs,
    const std::shared_ptr<application::MemoryStore> &store,
    std::vector<std::string> ids, std::string body, std::string project_id,
    State<MemoryPageState> state, const TaskScope &tasks, ToastHandle toast,
    const ResolvedMemoryStrings &strings) {
  dialogs.Show([store, ids = std::move(ids), body = std::move(body),
                project_id = std::move(project_id), state, tasks, toast,
                strings](DialogContext dialog) {
    auto remove = [dialog, store, ids, project_id, state, tasks,
                   toast]() mutable {
      dialog.Dismiss();
      tasks.Launch([store, ids = std::move(ids),
                    project_id = std::move(project_id), state,
                    toast]() mutable {
        return DeleteMemories(store, std::move(ids), std::move(project_id),
                              state, toast);
      });
    };
    return DialogPanel(
        strings.delete_title,
        {DialogBody(body),
         DialogActions({DialogAction(strings.cancel, colors::secondary,
                                     [dialog] { dialog.Dismiss(); }),
                        DialogAction(strings.action_delete, colors::danger,
                                     std::move(remove))})});
  });
}

void ShowMemoryActions(const DialogHandle &dialogs,
                       const std::shared_ptr<application::MemoryStore> &store,
                       domain::MemoryRecord memory, std::string project_id,
                       State<MemoryPageState> state, const TaskScope &tasks,
                       ToastHandle toast, const ResolvedMemoryStrings &strings,
                       std::function<void()> reload) {
  dialogs.Show([dialogs, store, memory = std::move(memory),
                project_id = std::move(project_id), state, tasks, toast,
                strings, reload = std::move(reload)](DialogContext dialog) {
    auto action = [&](std::string text, Color color,
                      std::function<void()> selected) {
      return Text(std::move(text))
          .Style(Label(16.0F, FontWeight::Regular, color))
          .OnClick([dialog, selected = std::move(selected)] {
            dialog.Dismiss();
            std::invoke(selected);
          })
          .With(Padding(EdgeInsets::Symmetric(8.0F, 12.0F)), Focusable(),
                PointerCursor(PointerCursorKind::Hand));
    };
    return DialogPanel(
        strings.action_title,
        {action(strings.action_edit, colors::text,
                [dialogs, store, memory, project_id, strings, reload] {
                  ShowEditor(dialogs, store, memory, project_id, strings,
                             reload);
                }),
         action(strings.action_multi_select, colors::text,
                [state, id = memory.id] {
                  state.Update([&](MemoryPageState &next) {
                    next.selected.clear();
                    next.selected.push_back(id);
                  });
                }),
         action(strings.action_delete, colors::danger,
                [dialogs, store, memory, project_id, state, tasks, toast,
                 strings] {
                  ShowDeleteConfirmation(
                      dialogs, store, {memory.id},
                      strings.delete_prompt + "\n\n" +
                          Preview(memory.content, 120, strings),
                      project_id, state, tasks, toast, strings);
                }),
         action(strings.cancel, colors::secondary, [] {})});
  });
}

} // namespace

[[huxerui::composable]] View
MemoryScreen(std::shared_ptr<application::MemoryStore> store,
             std::string project_id, MemoryScreenPresentation presentation,
             std::string project_display) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  auto state = UseState(MemoryPageState{});
  const ResolvedMemoryStrings strings{
      .title = UseString(presentation.title),
      .long_term = UseString(presentation.long_term),
      .project = UseString(presentation.project),
      .environment = UseString(presentation.environment),
      .short_term = UseString(presentation.short_term),
      .chat_index = UseString(presentation.chat_index),
      .delete_title = UseString(presentation.delete_title),
      .delete_prompt = UseString(presentation.delete_prompt),
      .batch_delete_prefix = UseString(presentation.batch_delete_prefix),
      .batch_delete_suffix = UseString(presentation.batch_delete_suffix),
      .editor_add = UseString(presentation.editor_add),
      .editor_edit = UseString(presentation.editor_edit),
      .scope_user = UseString(presentation.scope_user),
      .scope_project = UseString(presentation.scope_project),
      .scope_environment = UseString(presentation.scope_environment),
      .input_hint = UseString(presentation.input_hint),
      .empty_toast = UseString(presentation.empty_toast),
      .action_title = UseString(presentation.action_title),
      .action_edit = UseString(presentation.action_edit),
      .action_delete = UseString(presentation.action_delete),
      .action_multi_select = UseString(presentation.action_multi_select),
      .selected_prefix = UseString(presentation.selected_prefix),
      .selected_suffix = UseString(presentation.selected_suffix),
      .current_project = UseString(presentation.current_project),
      .project_unselected = UseString(presentation.project_unselected),
      .empty = UseString(presentation.empty),
      .source = UseString(presentation.source),
      .used_prefix = UseString(presentation.used_prefix),
      .used_suffix = UseString(presentation.used_suffix),
      .scope = UseString(presentation.scope),
      .project_field = UseString(presentation.project_field),
      .confidence = UseString(presentation.confidence),
      .use_count = UseString(presentation.use_count),
      .created = UseString(presentation.created),
      .updated = UseString(presentation.updated),
      .last_used = UseString(presentation.last_used),
      .expires = UseString(presentation.expires),
      .title_field = UseString(presentation.title_field),
      .conversation = UseString(presentation.conversation),
      .message = UseString(presentation.message),
      .global = UseString(presentation.global),
      .not_used = UseString(presentation.not_used),
      .no_expiry = UseString(presentation.no_expiry),
      .empty_value = UseString(presentation.empty_value),
      .unknown_time = UseString(presentation.unknown_time),
      .loading = UseString(presentation.loading),
      .retry = UseString(presentation.retry),
      .save = UseString(presentation.save),
      .cancel = UseString(presentation.cancel),
      .close = UseString(presentation.close),
  };

  auto reload = [tasks, store, project_id, state] {
    tasks.Launch([store, project_id, state] {
      return Reload(store, project_id, state);
    });
  };
  Lifecycle([reload] { std::invoke(reload); });

  auto add = [dialogs, store, project_id, strings, reload] {
    ShowEditor(dialogs, store,
               domain::MemoryRecord{.scope = domain::MemoryScope::user},
               project_id, strings, reload);
  };
  auto delete_selected = [dialogs, store, project_id, state, tasks, toast,
                          strings] {
    ShowDeleteConfirmation(dialogs, store, state->selected,
                           strings.batch_delete_prefix +
                               std::to_string(state->selected.size()) +
                               strings.batch_delete_suffix,
                           project_id, state, tasks, toast, strings);
  };

  std::vector<View> content;
  if (state->phase == LoadPhase::loading) {
    content.push_back(
        Text(strings.loading)
            .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(16.0F)));
  } else if (state->phase == LoadPhase::failed) {
    content.push_back(Column{
        Text(state->error)
            .Style(Label(13.0F, FontWeight::Regular, colors::danger)),
        Text(strings.retry)
            .Style(Label(13.0F, FontWeight::Medium, colors::accent))
            .OnClick(reload)
            .With(Padding(EdgeInsets::Symmetric(0.0F, 12.0F)), Focusable(),
                  PointerCursor(PointerCursorKind::Hand)),
    }
                          .With(Padding(16.0F), Spacing(8.0F)));
  } else {
    content.push_back(
        Text(strings.current_project + (!project_display.empty()
                                            ? project_display
                                        : state->overview.project_id.empty()
                                            ? strings.project_unselected
                                            : state->overview.project_id))
            .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(
                EdgeInsets{.top = 12.0F, .right = 16.0F, .left = 16.0F})));

    const std::array memory_sections{
        MemorySectionSpec{&ResolvedMemoryStrings::long_term,
                          &domain::MemoryOverview::long_term,
                          app::images::database},
        MemorySectionSpec{&ResolvedMemoryStrings::project,
                          &domain::MemoryOverview::project,
                          app::images::folder_open},
        MemorySectionSpec{&ResolvedMemoryStrings::environment,
                          &domain::MemoryOverview::environment,
                          app::images::globe},
    };
    for (const auto &section : memory_sections) {
      const auto &records = state->overview.*section.rows;
      const std::string section_title = strings.*section.title;
      std::vector<View> rows;
      if (records.empty()) {
        rows.push_back(EmptyRow(strings));
      } else {
        rows.reserve(records.size() * 2U);
        for (std::size_t index{}; index < records.size(); ++index) {
          const auto memory = records[index];
          const bool multi_select = !state->selected.empty();
          const bool selected = Selected(state.Get(), memory.id);
          auto activate = [state, memory, multi_select, dialogs, strings,
                           section_title] {
            if (multi_select) {
              ToggleSelection(state, memory.id);
              return;
            }
            ShowTextDialog(dialogs, section_title,
                           MemoryDetail(memory, strings), strings.close);
          };
          auto long_press = [state, memory, multi_select, dialogs, store,
                             project_id, tasks, toast, strings, reload] {
            if (multi_select) {
              ToggleSelection(state, memory.id);
              return;
            }
            ShowMemoryActions(dialogs, store, memory, project_id, state, tasks,
                              toast, strings, reload);
          };
          rows.push_back(MemoryRow(memory, section.icon, multi_select, selected,
                                   strings, std::move(activate),
                                   std::move(long_press))
                             .Key(memory.id));
          if (index + 1U < records.size())
            rows.push_back(RowDivider());
        }
      }
      content.push_back(Section(strings.*section.title + "（" +
                                    std::to_string(records.size()) + "）",
                                std::move(rows)));
    }

    std::vector<View> short_rows;
    if (state->overview.short_term.empty()) {
      short_rows.push_back(EmptyRow(strings));
    } else {
      short_rows.reserve(state->overview.short_term.size() * 2U);
      for (std::size_t index{}; index < state->overview.short_term.size();
           ++index) {
        const auto memory = state->overview.short_term[index];
        short_rows.push_back(
            ReadOnlyRow(
                memory, app::images::clock_3,
                [&strings](const auto &item) {
                  return Preview(item.content, 80, strings);
                },
                [&strings](const auto &item) {
                  return Time(item.updated_at, strings);
                },
                [dialogs, memory, strings] {
                  ShowTextDialog(dialogs, strings.short_term,
                                 WorkingDetail(memory, strings), strings.close);
                })
                .Key(memory.id));
        if (index + 1U < state->overview.short_term.size())
          short_rows.push_back(RowDivider());
      }
    }
    content.push_back(
        Section(strings.short_term + "（" +
                    std::to_string(state->overview.short_term.size()) + "）",
                std::move(short_rows)));

    std::vector<View> history_rows;
    if (state->overview.history.empty()) {
      history_rows.push_back(EmptyRow(strings));
    } else {
      history_rows.reserve(state->overview.history.size() * 2U);
      for (std::size_t index{}; index < state->overview.history.size();
           ++index) {
        const auto entry = state->overview.history[index];
        history_rows.push_back(
            ReadOnlyRow(
                entry, app::images::book_open,
                [&strings](const auto &item) {
                  return Preview(item.text, 80, strings);
                },
                [&strings](const auto &item) {
                  return HistoryDescription(item, strings);
                },
                [dialogs, entry, strings] {
                  ShowTextDialog(dialogs, strings.chat_index,
                                 HistoryDetail(entry, strings), strings.close);
                })
                .Key(entry.id));
        if (index + 1U < state->overview.history.size())
          history_rows.push_back(RowDivider());
      }
    }
    content.push_back(
        Section(strings.chat_index + "（" +
                    std::to_string(state->overview.history.size()) + "）",
                std::move(history_rows)));
  }
  content.push_back(Gap(100.0F));

  return Column{
      Header(navigation, state, strings, std::move(add),
             std::move(delete_selected)),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
