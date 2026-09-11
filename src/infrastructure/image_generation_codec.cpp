#include "infrastructure/image_generation_codec.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstddef>
#include <ranges>
#include <span>
#include <string_view>

#include "infrastructure/archive_json.h"
#include "infrastructure/model_url_policy.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
using application::ImageGenerationError;
using application::ImageGenerationErrorCode;
using application::ImageGenerationResult;

ImageGenerationError Error(ImageGenerationErrorCode code,
                           std::string message) {
  return {.code = code, .message = std::move(message)};
}

std::string_view Trim(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos)
    return {};
  const auto last = value.find_last_not_of(" \t\r\n");
  return value.substr(first, last - first + 1U);
}

std::string Lower(std::string_view value) {
  std::string result{value};
  std::ranges::transform(result, result.begin(), [](unsigned char character) {
    return static_cast<char>(std::tolower(character));
  });
  return result;
}

std::string ApiModelId(const domain::ModelConfig &model) {
  const auto value = Trim(model.model_id);
  if (model.context_size > 0)
    return std::string{value};
  if (!value.ends_with(']'))
    return std::string{value};
  const auto opening = value.rfind('[');
  if (opening == std::string_view::npos || opening == 0U)
    return std::string{value};
  const auto suffix = value.substr(opening + 1U, value.size() - opening - 2U);
  if (suffix.empty())
    return std::string{value};
  const auto numeric_end = suffix.find_first_not_of("0123456789.");
  const auto number = suffix.substr(0U, numeric_end);
  const auto unit = numeric_end == std::string_view::npos
                        ? std::string_view{}
                        : suffix.substr(numeric_end);
  if (number.empty() || number.find_first_not_of("0123456789.") !=
                            std::string_view::npos ||
      (unit != "k" && unit != "K" && unit != "m" && unit != "M" &&
       !unit.empty())) {
    return std::string{value};
  }
  return std::string{Trim(value.substr(0U, opening))};
}

std::string Endpoint(std::string base, std::string_view suffix) {
  while (base.ends_with('/'))
    base.pop_back();
  constexpr std::array known_endpoints{
      std::string_view{"/chat/completions"},
      std::string_view{"/responses"},
      std::string_view{"/images/generations"},
  };
  for (const auto endpoint : known_endpoints) {
    if (base.ends_with(endpoint)) {
      base.resize(base.size() - endpoint.size());
      break;
    }
  }
  return base + std::string{suffix};
}

void PutIfPresent(json::Object &body, std::string key,
                  std::string_view value) {
  const auto trimmed = Trim(value);
  if (!trimmed.empty())
    body.emplace(std::move(key), std::string{trimmed});
}

bool ShouldRequestBase64(const domain::ModelConfig &model) {
  const auto id = Lower(ApiModelId(model));
  return !id.starts_with("gpt-image-") && id != "chatgpt-image-latest";
}

ImageGenerationResult<ImageHttpRequestDescriptor>
BuildOpenAi(const domain::ModelConfig &model,
            const domain::ImageGenerationRequest &request,
            bool request_base64) {
  json::Object body{{"model", ApiModelId(model)},
                    {"prompt", request.prompt},
                    {"n", std::int64_t{1}},
                    {"size", request.size.empty()
                                 ? std::string{"1024x1024"}
                                 : request.size}};
  PutIfPresent(body, "quality", request.quality);
  PutIfPresent(body, "background", request.background);
  if (request_base64 && ShouldRequestBase64(model))
    body.emplace("response_format", "b64_json");
  return ImageHttpRequestDescriptor{
      .url = Endpoint(model.base_url, "/images/generations"),
      .headers = {{"Authorization", "Bearer " + model.api_key}},
      .body = json::Serialize(body),
  };
}

ImageGenerationResult<ImageHttpRequestDescriptor>
BuildCodex(const domain::ModelConfig &model,
           const domain::ImageGenerationRequest &request, bool) {
  json::Object tool{{"type", "image_generation"}, {"action", "generate"}};
  PutIfPresent(tool, "size", request.size);
  PutIfPresent(tool, "quality", request.quality);
  PutIfPresent(tool, "background", request.background);
  json::Object body{
      {"model", ApiModelId(model)},
      {"input", request.prompt},
      {"tools", json::Array{std::move(tool)}},
      {"tool_choice", json::Object{{"type", "image_generation"}}},
      {"store", false},
  };
  return ImageHttpRequestDescriptor{
      .url = Endpoint(model.base_url, "/responses"),
      .headers = {{"Authorization", "Bearer " + model.api_key},
                  {"version", "0.120.0"},
                  {"originator", "codex_cli_rs"},
                  {"User-Agent", "codex_cli_rs/0.120.0 (Android; LineCode)"}},
      .body = json::Serialize(body),
  };
}

