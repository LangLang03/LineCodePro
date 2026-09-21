#include "presentation/screens/theme_settings_screen.h"

#include <array>
#include <cctype>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/theme_settings.h"
#include "domain/app_state.h"
#include "domain/theme_palette.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/legacy_text_presentation.h"
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
  float minimum_height;
  float vertical_padding;
};

struct FieldMeta final {
  ThemeColorRole role;
  StringResource title;
  StringResource description;
};

enum class StarterId : std::uint8_t {
  default_theme,
  light,
  dark,
  coffee,
  vscode,
  github_dark,
  gruvbox,
  high_contrast,
  saved,
};

using StarterPaletteFactory =
    ThemePalette (*)(const application::ThemeSettingsState &);

struct Starter final {
  StarterId id;
  StringResource title;
  ImageResource icon;
  StarterPaletteFactory palette;
};

template <ThemeMode Mode, const char *CodeBackground = nullptr>
ThemePalette BuiltInStarterPalette(const application::ThemeSettingsState &) {
  auto palette = domain::PaletteForMode(Mode);
  if constexpr (CodeBackground != nullptr) {
    palette[ThemeColorRole::code_background] =
        *domain::ParseHexColor(CodeBackground);
  }
  return palette;
}

inline constexpr char kLightCodeBackground[] = "#F2F2F7";
inline constexpr char kDarkCodeBackground[] = "#151515";
inline constexpr char kCoffeeCodeBackground[] = "#EFE4D4";

ThemePalette SavedStarterPalette(const application::ThemeSettingsState &saved) {
  return domain::ApplyThemeDraft(domain::PaletteForMode(ThemeMode::custom),
                                 saved.custom_colors);
}

Color UiColor(domain::PackedColor value, domain::PackedColor background) {
  return LineColorForPacked(value, background);
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
        app::strings::screen_theme_system_desc, app::images::monitor, 64.76F,
        12.0F},
       {ThemeMode::light, app::strings::screen_theme_light,
        app::strings::screen_theme_light_desc, app::images::sun, 64.76F,
        12.0F},
       {ThemeMode::dark, app::strings::screen_theme_dark,
        app::strings::screen_theme_dark_desc, app::images::moon, 64.76F,
        12.0F},
       {ThemeMode::coffee, app::strings::screen_theme_coffee,
        app::strings::screen_theme_coffee_desc, app::images::coffee, 64.76F,
        12.0F},
       {ThemeMode::vscode, app::strings::screen_theme_vscode,
        app::strings::screen_theme_vscode_desc, app::images::code, 60.19F,
        10.5F},
       {ThemeMode::github_dark, app::strings::screen_theme_github_dark,
        app::strings::screen_theme_github_dark_desc, app::images::git_branch,
        60.19F, 10.5F},
       {ThemeMode::gruvbox, app::strings::screen_theme_gruvbox,
        app::strings::screen_theme_gruvbox_desc, app::images::code, 60.19F,
        10.5F},
       {ThemeMode::high_contrast, app::strings::screen_theme_high_contrast,
        app::strings::screen_theme_high_contrast_desc, app::images::contrast,
        64.76F, 12.0F},
       {ThemeMode::custom, app::strings::screen_theme_custom,
        app::strings::screen_theme_custom_desc, app::images::paintbrush,
        64.76F, 12.0F}}};
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

std::string NormalizeHexDraftText(std::string_view value) {
  while (!value.empty() &&
         std::isspace(static_cast<unsigned char>(value.front())) != 0)
    value.remove_prefix(1);
  while (!value.empty() &&
         std::isspace(static_cast<unsigned char>(value.back())) != 0)
    value.remove_suffix(1);
  std::string normalized(value);
  if (!normalized.empty() && normalized.front() != '#')
    normalized.insert(normalized.begin(), '#');
  return normalized;
}

