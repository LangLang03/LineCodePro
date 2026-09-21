#include "gtest_support.h"
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
  EXPECT_EXPRESSION(json == "{\"model\":\"linecode-test-model\",\"messages\":["
                 "{\"role\":\"user\",\"content\":\"你好\\n\\\"LineCode\\\"\"},"
                 "{\"role\":\"assistant\",\"content\":\"ready\\\\ok\"}],"
                 "\"temperature\":0.2,"
                 "\"reasoning\":{\"effort\":\"medium\"},"
                 "\"stream\":true}");
}

void DecodesBufferedFixtureAndUnicodeEscapes() {
  const auto response = DecodeOpenAiChatResponse(
      R"json({"choices":[{"message":{"content":"固定\u56de\u590d \ud83c\udf0d","reasoning_content":"先思考"}}],"usage":{"prompt_tokens":2,"completion_tokens":3}})json");
  EXPECT_EXPRESSION(response.has_value());
  EXPECT_EXPRESSION(response->text == "固定回复 🌍");
  EXPECT_EXPRESSION(response->reasoning_content == "先思考");
  EXPECT_EXPRESSION(response->input_tokens == 2);
  EXPECT_EXPRESSION(response->output_tokens == 3);

  const auto missing = DecodeOpenAiChatResponse(R"json({"choices":[]})json");
  EXPECT_EXPRESSION(!missing.has_value());
  const auto malformed = DecodeOpenAiChatResponse("{");
  EXPECT_EXPRESSION(!malformed.has_value());
}

void DecodesOpenAiSsePayloadsAndDoneSentinel() {
  const auto role = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]})json");
  EXPECT_EXPRESSION(role.has_value());
  EXPECT_EXPRESSION(role->text_delta == "");

  const auto delta = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{"content":"固定回复","reasoning_content":"思考"},"finish_reason":null}]})json");
  EXPECT_EXPRESSION(delta.has_value());
  EXPECT_EXPRESSION(delta->text_delta == "固定回复");
  EXPECT_EXPRESSION(delta->reasoning_delta == "思考");

  const auto stopped = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{},"finish_reason":"stop"}]})json");
  EXPECT_EXPRESSION(stopped.has_value());
  EXPECT_EXPRESSION(!stopped->done);
  EXPECT_EXPRESSION(!stopped->text_delta.has_value());

  const auto done = DecodeOpenAiChatStreamEvent(" \t[DONE]\r\n");
  EXPECT_EXPRESSION(done.has_value());
  EXPECT_EXPRESSION(done->done);

  const auto api_error = DecodeOpenAiChatStreamEvent(
      R"json({"error":{"message":"fixture failed"}})json");
  EXPECT_EXPRESSION(!api_error.has_value());
  EXPECT_EXPRESSION(api_error.error().message.find("fixture failed") != std::string::npos);

  const auto filtered = DecodeOpenAiChatStreamEvent(
      R"json({"choices":[{"delta":{},"finish_reason":"content_filter"}]})json");
  EXPECT_EXPRESSION(!filtered.has_value());
}

void BoundsAggregateStreamTextAcrossManySmallDeltas() {
  BoundedTextAccumulator text{6U};
  EXPECT_EXPRESSION(text.Append("ab"));
  EXPECT_EXPRESSION(text.Append("cd"));
  EXPECT_EXPRESSION(text.Append("ef"));
  const auto over_limit = text.Append("g");
  EXPECT_EXPRESSION(!over_limit.has_value());
  EXPECT_EXPRESSION(over_limit.error().maximum_bytes == 6U);
  EXPECT_EXPRESSION(text.Value() == "abcdef");

  BoundedTextAccumulator utf8{6U};
  EXPECT_EXPRESSION(utf8.Append("你"));
  EXPECT_EXPRESSION(utf8.Append("好"));
  EXPECT_EXPRESSION(!utf8.Append("!"));
  EXPECT_EXPRESSION(utf8.Value() == "你好");
}

void JoinsEndpointExactlyOnce() {
  EXPECT_EXPRESSION(OpenAiChatEndpoint(" https://api.example.test/v1/ ") ==
         "https://api.example.test/v1/chat/completions");
  EXPECT_EXPRESSION(OpenAiChatEndpoint("https://api.example.test/v1/chat/completions/") ==
         "https://api.example.test/v1/chat/completions");
}

void EnforcesHttpsOrLiteralPrivateCleartextHosts() {
  EXPECT_EXPRESSION(ValidateModelBaseUrl("https://models.example.test/v1"));
  EXPECT_EXPRESSION(ValidateModelBaseUrl("http://localhost:18080/v1"));
  EXPECT_EXPRESSION(ValidateModelBaseUrl("http://127.0.0.1:18080/v1"));
  EXPECT_EXPRESSION(ValidateModelBaseUrl("http://10.0.2.2:18080/v1"));
  EXPECT_EXPRESSION(ValidateModelBaseUrl("http://192.168.1.4/v1"));
  EXPECT_EXPRESSION(ValidateModelBaseUrl("http://[::1]:18080/v1"));

  const auto public_http =
      ValidateModelBaseUrl("http://models.example.test/v1");
  EXPECT_EXPRESSION(!public_http.has_value());
  EXPECT_EXPRESSION(public_http.error().code == ModelUrlError::cleartext_not_allowed);
  EXPECT_EXPRESSION(!ValidateModelBaseUrl("http://127.0.0.1.evil.test/v1"));
  EXPECT_EXPRESSION(!ValidateModelBaseUrl("file:///tmp/model"));
  EXPECT_EXPRESSION(!ValidateModelBaseUrl("http://user@127.0.0.1/v1"));
}

