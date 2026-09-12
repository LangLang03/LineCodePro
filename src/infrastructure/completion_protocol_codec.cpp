#include "infrastructure/completion_protocol_codec.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <charconv>
#include <cstdint>
#include <functional>
#include <string>
#include <utility>

#include "domain/chat_image.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/openai_chat_codec.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
using application::CompletionMessage;
using application::CompletionRequest;
using application::CompletionResponse;
using application::CompletionRole;

json::Value ToolSchema(std::string_view schema_json) {
  auto parsed = json::Parse(schema_json);
  return parsed && json::AsObject(&*parsed) ? std::move(*parsed)
                                            : json::Value{json::Object{}};
}

json::Array AnthropicTools(const CompletionRequest &request) {
  json::Array tools;
  tools.reserve(request.tools.size());
  for (const auto &tool : request.tools) {
    tools.emplace_back(
        json::Object{{"description", tool.description},
                     {"input_schema", ToolSchema(tool.parameters_json)},
                     {"name", tool.name}});
  }
  return tools;
}

json::Array CodexTools(const CompletionRequest &request) {
  json::Array tools;
  tools.reserve(request.tools.size());
  for (const auto &tool : request.tools) {
    tools.emplace_back(
        json::Object{{"description", tool.description},
                     {"name", tool.name},
                     {"parameters", ToolSchema(tool.parameters_json)},
                     {"type", "function"}});
  }
  return tools;
}

CompletionProtocolCodecError Error(std::string message) {
  return CompletionProtocolCodecError{std::move(message)};
}

const json::Object *
ParseObject(std::string_view source,
            std::expected<json::Value, json::Error> &value) {
  value = json::Parse(source);
  return value ? json::AsObject(&*value) : nullptr;
}

std::int64_t Integer(const json::Value *value) {
  return value == nullptr ? 0
         : std::get_if<std::int64_t>(value) != nullptr
             ? *std::get_if<std::int64_t>(value)
             : 0;
}

std::string_view Text(const json::Value *value) {
  const auto *text = json::AsString(value);
  return text == nullptr ? std::string_view{} : std::string_view{*text};
}

std::expected<const json::Object *, CompletionProtocolCodecError>
RootObject(std::string_view source,
           std::expected<json::Value, json::Error> &storage,
           std::string_view protocol) {
  const auto *root = ParseObject(source, storage);
  if (!storage) {
    return std::unexpected(
        Error(std::string{protocol} +
              " JSON parse failed: " + storage.error().message));
  }
  if (root == nullptr) {
    return std::unexpected(
        Error(std::string{protocol} + " response must be a JSON object"));
  }
  if (const auto *api_error = json::Find(*root, "error");
      api_error != nullptr && !std::holds_alternative<json::Null>(*api_error)) {
    if (const auto *object = json::AsObject(api_error); object != nullptr) {
      const auto message = Text(json::Find(*object, "message"));
      if (!message.empty()) {
        return std::unexpected(
            Error(std::string{protocol} + " error: " + std::string{message}));
      }
    }
    return std::unexpected(Error(std::string{protocol} + " returned an error"));
  }
  return root;
}

json::Array ChatMessages(const CompletionRequest &request) {
  json::Array messages;
  messages.reserve(request.messages.size());
  for (const CompletionMessage &message : request.messages) {
    if (message.role == CompletionRole::system)
      continue;
    if (message.role == CompletionRole::tool && message.tool_result) {
      messages.emplace_back(json::Object{
          {"content", json::Array{json::Object{
                          {"content", message.tool_result->content},
                          {"is_error", message.tool_result->error},
                          {"tool_use_id", message.tool_result->call_id},
                          {"type", "tool_result"}}}},
          {"role", "user"},
      });
      continue;
    }
    json::Array content;
    if (!message.content.empty())
      content.emplace_back(
          json::Object{{"text", message.content}, {"type", "text"}});
    if (message.image.has_value() && !message.image->Empty() &&
        message.role == CompletionRole::user) {
      // Legacy shape reused from the image understanding path
      // (`image_understanding_codec.cpp:152-155`).
      content.emplace_back(json::Object{
          {"source", json::Object{{"data", message.image->base64},
                                   {"media_type", message.image->mime_type},
                                   {"type", "base64"}}},
          {"type", "image"},
      });
    }
    if (message.role == CompletionRole::assistant) {
      for (const auto &call : message.tool_calls) {
        content.emplace_back(json::Object{
            {"id", call.id},
            {"input", ToolSchema(call.arguments_json)},
            {"name", call.name},
            {"type", "tool_use"},
        });
      }
    }
    messages.emplace_back(json::Object{
        {"content", std::move(content)},
        {"role",
         message.role == CompletionRole::assistant ? "assistant" : "user"},
    });
  }
  return messages;
}

