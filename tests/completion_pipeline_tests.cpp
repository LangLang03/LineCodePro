#include <cassert>
#include <chrono>
#include <memory>
#include <string>

#include "application/generation_controller.h"
#include "infrastructure/bounded_text_accumulator.h"
#include "infrastructure/in_memory_conversation_store.h"
#include "infrastructure/model_url_policy.h"
#include "infrastructure/openai_chat_codec.h"

namespace {

using linecode::application::CompletionError;
using linecode::application::CompletionErrorCode;
using linecode::application::CompletionMessage;
using linecode::application::CompletionRequest;
using linecode::application::CompletionResponse;
using linecode::application::GenerationController;
using linecode::application::GenerationPhase;
using linecode::domain::MessageRole;
using linecode::domain::ModelConfig;
using linecode::domain::ModelProtocol;
using linecode::infrastructure::DecodeOpenAiChatResponse;
using linecode::infrastructure::DecodeOpenAiChatStreamEvent;
using linecode::infrastructure::BoundedTextAccumulator;
using linecode::infrastructure::EncodeOpenAiChatRequest;
using linecode::infrastructure::InMemoryConversationStore;
using linecode::infrastructure::ModelUrlError;
using linecode::infrastructure::OpenAiChatEndpoint;
using linecode::infrastructure::ValidateModelBaseUrl;

ModelConfig FixtureModel() {
  return ModelConfig{
      .id = "fixture",
      .name = "Fixture",
      .protocol = ModelProtocol::openai_compatible,
      .provider_label = "OpenAI",
      .base_url = "http://127.0.0.1:18080/v1",
      .api_key = "fixture-key",
      .model_id = "linecode-test-model",
      .tool_call_limit = ModelConfig::default_tool_call_limit,
      .compression_model_enabled = false,
      .compression_model_auto = true,
      .compression_model_id = {},
      .context_size = ModelConfig::context_size_unset,
  };
}

void EncodesOpenAiRequestWithoutLosingUtf8OrControlCharacters() {
  CompletionRequest request{
      .model = FixtureModel(),
      .messages = {
          {.role = linecode::application::CompletionRole::user,
           .content = "你好\n\"LineCode\""},
          {.role = linecode::application::CompletionRole::assistant,
           .content = "ready\\ok"},
      },
      .stream = true,
  };
  const auto json = EncodeOpenAiChatRequest(request);
  assert(json ==
         "{\"model\":\"linecode-test-model\",\"messages\":["
         "{\"role\":\"user\",\"content\":\"你好\\n\\\"LineCode\\\"\"},"
         "{\"role\":\"assistant\",\"content\":\"ready\\\\ok\"}],"
         "\"temperature\":0.2,\"stream\":true}");
}

void DecodesBufferedFixtureAndUnicodeEscapes() {
  const auto response = DecodeOpenAiChatResponse(
      R"json({"choices":[{"message":{"content":"固定\u56de\u590d \ud83c\udf0d"}}],"usage":{"prompt_tokens":2,"completion_tokens":3}})json");
  assert(response.has_value());
  assert(response->text == "固定回复 🌍");
  assert(response->input_tokens == 2);
  assert(response->output_tokens == 3);

  const auto missing = DecodeOpenAiChatResponse(R"json({"choices":[]})json");
  assert(!missing.has_value());
  const auto malformed = DecodeOpenAiChatResponse("{");
  assert(!malformed.has_value());
}

void DecodesOpenAiSsePayloadsAndDoneSentinel() {
  const auto role = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]})json");
  assert(role.has_value());
  assert(role->text_delta == "");

  const auto delta = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{"content":"固定回复"},"finish_reason":null}]})json");
  assert(delta.has_value());
  assert(delta->text_delta == "固定回复");

  const auto stopped = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{},"finish_reason":"stop"}]})json");
  assert(stopped.has_value());
  assert(!stopped->done);
  assert(!stopped->text_delta.has_value());

  const auto done = DecodeOpenAiChatStreamEvent(" \t[DONE]\r\n");
  assert(done.has_value());
  assert(done->done);

  const auto api_error = DecodeOpenAiChatStreamEvent(
      R"json({"error":{"message":"fixture failed"}})json");
  assert(!api_error.has_value());
  assert(api_error.error().message.find("fixture failed") != std::string::npos);

  const auto filtered = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{},"finish_reason":"content_filter"}]})json");
  assert(!filtered.has_value());
}

