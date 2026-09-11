#include "infrastructure/image_understanding_codec.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <ranges>
#include <span>
#include <string_view>
#include <utility>

#include "infrastructure/archive_json.h"
#include "infrastructure/image_generation_codec.h"
#include "infrastructure/model_url_policy.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
using application::ImageUnderstandingError;
using application::ImageUnderstandingErrorCode;
using application::ImageUnderstandingResult;

constexpr std::size_t kMaximumImageBytes = 10U * 1024U * 1024U;
constexpr std::string_view kDefaultPrompt =
    "Please describe the content of this image.";

ImageUnderstandingError Error(ImageUnderstandingErrorCode code,
                              std::string message, int status = 0) {
  return {.code = code, .message = std::move(message), .http_status = status};
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
  if (model.context_size > 0 || !value.ends_with(']'))
    return std::string{value};
  const auto opening = value.rfind('[');
  if (opening == std::string_view::npos || opening == 0U)
    return std::string{value};
  const auto suffix = value.substr(opening + 1U, value.size() - opening - 2U);
  const auto numeric_end = suffix.find_first_not_of("0123456789.");
  const auto number = suffix.substr(0U, numeric_end);
  const auto unit = numeric_end == std::string_view::npos
                        ? std::string_view{}
                        : suffix.substr(numeric_end);
  if (number.empty() || number.find_first_not_of("0123456789.") !=
                            std::string_view::npos ||
      (unit != "k" && unit != "K" && unit != "m" && unit != "M" &&
       !unit.empty()))
    return std::string{value};
  return std::string{Trim(value.substr(0U, opening))};
}

std::string Endpoint(std::string base, std::string_view suffix) {
  while (base.ends_with('/'))
    base.pop_back();
  constexpr std::array known{
      std::string_view{"/chat/completions"},
      std::string_view{"/responses"},
      std::string_view{"/messages"},
  };
  for (const auto endpoint : known) {
    if (base.ends_with(endpoint)) {
      base.resize(base.size() - endpoint.size());
      break;
    }
  }
  if (suffix == "/messages" && base.ends_with("/v1"))
    return base + "/messages";
  return base + std::string{suffix};
}

std::string DataUrl(const domain::WorkspaceImage &image) {
  return "data:" + image.mime_type + ";base64," + Base64Encode(image.bytes);
}

ImageUnderstandingResult<ImageUnderstandingHttpRequest>
BuildOpenAi(const domain::ModelConfig &model, std::string_view system_prompt,
            const domain::ImageUnderstandingRequest &request,
            const domain::WorkspaceImage &image) {
  const auto data_url = DataUrl(image);
  json::Array messages{
      json::Object{{"role", "system"},
                   {"content", std::string{system_prompt}}},
      json::Object{
          {"role", "user"},
          {"content",
           json::Array{
               json::Object{{"type", "text"}, {"text", request.prompt}},
               json::Object{{"type", "image_url"},
                            {"image_url", json::Object{{"url", data_url}}}},
           }}},
  };
  return ImageUnderstandingHttpRequest{
      .url = Endpoint(model.base_url, "/chat/completions"),
      .headers = {{"Authorization", "Bearer " + model.api_key}},
      .body = json::Serialize(json::Object{{"model", ApiModelId(model)},
                                           {"messages", std::move(messages)},
                                           {"stream", false}}),
  };
}

