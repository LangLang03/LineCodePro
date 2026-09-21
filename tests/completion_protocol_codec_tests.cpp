#include <algorithm>
#include <array>
#include "gtest_support.h"
#include <string>
#include <string_view>

#include "infrastructure/archive_json.h"
#include "infrastructure/completion_protocol_codec.h"
#include "infrastructure/openai_chat_codec.h"

namespace {

namespace json = linecode::infrastructure::archive_json;
using linecode::application::CompletionMessage;
using linecode::application::CompletionRequest;
using linecode::application::CompletionRole;
using linecode::domain::ChatImage;
using linecode::domain::ModelProtocol;
using linecode::infrastructure::EncodeOpenAiChatRequest;
using linecode::infrastructure::CompletionProtocolCodec;
using linecode::infrastructure::FindCompletionProtocolCodec;

CompletionRequest Request(ModelProtocol protocol, bool stream) {
  CompletionRequest request;
  request.model.protocol = protocol;
  request.model.base_url = "https://example.test/v1";
  request.model.api_key = "secret";
  request.model.model_id = "linecode-test-model";
  request.messages = {
      CompletionMessage{.role = CompletionRole::user,
                        .content = "question",
                        .tool_calls = {},
                        .tool_result = std::nullopt},
      CompletionMessage{.role = CompletionRole::assistant,
                        .content = "answer",
                        .tool_calls = {},
                        .tool_result = std::nullopt},
  };
  request.stream = stream;
  return request;
}

CompletionRequest ToolRequest(ModelProtocol protocol, bool stream) {
  auto request = Request(protocol, stream);
  request.tools.push_back(
      {.name = "mcpx_demo_echo",
       .description = "Echo through MCP",
       .parameters_json =
           R"({"properties":{"text":{"type":"string"}},"type":"object"})"});
  request.messages.push_back(CompletionMessage::Assistant(
      "calling", {{.id = "call-old",
                   .name = "mcpx_demo_echo",
                   .arguments_json = R"({"text":"old"})"}}));
  request.messages.push_back(CompletionMessage::Tool({.call_id = "call-old",
                                                      .name = "mcpx_demo_echo",
                                                      .content = "old result",
                                                      .error = false}));
  return request;
}

bool HasHeader(
    const linecode::infrastructure::CompletionProtocolWireRequest &wire,
    std::string_view name, std::string_view value) {
  return std::ranges::any_of(wire.headers, [&](const auto &header) {
    return header.first == name && header.second == value;
  });
}

const json::Object &BodyObject(const std::string &body, json::Value &storage) {
  auto parsed = json::Parse(body);
  EXPECT_EXPRESSION(parsed);
  storage = std::move(*parsed);
  const auto *object = json::AsObject(&storage);
  EXPECT_EXPRESSION(object != nullptr);
  return *object;
}

void OpenAiCodecStillUsesChatCompletions() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  EXPECT_EXPRESSION(codec != nullptr);
  const auto wire =
      codec->encode(Request(ModelProtocol::openai_compatible, true),
                    "https://example.test/v1");
  EXPECT_EXPRESSION(wire);
  EXPECT_EXPRESSION(wire->endpoint == "https://example.test/v1/chat/completions");
  EXPECT_EXPRESSION(HasHeader(*wire, "Authorization", "Bearer secret"));
  EXPECT_EXPRESSION(wire->body.find("\"stream\":true") != std::string::npos);
}

void OpenAiCodecCarriesSystemAndProviderReasoning() {
  auto request = Request(ModelProtocol::openai_compatible, true);
  request.model.model_id = "gpt-5.1";
  request.messages.insert(
      request.messages.begin(),
      {.role = CompletionRole::system, .content = "LineCode system"});
  request.messages[2].reasoning_content = "prior thought";
  request.reasoning_effort = linecode::domain::ReasoningEffort::maximum;
  request.preserve_reasoning = true;
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  const auto wire = codec->encode(request, request.model.base_url);
  EXPECT_EXPRESSION(wire);
  EXPECT_EXPRESSION(wire->body.contains("\"role\":\"system\""));
  EXPECT_EXPRESSION(wire->body.contains("\"reasoning_effort\":\"xhigh\""));
  EXPECT_EXPRESSION(wire->body.contains("\"reasoning_content\":\"prior thought\""));

  request.model.base_url = "https://dashscope.aliyuncs.com/compatible-mode/v1";
  request.model.model_id = "qwen3-max";
  request.reasoning_effort = linecode::domain::ReasoningEffort::high;
  const auto dashscope = codec->encode(request, request.model.base_url);
  EXPECT_EXPRESSION(dashscope);
  EXPECT_EXPRESSION(dashscope->body.contains("\"enable_thinking\":true"));
  EXPECT_EXPRESSION(dashscope->body.contains("\"thinking_budget\":8192"));
  EXPECT_EXPRESSION(dashscope->body.contains("\"preserve_thinking\":true"));

  request.model.base_url = "https://integrate.api.nvidia.com/v1";
  const auto nvidia = codec->encode(request, request.model.base_url);
  EXPECT_EXPRESSION(nvidia);
  EXPECT_EXPRESSION(!nvidia->body.contains("reasoning_effort"));
  EXPECT_EXPRESSION(!nvidia->body.contains("enable_thinking"));
}

void OpenAiCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::openai_compatible, true),
                    "https://example.test/v1");
  EXPECT_EXPRESSION(wire && wire->body.contains("\"tools\""));
  EXPECT_EXPRESSION(wire->body.contains("\"tool_call_id\":\"call-old\""));

  const auto buffered = codec->decode_response(R"json({
    "choices":[{"message":{"content":null,"tool_calls":[{
      "id":"call-new","type":"function","function":{
        "name":"mcpx_demo_echo","arguments":"{\"text\":\"new\"}"}}]}}],
    "usage":{"prompt_tokens":7,"completion_tokens":2}
  })json");
  EXPECT_EXPRESSION(buffered && buffered->text.empty());
  EXPECT_EXPRESSION((buffered->tool_calls ==
               std::vector<linecode::application::CompletionToolCall>{
                   {.id = "call-new",
                    .name = "mcpx_demo_echo",
                    .arguments_json = R"({"text":"new"})"}}));

  const auto first = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"tool_calls":[{"index":0,"id":"call-new","function":{
      "name":"mcpx_demo_echo","arguments":"{\"text\":"}}]},
    "finish_reason":null}]})json");
  const auto second = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"new\"}"}}]},
    "finish_reason":"tool_calls"}]})json");
  EXPECT_EXPRESSION(first && first->tool_call_deltas.size() == 1);
  EXPECT_EXPRESSION(first->tool_call_deltas[0].id == "call-new");
  EXPECT_EXPRESSION(second && second->tool_call_deltas[0].arguments_delta == "\"new\"}");
}

void OpenAiCodecDecodesReasoningSeparatelyFromAnswerText() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  const auto buffered = codec->decode_response(R"json({
    "choices":[{"message":{"content":"answer",
      "reasoning_content":"private reasoning"}}]
  })json");
  EXPECT_EXPRESSION(buffered && buffered->text == "answer");
  EXPECT_EXPRESSION(buffered->reasoning_content == "private reasoning");

  const auto streamed = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"reasoning":{"text":"reasoning delta"}},
    "finish_reason":null}]})json");
  EXPECT_EXPRESSION(streamed && streamed->reasoning_deltas.size() == 1U);
  EXPECT_EXPRESSION(streamed->reasoning_deltas.front().text == "reasoning delta");
}

void AnthropicCodecMatchesMessagesContract() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  EXPECT_EXPRESSION(codec != nullptr);
  const auto wire = codec->encode(
      Request(ModelProtocol::anthropic_messages, true), "https://example.test");
  EXPECT_EXPRESSION(wire);
  EXPECT_EXPRESSION(wire->endpoint == "https://example.test/v1/messages");
  EXPECT_EXPRESSION(HasHeader(*wire, "x-api-key", "secret"));
  EXPECT_EXPRESSION(HasHeader(*wire, "anthropic-version", "2023-06-01"));

  json::Value body_storage{json::Null{}};
  const auto &body = BodyObject(wire->body, body_storage);
  EXPECT_EXPRESSION(json::AsString(json::Find(body, "model")) != nullptr);
  EXPECT_EXPRESSION(json::AsArray(json::Find(body, "messages"))->size() == 2U);

  const auto response = codec->decode_response(R"json({
    "content":[{"type":"text","text":"fixed"}],
    "usage":{"input_tokens":3,"output_tokens":4}
  })json");
  EXPECT_EXPRESSION(response && response->text == "fixed");
  EXPECT_EXPRESSION(response->input_tokens == 3 && response->output_tokens == 4);

  const auto delta = codec->decode_stream_event(R"json({
    "type":"content_block_delta",
    "delta":{"type":"text_delta","text":"fi"}
  })json");
  EXPECT_EXPRESSION(delta && delta->text_delta == "fi" && !delta->done);
  const auto stop =
      codec->decode_stream_event(R"json({"type":"message_stop"})json");
  EXPECT_EXPRESSION(stop && stop->done);

  const auto versioned =
      codec->encode(Request(ModelProtocol::anthropic_messages, false),
                    "https://example.test/v1/");
  EXPECT_EXPRESSION(versioned &&
         versioned->endpoint == "https://example.test/v1/messages");
}