void BoundsAggregateStreamTextAcrossManySmallDeltas() {
  BoundedTextAccumulator text{6U};
  assert(text.Append("ab"));
  assert(text.Append("cd"));
  assert(text.Append("ef"));
  const auto over_limit = text.Append("g");
  assert(!over_limit.has_value());
  assert(over_limit.error().maximum_bytes == 6U);
  assert(text.Value() == "abcdef");

  BoundedTextAccumulator utf8{6U};
  assert(utf8.Append("你"));
  assert(utf8.Append("好"));
  assert(!utf8.Append("!"));
  assert(utf8.Value() == "你好");
}

void JoinsEndpointExactlyOnce() {
  assert(OpenAiChatEndpoint(" https://api.example.test/v1/ ") ==
         "https://api.example.test/v1/chat/completions");
  assert(OpenAiChatEndpoint(
             "https://api.example.test/v1/chat/completions/") ==
         "https://api.example.test/v1/chat/completions");
}

void EnforcesHttpsOrLiteralPrivateCleartextHosts() {
  assert(ValidateModelBaseUrl("https://models.example.test/v1"));
  assert(ValidateModelBaseUrl("http://localhost:18080/v1"));
  assert(ValidateModelBaseUrl("http://127.0.0.1:18080/v1"));
  assert(ValidateModelBaseUrl("http://10.0.2.2:18080/v1"));
  assert(ValidateModelBaseUrl("http://192.168.1.4/v1"));
  assert(ValidateModelBaseUrl("http://[::1]:18080/v1"));

  const auto public_http =
      ValidateModelBaseUrl("http://models.example.test/v1");
  assert(!public_http.has_value());
  assert(public_http.error().code == ModelUrlError::cleartext_not_allowed);
  assert(!ValidateModelBaseUrl("http://127.0.0.1.evil.test/v1"));
  assert(!ValidateModelBaseUrl("file:///tmp/model"));
  assert(!ValidateModelBaseUrl("http://user@127.0.0.1/v1"));
}

void GenerationControllerRejectsStaleResultsAndPersistsAssistant() {
  auto store = std::make_unique<InMemoryConversationStore>();
  auto *recording = store.get();
  linecode::application::ChatSession session(std::move(store));
  static_cast<void>(session.AppendAssistant("history"));
  GenerationController controller(session);

  auto first = controller.Begin("first");
  assert(first.has_value());
  assert(first->messages.size() == 2U);
  assert(first->messages[0].content == "history");
  assert(first->messages[1].content == "first");
  assert(controller.State().phase == GenerationPhase::running);
  const auto concurrent = controller.Begin("must not run concurrently");
  assert(!concurrent.has_value());
  assert(concurrent.error() ==
         linecode::application::SendMessageError::generation_in_progress);
  assert(recording->Messages().size() == 2U);

  controller.Cancel();
  assert(controller.State().phase == GenerationPhase::cancelled);
  assert(!controller.Complete(first->generation_id,
                              CompletionResponse{.text = "stale"}));
  assert(recording->Messages().size() == 2U);

  auto second = controller.Begin("second");
  assert(second.has_value());
  assert(second->generation_id > first->generation_id);
  assert(controller.Complete(
      second->generation_id,
      CompletionResponse{.text = "这是 LineCode 自动化测试的固定回复。"}));
  assert(controller.State().phase == GenerationPhase::completed);
  assert(recording->Messages().back().role == MessageRole::assistant);
  assert(recording->Messages().back().content ==
         "这是 LineCode 自动化测试的固定回复。");

  auto third = controller.Begin("third");
  assert(third.has_value());
  assert(controller.Fail(
      third->generation_id,
      CompletionError{.code = CompletionErrorCode::transport,
                      .message = "fixture unavailable"}));
  assert(controller.State().phase == GenerationPhase::failed);
  assert(controller.State().error == "fixture unavailable");
  controller.Reset();
  assert(controller.State().phase == GenerationPhase::idle);
  assert(controller.State().error.empty());
}

} // namespace

int main() {
  EncodesOpenAiRequestWithoutLosingUtf8OrControlCharacters();
  DecodesBufferedFixtureAndUnicodeEscapes();
  DecodesOpenAiSsePayloadsAndDoneSentinel();
  BoundsAggregateStreamTextAcrossManySmallDeltas();
  JoinsEndpointExactlyOnce();
  EnforcesHttpsOrLiteralPrivateCleartextHosts();
  GenerationControllerRejectsStaleResultsAndPersistsAssistant();
}