const std::string *Text(const json::Object &object, std::string_view key) {
  return json::AsString(json::Find(object, key));
}

std::string TextOr(const json::Object &object, std::string_view key,
                   std::string fallback = {}) {
  if (const auto *text = Text(object, key))
    return *text;
  return fallback;
}

ImageGenerationResult<ImagePayloadCandidate>
CandidateFromObject(const json::Object &item) {
  const auto revised = TextOr(item, "revised_prompt");
  auto mime = TextOr(item, "mime_type", "image/png");
  if (mime.empty() || Lower(mime) == "null")
    mime = "image/png";
  if (const auto *base64 = Text(item, "b64_json");
      base64 && IsUsableImageBase64(*base64)) {
    return ImagePayloadCandidate{.source = ImagePayloadSource::base64,
                                 .payload = *base64,
                                 .mime_type = std::move(mime),
                                 .revised_prompt = revised};
  }
  if (const auto *data_url = Text(item, "data_url");
      data_url && IsUsableImageDataUrl(*data_url)) {
    return ImagePayloadCandidate{.source = ImagePayloadSource::data_url,
                                 .payload = *data_url,
                                 .mime_type = std::move(mime),
                                 .revised_prompt = revised};
  }
  if (const auto *url = Text(item, "url"); url && !Trim(*url).empty() &&
                                               Lower(Trim(*url)) != "null") {
    return ImagePayloadCandidate{.source = ImagePayloadSource::remote_url,
                                 .payload = std::string{Trim(*url)},
                                 .mime_type = std::move(mime),
                                 .revised_prompt = revised};
  }
  return std::unexpected(
      Error(ImageGenerationErrorCode::decode,
            "Image API did not return usable b64_json, data_url, or url"));
}

ImageGenerationResult<json::Object> ParseRoot(std::string_view body) {
  auto parsed = json::Parse(body);
  const auto *root = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!root) {
    return std::unexpected(Error(
        ImageGenerationErrorCode::decode,
        parsed ? "Image API response must be a JSON object"
               : "Invalid image API JSON: " + parsed.error().message));
  }
  if (const auto *error = json::AsObject(json::Find(*root, "error"))) {
    return std::unexpected(Error(ImageGenerationErrorCode::decode,
                                 TextOr(*error, "message",
                                        json::Serialize(*error))));
  }
  return *root;
}

ImageGenerationResult<ImagePayloadCandidate>
DecodeOpenAi(std::string_view body) {
  auto root = ParseRoot(body);
  if (!root)
    return std::unexpected(std::move(root.error()));
  const auto *data = json::AsArray(json::Find(*root, "data"));
  const auto *item = data && !data->empty() ? json::AsObject(&data->front())
                                            : nullptr;
  if (!item) {
    return std::unexpected(Error(ImageGenerationErrorCode::decode,
                                 "Image API did not return image data"));
  }
  return CandidateFromObject(*item);
}

ImageGenerationResult<ImagePayloadCandidate>
DecodeCodex(std::string_view body) {
  auto root = ParseRoot(body);
  if (!root)
    return std::unexpected(std::move(root.error()));
  const auto *output = json::AsArray(json::Find(*root, "output"));
  if (!output) {
    return std::unexpected(Error(ImageGenerationErrorCode::decode,
                                 "Responses API did not return output"));
  }
  for (const auto &value : *output) {
    const auto *item = json::AsObject(&value);
    if (!item || TextOr(*item, "type") != "image_generation_call")
      continue;
    const auto result = TextOr(*item, "result");
    const auto revised = TextOr(*item, "revised_prompt");
    if (IsUsableImageDataUrl(result)) {
      return ImagePayloadCandidate{.source = ImagePayloadSource::data_url,
                                   .payload = result,
                                   .mime_type = "image/png",
                                   .revised_prompt = revised};
    }
    if (IsUsableImageBase64(result)) {
      return ImagePayloadCandidate{.source = ImagePayloadSource::base64,
                                   .payload = result,
                                   .mime_type = "image/png",
                                   .revised_prompt = revised};
    }
    if (TextOr(*item, "status") == "failed") {
      return std::unexpected(
          Error(ImageGenerationErrorCode::decode,
                "Responses API image generation failed"));
    }
  }
  return std::unexpected(
      Error(ImageGenerationErrorCode::decode,
            "Responses API did not return a usable image generation result"));
}

using BuildFunction = ImageGenerationResult<ImageHttpRequestDescriptor> (*)(
    const domain::ModelConfig &, const domain::ImageGenerationRequest &, bool);
