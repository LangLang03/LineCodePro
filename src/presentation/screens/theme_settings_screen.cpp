#include "presentation/screens/theme_settings_screen.h"

#include <array>
#include <cstddef>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/theme_settings.h"
#include "domain/app_state.h"
#include "domain/theme_palette.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using domain::ThemeColorDraft;
using domain::ThemeColorRole;
using domain::ThemeMode;
using domain::ThemePalette;

struct OptionMeta final {
  ThemeMode mode;
  StringResource title;
  StringResource description;
  ImageResource icon;
};

struct FieldMeta final {
  ThemeColorRole role;
  StringResource title;
  StringResource description;
};

Color UiColor(domain::PackedColor value) {
  return Color::Rgb(static_cast<int>((value >> 16U) & 0xFFU),
                    static_cast<int>((value >> 8U) & 0xFFU),
                    static_cast<int>(value & 0xFFU),
                    static_cast<float>((value >> 24U) & 0xFFU) / 255.0F);
}

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon).Tint(tint).With(Frame{.width = size, .height = size});
}

std::array<OptionMeta, 9> Options() {
  return {
      {{ThemeMode::system, app::strings::screen_theme_system,
        app::strings::screen_theme_system_desc, app::images::monitor},
       {ThemeMode::light, app::strings::screen_theme_light,
        app::strings::screen_theme_light_desc, app::images::sun},
       {ThemeMode::dark, app::strings::screen_theme_dark,
        app::strings::screen_theme_dark_desc, app::images::moon},
       {ThemeMode::coffee, app::strings::screen_theme_coffee,
        app::strings::screen_theme_coffee_desc, app::images::coffee},
       {ThemeMode::vscode, app::strings::screen_theme_vscode,
        app::strings::screen_theme_vscode_desc, app::images::code},
       {ThemeMode::github_dark, app::strings::screen_theme_github_dark,
        app::strings::screen_theme_github_dark_desc, app::images::git_branch},
       {ThemeMode::gruvbox, app::strings::screen_theme_gruvbox,
        app::strings::screen_theme_gruvbox_desc, app::images::code},
       {ThemeMode::high_contrast, app::strings::screen_theme_high_contrast,
        app::strings::screen_theme_high_contrast_desc, app::images::contrast},
       {ThemeMode::custom, app::strings::screen_theme_custom,
        app::strings::screen_theme_custom_desc, app::images::paintbrush}}};
}

std::array<FieldMeta, domain::theme_color_count> Fields() {
  return {
      {{ThemeColorRole::background, app::strings::screen_theme_color_background,
        app::strings::screen_theme_color_background_desc},
       {ThemeColorRole::surface, app::strings::screen_theme_color_surface,
        app::strings::screen_theme_color_surface_desc},
       {ThemeColorRole::surface_elevated,
        app::strings::screen_theme_color_panel,
        app::strings::screen_theme_color_panel_desc},
       {ThemeColorRole::surface_light,
        app::strings::screen_theme_color_panel_light,
        app::strings::screen_theme_color_panel_light_desc},
       {ThemeColorRole::input_background,
        app::strings::screen_theme_color_input,
        app::strings::screen_theme_color_input_desc},
       {ThemeColorRole::text, app::strings::screen_theme_color_text,
        app::strings::screen_theme_color_text_desc},
       {ThemeColorRole::text_secondary,
        app::strings::screen_theme_color_text_secondary,
        app::strings::screen_theme_color_text_secondary_desc},
       {ThemeColorRole::text_tertiary,
        app::strings::screen_theme_color_text_tertiary,
        app::strings::screen_theme_color_text_tertiary_desc},
       {ThemeColorRole::text_on_color,
        app::strings::screen_theme_color_text_on_color,
        app::strings::screen_theme_color_text_on_color_desc},
       {ThemeColorRole::accent, app::strings::screen_theme_color_accent,
        app::strings::screen_theme_color_accent_desc},
       {ThemeColorRole::user_bubble,
        app::strings::screen_theme_color_user_bubble,
        app::strings::screen_theme_color_user_bubble_desc},
       {ThemeColorRole::ai_bubble, app::strings::screen_theme_color_ai_bubble,
        app::strings::screen_theme_color_ai_bubble_desc},
       {ThemeColorRole::border, app::strings::screen_theme_color_border,
        app::strings::screen_theme_color_border_desc},
       {ThemeColorRole::border_light,
        app::strings::screen_theme_color_border_light,
        app::strings::screen_theme_color_border_light_desc},
       {ThemeColorRole::code_background,
        app::strings::screen_theme_color_code_background,
        app::strings::screen_theme_color_code_background_desc},
       {ThemeColorRole::code_border,
        app::strings::screen_theme_color_code_border,
        app::strings::screen_theme_color_code_border_desc},
       {ThemeColorRole::danger, app::strings::screen_theme_color_danger,
        app::strings::screen_theme_color_danger_desc},
       {ThemeColorRole::warning, app::strings::screen_theme_color_warning,
        app::strings::screen_theme_color_warning_desc},
       {ThemeColorRole::success, app::strings::screen_theme_color_success,
        app::strings::screen_theme_color_success_desc}}};
}