ImageUnderstandingResult<ImageUnderstandingHttpRequest>
BuildCodex(const domain::ModelConfig &model, std::string_view system_prompt,
           const domain::ImageUnderstandingRequest &request,
           const domain::WorkspaceImage &image) {
  json::Array content{
      json::Object{{"type", "input_text"}, {"text", request.prompt}},
      json::Object{{"type", "input_image"}, {"image_url", DataUrl(image)}},
  };
  json::Array input{json::Object{{"type", "message"},
                                {"role", "user"},
                                {"content", std::move(content)}}};
  return ImageUnderstandingHttpRequest{
      .url = Endpoint(model.base_url, "/responses"),
      .headers = {{"Authorization", "Bearer " + model.api_key},
                  {"version", "0.120.0"},
                  {"originator", "codex_cli_rs"},
                  {"User-Agent", "codex_cli_rs/0.120.0 (Android; LineCode)"}},
      .body = json::Serialize(json::Object{
          {"model", ApiModelId(model)},
          {"instructions", std::string{system_prompt}},
          {"input", std::move(input)},
          {"store", false},
          {"stream", false},
      }),
  };
}

ImageUnderstandingResult<ImageUnderstandingHttpRequest>
BuildAnthropic(const domain::ModelConfig &model,
               std::string_view system_prompt,
               const domain::ImageUnderstandingRequest &request,
               const domain::WorkspaceImage &image) {
  json::Array content{
      json::Object{{"type", "text"}, {"text", request.prompt}},
      json::Object{{"type", "image"},
                   {"source", json::Object{{"type", "base64"},
                                            {"media_type", image.mime_type},
                                            {"data", Base64Encode(image.bytes)}}}},
  };
  return ImageUnderstandingHttpRequest{
      .url = Endpoint(model.base_url, "/messages"),
      .headers = {{"anthropic-version", "2023-06-01"},
                  {"x-api-key", model.api_key}},
      .body = json::Serialize(json::Object{
          {"model", ApiModelId(model)},
          {"system", std::string{system_prompt}},
          {"max_tokens", std::int64_t{4096}},
          {"messages", json::Array{json::Object{{"role", "user"},
                                                {"content", std::move(content)}}}},
          {"stream", false},
      }),
  };
}

const std::string *Text(const json::Object &object, std::string_view key) {
  return json::AsString(json::Find(object, key));
}

std::string TextOr(const json::Object &object, std::string_view key) {
  const auto *value = Text(object, key);
  return value == nullptr ? std::string{} : *value;
}

ImageUnderstandingResult<json::Object> ParseRoot(std::string_view body) {
  auto parsed = json::Parse(body);
  const auto *root = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!root) {
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::decode,
        parsed ? "Vision response must be a JSON object"
               : "Invalid vision response JSON: " + parsed.error().message));
  }
  if (const auto *api_error = json::AsObject(json::Find(*root, "error"))) {
    return std::unexpected(
        Error(ImageUnderstandingErrorCode::decode,
              TextOr(*api_error, "message").empty()
                  ? json::Serialize(*api_error)
                  : TextOr(*api_error, "message")));
  }
  return *root;
}

std::string TextParts(const json::Array &parts) {
  std::string result;
  for (const auto &value : parts) {
    const auto *part = json::AsObject(&value);
    if (!part)
      continue;
    const auto type = TextOr(*part, "type");
    if (type == "text" || type == "output_text")
      result += TextOr(*part, "text");
  }
  return result;
}

ImageUnderstandingResult<std::string> DecodeOpenAi(std::string_view body) {
  auto root = ParseRoot(body);
  if (!root)
    return std::unexpected(std::move(root.error()));
  const auto *choices = json::AsArray(json::Find(*root, "choices"));
  const auto *choice = choices && !choices->empty()
                           ? json::AsObject(&choices->front())
                           : nullptr;
  const auto *message =
      choice ? json::AsObject(json::Find(*choice, "message")) : nullptr;
  if (!message)
    return std::unexpected(Error(ImageUnderstandingErrorCode::decode,
                                 "Vision response has no assistant message"));
  if (const auto *text = Text(*message, "content"); text && !text->empty())
    return *text;
  if (const auto *parts = json::AsArray(json::Find(*message, "content"))) {
    if (auto text = TextParts(*parts); !text.empty())
      return text;
  }
  return std::unexpected(Error(ImageUnderstandingErrorCode::decode,
                               "Vision response has no text content"));
}

