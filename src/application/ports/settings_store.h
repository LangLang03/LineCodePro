#pragma once

#include <charconv>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

#include <huxerui/task.h>

namespace linecode::application {

class SettingsStore {
public:
  virtual ~SettingsStore() = default;

  [[nodiscard]] virtual std::optional<std::string> Read(std::string_view key) const = 0;
  virtual void Write(std::string_view key, std::string value) = 0;
};

// The theme service still consumes the synchronous SettingsStore above. New
// database-backed settings use this asynchronous port so storage work never
// blocks the UI thread. The two contracts can coexist until theme persistence
// is moved to the shared database.
enum class SettingsValueKind { string, boolean, integer };

[[nodiscard]] constexpr std::string_view
SettingsValueKindName(SettingsValueKind kind) noexcept {
  switch (kind) {
  case SettingsValueKind::string:
    return "string";
  case SettingsValueKind::boolean:
    return "boolean";
  case SettingsValueKind::integer:
    return "long";
  }
  return "string";
}

struct SettingsStoreError final {
  std::string message;
};

template <class Value>
using SettingsResult = std::expected<Value, SettingsStoreError>;

// Accepts every spelling a stored row can legitimately contain: this app writes
// "true"/"false", but archive imports copy values verbatim and older or foreign
// builds stored "1", "yes" or "on". Surrounding whitespace is ignored. Anything
// unrecognised, including the empty string, is false -- a missing or corrupt row
// must never read as enabled.
[[nodiscard]] inline bool ParseStoredBoolean(std::string_view value) noexcept {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos)
    return false;
  const auto last = value.find_last_not_of(" \t\r\n");
  value = value.substr(first, last - first + 1U);
  if (value == "1")
    return true;
  const auto equals_ascii_lower = [value](std::string_view candidate) noexcept {
    if (value.size() != candidate.size())
      return false;
    for (std::size_t index = 0; index < value.size(); ++index) {
      const auto character = static_cast<unsigned char>(value[index]);
      const auto lowered = (character >= 'A' && character <= 'Z')
                               ? static_cast<char>(character - 'A' + 'a')
                               : static_cast<char>(character);
      if (lowered != candidate[index])
        return false;
    }
    return true;
  };
  return equals_ascii_lower("true") || equals_ascii_lower("yes") ||
         equals_ascii_lower("on") || equals_ascii_lower("y") ||
         equals_ascii_lower("t");
}

[[nodiscard]] inline std::optional<std::int64_t>
ParseStoredInteger(std::string_view value) noexcept {
  std::int64_t parsed{};
  const auto [end, error] =
      std::from_chars(value.data(), value.data() + value.size(), parsed);
  if (error != std::errc{} || end != value.data() + value.size())
    return std::nullopt;
  return parsed;
}

class AsyncSettingsStore {
public:
  virtual ~AsyncSettingsStore() = default;

  [[nodiscard]] virtual huxerui::Task<SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<bool>>
  GetBoolean(std::string key, bool fallback) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<std::int64_t>>
  GetInteger(std::string key, std::int64_t fallback) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  SetString(std::string key, std::string value) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  SetBoolean(std::string key, bool value) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  SetInteger(std::string key, std::int64_t value) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  Remove(std::string key) = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  ClearLineCodeSettings() = 0;
  [[nodiscard]] virtual huxerui::Task<
      SettingsResult<std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() = 0;
};

} // namespace linecode::application