void AnthropicCodecUsesSystemAndThinkingBudget() {
  auto request = Request(ModelProtocol::anthropic_messages, true);
  request.messages.insert(
      request.messages.begin(),
      {.role = CompletionRole::system, .content = "LineCode system"});
  request.reasoning_effort = linecode::domain::ReasoningEffort::high;
  request.preserve_reasoning = true; // Intentionally unsupported on wire.
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  const auto wire = codec->encode(request, request.model.base_url);
  EXPECT_EXPRESSION(wire);
  json::Value storage{json::Null{}};
  const auto &body = BodyObject(wire->body, storage);
  EXPECT_EXPRESSION(*json::AsString(json::Find(body, "system")) == "LineCode system");
  EXPECT_EXPRESSION(json::AsArray(json::Find(body, "messages"))->size() == 2U);
  EXPECT_EXPRESSION(std::get<std::int64_t>(*json::Find(body, "max_tokens")) == 9216);
  const auto *thinking = json::AsObject(json::Find(body, "thinking"));
  EXPECT_EXPRESSION(thinking != nullptr);
  EXPECT_EXPRESSION(std::get<std::int64_t>(*json::Find(*thinking, "budget_tokens")) ==
         8192);
  EXPECT_EXPRESSION(json::Find(body, "reasoning_content") == nullptr);
}

void AnthropicCodecDecodesThinkingBlocks() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  const auto buffered = codec->decode_response(R"json({
    "content":[{"type":"thinking","thinking":"think"},
      {"type":"text","text":"answer"}]
  })json");
  EXPECT_EXPRESSION(buffered && buffered->reasoning_content == "think");
  EXPECT_EXPRESSION(buffered->text == "answer");

  const auto streamed = codec->decode_stream_event(R"json({
    "type":"content_block_delta","index":0,
    "delta":{"type":"thinking_delta","thinking":"delta"}
  })json");
  EXPECT_EXPRESSION(streamed && streamed->reasoning_deltas.size() == 1U);
  EXPECT_EXPRESSION(streamed->reasoning_deltas.front().text == "delta");
}

void AnthropicCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::anthropic_messages, true),
                    "https://example.test");
  EXPECT_EXPRESSION(wire && wire->body.contains("\"input_schema\""));
  EXPECT_EXPRESSION(wire->body.contains("\"tool_result\""));
  EXPECT_EXPRESSION(wire->body.contains("\"is_error\":false"));

  const auto buffered = codec->decode_response(R"json({
    "content":[{"type":"tool_use","id":"anthropic-call",
      "name":"mcpx_demo_echo","input":{"text":"new"}}]
  })json");
  EXPECT_EXPRESSION(buffered && buffered->tool_calls.size() == 1);
  EXPECT_EXPRESSION(buffered->tool_calls[0].id == "anthropic-call");
  EXPECT_EXPRESSION(buffered->tool_calls[0].arguments_json == R"({"text":"new"})");

  const auto start = codec->decode_stream_event(R"json({
    "type":"content_block_start","index":1,"content_block":{
      "type":"tool_use","id":"anthropic-call","name":"mcpx_demo_echo","input":{}}})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"content_block_delta","index":1,"delta":{
      "type":"input_json_delta","partial_json":"{\"text\":\"new\"}"}})json");
  EXPECT_EXPRESSION(start && start->tool_call_deltas[0].index == 1);
  EXPECT_EXPRESSION(start->tool_call_deltas[0].name == "mcpx_demo_echo");
  EXPECT_EXPRESSION(delta &&
         delta->tool_call_deltas[0].arguments_delta == R"({"text":"new"})");
}

