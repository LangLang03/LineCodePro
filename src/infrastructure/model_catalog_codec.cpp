#include "infrastructure/model_catalog_codec.h"

#include <algorithm>
#include <charconv>
#include <cstdint>
#include <cstdio>
#include <ranges>
#include <variant>

namespace linecode::infrastructure {
namespace {

struct JsonValue final {
  using Array = std::vector<JsonValue>;
  using Object = std::vector<std::pair<std::string, JsonValue>>;
  std::variant<std::nullptr_t, bool, double, std::string, Array, Object> value;
};

class JsonParser final {
public:
  explicit JsonParser(const std::string_view source) : source_(source) {}

  std::expected<JsonValue, ModelCatalogCodecError> Parse() {
    SkipWhitespace();
    auto result = ParseValue(0U);
    if (!result.has_value()) {
      return result;
    }
    SkipWhitespace();
    if (cursor_ != source_.size()) {
      return Error("Unexpected content after JSON value");
    }
    return result;
  }

private:
  static constexpr std::size_t kMaximumDepth = 128U;

  std::expected<JsonValue, ModelCatalogCodecError>
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
      return text.has_value()
                 ? std::expected<JsonValue, ModelCatalogCodecError>{
                       JsonValue{.value = std::move(*text)}}
                 : std::unexpected(text.error());
    }
    case '[':
      return ParseArray(depth + 1U);
    case '{':
      return ParseObject(depth + 1U);
    default:
      return ParseNumber();
    }
  }

  std::expected<JsonValue, ModelCatalogCodecError>
  ParseLiteral(const std::string_view literal, JsonValue value) {
    if (!source_.substr(cursor_).starts_with(literal)) {
      return Error("Invalid JSON literal");
    }
    cursor_ += literal.size();
    return value;
  }

  std::expected<JsonValue, ModelCatalogCodecError>
  ParseArray(const std::size_t depth) {
    ++cursor_;
    SkipWhitespace();
    JsonValue::Array values;
    if (Consume(']')) {
      return JsonValue{.value = std::move(values)};
    }
    while (true) {
      auto value = ParseValue(depth);
      if (!value.has_value()) {
        return value;
      }
      values.push_back(std::move(*value));
      SkipWhitespace();
      if (Consume(']')) {
        return JsonValue{.value = std::move(values)};
      }
      if (!Consume(',')) {
        return Error("Expected ',' or ']' in JSON array");
      }
    }
  }

  std::expected<JsonValue, ModelCatalogCodecError>
  ParseObject(const std::size_t depth) {
    ++cursor_;
    SkipWhitespace();
    JsonValue::Object values;
    if (Consume('}')) {
      return JsonValue{.value = std::move(values)};
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
      values.emplace_back(std::move(*key), std::move(*value));
      SkipWhitespace();
      if (Consume('}')) {
        return JsonValue{.value = std::move(values)};
      }
      if (!Consume(',')) {
        return Error("Expected ',' or '}' in JSON object");
      }
      SkipWhitespace();
    }
  }

  std::expected<std::string, ModelCatalogCodecError> ParseString() {
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
      switch (const char escape = source_[cursor_++]) {
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

  std::expected<std::uint32_t, ModelCatalogCodecError> ParseUnicodeEscape() {
    auto first = ParseHexQuad();
    if (!first.has_value()) {
      return first;
    }
    if (*first < 0xD800U || *first > 0xDFFFU) {
      return first;
    }
    if (*first > 0xDBFFU || cursor_ + 2U > source_.size() ||
        source_[cursor_] != '\\' || source_[cursor_ + 1U] != 'u') {
      return Error("Invalid JSON Unicode surrogate pair");
    }
    cursor_ += 2U;
    auto second = ParseHexQuad();
    if (!second.has_value() || *second < 0xDC00U || *second > 0xDFFFU) {
      return Error("Invalid JSON Unicode surrogate pair");
    }
    return 0x10000U + ((*first - 0xD800U) << 10U) + (*second - 0xDC00U);
  }

  std::expected<std::uint32_t, ModelCatalogCodecError> ParseHexQuad() {
    if (cursor_ + 4U > source_.size()) {
      return Error("Incomplete JSON Unicode escape");
    }
    std::uint32_t value{};
    for (std::size_t index{}; index < 4U; ++index) {
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

  static void AppendUtf8(std::string &target, const std::uint32_t point) {
    if (point <= 0x7FU) {
      target.push_back(static_cast<char>(point));
    } else if (point <= 0x7FFU) {
      target.push_back(static_cast<char>(0xC0U | (point >> 6U)));
      target.push_back(static_cast<char>(0x80U | (point & 0x3FU)));
    } else if (point <= 0xFFFFU) {
      target.push_back(static_cast<char>(0xE0U | (point >> 12U)));
      target.push_back(static_cast<char>(0x80U | ((point >> 6U) & 0x3FU)));
      target.push_back(static_cast<char>(0x80U | (point & 0x3FU)));
    } else {
      target.push_back(static_cast<char>(0xF0U | (point >> 18U)));
      target.push_back(static_cast<char>(0x80U | ((point >> 12U) & 0x3FU)));
      target.push_back(static_cast<char>(0x80U | ((point >> 6U) & 0x3FU)));
      target.push_back(static_cast<char>(0x80U | (point & 0x3FU)));
    }
  }

  std::expected<JsonValue, ModelCatalogCodecError> ParseNumber() {
    const auto start = cursor_;
    if (cursor_ < source_.size() && source_[cursor_] == '-') {
      ++cursor_;
    }
    if (cursor_ == source_.size()) {
      return Error("Invalid JSON number");
    }
    if (source_[cursor_] == '0') {
      ++cursor_;
    } else {
      const auto digits = cursor_;
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
      if (digits == cursor_) {
        return Error("Invalid JSON value");
      }
    }
    if (cursor_ < source_.size() && source_[cursor_] == '.') {
      ++cursor_;
      const auto digits = cursor_;
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
      if (digits == cursor_) {
        return Error("Invalid JSON number fraction");
      }
    }
    if (cursor_ < source_.size() &&
        (source_[cursor_] == 'e' || source_[cursor_] == 'E')) {
      ++cursor_;
      if (cursor_ < source_.size() &&
          (source_[cursor_] == '+' || source_[cursor_] == '-')) {
        ++cursor_;
      }
      const auto digits = cursor_;
      while (cursor_ < source_.size() && source_[cursor_] >= '0' &&
             source_[cursor_] <= '9') {
        ++cursor_;
      }
      if (digits == cursor_) {
        return Error("Invalid JSON number exponent");
      }
    }
    double value{};
    const auto [end, error] = std::from_chars(
        source_.data() + start, source_.data() + cursor_, value);
    if (error != std::errc{} || end != source_.data() + cursor_) {
      return Error("Invalid JSON number");
    }
    return JsonValue{.value = value};
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
            source_[cursor_] == '\r' || source_[cursor_] == '\n')) {
      ++cursor_;
    }
  }

  std::unexpected<ModelCatalogCodecError> Error(std::string message) const {
    return std::unexpected(ModelCatalogCodecError{
        .message = std::move(message), .offset = cursor_});
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

const JsonValue::Array *ArrayValue(const JsonValue *value) {
  return value == nullptr ? nullptr : std::get_if<JsonValue::Array>(&value->value);
}

const std::string *StringValue(const JsonValue *value) {
  return value == nullptr ? nullptr : std::get_if<std::string>(&value->value);
}

std::string_view Trim(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = value.find_last_not_of(" \t\r\n");
  return value.substr(first, last - first + 1U);
}

std::string AppendEndpoint(std::string_view base_url,
                           const std::string_view suffix) {
  auto base = Trim(base_url);
  while (base.ends_with('/')) {
    base.remove_suffix(1U);
  }
  if (base.ends_with(suffix)) {
    return std::string{base};
  }
  return std::string{base} + std::string{suffix};
}

std::expected<std::string, ModelCatalogCodecError>
RootOrigin(const std::string_view base_url) {
  const auto base = Trim(base_url);
  const auto scheme = base.find("://");
  if (scheme == std::string_view::npos) {
    return std::unexpected(ModelCatalogCodecError{
        .message = "Anthropic base URL must be absolute"});
  }
  const auto end = base.find_first_of("/?#", scheme + 3U);
  return std::string{base.substr(0U, end)};
}

void AppendEscaped(std::string &target, const std::string_view value) {
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

std::string OpenAiProbeBody(const std::string_view model_id) {
  std::string result = "{\"model\":";
  AppendEscaped(result, model_id);
  result += ",\"messages\":[{\"role\":\"user\",\"content\":";
  AppendEscaped(result, kModelProbePrompt);
  result += "}],\"temperature\":0.2}";
  return result;
}

std::string AnthropicProbeBody(const std::string_view model_id) {
  std::string result = "{\"model\":";
  AppendEscaped(result, model_id);
  result += ",\"max_tokens\":4096,\"messages\":[{\"role\":\"user\",\"content\":";
  AppendEscaped(result, kModelProbePrompt);
  result += "}]}";
  return result;
}

std::uint32_t JavaStringHash(const std::string_view value) {
  std::uint32_t result{};
  for (const unsigned char character : value) {
    result = result * 31U + character;
  }
  return result;
}

std::string JavaHex(const std::uint32_t value) {
  char buffer[9]{};
  std::snprintf(buffer, sizeof(buffer), "%x", value);
  return buffer;
}

bool IsAzureResponsesEndpoint(const std::string_view base_url) {
  std::string normalized{base_url};
  std::ranges::transform(normalized, normalized.begin(), [](const char value) {
    return value >= 'A' && value <= 'Z'
               ? static_cast<char>(value - 'A' + 'a')
               : value;
  });
  return normalized.contains("openai.azure.") ||
         normalized.contains("cognitiveservices.azure.") ||
         normalized.contains("aoai.azure.") ||
         normalized.contains("azure-api.") ||
         normalized.contains("azurefd.") ||
         normalized.contains("windows.net/openai");
}

std::string CodexProbeBody(const std::string_view model_id,
                           const bool store) {
  constexpr std::string_view kInstallation =
      "21effb47-cc47-3fbd-a17c-31b0d3a0675e";
  std::string result = "{\"model\":";
  AppendEscaped(result, model_id);
  result += ",\"input\":[{\"type\":\"message\",\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":";
  AppendEscaped(result, kModelProbePrompt);
  result += "}]}],\"tools\":[],\"tool_choice\":\"auto\",\"parallel_tool_calls\":true,\"store\":";
  result += store ? "true" : "false";
  result += ",\"include\":[],\"prompt_cache_key\":\"linecode-codex-";
  result += JavaHex(JavaStringHash(model_id));
  result += "\",\"client_metadata\":{\"x-codex-installation-id\":\"";
  result += kInstallation;
  result += "\",\"x-codex-window-id\":\"";
  result += kInstallation;
  result += ":0\"}}";
  return result;
}

std::vector<std::pair<std::string, std::string>>
CommonHeaders(const std::string_view api_key) {
  return {{"Accept", "application/json"},
          {"Authorization", "Bearer " + std::string{api_key}}};
}

std::expected<JsonValue, ModelCatalogCodecError> Parse(const std::string_view json) {
  return JsonParser(json).Parse();
}

std::optional<ModelCatalogCodecError> ApiError(const JsonValue &root) {
  const auto *error = Member(root, "error");
  if (error == nullptr || std::holds_alternative<std::nullptr_t>(error->value)) {
    return std::nullopt;
  }
  if (const auto *message = StringValue(Member(*error, "message"));
      message != nullptr) {
    return ModelCatalogCodecError{.message = *message};
  }
  if (const auto *message = StringValue(error); message != nullptr) {
    return ModelCatalogCodecError{.message = *message};
  }
  return ModelCatalogCodecError{.message = "Model API returned an error"};
}

} // namespace

std::expected<ModelHttpRequestDescriptor, ModelCatalogCodecError>
BuildModelCatalogRequest(const domain::ModelProtocol protocol,
                         const std::string_view base_url,
                         const std::string_view api_key) {
  auto headers = CommonHeaders(api_key);
  switch (protocol) {
  case domain::ModelProtocol::openai_compatible:
    return ModelHttpRequestDescriptor{
        .url = AppendEndpoint(base_url, "/models"),
        .headers = std::move(headers),
        .body = {}};
  case domain::ModelProtocol::codex_responses:
    headers.emplace_back("version", kCodexProtocolVersion);
    headers.emplace_back("originator", kCodexOriginator);
    headers.emplace_back("User-Agent", "codex_cli_rs/0.120.0 (Android; LineCode)");
    return ModelHttpRequestDescriptor{
        .url = AppendEndpoint(base_url, "/models") +
               "?client_version=0.120.0",
        .headers = std::move(headers),
        .body = {}};
  case domain::ModelProtocol::anthropic_messages: {
    auto root = RootOrigin(base_url);
    if (!root.has_value()) {
      return std::unexpected(root.error());
    }
    return ModelHttpRequestDescriptor{
        .url = AppendEndpoint(*root, "/v1/models"),
        .headers = {{"Accept", "application/json"},
                    {"x-api-key", std::string{api_key}},
                    {"anthropic-version", "2023-06-01"}},
        .body = {}};
  }
  case domain::ModelProtocol::local_gguf:
    return std::unexpected(ModelCatalogCodecError{
        .message = "Local GGUF models do not expose an HTTP catalog"});
  }
  return std::unexpected(
      ModelCatalogCodecError{.message = "Unsupported model protocol"});
}

std::expected<ModelHttpRequestDescriptor, ModelCatalogCodecError>
BuildModelProbeRequest(const domain::ModelConfig &model) {
  auto headers = CommonHeaders(model.api_key);
  headers.emplace_back("Content-Type", "application/json");
  switch (model.protocol) {
  case domain::ModelProtocol::openai_compatible:
    return ModelHttpRequestDescriptor{
        .url = AppendEndpoint(model.base_url, "/chat/completions"),
        .headers = std::move(headers),
        .body = OpenAiProbeBody(model.model_id)};
  case domain::ModelProtocol::codex_responses: {
    auto endpoint = AppendEndpoint(model.base_url, "/responses");
    constexpr std::string_view kChatSuffix = "/chat/completions";
    const auto trimmed = Trim(model.base_url);
    if (trimmed.ends_with(kChatSuffix)) {
      endpoint = std::string{trimmed.substr(0U, trimmed.size() - kChatSuffix.size())} +
                 "/responses";
    }
    headers.emplace_back("version", kCodexProtocolVersion);
    headers.emplace_back("originator", kCodexOriginator);
    headers.emplace_back("User-Agent", "codex_cli_rs/0.120.0 (Android; LineCode)");
    return ModelHttpRequestDescriptor{.url = std::move(endpoint),
                                      .headers = std::move(headers),
                                      .body = CodexProbeBody(
                                          model.model_id,
                                          IsAzureResponsesEndpoint(
                                              model.base_url))};
  }
  case domain::ModelProtocol::anthropic_messages:
    return ModelHttpRequestDescriptor{
        .url = AppendEndpoint(model.base_url, "/v1/messages"),
        .headers = {{"Accept", "application/json"},
                    {"Content-Type", "application/json"},
                    {"x-api-key", model.api_key},
                    {"anthropic-version", "2023-06-01"}},
        .body = AnthropicProbeBody(model.model_id)};
  case domain::ModelProtocol::local_gguf:
    return std::unexpected(ModelCatalogCodecError{
        .message = "Local GGUF model probing is unavailable"});
  }
  return std::unexpected(
      ModelCatalogCodecError{.message = "Unsupported model protocol"});
}

std::expected<std::vector<std::string>, ModelCatalogCodecError>
DecodeModelCatalogResponse(const std::string_view json) {
  auto root = Parse(json);
  if (!root.has_value()) {
    return std::unexpected(root.error());
  }
  if (const auto error = ApiError(*root); error.has_value()) {
    return std::unexpected(*error);
  }
  const auto *data = ArrayValue(Member(*root, "data"));
  std::vector<std::string> ids;
  if (data != nullptr) {
    ids.reserve(data->size());
    for (const auto &item : *data) {
      if (const auto *id = StringValue(Member(item, "id"));
          id != nullptr && !id->empty()) {
        ids.push_back(*id);
      }
    }
  }
  std::ranges::sort(ids);
  return ids;
}

std::expected<std::string, ModelCatalogCodecError>
DecodeModelProbeResponse(const domain::ModelProtocol protocol,
                         const std::string_view json) {
  auto root = Parse(json);
  if (!root.has_value()) {
    return std::unexpected(root.error());
  }
  if (const auto error = ApiError(*root); error.has_value()) {
    return std::unexpected(*error);
  }
  switch (protocol) {
  case domain::ModelProtocol::openai_compatible: {
    const auto *choices = ArrayValue(Member(*root, "choices"));
    const auto *message = choices == nullptr || choices->empty()
                              ? nullptr
                              : Member(choices->front(), "message");
    const auto *content = message == nullptr
                              ? nullptr
                              : StringValue(Member(*message, "content"));
    if (content != nullptr) {
      return *content;
    }
    break;
  }
  case domain::ModelProtocol::codex_responses: {
    if (const auto *output = StringValue(Member(*root, "output_text"));
        output != nullptr) {
      return *output;
    }
    const auto *items = ArrayValue(Member(*root, "output"));
    std::string text;
    if (items != nullptr) {
      for (const auto &item : *items) {
        const auto *content = ArrayValue(Member(item, "content"));
        if (content == nullptr) {
          continue;
        }
        for (const auto &part : *content) {
          if (const auto *value = StringValue(Member(part, "text"));
              value != nullptr) {
            text += *value;
          }
        }
      }
    }
    if (!text.empty()) {
      return text;
    }
    break;
  }
  case domain::ModelProtocol::anthropic_messages: {
    const auto *content = ArrayValue(Member(*root, "content"));
    std::string text;
    if (content != nullptr) {
      for (const auto &part : *content) {
        const auto *type = StringValue(Member(part, "type"));
        const auto *value = StringValue(Member(part, "text"));
        if (value != nullptr && (type == nullptr || *type == "text")) {
          text += *value;
        }
      }
    }
    return text;
  }
  case domain::ModelProtocol::local_gguf:
    return std::unexpected(ModelCatalogCodecError{
        .message = "Local GGUF model probing is unavailable"});
  }
  return std::unexpected(ModelCatalogCodecError{
      .message = "Model probe response does not contain text"});
}

} // namespace linecode::infrastructure
