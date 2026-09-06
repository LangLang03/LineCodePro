#pragma once

#include <cstdint>
#include <expected>
#include <map>
#include <string>
#include <string_view>
#include <variant>
#include <vector>

namespace linecode::infrastructure::archive_json {

struct Null final {
  bool operator==(const Null &) const = default;
};

struct Value;
using Array = std::vector<Value>;
using Object = std::map<std::string, Value, std::less<>>;

struct Value final
    : std::variant<Null, bool, std::int64_t, double, std::string, Array,
                   Object> {
  using Base =
      std::variant<Null, bool, std::int64_t, double, std::string, Array, Object>;
  using Base::Base;
};

struct Error final {
  std::string message;
};

[[nodiscard]] std::expected<Value, Error> Parse(std::string_view text);
[[nodiscard]] std::string Serialize(const Value &value);

[[nodiscard]] const Object *AsObject(const Value *value) noexcept;
[[nodiscard]] const Array *AsArray(const Value *value) noexcept;
[[nodiscard]] const std::string *AsString(const Value *value) noexcept;
[[nodiscard]] const Value *Find(const Object &object, std::string_view key);

} // namespace linecode::infrastructure::archive_json
