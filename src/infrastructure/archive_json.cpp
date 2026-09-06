#include "infrastructure/archive_json.h"

#include <charconv>
#include <cmath>
#include <concepts>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <limits>
#include <string>
#include <string_view>
#include <type_traits>
#include <utility>

namespace linecode::infrastructure::archive_json {
namespace {

void AppendUtf8(std::string &output, std::uint32_t codepoint) {
  if (codepoint <= 0x7FU) {
    output.push_back(static_cast<char>(codepoint));
  } else if (codepoint <= 0x7FFU) {
    output.push_back(static_cast<char>(0xC0U | (codepoint >> 6U)));
    output.push_back(static_cast<char>(0x80U | (codepoint & 0x3FU)));
  } else if (codepoint <= 0xFFFFU) {
    output.push_back(static_cast<char>(0xE0U | (codepoint >> 12U)));
    output.push_back(static_cast<char>(0x80U | ((codepoint >> 6U) & 0x3FU)));
    output.push_back(static_cast<char>(0x80U | (codepoint & 0x3FU)));
  } else {
    output.push_back(static_cast<char>(0xF0U | (codepoint >> 18U)));
    output.push_back(static_cast<char>(0x80U | ((codepoint >> 12U) & 0x3FU)));
    output.push_back(static_cast<char>(0x80U | ((codepoint >> 6U) & 0x3FU)));
    output.push_back(static_cast<char>(0x80U | (codepoint & 0x3FU)));
  }
}

class Parser final {
public:
  explicit Parser(std::string_view input) : input_(input) {}

  std::expected<Value, Error> Run() {
    auto value = ParseValue(0);
    SkipWhitespace();
    if (value && position_ != input_.size()) {
      return Fail("trailing JSON data");
    }
    return value;
  }

private:
  static constexpr std::size_t kMaximumDepth = 96;

  std::expected<Value, Error> Fail(std::string message) const {
    return std::unexpected(
        Error{std::move(message) + " at byte " + std::to_string(position_)});
  }

  void SkipWhitespace() {
    while (position_ < input_.size() &&
           (input_[position_] == ' ' || input_[position_] == '\n' ||
            input_[position_] == '\r' || input_[position_] == '\t')) {
      ++position_;
    }
  }

  bool Consume(char expected) {
    SkipWhitespace();
    if (position_ >= input_.size() || input_[position_] != expected) {
      return false;
    }
    ++position_;
    return true;
  }

  std::expected<Value, Error> ParseValue(std::size_t depth) {
    if (depth > kMaximumDepth) {
      return Fail("JSON nesting is too deep");
    }
    SkipWhitespace();
    if (position_ >= input_.size()) {
      return Fail("unexpected end of JSON");
    }
    switch (input_[position_]) {
    case '{':
      return ParseObject(depth + 1);
    case '[':
      return ParseArray(depth + 1);
    case '"': {
      auto text = ParseString();
      if (!text)
        return std::unexpected(text.error());
      return Value{std::move(*text)};
    }
    case 't':
      return ParseLiteral("true", Value{true});
    case 'f':
      return ParseLiteral("false", Value{false});
    case 'n':
      return ParseLiteral("null", Value{Null{}});
    default:
      return ParseNumber();
    }
  }

  std::expected<Value, Error> ParseLiteral(std::string_view literal,
                                           Value value) {
    if (input_.substr(position_, literal.size()) != literal) {
      return Fail("invalid JSON literal");
    }
    position_ += literal.size();
    return value;
  }

  std::expected<Value, Error> ParseObject(std::size_t depth) {
    ++position_;
    Object object;
    SkipWhitespace();
    if (Consume('}'))
      return Value{std::move(object)};
    for (;;) {
      SkipWhitespace();
      if (position_ >= input_.size() || input_[position_] != '"')
        return Fail("expected JSON object key");
      auto key = ParseString();
      if (!key)
        return std::unexpected(key.error());
      if (!Consume(':'))
        return Fail("expected ':' after JSON key");
      auto value = ParseValue(depth);
      if (!value)
        return value;
      if (!object.emplace(std::move(*key), std::move(*value)).second)
        return Fail("duplicate JSON object key");
      if (Consume('}'))
        break;
      if (!Consume(','))
        return Fail("expected ',' in JSON object");
    }
    return Value{std::move(object)};
  }

