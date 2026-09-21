#include "gtest_support.h"
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <string_view>

#include "application/theme_settings.h"
#include "domain/theme_palette.h"

namespace {

class MemorySettings final : public linecode::application::SettingsStore {
public:
  std::optional<std::string> Read(std::string_view key) const override {
    const auto found = values.find(key);
    return found == values.end() ? std::nullopt
                                 : std::optional<std::string>{found->second};
  }

  void Write(std::string_view key, std::string value) override {
    values.insert_or_assign(std::string(key), std::move(value));
  }

  std::map<std::string, std::string, std::less<>> values;
};

class DarkSystem final : public linecode::application::SystemThemeSource {
public:
  bool IsDarkModeEnabled() const override { return true; }
};

} // namespace

TEST(theme_settings_tests, LegacySuite) {
  using namespace linecode;
  auto settings = std::make_shared<MemorySettings>();
  auto system = std::make_shared<DarkSystem>();
  application::ThemeSettingsRepository repository(settings, system);

  const auto initial = repository.Load();
  EXPECT_EXPRESSION(initial.selected_mode == domain::ThemeMode::system);
  EXPECT_EXPRESSION(initial.resolved_mode == domain::ThemeMode::dark);
  EXPECT_EXPRESSION(initial.palette == domain::PaletteForMode(domain::ThemeMode::dark));
  EXPECT_EXPRESSION(initial.palette[domain::ThemeColorRole::accent_dim] == 0xFF353A40U);
  EXPECT_EXPRESSION(initial.palette[domain::ThemeColorRole::accent_muted] == 0x14E5E9EEU);
  EXPECT_EXPRESSION(initial.palette[domain::ThemeColorRole::overlay] == 0x73000000U);
  EXPECT_EXPRESSION(initial.palette[domain::ThemeColorRole::code_border] == 0xFF383D42U);
  EXPECT_EXPRESSION(initial.palette[domain::ThemeColorRole::diff_add_background] ==
         0xFF22322AU);

  const auto coffee = repository.SelectMode(domain::ThemeMode::coffee);
  EXPECT_EXPRESSION(coffee.selected_mode == domain::ThemeMode::coffee);
  EXPECT_EXPRESSION(coffee.palette[domain::ThemeColorRole::background] == 0xFFF4EFE6U);
  // Coffee-paper code and tool output use the same light paper swatch as the
  // built-in coffee custom-theme starter, keeping dark monospace text legible.
  EXPECT_EXPRESSION(coffee.palette[domain::ThemeColorRole::code_background] ==
         0xFFEFE4D4U);
  EXPECT_EXPRESSION(coffee.palette[domain::ThemeColorRole::code_border] == 0xFF7A5A3AU);

  auto custom = domain::EditableThemeDraft(
      domain::PaletteForMode(domain::ThemeMode::custom));
  custom[static_cast<std::size_t>(domain::ThemeColorRole::accent)] = "#123456";
  const auto saved = repository.SaveCustomColors(custom);
  EXPECT_EXPRESSION(saved.selected_mode == domain::ThemeMode::custom);
  EXPECT_EXPRESSION(saved.has_saved_custom_colors);
  EXPECT_EXPRESSION(saved.palette[domain::ThemeColorRole::accent] == 0xFF123456U);
  // The legacy custom editor owns only 19 colors. Runtime-only roles retain
  // the original custom/light palette values instead of being guessed from
  // the edited accent.
  EXPECT_EXPRESSION(saved.palette[domain::ThemeColorRole::accent_dim] == 0xFFE5E8ECU);
  EXPECT_EXPRESSION(saved.palette[domain::ThemeColorRole::diff_delete_text] ==
         0xFF9C5058U);

  EXPECT_EXPRESSION(domain::IsHexColor("#AABBCC"));
  EXPECT_EXPRESSION(domain::IsHexColor("#80AABBCC"));
  EXPECT_EXPRESSION(!domain::IsHexColor("AABBCC"));
}