ImageUnderstandingResult<std::string> DecodeCodex(std::string_view body) {
  auto root = ParseRoot(body);
  if (!root)
    return std::unexpected(std::move(root.error()));
  std::string result = TextOr(*root, "output_text");
  std::string structured_result;
  const auto *output = json::AsArray(json::Find(*root, "output"));
  if (output) {
    for (const auto &value : *output) {
      const auto *item = json::AsObject(&value);
      const auto *parts =
          item ? json::AsArray(json::Find(*item, "content")) : nullptr;
      if (parts)
        structured_result += TextParts(*parts);
    }
  }
  if (result.empty())
    result = std::move(structured_result);
  if (result.empty())
    return std::unexpected(Error(ImageUnderstandingErrorCode::decode,
                                 "Responses vision result has no output text"));
  return result;
}

ImageUnderstandingResult<std::string> DecodeAnthropic(std::string_view body) {
  auto root = ParseRoot(body);
  if (!root)
    return std::unexpected(std::move(root.error()));
  const auto *content = json::AsArray(json::Find(*root, "content"));
  if (!content)
    return std::unexpected(Error(ImageUnderstandingErrorCode::decode,
                                 "Anthropic vision result has no content"));
  auto result = TextParts(*content);
  if (result.empty())
    return std::unexpected(Error(ImageUnderstandingErrorCode::decode,
                                 "Anthropic vision result has no text"));
  return result;
}

using BuildFunction = ImageUnderstandingResult<ImageUnderstandingHttpRequest> (*)(
    const domain::ModelConfig &, std::string_view,
    const domain::ImageUnderstandingRequest &, const domain::WorkspaceImage &);
using DecodeFunction = ImageUnderstandingResult<std::string> (*)(
    std::string_view);

struct ProtocolStrategy final {
  domain::ModelProtocol protocol;
  BuildFunction build;
  DecodeFunction decode;
};

constexpr std::array strategies{
    ProtocolStrategy{domain::ModelProtocol::openai_compatible, BuildOpenAi,
                     DecodeOpenAi},
    ProtocolStrategy{domain::ModelProtocol::codex_responses, BuildCodex,
                     DecodeCodex},
    ProtocolStrategy{domain::ModelProtocol::anthropic_messages,
                     BuildAnthropic, DecodeAnthropic},
};

const ProtocolStrategy *Strategy(domain::ModelProtocol protocol) {
  const auto found =
      std::ranges::find(strategies, protocol, &ProtocolStrategy::protocol);
  return found == strategies.end() ? nullptr : &*found;
}

template <std::size_t Size>
bool StartsWith(std::span<const std::byte> bytes,
                const std::array<unsigned char, Size> &signature) {
  return bytes.size() >= Size &&
         std::ranges::equal(signature, bytes.first(Size), {},
                            [](unsigned char value) {
                              return static_cast<std::byte>(value);
                            });
}

bool MatchesMime(std::string_view mime, std::span<const std::byte> bytes) {
  if (mime == "image/png")
    return StartsWith(bytes, std::array<unsigned char, 8>{
                                 0x89, 0x50, 0x4E, 0x47,
                                 0x0D, 0x0A, 0x1A, 0x0A});
  if (mime == "image/jpeg")
    return StartsWith(bytes,
                      std::array<unsigned char, 3>{0xFF, 0xD8, 0xFF});
  if (mime == "image/gif")
    return StartsWith(bytes,
                      std::array<unsigned char, 4>{'G', 'I', 'F', '8'});
  return bytes.size() >= 12U &&
         StartsWith(bytes,
                    std::array<unsigned char, 4>{'R', 'I', 'F', 'F'}) &&
         bytes[8] == std::byte{'W'} && bytes[9] == std::byte{'E'} &&
         bytes[10] == std::byte{'B'} && bytes[11] == std::byte{'P'};
}

