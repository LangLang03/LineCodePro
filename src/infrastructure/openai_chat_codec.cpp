#include "infrastructure/openai_chat_codec.h"

#include <charconv>
#include <cstdint>
#include <limits>
#include <string>
#include <utility>
#include <variant>
#include <vector>

namespace linecode::infrastructure {
namespace {

struct JsonNumber final {
  std::string value;
};

struct JsonValue final {
  using Array = std::vector<JsonValue>;
  using Object = std::vector<std::pair<std::string, JsonValue>>;
  std::variant<std::nullptr_t, bool, JsonNumber, std::string, Array, Object>
      value;
};

class JsonParser final {
public:
  explicit JsonParser(std::string_view source) : source_(source) {}

  std::expected<JsonValue, OpenAiCodecError> Parse() {
    SkipWhitespace();
    auto value = ParseValue(0U);
    if (!value.has_value()) {
      return value;
    }
    SkipWhitespace();
    if (cursor_ != source_.size()) {
      return Error("Unexpected content after JSON value");
    }
    return value;
  }

private:
  static constexpr std::size_t kMaximumDepth = 128U;

  std::expected<JsonValue, OpenAiCodecError>
  ParseValue(const std::size_t depth) {
    if (depth > kMaximumDepth) {
      return Error("JSON nesting exceeds 128 levels");
    }
    SkipWhitespace();
    if (cursor_ == source_.size()) {
      return Error("Unexpected end of JSON input");
    }
    switch (source_[cursor_]) {
    case 'n':
      return ParseLiteral("null", JsonValue{.value = nullptr});
    case 't':
      return ParseLiteral("true", JsonValue{.value = true});
    case 'f':
      return ParseLiteral("false", JsonValue{.value = false});
    case '"': {
      auto text = ParseString();
      if (!text.has_value()) {
        return std::unexpected(text.error());
      }
      return JsonValue{.value = std::move(*text)};
    }
    case '[':
      return ParseArray(depth + 1U);
    case '{':
      return ParseObject(depth + 1U);
    default:
      return ParseNumber();
    }
  }

  std::expected<JsonValue, OpenAiCodecError>
  ParseLiteral(const std::string_view literal, JsonValue value) {
    if (!source_.substr(cursor_).starts_with(literal)) {
      return Error("Invalid JSON literal");
    }
    cursor_ += literal.size();
    return value;
  }

  std::expected<JsonValue, OpenAiCodecError>
  ParseArray(const std::size_t depth) {
    ++cursor_;
    SkipWhitespace();
    JsonValue::Array array;
    if (Consume(']')) {
      return JsonValue{.value = std::move(array)};
    }
    while (true) {
      auto value = ParseValue(depth);
      if (!value.has_value()) {
        return value;
      }
      array.push_back(std::move(*value));
      SkipWhitespace();
      if (Consume(']')) {
        return JsonValue{.value = std::move(array)};
      }
      if (!Consume(',')) {
        return Error("Expected ',' or ']' in JSON array");
      }
    }
  }

  std::expected<JsonValue, OpenAiCodecError>
  ParseObject(const std::size_t depth) {
    ++cursor_;
    SkipWhitespace();
    JsonValue::Object object;
    if (Consume('}')) {
      return JsonValue{.value = std::move(object)};
    }
    while (true) {
      if (cursor_ == source_.size() || source_[cursor_] != '"') {
        return Error("Expected a string key in JSON object");
      }
      auto key = ParseString();
      if (!key.has_value()) {
        return std::unexpected(key.error());
      }
      SkipWhitespace();
      if (!Consume(':')) {
        return Error("Expected ':' after JSON object key");
      }
      auto value = ParseValue(depth);
      if (!value.has_value()) {
        return value;
      }
      object.emplace_back(std::move(*key), std::move(*value));
      SkipWhitespace();
      if (Consume('}')) {
        return JsonValue{.value = std::move(object)};
      }
      if (!Consume(',')) {
        return Error("Expected ',' or '}' in JSON object");
      }
      SkipWhitespace();
    }
  }

