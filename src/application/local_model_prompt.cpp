#include "application/local_model_prompt.h"

#include <algorithm>
#include <cstddef>
#include <optional>
#include <string>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

constexpr std::string_view kEnvelopeSchema =
    R"({"tool_calls":[{"name":"<tool name>","arguments":{}}]})";

std::string Trim(std::string_view text) {
  const auto first = text.find_first_not_of(" \t\n\r");
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = text.find_last_not_of(" \t\n\r");
  return std::string{text.substr(first, last - first + 1)};
}

std::string RenderToolCallEnvelope(
    const std::vector<CompletionToolCall> &calls) {
  json::Array rendered;
  rendered.reserve(calls.size());
  for (const auto &call : calls) {
    // A call whose arguments did not parse as JSON is echoed as a string so the
    // model still sees what it asked for instead of an empty object.
    json::Value arguments{std::string{}};
    if (auto parsed = json::Parse(call.arguments_json); parsed) {
      arguments = std::move(*parsed);
    } else {
      arguments = call.arguments_json;
    }
    rendered.push_back(json::Object{
        {"name", call.name},
        {"arguments", std::move(arguments)},
    });
  }
  return json::Serialize(
      json::Object{{"tool_calls", std::move(rendered)}});
}

std::string ToolResultTurn(const CompletionToolResult &result) {
  std::string content = "Tool result for `" + result.name + "`";
  if (result.error) {
    content += " (failed)";
  }
  content += ":\n";
  content += result.content;
  return content;
}

// Byte ranges of balanced `{...}` objects, ignoring braces inside JSON strings.
// A model that wraps its reply in prose or a fenced block still yields exactly
// its object here, and an unbalanced brace never produces a candidate.
std::vector<std::pair<std::size_t, std::size_t>>
BalancedObjectRanges(std::string_view text) {
  std::vector<std::pair<std::size_t, std::size_t>> ranges;
  std::size_t index = 0;
  while (index < text.size()) {
    if (text[index] != '{') {
      ++index;
      continue;
    }
    const auto begin = index;
    std::size_t depth = 0;
    bool in_string = false;
    bool escaped = false;
    bool closed = false;
    for (; index < text.size(); ++index) {
      const char character = text[index];
      if (in_string) {
        if (escaped) {
          escaped = false;
        } else if (character == '\\') {
          escaped = true;
        } else if (character == '"') {
          in_string = false;
        }
        continue;
      }
      if (character == '"') {
        in_string = true;
        continue;
      }
      if (character == '{') {
        ++depth;
        continue;
      }
      if (character == '}') {
        if (--depth == 0) {
          ++index;
          closed = true;
          break;
        }
      }
    }
    if (!closed) {
      break;
    }
    ranges.emplace_back(begin, index);
  }
  return ranges;
}

// A fenced block that contains the match, so removing the envelope also removes
// the fence markers instead of leaving an empty block behind. The text between
// the opening marker and the object is the optional info string (`json`), which
// must not itself contain a brace or another marker.
std::pair<std::size_t, std::size_t>
ExpandToFence(std::string_view text, std::size_t begin, std::size_t end) {
  constexpr std::string_view fence = "```";
  const auto open = text.rfind(fence, begin);
  if (open == std::string_view::npos || open + fence.size() > begin) {
    return {begin, end};
  }
  const auto info = text.substr(open + fence.size(), begin - open - fence.size());
  // The info string is the optional language tag, so it is short and free of
  // whitespace once trimmed. Requiring both keeps a fence from an unrelated
  // earlier block from swallowing the text between it and this object.
  auto trimmed_info = info;
  if (const auto first = trimmed_info.find_first_not_of(" \t\r\n");
      first != std::string_view::npos) {
    const auto last = trimmed_info.find_last_not_of(" \t\r\n");
    trimmed_info = trimmed_info.substr(first, last - first + 1);
  } else {
    trimmed_info = {};
  }
  const bool plain_info = trimmed_info.size() <= 32U &&
                          trimmed_info.find_first_of(" \t\r\n{}`") ==
                              std::string_view::npos;
  if (!plain_info) {
    return {begin, end};
  }
  const auto close = text.find(fence, end);
  if (close == std::string_view::npos) {
    return {begin, end};
  }
  return {open, close + fence.size()};
}

