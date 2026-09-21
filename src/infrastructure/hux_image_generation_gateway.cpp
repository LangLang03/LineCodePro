#include "infrastructure/hux_image_generation_gateway.h"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstddef>
#include <expected>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/image_generation_codec.h"

namespace linecode::infrastructure {
namespace {

using application::ImageGenerationError;
using application::ImageGenerationErrorCode;
using application::ImageGenerationResult;

constexpr std::size_t kMaximumResponseBytes = 24U * 1024U * 1024U;
constexpr std::size_t kMaximumDownloadBytes = 12U * 1024U * 1024U;
constexpr std::size_t kMaximumErrorBytes = 4U * 1024U;
constexpr std::size_t kReadChunkBytes = 64U * 1024U;
constexpr auto kGenerationTimeout = std::chrono::minutes{3};
constexpr auto kDownloadTimeout = std::chrono::minutes{2};

ImageGenerationError Error(ImageGenerationErrorCode code,
                           std::string message) {
  return {.code = code, .message = std::move(message)};
}

huxerui::Bytes BytesFromString(std::string_view text) {
  const auto *first = reinterpret_cast<const std::byte *>(text.data());
  return text.empty() ? huxerui::Bytes{}
                      : huxerui::Bytes(first, first + text.size());
}

std::vector<huxerui::HttpHeader> HeadersFrom(
    std::vector<std::pair<std::string, std::string>> values) {
  std::vector<huxerui::HttpHeader> headers;
  headers.reserve(values.size() + 1U);
  headers.push_back({.name = "Content-Type", .value = "application/json"});
  for (auto &[name, value] : values) {
    headers.push_back(
        {.name = std::move(name), .value = std::move(value)});
  }
  return headers;
}

huxerui::HttpRequest ToRequest(ImageHttpRequestDescriptor descriptor) {
  return {.url = std::move(descriptor.url),
          .method = huxerui::HttpMethod::Post,
          .headers = HeadersFrom(std::move(descriptor.headers)),
          .body = BytesFromString(descriptor.body),
          .timeout = kGenerationTimeout};
}

std::string Lower(std::string_view value) {
  std::string lowered{value};
  std::ranges::transform(lowered, lowered.begin(), [](unsigned char value) {
    return static_cast<char>(std::tolower(value));
  });
  return lowered;
}

std::optional<std::string>
HeaderValue(const std::vector<huxerui::HttpHeader> &headers,
            std::string_view name) {
  const auto lower_name = Lower(name);
  const auto found = std::ranges::find_if(headers, [&](const auto &header) {
    return Lower(header.name) == lower_name;
  });
  return found == headers.end() ? std::nullopt
                                : std::optional{found->value};
}

std::string NormalizeMime(std::string value) {
  if (const auto semicolon = value.find(';'); semicolon != std::string::npos)
    value.resize(semicolon);
  value = Lower(value);
  return value.starts_with("image/") ? std::move(value) : "image/png";
}

std::string MimeFromDataUrl(std::string_view data_url) {
  const auto colon = data_url.find(':');
  const auto semicolon = data_url.find(';');
  return colon != std::string_view::npos && semicolon > colon
             ? NormalizeMime(
                   std::string{data_url.substr(colon + 1U,
                                                semicolon - colon - 1U)})
             : "image/png";
}

struct BufferedResponse final {
  int status{};
  std::vector<huxerui::HttpHeader> headers;
  huxerui::Bytes body;
};

huxerui::Task<ImageGenerationResult<BufferedResponse>> ReadBounded(
    huxerui::HttpResult<huxerui::HttpResponseStream> opened,
    std::size_t maximum_success_bytes) {
  if (!opened.Succeeded()) {
    co_return std::unexpected(
        Error(ImageGenerationErrorCode::transport, opened.Error().message));
  }
  auto stream = std::move(opened).Value();
  const auto status = stream.StatusCode();
  const auto header_view = stream.Headers();
  std::vector<huxerui::HttpHeader> headers(header_view.begin(),
                                           header_view.end());
  huxerui::Bytes body;
  const auto limit = status >= 200 && status < 300 ? maximum_success_bytes
                                                   : kMaximumErrorBytes;
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(
          Error(ImageGenerationErrorCode::transport, read.Error().message));
    }
    auto chunk = std::move(read).Value();
    if (chunk.empty())
      break;
    if (chunk.size() > limit - std::min(limit, body.size())) {
      co_return std::unexpected(Error(
          ImageGenerationErrorCode::response_too_large,
          status >= 200 && status < 300
              ? "Image API response exceeds the safety limit"
              : "Image API error response exceeds the safety limit"));
    }
    body.insert(body.end(), chunk.begin(), chunk.end());
  }
  if (status < 200 || status >= 300) {
    co_return std::unexpected(Error(
        ImageGenerationErrorCode::transport,
        "HTTP " + std::to_string(status) +
            (body.empty()
                 ? std::string{}
                 : ": " + std::string{
                              reinterpret_cast<const char *>(body.data()),
                              body.size()})));
  }
  co_return BufferedResponse{.status = status,
                             .headers = std::move(headers),
                             .body = std::move(body)};
}

