#include "application/agent_tool_registry.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <expected>
#include <ranges>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_set>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// AgentTool.getDescription() (lines 29-31), verbatim.
constexpr std::string_view kAgentDescription =
    "Dispatch a sub-Agent to handle a task. explore is read-only; sub-coding "
    "must have a clear and unique write scope. "
    "Returns a compact ref with agent_id (not full transcript). Use "
    "agent_output(agent_id) to fetch full output when needed. "
    "Optional async=true returns immediately for explore agents.";

// AgentPipelineTool.getDescription() (line 29), verbatim.
constexpr std::string_view kAgentPipelineDescription =
    "Create a pipeline of Agent tasks with dependencies. sub-coding must "
    "declare a unique write_scope; multiple Agents cannot write the same file "
    "or overlapping directories.";

// AgentOutputTool.getDescription() (lines 25-28), verbatim.
constexpr std::string_view kAgentOutputDescription =
    "Fetch a previously started agent result by agent_id. "
    "The agent / agent_pipeline tools return a compact ref with agent_id; "
    "call this tool when you need the full output (or while async agents are "
    "still running). "
    "Do not invent agent_id values — only use ids returned by agent tools.";

// AgentTool.getParameters() (lines 58-84): name/type/properties/required and
// every description string, serialized in the canonical sorted-key form
// produced by archive_json::Serialize for the other native registries.
constexpr std::string_view kAgentSchema =
    R"({"properties":{"async":{"description":"If true, return immediately with agent_id (explore only; default false). Fetch full output later via agent_output.","type":"boolean"},"description":{"description":"Task title of 3-8 words","type":"string"},"prompt":{"description":"Detailed task assigned to the Agent, including scope, constraints, and acceptance criteria. Must state that unauthorized files must not be modified; if out-of-scope files must be modified, stop and report.","type":"string"},"read_scope":{"description":"List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task","items":{"type":"string"},"type":"array"},"type":{"description":"Agent type: explore for read-only exploration, sub-coding for programming subtasks","enum":["explore","sub-coding"],"type":"string"},"write_scope":{"description":"Unique list of files or directories sub-coding is allowed to write; explore must be empty. Do not assign the same file to multiple Agents","items":{"type":"string"},"type":"array"}},"required":["type","description","prompt"],"type":"object"})";

// AgentPipelineTool.getParameters() (lines 43-82), including the nested agent
// item schema, verbatim.
constexpr std::string_view kAgentPipelineSchema =
    R"({"properties":{"agents":{"description":"List of Agent tasks","items":{"properties":{"depends_on":{"description":"List of dependent Agent IDs","items":{"type":"string"},"type":"array"},"description":{"description":"Short task title","type":"string"},"id":{"description":"Unique identifier within the pipeline","type":"string"},"prompt":{"description":"Detailed task description. Must state the task boundaries, acceptance criteria, and that files outside write_scope must not be modified","type":"string"},"read_scope":{"description":"List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task","items":{"type":"string"},"type":"array"},"type":{"description":"Agent type","enum":["explore","sub-coding"],"type":"string"},"write_scope":{"description":"Unique list of files or directories sub-coding is allowed to write; explore must be empty. Multiple Agents' write_scope must not be identical, nor contain or be contained by one another","items":{"type":"string"},"type":"array"}},"required":["id","type","description","prompt"],"type":"object"},"type":"array"}},"required":["agents"],"type":"object"})";

// AgentOutputTool.getParameters() (lines 74-86), verbatim.
constexpr std::string_view kAgentOutputSchema =
    R"({"properties":{"agent_id":{"description":"agent_id from a prior agent / agent_pipeline tool result","type":"string"},"include":{"description":"output (default): full body when done; meta: status fields only","enum":["output","meta"],"type":"string"}},"required":["agent_id"],"type":"object"})";

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

// Legacy `BaseTool.ok(message)` / `error(message)`: the failure is a normal tool
// result flagged as an error, not a registry-level failure.
ToolInvocationResult ToolFailure(std::string content) {
  return {.content = std::move(content), .error = true};
}

ToolInvocationResult ToolFinished(std::string content, bool error) {
  return {.content = std::move(content), .error = error};
}

// Java `String.trim()`: strips every leading and trailing character <= U+0020.
std::string Trim(std::string_view text) {
  const auto visible = [](char character) {
    return static_cast<unsigned char>(character) > 0x20U;
  };
  const auto begin = std::ranges::find_if(text, visible);
  const auto end =
      std::ranges::find_if(text | std::views::reverse, visible).base();
  return begin < end ? std::string{begin, end} : std::string{};
}