json::Array ResponsesInput(const CompletionRequest &request) {
  json::Array input;
  input.reserve(request.messages.size());
  for (const CompletionMessage &message : request.messages) {
    if (message.role == CompletionRole::system)
      continue;
    if (message.role == CompletionRole::tool && message.tool_result) {
      input.emplace_back(json::Object{
          {"call_id", message.tool_result->call_id},
          {"output", message.tool_result->content},
          {"type", "function_call_output"},
      });
      continue;
    }
    const bool assistant = message.role == CompletionRole::assistant;
    const bool has_image = message.image.has_value() &&
                           !message.image->Empty() && !assistant;
    if (!message.content.empty() || has_image) {
      json::Array parts;
      if (!message.content.empty()) {
        parts.emplace_back(json::Object{
            {"text", message.content},
            {"type", assistant ? "output_text" : "input_text"},
        });
      }
      if (has_image) {
        // Same shape as the image understanding path
        // (`image_understanding_codec.cpp:124`).
        parts.emplace_back(json::Object{
            {"image_url", domain::ChatImageDataUrl(*message.image)},
            {"type", "input_image"},
        });
      }
      input.emplace_back(json::Object{
          {"content", std::move(parts)},
          {"role", assistant ? "assistant" : "user"},
          {"type", "message"},
      });
    }
    if (assistant) {
      for (const auto &call : message.tool_calls) {
        input.emplace_back(json::Object{{"arguments", call.arguments_json},
                                        {"call_id", call.id},
                                        {"name", call.name},
                                        {"type", "function_call"}});
      }
    }
  }
  return input;
}

std::string SystemInstructions(const CompletionRequest &request) {
  std::string instructions;
  for (const auto &message : request.messages) {
    if (message.role != CompletionRole::system || message.content.empty())
      continue;
    if (!instructions.empty())
      instructions += "\n\n";
    instructions += message.content;
  }
  return instructions;
}

bool ReasoningEnabled(domain::ReasoningEffort effort) {
  return effort != domain::ReasoningEffort::off;
}

std::string_view ConcreteEffort(domain::ReasoningEffort effort) {
  using enum domain::ReasoningEffort;
  switch (effort) {
  case off:
    return "off";
  case automatic:
    return "medium";
  case low:
    return "low";
  case medium:
    return "medium";
  case high:
    return "high";
  case maximum:
    return "max";
  }
  std::unreachable();
}

std::int64_t ThinkingBudget(domain::ReasoningEffort effort) {
  using enum domain::ReasoningEffort;
  switch (effort) {
  case low:
    return 1024;
  case high:
    return 8192;
  case maximum:
    return 16000;
  default:
    return 4096;
  }
}

std::string TrimTrailingSlashes(std::string_view source) {
  while (!source.empty() && source.back() == '/') {
    source.remove_suffix(1);
  }
  return std::string{source};
}

std::string ReplaceEndpoint(std::string_view base, std::string_view old_suffix,
                            std::string_view new_suffix) {
  std::string normalized = TrimTrailingSlashes(base);
  if (normalized.ends_with(old_suffix)) {
    normalized.resize(normalized.size() - old_suffix.size());
    normalized += new_suffix;
    return normalized;
  }
  if (!normalized.ends_with(new_suffix)) {
    normalized += new_suffix;
  }
  return normalized;
}

