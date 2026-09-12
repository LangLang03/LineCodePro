#include <algorithm>
#include <cassert>
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
  assert(parsed);
  storage = std::move(*parsed);
  const auto *object = json::AsObject(&storage);
  assert(object != nullptr);
  return *object;
}

void OpenAiCodecStillUsesChatCompletions() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  assert(codec != nullptr);
  const auto wire =
      codec->encode(Request(ModelProtocol::openai_compatible, true),
                    "https://example.test/v1");
  assert(wire);
  assert(wire->endpoint == "https://example.test/v1/chat/completions");
  assert(HasHeader(*wire, "Authorization", "Bearer secret"));
  assert(wire->body.find("\"stream\":true") != std::string::npos);
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
  assert(wire);
  assert(wire->body.contains("\"role\":\"system\""));
  assert(wire->body.contains("\"reasoning_effort\":\"xhigh\""));
  assert(wire->body.contains("\"reasoning_content\":\"prior thought\""));

  request.model.base_url = "https://dashscope.aliyuncs.com/compatible-mode/v1";
  request.model.model_id = "qwen3-max";
  request.reasoning_effort = linecode::domain::ReasoningEffort::high;
  const auto dashscope = codec->encode(request, request.model.base_url);
  assert(dashscope);
  assert(dashscope->body.contains("\"enable_thinking\":true"));
  assert(dashscope->body.contains("\"thinking_budget\":8192"));
  assert(dashscope->body.contains("\"preserve_thinking\":true"));

  request.model.base_url = "https://integrate.api.nvidia.com/v1";
  const auto nvidia = codec->encode(request, request.model.base_url);
  assert(nvidia);
  assert(!nvidia->body.contains("reasoning_effort"));
  assert(!nvidia->body.contains("enable_thinking"));
}

void OpenAiCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::openai_compatible, true),
                    "https://example.test/v1");
  assert(wire && wire->body.contains("\"tools\""));
  assert(wire->body.contains("\"tool_call_id\":\"call-old\""));

  const auto buffered = codec->decode_response(R"json({
    "choices":[{"message":{"content":null,"tool_calls":[{
      "id":"call-new","type":"function","function":{
        "name":"mcpx_demo_echo","arguments":"{\"text\":\"new\"}"}}]}}],
    "usage":{"prompt_tokens":7,"completion_tokens":2}
  })json");
  assert(buffered && buffered->text.empty());
  assert(buffered->tool_calls ==
         std::vector<linecode::application::CompletionToolCall>{
             {.id = "call-new",
              .name = "mcpx_demo_echo",
              .arguments_json = R"({"text":"new"})"}});

  const auto first = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"tool_calls":[{"index":0,"id":"call-new","function":{
      "name":"mcpx_demo_echo","arguments":"{\"text\":"}}]},
    "finish_reason":null}]})json");
  const auto second = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"new\"}"}}]},
    "finish_reason":"tool_calls"}]})json");
  assert(first && first->tool_call_deltas.size() == 1);
  assert(first->tool_call_deltas[0].id == "call-new");
  assert(second && second->tool_call_deltas[0].arguments_delta == "\"new\"}");
}

void OpenAiCodecDecodesReasoningSeparatelyFromAnswerText() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::openai_compatible);
  const auto buffered = codec->decode_response(R"json({
    "choices":[{"message":{"content":"answer",
      "reasoning_content":"private reasoning"}}]
  })json");
  assert(buffered && buffered->text == "answer");
  assert(buffered->reasoning_content == "private reasoning");

  const auto streamed = codec->decode_stream_event(R"json({"choices":[{
    "delta":{"reasoning":{"text":"reasoning delta"}},
    "finish_reason":null}]})json");
  assert(streamed && streamed->reasoning_deltas.size() == 1U);
  assert(streamed->reasoning_deltas.front().text == "reasoning delta");
}

