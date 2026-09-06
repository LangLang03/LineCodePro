#include "infrastructure/hux_model_catalog_gateway.h"

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <expected>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/model_catalog_codec.h"
#include "infrastructure/model_url_policy.h"

namespace linecode::infrastructure {
namespace {

constexpr std::size_t kMaximumCatalogResponseBytes = 4U * 1024U * 1024U;
constexpr std::size_t kMaximumErrorBodyBytes = 4U * 1024U;
constexpr auto kCatalogTimeout = std::chrono::seconds{30};
constexpr auto kProbeTimeout = std::chrono::minutes{10};

huxerui::Bytes BytesFromString(const std::string_view value) {
  const auto *begin = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(begin, begin + value.size());
}

application::ModelStoreError Error(std::string message) {
  return application::ModelStoreError{.message = std::move(message)};
}

application::ModelStoreError StatusError(const int status,
                                         std::string body,
                                         const std::optional<std::string> &read_error =
                                             std::nullopt) {
  return Error("HTTP " + std::to_string(status) +
               (body.empty() ? std::string{} : ": " + body) +
               (read_error.has_value()
                    ? " (response body read failed: " + *read_error + ")"
                    : std::string{}));
}

std::vector<huxerui::HttpHeader> HeadersFrom(
    std::vector<std::pair<std::string, std::string>> source) {
  std::vector<huxerui::HttpHeader> result;
  result.reserve(source.size());
  for (auto &[name, value] : source) {
    result.push_back(
        huxerui::HttpHeader{.name = std::move(name), .value = std::move(value)});
  }
  return result;
}

huxerui::HttpRequest ToRequest(ModelHttpRequestDescriptor descriptor,
                               const huxerui::HttpMethod method,
                               const std::chrono::milliseconds timeout) {
  return huxerui::HttpRequest{
      .url = std::move(descriptor.url),
      .method = method,
      .headers = HeadersFrom(std::move(descriptor.headers)),
      .body = BytesFromString(descriptor.body),
      .timeout = timeout,
  };
}

struct DrainedErrorBody final {
  std::string retained;
  std::optional<std::string> read_error;
};

huxerui::Task<std::expected<std::string, application::ModelStoreError>>
ReadBoundedBody(huxerui::HttpResponseStream &stream) {
  std::string body;
  while (true) {
    auto read = co_await stream.Read();
    if (read.HasError()) {
      co_return std::unexpected(Error(read.Error().message));
    }
    if (read.IsComplete()) {
      co_return body;
    }
    const auto &chunk = read.Data();
    const auto remaining =
        kMaximumCatalogResponseBytes -
        std::min(kMaximumCatalogResponseBytes, body.size());
    if (chunk.size() > remaining) {
      // Destroying the unfinished stream cancels the operation according to
      // the public HuxerUI contract. No over-limit bytes are retained.
      co_return std::unexpected(
          Error("Model API response exceeds the 4 MiB safety limit"));
    }
    body.append(reinterpret_cast<const char *>(chunk.data()), chunk.size());
  }
}

huxerui::Task<DrainedErrorBody>
DrainErrorBody(huxerui::HttpResponseStream &stream) {
  DrainedErrorBody result;
  while (true) {
    auto read = co_await stream.Read();
    if (read.HasError()) {
      result.read_error = read.Error().message;
      co_return result;
    }
    if (read.IsComplete()) {
      co_return result;
    }
    const auto &chunk = read.Data();
    const auto remaining = kMaximumErrorBodyBytes -
                           std::min(kMaximumErrorBodyBytes,
                                    result.retained.size());
    const auto retained_bytes = std::min(remaining, chunk.size());
    result.retained.append(reinterpret_cast<const char *>(chunk.data()),
                           retained_bytes);
    // Continue reading after the diagnostic prefix is full. This drains the
    // response without allowing an error page to grow application memory.
  }
}

huxerui::Task<std::expected<std::string, application::ModelStoreError>>
ReadResponse(huxerui::HttpStreamResult opened) {
  if (!opened.HasResponse()) {
    co_return std::unexpected(Error(opened.Error().message));
  }
  auto stream = std::move(opened).Response();
  const auto status = stream.StatusCode();
  if (status < 200 || status >= 300) {
    auto error_body = co_await DrainErrorBody(stream);
    co_return std::unexpected(StatusError(status, std::move(error_body.retained),
                                          error_body.read_error));
  }
  co_return co_await ReadBoundedBody(stream);
}

} // namespace

HuxModelCatalogGateway::HuxModelCatalogGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_) {
    throw std::invalid_argument(
        "HuxModelCatalogGateway requires HttpClient");
  }
}

huxerui::Task<
    std::expected<std::vector<std::string>, application::ModelStoreError>>
HuxModelCatalogGateway::Fetch(const domain::ModelProtocol protocol,
                              std::string base_url, std::string api_key) {
  if (protocol == domain::ModelProtocol::local_gguf) {
    co_return std::vector<std::string>{};
  }
  if (base_url.empty() || api_key.empty()) {
    co_return std::unexpected(
        Error("Base URL and API Key are required"));
  }
  auto validated = ValidateModelBaseUrl(base_url);
  if (!validated.has_value()) {
    co_return std::unexpected(Error(validated.error().message));
  }
  auto descriptor =
      BuildModelCatalogRequest(protocol, *validated, api_key);
  if (!descriptor.has_value()) {
    co_return std::unexpected(Error(descriptor.error().message));
  }
  auto opened = co_await http_->SendStream(
      ToRequest(std::move(*descriptor), huxerui::HttpMethod::Get,
                kCatalogTimeout));
  auto body = co_await ReadResponse(std::move(opened));
  if (!body.has_value()) {
    co_return std::unexpected(std::move(body.error()));
  }
  auto decoded = DecodeModelCatalogResponse(*body);
  if (!decoded.has_value()) {
    co_return std::unexpected(
        Error("Model list query failed: " + decoded.error().message));
  }
  co_return std::move(*decoded);
}

huxerui::Task<std::expected<application::ModelProbeResult,
                            application::ModelStoreError>>
HuxModelCatalogGateway::Probe(domain::ModelConfig model) {
  if (model.base_url.empty() || model.api_key.empty() ||
      model.model_id.empty()) {
    co_return std::unexpected(
        Error("Base URL, API Key and Model ID are required"));
  }
  if (model.protocol == domain::ModelProtocol::local_gguf) {
    co_return std::unexpected(
        Error("Local GGUF model probing is unavailable"));
  }
  auto validated = ValidateModelBaseUrl(model.base_url);
  if (!validated.has_value()) {
    co_return std::unexpected(Error(validated.error().message));
  }
  model.base_url = std::move(*validated);
  auto descriptor = BuildModelProbeRequest(model);
  if (!descriptor.has_value()) {
    co_return std::unexpected(Error(descriptor.error().message));
  }
  const auto started = std::chrono::steady_clock::now();
  auto opened = co_await http_->SendStream(
      ToRequest(std::move(*descriptor), huxerui::HttpMethod::Post,
                kProbeTimeout));
  auto body = co_await ReadResponse(std::move(opened));
  const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                           std::chrono::steady_clock::now() - started)
                           .count();
  if (!body.has_value()) {
    co_return std::unexpected(std::move(body.error()));
  }
  auto decoded = DecodeModelProbeResponse(model.protocol, *body);
  if (!decoded.has_value()) {
    co_return std::unexpected(
        Error("Model probe failed: " + decoded.error().message));
  }
  co_return application::ModelProbeResult{
      .response = std::move(*decoded),
      .elapsed_milliseconds = elapsed,
  };
}

} // namespace linecode::infrastructure