// org.json `Object.toString()`: a string yields its text, JSON null yields
// nothing, and every other value yields its JSON text. The legacy tools relied
// on that coercion because `JSONObject.optString` accepts non-string values.
std::string OptString(const json::Value *value) {
  if (value == nullptr)
    return {};
  if (const auto *text = json::AsString(value))
    return *text;
  if (std::holds_alternative<json::Null>(*value))
    return {};
  return json::Serialize(*value);
}

std::string OptString(const json::Object &object, std::string_view key) {
  return OptString(json::Find(object, key));
}

// Legacy `AgentTool.hasScope`: true when at least one element trims to a
// non-empty value. A missing or non-array value has no scope.
bool HasScope(const json::Array *array) {
  if (array == nullptr)
    return false;
  return std::ranges::any_of(*array, [](const json::Value &value) {
    return !Trim(OptString(&value)).empty();
  });
}

// Legacy `AgentPipelineTool.scopeList` / `dependencyList`: trimmed, non-empty
// string elements, in input order.
std::vector<std::string> ScopeList(const json::Array *array) {
  std::vector<std::string> values;
  if (array == nullptr)
    return values;
  values.reserve(array->size());
  for (const auto &value : *array) {
    auto text = Trim(OptString(&value));
    if (!text.empty())
      values.push_back(std::move(text));
  }
  return values;
}

// Legacy `AgentPipelineTool.WriteScopeOwner`: the normalized scope used for
// comparison plus the trimmed original quoted back in the overlap message.
struct WriteScopeOwner final {
  std::string agent_id;
  std::string scope;
  std::string original_scope;
};

template <class Argument>
std::string TextArgument(const Argument &argument) {
  if constexpr (std::is_convertible_v<const Argument &, std::string_view>) {
    return std::string{std::string_view{argument}};
  } else {
    return std::to_string(argument);
  }
}

// Resolves a migrated legacy string resource. Arguments are supplied in the
// same order the old getString(...) call used, which is also the {0}/{1}
// placeholder order of the application-owned catalog.
template <class... Arguments>
std::string Text(ToolTextLanguage language, ToolTextKey key,
                 const Arguments &...arguments) {
  const std::array<std::string, sizeof...(Arguments)> values{
      TextArgument(arguments)...};
  return ToolText(key, std::span<const std::string>{values.data(),
                                                   values.size()},
                  language);
}

// Everything the agent tools need beyond their arguments: the result store the
// `agent_output` tool reads and the engine the two dispatching tools call.
struct AgentToolContext final {
  AgentResultRegistry *results{};
  AgentRunner *runner{};
  ToolTextLanguage language{ToolTextLanguage::english};
};

using AgentToolExecutor = huxerui::Task<
    std::expected<ToolInvocationResult, ToolRegistryError>> (*)(
    AgentToolContext context, std::string arguments_json);

// Declarative tool catalog: Invoke() dispatches through this table and Refresh
// projects it into the catalog, so a new agent tool never edits dispatch code.
struct AgentToolDescriptor final {
  std::string_view name;
  std::string_view description;
  std::string_view parameters_json;
  bool allowed_in_read_only;
  bool permanent_grant_supported;
  std::string_view category;
  AgentToolExecutor execute;
};

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgent(AgentToolContext context, std::string arguments_json);

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgentPipeline(AgentToolContext context, std::string arguments_json);

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgentOutput(AgentToolContext context, std::string arguments_json);

// Every row overrides BaseTool.isAllowedInReadonlyMode() with `true`
// (AgentTool.java:22-25, AgentPipelineTool.java:22-25,
// AgentOutputTool.java:41-44), and none of them derives a stable narrow action
// key, so a permanent grant stays unsupported.
constexpr std::array<AgentToolDescriptor, 3> kAgentTools{{
    {
        .name = kAgentToolName,
        .description = kAgentDescription,
        .parameters_json = kAgentSchema,
        .allowed_in_read_only = true,
        .permanent_grant_supported = false,
        .category = kAgentToolGroupId,
        .execute = &ExecuteAgent,
    },
    {
        .name = kAgentPipelineToolName,
        .description = kAgentPipelineDescription,
        .parameters_json = kAgentPipelineSchema,
        .allowed_in_read_only = true,
        .permanent_grant_supported = false,
        .category = kAgentToolGroupId,
        .execute = &ExecuteAgentPipeline,
    },
    {
        .name = kAgentOutputToolName,
        .description = kAgentOutputDescription,
        .parameters_json = kAgentOutputSchema,
        .allowed_in_read_only = true,
        .permanent_grant_supported = false,
        .category = kAgentToolGroupId,
        .execute = &ExecuteAgentOutput,
    },
}};

