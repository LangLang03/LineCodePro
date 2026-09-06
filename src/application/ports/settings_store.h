#pragma once

#include <charconv>
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

[[nodiscard]] inline bool ParseStoredBoolean(std::string_view value) noexcept {
  if (value == "1")
    return true;
  if (value.size() != 4)
    return false;
  return (value[0] == 't' || value[0] == 'T') &&
         (value[1] == 'r' || value[1] == 'R') &&
         (value[2] == 'u' || value[2] == 'U') &&
         (value[3] == 'e' || value[3] == 'E');
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