std::string AnthropicEndpoint(std::string_view base) {
  std::string normalized = TrimTrailingSlashes(base);
  constexpr std::string_view chat_suffix = "/chat/completions";
  if (normalized.ends_with(chat_suffix)) {
    normalized.resize(normalized.size() - chat_suffix.size());
  }
  if (normalized.ends_with("/v1/messages") ||
      normalized.ends_with("/messages")) {
    return normalized;
  }
  normalized += normalized.ends_with("/v1") ? "/messages" : "/v1/messages";
  return normalized;
}

std::uint32_t JavaStringHash(std::string_view text) {
  std::uint32_t hash{};
  for (const unsigned char character : text) {
    hash = hash * 31U + character;
  }
  return hash;
}

std::string LowerHex(std::uint32_t value) {
  std::array<char, 8> buffer{};
  const auto converted =
      std::to_chars(buffer.data(), buffer.data() + buffer.size(), value, 16);
  return std::string{buffer.data(), converted.ptr};
}

std::expected<CompletionProtocolWireRequest, CompletionProtocolCodecError>
EncodeOpenAi(const CompletionRequest &request, std::string_view base_url) {
  CompletionProtocolWireRequest encoded{
      .endpoint = OpenAiChatEndpoint(base_url),
      .headers = {},
      .body = EncodeOpenAiChatRequest(request),
  };
  if (!request.model.api_key.empty()) {
    encoded.headers.emplace_back("Authorization",
                                 "Bearer " + request.model.api_key);
  }
  return encoded;
}

std::expected<CompletionResponse, CompletionProtocolCodecError>
DecodeOpenAi(std::string_view body) {
  auto response = DecodeOpenAiChatResponse(body);
  if (!response) {
    return std::unexpected(Error(response.error().message));
  }
  return std::move(*response);
}

std::expected<CompletionProtocolStreamChunk, CompletionProtocolCodecError>
DecodeOpenAiEvent(std::string_view data) {
  auto decoded = DecodeOpenAiChatStreamEvent(data);
  if (!decoded) {
    return std::unexpected(Error(decoded.error().message));
  }
  CompletionProtocolStreamChunk chunk{
      .done = decoded->done,
      .text_delta = std::move(decoded->text_delta),
      .final_text = std::nullopt,
      .reasoning_deltas = {},
      .final_reasoning = std::nullopt,
      .tool_call_deltas = {},
      .final_tool_calls = {},
      .input_tokens = 0,
      .output_tokens = 0,
  };
  if (decoded->reasoning_delta && !decoded->reasoning_delta->empty()) {
    chunk.reasoning_deltas.push_back(application::CompletionReasoningDelta{
        .turn_index = 0,
        .text = std::move(*decoded->reasoning_delta),
        .kind = application::CompletionReasoningKind::thinking,
    });
  }
  chunk.tool_call_deltas.reserve(decoded->tool_call_deltas.size());
  for (auto &delta : decoded->tool_call_deltas) {
    chunk.tool_call_deltas.push_back({
        .index = delta.index,
        .id = std::move(delta.id),
        .name = std::move(delta.name),
        .arguments_delta = std::move(delta.arguments_delta),
    });
  }
  return chunk;
}

std::expected<CompletionProtocolWireRequest, CompletionProtocolCodecError>
EncodeAnthropic(const CompletionRequest &request, std::string_view base_url) {
  const bool thinking = ReasoningEnabled(request.reasoning_effort);
  const auto thinking_budget = ThinkingBudget(request.reasoning_effort);
  json::Object body{
      {"max_tokens", thinking
                         ? std::max<std::int64_t>(4096, thinking_budget + 1024)
                         : std::int64_t{4096}},
      {"messages", ChatMessages(request)},
      {"model", request.model.model_id},
      {"stream", request.stream},
  };
  if (const auto system = SystemInstructions(request); !system.empty())
    body.emplace("system", system);
  if (!request.tools.empty())
    body.emplace("tools", AnthropicTools(request));
  if (thinking) {
    body.emplace("thinking", json::Object{{"budget_tokens", thinking_budget},
                                          {"type", "enabled"}});
  }
  // Legacy Anthropic does not transmit preserved assistant reasoning; the API
  // has no compatible replay field for it.
  CompletionProtocolWireRequest encoded{
      .endpoint = AnthropicEndpoint(base_url),
      .headers = {{"anthropic-version", "2023-06-01"}},
      .body = json::Serialize(json::Value{std::move(body)}),
  };
  if (!request.model.api_key.empty()) {
    encoded.headers.emplace_back("x-api-key", request.model.api_key);
  }
  return encoded;
}