  std::expected<std::string, OpenAiCodecError> ParseString() {
    ++cursor_;
    std::string result;
    while (cursor_ < source_.size()) {
      const auto character = static_cast<unsigned char>(source_[cursor_++]);
      if (character == '"') {
        return result;
      }
      if (character < 0x20U) {
        return Error("Unescaped control character in JSON string");
      }
      if (character != '\\') {
        result.push_back(static_cast<char>(character));
        continue;
      }
      if (cursor_ == source_.size()) {
        return Error("Unterminated JSON escape");
      }
      const char escape = source_[cursor_++];
      switch (escape) {
      case '"':
      case '\\':
      case '/':
        result.push_back(escape);
        break;
      case 'b':
        result.push_back('\b');
        break;
      case 'f':
        result.push_back('\f');
        break;
      case 'n':
        result.push_back('\n');
        break;
      case 'r':
        result.push_back('\r');
        break;
      case 't':
        result.push_back('\t');
        break;
      case 'u': {
        auto code_point = ParseUnicodeEscape();
        if (!code_point.has_value()) {
          return std::unexpected(code_point.error());
        }
        AppendUtf8(result, *code_point);
        break;
      }
      default:
        return Error("Invalid JSON escape");
      }
    }
    return Error("Unterminated JSON string");
  }

  std::expected<std::uint32_t, OpenAiCodecError> ParseUnicodeEscape() {
    auto first = ParseHexQuad();
    if (!first.has_value()) {
      return first;
    }
    if (*first < 0xD800U || *first > 0xDFFFU) {
      return *first;
    }
    if (*first > 0xDBFFU || cursor_ + 2U > source_.size() ||
        source_[cursor_] != '\\' || source_[cursor_ + 1U] != 'u') {
      return Error("Invalid JSON Unicode surrogate pair");
    }
    cursor_ += 2U;
    auto second = ParseHexQuad();
    if (!second.has_value()) {
      return second;
    }
    if (*second < 0xDC00U || *second > 0xDFFFU) {
      return Error("Invalid JSON Unicode surrogate pair");
    }
    return 0x10000U + ((*first - 0xD800U) << 10U) + (*second - 0xDC00U);
  }

  std::expected<std::uint32_t, OpenAiCodecError> ParseHexQuad() {
    if (cursor_ + 4U > source_.size()) {
      return Error("Incomplete JSON Unicode escape");
    }
    std::uint32_t value = 0U;
    for (std::size_t index = 0U; index < 4U; ++index) {
      const char character = source_[cursor_++];
      value <<= 4U;
      if (character >= '0' && character <= '9') {
        value += static_cast<std::uint32_t>(character - '0');
      } else if (character >= 'a' && character <= 'f') {
        value += static_cast<std::uint32_t>(character - 'a' + 10);
      } else if (character >= 'A' && character <= 'F') {
        value += static_cast<std::uint32_t>(character - 'A' + 10);
      } else {
        return Error("Invalid JSON Unicode escape");
      }
    }
    return value;
  }

