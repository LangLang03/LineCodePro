#include <cassert>
#include <algorithm>
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
using linecode::infrastructure::BoundedTextAccumulator;
using linecode::infrastructure::DecodeOpenAiChatResponse;
using linecode::infrastructure::DecodeOpenAiChatStreamEvent;
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
      .messages =
          {
              {.role = linecode::application::CompletionRole::user,
               .content = "你好\n\"LineCode\"",
               .tool_calls = {},
               .tool_result = std::nullopt},
              {.role = linecode::application::CompletionRole::assistant,
               .content = "ready\\ok",
               .tool_calls = {},
               .tool_result = std::nullopt},
          },
      .tools = {},
      .stream = true,
      .permission_scope = {},
  };
  const auto json = EncodeOpenAiChatRequest(request);
  assert(json == "{\"model\":\"linecode-test-model\",\"messages\":["
                 "{\"role\":\"user\",\"content\":\"你好\\n\\\"LineCode\\\"\"},"
                 "{\"role\":\"assistant\",\"content\":\"ready\\\\ok\"}],"
                 "\"temperature\":0.2,"
                 "\"reasoning\":{\"effort\":\"medium\"},"
                 "\"stream\":true}");
}

void DecodesBufferedFixtureAndUnicodeEscapes() {
  const auto response = DecodeOpenAiChatResponse(
      R"json({"choices":[{"message":{"content":"固定\u56de\u590d \ud83c\udf0d","reasoning_content":"先思考"}}],"usage":{"prompt_tokens":2,"completion_tokens":3}})json");
  assert(response.has_value());
  assert(response->text == "固定回复 🌍");
  assert(response->reasoning_content == "先思考");
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
      R"json({"choices":[{"delta":{"content":"固定回复","reasoning_content":"思考"},"finish_reason":null}]})json");
  assert(delta.has_value());
  assert(delta->text_delta == "固定回复");
  assert(delta->reasoning_delta == "思考");

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
  assert(OpenAiChatEndpoint("https://api.example.test/v1/chat/completions/") ==
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
  assert(controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "流"}));
  assert(controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "式"}));
  assert(controller.State().streamed_text == "流式");
  const auto concurrent = controller.Begin("must not run concurrently");
  assert(!concurrent.has_value());
  assert(concurrent.error() ==
         linecode::application::SendMessageError::generation_in_progress);
  assert(recording->Messages().size() == 2U);

  controller.Cancel();
  assert(controller.State().phase == GenerationPhase::cancelled);
  assert(controller.State().streamed_text.empty());
  assert(recording->Messages().size() == 3U);
  assert(recording->Messages().back().content == "流式");
  assert(!recording->Messages().back().error);
  assert(!controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "stale"}));
  assert(!controller.Complete(first->generation_id,
                              CompletionResponse{.text = "stale",
                                                 .reasoning_content = {},
                                                 .tool_calls = {},
                                                 .input_tokens = 0,
                                                 .output_tokens = 0}));
  assert(recording->Messages().size() == 3U);

  auto second = controller.Begin("second");
  assert(second.has_value());
  assert(second->generation_id > first->generation_id);
  assert(controller.Observe(
      second->generation_id,
      linecode::application::CompletionReasoningDelta{
          .turn_index = 0,
          .text = "先读取文件",
          .kind = linecode::application::CompletionReasoningKind::thinking,
          .starts_new_segment = true}));
  assert(controller.Observe(
      second->generation_id,
      linecode::application::CompletionToolCallEvent{
          .turn_index = 0,
          .call = {.id = "call-1",
                   .name = "read_file",
                   .arguments_json = "{}"},
          .status = linecode::application::CompletionToolCallStatus::completed,
          .result = linecode::application::CompletionToolResult{
              .call_id = "call-1",
              .name = "read_file",
              .content = "fixture",
              .error = false},
          .display = {},
          .created_at_millis = 1,
          .duration_millis = 2}));
  assert(controller.Observe(
      second->generation_id,
      linecode::application::CompletionTextDelta{.turn_index = 1,
                                                 .text = "这是 LineCode 自动化测试的固定回复。"}));
  assert(controller.Complete(
      second->generation_id,
      CompletionResponse{.text = "这是 LineCode 自动化测试的固定回复。",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  assert(controller.State().phase == GenerationPhase::completed);
  assert(recording->Messages().back().role == MessageRole::assistant);
  assert(recording->Messages().back().content ==
         "这是 LineCode 自动化测试的固定回复。");
  assert(recording->Messages().back().timeline.size() == 2U);
  const auto completed_message_count = recording->Messages().size();
  assert(!controller.Complete(
      second->generation_id,
      CompletionResponse{.text = "同一 generation 不应重复落盘",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  assert(recording->Messages().size() == completed_message_count);

  auto third = controller.Begin("third");
  assert(third.has_value());
  assert(std::ranges::any_of(third->messages, [](const auto &message) {
    return message.role == linecode::application::CompletionRole::tool &&
           message.tool_result && message.tool_result->call_id == "call-1" &&
           message.tool_result->content == "fixture";
  }));
  assert(controller.Observe(
      third->generation_id,
      linecode::application::CompletionReasoningDelta{
          .turn_index = 0,
          .text = "partial reasoning",
          .kind = linecode::application::CompletionReasoningKind::summary,
          .starts_new_segment = false}));
  assert(controller.Observe(
      third->generation_id,
      linecode::application::CompletionToolCallEvent{
          .turn_index = 0,
          .call = {.id = "call-fail", .name = "shell_execute", .arguments_json = "{}"},
          .status = linecode::application::CompletionToolCallStatus::running,
          .result = std::nullopt,
          .display = {},
          .created_at_millis = 4,
          .duration_millis = 0}));
  assert(controller.Fail(third->generation_id,
                         CompletionError{.code = CompletionErrorCode::transport,
                                         .message = "fixture unavailable"}));
  assert(controller.State().phase == GenerationPhase::failed);
  assert(controller.State().error == "fixture unavailable");
  assert(recording->Messages().back().error);
  assert(recording->Messages().back().error_message == "fixture unavailable");
  assert(recording->Messages().back().reasoning_content ==
         "partial reasoning");
  const auto *failed_tool =
      std::get_if<linecode::domain::AssistantToolEvent>(
          &recording->Messages().back().timeline.back());
  assert(failed_tool && failed_tool->result && failed_tool->result->error &&
         failed_tool->call.status ==
             linecode::domain::ToolCallStatus::failed);
  controller.Reset();
  assert(controller.State().phase == GenerationPhase::idle);
  assert(controller.State().error.empty());

  auto image = controller.Begin("generate an image");
  assert(image.has_value());
  const std::string raw_image_result =
      R"json({"linecode_image_generation":true,"display_markdown":"![fixture](data:image/png;base64,AAAA)","model_content":"Generated image for: fixture"})json";
  const auto image_display =
      linecode::application::DefaultToolResultDisplayProjector()->Project(
          "image_generation", raw_image_result, false);
  assert(controller.Observe(
      image->generation_id,
      linecode::application::CompletionToolCallEvent{
          .turn_index = 0,
          .call = {.id = "call-image",
                   .name = "image_generation",
                   .arguments_json = R"({"prompt":"fixture"})"},
          .status =
              linecode::application::CompletionToolCallStatus::completed,
          .result = linecode::application::CompletionToolResult{
              .call_id = "call-image",
              .name = "image_generation",
              .content = raw_image_result,
              .error = false},
          .display = image_display,
          .created_at_millis = 8,
          .duration_millis = 9}));
  assert(controller.Observe(
      image->generation_id,
      linecode::application::CompletionTextDelta{
          .turn_index = 1, .text = "Image complete."}));
  assert(controller.Complete(
      image->generation_id,
      CompletionResponse{.text = "Image complete.",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  const auto &image_message = recording->Messages().back();
  assert(image_message.content ==
         "![fixture](data:image/png;base64,AAAA)\n\nImage complete.");

  auto after_image = controller.Begin("continue after image");
  assert(after_image.has_value());
  for (const auto &message : after_image->messages) {
    assert(!message.content.contains("data:image/"));
    if (message.tool_result)
      assert(!message.tool_result->content.contains("data:image/"));
  }
  assert(std::ranges::any_of(after_image->messages, [](const auto &message) {
    return message.tool_result &&
           message.tool_result->call_id == "call-image" &&
           message.tool_result->content == "Generated image for: fixture";
  }));
  controller.Cancel();

  auto attachment_only =
      controller.Begin("", {{"notes.md", "/workspace/notes.md", "local"}});
  assert(attachment_only.has_value());
  assert(recording->Messages().back().content.empty());
  assert(recording->Messages().back().attachments.size() == 1U);
  assert(controller.Observe(
      attachment_only->generation_id,
      linecode::application::CompletionToolCallEvent{
          .turn_index = 0,
          .call = {.id = "call-cancel",
                   .name = "shell_execute",
                   .arguments_json = "{}"},
          .status = linecode::application::CompletionToolCallStatus::running,
          .result = std::nullopt,
          .display = {},
          .created_at_millis = 5,
          .duration_millis = 0}));
  controller.Cancel();
  const auto *cancelled_tool =
      std::get_if<linecode::domain::AssistantToolEvent>(
          &recording->Messages().back().timeline.back());
  assert(cancelled_tool && cancelled_tool->result &&
         cancelled_tool->call.status ==
             linecode::domain::ToolCallStatus::rejected);
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