TextFieldStyle ThemeColorFieldStyle(bool valid) {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Standard;
  style.show_label = false;
  style.standard.background = colors::surface_light;
  style.standard.border = valid ? colors::border_light : colors::danger;
  style.standard.hovered_border = valid ? colors::border_light : colors::danger;
  style.standard.focused_border = valid ? colors::border_light : colors::danger;
  style.standard.corner_radii = CornerRadii{8.0F};
  style.standard.minimum_height = 38.0F;
  style.text_style =
      TextStyle{Font::Monospace(13.0F), valid ? colors::text : colors::danger};
  style.placeholder_style = TextStyle{Font::Monospace(13.0F), colors::tertiary};
  style.padding = EdgeInsets::Symmetric(8.0F, 0.0F);
  style.caret = valid ? colors::accent : colors::danger;
  style.error_caret = colors::danger;
  style.selection = colors::accent_muted_strong;
  style.focused_border_width = 1.0F;
  return style;
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
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
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
      .With(Frame{.min_height = option.minimum_height}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, option.vertical_padding)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View ThemeModes(State<application::ThemeSettingsState> state,
                std::shared_ptr<application::ThemeSettingsService> service,
                std::string section_title) {
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
      Text(std::move(section_title))
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Frame{.height = 47.625F},
                Padding(EdgeInsets{.top = 20.0F,
                                   .right = 16.0F,
                                   .bottom = 12.0F,
                                   .left = 16.0F})),
      LegacySettingsCardFrame{
          Column(std::move(rows))
              .With(CornerRadius(12.0F), Background(colors::elevated),
                    CrossAlign(CrossAxisAlignment::Stretch)),
      },
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View PaletteChips(const ThemePalette &palette) {
  const auto background = palette[ThemeColorRole::background];
  constexpr auto legacy_chip_border = Color::Rgb(0, 0, 0, 0.125F);
  return Row{
      Stack{}.With(
          Frame{.width = 18.0F, .height = 18.0F},
          Background(UiColor(palette[ThemeColorRole::background], background)),
          CornerRadius(9.0F), Border{legacy_chip_border, 1.0F}),
      Stack{}.With(
          Frame{.width = 18.0F, .height = 18.0F},
          Background(UiColor(palette[ThemeColorRole::ai_bubble], background)),
          CornerRadius(9.0F), Border{legacy_chip_border, 1.0F}),
      Stack{}.With(
          Frame{.width = 18.0F, .height = 18.0F},
          Background(UiColor(palette[ThemeColorRole::accent], background)),
          CornerRadius(9.0F), Border{legacy_chip_border, 1.0F}),
  }
      .With(Spacing(-4.0F));
}

View StarterTile(
    StarterId id, StringResource title, ImageResource icon,
    ThemePalette palette, std::optional<StarterId> selected,
    State<std::optional<StarterId>> active_starter,
    State<ThemeColorDraft> draft, State<ThemeColorDraft> preview_draft,
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
      .OnClick([id, palette, active_starter, draft, preview_draft, editing] {
        auto next = domain::EditableThemeDraft(palette);
        active_starter = id;
        draft = next;
        preview_draft = next;
        editing = EditingValues(next);
      })
      .With(Grow(), Padding(8.0F), CornerRadius(8.0F),
            Background(active ? colors::accent_muted : colors::surface),
            Border{active ? colors::accent : colors::border_light, 1.0F},
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View StarterPanel(
    const application::ThemeSettingsState &saved,
    State<std::optional<StarterId>> active, State<ThemeColorDraft> draft,
    State<ThemeColorDraft> preview_draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  std::vector<Starter> starters{
      {StarterId::default_theme, app::strings::screen_theme_starter_default,
       app::images::paintbrush, &BuiltInStarterPalette<ThemeMode::custom>},
      {StarterId::light, app::strings::screen_theme_starter_light,
       app::images::sun,
       &BuiltInStarterPalette<ThemeMode::light, kLightCodeBackground>},
      {StarterId::dark, app::strings::screen_theme_starter_dark,
       app::images::moon,
       &BuiltInStarterPalette<ThemeMode::dark, kDarkCodeBackground>},
      {StarterId::coffee, app::strings::screen_theme_starter_coffee,
       app::images::coffee,
       &BuiltInStarterPalette<ThemeMode::coffee, kCoffeeCodeBackground>},
      {StarterId::vscode, app::strings::screen_theme_starter_vscode,
       app::images::code, &BuiltInStarterPalette<ThemeMode::vscode>},
      {StarterId::github_dark, app::strings::screen_theme_starter_github,
       app::images::git_branch, &BuiltInStarterPalette<ThemeMode::github_dark>},
      {StarterId::gruvbox, app::strings::screen_theme_starter_gruvbox,
       app::images::code, &BuiltInStarterPalette<ThemeMode::gruvbox>},
      {StarterId::high_contrast,
       app::strings::screen_theme_starter_high_contrast, app::images::contrast,
       &BuiltInStarterPalette<ThemeMode::high_contrast>},
  };
  if (saved.has_saved_custom_colors) {
    starters.push_back({StarterId::saved,
                        app::strings::screen_theme_starter_saved,
                        app::images::save, &SavedStarterPalette});
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
      auto palette = std::invoke(starter.palette, saved);
      tiles.push_back(StarterTile(starter.id, starter.title, starter.icon,
                                  palette, active.Get(), active, draft,
                                  preview_draft, editing)
                          .Key(std::to_underlying(starter.id)));
    }
    // Legacy code adds an 8dp right margin to every grid cell, including the
    // last column, so the three columns share the width left over from that
    // trailing margin.
    grid.push_back(
        Row(std::move(tiles))
            .With(Spacing(8.0F), Padding(EdgeInsets{.right = 8.0F})));
  }
  return Column{
      Text(app::strings::screen_theme_starter_section)
          .Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
      // Legacy grid carries its own 8dp top margin on top of the cell margin.
      Column(std::move(grid))
          .With(Spacing(8.0F), Padding(EdgeInsets{.top = 8.0F})),
  }
      .With(Spacing(8.0F), Padding(12.0F), CornerRadius(12.0F),
            Background(colors::elevated));
}

View Preview(const ThemePalette &palette) {
  const auto background = palette[ThemeColorRole::background];
  return Column{
      Column{
          Text(app::strings::screen_theme_section_preview)
              .Style(Label(16.0F, FontWeight::Bold,
                           UiColor(palette[ThemeColorRole::text], background))),
          Text(app::strings::screen_theme_section_preview_desc)
              .Style(Label(
                  13.0F, FontWeight::Regular,
                  UiColor(palette[ThemeColorRole::text_secondary], background)))
              .With(Padding(EdgeInsets{.top = 4.0F})),
      }
          .With(Padding(12.0F), CornerRadius(8.0F),
                Background(
                    UiColor(palette[ThemeColorRole::ai_bubble], background))),
      Text(app::strings::screen_theme_color_accent)
          .Style(Label(
              11.0F, FontWeight::Bold,
              UiColor(palette[ThemeColorRole::text_on_color], background)))
          .With(
              Padding(EdgeInsets::Symmetric(12.0F, 4.0F)), CornerRadius(999.0F),
              Background(UiColor(palette[ThemeColorRole::accent], background))),
  }
      .With(
          Spacing(12.0F), Padding(12.0F), CornerRadius(12.0F),
          Background(UiColor(palette[ThemeColorRole::background], background)),
          Border{UiColor(palette[ThemeColorRole::border], background), 1.0F},
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
    State<std::optional<StarterId>> active_starter,
    State<ThemeColorDraft> draft, State<ThemeColorDraft> preview_draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const auto role_index = static_cast<std::size_t>(active_role.Get());
  std::vector<View> lines;
  for (std::size_t row = 0; row < 5; ++row) {
    std::vector<View> swatches;
    for (std::size_t column = 0; column < 7; ++column) {
      const auto index = row * 7 + column;
      if (index >= kSwatches.size()) {
        swatches.push_back(
            Stack{}.With(Frame{.width = 34.0F, .height = 34.0F}));
        continue;
      }
      const std::string value(kSwatches[index]);
      const bool selected = draft->at(role_index) == value;
      const auto background = domain::ParseHexColor(
          draft->at(static_cast<std::size_t>(ThemeColorRole::background)));
      const auto fallback_background =
          domain::PaletteForMode(ThemeMode::custom)[ThemeColorRole::background];
      const auto color = UiColor(*domain::ParseHexColor(value),
                                 background.value_or(fallback_background));
      swatches.push_back(
          Stack{selected
                    ? Glyph(app::images::check, 14.0F, colors::text_on_color)
                    : Spacer()}
              .OnClick([value, role_index, active_starter, draft, preview_draft,
                        editing] {
                auto next = draft.Get();
                next[role_index] = value;
                draft = next;
                preview_draft = next;
                editing.Update([&](auto &next) {
                  next[role_index] = TextEditingValue::FromText(value);
                });
                active_starter = std::nullopt;
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
          .With(Spacing(0.0F)),
      Column(std::move(lines))
          .With(Spacing(8.0F), Padding(EdgeInsets{.top = 8.0F})),
  }
      .With(Spacing(8.0F), Padding(12.0F), CornerRadius(12.0F),
            Background(colors::elevated));
}

View EditorRow(
    const FieldMeta &field, std::size_t index,
    State<ThemeColorRole> active_role,
    State<std::optional<StarterId>> active_starter,
    State<ThemeColorDraft> draft, State<ThemeColorDraft> preview_draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const bool active = active_role.Get() == field.role;
  const bool valid = domain::IsHexColor(draft->at(index));
  const auto background = domain::ParseHexColor(
      draft->at(static_cast<std::size_t>(ThemeColorRole::background)));
  const auto fallback_background =
      domain::PaletteForMode(ThemeMode::custom)[ThemeColorRole::background];
  const auto preview = valid ? UiColor(*domain::ParseHexColor(draft->at(index)),
                                       background.value_or(fallback_background))
                             : colors::surface_light;
  ThemeDefinition field_theme;
  field_theme.Set(ThemeColorFieldStyle(valid));
  return Row{
      Stack{}.With(Frame{.width = 30.0F, .height = 30.0F}, Background(preview),
                   CornerRadius(15.0F), Border{colors::border_light, 1.0F}),
      Column{
          Text(field.title).Style(Label(16.0F, FontWeight::Medium)),
          Text(valid ? StringVariant(field.description)
                     : StringVariant(app::strings::screen_theme_color_hex_hint))
              .Style(Label(11.0F, FontWeight::Regular,
                           valid ? colors::tertiary : colors::danger)),
      }
          .With(Spacing(2.0F), Grow()),
      Theme(field_theme,
            TextField(editing->at(index))
                .Placeholder(app::strings::screen_theme_color_hex_placeholder)
                .LineLimits(TextFieldLineLimits::SingleLine())
                .MaxLength(9)
                .InputConfiguration(TextInputConfiguration{
                    .type = TextInputType::Text,
                    .capitalization = TextCapitalization::Characters,
                    .action = TextInputAction::Done,
                    .multiline = false,
                    .secure = false,
                    .autocorrect = false,
                })
                .OnChanged([index, role = field.role, active_role,
                            active_starter, draft, preview_draft,
                            editing](const TextEditingValue &proposed) {
                  editing.Update(
                      [&](auto &values) { values[index] = proposed; });
            auto next = draft.Get();
            next[index] = NormalizeHexDraftText(proposed.text);
            draft = next;
            // Legacy preview freezes only while the field currently being
            // edited is invalid. Other invalid fields fall back to the custom
            // base palette when this active field becomes valid.
            if (domain::IsHexColor(next[index]))
              preview_draft = next;
                  active_role = role;
                  active_starter = std::nullopt;
                })
                .With(Frame{.width = 92.0F, .height = 38.0F})),
  }
      .OnClick([role = field.role, active_role] { active_role = role; })
      .With(Frame{.min_height = 66.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(12.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View EditorGroup(
    State<ThemeColorRole> active_role,
    State<std::optional<StarterId>> active_starter,
    State<ThemeColorDraft> draft, State<ThemeColorDraft> preview_draft,
    State<std::array<TextEditingValue, domain::theme_color_count>> editing) {
  const auto fields = Fields();
  std::vector<View> rows;
  for (std::size_t index = 0; index < fields.size(); ++index) {
    rows.push_back(EditorRow(fields[index], index, active_role, active_starter,
                             draft, preview_draft, editing)
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
  auto preview_draft = UseState(settings->custom_colors);
  auto editing = UseState(EditingValues(settings->custom_colors));
  auto active_role = UseState(ThemeColorRole::accent);
  auto active_starter = UseState(std::optional{settings->has_saved_custom_colors
                                                   ? StarterId::saved
                                                   : StarterId::default_theme});
  const auto preview = domain::ApplyThemeDraft(
      domain::PaletteForMode(ThemeMode::custom), preview_draft.Get());
  const bool valid = domain::IsValidThemeDraft(draft.Get());
  const auto active_index = static_cast<std::size_t>(active_role.Get());
  const auto fields = Fields();
  const auto themes_title =
      LegacySectionTitle(UseString(app::strings::screen_theme_section_themes));
  const auto custom_colors_title =
      LegacySectionTitle(UseString(app::strings::screen_theme_custom_colors));

  View custom_header =
      Row{
          Text(custom_colors_title)
              .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
              .With(Grow()),
          Stack{Glyph(app::images::rotate_ccw, 15.0F, colors::secondary)}
              .OnClick([draft, preview_draft, editing, active_starter] {
                auto reset = domain::EditableThemeDraft(
                    domain::PaletteForMode(ThemeMode::custom));
                draft = reset;
                preview_draft = reset;
                editing = EditingValues(reset);
                active_starter = StarterId::default_theme;
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
                    active_starter = StarterId::saved;
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
      LegacyScreenHeaderDivider(),
      ScrollView(
          Column{
              ThemeModes(settings, service, themes_title),
              custom_header,
              Column{
                  StarterPanel(settings.Get(), active_starter, draft,
                               preview_draft, editing),
                  Preview(preview),
                  SwatchPanel(fields[active_index], active_role, active_starter,
                              draft, preview_draft, editing),
                  EditorGroup(active_role, active_starter, draft, preview_draft,
                              editing),
              }
                  .With(Spacing(12.0F),
                        Padding(EdgeInsets::Symmetric(16.0F, 0.0F)),
                        CrossAlign(CrossAxisAlignment::Stretch)),
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