const AgentToolDescriptor *FindDescriptor(std::string_view name) {
  const auto found =
      std::ranges::find(kAgentTools, name, &AgentToolDescriptor::name);
  return found == kAgentTools.end() ? nullptr : &*found;
}

RegisteredTool CatalogEntry(const AgentToolDescriptor &descriptor) {
  return RegisteredTool{
      .name = std::string{descriptor.name},
      .description = std::string{descriptor.description},
      .parameters_json = std::string{descriptor.parameters_json},
      .allowed_in_read_only = descriptor.allowed_in_read_only,
      .permanent_grant_supported = descriptor.permanent_grant_supported,
      .category = std::string{descriptor.category},
  };
}

bool AgentGroupEnabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, kAgentToolGroupId,
      [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

bool Exposed(const std::vector<RegisteredTool> &tools, std::string_view name) {
  return std::ranges::any_of(tools, [name](const RegisteredTool &tool) {
    return tool.name == name;
  });
}

// Legacy `PipelineDependencyResolver.parsePipelineAgents` (lines 12-39): one
// malformed element, an empty id or a repeated id discards the whole list.
std::vector<domain::PipelineAgent>
ParsePipelineAgentArray(const json::Array &array) {
  std::vector<domain::PipelineAgent> agents;
  agents.reserve(array.size());
  std::unordered_set<std::string> ids;
  for (const auto &value : array) {
    const auto *object = json::AsObject(&value);
    if (object == nullptr)
      return {};
    auto id = Trim(OptString(*object, "id"));
    if (id.empty() || ids.contains(id))
      return {};
    ids.insert(id);
    agents.push_back(domain::PipelineAgent{
        .id = std::move(id),
        .type = NormalizeAgentType(OptString(*object, "type")),
        .description = Trim(OptString(*object, "description")),
        .prompt = Trim(OptString(*object, "prompt")),
        .read_scope = ScopeList(json::AsArray(json::Find(*object, "read_scope"))),
        .write_scope =
            ScopeList(json::AsArray(json::Find(*object, "write_scope"))),
        .dependencies =
            ScopeList(json::AsArray(json::Find(*object, "depends_on"))),
    });
  }
  return agents;
}

// AgentTool.execute (lines 87-115). The legacy method received an already
// parsed JSONObject, so the only failure the C++ port can report through the
// legacy "parameter parsing failed" resource is an unparseable argument body.
huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgent(AgentToolContext context, std::string arguments_json) {
  auto parsed = json::Parse(arguments_json);
  const auto *input = parsed ? json::AsObject(&*parsed) : nullptr;
  if (input == nullptr) {
    co_return ToolFailure(Text(
        context.language, ToolTextKey::tool_agent_parse_failed,
        parsed ? std::string_view{"arguments must be a JSON object"}
               : std::string_view{parsed.error().message}));
  }
  const auto type = NormalizeAgentType(OptString(*input, "type"));
  const auto description = Trim(OptString(*input, "description"));
  const auto prompt = Trim(OptString(*input, "prompt"));
  // Line 91: the normalized type must be one of the two known values.
  if (type != kAgentTypeExplore && type != kAgentTypeSubCoding) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_invalid_type));
  }
  // Line 94: explore may not declare a non-empty write scope.
  if (type == kAgentTypeExplore &&
      HasScope(json::AsArray(json::Find(*input, "write_scope")))) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_explore_no_write));
  }
  // Line 97: the description must not be blank.
  if (description.empty()) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_description_empty));
  }
  // Line 100: the prompt must not be blank.
  if (prompt.empty()) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_prompt_empty));
  }
  // Line 103: without an engine there is nothing to dispatch to.
  if (context.runner == nullptr) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_runner_not_available));
  }
  const auto *async_value = json::Find(*input, "async");
  const bool async_requested =
      async_value != nullptr && std::holds_alternative<bool>(*async_value) &&
      std::get<bool>(*async_value);
  auto result = co_await context.runner->RunAgent(AgentRunRequest{
      .type = type,
      .agent_id = {},
      .description = description,
      .prompt = prompt,
      .read_scope =
          ScopeList(json::AsArray(json::Find(*input, "read_scope"))),
      .write_scope =
          ScopeList(json::AsArray(json::Find(*input, "write_scope"))),
      .async = async_requested,
      .tool_call_id = {},
  });
  co_return ToolFinished(std::move(result.output), result.error);
}