std::expected<CompletionResponse, CompletionProtocolCodecError>
DecodeAnthropic(std::string_view body) {
  std::expected<json::Value, json::Error> parsed =
      std::unexpected(json::Error{});
  auto root = RootObject(body, parsed, "Anthropic");
  if (!root) {
    return std::unexpected(root.error());
  }
  const auto *content = json::AsArray(json::Find(**root, "content"));
  if (content == nullptr) {
    return std::unexpected(Error("Anthropic response has no content array"));
  }
  CompletionResponse response;
  for (const auto &block_value : *content) {
    const auto *block = json::AsObject(&block_value);
    if (block != nullptr && Text(json::Find(*block, "type")) == "text") {
      response.text += Text(json::Find(*block, "text"));
    } else if (block != nullptr &&
               Text(json::Find(*block, "type")) == "thinking") {
      response.reasoning_content += Text(json::Find(*block, "thinking"));
    } else if (block != nullptr &&
               Text(json::Find(*block, "type")) == "redacted_thinking") {
      response.reasoning_content += "[redacted thinking]";
    } else if (block != nullptr &&
               Text(json::Find(*block, "type")) == "tool_use") {
      const auto id = Text(json::Find(*block, "id"));
      const auto name = Text(json::Find(*block, "name"));
      const auto *input = json::Find(*block, "input");
      if (!id.empty() && !name.empty()) {
        response.tool_calls.push_back(application::CompletionToolCall{
            .id = std::string{id},
            .name = std::string{name},
            .arguments_json = input ? json::Serialize(*input) : "{}",
        });
      }
    }
  }
  if (const auto *usage = json::AsObject(json::Find(**root, "usage"));
      usage != nullptr) {
    response.input_tokens = Integer(json::Find(*usage, "input_tokens"));
    response.output_tokens = Integer(json::Find(*usage, "output_tokens"));
  }
  return response;
}

std::expected<CompletionProtocolStreamChunk, CompletionProtocolCodecError>
DecodeAnthropicEvent(std::string_view data) {
  std::expected<json::Value, json::Error> parsed =
      std::unexpected(json::Error{});
  auto root = RootObject(data, parsed, "Anthropic stream");
  if (!root) {
    return std::unexpected(root.error());
  }
  CompletionProtocolStreamChunk chunk;
  const auto type = Text(json::Find(**root, "type"));
  if (type == "message_stop") {
    chunk.done = true;
  } else if (type == "content_block_start") {
    const auto index = static_cast<std::size_t>(
        std::max<std::int64_t>(0, Integer(json::Find(**root, "index"))));
    const auto *block = json::AsObject(json::Find(**root, "content_block"));
    if (block && Text(json::Find(*block, "type")) == "tool_use") {
      const auto id = Text(json::Find(*block, "id"));
      const auto name = Text(json::Find(*block, "name"));
      chunk.tool_call_deltas.push_back({
          .index = index,
          .id = id.empty() ? std::nullopt : std::optional<std::string>{id},
          .name =
              name.empty() ? std::nullopt : std::optional<std::string>{name},
          .arguments_delta = {},
      });
    } else if (block &&
               Text(json::Find(*block, "type")) == "redacted_thinking") {
      chunk.reasoning_deltas.push_back({
          .text = "[redacted thinking]",
          .kind = application::CompletionReasoningKind::thinking,
      });
    }
  } else if (type == "content_block_delta") {
    const auto *delta = json::AsObject(json::Find(**root, "delta"));
    if (delta != nullptr && Text(json::Find(*delta, "type")) == "text_delta") {
      const auto text = Text(json::Find(*delta, "text"));
      if (!text.empty()) {
        chunk.text_delta = std::string{text};
      }
    } else if (delta != nullptr &&
               Text(json::Find(*delta, "type")) == "thinking_delta") {
      const auto text = Text(json::Find(*delta, "thinking"));
      if (!text.empty()) {
        chunk.reasoning_deltas.push_back({
            .text = std::string{text},
            .kind = application::CompletionReasoningKind::thinking,
        });
      }
    } else if (delta != nullptr &&
               Text(json::Find(*delta, "type")) == "input_json_delta") {
      chunk.tool_call_deltas.push_back({
          .index = static_cast<std::size_t>(
              std::max<std::int64_t>(0, Integer(json::Find(**root, "index")))),
          .id = std::nullopt,
          .name = std::nullopt,
          .arguments_delta =
              std::string{Text(json::Find(*delta, "partial_json"))},
      });
    }
  } else if (type == "message_start") {
    const auto *message = json::AsObject(json::Find(**root, "message"));
    const auto *usage = message == nullptr
                            ? nullptr
                            : json::AsObject(json::Find(*message, "usage"));
    if (usage != nullptr) {
      chunk.input_tokens = Integer(json::Find(*usage, "input_tokens"));
    }
  } else if (type == "message_delta") {
    const auto *usage = json::AsObject(json::Find(**root, "usage"));
    if (usage != nullptr) {
      chunk.output_tokens = Integer(json::Find(*usage, "output_tokens"));
    }
  }
  return chunk;
}