  std::expected<Value, Error> ParseArray(std::size_t depth) {
    ++position_;
    Array array;
    SkipWhitespace();
    if (Consume(']'))
      return Value{std::move(array)};
    for (;;) {
      auto value = ParseValue(depth);
      if (!value)
        return value;
      array.push_back(std::move(*value));
      if (Consume(']'))
        break;
      if (!Consume(','))
        return Fail("expected ',' in JSON array");
    }
    return Value{std::move(array)};
  }

  std::expected<std::uint32_t, Error> ParseHex4() {
    if (position_ + 4 > input_.size())
      return std::unexpected(Error{"truncated JSON unicode escape"});
    std::uint32_t value{};
    for (int index = 0; index < 4; ++index) {
      const char c = input_[position_++];
      value <<= 4U;
      if (c >= '0' && c <= '9')
        value |= static_cast<unsigned>(c - '0');
      else if (c >= 'a' && c <= 'f')
        value |= static_cast<unsigned>(c - 'a' + 10);
      else if (c >= 'A' && c <= 'F')
        value |= static_cast<unsigned>(c - 'A' + 10);
      else
        return std::unexpected(Error{"invalid JSON unicode escape"});
    }
    return value;
  }

  std::expected<std::string, Error> ParseString() {
    ++position_;
    std::string output;
    while (position_ < input_.size()) {
      const unsigned char c = static_cast<unsigned char>(input_[position_++]);
      if (c == '"')
        return output;
      if (c < 0x20U)
        return std::unexpected(Error{"control byte in JSON string"});
      if (c != '\\') {
        output.push_back(static_cast<char>(c));
        continue;
      }
      if (position_ >= input_.size())
        return std::unexpected(Error{"truncated JSON escape"});
      const char escaped = input_[position_++];
      switch (escaped) {
      case '"': case '\\': case '/': output.push_back(escaped); break;
      case 'b': output.push_back('\b'); break;
      case 'f': output.push_back('\f'); break;
      case 'n': output.push_back('\n'); break;
      case 'r': output.push_back('\r'); break;
      case 't': output.push_back('\t'); break;
      case 'u': {
        auto first = ParseHex4();
        if (!first)
          return std::unexpected(first.error());
        std::uint32_t codepoint = *first;
        if (codepoint >= 0xD800U && codepoint <= 0xDBFFU) {
          if (position_ + 2 > input_.size() || input_[position_] != '\\' ||
              input_[position_ + 1] != 'u')
            return std::unexpected(Error{"missing low unicode surrogate"});
          position_ += 2;
          auto second = ParseHex4();
          if (!second || *second < 0xDC00U || *second > 0xDFFFU)
            return std::unexpected(Error{"invalid low unicode surrogate"});
          codepoint = 0x10000U + ((codepoint - 0xD800U) << 10U) +
                      (*second - 0xDC00U);
        } else if (codepoint >= 0xDC00U && codepoint <= 0xDFFFU) {
          return std::unexpected(Error{"unexpected low unicode surrogate"});
        }
        AppendUtf8(output, codepoint);
        break;
      }
      default:
        return std::unexpected(Error{"invalid JSON escape"});
      }
    }
    return std::unexpected(Error{"unterminated JSON string"});
  }

