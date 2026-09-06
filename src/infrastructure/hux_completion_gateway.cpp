#include "infrastructure/hux_completion_gateway.h"

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <expected>
#include <span>
#include <stdexcept>
#include <string>
#include <utility>

#include "infrastructure/bounded_text_accumulator.h"
#include "infrastructure/model_url_policy.h"
#include "infrastructure/openai_chat_codec.h"
#include "infrastructure/sse_decoder.h"

namespace linecode::infrastructure {
namespace {

using application::CompletionError;
using application::CompletionErrorCode;
using application::CompletionRequest;
using application::CompletionResponse;

constexpr std::size_t kMaximumBufferedResponse = 4U * 1024U * 1024U;
constexpr std::size_t kMaximumStreamedText = 4U * 1024U * 1024U;

huxerui::Bytes BytesFromString(const std::string_view value) {
  const auto *begin = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(begin, begin + value.size());
}

std::string StringFromBytes(const huxerui::Bytes &value) {
  if (value.empty()) {
    return {};
  }
  return {reinterpret_cast<const char *>(value.data()), value.size()};
}

CompletionError TransportError(const huxerui::HttpError &error) {
  return {.code = CompletionErrorCode::transport, .message = error.message};
}

CompletionError StatusError(const int status, std::string body) {
  if (body.size() > 4096U) {
    body.resize(4096U);
  }
  return {.code = CompletionErrorCode::http_status,
          .message = "HTTP " + std::to_string(status) +
                     (body.empty() ? std::string{} : ": " + body),
          .http_status = status};
}

CompletionError DecodeError(const OpenAiCodecError &error) {
  return {.code = CompletionErrorCode::decode, .message = error.message};
}

CompletionError DecodeError(const SseDecodeError &error) {
  return {.code = CompletionErrorCode::decode,
          .message = "SSE response exceeded its configured limit at byte " +
                     std::to_string(error.byte_offset)};
}

huxerui::HttpRequest BuildRequest(const CompletionRequest &request,
                                  const std::string &base_url) {
  const auto body = EncodeOpenAiChatRequest(request);
  std::vector<huxerui::HttpHeader> headers{
      {.name = "Content-Type", .value = "application/json"},
      {.name = "Accept",
       .value = request.stream ? "text/event-stream" : "application/json"},
      {.name = "Connection", .value = "close"},
  };
  if (!request.model.api_key.empty()) {
    headers.push_back(
        {.name = "Authorization", .value = "Bearer " + request.model.api_key});
  }
  return huxerui::HttpRequest{
      .url = OpenAiChatEndpoint(base_url),
      .method = huxerui::HttpMethod::Post,
      .headers = std::move(headers),
      .body = BytesFromString(body),
      .timeout = std::chrono::minutes(10),
  };
}

huxerui::Task<std::expected<std::string, CompletionError>>
ReadBody(huxerui::HttpResponseStream &stream,
         const std::size_t maximum_bytes) {
  std::string body;
  while (true) {
    auto read = co_await stream.Read();
    if (read.HasError()) {
      co_return std::unexpected(TransportError(read.Error()));
    }
    if (read.IsComplete()) {
      co_return body;
    }
    const auto &bytes = read.Data();
    if (bytes.size() > maximum_bytes -
                           std::min(maximum_bytes, body.size())) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "HTTP response body exceeds its configured limit"});
    }
    body.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
  }
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
CompleteBuffered(const std::shared_ptr<huxerui::HttpClient> &http,
                 huxerui::HttpRequest request) {
  auto result = co_await http->Send(std::move(request));
  if (!result.HasResponse()) {
    co_return std::unexpected(TransportError(result.Error()));
  }
  auto response = std::move(result).Response();
  const auto body = StringFromBytes(response.body);
  if (response.status_code < 200 || response.status_code >= 300) {
    co_return std::unexpected(StatusError(response.status_code, body));
  }
  if (response.body.size() > kMaximumBufferedResponse) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::decode,
        .message = "HTTP response body exceeds its configured limit"});
  }
  auto decoded = DecodeOpenAiChatResponse(body);
  if (!decoded.has_value()) {
    co_return std::unexpected(DecodeError(decoded.error()));
  }
  co_return std::move(*decoded);
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
CompleteStreaming(const std::shared_ptr<huxerui::HttpClient> &http,
                  huxerui::HttpRequest request,
                  application::CompletionObserver observer) {
  auto opened = co_await http->SendStream(std::move(request));
  if (!opened.HasResponse()) {
    co_return std::unexpected(TransportError(opened.Error()));
  }
  auto stream = std::move(opened).Response();
  if (stream.StatusCode() < 200 || stream.StatusCode() >= 300) {
    const auto status = stream.StatusCode();
    auto body = co_await ReadBody(stream, 4096U);
    co_return std::unexpected(
        body.has_value() ? StatusError(status, std::move(*body))
                         : std::move(body.error()));
  }

  SseDecoder decoder;
  CompletionResponse response;
  BoundedTextAccumulator text{kMaximumStreamedText};
  auto consume = [&](std::vector<SseEvent> events)
      -> std::expected<bool, CompletionError> {
    for (const auto &event : events) {
      auto chunk = DecodeOpenAiChatStreamEvent(event.data);
      if (!chunk.has_value()) {
        return std::unexpected(DecodeError(chunk.error()));
      }
      if (chunk->done) {
        return true;
      }
      if (chunk->text_delta.has_value() && !chunk->text_delta->empty()) {
        if (auto appended = text.Append(*chunk->text_delta);
            !appended.has_value()) {
          return std::unexpected(CompletionError{
              .code = CompletionErrorCode::decode,
              .message = "Streamed completion text exceeds its configured "
                         "limit of " +
                         std::to_string(appended.error().maximum_bytes) +
                         " bytes"});
        }
        if (observer.on_text_delta) {
          observer.on_text_delta(*chunk->text_delta);
        }
      }
    }
    return false;
  };

  while (true) {
    auto read = co_await stream.Read();
    if (read.HasError()) {
      co_return std::unexpected(TransportError(read.Error()));
    }
    if (read.IsComplete()) {
      auto finished = decoder.Finish();
      if (!finished.has_value()) {
        co_return std::unexpected(DecodeError(finished.error()));
      }
      auto consumed = consume(std::move(*finished));
      if (!consumed.has_value()) {
        co_return std::unexpected(consumed.error());
      }
      response.text = std::move(text).Take();
      co_return response;
    }
    auto events = decoder.Feed(std::span<const std::byte>{read.Data()});
    if (!events.has_value()) {
      co_return std::unexpected(DecodeError(events.error()));
    }
    auto consumed = consume(std::move(*events));
    if (!consumed.has_value()) {
      co_return std::unexpected(consumed.error());
    }
    if (*consumed) {
      response.text = std::move(text).Take();
      co_return response;
    }
  }
}

} // namespace

HuxCompletionGateway::HuxCompletionGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_) {
    throw std::invalid_argument("HuxCompletionGateway requires HttpClient");
  }
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
HuxCompletionGateway::Complete(CompletionRequest request,
                               application::CompletionObserver observer) {
  if (request.model.protocol != domain::ModelProtocol::openai_compatible) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::unsupported_protocol,
        .message = "Only OpenAI-compatible Chat Completions is connected"});
  }
  if (request.model.model_id.empty()) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = "The selected model has no model ID"});
  }
  auto base_url = ValidateModelBaseUrl(request.model.base_url);
  if (!base_url.has_value()) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = base_url.error().message});
  }
  auto http_request = BuildRequest(request, *base_url);
  if (request.stream) {
    co_return co_await CompleteStreaming(http_, std::move(http_request),
                                         std::move(observer));
  }
  co_return co_await CompleteBuffered(http_, std::move(http_request));
}

} // namespace linecode::infrastructure