std::string CodexOutputText(const json::Object &root) {
  std::string result{Text(json::Find(root, "output_text"))};
  const auto *output = json::AsArray(json::Find(root, "output"));
  if (output == nullptr) {
    return result;
  }
  for (const auto &item_value : *output) {
    const auto *item = json::AsObject(&item_value);
    const auto *content =
        item == nullptr ? nullptr : json::AsArray(json::Find(*item, "content"));
    if (content == nullptr) {
      continue;
    }
    for (const auto &part_value : *content) {
      const auto *part = json::AsObject(&part_value);
      if (part == nullptr) {
        continue;
      }
      const auto type = Text(json::Find(*part, "type"));
      if (type == "output_text" || type == "text") {
        const auto text = Text(json::Find(*part, "text"));
        if (!text.empty() && result.find(text) == std::string::npos) {
          result += text;
        }
      }
    }
  }
  return result;
}

std::string CodexReasoningText(const json::Object &root) {
  std::string result;
  const auto *output = json::AsArray(json::Find(root, "output"));
  if (output == nullptr)
    return result;
  for (const auto &item_value : *output) {
    const auto *item = json::AsObject(&item_value);
    if (item == nullptr || Text(json::Find(*item, "type")) != "reasoning")
      continue;
    const auto *summary = json::AsArray(json::Find(*item, "summary"));
    if (summary != nullptr) {
      for (const auto &part_value : *summary) {
        const auto *part = json::AsObject(&part_value);
        std::string segment{part == nullptr ? Text(&part_value)
                                            : Text(json::Find(*part, "text"))};
        const auto first = segment.find_first_not_of(" \t\r\n");
        if (first == std::string::npos)
          continue;
        segment.erase(0, first);
        while (!result.empty() &&
               std::isspace(static_cast<unsigned char>(result.back())))
          result.pop_back();
        if (!result.empty())
          result += " | ";
        result += segment;
      }
    }
    if (result.empty())
      result += Text(json::Find(*item, "content"));
  }
  return result;
}

std::vector<application::CompletionToolCall>
CodexToolCalls(const json::Object &root) {
  std::vector<application::CompletionToolCall> calls;
  const auto *output = json::AsArray(json::Find(root, "output"));
  if (!output)
    return calls;
  for (const auto &item_value : *output) {
    const auto *item = json::AsObject(&item_value);
    if (!item || Text(json::Find(*item, "type")) != "function_call")
      continue;
    const auto id = Text(json::Find(*item, "call_id"));
    const auto name = Text(json::Find(*item, "name"));
    const auto arguments = Text(json::Find(*item, "arguments"));
    if (!id.empty() && !name.empty()) {
      calls.push_back({.id = std::string{id},
                       .name = std::string{name},
                       .arguments_json = arguments.empty()
                                             ? std::string{"{}"}
                                             : std::string{arguments}});
    }
  }
  return calls;
}

