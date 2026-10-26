#include "infrastructure/local_model_completion_gateway.h"

#include <stdexcept>
#include <string>
#include <utility>

#include "application/local_model_prompt.h"

namespace linecode::infrastructure {
namespace {

using application::CompletionError;
using application::CompletionErrorCode;
using application::CompletionObserver;
using application::CompletionRequest;
using application::CompletionResponse;
using application::CompletionTextDelta;

} // namespace

LocalModelCompletionGateway::LocalModelCompletionGateway(
    std::shared_ptr<application::CompletionGateway> remote,
    std::shared_ptr<application::LocalModelEngine> engine)
    : remote_(std::move(remote)), engine_(std::move(engine)) {
  if (!remote_) {
    throw std::invalid_argument(
        "LocalModelCompletionGateway requires a remote gateway");
  }
  if (!engine_) {
    throw std::invalid_argument(
        "LocalModelCompletionGateway requires a local model engine");
  }
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
LocalModelCompletionGateway::Complete(CompletionRequest request,
                                      CompletionObserver observer) {
  if (request.model.protocol != domain::ModelProtocol::local_gguf) {
    co_return co_await remote_->Complete(std::move(request),
                                         std::move(observer));
  }
  co_return co_await CompleteLocal(std::move(request), std::move(observer));
}

huxerui::Task<std::expected<CompletionResponse, CompletionError>>
LocalModelCompletionGateway::CompleteLocal(CompletionRequest request,
                                           CompletionObserver observer) {
  if (!engine_->Available()) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::unsupported_protocol,
        .message = "This build does not include the local model backend",
    });
  }
  if (request.model.local_model_path.empty()) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = "The local model has no imported GGUF file",
    });
  }

  application::LocalGenerationRequest generation{
      .model_path = request.model.local_model_path,
      .messages = application::BuildLocalModelTurns(request),
      .context_size = request.model.context_size,
      .acceleration = request.model.local_acceleration,
      .max_tokens = 0,
      .chat_template = {},
      .on_load_progress = observer.on_load_progress,
      .on_load_finished = observer.on_load_finished,
      .on_activity = observer.on_activity,
  };

  // A tool-calling turn is buffered rather than streamed. The envelope has to be
  // understood before any of it is shown, and emitting it as prose would put raw
  // JSON in the answer bubble for the moment before the call is recognised. A
  // plain chat turn streams normally, because its reply needs no interpretation.
  const bool buffers_for_tools = !request.tools.empty();
  std::string streamed;
  const auto generated = co_await engine_->Generate(
      std::move(generation),
      [&](std::string_view piece) {
        if (piece.empty()) {
          return;
        }
        streamed += piece;
        if (!buffers_for_tools && request.stream && observer.on_event) {
          observer.on_event(CompletionTextDelta{
              .turn_index = 0, .text = std::string{piece}});
        }
      });
  if (!generated.has_value()) {
    co_return std::unexpected(generated.error());
  }

  CompletionResponse response;
  response.input_tokens = generated->prompt_tokens;
  response.output_tokens = generated->generated_tokens;

  if (buffers_for_tools) {
    auto extraction = application::ExtractLocalToolCalls(streamed);
    response.text = std::move(extraction.text);
    response.tool_calls = std::move(extraction.calls);
    // Only the prose is emitted here. Tool lifecycle events belong to the tool
    // loop that runs the calls, exactly as they do for the HTTP gateway, which
    // reports calls through the response and never as stream events.
    if (!response.text.empty() && request.stream && observer.on_event) {
      observer.on_event(
          CompletionTextDelta{.turn_index = 0, .text = response.text});
    }
  } else {
    response.text = std::move(streamed);
  }

  co_return response;
}

} // namespace linecode::infrastructure
