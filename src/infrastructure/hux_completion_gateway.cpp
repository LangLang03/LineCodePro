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
#include "infrastructure/completion_end_marker.h"
#include "infrastructure/completion_protocol_codec.h"
#include "infrastructure/model_url_policy.h"
#include "infrastructure/sse_decoder.h"

namespace linecode::infrastructure {
namespace {

using application::CompletionError;
using application::CompletionErrorCode;
using application::CompletionRequest;
using application::CompletionResponse;

constexpr std::size_t kMaximumBufferedResponse = 4U * 1024U * 1024U;
constexpr std::size_t kMaximumStreamedText = 4U * 1024U * 1024U;
constexpr std::size_t kMaximumStreamedReasoning = 4U * 1024U * 1024U;
constexpr std::size_t kHttpReadChunkBytes = 64U * 1024U;

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

CompletionError TransportError(const huxerui::IoError &error) {
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

CompletionError DecodeError(const CompletionProtocolCodecError &error) {
  return {.code = CompletionErrorCode::decode, .message = error.message};
}

CompletionError DecodeError(const SseDecodeError &error) {
  return {.code = CompletionErrorCode::decode,
          .message = "SSE response exceeded its configured limit at byte " +
                     std::to_string(error.byte_offset)};
}

huxerui::HttpRequest BuildRequest(const CompletionRequest &request,
                                  CompletionProtocolWireRequest wire_request) {
  std::vector<huxerui::HttpHeader> headers{
      {.name = "Content-Type", .value = "application/json"},
      {.name = "Accept",
       .value = request.stream ? "text/event-stream" : "application/json"},
      {.name = "Connection", .value = "close"},
  };
  headers.reserve(headers.size() + wire_request.headers.size());
  for (auto &[name, value] : wire_request.headers) {
    headers.push_back({.name = std::move(name), .value = std::move(value)});
  }
  return huxerui::HttpRequest{
      .url = std::move(wire_request.endpoint),
      .method = huxerui::HttpMethod::Post,
      .headers = std::move(headers),
      .body = BytesFromString(wire_request.body),
      .timeout = std::chrono::minutes(10),
  };
}

huxerui::Task<std::expected<std::string, CompletionError>>
ReadBody(huxerui::HttpResponseStream &stream, const std::size_t maximum_bytes) {
  std::string body;
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kHttpReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(TransportError(read.Error()));
    }
    auto bytes = std::move(read).Value();
    if (bytes.empty()) {
      co_return body;
    }
    if (bytes.size() > maximum_bytes - std::min(maximum_bytes, body.size())) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "HTTP response body exceeds its configured limit"});
    }
    body.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
  }
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
CompleteBuffered(const std::shared_ptr<huxerui::HttpClient> &http,
                 huxerui::HttpRequest request,
                 const CompletionProtocolCodec &codec) {
  auto result = co_await http->SendAsync(std::move(request));
  if (!result.Succeeded()) {
    co_return std::unexpected(TransportError(result.Error()));
  }
  auto response = std::move(result).Value();
  const auto body = StringFromBytes(response.body);
  if (response.status_code < 200 || response.status_code >= 300) {
    co_return std::unexpected(StatusError(response.status_code, body));
  }
  if (response.body.size() > kMaximumBufferedResponse) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::decode,
        .message = "HTTP response body exceeds its configured limit"});
  }
  auto decoded = codec.decode_response(body);
  if (!decoded.has_value()) {
    co_return std::unexpected(DecodeError(decoded.error()));
  }
  decoded->text = StripCompletionEndMarkers(decoded->text);
  decoded->reasoning_content = StripCompletionEndMarkers(decoded->reasoning_content);
  co_return std::move(*decoded);
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
CompleteStreaming(const std::shared_ptr<huxerui::HttpClient> &http,
                  huxerui::HttpRequest request,
                  application::CompletionObserver observer,
                  const CompletionProtocolCodec &codec) {
  auto opened = co_await http->SendStreamAsync(std::move(request));
  if (!opened.Succeeded()) {
    co_return std::unexpected(TransportError(opened.Error()));
  }
  auto stream = std::move(opened).Value();
  if (stream.StatusCode() < 200 || stream.StatusCode() >= 300) {
    const auto status = stream.StatusCode();
    auto body = co_await ReadBody(stream, 4096U);
    co_return std::unexpected(body.has_value()
                                  ? StatusError(status, std::move(*body))
                                  : std::move(body.error()));
  }

  SseDecoder decoder;
  CompletionResponse response;
  std::vector<application::CompletionToolCall> streamed_tool_calls;
  BoundedTextAccumulator text{kMaximumStreamedText};
  BoundedTextAccumulator reasoning{kMaximumStreamedReasoning};
  // A leaked end-of-turn marker ("<end_of_turn>", "<｜end▁of▁sentence｜>", ...) is
  // dropped together with everything the model emits after it, so a finished
  // turn stops growing instead of showing the marker in the answer.
  CompletionEndMarkerFilter text_marker;
  CompletionEndMarkerFilter reasoning_marker;
  application::CompletionReasoningKind reasoning_kind{
      application::CompletionReasoningKind::thinking};
  auto append_text_raw =
      [&](std::string_view delta) -> std::expected<void, CompletionError> {
    if (delta.empty()) {
      return {};
    }
    if (auto appended = text.Append(delta); !appended.has_value()) {
      return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "Streamed completion text exceeds its configured limit "
                     "of " +
                     std::to_string(appended.error().maximum_bytes) +
                     " bytes"});
    }
    if (observer.on_event) {
      const application::CompletionEvent event =
          application::CompletionTextDelta{.turn_index = 0,
                                           .text = std::string{delta}};
      observer.on_event(event);
    }
    return {};
  };
  auto append_text =
      [&](std::string_view delta) -> std::expected<void, CompletionError> {
    return append_text_raw(text_marker.Push(delta));
  };
  auto append_reasoning_raw = [&](application::CompletionReasoningDelta delta)
      -> std::expected<void, CompletionError> {
    if (delta.starts_new_segment && !reasoning.Value().empty()) {
      constexpr std::string_view separator{" | "};
      if (auto appended = reasoning.Append(separator); !appended) {
        return std::unexpected(CompletionError{
            .code = CompletionErrorCode::decode,
            .message = "Streamed completion reasoning exceeds its configured "
                       "limit of " +
                       std::to_string(appended.error().maximum_bytes) +
                       " bytes"});
      }
      if (observer.on_event) {
        const application::CompletionEvent event =
            application::CompletionReasoningDelta{
                .turn_index = 0,
                .text = std::string{separator},
                .kind = delta.kind,
                .starts_new_segment = true,
            };
        observer.on_event(event);
      }
    }
    if (delta.text.empty())
      return {};
    if (auto appended = reasoning.Append(delta.text); !appended) {
      return std::unexpected(CompletionError{
          .code = CompletionErrorCode::decode,
          .message = "Streamed completion reasoning exceeds its configured "
                     "limit of " +
                     std::to_string(appended.error().maximum_bytes) +
                     " bytes"});
    }
    if (observer.on_event) {
      const application::CompletionEvent event = std::move(delta);
      observer.on_event(event);
    }
    return {};
  };
  auto append_reasoning = [&](application::CompletionReasoningDelta delta)
      -> std::expected<void, CompletionError> {
    if (delta.text.empty())
      return {};
    delta.text = reasoning_marker.Push(delta.text);
    reasoning_kind = delta.kind;
    if (delta.text.empty())
      return {};
    return append_reasoning_raw(std::move(delta));
  };
  // Whatever the marker filter held back at the end of the stream is ordinary
  // text that simply ended in '<'.
  auto flush_marker_filters = [&]() -> std::expected<void, CompletionError> {
    if (const auto tail = text_marker.Flush(); !tail.empty()) {
      if (auto appended = append_text_raw(tail); !appended.has_value()) {
        return std::unexpected(std::move(appended.error()));
      }
    }
    if (const auto tail = reasoning_marker.Flush(); !tail.empty()) {
      if (auto appended = append_reasoning_raw(application::CompletionReasoningDelta{
              .turn_index = 0,
              .text = tail,
              .kind = reasoning_kind,
              .starts_new_segment = false});
          !appended.has_value()) {
        return std::unexpected(std::move(appended.error()));
      }
    }
    return {};
  };
  auto consume = [&](std::vector<SseEvent> events)
      -> std::expected<bool, CompletionError> {
    // The whole batch is consumed before reporting the end of the stream:
    // providers send their usage-only chunk right after the finishing one, and
    // returning early dropped that accounting.
    bool done = false;
    for (const auto &event : events) {
      auto chunk = codec.decode_stream_event(event.data);
      if (!chunk.has_value()) {
        return std::unexpected(DecodeError(chunk.error()));
      }
      if (chunk->text_delta.has_value() && !chunk->text_delta->empty()) {
        if (auto appended = append_text(*chunk->text_delta); !appended) {
          return std::unexpected(std::move(appended.error()));
        }
      }
      if (chunk->final_text.has_value() && !chunk->final_text->empty()) {
        const auto &current = text.Value();
        std::string_view suffix;
        if (chunk->final_text->starts_with(current)) {
          suffix = std::string_view{*chunk->final_text}.substr(current.size());
        } else if (current.find(*chunk->final_text) == std::string::npos) {
          suffix = *chunk->final_text;
        }
        if (auto appended = append_text(suffix); !appended) {
          return std::unexpected(std::move(appended.error()));
        }
      }
      for (auto &delta : chunk->reasoning_deltas) {
        if (auto appended = append_reasoning(std::move(delta)); !appended)
          return std::unexpected(std::move(appended.error()));
      }
      if (chunk->final_reasoning && !chunk->final_reasoning->empty()) {
        const auto &current = reasoning.Value();
        std::string_view suffix;
        if (chunk->final_reasoning->starts_with(current)) {
          suffix =
              std::string_view{*chunk->final_reasoning}.substr(current.size());
        } else if (current.find(*chunk->final_reasoning) == std::string::npos) {
          suffix = *chunk->final_reasoning;
        }
        if (!suffix.empty()) {
          if (auto appended = append_reasoning({
                  .text = std::string{suffix},
                  .kind = application::CompletionReasoningKind::summary,
              });
              !appended)
            return std::unexpected(std::move(appended.error()));
        }
      }
      for (auto &delta : chunk->tool_call_deltas) {
        if (streamed_tool_calls.size() <= delta.index)
          streamed_tool_calls.resize(delta.index + 1U);
        auto &call = streamed_tool_calls[delta.index];
        if (delta.id)
          call.id = std::move(*delta.id);
        if (delta.name)
          call.name = std::move(*delta.name);
        call.arguments_json += delta.arguments_delta;
      }
      if (!chunk->final_tool_calls.empty())
        streamed_tool_calls = std::move(chunk->final_tool_calls);
      response.input_tokens =
          std::max(response.input_tokens, chunk->input_tokens);
      response.output_tokens =
          std::max(response.output_tokens, chunk->output_tokens);
      if (chunk->done) {
        done = true;
      }
    }
    return done;
  };

  while (true) {
    auto read = co_await stream.Body().ReadAsync(kHttpReadChunkBytes);
    if (!read.Succeeded()) {
      co_return std::unexpected(TransportError(read.Error()));
    }
    auto bytes = std::move(read).Value();
    if (bytes.empty()) {
      auto finished = decoder.Finish();
      if (!finished.has_value()) {
        co_return std::unexpected(DecodeError(finished.error()));
      }
      auto consumed = consume(std::move(*finished));
      if (!consumed.has_value()) {
        co_return std::unexpected(consumed.error());
      }
      if (auto flushed = flush_marker_filters(); !flushed.has_value()) {
        co_return std::unexpected(std::move(flushed.error()));
      }
      response.text = std::move(text).Take();
      response.reasoning_content = std::move(reasoning).Take();
      std::erase_if(streamed_tool_calls, [](const auto &call) {
        return call.id.empty() || call.name.empty();
      });
      for (auto &call : streamed_tool_calls) {
        if (call.arguments_json.empty())
          call.arguments_json = "{}";
      }
      response.tool_calls = std::move(streamed_tool_calls);
      co_return response;
    }
    auto events = decoder.Feed(std::span<const std::byte>{bytes});
    if (!events.has_value()) {
      co_return std::unexpected(DecodeError(events.error()));
    }
    auto consumed = consume(std::move(*events));
    if (!consumed.has_value()) {
      co_return std::unexpected(consumed.error());
    }
    if (*consumed) {
      if (auto flushed = flush_marker_filters(); !flushed.has_value()) {
        co_return std::unexpected(std::move(flushed.error()));
      }
      response.text = std::move(text).Take();
      response.reasoning_content = std::move(reasoning).Take();
      std::erase_if(streamed_tool_calls, [](const auto &call) {
        return call.id.empty() || call.name.empty();
      });
      for (auto &call : streamed_tool_calls) {
        if (call.arguments_json.empty())
          call.arguments_json = "{}";
      }
      response.tool_calls = std::move(streamed_tool_calls);
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
  const auto *codec = FindCompletionProtocolCodec(request.model.protocol);
  if (codec == nullptr) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::unsupported_protocol,
        .message = "The selected model protocol has no completion codec"});
  }
  if (request.model.model_id.empty()) {
    co_return std::unexpected(
        CompletionError{.code = CompletionErrorCode::invalid_configuration,
                        .message = "The selected model has no model ID"});
  }
  auto base_url = ValidateModelBaseUrl(request.model.base_url);
  if (!base_url.has_value()) {
    co_return std::unexpected(
        CompletionError{.code = CompletionErrorCode::invalid_configuration,
                        .message = base_url.error().message});
  }
  auto encoded = codec->encode(request, *base_url);
  if (!encoded) {
    co_return std::unexpected(DecodeError(encoded.error()));
  }
  auto http_request = BuildRequest(request, std::move(*encoded));
  if (request.stream) {
    co_return co_await CompleteStreaming(http_, std::move(http_request),
                                         std::move(observer), *codec);
  }
  co_return co_await CompleteBuffered(http_, std::move(http_request), *codec);
}

} // namespace linecode::infrastructure
