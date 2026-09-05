#include "domain/theme_palette.h"

#include <algorithm>
#include <array>
#include <charconv>
#include <cstdio>

namespace linecode::domain {
namespace {

constexpr PackedColor C(std::uint32_t rgb) { return 0xFF000000U | rgb; }
constexpr PackedColor A(std::uint32_t rgb, std::uint8_t alpha) {
  return (static_cast<PackedColor>(alpha) << 24U) | rgb;
}

constexpr ThemePalette
Make(std::array<PackedColor, theme_palette_color_count> values) {
  return {values};
}

constexpr ThemePalette kDark = Make({
    C(0x171819),     C(0x242629),     C(0x292D31),     C(0x34393F),
    C(0x242629),     C(0xEDF0F2),     C(0x969DA5),     C(0x969DA5),
    C(0x24262A),     C(0xE5E9EE),     C(0x242629),     C(0x171819),
    C(0x383D42),     C(0x30353A),     C(0x1D1F21),     C(0x383D42),
    C(0xD7A6AB),     C(0xD8B986),     C(0xADCEB8),     C(0x353A40),
    A(0xE5E9EE, 20), A(0xE5E9EE, 33), C(0x969DA5),     A(0x000000, 115),
    A(0xD7A6AB, 26), A(0xD7A6AB, 46), A(0x969DA5, 26), C(0x22322A),
    C(0x35272B),     C(0xADCEB8),     C(0xD7A6AB),
});
constexpr ThemePalette kLight = Make({
    C(0xFCFCFD),     C(0xF0F1F3),     C(0xEAECF0),     C(0xE1E4E9),
    C(0xF0F1F3),     C(0x24262A),     C(0x6C737D),     C(0x6C737D),
    C(0xFFFFFF),     C(0x333B46),     C(0xF0F1F3),     C(0xFCFCFD),
    C(0xE1E4E8),     C(0xEAECF0),     C(0xF0F1F3),     C(0xE1E4E8),
    C(0x9C5058),     C(0x8D6C32),     C(0x35684D),     C(0xE5E8EC),
    A(0x333B46, 15), A(0x333B46, 26), C(0x6C737D),     A(0x161A20, 66),
    A(0x9C5058, 20), A(0x9C5058, 36), A(0x6C737D, 20), C(0xEDF4EE),
    C(0xF8EDEF),     C(0x35684D),     C(0x9C5058),
});
constexpr ThemePalette kCoffee = Make({
    C(0xF4EFE6),     C(0xEEE5D8),     C(0xE7DCCA),     C(0xDED0B9),
    C(0xFFFBF3),     C(0x2B2118),     C(0x6C5A49),     C(0x9B8976),
    C(0xFFFFFF),     C(0xD97757),     C(0xB86F50),     C(0xEFE4D4),
    C(0xDDD0BF),     C(0xE8DDCF),     C(0x5B4128),     C(0x7A5A3A),
    C(0xB5473F),     C(0xB7791F),     C(0x6A7F46),     C(0xF1D4C6),
    A(0xD97757, 31), A(0xD97757, 46), C(0xC27A31),     A(0x2B2118, 97),
    A(0xB5473F, 31), A(0xB5473F, 46), A(0xC27A31, 31), A(0x6A7F46, 36),
    A(0xB5473F, 31), C(0x5E7447),     C(0xA0443E),
});
constexpr ThemePalette kVscode = Make({
    C(0x1E1E1E),     C(0x252526),     C(0x333337),     C(0x414145),
    C(0x3C3C3C),     C(0xD4D4D4),     C(0xA6A6A6),     C(0x6A6A6A),
    C(0xFFFFFF),     C(0x007ACC),     C(0x094771),     C(0x252526),
    C(0x3C3C3C),     C(0x454545),     C(0x1E1E1E),     C(0x3C3C3C),
    C(0xF48771),     C(0xCCA700),     C(0x89D185),     C(0x073A5A),
    A(0x007ACC, 41), A(0x007ACC, 61), C(0xDCDCAA),     A(0x000000, 148),
    A(0xF48771, 36), A(0xF48771, 56), A(0xDCDCAA, 31), A(0x89D185, 31),
    A(0xF48771, 31), C(0x89D185),     C(0xF48771),
});
constexpr ThemePalette kGithubDark = Make({
    C(0x0D1117),     C(0x010409),     C(0x21262D),     C(0x30363D),
    C(0x0D1117),     C(0xE6EDF3),     C(0x8B949E),     C(0x6E7681),
    C(0xFFFFFF),     C(0x2F81F7),     C(0x1F6FEB),     C(0x161B22),
    C(0x30363D),     C(0x21262D),     C(0x0D1117),     C(0x30363D),
    C(0xF85149),     C(0xD29922),     C(0x3FB950),     C(0x0D419D),
    A(0x2F81F7, 31), A(0x2F81F7, 51), C(0xD29922),     A(0x010409, 173),
    A(0xF85149, 36), A(0xF85149, 56), A(0xD29922, 31), A(0x2EA043, 36),
    A(0xF85149, 36), C(0x3FB950),     C(0xF85149),
});
constexpr ThemePalette kGruvbox = Make({
    C(0x282828),     C(0x1D2021),     C(0x3C3836),     C(0x504945),
    C(0x1D2021),     C(0xEBDBB2),     C(0xBDAE93),     C(0x928374),
    C(0x282828),     C(0xFABD2F),     C(0x458588),     C(0x32302F),
    C(0x504945),     C(0x665C54),     C(0x1D2021),     C(0x504945),
    C(0xFB4934),     C(0xFE8019),     C(0xB8BB26),     C(0x665C2E),
    A(0xFABD2F, 36), A(0xFABD2F, 56), C(0xFABD2F),     A(0x1D2021, 168),
    A(0xFB4934, 36), A(0xFB4934, 56), A(0xFABD2F, 33), A(0xB8BB26, 33),
    A(0xFB4934, 33), C(0xB8BB26),     C(0xFB4934),
});
constexpr ThemePalette kHighContrast = Make({
    C(0x000000),     C(0x050505),     C(0x222222),     C(0x333333),
    C(0x111111),     C(0xFFFFFF),     C(0xC7C7CC),     C(0x8E8E93),
    C(0x000000),     C(0x64D2FF),     C(0x004D80),     C(0x101010),
    C(0x666666),     C(0x3A3A3C),     C(0x000000),     C(0x555555),
    C(0xFF453A),     C(0xFFD60A),     C(0x30D158),     C(0x063B4C),
    A(0x64D2FF, 41), A(0x64D2FF, 61), C(0xFF9F0A),     A(0x000000, 191),
    A(0xFF453A, 46), A(0xFF453A, 66), A(0xFF9F0A, 41), A(0x30D158, 46),
    A(0xFF453A, 46), C(0x30D158),     C(0xFF453A),
});

constexpr ThemePalette CustomDefault() {
  auto value = kLight;
  value[ThemeColorRole::surface] = C(0xFFFFFF);
  value[ThemeColorRole::surface_elevated] = C(0xFFFFFF);
  value[ThemeColorRole::surface_light] = C(0xECECF1);
  value[ThemeColorRole::input_background] = C(0xFFFFFF);
  value[ThemeColorRole::user_bubble] = C(0x0A84FF);
  value[ThemeColorRole::ai_bubble] = C(0xF2F2F7);
  value[ThemeColorRole::code_background] = C(0xF2F2F7);
  value[ThemeColorRole::code_border] = C(0xD9D9DE);
  return value;
}

static_assert(kCoffee[ThemeColorRole::background] == C(0xF4EFE6));
static_assert(CustomDefault()[ThemeColorRole::user_bubble] == C(0x0A84FF));

} // namespace

std::string_view SerializeThemeMode(ThemeMode mode) {
  constexpr std::array values{"system",  "light",        "dark",
                              "coffee",  "vscode",       "githubDark",
                              "gruvbox", "highContrast", "custom"};
  return values[static_cast<std::size_t>(mode)];
}

ThemeMode ParseThemeMode(std::string_view mode) {
  for (std::size_t index = 0; index < 9; ++index) {
    auto value = static_cast<ThemeMode>(index);
    if (SerializeThemeMode(value) == mode)
      return value;
  }
  return ThemeMode::system;
}

std::string_view SerializeThemeColorRole(ThemeColorRole role) {
  constexpr std::array values{
      "bg",          "surface",     "surfaceElevated", "surfaceLight",
      "inputBg",     "text",        "textSecondary",   "textTertiary",
      "textOnColor", "accent",      "userBubble",      "aiBubble",
      "border",      "borderLight", "codeBg",          "codeBorder",
      "danger",      "warning",     "success"};
  return values[static_cast<std::size_t>(role)];
}

ThemePalette PaletteForMode(ThemeMode mode) {
  switch (mode) {
  case ThemeMode::light:
    return kLight;
  case ThemeMode::coffee:
    return kCoffee;
  case ThemeMode::vscode:
    return kVscode;
  case ThemeMode::github_dark:
    return kGithubDark;
  case ThemeMode::gruvbox:
    return kGruvbox;
  case ThemeMode::high_contrast:
    return kHighContrast;
  case ThemeMode::custom:
    return CustomDefault();
  case ThemeMode::system:
  case ThemeMode::dark:
    return kDark;
  }
  return kDark;
}

ThemePalette ApplyThemeDraft(ThemePalette base, const ThemeColorDraft &draft) {
  for (auto role : theme_color_roles) {
    if (auto parsed = ParseHexColor(draft[static_cast<std::size_t>(role)]))
      base[role] = *parsed;
  }
  return base;
}

ThemeColorDraft EditableThemeDraft(const ThemePalette &palette) {
  ThemeColorDraft result;
  for (auto role : theme_color_roles)
    result[static_cast<std::size_t>(role)] = ToHexColor(palette[role]);
  return result;
}

bool IsHexColor(std::string_view value) {
  if (value.size() != 7 && value.size() != 9)
    return false;
  if (value.front() != '#')
    return false;
  return std::ranges::all_of(value.substr(1), [](char character) {
    return (character >= '0' && character <= '9') ||
           (character >= 'a' && character <= 'f') ||
           (character >= 'A' && character <= 'F');
  });
}

std::optional<PackedColor> ParseHexColor(std::string_view value) {
  if (!IsHexColor(value))
    return std::nullopt;
  PackedColor parsed{};
  const auto result = std::from_chars(value.data() + 1,
                                      value.data() + value.size(), parsed, 16);
  if (result.ec != std::errc{})
    return std::nullopt;
  return value.size() == 7 ? parsed | 0xFF000000U : parsed;
}

std::string ToHexColor(PackedColor color) {
  std::array<char, 8> buffer{};
  std::snprintf(buffer.data(), buffer.size(), "#%06X", color & 0x00FFFFFFU);
  return buffer.data();
}

bool IsValidThemeDraft(const ThemeColorDraft &draft) {
  return std::ranges::all_of(draft, IsHexColor);
}

} // namespace linecode::domain