std::expected<CompletionProtocolWireRequest, CompletionProtocolCodecError>
EncodeCodex(const CompletionRequest &request, std::string_view base_url) {
  constexpr std::string_view installation =
      "21effb47-cc47-3fbd-a17c-31b0d3a0675e";
  json::Object body{
      {"client_metadata",
       json::Object{{"x-codex-installation-id", std::string{installation}},
                    {"x-codex-window-id", std::string{installation} + ":0"}}},
      {"include", json::Array{}},
      {"input", ResponsesInput(request)},
      {"model", request.model.model_id},
      {"parallel_tool_calls", true},
      {"prompt_cache_key",
       "linecode-codex-" + LowerHex(JavaStringHash(request.model.model_id))},
      {"store", false},
      {"stream", request.stream},
      {"tool_choice", "auto"},
      {"tools", CodexTools(request)},
  };
  if (const auto instructions = SystemInstructions(request);
      !instructions.empty())
    body.emplace("instructions", instructions);
  if (ReasoningEnabled(request.reasoning_effort)) {
    const auto effort =
        request.reasoning_effort == domain::ReasoningEffort::maximum
            ? std::string_view{"high"}
            : ConcreteEffort(request.reasoning_effort);
    body.insert_or_assign(
        "reasoning",
        json::Object{{"effort", std::string{effort}}, {"summary", "auto"}});
    body.insert_or_assign(
        "include", json::Array{std::string{"reasoning.encrypted_content"}});
  }
  // Codex Responses preserves reasoning through encrypted response items, not
  // through a chat-style reasoning_content message field.
  CompletionProtocolWireRequest encoded{
      .endpoint = ReplaceEndpoint(base_url, "/chat/completions", "/responses"),
      .headers = {{"version", "0.120.0"},
                  {"originator", "codex_cli_rs"},
                  {"User-Agent", "codex_cli_rs/0.120.0 (Android; LineCode)"}},
      .body = json::Serialize(json::Value{std::move(body)}),
  };
  if (!request.model.api_key.empty()) {
    encoded.headers.emplace_back("Authorization",
                                 "Bearer " + request.model.api_key);
  }
  return encoded;
}

std::expected<CompletionResponse, CompletionProtocolCodecError>
DecodeCodex(std::string_view body) {
  std::expected<json::Value, json::Error> parsed =
      std::unexpected(json::Error{});
  auto root = RootObject(body, parsed, "Codex");
  if (!root) {
    return std::unexpected(root.error());
  }
  CompletionResponse response{.text = CodexOutputText(**root),
                              .reasoning_content = CodexReasoningText(**root),
                              .tool_calls = CodexToolCalls(**root)};
  if (const auto *usage = json::AsObject(json::Find(**root, "usage"));
      usage != nullptr) {
    response.input_tokens = Integer(json::Find(*usage, "input_tokens"));
    response.output_tokens = Integer(json::Find(*usage, "output_tokens"));
  }
  if (response.text.empty() && response.tool_calls.empty()) {
    return std::unexpected(Error("Codex response has no output text"));
  }
  return response;
}