void AnthropicCodecMatchesMessagesContract() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  assert(codec != nullptr);
  const auto wire = codec->encode(
      Request(ModelProtocol::anthropic_messages, true), "https://example.test");
  assert(wire);
  assert(wire->endpoint == "https://example.test/v1/messages");
  assert(HasHeader(*wire, "x-api-key", "secret"));
  assert(HasHeader(*wire, "anthropic-version", "2023-06-01"));

  json::Value body_storage{json::Null{}};
  const auto &body = BodyObject(wire->body, body_storage);
  assert(json::AsString(json::Find(body, "model")) != nullptr);
  assert(json::AsArray(json::Find(body, "messages"))->size() == 2U);

  const auto response = codec->decode_response(R"json({
    "content":[{"type":"text","text":"fixed"}],
    "usage":{"input_tokens":3,"output_tokens":4}
  })json");
  assert(response && response->text == "fixed");
  assert(response->input_tokens == 3 && response->output_tokens == 4);

  const auto delta = codec->decode_stream_event(R"json({
    "type":"content_block_delta",
    "delta":{"type":"text_delta","text":"fi"}
  })json");
  assert(delta && delta->text_delta == "fi" && !delta->done);
  const auto stop =
      codec->decode_stream_event(R"json({"type":"message_stop"})json");
  assert(stop && stop->done);

  const auto versioned =
      codec->encode(Request(ModelProtocol::anthropic_messages, false),
                    "https://example.test/v1/");
  assert(versioned &&
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
  assert(wire);
  json::Value storage{json::Null{}};
  const auto &body = BodyObject(wire->body, storage);
  assert(*json::AsString(json::Find(body, "system")) == "LineCode system");
  assert(json::AsArray(json::Find(body, "messages"))->size() == 2U);
  assert(std::get<std::int64_t>(*json::Find(body, "max_tokens")) == 9216);
  const auto *thinking = json::AsObject(json::Find(body, "thinking"));
  assert(thinking != nullptr);
  assert(std::get<std::int64_t>(*json::Find(*thinking, "budget_tokens")) ==
         8192);
  assert(json::Find(body, "reasoning_content") == nullptr);
}

void AnthropicCodecDecodesThinkingBlocks() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  const auto buffered = codec->decode_response(R"json({
    "content":[{"type":"thinking","thinking":"think"},
      {"type":"text","text":"answer"}]
  })json");
  assert(buffered && buffered->reasoning_content == "think");
  assert(buffered->text == "answer");

  const auto streamed = codec->decode_stream_event(R"json({
    "type":"content_block_delta","index":0,
    "delta":{"type":"thinking_delta","thinking":"delta"}
  })json");
  assert(streamed && streamed->reasoning_deltas.size() == 1U);
  assert(streamed->reasoning_deltas.front().text == "delta");
}

void AnthropicCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::anthropic_messages);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::anthropic_messages, true),
                    "https://example.test");
  assert(wire && wire->body.contains("\"input_schema\""));
  assert(wire->body.contains("\"tool_result\""));
  assert(wire->body.contains("\"is_error\":false"));

  const auto buffered = codec->decode_response(R"json({
    "content":[{"type":"tool_use","id":"anthropic-call",
      "name":"mcpx_demo_echo","input":{"text":"new"}}]
  })json");
  assert(buffered && buffered->tool_calls.size() == 1);
  assert(buffered->tool_calls[0].id == "anthropic-call");
  assert(buffered->tool_calls[0].arguments_json == R"({"text":"new"})");

  const auto start = codec->decode_stream_event(R"json({
    "type":"content_block_start","index":1,"content_block":{
      "type":"tool_use","id":"anthropic-call","name":"mcpx_demo_echo","input":{}}})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"content_block_delta","index":1,"delta":{
      "type":"input_json_delta","partial_json":"{\"text\":\"new\"}"}})json");
  assert(start && start->tool_call_deltas[0].index == 1);
  assert(start->tool_call_deltas[0].name == "mcpx_demo_echo");
  assert(delta &&
         delta->tool_call_deltas[0].arguments_delta == R"({"text":"new"})");
}