void CodexCodecMatchesResponsesContract() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  EXPECT_EXPRESSION(codec != nullptr);
  const auto wire = codec->encode(Request(ModelProtocol::codex_responses, true),
                                  "https://example.test/v1/chat/completions");
  EXPECT_EXPRESSION(wire);
  EXPECT_EXPRESSION(wire->endpoint == "https://example.test/v1/responses");
  EXPECT_EXPRESSION(HasHeader(*wire, "Authorization", "Bearer secret"));
  EXPECT_EXPRESSION(HasHeader(*wire, "originator", "codex_cli_rs"));

  json::Value body_storage{json::Null{}};
  const auto &body = BodyObject(wire->body, body_storage);
  EXPECT_EXPRESSION(json::AsArray(json::Find(body, "input"))->size() == 2U);
  EXPECT_EXPRESSION(json::AsObject(json::Find(body, "client_metadata")) != nullptr);

  const auto response = codec->decode_response(R"json({
    "output":[{"type":"message","content":[
      {"type":"output_text","text":"fixed"}]}],
    "usage":{"input_tokens":5,"output_tokens":6}
  })json");
  EXPECT_EXPRESSION(response && response->text == "fixed");
  EXPECT_EXPRESSION(response->input_tokens == 5 && response->output_tokens == 6);

  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.output_text.delta","delta":"fi"
  })json");
  EXPECT_EXPRESSION(delta && delta->text_delta == "fi");
  const auto completed = codec->decode_stream_event(R"json({
    "type":"response.completed","response":{
      "output":[{"content":[{"type":"output_text","text":"fixed"}]}],
      "usage":{"input_tokens":5,"output_tokens":6}}
  })json");
  EXPECT_EXPRESSION(completed && completed->done && completed->final_text == "fixed");
  EXPECT_EXPRESSION(completed->input_tokens == 5 && completed->output_tokens == 6);
}

void CodexCodecUsesInstructionsAndResponsesReasoning() {
  auto request = Request(ModelProtocol::codex_responses, true);
  request.messages.insert(
      request.messages.begin(),
      {.role = CompletionRole::system, .content = "LineCode system"});
  request.reasoning_effort = linecode::domain::ReasoningEffort::maximum;
  request.preserve_reasoning = true;
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  const auto wire = codec->encode(request, request.model.base_url);
  EXPECT_EXPRESSION(wire);
  json::Value storage{json::Null{}};
  const auto &body = BodyObject(wire->body, storage);
  EXPECT_EXPRESSION(*json::AsString(json::Find(body, "instructions")) ==
         "LineCode system");
  EXPECT_EXPRESSION(json::AsArray(json::Find(body, "input"))->size() == 2U);
  const auto *reasoning = json::AsObject(json::Find(body, "reasoning"));
  EXPECT_EXPRESSION(reasoning != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*reasoning, "effort")) == "high");
  EXPECT_EXPRESSION(*json::AsString(json::Find(*reasoning, "summary")) == "auto");
  const auto *include = json::AsArray(json::Find(body, "include"));
  EXPECT_EXPRESSION(include && include->size() == 1U);
  EXPECT_EXPRESSION(*json::AsString(&include->front()) == "reasoning.encrypted_content");
}

void CodexCodecDecodesReasoningSummaryEvents() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  const auto buffered = codec->decode_response(R"json({
    "output":[{"type":"reasoning","summary":[
      {"type":"summary_text","text":"First"},
      {"type":"summary_text","text":"Second"}]},
      {"type":"message","content":[
        {"type":"output_text","text":"answer"}]}]
  })json");
  EXPECT_EXPRESSION(buffered && buffered->reasoning_content == "First | Second");
  EXPECT_EXPRESSION(buffered->text == "answer");

  const auto boundary = codec->decode_stream_event(R"json({
    "type":"response.reasoning_summary_part.added"})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.reasoning_summary_text.delta","delta":"First"})json");
  EXPECT_EXPRESSION(boundary && boundary->reasoning_deltas.size() == 1U);
  EXPECT_EXPRESSION(boundary->reasoning_deltas.front().starts_new_segment);
  EXPECT_EXPRESSION(delta && delta->reasoning_deltas.size() == 1U);
  EXPECT_EXPRESSION(delta->reasoning_deltas.front().kind ==
         linecode::application::CompletionReasoningKind::summary);
}

void CodexCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::codex_responses, true),
                    "https://example.test/v1");
  EXPECT_EXPRESSION(wire && wire->body.contains("\"function_call_output\""));
  EXPECT_EXPRESSION(wire->body.contains("\"parameters\""));

  const auto buffered = codec->decode_response(R"json({
    "output":[{"type":"function_call","call_id":"codex-call",
      "name":"mcpx_demo_echo","arguments":"{\"text\":\"new\"}"}]
  })json");
  EXPECT_EXPRESSION(buffered && buffered->tool_calls.size() == 1);
  EXPECT_EXPRESSION(buffered->tool_calls[0].id == "codex-call");

  const auto added = codec->decode_stream_event(R"json({
    "type":"response.output_item.added","output_index":0,"item":{
      "type":"function_call","call_id":"codex-call","name":"mcpx_demo_echo"}})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.function_call_arguments.delta","output_index":0,
    "delta":"{\"text\":\"new\"}"})json");
  EXPECT_EXPRESSION(added && added->tool_call_deltas[0].id == "codex-call");
  EXPECT_EXPRESSION(delta &&
         delta->tool_call_deltas[0].arguments_delta == R"({"text":"new"})");
}

void UnsupportedProtocolsHaveNoRegisteredCodec() {
  EXPECT_EXPRESSION(FindCompletionProtocolCodec(ModelProtocol::local_gguf) == nullptr);
}

// An attached image has to reach the wire in each protocol's own multimodal
// shape; legacy carried base64 + mime through `onSendWithImage`.
void ImagePartsFollowEachProtocolShape() {
  ChatImage image;
  image.name = "photo.jpg";
  image.mime_type = "image/jpeg";
  image.base64 = "QUJD";

  const auto with_image = [&](ModelProtocol protocol) {
    auto request = Request(protocol, false);
    request.messages[0].content = "look";
    request.messages[0].image = image;
    return request;
  };

  // Anthropic: a `base64` source block next to the text.
  const auto *anthropic =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  EXPECT_EXPRESSION(anthropic != nullptr);
  const auto anthropic_wire =
      anthropic->encode(with_image(ModelProtocol::anthropic_messages),
                        "https://example.test");
  EXPECT_EXPRESSION(anthropic_wire);
  json::Value anthropic_storage{json::Null{}};
  const auto &anthropic_body = BodyObject(anthropic_wire->body, anthropic_storage);
  const auto *anthropic_messages =
      json::AsArray(json::Find(anthropic_body, "messages"));
  EXPECT_EXPRESSION(anthropic_messages != nullptr && !anthropic_messages->empty());
  const auto *first = json::AsObject(&anthropic_messages->front());
  EXPECT_EXPRESSION(first != nullptr);
  const auto *parts = json::AsArray(json::Find(*first, "content"));
  EXPECT_EXPRESSION(parts != nullptr && parts->size() == 2U);
  const auto *image_part = json::AsObject(&parts->at(1));
  EXPECT_EXPRESSION(image_part != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*image_part, "type")) == "image");
  const auto *source = json::AsObject(json::Find(*image_part, "source"));
  EXPECT_EXPRESSION(source != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*source, "type")) == "base64");
  EXPECT_EXPRESSION(*json::AsString(json::Find(*source, "media_type")) == "image/jpeg");
  EXPECT_EXPRESSION(*json::AsString(json::Find(*source, "data")) == "QUJD");

  // Responses: an `input_image` part whose url is the data URL.
  const auto *codex = FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  EXPECT_EXPRESSION(codex != nullptr);
  const auto codex_wire = codex->encode(with_image(ModelProtocol::codex_responses),
                                        "https://example.test");
  EXPECT_EXPRESSION(codex_wire);
  json::Value codex_storage{json::Null{}};
  const auto &codex_body = BodyObject(codex_wire->body, codex_storage);
  const auto *codex_input = json::AsArray(json::Find(codex_body, "input"));
  EXPECT_EXPRESSION(codex_input != nullptr && !codex_input->empty());
  const auto *codex_message = json::AsObject(&codex_input->front());
  EXPECT_EXPRESSION(codex_message != nullptr);
  const auto *codex_parts = json::AsArray(json::Find(*codex_message, "content"));
  EXPECT_EXPRESSION(codex_parts != nullptr && codex_parts->size() == 2U);
  const auto *codex_image = json::AsObject(&codex_parts->at(1));
  EXPECT_EXPRESSION(codex_image != nullptr);
  EXPECT_EXPRESSION(*json::AsString(json::Find(*codex_image, "type")) == "input_image");
  EXPECT_EXPRESSION(*json::AsString(json::Find(*codex_image, "image_url")) ==
         "data:image/jpeg;base64,QUJD");

  // The HuxerUI picker keeps PNG input as PNG because the public SDK has no
  // raster transcoder. Every supported protocol must preserve that MIME too.
  const std::array protocols{
      ModelProtocol::anthropic_messages,
      ModelProtocol::codex_responses,
      ModelProtocol::openai_compatible,
  };
  for (const auto protocol : protocols) {
    auto request = with_image(protocol);
    request.messages[0].image->mime_type = "image/png";
    const auto *codec = FindCompletionProtocolCodec(protocol);
    EXPECT_EXPRESSION(codec != nullptr);
    const auto wire = codec->encode(request, "https://example.test");
    EXPECT_EXPRESSION(wire.has_value());
    EXPECT_EXPRESSION(wire->body.find("image/png") != std::string::npos);
  }
}