  static void AppendUtf8(std::string &target, const std::uint32_t code_point) {
    if (code_point <= 0x7FU) {
      target.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7FFU) {
      target.push_back(static_cast<char>(0xC0U | (code_point >> 6U)));
      target.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    } else if (code_point <= 0xFFFFU) {
      target.push_back(static_cast<char>(0xE0U | (code_point >> 12U)));
      target.push_back(
          static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
      target.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    } else {
      target.push_back(static_cast<char>(0xF0U | (code_point >> 18U)));
      target.push_back(
          static_cast<char>(0x80U | ((code_point >> 12U) & 0x3FU)));
      target.push_back(
          static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
      target.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
    }
  }

  std::expected<JsonValue, OpenAiCodecError> ParseNumber() {
    const auto start = cursor_;
    Consume('-');
    if (cursor_ == source_.size()) {
      return Error("Invalid JSON number");
    }
    if (source_[cursor_] == '0') {
      ++cursor_;
    } else if (source_[cursor_] >= '1' && source_[cursor_] <= '9') {
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
    } else {
      return Error("Invalid JSON number");
    }
    if (Consume('.')) {
      if (cursor_ == source_.size() || source_[cursor_] < '0' ||
          source_[cursor_] > '9') {
        return Error("Invalid JSON number fraction");
      }
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
    }
    if (cursor_ < source_.size() &&
        (source_[cursor_] == 'e' || source_[cursor_] == 'E')) {
      ++cursor_;
      if (cursor_ < source_.size() &&
          (source_[cursor_] == '+' || source_[cursor_] == '-')) {
        ++cursor_;
      }
      if (cursor_ == source_.size() || source_[cursor_] < '0' ||
          source_[cursor_] > '9') {
        return Error("Invalid JSON number exponent");
      }
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
    }
    return JsonValue{
        .value = JsonNumber{.value = std::string{source_.substr(
                                start, cursor_ - start)}},
    };
  }

  bool Consume(const char expected) {
    if (cursor_ == source_.size() || source_[cursor_] != expected) {
      return false;
    }
    ++cursor_;
    return true;
  }

  void SkipWhitespace() {
    while (cursor_ < source_.size() &&
           (source_[cursor_] == ' ' || source_[cursor_] == '\t' ||
            source_[cursor_] == '\n' || source_[cursor_] == '\r')) {
      ++cursor_;
    }
  }

  std::unexpected<OpenAiCodecError> Error(std::string message) const {
    return std::unexpected(OpenAiCodecError{.message = std::move(message),
                                            .offset = cursor_});
  }

  std::string_view source_;
  std::size_t cursor_{};
};

const JsonValue *Member(const JsonValue &value, const std::string_view key) {
  const auto *object = std::get_if<JsonValue::Object>(&value.value);
  if (object == nullptr) {
    return nullptr;
  }
  for (const auto &[name, member] : *object) {
    if (name == key) {
      return &member;
    }
  }
  return nullptr;
}

const JsonValue *Element(const JsonValue *value, const std::size_t index) {
  if (value == nullptr) {
    return nullptr;
  }
  const auto *array = std::get_if<JsonValue::Array>(&value->value);
  return array != nullptr && index < array->size() ? &(*array)[index] : nullptr;
}

const std::string *StringValue(const JsonValue *value) {
  return value == nullptr ? nullptr : std::get_if<std::string>(&value->value);
}

std::int64_t IntegerValue(const JsonValue *value) {
  if (value == nullptr) {
    return 0;
  }
  const auto *number = std::get_if<JsonNumber>(&value->value);
  if (number == nullptr) {
    return 0;
  }
  std::int64_t result{};
  const auto [end, error] = std::from_chars(
      number->value.data(), number->value.data() + number->value.size(), result);
  return error == std::errc{} &&
                 end == number->value.data() + number->value.size()
             ? result
             : 0;
}

std::expected<JsonValue, OpenAiCodecError> Parse(std::string_view json) {
  return JsonParser(json).Parse();
}

std::expected<void, OpenAiCodecError> CheckApiError(const JsonValue &root) {
  const auto *error = Member(root, "error");
  if (error == nullptr || std::holds_alternative<std::nullptr_t>(error->value)) {
    return {};
  }
  if (const auto *text = StringValue(error); text != nullptr) {
    return std::unexpected(
        OpenAiCodecError{.message = "OpenAI error: " + *text});
  }
  if (const auto *message = StringValue(Member(*error, "message"));
      message != nullptr) {
    return std::unexpected(
        OpenAiCodecError{.message = "OpenAI error: " + *message});
  }
  return std::unexpected(OpenAiCodecError{.message = "OpenAI returned an error"});
}

void AppendEscaped(std::string &target, std::string_view value) {
  constexpr char kHex[] = "0123456789abcdef";
  target.push_back('"');
  for (const unsigned char character : value) {
    switch (character) {
    case '"':
      target += "\\\"";
      break;
    case '\\':
      target += "\\\\";
      break;
    case '\b':
      target += "\\b";
      break;
    case '\f':
      target += "\\f";
      break;
    case '\n':
      target += "\\n";
      break;
    case '\r':
      target += "\\r";
      break;
    case '\t':
      target += "\\t";
      break;
    default:
      if (character < 0x20U) {
        target += "\\u00";
        target.push_back(kHex[character >> 4U]);
        target.push_back(kHex[character & 0x0FU]);
      } else {
        target.push_back(static_cast<char>(character));
      }
    }
  }
  target.push_back('"');
}

std::string_view Trim(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = value.find_last_not_of(" \t\r\n");
  return value.substr(first, last - first + 1U);
}

} // namespace

std::string OpenAiChatEndpoint(const std::string_view base_url) {
  auto base = Trim(base_url);
  while (base.ends_with('/')) {
    base.remove_suffix(1U);
  }
  constexpr std::string_view kSuffix = "/chat/completions";
  if (base.ends_with(kSuffix)) {
    return std::string{base};
  }
  return std::string{base} + std::string{kSuffix};
}

std::string
EncodeOpenAiChatRequest(const application::CompletionRequest &request) {
  std::string json = "{\"model\":";
  AppendEscaped(json, request.model.model_id);
  json += ",\"messages\":[";
  bool first = true;
  for (const auto &message : request.messages) {
    if (!first) {
      json.push_back(',');
    }
    first = false;
    json += "{\"role\":";
    AppendEscaped(json, message.role == application::CompletionRole::assistant
                            ? "assistant"
                            : "user");
    json += ",\"content\":";
    AppendEscaped(json, message.content);
    json.push_back('}');
  }
  json += "],\"temperature\":0.2,\"stream\":";
  json += request.stream ? "true" : "false";
  json.push_back('}');
  return json;
}

std::expected<application::CompletionResponse, OpenAiCodecError>
DecodeOpenAiChatResponse(const std::string_view json) {
  auto root = Parse(json);
  if (!root.has_value()) {
    return std::unexpected(root.error());
  }
  auto api_error = CheckApiError(*root);
  if (!api_error.has_value()) {
    return std::unexpected(api_error.error());
  }
  const auto *choice = Element(Member(*root, "choices"), 0U);
  const auto *message = choice == nullptr ? nullptr : Member(*choice, "message");
  const auto *content = message == nullptr
                            ? nullptr
                            : StringValue(Member(*message, "content"));
  if (content == nullptr) {
    return std::unexpected(OpenAiCodecError{
        .message = "OpenAI response has no choices[0].message.content"});
  }
  const auto *usage = Member(*root, "usage");
  return application::CompletionResponse{
      .text = *content,
      .input_tokens = usage == nullptr
                          ? 0
                          : IntegerValue(Member(*usage, "prompt_tokens")),
      .output_tokens =
          usage == nullptr
              ? 0
              : IntegerValue(Member(*usage, "completion_tokens")),
  };
}

std::expected<OpenAiStreamChunk, OpenAiCodecError>
DecodeOpenAiChatStreamEvent(const std::string_view data) {
  if (Trim(data) == "[DONE]") {
    return OpenAiStreamChunk{.done = true, .text_delta = std::nullopt};
  }
  auto root = Parse(data);
  if (!root.has_value()) {
    return std::unexpected(root.error());
  }
  auto api_error = CheckApiError(*root);
  if (!api_error.has_value()) {
    return std::unexpected(api_error.error());
  }
  const auto *choice = Element(Member(*root, "choices"), 0U);
  if (choice == nullptr) {
    return OpenAiStreamChunk{};
  }
  if (const auto *finish_reason = StringValue(Member(*choice, "finish_reason"));
      finish_reason != nullptr && *finish_reason == "content_filter") {
    return std::unexpected(OpenAiCodecError{
        .message = "OpenAI blocked the response with its content filter"});
  }
  const auto *delta = Member(*choice, "delta");
  const auto *content =
      delta == nullptr ? nullptr : StringValue(Member(*delta, "content"));
  return OpenAiStreamChunk{
      .text_delta = content == nullptr
                        ? std::nullopt
                        : std::optional<std::string>{*content},
  };
}

} // namespace linecode::infrastructure