void GenerationControllerRejectsStaleResultsAndPersistsAssistant() {
  auto store = std::make_unique<InMemoryConversationStore>();
  auto *recording = store.get();
  linecode::application::ChatSession session(std::move(store));
  static_cast<void>(session.AppendAssistant("history"));
  GenerationController controller(session);

  auto first = controller.Begin("first");
  EXPECT_EXPRESSION(first.has_value());
  EXPECT_EXPRESSION(first->messages.size() == 2U);
  EXPECT_EXPRESSION(first->messages[0].content == "history");
  EXPECT_EXPRESSION(first->messages[1].content == "first");
  EXPECT_EXPRESSION(controller.State().phase == GenerationPhase::running);
  EXPECT_EXPRESSION(controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "流"}));
  EXPECT_EXPRESSION(controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "式"}));
  EXPECT_EXPRESSION(controller.State().streamed_text == "流式");
  const auto concurrent = controller.Begin("must not run concurrently");
  EXPECT_EXPRESSION(!concurrent.has_value());
  EXPECT_EXPRESSION(concurrent.error() ==
         linecode::application::SendMessageError::generation_in_progress);
  EXPECT_EXPRESSION(recording->Messages().size() == 2U);

  controller.Cancel();
  EXPECT_EXPRESSION(controller.State().phase == GenerationPhase::cancelled);
  EXPECT_EXPRESSION(controller.State().streamed_text.empty());
  EXPECT_EXPRESSION(recording->Messages().size() == 3U);
  EXPECT_EXPRESSION(recording->Messages().back().content == "流式");
  EXPECT_EXPRESSION(!recording->Messages().back().error);
  EXPECT_EXPRESSION(!controller.Observe(
      first->generation_id,
      linecode::application::CompletionTextDelta{.text = "stale"}));
  EXPECT_EXPRESSION(!controller.Complete(first->generation_id,
                              CompletionResponse{.text = "stale",
                                                 .reasoning_content = {},
                                                 .tool_calls = {},
                                                 .input_tokens = 0,
                                                 .output_tokens = 0}));
  EXPECT_EXPRESSION(recording->Messages().size() == 3U);

  auto second = controller.Begin("second");
  EXPECT_EXPRESSION(second.has_value());
  EXPECT_EXPRESSION(second->generation_id > first->generation_id);
  EXPECT_EXPRESSION(controller.Observe(
      second->generation_id,
      linecode::application::CompletionReasoningDelta{
          .turn_index = 0,
          .text = "先读取文件",
          .kind = linecode::application::CompletionReasoningKind::thinking,
          .starts_new_segment = true}));
  EXPECT_EXPRESSION(controller.Observe(
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
  EXPECT_EXPRESSION(controller.Observe(
      second->generation_id,
      linecode::application::CompletionTextDelta{.turn_index = 1,
                                                 .text = "这是 LineCode 自动化测试的固定回复。"}));
  EXPECT_EXPRESSION(controller.Complete(
      second->generation_id,
      CompletionResponse{.text = "这是 LineCode 自动化测试的固定回复。",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  EXPECT_EXPRESSION(controller.State().phase == GenerationPhase::completed);
  EXPECT_EXPRESSION(recording->Messages().back().role == MessageRole::assistant);
  EXPECT_EXPRESSION(recording->Messages().back().content ==
         "这是 LineCode 自动化测试的固定回复。");
  EXPECT_EXPRESSION(recording->Messages().back().timeline.size() == 2U);
  const auto completed_message_count = recording->Messages().size();
  EXPECT_EXPRESSION(!controller.Complete(
      second->generation_id,
      CompletionResponse{.text = "同一 generation 不应重复落盘",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  EXPECT_EXPRESSION(recording->Messages().size() == completed_message_count);

  auto third = controller.Begin("third");
  EXPECT_EXPRESSION(third.has_value());
  EXPECT_EXPRESSION(std::ranges::any_of(third->messages, [](const auto &message) {
    return message.role == linecode::application::CompletionRole::tool &&
           message.tool_result && message.tool_result->call_id == "call-1" &&
           message.tool_result->content == "fixture";
  }));
  EXPECT_EXPRESSION(controller.Observe(
      third->generation_id,
      linecode::application::CompletionReasoningDelta{
          .turn_index = 0,
          .text = "partial reasoning",
          .kind = linecode::application::CompletionReasoningKind::summary,
          .starts_new_segment = false}));
  EXPECT_EXPRESSION(controller.Observe(
      third->generation_id,
      linecode::application::CompletionToolCallEvent{
          .turn_index = 0,
          .call = {.id = "call-fail", .name = "shell_execute", .arguments_json = "{}"},
          .status = linecode::application::CompletionToolCallStatus::running,
          .result = std::nullopt,
          .display = {},
          .created_at_millis = 4,
          .duration_millis = 0}));
  EXPECT_EXPRESSION(controller.Fail(third->generation_id,
                         CompletionError{.code = CompletionErrorCode::transport,
                                         .message = "fixture unavailable"}));
  EXPECT_EXPRESSION(controller.State().phase == GenerationPhase::failed);
  EXPECT_EXPRESSION(controller.State().error == "fixture unavailable");
  EXPECT_EXPRESSION(recording->Messages().back().error);
  EXPECT_EXPRESSION(recording->Messages().back().error_message == "fixture unavailable");
  EXPECT_EXPRESSION(recording->Messages().back().reasoning_content ==
         "partial reasoning");
  const auto *failed_tool =
      std::get_if<linecode::domain::AssistantToolEvent>(
          &recording->Messages().back().timeline.back());
  EXPECT_EXPRESSION(failed_tool && failed_tool->result && failed_tool->result->error &&
         failed_tool->call.status ==
             linecode::domain::ToolCallStatus::failed);
  controller.Reset();
  EXPECT_EXPRESSION(controller.State().phase == GenerationPhase::idle);
  EXPECT_EXPRESSION(controller.State().error.empty());

  auto image = controller.Begin("generate an image");
  EXPECT_EXPRESSION(image.has_value());
  const std::string raw_image_result =
      R"json({"linecode_image_generation":true,"display_markdown":"![fixture](data:image/png;base64,AAAA)","model_content":"Generated image for: fixture"})json";
  const auto image_display =
      linecode::application::DefaultToolResultDisplayProjector()->Project(
          "image_generation", raw_image_result, false);
  EXPECT_EXPRESSION(controller.Observe(
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
  EXPECT_EXPRESSION(controller.Observe(
      image->generation_id,
      linecode::application::CompletionTextDelta{
          .turn_index = 1, .text = "Image complete."}));
  EXPECT_EXPRESSION(controller.Complete(
      image->generation_id,
      CompletionResponse{.text = "Image complete.",
                         .reasoning_content = {},
                         .tool_calls = {},
                         .input_tokens = 0,
                         .output_tokens = 0}));
  const auto &image_message = recording->Messages().back();
  EXPECT_EXPRESSION(image_message.content ==
         "![fixture](data:image/png;base64,AAAA)\n\nImage complete.");

  auto after_image = controller.Begin("continue after image");
  EXPECT_EXPRESSION(after_image.has_value());
  for (const auto &message : after_image->messages) {
    EXPECT_EXPRESSION(!message.content.contains("data:image/"));
    if (message.tool_result) {
      EXPECT_EXPRESSION(!message.tool_result->content.contains("data:image/"));
    }
  }
  EXPECT_EXPRESSION(std::ranges::any_of(after_image->messages, [](const auto &message) {
    return message.tool_result &&
           message.tool_result->call_id == "call-image" &&
           message.tool_result->content == "Generated image for: fixture";
  }));
  controller.Cancel();

  linecode::domain::ChatImage input_image{
      .name = "photo.png",
      .mime_type = "image/png",
      .base64 = "iVBORw0KGgo=",
  };
  auto image_input = controller.Begin("describe", {}, input_image);
  EXPECT_EXPRESSION(image_input.has_value());
  EXPECT_EXPRESSION(recording->Messages().back().image == input_image);
  EXPECT_EXPRESSION(image_input->messages.back().image == input_image);
  controller.Cancel();

  auto attachment_only =
      controller.Begin("", {{"notes.md", "/workspace/notes.md", "local"}});
  EXPECT_EXPRESSION(attachment_only.has_value());
  EXPECT_EXPRESSION(recording->Messages().back().content.empty());
  EXPECT_EXPRESSION(recording->Messages().back().attachments.size() == 1U);
  EXPECT_EXPRESSION(controller.Observe(
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
  EXPECT_EXPRESSION(cancelled_tool && cancelled_tool->result &&
         cancelled_tool->call.status ==
             linecode::domain::ToolCallStatus::rejected);
}

} // namespace

TEST(completion_pipeline_tests, LegacySuite) {
  EncodesOpenAiRequestWithoutLosingUtf8OrControlCharacters();
  DecodesBufferedFixtureAndUnicodeEscapes();
  DecodesOpenAiSsePayloadsAndDoneSentinel();
  BoundsAggregateStreamTextAcrossManySmallDeltas();
  JoinsEndpointExactlyOnce();
  EnforcesHttpsOrLiteralPrivateCleartextHosts();
  GenerationControllerRejectsStaleResultsAndPersistsAssistant();
}