void CodexCodecMatchesResponsesContract() {
  const CompletionProtocolCodec *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  assert(codec != nullptr);
  const auto wire = codec->encode(Request(ModelProtocol::codex_responses, true),
                                  "https://example.test/v1/chat/completions");
  assert(wire);
  assert(wire->endpoint == "https://example.test/v1/responses");
  assert(HasHeader(*wire, "Authorization", "Bearer secret"));
  assert(HasHeader(*wire, "originator", "codex_cli_rs"));

  json::Value body_storage{json::Null{}};
  const auto &body = BodyObject(wire->body, body_storage);
  assert(json::AsArray(json::Find(body, "input"))->size() == 2U);
  assert(json::AsObject(json::Find(body, "client_metadata")) != nullptr);

  const auto response = codec->decode_response(R"json({
    "output":[{"type":"message","content":[
      {"type":"output_text","text":"fixed"}]}],
    "usage":{"input_tokens":5,"output_tokens":6}
  })json");
  assert(response && response->text == "fixed");
  assert(response->input_tokens == 5 && response->output_tokens == 6);

  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.output_text.delta","delta":"fi"
  })json");
  assert(delta && delta->text_delta == "fi");
  const auto completed = codec->decode_stream_event(R"json({
    "type":"response.completed","response":{
      "output":[{"content":[{"type":"output_text","text":"fixed"}]}],
      "usage":{"input_tokens":5,"output_tokens":6}}
  })json");
  assert(completed && completed->done && completed->final_text == "fixed");
  assert(completed->input_tokens == 5 && completed->output_tokens == 6);
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
  assert(wire);
  json::Value storage{json::Null{}};
  const auto &body = BodyObject(wire->body, storage);
  assert(*json::AsString(json::Find(body, "instructions")) ==
         "LineCode system");
  assert(json::AsArray(json::Find(body, "input"))->size() == 2U);
  const auto *reasoning = json::AsObject(json::Find(body, "reasoning"));
  assert(reasoning != nullptr);
  assert(*json::AsString(json::Find(*reasoning, "effort")) == "high");
  assert(*json::AsString(json::Find(*reasoning, "summary")) == "auto");
  const auto *include = json::AsArray(json::Find(body, "include"));
  assert(include && include->size() == 1U);
  assert(*json::AsString(&include->front()) == "reasoning.encrypted_content");
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
  assert(buffered && buffered->reasoning_content == "First | Second");
  assert(buffered->text == "answer");

  const auto boundary = codec->decode_stream_event(R"json({
    "type":"response.reasoning_summary_part.added"})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.reasoning_summary_text.delta","delta":"First"})json");
  assert(boundary && boundary->reasoning_deltas.size() == 1U);
  assert(boundary->reasoning_deltas.front().starts_new_segment);
  assert(delta && delta->reasoning_deltas.size() == 1U);
  assert(delta->reasoning_deltas.front().kind ==
         linecode::application::CompletionReasoningKind::summary);
}

void CodexCodecSupportsToolRoundTrips() {
  const auto *codec =
      FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  const auto wire =
      codec->encode(ToolRequest(ModelProtocol::codex_responses, true),
                    "https://example.test/v1");
  assert(wire && wire->body.contains("\"function_call_output\""));
  assert(wire->body.contains("\"parameters\""));

  const auto buffered = codec->decode_response(R"json({
    "output":[{"type":"function_call","call_id":"codex-call",
      "name":"mcpx_demo_echo","arguments":"{\"text\":\"new\"}"}]
  })json");
  assert(buffered && buffered->tool_calls.size() == 1);
  assert(buffered->tool_calls[0].id == "codex-call");

  const auto added = codec->decode_stream_event(R"json({
    "type":"response.output_item.added","output_index":0,"item":{
      "type":"function_call","call_id":"codex-call","name":"mcpx_demo_echo"}})json");
  const auto delta = codec->decode_stream_event(R"json({
    "type":"response.function_call_arguments.delta","output_index":0,
    "delta":"{\"text\":\"new\"}"})json");
  assert(added && added->tool_call_deltas[0].id == "codex-call");
  assert(delta &&
         delta->tool_call_deltas[0].arguments_delta == R"({"text":"new"})");
}