using DecodeFunction = ImageGenerationResult<ImagePayloadCandidate> (*)(
    std::string_view);

struct ProtocolStrategy final {
  domain::ModelProtocol protocol;
  BuildFunction build;
  DecodeFunction decode;
};

constexpr std::array protocol_strategies{
    ProtocolStrategy{domain::ModelProtocol::openai_compatible, BuildOpenAi,
                     DecodeOpenAi},
    ProtocolStrategy{domain::ModelProtocol::codex_responses, BuildCodex,
                     DecodeCodex},
};

const ProtocolStrategy *StrategyFor(domain::ModelProtocol protocol) noexcept {
  const auto found = std::ranges::find(protocol_strategies, protocol,
                                       &ProtocolStrategy::protocol);
  return found == protocol_strategies.end() ? nullptr : &*found;
}

int Base64Digit(char value) noexcept {
  if (value >= 'A' && value <= 'Z')
    return value - 'A';
  if (value >= 'a' && value <= 'z')
    return value - 'a' + 26;
  if (value >= '0' && value <= '9')
    return value - '0' + 52;
  if (value == '+' || value == '-')
    return 62;
  if (value == '/' || value == '_')
    return 63;
  return -1;
}

std::vector<std::byte> DecodePrefix(std::string_view value,
                                    std::size_t maximum) {
  std::vector<std::byte> output;
  output.reserve(maximum);
  unsigned buffer{};
  unsigned bits{};
  bool padding{};
  for (const char character : value) {
    if (output.size() == maximum)
      break;
    if (std::isspace(static_cast<unsigned char>(character)))
      continue;
    if (character == '=') {
      padding = true;
      continue;
    }
    const int digit = Base64Digit(character);
    if (digit < 0 || padding)
      return {};
    buffer = (buffer << 6U) | static_cast<unsigned>(digit);
    bits += 6U;
    if (bits >= 8U) {
      bits -= 8U;
      output.push_back(
          static_cast<std::byte>((buffer >> bits) & 0xFFU));
    }
  }
  return output;
}

template <std::size_t Size>
bool StartsWith(std::span<const std::byte> bytes,
                const std::array<unsigned char, Size> &signature) noexcept {
  return bytes.size() >= signature.size() &&
         std::ranges::equal(signature, bytes.first(signature.size()), {},
                            [](unsigned char value) {
                              return static_cast<std::byte>(value);
                            });
}

std::string MarkdownAlt(std::string_view prompt) {
  std::string result;
  result.reserve(std::min<std::size_t>(prompt.size(), 80U));
  for (const char character : prompt) {
    result.push_back(character == '\n' || character == '\r' ||
                             character == '[' || character == ']'
                         ? ' '
                         : character);
    if (result.size() == 80U)
      break;
  }
  if (prompt.size() > 80U) {
    result.resize(77U);
    result += "...";
  }
  const auto trimmed = Trim(result);
  return trimmed.empty() ? "Generated image" : std::string{trimmed};
}

std::string ModelSummary(std::string_view prompt) {
  const auto trimmed = Trim(prompt);
  if (trimmed.size() <= 500U)
    return std::string{trimmed};
  return std::string{trimmed.substr(0U, 497U)} + "...";
}

} // namespace

ImageGenerationResult<ImageHttpRequestDescriptor>
BuildImageGenerationRequest(const domain::ModelConfig &model,
                            const domain::ImageGenerationRequest &request,
                            bool request_base64) {
  if (request.prompt.empty()) {
    return std::unexpected(Error(ImageGenerationErrorCode::invalid_arguments,
                                 "Image generation prompt is required"));
  }
  if (model.base_url.empty() || model.api_key.empty() ||
      model.model_id.empty()) {
    return std::unexpected(
        Error(ImageGenerationErrorCode::invalid_configuration,
              "Image generation model requires Base URL, API Key, and model ID"));
  }
  auto validated = ValidateModelBaseUrl(model.base_url);
  if (!validated) {
    return std::unexpected(Error(ImageGenerationErrorCode::invalid_configuration,
                                 validated.error().message));
  }
  const auto *strategy = StrategyFor(model.protocol);
  if (!strategy) {
    return std::unexpected(Error(
        ImageGenerationErrorCode::unsupported_protocol,
        "The selected model protocol does not support image generation"));
  }
  auto normalized = model;
  normalized.base_url = std::move(*validated);
  return strategy->build(normalized, request, request_base64);
}