// AgentPipelineTool.execute (lines 85-153): the check order below is the
// legacy order, so the first offending agent wins and, inside one agent, the
// scope checks run before the description/prompt checks.
huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgentPipeline(AgentToolContext context, std::string arguments_json) {
  auto parsed = json::Parse(arguments_json);
  const auto *input = parsed ? json::AsObject(&*parsed) : nullptr;
  const auto *agents =
      input == nullptr ? nullptr : json::AsArray(json::Find(*input, "agents"));
  // Line 87: a missing, non-array or empty `agents` list.
  if (agents == nullptr || agents->empty()) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_pipeline_agents_empty));
  }
  std::unordered_set<std::string> ids;
  std::vector<WriteScopeOwner> write_scopes;
  for (std::size_t index = 0; index < agents->size(); ++index) {
    const auto *agent = json::AsObject(&(*agents)[index]);
    // Line 94: every element must be an object.
    if (agent == nullptr) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_agent_not_object,
          static_cast<int>(index)));
    }
    auto id = Trim(OptString(*agent, "id"));
    // Line 97: the id must not be blank.
    if (id.empty()) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_agent_id_empty,
          static_cast<int>(index)));
    }
    // Line 101: ids must be unique within the pipeline.
    if (ids.contains(id)) {
      co_return ToolFailure(
          Text(context.language, ToolTextKey::tool_pipeline_agent_id_duplicate,
               id));
    }
    ids.insert(id);
    // Lines 105-112: an agent may not depend on itself. The pipeline-wide
    // unknown-dependency and cycle checks belong to the planner that runs
    // after this contract layer.
    for (const auto &dependency :
         ScopeList(json::AsArray(json::Find(*agent, "depends_on")))) {
      if (dependency == id) {
        co_return ToolFailure(Text(
            context.language, ToolTextKey::tool_pipeline_agent_self_depend, id));
      }
    }
    const auto type = NormalizeAgentType(OptString(*agent, "type"));
    // Line 114: the normalized type must be one of the two known values.
    if (type != kAgentTypeExplore && type != kAgentTypeSubCoding) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_agent_invalid_type,
          static_cast<int>(index)));
    }
    const auto write_scope =
        ScopeList(json::AsArray(json::Find(*agent, "write_scope")));
    // Line 118: explore may not declare a write scope.
    if (type == kAgentTypeExplore && !write_scope.empty()) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_explore_no_write, id));
    }
    // Line 121: sub-coding must declare a write scope.
    if (type == kAgentTypeSubCoding && write_scope.empty()) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_coding_needs_write, id));
    }
    std::unordered_set<std::string> local_scopes;
    for (const auto &scope : write_scope) {
      const auto normalized = NormalizeAgentScope(scope);
      if (normalized.empty())
        continue;
      // Line 130: the same normalized scope twice inside one agent.
      if (local_scopes.contains(normalized)) {
        co_return ToolFailure(Text(
            context.language, ToolTextKey::tool_pipeline_scope_duplicate, id,
            scope));
      }
      local_scopes.insert(normalized);
      // Lines 134-139: no other agent (nor an earlier scope of this one) may
      // own an equal or enclosing scope.
      for (const auto &owner : write_scopes) {
        if (!AgentScopesOverlap(normalized, owner.scope))
          continue;
        co_return ToolFailure(Text(
            context.language, ToolTextKey::tool_pipeline_scope_overlap,
            owner.agent_id, owner.original_scope, id, scope));
      }
      write_scopes.push_back(WriteScopeOwner{.agent_id = id,
                                             .scope = normalized,
                                             .original_scope = scope});
    }
    // Line 142: the description must not be blank.
    if (Trim(OptString(*agent, "description")).empty()) {
      co_return ToolFailure(Text(
          context.language,
          ToolTextKey::tool_pipeline_agent_description_empty,
          static_cast<int>(index)));
    }
    // Line 145: the prompt must not be blank.
    if (Trim(OptString(*agent, "prompt")).empty()) {
      co_return ToolFailure(Text(
          context.language, ToolTextKey::tool_pipeline_agent_prompt_empty,
          static_cast<int>(index)));
    }
  }
  // Line 149: without an engine there is nothing to dispatch to.
  if (context.runner == nullptr) {
    co_return ToolFailure(Text(
        context.language, ToolTextKey::tool_pipeline_runner_not_available));
  }
  auto result = co_await context.runner->RunAgentPipeline(
      AgentPipelineRunRequest{.agents = ParsePipelineAgentArray(*agents),
                              .tool_call_id = {}});
  co_return ToolFinished(std::move(result.output), result.error);
}

