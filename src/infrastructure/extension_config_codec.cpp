#include "infrastructure/extension_config_codec.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <expected>
#include <sstream>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

using archive_json::Array;
using archive_json::Object;
using archive_json::Value;

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

[[nodiscard]] bool Boolean(const Value *value, bool fallback) noexcept {
  if (const auto *boolean = value ? std::get_if<bool>(value) : nullptr)
    return *boolean;
  if (const auto *integer = value ? std::get_if<std::int64_t>(value) : nullptr)
    return *integer != 0;
  return fallback;
}

[[nodiscard]] std::string Text(const Object &object, std::string_view key) {
  const auto *text = archive_json::AsString(archive_json::Find(object, key));
  return text ? *text : std::string{};
}

[[nodiscard]] std::string Schema(const Object &object) {
  constexpr std::string_view keys[]{"inputSchema", "input_schema", "schema"};
  for (const auto key : keys) {
    const auto *value = archive_json::Find(object, key);
    if (archive_json::AsObject(value))
      return archive_json::Serialize(*value);
  }
  return {};
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

[[nodiscard]] const Array *ToolArray(const Object &root) {
  if (const auto *result =
          archive_json::AsObject(archive_json::Find(root, "result"))) {
    if (const auto *tools =
            archive_json::AsArray(archive_json::Find(*result, "tools")))
      return tools;
    if (const auto *servers =
            archive_json::AsArray(archive_json::Find(*result, "servers")))
      return servers;
  }
  if (const auto *tools =
          archive_json::AsArray(archive_json::Find(root, "tools")))
    return tools;
  if (const auto *data =
          archive_json::AsObject(archive_json::Find(root, "data"))) {
    if (const auto *tools =
            archive_json::AsArray(archive_json::Find(*data, "tools")))
      return tools;
  }
  return archive_json::AsArray(archive_json::Find(root, "servers"));
}

} // namespace

std::string EncodeExtensionStringList(const std::vector<std::string> &values) {
  Array array;
  array.reserve(values.size());
  for (const auto &value : values) {
    auto trimmed = Trim(value);
    if (!trimmed.empty())
      array.emplace_back(std::move(trimmed));
  }
  return archive_json::Serialize(Value{std::move(array)});
}

std::vector<std::string> DecodeExtensionStringList(std::string_view json) {
  auto parsed = archive_json::Parse(json.empty() ? "[]" : json);
  const auto *array = parsed ? archive_json::AsArray(&*parsed) : nullptr;
  if (!array)
    return {};
  std::vector<std::string> values;
  values.reserve(array->size());
  for (const auto &item : *array) {
    const auto *text = archive_json::AsString(&item);
    auto trimmed = text ? Trim(*text) : std::string{};
    if (!trimmed.empty())
      values.push_back(std::move(trimmed));
  }
  return values;
}

std::string
EncodeMcpRequestHeaders(const std::vector<domain::McpRequestHeader> &headers) {
  Array array;
  array.reserve(headers.size());
  for (const auto &header : headers) {
    if (header.name.empty())
      continue;
    array.emplace_back(Object{{"name", header.name}, {"value", header.value}});
  }
  return archive_json::Serialize(Value{std::move(array)});
}

std::vector<domain::McpRequestHeader>
DecodeMcpRequestHeaders(std::string_view json) {
  auto parsed = archive_json::Parse(json.empty() ? "[]" : json);
  const auto *array = parsed ? archive_json::AsArray(&*parsed) : nullptr;
  if (!array)
    return {};
  std::vector<domain::McpRequestHeader> headers;
  headers.reserve(array->size());
  for (const auto &item : *array) {
    const auto *object = archive_json::AsObject(&item);
    auto name = object ? Text(*object, "name") : std::string{};
    if (!Trim(name).empty())
      headers.push_back(
          {.name = std::move(name), .value = Text(*object, "value")});
  }
  return headers;
}

std::string EncodeMcpTools(const std::vector<domain::McpToolSummary> &tools) {
  Array array;
  array.reserve(tools.size());
  for (const auto &tool : tools) {
    if (tool.name.empty())
      continue;
    Object object{{"description", tool.description},
                  {"enabled", tool.enabled},
                  {"name", tool.name}};
    if (!tool.input_schema_json.empty()) {
      auto schema = archive_json::Parse(tool.input_schema_json);
      if (!schema || !archive_json::AsObject(&*schema))
        continue;
      object.emplace("inputSchema", std::move(*schema));
    }
    array.emplace_back(std::move(object));
  }
  return archive_json::Serialize(Value{std::move(array)});
}

std::vector<domain::McpToolSummary> DecodeMcpTools(std::string_view json) {
  auto parsed = archive_json::Parse(json.empty() ? "[]" : json);
  const auto *array = parsed ? archive_json::AsArray(&*parsed) : nullptr;
  if (!array)
    return {};
  std::vector<domain::McpToolSummary> tools;
  tools.reserve(array->size());
  for (const auto &item : *array) {
    if (const auto *name = archive_json::AsString(&item)) {
      tools.push_back({.name = *name,
                       .enabled = true,
                       .description = {},
                       .input_schema_json = {}});
      continue;
    }
    const auto *object = archive_json::AsObject(&item);
    auto name = object ? Trim(Text(*object, "name")) : std::string{};
    if (name.empty())
      continue;
    tools.push_back({
        .name = std::move(name),
        .enabled = Boolean(archive_json::Find(*object, "enabled"), true),
        .description = Text(*object, "description"),
        .input_schema_json = Schema(*object),
    });
  }
  return tools;
}

std::expected<std::vector<domain::McpToolSummary>, std::string>
DecodeMcpToolResponse(std::string_view response) {
  const auto json = ExtractEventData(response);
  auto parsed = archive_json::Parse(json);
  if (!parsed)
    return std::unexpected("Invalid MCP JSON response: " +
                           parsed.error().message);
  const auto *root = archive_json::AsObject(&*parsed);
  if (!root)
    return std::unexpected("MCP response root must be an object");
  const auto *tools = ToolArray(*root);
  if (!tools)
    return std::unexpected("No tools list found in MCP response");
  auto decoded = DecodeMcpTools(archive_json::Serialize(Value{*tools}));
  if (decoded.empty())
    return std::unexpected("No tools list found in MCP response");
  return decoded;
}

} // namespace linecode::infrastructure