void UnsupportedProtocolsHaveNoRegisteredCodec() {
  assert(FindCompletionProtocolCodec(ModelProtocol::local_gguf) == nullptr);
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
  assert(anthropic != nullptr);
  const auto anthropic_wire =
      anthropic->encode(with_image(ModelProtocol::anthropic_messages),
                        "https://example.test");
  assert(anthropic_wire);
  json::Value anthropic_storage{json::Null{}};
  const auto &anthropic_body = BodyObject(anthropic_wire->body, anthropic_storage);
  const auto *anthropic_messages =
      json::AsArray(json::Find(anthropic_body, "messages"));
  assert(anthropic_messages != nullptr && !anthropic_messages->empty());
  const auto *first = json::AsObject(&anthropic_messages->front());
  assert(first != nullptr);
  const auto *parts = json::AsArray(json::Find(*first, "content"));
  assert(parts != nullptr && parts->size() == 2U);
  const auto *image_part = json::AsObject(&parts->at(1));
  assert(image_part != nullptr);
  assert(*json::AsString(json::Find(*image_part, "type")) == "image");
  const auto *source = json::AsObject(json::Find(*image_part, "source"));
  assert(source != nullptr);
  assert(*json::AsString(json::Find(*source, "type")) == "base64");
  assert(*json::AsString(json::Find(*source, "media_type")) == "image/jpeg");
  assert(*json::AsString(json::Find(*source, "data")) == "QUJD");

  // Responses: an `input_image` part whose url is the data URL.
  const auto *codex = FindCompletionProtocolCodec(ModelProtocol::codex_responses);
  assert(codex != nullptr);
  const auto codex_wire = codex->encode(with_image(ModelProtocol::codex_responses),
                                        "https://example.test");
  assert(codex_wire);
  json::Value codex_storage{json::Null{}};
  const auto &codex_body = BodyObject(codex_wire->body, codex_storage);
  const auto *codex_input = json::AsArray(json::Find(codex_body, "input"));
  assert(codex_input != nullptr && !codex_input->empty());
  const auto *codex_message = json::AsObject(&codex_input->front());
  assert(codex_message != nullptr);
  const auto *codex_parts = json::AsArray(json::Find(*codex_message, "content"));
  assert(codex_parts != nullptr && codex_parts->size() == 2U);
  const auto *codex_image = json::AsObject(&codex_parts->at(1));
  assert(codex_image != nullptr);
  assert(*json::AsString(json::Find(*codex_image, "type")) == "input_image");
  assert(*json::AsString(json::Find(*codex_image, "image_url")) ==
         "data:image/jpeg;base64,QUJD");
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
  assert(json_text.find("\"type\":\"image_url\"") != std::string::npos);
  assert(json_text.find("\"url\":\"data:image/jpeg;base64,QUJD\"") !=
         std::string::npos);
  assert(json_text.find("\"type\":\"text\",\"text\":\"look\"") !=
         std::string::npos);

  // Without an image the legacy string content is unchanged.
  auto plain = Request(ModelProtocol::openai_compatible, false);
  const auto plain_text = EncodeOpenAiChatRequest(plain);
  assert(plain_text.find("image_url") == std::string::npos);
  assert(plain_text.find("\"content\":\"question\"") != std::string::npos);
}

} // namespace

int main() {
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
  UnsupportedProtocolsHaveNoRegisteredCodec();
}
