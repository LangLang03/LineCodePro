#include "application/sub_agent_runner.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cstdint>
#include <exception>
#include <filesystem>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include <huxerui/task.h>

#include "domain/prompt_renderer.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

using infrastructure::archive_json::Object;
using infrastructure::archive_json::Value;

std::string Trim(std::string_view value) {
  const auto visible = [](const unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

std::int64_t NowMillis() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

// Legacy `AgentResultRegistry.allocateId` renders its two numbers in base 36.
std::string Base36(std::uint64_t value) {
  constexpr std::string_view kDigits = "0123456789abcdefghijklmnopqrstuvwxyz";
  if (value == 0)
    return "0";
  std::string digits;
  while (value > 0) {
    digits.push_back(kDigits[value % 36U]);
    value /= 36U;
  }
  std::ranges::reverse(digits);
  return digits;
}

// Legacy `AgentResultRecord.previewFrom` / `clampPreview` (lines 203-219).
std::string PreviewFrom(std::string_view output) {
  const auto trimmed = Trim(output);
  if (trimmed.size() <= kAgentPreviewMaxCharacters)
    return trimmed;
  return trimmed.substr(0, kAgentPreviewMaxCharacters);
}

// Legacy `PromptTemplateRepository.ID_*` prompt ids.
constexpr std::string_view kAgentSystemPromptTemplateId = "agentSystemPrompt";
constexpr std::string_view kAgentRoleExploreRemoteTemplateId =
    "agentRoleExploreRemote";
constexpr std::string_view kAgentRoleCodingRemoteTemplateId =
    "agentRoleCodingRemote";
constexpr std::string_view kAgentRoleExploreLocalTemplateId =
    "agentRoleExploreLocal";
constexpr std::string_view kAgentRoleCodingLocalTemplateId =
    "agentRoleCodingLocal";

// Registry group ids that carry the legacy per-tool `ToolCategory` split.
constexpr std::string_view kFileOpsGroupId = "file_ops";
constexpr std::string_view kImageGroupId = "image";
constexpr std::string_view kWebSearchGroupId = "web_search";

// Legacy `FileReadTool` / `GlobTool` / `ListDirectoryTool` report READ while
// the other `file_ops` members report WRITE.
constexpr std::array<std::string_view, 3> kReadOnlyFileToolNames{
    "file_read", "glob", "list_dir"};
// Legacy `ImageUnderstandingTool` reports READ, `ImageGenerationTool` GENERATE.
constexpr std::string_view kImageUnderstandingToolName = "image_understanding";

// Maps `RegisteredTool::category` back onto the legacy per-tool category:
//   file_read / glob / list_dir          -> READ
//   file_write / file_edit / file_delete -> WRITE
//   image_understanding                  -> READ
//   image_generation                     -> GENERATE
//   web_search                           -> READ
//   todo / memory / shell / mcp / agent / unknown -> SYSTEM
class GroupAgentToolAccessPolicy final : public AgentToolAccessPolicy {
public:
  [[nodiscard]] AgentToolCategory
  Classify(const RegisteredTool &tool) const override {
    if (tool.category == kFileOpsGroupId) {
      return std::ranges::contains(kReadOnlyFileToolNames,
                                   std::string_view{tool.name})
                 ? AgentToolCategory::read
                 : AgentToolCategory::write;
    }
    if (tool.category == kImageGroupId) {
      return tool.name == kImageUnderstandingToolName
                 ? AgentToolCategory::read
                 : AgentToolCategory::generate;
    }
    if (tool.category == kWebSearchGroupId)
      return AgentToolCategory::read;
    return AgentToolCategory::system;
  }
};

// Legacy `AgentPromptBuilder.ROLE_TEMPLATE_MAP` (lines 21-27) resolved through
// `agentRolePrompt` (lines 83-93): an unknown type falls back to the coding
// role, and SSH/terminal-provider mode selects the remote wording.
std::string_view RoleTemplateId(std::string_view type, bool remote_mode) {
  if (IsExploreAgentType(type))
    return remote_mode ? kAgentRoleExploreRemoteTemplateId
                       : kAgentRoleExploreLocalTemplateId;
  return remote_mode ? kAgentRoleCodingRemoteTemplateId
                     : kAgentRoleCodingLocalTemplateId;
}

// Legacy `AgentPromptBuilder.fallbackRolePrompt` (lines 188-202), used only
// when the template repository has no role template at all.
std::string FallbackRolePrompt(std::string_view type, bool remote_mode) {
  if (remote_mode) {
    return IsExploreAgentType(type)
               ? "You are an exploration Agent in a remote Shell environment. "
                 "You can use shell_execute to execute read-only commands.\n"
                 "Rules:\n- Only read code, do not make any modifications.\n- "
                 "Prefer read-only tools to search and read key files; you can "
                 "use shell_execute to execute read-only commands."
               : "You are a coding Agent in a remote Shell environment (the "
                 "current workspace is on an SSH remote or terminal provider "
                 "container; local file_read / file_write / file_edit / glob / "
                 "list_dir may not be available).\n"
                 "Rules:\n- Most work must be done via shell_execute: read "
                 "with cat/ls/grep, write with sed/awk/python heredoc or tee, "
                 "verify with cat.\n- Only modify files or directories listed "
                 "in write_scope; when there is no write_scope, writing to "
                 "files is prohibited.";
  }
  return IsExploreAgentType(type)
             ? "You are a code exploration Agent. Your task is to quickly "
               "locate and analyze code.\n"
               "Rules:\n- Only read code, do not make any modifications, and "
               "do not call any write tools.\n- Prefer read-only tools to "
               "search and read key files; you can use shell_execute to "
               "execute read-only commands.\n- Provide concise and accurate "
               "answers."
             : "You are a coding Agent. Your task is to complete well-scoped "
               "coding subtasks.\n"
               "Rules:\n- Only modify files or directories listed in "
               "write_scope; when there is no write_scope, writing to files is "
               "prohibited.\n- Prefer file_read / file_write / file_edit / "
               "glob / list_dir to complete work; only use shell_execute when "
               "file tools truly cannot meet the need.";
}

// The migrated C++ permission-mode wording
// (`prompt_request_composer.cpp::PermissionContext`), kept identical so the
// main prompt and the sub-agent prompt describe permissions the same way.
std::string PermissionContext(std::string_view mode) {
  struct Entry final {
    std::string_view mode;
    std::string_view prompt;
  };
  static constexpr std::array entries{
      Entry{"auto", "Permission mode: automatic. Enabled tools execute without "
                    "per-call confirmation. Submit tool calls directly instead "
                    "of asking the user for execution approval."},
      Entry{"confirm", "Permission mode: confirmation. Submit tool calls "
                       "directly; the app will request approval for tools that "
                       "require it before execution."},
      Entry{"readonly", "Permission mode: read-only. Only explicitly read-only "
                        "tools are available; do not request state-changing "
                        "operations."},
  };
  const auto found = std::ranges::find(entries, mode, &Entry::mode);
  return std::string{found == entries.end() ? entries.back().prompt
                                            : found->prompt};
}

// `TOOLS_CONTEXT`: the legacy sub-agent prompt rendered the allowed tools
// through `ToolSettingsRepository.buildToolPrompt` (`ToolPromptRenderer`). The
// C++ port reuses the migrated `ToolContext` wording of
// `prompt_request_composer.cpp` instead of the legacy group-based renderer.
std::string ToolsContext(std::span<const CompletionTool> tools,
                         std::string_view permission_mode) {
  std::string block = "## Available tools";
  if (tools.empty()) {
    block += "\nNo tools are available for this request.";
  } else {
    block += "\nOnly the following injected tools are available:";
    for (const auto &tool : tools) {
      block += "\n- ";
      block += tool.name;
      if (!tool.description.empty()) {
        block += ": ";
        block += tool.description;
      }
    }
  }
  return PermissionContext(permission_mode) + "\n\n" + block;
}

// Legacy `FileToolPathPolicy.resolve`: a relative argument is resolved against
// the workspace, and the result is compared without touching the filesystem.
std::filesystem::path ResolveWorkspacePath(std::string_view workspace_path,
                                          std::string_view path) {
  const std::filesystem::path value{std::string{path}};
  if (value.is_absolute())
    return value.lexically_normal();
  const auto workspace = Trim(workspace_path);
  if (workspace.empty())
    return value.lexically_normal();
  return (std::filesystem::path{workspace} / value).lexically_normal();
}

// Legacy `isInsidePath` (lines 1066-1072): equal paths or a proper descendant.
bool PathInside(const std::filesystem::path &root,
                const std::filesystem::path &target) {
  const auto relative = target.lexically_relative(root);
  if (relative.empty())
    return false;
  if (relative == ".")
    return true;
  return std::ranges::none_of(relative, [](const std::filesystem::path &part) {
    return part == "..";
  });
}

// Legacy `validateAgentWriteScope` read `file_path` out of the tool arguments.
std::optional<std::string> FilePathArgument(std::string_view arguments_json) {
  const auto parsed = infrastructure::archive_json::Parse(arguments_json);
  if (!parsed)
    return std::nullopt;
  const auto *object = infrastructure::archive_json::AsObject(&*parsed);
  if (object == nullptr)
    return std::nullopt;
  const auto *value = infrastructure::archive_json::Find(*object, "file_path");
  const auto *text = value == nullptr
                         ? nullptr
                         : infrastructure::archive_json::AsString(value);
  if (text == nullptr)
    return std::nullopt;
  return Trim(*text);
}

// Decrements the level join counter on every exit path, including the case
// where the owning child task is destroyed before its first resume.
class LevelPendingGuard final {
public:
  explicit LevelPendingGuard(
      std::shared_ptr<std::atomic<std::size_t>> pending) noexcept
      : pending_(std::move(pending)) {}
  ~LevelPendingGuard() {
    pending_->fetch_sub(1, std::memory_order_acq_rel);
  }

  LevelPendingGuard(const LevelPendingGuard &) = delete;
  LevelPendingGuard &operator=(const LevelPendingGuard &) = delete;
  LevelPendingGuard(LevelPendingGuard &&) = delete;
  LevelPendingGuard &operator=(LevelPendingGuard &&) = delete;

private:
  std::shared_ptr<std::atomic<std::size_t>> pending_;
};

std::string_view TemplateText(
    const std::unordered_map<std::string, std::string> &templates,
    std::string_view id, const PromptTemplateRepository &repository) {
  const auto found = templates.find(std::string{id});
  if (found != templates.end())
    return found->second;
  const auto *definition = repository.Find(id);
  return definition == nullptr ? std::string_view{} : definition->default_text;
}

// Legacy `runAgentLoop` timeout message (lines 684-693).
std::string AgentTimeoutMessage(std::string_view last_output) {
  return "Agent 达到总时长预算 " +
         std::to_string(kAgentTotalBudgetMillis / 60000LL) +
         " 分钟，最后输出：\n" + std::string{last_output};
}

} // namespace

bool IsExploreAgentType(std::string_view type) noexcept {
  return type == kAgentExploreType;
}

std::vector<AgentToolCategory> AgentAllowedCategories(std::string_view type) {
  // Legacy lines 858-866.
  if (IsExploreAgentType(type))
    return {AgentToolCategory::read};
  return {AgentToolCategory::read, AgentToolCategory::write};
}

std::shared_ptr<const AgentToolAccessPolicy> DefaultAgentToolAccessPolicy() {
  static const std::shared_ptr<const AgentToolAccessPolicy> policy =
      std::make_shared<const GroupAgentToolAccessPolicy>();
  return policy;
}

std::span<const std::string_view> AgentExcludedToolNames() noexcept {
  // Legacy `getAgentExcludedToolNames` default (lines 841-849).
  static constexpr std::array<std::string_view, 2> names{"agent",
                                                         "agent_pipeline"};
  return names;
}

bool IsAgentToolAllowed(const RegisteredTool &tool, std::string_view type,
                        std::span<const std::string> custom_tool_names,
                        std::span<const std::string> custom_mcp_ids,
                        const AgentToolAccessPolicy &access) {
  // Legacy `isAgentToolAllowed` (lines 812-839). The remote-execution and
  // delete-needs-confirmation shortcuts are still not ported: the port has no
  // execution mode on this path and `RegisteredTool` carries neither a
  // confirmation flag nor a display category.
  if (std::ranges::contains(AgentExcludedToolNames(),
                            std::string_view{tool.name}))
    return false;
  // Legacy line 817: an MCP tool the custom Agent selected is allowed before
  // the custom-name filter, so selecting MCP tools does not exclude them.
  if (!custom_mcp_ids.empty() &&
      std::ranges::contains(custom_mcp_ids, tool.name))
    return true;
  if (!custom_tool_names.empty() &&
      !std::ranges::contains(custom_tool_names, tool.name))
    return false;
  const auto allowed = AgentAllowedCategories(type);
  const auto category = access.Classify(tool);
  // Legacy `isRestrictedToRead` (lines 831-834): explore sees READ tools only,
  // even when a tool opts into read-only mode.
  if (allowed.size() == 1U && allowed.front() == AgentToolCategory::read)
    return category == AgentToolCategory::read;
  // Legacy lines 835-838.
  if (tool.allowed_in_read_only)
    return true;
  return std::ranges::contains(allowed, category);
}

std::string AgentWorkspacePrompt(std::string_view workspace_path,
                                 bool remote_mode) {
  // Legacy `AgentPromptBuilder.agentWorkspacePrompt` (lines 99-105). The port
  // has no `Host`, so an unset workspace falls back to "~" remotely and ".".
  auto path = Trim(workspace_path);
  if (path.empty())
    path = remote_mode ? "~" : ".";
  const std::string mode_hint =
      remote_mode
          ? "\n当前是 SSH 远端或终端提供者模式：所有命令作用于远端主机上的工作区，"
            "文件路径按远端约定解析。"
          : "\n所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 API "
            "key、token、密码等敏感数据。";
  return "当前工作区: " + path + mode_hint;
}

std::string AgentScopePrompt(std::string_view type,
                             const std::vector<std::string> &read_scope,
                             const std::vector<std::string> &write_scope,
                             bool remote_mode) {
  // Legacy `AgentPromptBuilder.agentScopePrompt` (lines 111-127).
  std::string builder = "## Agent 范围\n";
  builder += "read_scope: " + AgentScopeSummary(read_scope) + "\n";
  builder += "write_scope: " + AgentScopeSummary(write_scope) + "\n";
  if (IsExploreAgentType(type)) {
    builder += "这是 explore Agent，write_scope 必须视为无效，禁止任何写入。";
  } else if (write_scope.empty()) {
    builder += "没有授权写入范围。禁止写入文件；如果任务需要修改文件，直接说明需要"
               "主模型重新分配 write_scope。";
  } else {
    builder += "只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把多个 "
               "Agent 的职责混到同一个文件里。";
  }
  if (remote_mode) {
    builder += "\n注意：所有路径都是远端主机上的路径；写入/读取都要通过 "
               "shell_execute 调用 sed/awk/python heredoc/cat 等命令，不要尝试"
               "调用本地 file 类工具。";
  }
  return builder;
}

std::string AgentScopeSummary(std::span<const std::string> scopes) {
  // Legacy `AgentPromptBuilder.scopeSummary` (lines 129-144).
  std::string summary;
  for (const auto &scope : scopes) {
    const auto trimmed = Trim(scope);
    if (trimmed.empty())
      continue;
    if (!summary.empty())
      summary += ", ";
    summary += trimmed;
  }
  return summary.empty() ? "未声明" : summary;
}

void AgentRunResults::Put(std::string agent_id, AgentRunResult result) {
  const auto found = std::ranges::find(
      entries_, agent_id, &std::pair<std::string, AgentRunResult>::first);
  if (found == entries_.end())
    entries_.emplace_back(std::move(agent_id), std::move(result));
  else
    found->second = std::move(result);
}

const AgentRunResult *
AgentRunResults::Find(std::string_view agent_id) const noexcept {
  const auto found = std::ranges::find(
      entries_, agent_id, &std::pair<std::string, AgentRunResult>::first);
  return found == entries_.end() ? nullptr : &found->second;
}

std::string AgentDependencyOutputContext(const domain::PipelineAgent &agent,
                                         const AgentRunResults &results) {
  // Legacy `PipelineDependencyResolver.dependencyOutputContext` (lines 91-106).
  if (agent.dependencies.empty())
    return {};
  std::string builder = "\n\n## 上游 Agent 输出\n";
  for (const auto &dependency : agent.dependencies) {
    const auto *result = results.Find(dependency);
    if (result == nullptr)
      continue;
    builder += "\n### " + dependency + "\n" + result->output + "\n";
  }
  builder += "\n请基于以上结果继续你的任务。";
  return builder;
}

SkillRepositoryExtensionPromptSource::SkillRepositoryExtensionPromptSource(
    std::shared_ptr<SkillRepository> skills)
    : skills_(std::move(skills)) {
  if (!skills_)
    throw std::invalid_argument(
        "SkillRepositoryExtensionPromptSource requires a skill repository");
}

huxerui::Task<SkillResult<std::string>>
SkillRepositoryExtensionPromptSource::BuildExtensionPrompt() const {
  co_return co_await skills_->BuildExtensionPrompt();
}

TaskScopeSubAgentLauncher::TaskScopeSubAgentLauncher(huxerui::TaskScope scope)
    : scope_(std::move(scope)) {}

void TaskScopeSubAgentLauncher::Launch(
    std::function<huxerui::Task<void>()> factory) {
  // Ignoring the returned `TaskHandle` is the documented fire-and-forget
  // shape: the scope still cancels the child when its composition unmounts.
  scope_.Launch(std::move(factory));
}

std::string InMemorySubAgentResultSink::AllocateId() {
  // Legacy `AgentResultRegistry.allocateId()` (lines 17-21).
  const auto sequence = sequence_++;
  return "ag_" + Base36(static_cast<std::uint64_t>(NowMillis())) + "_" +
         Base36(sequence);
}

void InMemorySubAgentResultSink::Record(SubAgentRunRecord record) {
  // Legacy `AgentResultRegistry.put(...)`: an empty id is ignored.
  if (record.agent_id.empty())
    return;
  const auto found = std::ranges::find(records_, record.agent_id,
                                       &SubAgentRunRecord::agent_id);
  if (found == records_.end())
    records_.push_back(std::move(record));
  else
    *found = std::move(record);
}

const SubAgentRunRecord *
InMemorySubAgentResultSink::Find(std::string_view agent_id) const noexcept {
  const auto found =
      std::ranges::find(records_, agent_id, &SubAgentRunRecord::agent_id);
  return found == records_.end() ? nullptr : &*found;
}

std::string InMemorySubAgentResultSink::ToCompactRef(
    const SubAgentRunRecord &record) const {
  // Legacy `AgentResultRegistry.toCompactJson` (lines 112-134).
  Object object;
  object.emplace("linecode_agent_ref", Value{true});
  object.emplace("agent_id", Value{record.agent_id});
  object.emplace("status", Value{record.status});
  object.emplace("type", Value{record.type});
  object.emplace("description", Value{record.description});
  object.emplace("preview", Value{record.preview});
  object.emplace("tool_call_count",
                 Value{static_cast<std::int64_t>(record.tool_call_count)});
  object.emplace("error", Value{record.error});
  object.emplace("async", Value{record.async});
  if (!record.tool_call_id.empty())
    object.emplace("tool_call_id", Value{record.tool_call_id});
  return infrastructure::archive_json::Serialize(Value{std::move(object)});
}

SubAgentRunner::SubAgentRunner(
    std::shared_ptr<CompletionGateway> completion,
    std::shared_ptr<ToolRegistry> tools,
    std::shared_ptr<PromptTemplateRepository> prompt_templates,
    std::shared_ptr<ModelStore> models,
    std::shared_ptr<SubAgentResultSink> results,
    std::shared_ptr<AgentExtensionPromptSource> extensions,
    std::shared_ptr<SubAgentBackgroundLauncher> background,
    SubAgentEnvironment environment,
    std::shared_ptr<const AgentToolAccessPolicy> tool_access)
    : completion_(std::move(completion)), tools_(std::move(tools)),
      prompt_templates_(std::move(prompt_templates)),
      models_(std::move(models)), results_(std::move(results)),
      extensions_(std::move(extensions)), background_(std::move(background)),
      tool_access_(tool_access ? std::move(tool_access)
                               : DefaultAgentToolAccessPolicy()),
      environment_(std::move(environment)) {
  if (!completion_ || !tools_ || !prompt_templates_ || !models_)
    throw std::invalid_argument(
        "SubAgentRunner requires a completion gateway, a tool registry, the "
        "prompt templates and the model store");
}

huxerui::Task<std::optional<domain::ModelConfig>>
SubAgentRunner::ResolveSelectedModel() {
  // The legacy main flow passed its `selectedModel` into the controller; the
  // port reads the same selection from the model store.
  auto selected = co_await models_->SelectedId();
  if (!selected || selected->empty())
    co_return std::optional<domain::ModelConfig>{};
  auto found = co_await models_->Find(*selected);
  if (!found || !*found)
    co_return std::optional<domain::ModelConfig>{};
  co_return std::optional<domain::ModelConfig>{std::move(**found)};
}

huxerui::Task<SubAgentRunner::TemplateMap> SubAgentRunner::LoadTemplates() {
  TemplateMap templates;
  auto loaded = co_await prompt_templates_->Load();
  if (loaded) {
    for (auto &item : *loaded)
      templates.insert_or_assign(std::move(item.definition.id),
                                 std::move(item.current_text));
  }
  // A storage failure is not fatal: `TemplateText` falls back to the built-in
  // defaults the repository always carries.
  co_return templates;
}

huxerui::Task<std::string> SubAgentRunner::BuildSystemPrompt(
    const AgentRunInputs &inputs, std::span<const CompletionTool> tools,
    const TemplateMap &templates) {
  // Legacy `AgentPromptBuilder.agentSystemPrompt` (lines 57-77).
  const auto role_id = RoleTemplateId(inputs.type, environment_.remote_mode);
  auto role_prompt =
      std::string{TemplateText(templates, role_id, *prompt_templates_)};
  if (Trim(role_prompt).empty())
    role_prompt = FallbackRolePrompt(inputs.type, environment_.remote_mode);
  const auto task_description = inputs.description;
  const auto workspace_context =
      AgentWorkspacePrompt(inputs.workspace_path, environment_.remote_mode);
  const auto scope_context =
      AgentScopePrompt(inputs.type, inputs.read_scope, inputs.write_scope,
                       environment_.remote_mode);
  std::string extensions_context;
  if (extensions_) {
    // The only caller of `SkillRepository::BuildExtensionPrompt`, so installed
    // Skills finally reach the model through `EXTENSIONS_CONTEXT`.
    auto built = co_await extensions_->BuildExtensionPrompt();
    if (built)
      extensions_context = Trim(*built);
  }
  const auto tools_context = ToolsContext(tools, environment_.permission_mode);
  const std::array<domain::PromptVariable, 6> variables{{
      {"ROLE_PROMPT", role_prompt},
      {"TASK_DESCRIPTION", task_description},
      {"WORKSPACE_CONTEXT", workspace_context},
      {"SCOPE_CONTEXT", scope_context},
      {"EXTENSIONS_CONTEXT", extensions_context},
      {"TOOLS_CONTEXT", tools_context},
  }};
  co_return domain::RenderPromptTemplate(
      TemplateText(templates, kAgentSystemPromptTemplateId, *prompt_templates_),
      variables);
}

std::optional<std::string> SubAgentRunner::ValidateWriteScope(
    const CompletionToolCall &call, AgentToolCategory category,
    const AgentRunInputs &inputs) const {
  // Legacy `validateAgentWriteScope` (lines 1010-1054).
  if (category != AgentToolCategory::write)
    return std::nullopt;
  if (IsExploreAgentType(inputs.type))
    return "explore Agent 不允许写入文件。";
  if (inputs.write_scope.empty())
    return "Agent 未声明 write_scope，禁止写入文件。请让主模型重新分配明确的写入"
           "范围。";
  const auto file_path = FilePathArgument(call.arguments_json);
  if (!file_path || file_path->empty())
    return std::nullopt;
  const auto target = ResolveWorkspacePath(inputs.workspace_path, *file_path);
  for (const auto &scope : inputs.write_scope) {
    if (Trim(scope).empty())
      continue;
    if (PathInside(ResolveWorkspacePath(inputs.workspace_path, scope), target))
      return std::nullopt;
  }
  return "Agent 写入路径超出 write_scope: " + *file_path +
         "\n允许写入范围: " + AgentScopeSummary(inputs.write_scope) +
         "\n请停止写入并让主模型重新分配。";
}

huxerui::Task<CompletionToolResult> SubAgentRunner::ExecuteToolCall(
    const CompletionToolCall &call, const AgentRunInputs &inputs,
    std::span<const RegisteredTool> tools) {
  // Legacy `executeAgentToolCall` (lines 901-935).
  CompletionToolResult result{.call_id = call.id,
                              .name = call.name,
                              .content = {},
                              .error = false,
                              .diff_id = {}};
  if (call.name.empty()) {
    result.content = "Agent 工具调用为空";
    result.error = true;
    co_return result;
  }
  const auto found = std::ranges::find(tools, call.name, &RegisteredTool::name);
  if (found == tools.end()) {
    // Legacy line 916.
    result.content = "Agent 不允许调用此工具: " + call.name;
    result.error = true;
    co_return result;
  }
  if (const auto scope_error =
          ValidateWriteScope(call, tool_access_->Classify(*found), inputs)) {
    result.content = *scope_error;
    result.error = true;
    co_return result;
  }
  auto invoked = co_await tools_->Invoke(call.name, call.arguments_json);
  if (invoked) {
    result.content = std::move(invoked->content);
    result.error = invoked->error;
    result.diff_id = std::move(invoked->diff_id);
  } else {
    result.content = invoked.error().message;
    result.error = true;
  }
  co_return result;
}

huxerui::Task<AgentRunResult> SubAgentRunner::RunLoop(AgentRunInputs inputs) {
  // Legacy `runAgentLoop` (lines 638-796).
  auto tool_call_count = 0;
  std::string last_output;
  try {
    const auto templates = co_await LoadTemplates();
    auto refreshed = co_await tools_->Refresh();
    if (!refreshed) {
      co_return AgentRunResult{
          .output = "Agent 执行失败：\n" + refreshed.error().message,
          .tool_call_count = tool_call_count,
          .error = true};
    }
    // Legacy `agentTools(type, ...)` + `toolNames(agentTools)`: the visible set
    // is frozen for the whole loop and doubles as the allow list.
    std::vector<RegisteredTool> agent_tools;
    for (const auto &tool : tools_->Tools()) {
      if (IsAgentToolAllowed(tool, inputs.type, inputs.custom_tool_names,
                             inputs.custom_mcp_ids, *tool_access_))
        agent_tools.push_back(tool);
    }
    std::vector<CompletionTool> advertised_tools;
    advertised_tools.reserve(agent_tools.size());
    for (const auto &tool : agent_tools) {
      advertised_tools.push_back({.name = tool.name,
                                  .description = tool.description,
                                  .parameters_json = tool.parameters_json});
    }
    const auto system_prompt =
        co_await BuildSystemPrompt(inputs, advertised_tools, templates);
    std::vector<CompletionMessage> messages;
    messages.push_back(CompletionMessage{.role = CompletionRole::system,
                                         .content = system_prompt,
                                         .reasoning_content = {},
                                         .tool_calls = {},
                                         .tool_result = std::nullopt});
    messages.push_back(CompletionMessage{.role = CompletionRole::user,
                                         .content = inputs.prompt,
                                         .reasoning_content = {},
                                         .tool_calls = {},
                                         .tool_result = std::nullopt});
    const auto started_at = NowMillis();
    for (;;) {
      // Legacy lines 667-674: cancellation wins over the budget check.
      if (stop_requested()) {
        co_return AgentRunResult{
            .output = std::string{kAgentTerminatedMessage},
            .tool_call_count = tool_call_count,
            .error = true};
      }
      if (budget_.Exhausted()) {
        co_return AgentRunResult{
            .output = std::string{kAgentToolLimitMessage} + "\n" + last_output,
            .tool_call_count = tool_call_count,
            .error = true};
      }
      if (NowMillis() - started_at > kAgentTotalBudgetMillis) {
        co_return AgentRunResult{.output = AgentTimeoutMessage(last_output),
                                 .tool_call_count = tool_call_count,
                                 .error = true};
      }
      CompletionRequest request;
      request.model = inputs.model;
      request.messages = messages;
      request.tools = advertised_tools;
      request.reasoning_effort = domain::ReasoningEffort::medium;
      request.preserve_reasoning = false;
      // The sub-agent loop owns no progress observer: streaming deltas only
      // fed the legacy `AgentProgressSession`, which the result registry now
      // covers.
      request.stream = false;
      request.permission_scope = inputs.workspace_path;
      auto response = co_await completion_->Complete(std::move(request),
                                                     CompletionObserver{});
      if (!response) {
        // Legacy lines 780-786.
        co_return AgentRunResult{
            .output = "Agent 模型通信失败：\n" + response.error().message,
            .tool_call_count = tool_call_count,
            .error = true};
      }
      if (stop_requested()) {
        co_return AgentRunResult{
            .output = std::string{kAgentTerminatedMessage},
            .tool_call_count = tool_call_count,
            .error = true};
      }
      const auto output = response->text;
      // Legacy lines 738-744: only non-empty text becomes the final answer.
      if (!Trim(output).empty())
        last_output = output;
      if (response->tool_calls.empty()) {
        const auto trimmed = Trim(last_output);
        co_return AgentRunResult{
            .output = trimmed.empty() ? "Agent 没有返回文本。" : trimmed,
            .tool_call_count = tool_call_count,
            .error = false};
      }
      auto calls = response->tool_calls;
      messages.push_back(CompletionMessage::Assistant(
          output, std::move(response->tool_calls),
          std::move(response->reasoning_content)));
      for (const auto &call : calls) {
        // Legacy lines 748-764: cancel and the shared budget are re-checked
        // before every single tool call of the batch.
        if (stop_requested()) {
          co_return AgentRunResult{
              .output = std::string{kAgentTerminatedMessage},
              .tool_call_count = tool_call_count,
              .error = true};
        }
        if (budget_.Exhausted()) {
          co_return AgentRunResult{
              .output = std::string{kAgentToolLimitMessage} + "\n" + last_output,
              .tool_call_count = tool_call_count,
              .error = true};
        }
        auto tool_result = co_await ExecuteToolCall(call, inputs, agent_tools);
        ++tool_call_count;
        budget_.Accrue(1);
        messages.push_back(CompletionMessage::Tool(std::move(tool_result)));
      }
    }
  } catch (const std::exception &error) {
    // Legacy lines 787-794.
    co_return AgentRunResult{
        .output = "Agent 执行失败：\n" + std::string{error.what()},
        .tool_call_count = tool_call_count,
        .error = true};
  } catch (...) {
    co_return AgentRunResult{.output = "Agent 执行失败：\n",
                             .tool_call_count = tool_call_count,
                             .error = true};
  }
}

AgentRunResult SubAgentRunner::FinishRecord(SubAgentRunRecord record,
                                            AgentRunResult result) {
  // Legacy `finishAgentWithCompact` (lines 274-312): the registry keeps the
  // full output while the model only sees the compact ref.
  record.output = result.output;
  record.preview = PreviewFrom(result.output);
  record.tool_call_count = result.tool_call_count;
  record.error = result.error;
  record.status = result.error ? "error" : "done";
  if (!results_)
    return result;
  results_->Record(record);
  return AgentRunResult{.output = results_->ToCompactRef(record),
                        .tool_call_count = result.tool_call_count,
                        .error = result.error};
}

huxerui::Task<AgentRunResult> SubAgentRunner::RunAgent(AgentRunRequest request) {
  // Legacy `runAgentTool` (lines 183-272).
  const auto type = NormalizeAgentType(request.type);
  if (request.async && !IsExploreAgentType(type)) {
    // Legacy lines 202-208.
    co_return AgentRunResult{
        .output = "async=true is not allowed for sub-coding agents.",
        .tool_call_count = 0,
        .error = true};
  }
  auto model = co_await ResolveSelectedModel();
  if (!model) {
    // Legacy lines 192-194.
    co_return AgentRunResult{.output = "当前没有可用模型，无法运行 Agent。",
                             .tool_call_count = 0,
                             .error = true};
  }
  if (stop_requested()) {
    // Legacy lines 195-197.
    co_return AgentRunResult{.output = std::string{kAgentTerminatedMessage},
                             .tool_call_count = 0,
                             .error = true};
  }
  budget_.SetLimit(model->tool_call_limit);

  SubAgentRunRecord record;
  record.agent_id = results_ ? results_->AllocateId() : std::string{};
  record.tool_call_id = request.tool_call_id;
  record.type = type;
  record.description = request.description;
  record.status = "running";
  record.async = request.async;
  if (results_)
    results_->Record(record);

  AgentRunInputs inputs;
  inputs.type = type;
  inputs.description = request.description;
  inputs.prompt = request.prompt;
  inputs.read_scope = request.read_scope;
  inputs.write_scope = request.write_scope;
  inputs.custom_tool_names = request.custom_tool_names;
  inputs.custom_mcp_ids = request.custom_mcp_ids;
  inputs.workspace_path = environment_.workspace_path;
  inputs.model = *model;

  if (request.async) {
    // Legacy lines 227-254: publish the running compact ref, then keep the
    // loop running in the background.
    const auto compact =
        results_ ? results_->ToCompactRef(record) : std::string{};
    if (background_) {
      background_->Launch([self = shared_from_this(),
                           inputs = std::move(inputs),
                           record]() -> huxerui::Task<void> {
        // The background record is the only observable result of this task.
        static_cast<void>(
            self->FinishRecord(record, co_await self->RunLoop(std::move(inputs))));
        co_return;
      });
    } else {
      // Legacy `Host.runInBackground` default executed the runnable inline.
      static_cast<void>(FinishRecord(record, co_await RunLoop(std::move(inputs))));
    }
    co_return AgentRunResult{
        .output = compact, .tool_call_count = 0, .error = false};
  }
  co_return FinishRecord(record, co_await RunLoop(std::move(inputs)));
}

huxerui::Task<AgentRunResult> SubAgentRunner::RunPipelineAgent(
    const domain::PipelineAgent &agent, const AgentRunResults &completed,
    const domain::ModelConfig &model, std::string_view workspace_path) {
  // Legacy `runOnePipelineAgent` (lines 554-597).
  if (stop_requested()) {
    co_return AgentRunResult{.output = std::string{kAgentTerminatedMessage},
                             .tool_call_count = 0,
                             .error = true};
  }
  AgentRunInputs inputs;
  inputs.type = NormalizeAgentType(agent.type);
  inputs.description = agent.description;
  inputs.prompt = agent.prompt + AgentDependencyOutputContext(agent, completed);
  inputs.read_scope = agent.read_scope;
  inputs.write_scope = agent.write_scope;
  inputs.workspace_path = std::string{workspace_path};
  inputs.model = model;

  SubAgentRunRecord record;
  record.agent_id = agent.id;
  record.type = inputs.type;
  record.description = agent.description;

  auto result = co_await RunLoop(std::move(inputs));
  // The port records every pipeline stage under its own id so `agent_output`
  // can read one stage back without the pipeline summary.
  record.output = result.output;
  record.preview = PreviewFrom(result.output);
  record.tool_call_count = result.tool_call_count;
  record.error = result.error;
  record.status = result.error ? "error" : "done";
  if (results_ && !record.agent_id.empty())
    results_->Record(record);
  co_return result;
}

huxerui::Task<void> SubAgentRunner::RunLevelAgent(
    domain::PipelineAgent agent, domain::ModelConfig model,
    std::string workspace_path, AgentRunResults completed,
    std::shared_ptr<std::vector<std::optional<AgentRunResult>>> slots,
    std::shared_ptr<std::atomic<std::size_t>> pending, std::size_t index) {
  const LevelPendingGuard guard{pending};
  (*slots)[index] =
      co_await RunPipelineAgent(agent, completed, model, workspace_path);
}

huxerui::Task<std::vector<std::pair<domain::PipelineAgent, AgentRunResult>>>
SubAgentRunner::RunLevel(const std::vector<domain::PipelineAgent> &level,
                         const AgentRunResults &completed,
                         const domain::ModelConfig &model,
                         std::string_view workspace_path) {
  std::vector<std::pair<domain::PipelineAgent, AgentRunResult>> outcomes;
  outcomes.reserve(level.size());
  if (level.empty())
    co_return outcomes;
  // Legacy `runPipelineLevelParallel` (lines 460-552) gave every agent of a
  // level its own thread. The SDK has no when_all/join, so a level runs
  // concurrently only when a background launcher is injected: each agent
  // becomes an independent child task and the level joins by polling
  // completion, the only public SDK path that keeps a detached task
  // observable. Without a launcher the level runs sequentially in input order.
  if (!background_ || level.size() == 1U) {
    for (const auto &agent : level) {
      outcomes.emplace_back(
          agent,
          co_await RunPipelineAgent(agent, completed, model, workspace_path));
    }
    co_return outcomes;
  }
  const auto slot_count = level.size();
  auto slots = std::make_shared<std::vector<std::optional<AgentRunResult>>>(
      slot_count);
  auto pending = std::make_shared<std::atomic<std::size_t>>(slot_count);
  for (std::size_t index = 0; index < slot_count; ++index) {
    background_->Launch([self = shared_from_this(), agent = level[index], model,
                         workspace = std::string{workspace_path}, completed,
                         slots, pending,
                         index]() -> huxerui::Task<void> {
      co_await self->RunLevelAgent(agent, model, workspace, completed, slots,
                                   pending, index);
    });
  }
  // Bounded by the legacy per-agent time budget so a lost child can never pin
  // the pipeline forever.
  const auto deadline =
      NowMillis() + kAgentTotalBudgetMillis * static_cast<std::int64_t>(slot_count);
  while (pending->load(std::memory_order_acquire) != 0) {
    if (NowMillis() > deadline)
      break;
    co_await huxerui::Delay(std::chrono::milliseconds{1});
  }
  for (std::size_t index = 0; index < slot_count; ++index) {
    auto &slot = (*slots)[index];
    if (!slot) {
      slot = AgentRunResult{.output = AgentTimeoutMessage({}),
                            .tool_call_count = 0,
                            .error = true};
    }
    outcomes.emplace_back(level[index], std::move(*slot));
  }
  co_return outcomes;
}

huxerui::Task<AgentRunResult>
SubAgentRunner::RunAgentPipeline(AgentPipelineRunRequest request) {
  // Legacy `runAgentPipelineTool` (lines 314-437).
  auto model = co_await ResolveSelectedModel();
  if (!model) {
    co_return AgentRunResult{
        .output = "当前没有可用模型，无法运行 Agent 流水线。",
        .tool_call_count = 0,
        .error = true};
  }
  if (stop_requested()) {
    co_return AgentRunResult{.output = std::string{kAgentTerminatedMessage},
                             .tool_call_count = 0,
                             .error = true};
  }
  const auto plan = domain::PlanPipeline(request.agents);
  if (!plan.ok()) {
    // Legacy lines 329-340 plus `PipelineDependencyResolver`.
    std::string message = "Agent 流水线存在循环依赖或重复 id，无法执行。";
    switch (plan.error.code) {
    case domain::PipelinePlanErrorCode::empty:
      message = "agent_pipeline.agents 不能为空。";
      break;
    case domain::PipelinePlanErrorCode::self_dependency:
      message = "Agent 不能依赖自身: " + plan.error.agent_id;
      break;
    case domain::PipelinePlanErrorCode::unknown_dependency:
    case domain::PipelinePlanErrorCode::cycle:
      break;
    }
    co_return AgentRunResult{
        .output = std::move(message), .tool_call_count = 0, .error = true};
  }
  budget_.SetLimit(model->tool_call_limit);

  const auto tool_call_id = request.tool_call_id;
  const auto agent_count = request.agents.size();
  const auto finish = [this, &tool_call_id, agent_count](
                          std::string summary, int tool_call_count,
                          bool error) {
    // Legacy `terminatePipeline` and the final compact ref (lines 417-458).
    SubAgentRunRecord record;
    record.agent_id = results_ ? results_->AllocateId() : std::string{};
    record.tool_call_id = tool_call_id;
    record.type = "pipeline";
    record.description = std::to_string(agent_count) + " agents";
    record.output = summary;
    record.preview = PreviewFrom(summary);
    record.tool_call_count = tool_call_count;
    record.error = error;
    record.status = error ? "error" : "done";
    if (!results_)
      return AgentRunResult{.output = std::move(summary),
                            .tool_call_count = tool_call_count,
                            .error = error};
    results_->Record(record);
    return AgentRunResult{.output = results_->ToCompactRef(record),
                          .tool_call_count = tool_call_count,
                          .error = error};
  };

  AgentRunResults results;
  std::string summary = "Agent pipeline completed: " +
                        std::to_string(agent_count) + " 个任务";
  auto total_tool_calls = 0;
  bool has_error = false;
  const auto started_at = NowMillis();
  const auto pipeline_budget =
      kAgentTotalBudgetMillis * static_cast<std::int64_t>(agent_count);
  for (const auto &level : plan.levels) {
    if (stop_requested()) {
      // Legacy `terminatePipeline` (lines 439-458).
      co_return finish("Agent 流水线已终止。", total_tool_calls, true);
    }
    if (NowMillis() - started_at > pipeline_budget) {
      co_return finish("Agent 流水线达到总时长预算 " +
                           std::to_string(pipeline_budget / 60000LL) +
                           " 分钟，已强制结束。",
                       total_tool_calls, true);
    }
    auto outcomes =
        co_await RunLevel(level, results, *model, environment_.workspace_path);
    for (auto &[agent, result] : outcomes) {
      results.Put(agent.id, result);
      total_tool_calls += result.tool_call_count;
      has_error = has_error || result.error;
      summary += "\n\n## " + agent.id + " · " + agent.description + '\n' +
                 "类型: " + agent.type + '\n' +
                 "状态: " + (result.error ? "error" : "done") + '\n' +
                 "工具调用: " + std::to_string(result.tool_call_count) + '\n' +
                 result.output;
      if (result.error &&
          result.output.find(kAgentToolLimitMessage) != std::string::npos) {
        // Legacy lines 402-410: the shared main-flow budget ended the pipeline.
        co_return finish(
            "Agent 流水线因工具调用次数达到主流程上限，已提前结束：\n" +
                result.output,
            total_tool_calls, true);
      }
    }
  }
  summary += "\n\n总工具调用: " + std::to_string(total_tool_calls);
  co_return finish(Trim(summary), total_tool_calls, has_error);
}

} // namespace linecode::application