std::array<TextEditingValue, domain::theme_color_count>
EditingValues(const ThemeColorDraft &draft) {
  std::array<TextEditingValue, domain::theme_color_count> result;
  for (std::size_t index = 0; index < result.size(); ++index) {
    result[index] = TextEditingValue::FromText(draft[index]);
  }
  return result;
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_theme_section_themes)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Spacer().With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View OptionRow(const OptionMeta &option, ThemeMode selected,
               State<application::ThemeSettingsState> state,
               std::shared_ptr<application::ThemeSettingsService> service) {
  const bool active = selected == option.mode;
  return Row{
      Glyph(option.icon, 20.0F, active ? colors::accent : colors::secondary),
      Column{
          Text(option.title)
              .Style(Label(16.0F,
                           active ? FontWeight::Medium : FontWeight::Regular,
                           active ? colors::accent : colors::text)),
          Text(option.description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
  }
      .OnClick([mode = option.mode, state, service] {
        state = service->SelectMode(mode);
      })
      .With(Frame{.min_height = 56.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View ThemeModes(State<application::ThemeSettingsState> state,
                std::shared_ptr<application::ThemeSettingsService> service) {
  std::vector<View> rows;
  const auto options = Options();
  for (std::size_t index = 0; index < options.size(); ++index) {
    rows.push_back(
        OptionRow(options[index], state->selected_mode, state, service)
            .Key(options[index].mode));
    if (index + 1 < options.size())
      rows.push_back(Divider());
  }
  return Column{
      Text(app::strings::screen_theme_section_themes)
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(rows))
              .With(CornerRadius(12.0F), Background(colors::elevated),
                    CrossAlign(CrossAxisAlignment::Stretch)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View PaletteChips(const ThemePalette &palette) {
  return Row{
      Spacer().With(Frame{.width = 18.0F, .height = 18.0F},
                    Background(UiColor(palette[ThemeColorRole::background])),
                    CornerRadius(9.0F), Border{colors::border_light, 1.0F}),
      Spacer().With(Frame{.width = 18.0F, .height = 18.0F},
                    Background(UiColor(palette[ThemeColorRole::ai_bubble])),
                    CornerRadius(9.0F), Border{colors::border_light, 1.0F}),
      Spacer().With(Frame{.width = 18.0F, .height = 18.0F},
                    Background(UiColor(palette[ThemeColorRole::accent])),
                    CornerRadius(9.0F), Border{colors::border_light, 1.0F}),
  }
      .With(Spacing(-4.0F));
}

View StarterTile(
    int id, StringResource title, ImageResource icon, ThemePalette palette,
    int selected, State<int> active_starter, State<ThemeColorDraft> draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const bool active = id == selected;
  return Column{
      PaletteChips(palette),
      Glyph(icon, 14.0F, active ? colors::accent : colors::secondary)
          .With(Padding(EdgeInsets{.top = 6.0F})),
      Text(title)
          .Style(Label(11.0F, FontWeight::Bold,
                       active ? colors::accent : colors::secondary))
          .With(Padding(EdgeInsets{.top = 4.0F})),
  }
      .OnClick([id, palette, active_starter, draft, editing] {
        auto next = domain::EditableThemeDraft(palette);
        if (id == 1)
          next[static_cast<std::size_t>(ThemeColorRole::code_background)] =
              "#F2F2F7";
        if (id == 2)
          next[static_cast<std::size_t>(ThemeColorRole::code_background)] =
              "#151515";
        if (id == 3)
          next[static_cast<std::size_t>(ThemeColorRole::code_background)] =
              "#EFE4D4";
        active_starter = id;
        draft = next;
        editing = EditingValues(next);
      })
      .With(Grow(), Padding(8.0F), CornerRadius(8.0F),
            Background(active ? colors::accent_muted : colors::surface),
            Border{active ? colors::accent : colors::border_light, 1.0F},
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View StarterPanel(
    const application::ThemeSettingsState &saved, State<int> active,
    State<ThemeColorDraft> draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  struct Starter final {
    int id;
    ThemeMode mode;
    StringResource title;
    ImageResource icon;
  };
  std::vector<Starter> starters{
      {0, ThemeMode::custom, app::strings::screen_theme_starter_default,
       app::images::paintbrush},
      {1, ThemeMode::light, app::strings::screen_theme_starter_light,
       app::images::sun},
      {2, ThemeMode::dark, app::strings::screen_theme_starter_dark,
       app::images::moon},
      {3, ThemeMode::coffee, app::strings::screen_theme_starter_coffee,
       app::images::coffee},
      {4, ThemeMode::vscode, app::strings::screen_theme_starter_vscode,
       app::images::code},
      {5, ThemeMode::github_dark, app::strings::screen_theme_starter_github,
       app::images::git_branch},
      {6, ThemeMode::gruvbox, app::strings::screen_theme_starter_gruvbox,
       app::images::code},
      {7, ThemeMode::high_contrast,
       app::strings::screen_theme_starter_high_contrast, app::images::contrast},
  };
  if (saved.has_saved_custom_colors) {
    starters.push_back({8, ThemeMode::custom,
                        app::strings::screen_theme_starter_saved,
                        app::images::save});
  }
  std::vector<View> grid;
  for (std::size_t row = 0; row < (starters.size() + 2) / 3; ++row) {
    std::vector<View> tiles;
    for (std::size_t column = 0; column < 3; ++column) {
      const auto index = row * 3 + column;
      if (index >= starters.size()) {
        tiles.push_back(Spacer().With(Grow()));
        continue;
      }
      const auto &starter = starters[index];
      auto palette = starter.id == 8
                         ? domain::ApplyThemeDraft(
                               domain::PaletteForMode(ThemeMode::custom),
                               saved.custom_colors)
                         : domain::PaletteForMode(starter.mode);
      tiles.push_back(StarterTile(starter.id, starter.title, starter.icon,
                                  palette, active.Get(), active, draft, editing)
                          .Key(starter.id));
    }
    grid.push_back(Row(std::move(tiles)).With(Spacing(8.0F)));
  }
  return Column{
      Text(app::strings::screen_theme_starter_section)
          .Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
      Column(std::move(grid)).With(Spacing(8.0F)),
  }
      .With(Spacing(8.0F), Padding(12.0F), CornerRadius(12.0F),
            Background(colors::elevated));
}

View Preview(const ThemePalette &palette) {
  return Column{
      Column{
          Text(app::strings::screen_theme_section_preview)
              .Style(Label(16.0F, FontWeight::Bold,
                           UiColor(palette[ThemeColorRole::text]))),
          Text(app::strings::screen_theme_section_preview_desc)
              .Style(Label(13.0F, FontWeight::Regular,
                           UiColor(palette[ThemeColorRole::text_secondary])))
              .With(Padding(EdgeInsets{.top = 4.0F})),
      }
          .With(Padding(12.0F), CornerRadius(8.0F),
                Background(UiColor(palette[ThemeColorRole::ai_bubble]))),
      Text(app::strings::screen_theme_color_accent)
          .Style(Label(11.0F, FontWeight::Bold,
                       UiColor(palette[ThemeColorRole::text_on_color])))
          .With(Padding(EdgeInsets::Symmetric(12.0F, 4.0F)),
                CornerRadius(999.0F),
                Background(UiColor(palette[ThemeColorRole::accent]))),
  }
      .With(Spacing(12.0F), Padding(12.0F), CornerRadius(12.0F),
            Background(UiColor(palette[ThemeColorRole::background])),
            Border{UiColor(palette[ThemeColorRole::border]), 1.0F},
            CrossAlign(CrossAxisAlignment::Start));
}

constexpr std::array<std::string_view, 32> kSwatches{
    "#F4EFE6", "#FBF7EF", "#EEE5D8", "#E7DCCA", "#2B2118", "#6C5A49", "#9B8976",
    "#D97757", "#B86F50", "#EFE4D4", "#DDD0BF", "#6A7F46", "#0A0A0A", "#1C1C1E",
    "#FFFFFF", "#0A84FF", "#1E1E1E", "#252526", "#007ACC", "#D4D4D4", "#0D1117",
    "#161B22", "#2F81F7", "#E6EDF3", "#282828", "#FABD2F", "#EBDBB2", "#458588",
    "#64D2FF", "#FFD60A", "#30D158", "#FF453A"};

View SwatchPanel(
    const FieldMeta &active_field, State<ThemeColorRole> active_role,
    State<int> active_starter, State<ThemeColorDraft> draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const auto role_index = static_cast<std::size_t>(active_role.Get());
  std::vector<View> lines;
  for (std::size_t row = 0; row < 5; ++row) {
    std::vector<View> swatches;
    for (std::size_t column = 0; column < 7; ++column) {
      const auto index = row * 7 + column;
      if (index >= kSwatches.size()) {
        swatches.push_back(
            Spacer().With(Frame{.width = 34.0F, .height = 34.0F}));
        continue;
      }
      const std::string value(kSwatches[index]);
      const bool selected = draft->at(role_index) == value;
      const auto color = UiColor(*domain::ParseHexColor(value));
      swatches.push_back(
          Stack{selected ? Glyph(app::images::check, 14.0F, Color::White())
                         : Spacer()}
              .OnClick([value, role_index, active_starter, draft, editing] {
                draft.Update(
                    [&](ThemeColorDraft &next) { next[role_index] = value; });
                editing.Update([&](auto &next) {
                  next[role_index] = TextEditingValue::FromText(value);
                });
                active_starter = -1;
              })
              .With(
                  Frame{.width = 34.0F, .height = 34.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  CornerRadius(17.0F), Background(color),
                  Border{selected ? colors::accent : colors::border_light,
                         1.0F},
                  Focusable(), PointerCursor(PointerCursorKind::Hand)));
    }
    lines.push_back(Row(std::move(swatches)).With(Spacing(8.0F)));
  }
  return Column{
      Row{
          Text(app::strings::screen_theme_current_editing)
              .Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
          Text(active_field.title)
              .Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
      }
          .With(Spacing(4.0F)),
      Column(std::move(lines)).With(Spacing(8.0F)),
  }
      .With(Spacing(8.0F), Padding(12.0F), CornerRadius(12.0F),
            Background(colors::elevated));
}

View EditorRow(
    const FieldMeta &field, std::size_t index,
    State<ThemeColorRole> active_role, State<int> active_starter,
    State<ThemeColorDraft> draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const bool active = active_role.Get() == field.role;
  const bool valid = domain::IsHexColor(draft->at(index));
  const auto preview = valid ? UiColor(*domain::ParseHexColor(draft->at(index)))
                             : colors::surface_light;
  return Row{
      Spacer().With(Frame{.width = 30.0F, .height = 30.0F}, Background(preview),
                    CornerRadius(15.0F), Border{colors::border_light, 1.0F}),
      Column{
          Text(field.title).Style(Label(16.0F, FontWeight::Medium)),
          Text(valid ? StringVariant(field.description)
                     : StringVariant(app::strings::screen_theme_color_hex_hint))
              .Style(Label(11.0F, FontWeight::Regular,
                           valid ? colors::tertiary : colors::danger)),
      }
          .With(Spacing(2.0F), Grow()),
      TextField(editing->at(index))
          .Placeholder(app::strings::screen_theme_color_hex_placeholder)
          .LineLimits(TextFieldLineLimits::SingleLine())
          .MaxLength(9)
          .Validation(valid ? ValidationResult::None()
                            : ValidationResult::Invalid(
                                  app::strings::screen_theme_color_hex_hint))
          .OnChanged([index, role = field.role, active_role, active_starter,
                      draft, editing](const TextEditingValue &proposed) {
            auto next = proposed;
            std::string normalized = proposed.text;
            if (!normalized.empty() && normalized.front() != '#') {
              normalized.insert(normalized.begin(), '#');
              next = TextEditingValue::FromText(normalized);
            }
            editing.Update([&](auto &values) { values[index] = next; });
            draft.Update(
                [&](ThemeColorDraft &values) { values[index] = normalized; });
            active_role = role;
            active_starter = -1;
          })
          .With(Frame{.width = 92.0F, .height = 38.0F}, CornerRadius(8.0F),
                Background(colors::surface_light),
                Border{valid ? colors::border_light : colors::danger, 1.0F},
                Padding(EdgeInsets::Symmetric(8.0F, 0.0F))),
  }
      .OnClick([role = field.role, active_role] { active_role = role; })
      .With(Frame{.min_height = 66.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(12.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View EditorGroup(
    State<ThemeColorRole> active_role, State<int> active_starter,
    State<ThemeColorDraft> draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const auto fields = Fields();
  std::vector<View> rows;
  for (std::size_t index = 0; index < fields.size(); ++index) {
    rows.push_back(EditorRow(fields[index], index, active_role, active_starter,
                             draft, editing)
                       .Key(fields[index].role));
    if (index + 1 < fields.size()) {
      rows.push_back(Divider().With(Padding(EdgeInsets{.left = 58.0F})));
    }
  }
  return Column(std::move(rows))
      .With(CornerRadius(12.0F), Background(colors::elevated),
            CrossAlign(CrossAxisAlignment::Stretch));
}

} // namespace

[[huxerui::composable]] View
ThemeSettingsScreen(std::shared_ptr<application::ThemeSettingsService> service,
                    State<application::ThemeSettingsState> settings) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto toast = UseToast();
  auto draft = UseState(settings->custom_colors);
  auto editing = UseState(EditingValues(settings->custom_colors));
  auto active_role = UseState(ThemeColorRole::accent);
  auto active_starter = UseState(settings->has_saved_custom_colors ? 8 : 0);
  const auto preview = domain::ApplyThemeDraft(
      domain::PaletteForMode(ThemeMode::custom), draft.Get());
  const bool valid = domain::IsValidThemeDraft(draft.Get());
  const auto active_index = static_cast<std::size_t>(active_role.Get());
  const auto fields = Fields();

  View custom_header =
      Row{
          Text(app::strings::screen_theme_custom_colors)
              .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
              .With(Grow()),
          Stack{Glyph(app::images::rotate_ccw, 15.0F, colors::secondary)}
              .OnClick([draft, editing, active_starter] {
                auto reset = domain::EditableThemeDraft(
                    domain::PaletteForMode(ThemeMode::custom));
                draft = reset;
                editing = EditingValues(reset);
                active_starter = 0;
              })
              .With(
                  Frame{.width = 34.0F, .height = 34.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  CornerRadius(17.0F), Background(colors::surface_light),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
          Row{Glyph(app::images::save, 15.0F,
                    valid ? colors::text_on_color : colors::tertiary),
              Text(app::strings::screen_theme_color_save)
                  .Style(
                      Label(13.0F, FontWeight::Bold,
                            valid ? colors::text_on_color : colors::tertiary))}
              .OnClick(
                  [valid, service, settings, draft, active_starter, toast] {
                    if (!valid) {
                      toast.Show(app::strings::screen_theme_color_invalid);
                      return;
                    }
                    settings = service->SaveCustomColors(draft.Get());
                    active_starter = 8;
                  })
              .With(Frame{.height = 34.0F}, Spacing(4.0F),
                    Padding(EdgeInsets::Symmetric(12.0F, 0.0F)),
                    CrossAlign(CrossAxisAlignment::Center), CornerRadius(17.0F),
                    Background(valid ? colors::accent : colors::surface_light),
                    Enabled(valid), Focusable(),
                    PointerCursor(PointerCursorKind::Hand)),
      }
          .With(
              Spacing(8.0F),
              Padding(EdgeInsets{
                  .top = 20.0F, .right = 16.0F, .bottom = 8.0F, .left = 16.0F}),
              CrossAlign(CrossAxisAlignment::Center));

  return Column{
      Header(navigation),
      Divider(),
      ScrollView(
          Column{
              ThemeModes(settings, service),
              custom_header,
              Column{
                  StarterPanel(settings.Get(), active_starter, draft, editing),
                  Preview(preview),
                  SwatchPanel(fields[active_index], active_role, active_starter,
                              draft, editing),
                  EditorGroup(active_role, active_starter, draft, editing),
              }
                  .With(Spacing(12.0F),
                        Padding(EdgeInsets::Symmetric(16.0F, 0.0F)),
                        CrossAlign(CrossAxisAlignment::Stretch)),
              Spacer().With(Frame{.height = 100.0F}),
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