huxerui::Task<ImageGenerationResult<BufferedResponse>> PostJson(
    const std::shared_ptr<huxerui::HttpClient> &http,
    ImageHttpRequestDescriptor descriptor) {
  co_return co_await ReadBounded(
      co_await http->SendStreamAsync(ToRequest(std::move(descriptor))),
      kMaximumResponseBytes);
}

huxerui::Task<ImageGenerationResult<domain::GeneratedImage>> ResolveCandidate(
    const std::shared_ptr<huxerui::HttpClient> &http,
    ImagePayloadCandidate candidate) {
  if (candidate.source == ImagePayloadSource::base64) {
    co_return domain::GeneratedImage{
        .mime_type = NormalizeMime(candidate.mime_type),
        .data_url = "data:" + NormalizeMime(candidate.mime_type) +
                    ";base64," + candidate.payload,
        .revised_prompt = std::move(candidate.revised_prompt)};
  }
  if (candidate.source == ImagePayloadSource::data_url) {
    co_return domain::GeneratedImage{
        .mime_type = MimeFromDataUrl(candidate.payload),
        .data_url = std::move(candidate.payload),
        .revised_prompt = std::move(candidate.revised_prompt)};
  }

  auto response = co_await ReadBounded(
      co_await http->SendStreamAsync(
          huxerui::HttpRequest{.url = std::move(candidate.payload),
                               .method = huxerui::HttpMethod::Get,
                               .timeout = kDownloadTimeout}),
      kMaximumDownloadBytes);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  const auto mime = NormalizeMime(
      HeaderValue(response->headers, "Content-Type")
          .value_or(std::move(candidate.mime_type)));
  auto base64 = Base64Encode(response->body);
  if (!IsUsableImageBase64(base64)) {
    co_return std::unexpected(Error(ImageGenerationErrorCode::decode,
                                    "Downloaded payload is not a supported image"));
  }
  co_return domain::GeneratedImage{
      .mime_type = mime,
      .data_url = "data:" + mime + ";base64," + std::move(base64),
      .revised_prompt = std::move(candidate.revised_prompt)};
}

} // namespace

HuxImageGenerationGateway::HuxImageGenerationGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument("HuxImageGenerationGateway requires HttpClient");
}

huxerui::Task<
    application::ImageGenerationResult<domain::GeneratedImage>>
HuxImageGenerationGateway::Generate(domain::ModelConfig model,
                                    domain::ImageGenerationRequest request) {
  auto descriptor = BuildImageGenerationRequest(model, request);
  if (!descriptor)
    co_return std::unexpected(std::move(descriptor.error()));
  auto response = co_await PostJson(http_, std::move(*descriptor));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  const std::string body = response->body.empty()
                               ? std::string{}
                               : std::string{
                                     reinterpret_cast<const char *>(
                                         response->body.data()),
                                     response->body.size()};
  auto decoded = DecodeImageGenerationResponse(model.protocol, body);
  if (!decoded)
    co_return std::unexpected(std::move(decoded.error()));
  co_return co_await ResolveCandidate(http_, std::move(*decoded));
}

} // namespace linecode::infrastructure