  std::expected<Value, Error> ParseNumber() {
    const std::size_t start = position_;
    if (input_[position_] == '-')
      ++position_;
    if (position_ >= input_.size())
      return Fail("invalid JSON number");
    if (input_[position_] == '0') {
      ++position_;
    } else {
      if (input_[position_] < '1' || input_[position_] > '9')
        return Fail("invalid JSON number");
      while (position_ < input_.size() && input_[position_] >= '0' &&
             input_[position_] <= '9')
        ++position_;
    }
    bool floating = false;
    if (position_ < input_.size() && input_[position_] == '.') {
      floating = true;
      ++position_;
      const std::size_t digits = position_;
      while (position_ < input_.size() && input_[position_] >= '0' &&
             input_[position_] <= '9')
        ++position_;
      if (digits == position_)
        return Fail("invalid JSON fraction");
    }
    if (position_ < input_.size() &&
        (input_[position_] == 'e' || input_[position_] == 'E')) {
      floating = true;
      ++position_;
      if (position_ < input_.size() &&
          (input_[position_] == '+' || input_[position_] == '-'))
        ++position_;
      const std::size_t digits = position_;
      while (position_ < input_.size() && input_[position_] >= '0' &&
             input_[position_] <= '9')
        ++position_;
      if (digits == position_)
        return Fail("invalid JSON exponent");
    }
    const auto token = input_.substr(start, position_ - start);
    if (!floating) {
      std::int64_t integer{};
      const auto parsed = std::from_chars(token.data(), token.data() + token.size(), integer);
      if (parsed.ec == std::errc{} && parsed.ptr == token.data() + token.size())
        return Value{integer};
    }
    std::string owned{token};
    char *end{};
    const double number = std::strtod(owned.c_str(), &end);
    if (end != owned.c_str() + owned.size() || !std::isfinite(number))
      return Fail("invalid JSON number");
    return Value{number};
  }

  std::string_view input_;
  std::size_t position_{};
};

void WriteString(std::string &output, std::string_view value) {
  constexpr char hex[] = "0123456789abcdef";
  output.push_back('"');
  for (const unsigned char c : value) {
    switch (c) {
    case '"': output += "\\\""; break;
    case '\\': output += "\\\\"; break;
    case '\b': output += "\\b"; break;
    case '\f': output += "\\f"; break;
    case '\n': output += "\\n"; break;
    case '\r': output += "\\r"; break;
    case '\t': output += "\\t"; break;
    default:
      if (c < 0x20U) {
        output += "\\u00";
        output.push_back(hex[c >> 4U]);
        output.push_back(hex[c & 0xFU]);
      } else {
        output.push_back(static_cast<char>(c));
      }
    }
  }
  output.push_back('"');
}

void WriteValue(std::string &output, const Value &value) {
  std::visit(
      [&output](const auto &item) {
        using T = std::decay_t<decltype(item)>;
        if constexpr (std::same_as<T, Null>) output += "null";
        else if constexpr (std::same_as<T, bool>) output += item ? "true" : "false";
        else if constexpr (std::same_as<T, std::int64_t>) output += std::to_string(item);
        else if constexpr (std::same_as<T, double>) {
          char buffer[64];
          const auto converted = std::to_chars(buffer, buffer + sizeof(buffer), item);
          output.append(buffer, converted.ptr);
        } else if constexpr (std::same_as<T, std::string>) WriteString(output, item);
        else if constexpr (std::same_as<T, Array>) {
          output.push_back('[');
          for (std::size_t i = 0; i < item.size(); ++i) {
            if (i) output.push_back(',');
            WriteValue(output, item[i]);
          }
          output.push_back(']');
        } else {
          output.push_back('{');
          bool first = true;
          for (const auto &[key, child] : item) {
            if (!first) output.push_back(',');
            first = false;
            WriteString(output, key);
            output.push_back(':');
            WriteValue(output, child);
          }
          output.push_back('}');
        }
      },
      value);
}

} // namespace

std::expected<Value, Error> Parse(std::string_view text) {
  return Parser{text}.Run();
}

std::string Serialize(const Value &value) {
  std::string output;
  WriteValue(output, value);
  return output;
}

const Object *AsObject(const Value *value) noexcept {
  return value ? std::get_if<Object>(value) : nullptr;
}
const Array *AsArray(const Value *value) noexcept {
  return value ? std::get_if<Array>(value) : nullptr;
}
const std::string *AsString(const Value *value) noexcept {
  return value ? std::get_if<std::string>(value) : nullptr;
}
const Value *Find(const Object &object, std::string_view key) {
  const auto found = object.find(key);
  return found == object.end() ? nullptr : &found->second;
}

} // namespace linecode::infrastructure::archive_json