// AgentOutputTool.execute (lines 89-124). The store is the registry itself, so
// the legacy "store is not available" branch only fires when the composition
// root handed the tool a null registry.
huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteAgentOutput(AgentToolContext context, std::string arguments_json) {
  auto parsed = json::Parse(arguments_json);
  const auto *input = parsed ? json::AsObject(&*parsed) : nullptr;
  const auto agent_id =
      input == nullptr ? std::string{} : Trim(OptString(*input, "agent_id"));
  // Line 91: agent_id is required and is checked before the store.
  if (agent_id.empty()) {
    co_return ToolFailure(
        Text(context.language, ToolTextKey::tool_agent_output_id_missing));
  }
  // Line 95: the result store must exist.
  if (context.results == nullptr) {
    co_return ToolFailure(Text(context.language,
                               ToolTextKey::tool_agent_output_store_missing));
  }
  // The legacy default of `include` is "output" for both an absent key and an
  // unrecognized value.
  const auto include = input == nullptr ? std::string{}
                                        : OptString(*input, "include");
  auto fetched = context.results->Fetch(agent_id, include, context.language);
  co_return ToolInvocationResult{
      .content = std::move(fetched.content),
      .error = fetched.error,
  };
}

} // namespace

// NormalizeAgentType() is defined inline in the header so the sub-agent
// execution engine can share the single port of AgentTool.normalizeType.

std::string NormalizeAgentScope(std::string_view value) {
  auto text = Trim(value);
  std::ranges::replace(text, '\\', '/');
  while (text.starts_with("./"))
    text.erase(0, 2);
  while (true) {
    const auto separator = text.find("//");
    if (separator == std::string::npos)
      break;
    text.erase(separator, 1);
  }
  while (text.size() > 1 && text.ends_with('/'))
    text.pop_back();
  return text;
}

bool AgentScopesOverlap(std::string_view left, std::string_view right) noexcept {
  if (left.empty() || right.empty())
    return false;
  if (left == "." || right == "." || left == "/" || right == "/")
    return true;
  if (left == right)
    return true;
  const auto contains = [](std::string_view container,
                           std::string_view candidate) {
    return container.size() > candidate.size() &&
           container.starts_with(candidate) && container[candidate.size()] == '/';
  };
  return contains(left, right) || contains(right, left);
}

std::vector<domain::PipelineAgent>
ParsePipelineAgents(std::string_view agents_json) {
  auto parsed = json::Parse(agents_json);
  const auto *array = parsed ? json::AsArray(&*parsed) : nullptr;
  if (array == nullptr)
    return {};
  return ParsePipelineAgentArray(*array);
}

AgentToolRegistry::AgentToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<AgentResultRegistry> results,
    std::shared_ptr<AgentRunner> runner, ToolTextLanguage language)
    : settings_(std::move(settings)), results_(std::move(results)),
      runner_(std::move(runner)), language_(language) {
  if (!settings_) {
    throw std::invalid_argument(
        "AgentToolRegistry requires the MCP execution settings service");
  }
}

void AgentToolRegistry::SetRunner(std::shared_ptr<AgentRunner> runner) {
  runner_ = std::move(runner);
}

void AgentToolRegistry::SetResultRegistry(
    std::shared_ptr<AgentResultRegistry> results) {
  results_ = std::move(results);
}

huxerui::Task<std::expected<void, ToolRegistryError>>
AgentToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::load_failed,
                                    settings.error().message));
  }
  std::vector<RegisteredTool> next_tools;
  if (AgentGroupEnabled(*settings)) {
    next_tools.reserve(kAgentTools.size());
    for (const auto &descriptor : kAgentTools)
      next_tools.push_back(CatalogEntry(descriptor));
  }
  tools_ = std::move(next_tools);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> AgentToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
AgentToolRegistry::Invoke(std::string name, std::string arguments_json) {
  const auto *descriptor = FindDescriptor(name);
  if (descriptor == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown agent tool: " + name));
  }
  if (!Exposed(tools_, descriptor->name)) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "The agent tool group is disabled for the current execution mode"));
  }
  co_return co_await descriptor->execute(
      AgentToolContext{.results = results_.get(),
                       .runner = runner_.get(),
                       .language = language_},
      std::move(arguments_json));
}

} // namespace linecode::application