std::expected<CompletionProtocolStreamChunk, CompletionProtocolCodecError>
DecodeCodexEvent(std::string_view data) {
  std::expected<json::Value, json::Error> parsed =
      std::unexpected(json::Error{});
  auto root = RootObject(data, parsed, "Codex stream");
  if (!root) {
    return std::unexpected(root.error());
  }
  CompletionProtocolStreamChunk chunk;
  const auto type = Text(json::Find(**root, "type"));
  if (type == "response.output_text.delta") {
    const auto delta = Text(json::Find(**root, "delta"));
    if (!delta.empty()) {
      chunk.text_delta = std::string{delta};
    }
  } else if (type == "response.output_text.done") {
    const auto final_text = Text(json::Find(**root, "text"));
    if (!final_text.empty()) {
      chunk.final_text = std::string{final_text};
    }
  } else if (type == "response.reasoning_summary_part.added") {
    chunk.reasoning_deltas.push_back({
        .text = {},
        .kind = application::CompletionReasoningKind::summary,
        .starts_new_segment = true,
    });
  } else if (type == "response.reasoning_summary_text.delta") {
    const auto delta = Text(json::Find(**root, "delta"));
    if (!delta.empty()) {
      chunk.reasoning_deltas.push_back({
          .text = std::string{delta},
          .kind = application::CompletionReasoningKind::summary,
      });
    }
  } else if (type == "response.reasoning_text.delta") {
    const auto delta = Text(json::Find(**root, "delta"));
    if (!delta.empty()) {
      chunk.reasoning_deltas.push_back({
          .text = std::string{delta},
          .kind = application::CompletionReasoningKind::thinking,
      });
    }
  } else if (type == "response.output_item.added") {
    const auto *item = json::AsObject(json::Find(**root, "item"));
    if (item && Text(json::Find(*item, "type")) == "function_call") {
      const auto id = Text(json::Find(*item, "call_id"));
      const auto name = Text(json::Find(*item, "name"));
      chunk.tool_call_deltas.push_back({
          .index = static_cast<std::size_t>(std::max<std::int64_t>(
              0, Integer(json::Find(**root, "output_index")))),
          .id = id.empty() ? std::nullopt : std::optional<std::string>{id},
          .name =
              name.empty() ? std::nullopt : std::optional<std::string>{name},
          .arguments_delta = {},
      });
    }
  } else if (type == "response.function_call_arguments.delta") {
    chunk.tool_call_deltas.push_back({
        .index = static_cast<std::size_t>(std::max<std::int64_t>(
            0, Integer(json::Find(**root, "output_index")))),
        .id = std::nullopt,
        .name = std::nullopt,
        .arguments_delta = std::string{Text(json::Find(**root, "delta"))},
    });
  } else if (type == "response.completed") {
    chunk.done = true;
    const auto *response = json::AsObject(json::Find(**root, "response"));
    if (response != nullptr) {
      const auto final_text = CodexOutputText(*response);
      if (!final_text.empty()) {
        chunk.final_text = final_text;
      }
      if (const auto final_reasoning = CodexReasoningText(*response);
          !final_reasoning.empty()) {
        chunk.final_reasoning = final_reasoning;
      }
      chunk.final_tool_calls = CodexToolCalls(*response);
      if (const auto *usage = json::AsObject(json::Find(*response, "usage"));
          usage != nullptr) {
        chunk.input_tokens = Integer(json::Find(*usage, "input_tokens"));
        chunk.output_tokens = Integer(json::Find(*usage, "output_tokens"));
      }
    }
  } else if (type == "response.failed" || type == "response.incomplete") {
    return std::unexpected(
        Error("Codex stream terminated with event " + std::string{type}));
  }
  return chunk;
}

constexpr std::array kCodecs{
    CompletionProtocolCodec{domain::ModelProtocol::openai_compatible,
                            &EncodeOpenAi, &DecodeOpenAi, &DecodeOpenAiEvent},
    CompletionProtocolCodec{domain::ModelProtocol::codex_responses,
                            &EncodeCodex, &DecodeCodex, &DecodeCodexEvent},
    CompletionProtocolCodec{domain::ModelProtocol::anthropic_messages,
                            &EncodeAnthropic, &DecodeAnthropic,
                            &DecodeAnthropicEvent},
};

} // namespace

const CompletionProtocolCodec *
FindCompletionProtocolCodec(domain::ModelProtocol protocol) noexcept {
  const auto found =
      std::ranges::find(kCodecs, protocol, &CompletionProtocolCodec::protocol);
  return found == kCodecs.end() ? nullptr : &*found;
}

} // namespace linecode::infrastructure
