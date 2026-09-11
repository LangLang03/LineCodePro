#include "infrastructure/json_agent_extension_draft_codec.h"

#include <string>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
using application::AgentDraftError;
using application::AgentDraftErrorCode;
using application::AgentDraftResult;
using application::DecodedAgentDraft;

std::string Text(const json::Object &object, std::string_view name) {
  const auto *value = json::AsString(json::Find(object, name));
  return value ? *value : std::string{};
}

std::vector<std::string> Strings(const json::Object &object,
                                 std::string_view name) {
  std::vector<std::string> values;
  const auto *array = json::AsArray(json::Find(object, name));
  if (!array)
    return values;
  for (const auto &entry : *array) {
    if (const auto *value = json::AsString(&entry))
      values.push_back(*value);
  }
  return values;
}

std::string_view JsonObjectText(std::string_view response) {
  const auto first = response.find('{');
  const auto last = response.rfind('}');
  return first == std::string_view::npos || last <= first
             ? std::string_view{}
             : response.substr(first, last - first + 1U);
}

} // namespace

std::string JsonAgentExtensionDraftCodec::EncodeContext(
    const std::string_view description,
    const application::AgentDraftContext &context) const {
  json::Array tools;
  tools.reserve(context.tools.size());
  for (const auto &tool : context.tools) {
    tools.emplace_back(json::Object{{"name", tool.name},
                                    {"category", tool.category},
                                    {"description", tool.description}});
  }
  json::Array mcps;
  mcps.reserve(context.mcps.size());
  for (const auto &mcp : context.mcps) {
    mcps.emplace_back(json::Object{{"id", mcp.id},
                                   {"name", mcp.name},
                                   {"description", mcp.description}});
  }
  return json::Serialize(json::Object{{"userNeed", std::string{description}},
                                      {"availableTools", std::move(tools)},
                                      {"availableMcp", std::move(mcps)}});
}

AgentDraftResult<DecodedAgentDraft>
JsonAgentExtensionDraftCodec::Decode(const std::string_view response) const {
  const auto source = JsonObjectText(response);
  if (source.empty()) {
    return std::unexpected(AgentDraftError{
        .code = AgentDraftErrorCode::invalid_json,
        .message = "AI did not return valid JSON."});
  }
  const auto parsed = json::Parse(source);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (!object) {
    return std::unexpected(AgentDraftError{
        .code = AgentDraftErrorCode::invalid_json,
        .message = "AI did not return valid JSON."});
  }
  return DecodedAgentDraft{
      .name = Text(*object, "name"),
      .slug = Text(*object, "slug"),
      .prompt = Text(*object, "prompt"),
      .trigger = Text(*object, "trigger"),
      .tool_names = Strings(*object, "toolNames"),
      .mcp_ids = Strings(*object, "mcpIds"),
  };
}

} // namespace linecode::infrastructure
