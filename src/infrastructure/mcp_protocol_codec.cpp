#include "infrastructure/mcp_protocol_codec.h"

#include <cctype>
#include <sstream>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

using archive_json::Array;
using archive_json::Null;
using archive_json::Object;
using archive_json::Value;

constexpr std::string_view kProtocolVersion = "2025-03-26";

[[nodiscard]] std::string Trim(std::string_view value) {
  std::size_t first{};
  while (first < value.size() &&
         std::isspace(static_cast<unsigned char>(value[first])) != 0)
    ++first;
  std::size_t last = value.size();
  while (last > first &&
         std::isspace(static_cast<unsigned char>(value[last - 1])) != 0)
    --last;
  return std::string{value.substr(first, last - first)};
}

[[nodiscard]] std::string ExtractEventData(std::string_view response) {
  std::istringstream lines{std::string{response}};
  std::string line;
  std::string data;
  while (std::getline(lines, line)) {
    if (!line.starts_with("data:"))
      continue;
    auto value = Trim(std::string_view{line}.substr(5));
    if (!value.empty() && value != "[DONE]")
      data += value;
  }
  return data.empty() ? std::string{response} : data;
}

[[nodiscard]] std::string Summarize(const Value *value) {
  if (!value || std::holds_alternative<Null>(*value))
    return {};
  if (const auto *text = archive_json::AsString(value))
    return *text;
  if (const auto *object = archive_json::AsObject(value)) {
    constexpr std::string_view keys[]{"text", "content", "message"};
    for (const auto key : keys) {
      if (const auto *text =
              archive_json::AsString(archive_json::Find(*object, key));
          text && !text->empty()) {
        return *text;
      }
    }
  }
  return archive_json::Serialize(*value);
}

} // namespace

std::string BuildMcpInitializeRequest(std::string_view id) {
  Object client_info{{"name", "linecode"}, {"version", "1.0"}};
  Object params{{"capabilities", Object{}},
                {"clientInfo", std::move(client_info)},
                {"protocolVersion", std::string{kProtocolVersion}}};
  return archive_json::Serialize(Value{Object{{"id", std::string{id}},
                                               {"jsonrpc", "2.0"},
                                               {"method", "initialize"},
                                               {"params", std::move(params)}}});
}

std::expected<std::string, std::string>
BuildMcpCallRequest(std::string_view id, std::string_view tool_name,
                    std::string_view arguments_json) {
  const auto arguments_text = arguments_json.empty() ? "{}" : arguments_json;
  auto arguments = archive_json::Parse(arguments_text);
  if (!arguments || !archive_json::AsObject(&*arguments)) {
    return std::unexpected("MCP tool arguments must be a JSON object");
  }
  Object params{{"arguments", std::move(*arguments)},
                {"name", std::string{tool_name}}};
  return archive_json::Serialize(Value{Object{{"id", std::string{id}},
                                               {"jsonrpc", "2.0"},
                                               {"method", "tools/call"},
                                               {"params", std::move(params)}}});
}

DecodedMcpCallResult DecodeMcpCallResponse(std::string_view response) {
  const auto json = ExtractEventData(response);
  auto parsed = archive_json::Parse(json);
  const auto *root = parsed ? archive_json::AsObject(&*parsed) : nullptr;
  if (!root)
    return {.content = std::string{response}, .error = false};

  if (const auto *error = archive_json::Find(*root, "error");
      error && !std::holds_alternative<Null>(*error)) {
    return {.content = Summarize(error), .error = true};
  }
  const auto *result = archive_json::Find(*root, "result");
  return {.content = Summarize(result ? result : &*parsed), .error = false};
}

} // namespace linecode::infrastructure
