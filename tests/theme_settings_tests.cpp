#include <cassert>
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

int main() {
  using namespace linecode;
  auto settings = std::make_shared<MemorySettings>();
  auto system = std::make_shared<DarkSystem>();
  application::ThemeSettingsRepository repository(settings, system);

  const auto initial = repository.Load();
  assert(initial.selected_mode == domain::ThemeMode::system);
  assert(initial.resolved_mode == domain::ThemeMode::dark);
  assert(initial.palette == domain::PaletteForMode(domain::ThemeMode::dark));
  assert(initial.palette[domain::ThemeColorRole::accent_dim] == 0xFF353A40U);
  assert(initial.palette[domain::ThemeColorRole::accent_muted] == 0x14E5E9EEU);
  assert(initial.palette[domain::ThemeColorRole::overlay] == 0x73000000U);
  assert(initial.palette[domain::ThemeColorRole::code_border] == 0xFF383D42U);
  assert(initial.palette[domain::ThemeColorRole::diff_add_background] ==
         0xFF22322AU);

  const auto coffee = repository.SelectMode(domain::ThemeMode::coffee);
  assert(coffee.selected_mode == domain::ThemeMode::coffee);
  assert(coffee.palette[domain::ThemeColorRole::background] == 0xFFF4EFE6U);

  auto custom = domain::EditableThemeDraft(
      domain::PaletteForMode(domain::ThemeMode::custom));
  custom[static_cast<std::size_t>(domain::ThemeColorRole::accent)] = "#123456";
  const auto saved = repository.SaveCustomColors(custom);
  assert(saved.selected_mode == domain::ThemeMode::custom);
  assert(saved.has_saved_custom_colors);
  assert(saved.palette[domain::ThemeColorRole::accent] == 0xFF123456U);
  // The legacy custom editor owns only 19 colors. Runtime-only roles retain
  // the original custom/light palette values instead of being guessed from
  // the edited accent.
  assert(saved.palette[domain::ThemeColorRole::accent_dim] == 0xFFE5E8ECU);
  assert(saved.palette[domain::ThemeColorRole::diff_delete_text] ==
         0xFF9C5058U);

  assert(domain::IsHexColor("#AABBCC"));
  assert(domain::IsHexColor("#80AABBCC"));
  assert(!domain::IsHexColor("AABBCC"));
}