std::optional<std::vector<CompletionToolCall>>
DecodeToolCalls(const json::Value &value) {
  const auto *object = json::AsObject(&value);
  if (object == nullptr) {
    return std::nullopt;
  }
  const auto *calls = json::AsArray(json::Find(*object, "tool_calls"));
  if (calls == nullptr) {
    return std::nullopt;
  }
  // An empty array is still a recognised envelope: the model answered with the
  // envelope shape and chose no tool, so the object is consumed and not echoed
  // back into the conversation as apparent prose.
  if (calls->empty()) {
    return std::vector<CompletionToolCall>{};
  }
  std::vector<CompletionToolCall> decoded;
  decoded.reserve(calls->size());
  for (const auto &entry : *calls) {
    const auto *call_object = json::AsObject(&entry);
    if (call_object == nullptr) {
      return std::nullopt;
    }
    const auto *name = json::AsString(json::Find(*call_object, "name"));
    if (name == nullptr || name->empty()) {
      return std::nullopt;
    }
    std::string arguments = "{}";
    if (const auto *arguments_value = json::Find(*call_object, "arguments");
        arguments_value != nullptr) {
      if (const auto *as_string = json::AsString(arguments_value);
          as_string != nullptr) {
        arguments = *as_string;
      } else {
        arguments = json::Serialize(*arguments_value);
      }
    }
    decoded.push_back(CompletionToolCall{
        .id = "local-call-" + std::to_string(decoded.size() + 1),
        .name = *name,
        .arguments_json = std::move(arguments),
    });
  }
  return decoded;
}

} // namespace

std::string_view LocalToolCallEnvelopeSchema() noexcept {
  return kEnvelopeSchema;
}

std::string BuildLocalToolInstruction(const std::vector<CompletionTool> &tools) {
  if (tools.empty()) {
    return {};
  }
  std::string instruction;
  instruction +=
      "You can call tools. To call one, reply with a single JSON object and "
      "nothing else, in exactly this shape:\n";
  instruction += kEnvelopeSchema;
  instruction +=
      "\nGive `arguments` as an object matching the tool's parameters. Call "
      "one tool at a time and wait for its result. When no tool is needed, "
      "reply in plain text instead.\nAvailable tools:\n";
  for (const auto &tool : tools) {
    instruction += "- ";
    instruction += tool.name;
    if (!tool.description.empty()) {
      instruction += ": ";
      instruction += tool.description;
    }
    if (!tool.parameters_json.empty()) {
      instruction += "\n  parameters: ";
      instruction += tool.parameters_json;
    }
    instruction += "\n";
  }
  return instruction;
}

std::vector<LocalChatTurn>
BuildLocalModelTurns(const CompletionRequest &request) {
  const auto instruction = BuildLocalToolInstruction(request.tools);
  bool instruction_placed = false;

  std::vector<LocalChatTurn> turns;
  turns.reserve(request.messages.size() + 1);
  for (const auto &message : request.messages) {
    switch (message.role) {
    case CompletionRole::system: {
      auto content = message.content;
      if (!instruction.empty() && !instruction_placed) {
        if (!content.empty()) {
          content += "\n\n";
        }
        content += instruction;
        instruction_placed = true;
      }
      turns.push_back(
          LocalChatTurn{.role = "system", .content = std::move(content)});
      break;
    }
    case CompletionRole::user:
      turns.push_back(
          LocalChatTurn{.role = "user", .content = message.content});
      break;
    case CompletionRole::assistant: {
      auto content = message.content;
      if (!message.tool_calls.empty()) {
        if (!content.empty()) {
          content += "\n\n";
        }
        content += RenderToolCallEnvelope(message.tool_calls);
      }
      turns.push_back(
          LocalChatTurn{.role = "assistant", .content = std::move(content)});
      break;
    }
    case CompletionRole::tool:
      // Local templates reliably understand only system, user and assistant, so
      // a tool result is delivered as a user turn that names its call.
      turns.push_back(LocalChatTurn{
          .role = "user",
          .content = message.tool_result ? ToolResultTurn(*message.tool_result)
                                         : std::string{"Tool result: (no output)"},
      });
      break;
    }
  }

  if (!instruction.empty() && !instruction_placed) {
    turns.insert(turns.begin(),
                 LocalChatTurn{.role = "system", .content = instruction});
  }
  return turns;
}

LocalToolExtraction ExtractLocalToolCalls(std::string_view reply) {
  LocalToolExtraction extraction{.text = std::string{reply}, .calls = {}};
  for (const auto &[begin, end] : BalancedObjectRanges(reply)) {
    auto parsed = json::Parse(reply.substr(begin, end - begin));
    if (!parsed) {
      continue;
    }
    auto decoded = DecodeToolCalls(*parsed);
    if (!decoded) {
      continue;
    }
    const auto [remove_begin, remove_end] = ExpandToFence(reply, begin, end);
    extraction.text = Trim(std::string{reply.substr(0, remove_begin)} +
                           std::string{reply.substr(remove_end)});
    extraction.calls = std::move(*decoded);
    return extraction;
  }
  return extraction;
}

} // namespace linecode::application