std::string MimeFromPath(std::string_view path) {
  const auto lower = Lower(path);
  if (lower.ends_with(".png"))
    return "image/png";
  if (lower.ends_with(".jpg") || lower.ends_with(".jpeg"))
    return "image/jpeg";
  if (lower.ends_with(".webp"))
    return "image/webp";
  if (lower.ends_with(".gif"))
    return "image/gif";
  return {};
}

} // namespace

ImageUnderstandingResult<ImageUnderstandingHttpRequest>
BuildImageUnderstandingRequest(
    const domain::ModelConfig &model, std::string_view system_prompt,
    const domain::ImageUnderstandingRequest &request,
    const domain::WorkspaceImage &image) {
  if (request.prompt.empty() || image.bytes.empty())
    return std::unexpected(Error(ImageUnderstandingErrorCode::invalid_arguments,
                                 "Vision prompt and image are required"));
  if (model.base_url.empty() || model.api_key.empty() || model.model_id.empty())
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::invalid_configuration,
        "Image understanding model requires Base URL, API Key, and model ID"));
  auto url = ValidateModelBaseUrl(model.base_url);
  if (!url)
    return std::unexpected(Error(ImageUnderstandingErrorCode::invalid_configuration,
                                 url.error().message));
  const auto *strategy = Strategy(model.protocol);
  if (!strategy)
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::unsupported_protocol,
        "The selected model protocol does not support image understanding"));
  auto normalized = model;
  normalized.base_url = std::move(*url);
  return strategy->build(normalized, system_prompt, request, image);
}

ImageUnderstandingResult<std::string>
DecodeImageUnderstandingResponse(domain::ModelProtocol protocol,
                                 std::string_view body) {
  const auto *strategy = Strategy(protocol);
  if (!strategy)
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::unsupported_protocol,
        "The selected model protocol does not support image understanding"));
  return strategy->decode(body);
}

ImageUnderstandingResult<domain::ImageUnderstandingRequest>
JsonImageUnderstandingToolCodec::DecodeArguments(
    std::string arguments_json) const {
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!object)
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::invalid_arguments,
        parsed ? "Image understanding arguments must be a JSON object"
               : "Invalid image understanding arguments: " +
                     parsed.error().message));
  std::string path;
  constexpr std::array keys{std::string_view{"path"},
                            std::string_view{"image_path"},
                            std::string_view{"file_path"}};
  for (const auto key : keys) {
    if (const auto *value = Text(*object, key); value && !Trim(*value).empty()) {
      path = std::string{Trim(*value)};
      break;
    }
  }
  if (path.empty())
    return std::unexpected(Error(ImageUnderstandingErrorCode::invalid_arguments,
                                 "Image path is required"));
  auto prompt = std::string{Trim(TextOr(*object, "prompt"))};
  if (prompt.empty())
    prompt = kDefaultPrompt;
  return domain::ImageUnderstandingRequest{.path = std::move(path),
                                           .prompt = std::move(prompt)};
}

ImageUnderstandingResult<domain::WorkspaceImage>
JsonImageUnderstandingToolCodec::ValidateImage(
    std::string resolved_path, std::vector<std::byte> bytes) const {
  if (bytes.empty())
    return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                 "Image file is empty"));
  if (bytes.size() > kMaximumImageBytes)
    return std::unexpected(Error(ImageUnderstandingErrorCode::too_large,
                                 "Image exceeds the 10 MB safety limit"));
  auto mime = MimeFromPath(resolved_path);
  if (mime.empty())
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::unsupported_format,
        "Only PNG, JPEG, WebP, and GIF images are supported"));
  if (!MatchesMime(mime, bytes))
    return std::unexpected(Error(
        ImageUnderstandingErrorCode::unsupported_format,
        "Image signature does not match its supported file format"));
  return domain::WorkspaceImage{.resolved_path = std::move(resolved_path),
                                .mime_type = std::move(mime),
                                .bytes = std::move(bytes)};
}

} // namespace linecode::infrastructure
