#include "application/agent_extension_draft.h"

#include <algorithm>
#include <cctype>
#include <format>
#include <functional>
#include <ranges>
#include <stdexcept>
#include <unordered_set>
#include <utility>

namespace linecode::application {
namespace {

constexpr std::string_view kSystemPrompt =
    "You are LineCode's custom Agent configuration generator.\n"
    "Generate an Agent configuration draft from the user's description. "
    "Return only one JSON object; do not use Markdown and do not explain.\n"
    "The JSON fields must be: name, slug, prompt, trigger, toolNames, "
    "mcpIds.\n"
    "name should be a short Chinese name. slug uses lowercase English, "
    "digits, - or _, and starts with a lowercase letter.\n"
    "prompt is a directly usable Chinese Agent system prompt containing the "
    "role, task boundaries, workflow, output requirements and safety "
    "constraints.\n"
    "trigger describes in Chinese when this Agent should run.\n"
    "toolNames and mcpIds may contain only IDs present in the supplied "
    "catalogs.";

std::string Trim(std::string value) {
  const auto first = std::ranges::find_if_not(
      value, [](const unsigned char byte) { return std::isspace(byte) != 0; });
  const auto last = std::ranges::find_if_not(
                        value | std::views::reverse,
                        [](const unsigned char byte) {
                          return std::isspace(byte) != 0;
                        })
                        .base();
  return first < last ? std::string(first, last) : std::string{};
}

AgentDraftError Error(AgentDraftErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

template <class Range, class Projection>
std::vector<std::string> Allowed(const std::vector<std::string> &requested,
                                 const Range &catalog,
                                 Projection projection) {
  std::unordered_set<std::string_view> allowed;
  for (const auto &entry : catalog)
    allowed.emplace(std::invoke(projection, entry));
  std::vector<std::string> output;
  output.reserve(requested.size());
  for (const auto &value : requested) {
    if (allowed.contains(value) &&
        std::ranges::find(output, value) == output.end())
      output.push_back(value);
  }
  return output;
}

} // namespace

CompletionAgentExtensionDraftGenerator::CompletionAgentExtensionDraftGenerator(
    std::shared_ptr<ModelStore> models,
    std::shared_ptr<CompletionGateway> completion,
    std::shared_ptr<ToolRegistry> tools,
    std::shared_ptr<McpExtensionStore> mcps,
    std::shared_ptr<const AgentDraftCodec> codec)
    : models_(std::move(models)), completion_(std::move(completion)),
      tools_(std::move(tools)), mcps_(std::move(mcps)),
      codec_(std::move(codec)) {
  if (!models_ || !completion_ || !tools_ || !mcps_ || !codec_)
    throw std::invalid_argument(
        "CompletionAgentExtensionDraftGenerator requires all dependencies");
}

huxerui::Task<AgentDraftResult<AgentDraftContext>>
CompletionAgentExtensionDraftGenerator::LoadContext() {
  auto refreshed = co_await tools_->Refresh();
  if (!refreshed) {
    co_return std::unexpected(Error(AgentDraftErrorCode::catalog_load,
                                    refreshed.error().message));
  }
  auto stored_mcps = co_await mcps_->ListMcps();
  if (!stored_mcps) {
    co_return std::unexpected(Error(AgentDraftErrorCode::catalog_load,
                                    stored_mcps.error().message));
  }

  AgentDraftContext context;
  for (const auto &tool : tools_->Tools()) {
    if (!tool.agent_selectable)
      continue;
    context.tools.push_back({.name = tool.name,
                             .category = tool.category,
                             .description = tool.description,
                             .selected_by_default =
                                 tool.agent_selected_by_default});
  }
  for (const auto &mcp : *stored_mcps) {
    if (!mcp.enabled)
      continue;
    const auto enabled = std::ranges::count(
        mcp.tools, true, &domain::McpToolSummary::enabled);
    context.mcps.push_back(
        {.id = "custom:" + mcp.id,
         .name = mcp.name,
         .description = std::format("{}/{} tools · {}", enabled,
                                    mcp.tools.size(), mcp.url)});
  }
  co_return context;
}

huxerui::Task<AgentDraftResult<domain::AgentExtension>>
CompletionAgentExtensionDraftGenerator::Generate(std::string description) {
  description = Trim(std::move(description));
  if (description.empty()) {
    co_return std::unexpected(Error(AgentDraftErrorCode::empty_description,
                                    "Please describe the Agent requirements first"));
  }
  auto context = co_await LoadContext();
  if (!context)
    co_return std::unexpected(std::move(context.error()));

  auto selected_id = co_await models_->SelectedId();
  if (!selected_id) {
    co_return std::unexpected(Error(AgentDraftErrorCode::missing_model,
                                    selected_id.error().message));
  }
  if (selected_id->empty()) {
    co_return std::unexpected(Error(
        AgentDraftErrorCode::missing_model,
        "No model is selected. Add and select a model in Settings first."));
  }
  auto model = co_await models_->Find(*selected_id);
  if (!model) {
    co_return std::unexpected(
        Error(AgentDraftErrorCode::missing_model, model.error().message));
  }
  if (!*model) {
    co_return std::unexpected(Error(
        AgentDraftErrorCode::missing_model,
        "The selected model no longer exists. Select another model."));
  }

  CompletionRequest request{
      .model = std::move(**model),
      .messages =
          {
              {.role = CompletionRole::system, .content = std::string{kSystemPrompt}},
              {.role = CompletionRole::user,
               .content = codec_->EncodeContext(description, *context)},
          },
      .tools = {},
      .reasoning_effort = domain::ReasoningEffort::medium,
      .preserve_reasoning = false,
      .stream = false,
      .permission_scope = {},
  };
  auto completed = co_await completion_->Complete(std::move(request), {});
  if (!completed) {
    co_return std::unexpected(Error(AgentDraftErrorCode::completion,
                                    completed.error().message));
  }
  auto decoded = codec_->Decode(completed->text);
  if (!decoded)
    co_return std::unexpected(std::move(decoded.error()));

  decoded->name = Trim(std::move(decoded->name));
  decoded->prompt = Trim(std::move(decoded->prompt));
  decoded->trigger = Trim(std::move(decoded->trigger));
  decoded->slug = domain::NormalizeAgentEditorSlug(
      decoded->slug.empty() ? decoded->name : decoded->slug);
  if (decoded->slug.empty())
    decoded->slug = "custom-agent";
  if (decoded->name.empty() || decoded->slug.empty() ||
      decoded->prompt.empty()) {
    co_return std::unexpected(Error(
        AgentDraftErrorCode::missing_fields,
        "AI returned config missing name, slug, or prompt."));
  }

  auto tools = Allowed(decoded->tool_names, context->tools,
                       &AgentToolOption::name);
  if (tools.empty()) {
    for (const auto &tool : context->tools) {
      if (tool.selected_by_default)
        tools.push_back(tool.name);
    }
    if (tools.empty() && !context->tools.empty())
      tools.push_back(context->tools.front().name);
  }
  auto mcps = Allowed(decoded->mcp_ids, context->mcps, &AgentMcpOption::id);
  co_return domain::AgentExtension{
      .id = {},
      .enabled = true,
      .name = std::move(decoded->name),
      .slug = std::move(decoded->slug),
      .prompt = std::move(decoded->prompt),
      .trigger = std::move(decoded->trigger),
      .tool_names = std::move(tools),
      .mcp_ids = std::move(mcps),
      .created_at = 0,
      .updated_at = 0,
  };
}

} // namespace linecode::application
