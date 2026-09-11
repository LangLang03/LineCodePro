#include "infrastructure/hux_image_understanding_gateway.h"

#include <algorithm>
#include <chrono>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/image_understanding_codec.h"

namespace linecode::infrastructure {
namespace {

using application::ImageUnderstandingError;
using application::ImageUnderstandingErrorCode;
using application::ImageUnderstandingResult;

constexpr std::size_t kMaximumResponseBytes = 2U * 1024U * 1024U;
constexpr std::size_t kMaximumErrorBytes = 4U * 1024U;
constexpr std::size_t kReadChunkBytes = 32U * 1024U;
constexpr auto kTimeout = std::chrono::minutes{3};

ImageUnderstandingError Error(ImageUnderstandingErrorCode code,
                              std::string message, int status = 0) {
  return {.code = code, .message = std::move(message), .http_status = status};
}

huxerui::Bytes Bytes(std::string_view text) {
  const auto *first = reinterpret_cast<const std::byte *>(text.data());
  return text.empty() ? huxerui::Bytes{}
                      : huxerui::Bytes(first, first + text.size());
}

huxerui::HttpRequest ToRequest(ImageUnderstandingHttpRequest descriptor) {
  std::vector<huxerui::HttpHeader> headers;
  headers.reserve(descriptor.headers.size() + 1U);
  headers.push_back({.name = "Content-Type", .value = "application/json"});
  for (auto &[name, value] : descriptor.headers)
    headers.push_back({.name = std::move(name), .value = std::move(value)});
  return {.url = std::move(descriptor.url),
          .method = huxerui::HttpMethod::Post,
          .headers = std::move(headers),
          .body = Bytes(descriptor.body),
          .timeout = kTimeout};
}

huxerui::Task<ImageUnderstandingResult<std::string>> SendBounded(
    const std::shared_ptr<huxerui::HttpClient> &http,
    ImageUnderstandingHttpRequest descriptor) {
  auto opened = co_await http->SendStreamAsync(ToRequest(std::move(descriptor)));
  if (!opened.Succeeded())
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::transport,
                                    opened.Error().message));
  auto stream = std::move(opened).Value();
  const auto status = stream.StatusCode();
  const auto maximum = status >= 200 && status < 300
                           ? kMaximumResponseBytes
                           : kMaximumErrorBytes;
  std::string body;
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kReadChunkBytes);
    if (!read.Succeeded())
      co_return std::unexpected(Error(ImageUnderstandingErrorCode::transport,
                                      read.Error().message));
    auto chunk = std::move(read).Value();
    if (chunk.empty())
      break;
    if (chunk.size() > maximum - std::min(maximum, body.size()))
      co_return std::unexpected(Error(ImageUnderstandingErrorCode::too_large,
                                      "Vision response exceeds safety limit"));
    body.append(reinterpret_cast<const char *>(chunk.data()), chunk.size());
  }
  if (status < 200 || status >= 300)
    co_return std::unexpected(Error(
        ImageUnderstandingErrorCode::http_status,
        "HTTP " + std::to_string(status) +
            (body.empty() ? std::string{} : ": " + body),
        status));
  co_return body;
}

} // namespace

HuxImageUnderstandingGateway::HuxImageUnderstandingGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument(
        "HuxImageUnderstandingGateway requires HttpClient");
}

huxerui::Task<ImageUnderstandingResult<std::string>>
HuxImageUnderstandingGateway::Analyze(
    domain::ModelConfig model, std::string system_prompt,
    domain::ImageUnderstandingRequest request,
    domain::WorkspaceImage image) {
  auto descriptor = BuildImageUnderstandingRequest(
      model, system_prompt, request, image);
  if (!descriptor)
    co_return std::unexpected(std::move(descriptor.error()));
  auto body = co_await SendBounded(http_, std::move(*descriptor));
  if (!body)
    co_return std::unexpected(std::move(body.error()));
  co_return DecodeImageUnderstandingResponse(model.protocol, *body);
}

} // namespace linecode::infrastructure