ImageGenerationResult<ImagePayloadCandidate>
DecodeImageGenerationResponse(domain::ModelProtocol protocol,
                              std::string_view body) {
  const auto *strategy = StrategyFor(protocol);
  if (!strategy) {
    return std::unexpected(Error(
        ImageGenerationErrorCode::unsupported_protocol,
        "The selected model protocol does not support image generation"));
  }
  return strategy->decode(body);
}

bool IsUsableImageBase64(std::string_view payload) noexcept {
  const auto bytes = DecodePrefix(Trim(payload), 16U);
  const std::span view{bytes};
  return StartsWith(view,
                    std::array<unsigned char, 8>{0x89, 0x50, 0x4E, 0x47,
                                                 0x0D, 0x0A, 0x1A, 0x0A}) ||
         StartsWith(view, std::array<unsigned char, 3>{0xFF, 0xD8, 0xFF}) ||
         StartsWith(view, std::array<unsigned char, 4>{'G', 'I', 'F', '8'}) ||
         StartsWith(view, std::array<unsigned char, 2>{'B', 'M'}) ||
         (view.size() >= 12U &&
          StartsWith(view, std::array<unsigned char, 4>{'R', 'I', 'F', 'F'}) &&
          view[8] == std::byte{'W'} && view[9] == std::byte{'E'} &&
          view[10] == std::byte{'B'} && view[11] == std::byte{'P'}) ||
         (view.size() >= 12U && view[4] == std::byte{'f'} &&
          view[5] == std::byte{'t'} && view[6] == std::byte{'y'} &&
          view[7] == std::byte{'p'} && view[8] == std::byte{'a'} &&
          view[9] == std::byte{'v'} && view[10] == std::byte{'i'} &&
          view[11] == std::byte{'f'});
}

bool IsUsableImageDataUrl(std::string_view value) noexcept {
  const auto lower = Lower(value);
  if (!lower.starts_with("data:image/"))
    return false;
  const auto comma = value.find(',');
  return comma != std::string_view::npos &&
         lower.substr(0U, comma).ends_with(";base64") &&
         IsUsableImageBase64(value.substr(comma + 1U));
}

std::string Base64Encode(std::span<const std::byte> bytes) {
  constexpr std::string_view alphabet =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  std::string output;
  output.reserve(((bytes.size() + 2U) / 3U) * 4U);
  for (std::size_t index = 0; index < bytes.size(); index += 3U) {
    const auto first = std::to_integer<unsigned>(bytes[index]);
    const auto second = index + 1U < bytes.size()
                            ? std::to_integer<unsigned>(bytes[index + 1U])
                            : 0U;
    const auto third = index + 2U < bytes.size()
                           ? std::to_integer<unsigned>(bytes[index + 2U])
                           : 0U;
    const unsigned value = (first << 16U) | (second << 8U) | third;
    output.push_back(alphabet[(value >> 18U) & 0x3FU]);
    output.push_back(alphabet[(value >> 12U) & 0x3FU]);
    output.push_back(index + 1U < bytes.size()
                         ? alphabet[(value >> 6U) & 0x3FU]
                         : '=');
    output.push_back(index + 2U < bytes.size() ? alphabet[value & 0x3FU]
                                               : '=');
  }
  return output;
}

ImageGenerationResult<domain::ImageGenerationRequest>
JsonImageGenerationToolCodec::DecodeArguments(
    std::string arguments_json) const {
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!object) {
    return std::unexpected(Error(
        ImageGenerationErrorCode::invalid_arguments,
        parsed ? "Image generation arguments must be a JSON object"
               : "Invalid image generation arguments: " +
                     parsed.error().message));
  }
  domain::ImageGenerationRequest request{
      .prompt = TextOr(*object, "prompt"),
      .size = TextOr(*object, "size", "1024x1024"),
      .quality = TextOr(*object, "quality"),
      .background = TextOr(*object, "background"),
  };
  request.prompt = std::string{Trim(request.prompt)};
  if (request.prompt.empty()) {
    return std::unexpected(Error(ImageGenerationErrorCode::invalid_arguments,
                                 "Image generation prompt is required"));
  }
  return request;
}

std::string JsonImageGenerationToolCodec::EncodeToolResult(
    const domain::ImageGenerationRequest &request,
    const domain::GeneratedImage &image) const {
  const auto markdown = "![" + MarkdownAlt(request.prompt) + "](" +
                        image.data_url + ")";
  return json::Serialize(json::Object{
      {"linecode_image_generation", true},
      {"display_markdown", markdown},
      {"model_content", "Generated image for: " + ModelSummary(request.prompt)},
      {"mime_type", image.mime_type},
      {"prompt", request.prompt},
      {"revised_prompt", image.revised_prompt},
  });
}

} // namespace linecode::infrastructure