// The OpenAI Chat Completions body is assembled into a multimodal part array
// only when an image is present, so a plain turn keeps its string content.
void OpenAiImageTurnTurnsContentIntoParts() {
  ChatImage image;
  image.mime_type = "image/jpeg";
  image.base64 = "QUJD";
  auto request = Request(ModelProtocol::openai_compatible, false);
  request.messages[0].content = "look";
  request.messages[0].image = image;
  const auto json_text = EncodeOpenAiChatRequest(request);
  EXPECT_EXPRESSION(json_text.find("\"type\":\"image_url\"") != std::string::npos);
  EXPECT_EXPRESSION(json_text.find("\"url\":\"data:image/jpeg;base64,QUJD\"") !=
         std::string::npos);
  EXPECT_EXPRESSION(json_text.find("\"type\":\"text\",\"text\":\"look\"") !=
         std::string::npos);

  // Without an image the legacy string content is unchanged.
  auto plain = Request(ModelProtocol::openai_compatible, false);
  const auto plain_text = EncodeOpenAiChatRequest(plain);
  EXPECT_EXPRESSION(plain_text.find("image_url") == std::string::npos);
  EXPECT_EXPRESSION(plain_text.find("\"content\":\"question\"") != std::string::npos);
}

// A compatible endpoint puts usage on the final chunk, whose `choices` array is
// empty. The legacy protocol read it before looking at choices for that reason;
// skipping it leaves the compaction trigger stuck on its local estimate.
void StreamUsageSurvivesEmptyChoices() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  EXPECT_EXPRESSION(codec != nullptr);

  // A usage-only chunk still has to surface the counts.
  const auto usage_only = codec->decode_stream_event(
      R"json({"choices":[],"usage":{"prompt_tokens":4321,"completion_tokens":12}})json");
  EXPECT_EXPRESSION(usage_only);
  EXPECT_EXPRESSION(usage_only->input_tokens == 4321);
  EXPECT_EXPRESSION(usage_only->output_tokens == 12);

  // A normal content chunk carries no usage and must not invent one.
  const auto content = codec->decode_stream_event(
      R"json({"choices":[{"delta":{"content":"hi"}}]})json");
  EXPECT_EXPRESSION(content);
  EXPECT_EXPRESSION(content->text_delta && *content->text_delta == "hi");
  EXPECT_EXPRESSION(content->input_tokens == 0);

  // Usage alongside content is kept too.
  const auto both = codec->decode_stream_event(
      R"json({"choices":[{"delta":{"content":"hi"}}],"usage":{"prompt_tokens":77}})json");
  EXPECT_EXPRESSION(both);
  EXPECT_EXPRESSION(both->input_tokens == 77);

  // Non-streaming responses already reported usage; keep that working.
  const auto buffered = codec->decode_response(
      R"json({"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":9,"completion_tokens":2}})json");
  EXPECT_EXPRESSION(buffered);
  EXPECT_EXPRESSION(buffered->input_tokens == 9 && buffered->output_tokens == 2);
}

} // namespace

TEST(completion_protocol_codec_tests, LegacySuite) {
  OpenAiCodecStillUsesChatCompletions();
  OpenAiCodecCarriesSystemAndProviderReasoning();
  OpenAiCodecSupportsToolRoundTrips();
  OpenAiCodecDecodesReasoningSeparatelyFromAnswerText();
  AnthropicCodecMatchesMessagesContract();
  AnthropicCodecUsesSystemAndThinkingBudget();
  AnthropicCodecDecodesThinkingBlocks();
  AnthropicCodecSupportsToolRoundTrips();
  CodexCodecMatchesResponsesContract();
  CodexCodecUsesInstructionsAndResponsesReasoning();
  CodexCodecDecodesReasoningSummaryEvents();
  CodexCodecSupportsToolRoundTrips();
  ImagePartsFollowEachProtocolShape();
  OpenAiImageTurnTurnsContentIntoParts();
  StreamUsageSurvivesEmptyChoices();
  UnsupportedProtocolsHaveNoRegisteredCodec();
}
