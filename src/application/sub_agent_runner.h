#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <optional>
#include <span>
#include <stop_token>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/agent_runner.h"
#include "application/agent_tool_registry.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/model_store.h"
#include "application/ports/skill_services.h"
#include "application/ports/tool_registry.h"
#include "application/prompt_template_repository.h"
#include "application/skill_repository.h"
#include "domain/agent_pipeline.h"

namespace linecode::application {

// Port of the legacy `AgentExecutionController` execution half: it owns the
// sub-agent model loop (model -> tools -> model), the pipeline level walk and
// the result records. The registry half (`AgentTool`/`AgentPipelineTool`
// validation, argument decoding and the `agent_output` reader) stays outside.
//
// Every legacy behaviour below cites the file and line it was ported from:
// `AgentExecutionController.java`, `AgentPromptBuilder.java` and
// `PipelineDependencyResolver.java` of the Java app.

// Legacy `AgentTool.TYPE_EXPLORE` / `AgentTool.TYPE_SUB_CODING`.
inline constexpr std::string_view kAgentExploreType = "explore";
inline constexpr std::string_view kAgentSubCodingType = "sub-coding";

// Legacy `AgentExecutionController.AGENT_TERMINATED_MESSAGE` (line 55) and
// `AGENT_TOOL_LIMIT_MESSAGE` (line 56).
inline constexpr std::string_view kAgentTerminatedMessage = "Agent 已终止。";
inline constexpr std::string_view kAgentToolLimitMessage =
    "Agent 已达到主流程总工具调用次数上限。";

// Legacy `AGENT_TOTAL_BUDGET_MS` (line 54): one sub-agent may not run longer
// than 30 minutes; the pipeline budgets 30 minutes per pipeline agent.
inline constexpr std::int64_t kAgentTotalBudgetMillis = 30LL * 60LL * 1000LL;

// Legacy `AgentResultRecord.PREVIEW_MAX_CHARS`.
inline constexpr std::size_t kAgentPreviewMaxCharacters = 240;


// Legacy `AgentTool.TYPE_EXPLORE.equals(type)`.
[[nodiscard]] bool IsExploreAgentType(std::string_view type) noexcept;

// The legacy tool taxonomy (`cn.lineai.tool.ToolCategory`) collapsed onto the
// value the agent policy switches on. `RegisteredTool::category` carries the
// C++ registry group id ("file_ops", "todo", "web_search", "memory", "shell",
// "image", "mcp", ...), so a policy maps a group back onto the legacy
// per-tool category.
enum class AgentToolCategory : std::uint8_t {
  read,
  generate,
  write,
  system,
};

// Legacy `getAgentAllowedCategories(type)` (lines 851-866). explore may only
// see READ tools; every other type sees READ + WRITE.
[[nodiscard]] std::vector<AgentToolCategory>
AgentAllowedCategories(std::string_view type);

// Classification boundary for `RegisteredTool` -> legacy `ToolCategory`. The
// registries own their group ids, so the mapping is injected rather than
// hard-coded in the runner.
class AgentToolAccessPolicy {
public:
  virtual ~AgentToolAccessPolicy() = default;

  [[nodiscard]] virtual AgentToolCategory
  Classify(const RegisteredTool &tool) const = 0;
};

// Maps the registry group ids onto the legacy per-tool categories; see the
// implementation for the exact table and its legacy source lines.
[[nodiscard]] std::shared_ptr<const AgentToolAccessPolicy>
DefaultAgentToolAccessPolicy();

// Legacy `getAgentExcludedToolNames` default (lines 841-849): the dispatch
// tools themselves can never run inside a sub-agent.
[[nodiscard]] std::span<const std::string_view>
AgentExcludedToolNames() noexcept;

// Legacy `isAgentToolAllowed` (lines 812-839).
[[nodiscard]] bool
IsAgentToolAllowed(const RegisteredTool &tool, std::string_view type,
                   std::span<const std::string> custom_tool_names,
                   const AgentToolAccessPolicy &access);

// Legacy `AgentPromptBuilder.agentWorkspacePrompt` (lines 99-105).
[[nodiscard]] std::string AgentWorkspacePrompt(std::string_view workspace_path,
                                               bool remote_mode);

// Legacy `AgentPromptBuilder.agentScopePrompt` (lines 111-127).
[[nodiscard]] std::string
AgentScopePrompt(std::string_view type,
                 const std::vector<std::string> &read_scope,
                 const std::vector<std::string> &write_scope, bool remote_mode);

// Legacy `AgentPromptBuilder.scopeSummary` (lines 129-144).
[[nodiscard]] std::string
AgentScopeSummary(std::span<const std::string> scopes);

// Ordered agent_id -> result map, mirroring the legacy `LinkedHashMap` the
// dependency resolver reads.
class AgentRunResults final {
public:
  void Put(std::string agent_id, AgentRunResult result);
  [[nodiscard]] const AgentRunResult *
  Find(std::string_view agent_id) const noexcept;
  [[nodiscard]] bool empty() const noexcept { return entries_.empty(); }
  [[nodiscard]] std::size_t size() const noexcept { return entries_.size(); }
  [[nodiscard]] const std::vector<std::pair<std::string, AgentRunResult>> &
  entries() const noexcept {
    return entries_;
  }

private:
  std::vector<std::pair<std::string, AgentRunResult>> entries_;
};

// Legacy `PipelineDependencyResolver.dependencyOutputContext` (lines 91-106):
// appends every upstream output under "## 上游 Agent 输出" and closes with
// "请基于以上结果继续你的任务。".
[[nodiscard]] std::string
AgentDependencyOutputContext(const domain::PipelineAgent &agent,
                             const AgentRunResults &results);

// Cooperative cancellation for every run of one runner, mirroring the legacy
// `ModelCancellationToken`. The token is standard `std::stop_token`, so a
// caller may also observe it directly.
class AgentCancellation final {
public:
  [[nodiscard]] std::stop_token token() const noexcept {
    return source_.get_token();
  }
  [[nodiscard]] bool stop_requested() const noexcept {
    return source_.stop_requested();
  }
  void RequestStop() noexcept { source_.request_stop(); }

private:
  std::stop_source source_;
};

// Main-flow tool budget, mirroring the legacy `executedAgentToolCalls` counter
// plus the shared `AtomicInteger toolCallBudget` (lines 69-94, 663-683): the
// limit comes from the selected model and the used count accumulates across
// every sub-agent of one generation.
class AgentToolBudget final {
public:
  AgentToolBudget() = default;

  // Legacy `ModelConfig::unlimited_tool_calls` (-1) and any non-positive value
  // mean "no limit", exactly like `toolCallLimit > 0 &&` in the legacy loop.
  void SetLimit(int limit) noexcept {
    limit_.store(limit, std::memory_order_relaxed);
  }
  [[nodiscard]] int limit() const noexcept {
    return limit_.load(std::memory_order_relaxed);
  }
  [[nodiscard]] int used() const noexcept {
    return used_.load(std::memory_order_relaxed);
  }
  [[nodiscard]] bool Exhausted() const noexcept {
    const auto current_limit = limit();
    return current_limit > 0 && used() >= current_limit;
  }
  void Accrue(int count) noexcept {
    if (count > 0)
      used_.fetch_add(count, std::memory_order_relaxed);
  }
  // Legacy `resetExecutedAgentToolCalls()`.
  void Reset() noexcept { used_.store(0, std::memory_order_relaxed); }

private:
  std::atomic<int> limit_{0};
  std::atomic<int> used_{0};
};

// Runtime facts the sub-agent prompt needs and the tool registry does not own:
// the legacy `homePath`, the SSH/terminal-provider mode and the permission
// mode rendered into `TOOLS_CONTEXT`.
struct SubAgentEnvironment final {
  std::string workspace_path;
  bool remote_mode{};
  std::string permission_mode{"auto"};

  bool operator==(const SubAgentEnvironment &) const = default;
};

// Read-side view of the installed-Skill prompt block that fills
// `EXTENSIONS_CONTEXT`. `SkillRepository` already implements the behaviour, so
// this adapter keeps the runner testable without a filesystem.
class AgentExtensionPromptSource {
public:
  virtual ~AgentExtensionPromptSource() = default;

  [[nodiscard]] virtual huxerui::Task<SkillResult<std::string>>
  BuildExtensionPrompt() const = 0;
};

class SkillRepositoryExtensionPromptSource final
    : public AgentExtensionPromptSource {
public:
  explicit SkillRepositoryExtensionPromptSource(
      std::shared_ptr<SkillRepository> skills);

  [[nodiscard]] huxerui::Task<SkillResult<std::string>>
  BuildExtensionPrompt() const override;

private:
  std::shared_ptr<SkillRepository> skills_;
};

// Starts one detached background task, mirroring `Host.runInBackground`
// (line 233). `huxerui::TaskScope` is the only public SDK primitive that owns a
// detached task, so the app injects the adapter below and tests inject a fake.
// `huxerui::Task` has no join/when_all primitive: a caller that needs the
// result polls completion (see the pipeline level join).
class SubAgentBackgroundLauncher {
public:
  virtual ~SubAgentBackgroundLauncher() = default;

  // Takes ownership of `factory` and starts the task it produces. The factory
  // is kept alive for the complete child lifetime, which is why the SDK's
  // coroutine-factory overload is the required shape.
  virtual void Launch(std::function<huxerui::Task<void>()> factory) = 0;
};

class TaskScopeSubAgentLauncher final : public SubAgentBackgroundLauncher {
public:
  explicit TaskScopeSubAgentLauncher(huxerui::TaskScope scope);

  void Launch(std::function<huxerui::Task<void>()> factory) override;

private:
  huxerui::TaskScope scope_;
};

// One recorded sub-agent run, mirroring `AgentResultRecord` minus the UI-only
// fields (thinking text and the progress payload) that the port does not
// produce yet.
struct SubAgentRunRecord final {
  std::string agent_id;
  std::string tool_call_id;
  std::string type;
  std::string description;
  // "running" | "done" | "error", matching the legacy status strings.
  std::string status{"running"};
  std::string preview;
  std::string output;
  int tool_call_count{};
  bool error{};
  bool async{};

  bool operator==(const SubAgentRunRecord &) const = default;
};

// Result-recording boundary. Another agent owns the full
// `AgentResultRegistry` (output/meta modes and the `agent_output` reader); the
// execution engine only needs these three operations, so it depends on this
// narrow port and ships an in-memory implementation for tests and wiring.
class SubAgentResultSink {
public:
  virtual ~SubAgentResultSink() = default;

  // Legacy `AgentResultRegistry.allocateId()`: "ag_<millis base36>_<seq base36>".
  [[nodiscard]] virtual std::string AllocateId() = 0;
  // Upsert by `agent_id`; the newest record wins (legacy `put`).
  virtual void Record(SubAgentRunRecord record) = 0;
  // Legacy `AgentResultRegistry.toCompactJson`.
  [[nodiscard]] virtual std::string
  ToCompactRef(const SubAgentRunRecord &record) const = 0;
};

// Minimal in-memory sink: allocation, ordered storage and the compact ref. It
// exists so the engine is runnable and testable before the database-backed
// registry is wired in; the app replaces it with that registry.
class InMemorySubAgentResultSink final : public SubAgentResultSink {
public:
  [[nodiscard]] std::string AllocateId() override;
  void Record(SubAgentRunRecord record) override;
  [[nodiscard]] std::string
  ToCompactRef(const SubAgentRunRecord &record) const override;

  [[nodiscard]] const SubAgentRunRecord *
  Find(std::string_view agent_id) const noexcept;
  [[nodiscard]] std::size_t size() const noexcept { return records_.size(); }
  [[nodiscard]] const std::vector<SubAgentRunRecord> &records() const noexcept {
    return records_;
  }
  void Clear() noexcept { records_.clear(); }

private:
  std::vector<SubAgentRunRecord> records_;
  std::uint64_t sequence_{1};
};

// Runs sub-agents and agent pipelines on top of the completion gateway and the
// tool registry. All collaborators are injected so the engine stays testable.
//
// The runner must be owned by a `std::shared_ptr` (`enable_shared_from_this`):
// an async explore run and a concurrent pipeline level both detach work that
// has to outlive the call, so they keep the runner alive through a
// self-reference.
class SubAgentRunner final : public AgentRunner,
                             public std::enable_shared_from_this<SubAgentRunner> {
public:
  SubAgentRunner(std::shared_ptr<CompletionGateway> completion,
                 std::shared_ptr<ToolRegistry> tools,
                 std::shared_ptr<PromptTemplateRepository> prompt_templates,
                 std::shared_ptr<ModelStore> models,
                 std::shared_ptr<SubAgentResultSink> results = {},
                 std::shared_ptr<AgentExtensionPromptSource> extensions = {},
                 std::shared_ptr<SubAgentBackgroundLauncher> background = {},
                 SubAgentEnvironment environment = {},
                 std::shared_ptr<const AgentToolAccessPolicy> tool_access = {});

  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunAgent(AgentRunRequest request) override;
  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunAgentPipeline(AgentPipelineRunRequest request) override;

  [[nodiscard]] std::stop_token StopToken() const noexcept {
    return cancellation_.token();
  }
  [[nodiscard]] bool stop_requested() const noexcept {
    return cancellation_.stop_requested();
  }
  // Cancels this runner; a cancelled run returns the legacy
  // `AGENT_TERMINATED_MESSAGE` error result instead of continuing.
  void RequestStop() noexcept { cancellation_.RequestStop(); }

  [[nodiscard]] AgentToolBudget &budget() noexcept { return budget_; }
  [[nodiscard]] const AgentToolBudget &budget() const noexcept {
    return budget_;
  }
  [[nodiscard]] const SubAgentEnvironment &environment() const noexcept {
    return environment_;
  }

private:
  // Everything one sub-agent loop needs after the request was normalized.
  struct AgentRunInputs final {
    std::string type;
    std::string description;
    std::string prompt;
    std::vector<std::string> read_scope;
    std::vector<std::string> write_scope;
    std::string workspace_path;
    domain::ModelConfig model;
  };

  using TemplateMap = std::unordered_map<std::string, std::string>;

  [[nodiscard]] huxerui::Task<std::optional<domain::ModelConfig>>
  ResolveSelectedModel();
  [[nodiscard]] huxerui::Task<TemplateMap> LoadTemplates();
  [[nodiscard]] huxerui::Task<std::string>
  BuildSystemPrompt(const AgentRunInputs &inputs,
                    std::span<const CompletionTool> tools,
                    const TemplateMap &templates);
  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunLoop(AgentRunInputs inputs);
  [[nodiscard]] huxerui::Task<CompletionToolResult>
  ExecuteToolCall(const CompletionToolCall &call, const AgentRunInputs &inputs,
                  std::span<const RegisteredTool> tools);
  // Legacy `validateAgentWriteScope` (lines 1010-1054): explore never writes,
  // a write without `write_scope` is rejected, and a write outside the
  // declared scope is rejected with the legacy message.
  [[nodiscard]] std::optional<std::string>
  ValidateWriteScope(const CompletionToolCall &call, AgentToolCategory category,
                     const AgentRunInputs &inputs) const;
  [[nodiscard]] huxerui::Task<std::vector<std::pair<
      domain::PipelineAgent, AgentRunResult>>>
  RunLevel(const std::vector<domain::PipelineAgent> &level,
           const AgentRunResults &completed, const domain::ModelConfig &model,
           std::string_view workspace_path);
  [[nodiscard]] huxerui::Task<void>
  RunLevelAgent(domain::PipelineAgent agent, domain::ModelConfig model,
                std::string workspace_path, AgentRunResults completed,
                std::shared_ptr<std::vector<std::optional<AgentRunResult>>>
                    slots,
                std::shared_ptr<std::atomic<std::size_t>> pending,
                std::size_t index);
  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunPipelineAgent(const domain::PipelineAgent &agent,
                   const AgentRunResults &completed,
                   const domain::ModelConfig &model,
                   std::string_view workspace_path);
  // Shared tail of `RunAgent`: records the finished run and returns the compact
  // ref the main model sees (legacy `finishAgentWithCompact`, lines 274-312).
  [[nodiscard]] AgentRunResult FinishRecord(SubAgentRunRecord record,
                                            AgentRunResult result);

  std::shared_ptr<CompletionGateway> completion_;
  std::shared_ptr<ToolRegistry> tools_;
  std::shared_ptr<PromptTemplateRepository> prompt_templates_;
  std::shared_ptr<ModelStore> models_;
  std::shared_ptr<SubAgentResultSink> results_;
  std::shared_ptr<AgentExtensionPromptSource> extensions_;
  std::shared_ptr<SubAgentBackgroundLauncher> background_;
  std::shared_ptr<const AgentToolAccessPolicy> tool_access_;
  SubAgentEnvironment environment_;
  AgentCancellation cancellation_;
  AgentToolBudget budget_;
};

} // namespace linecode::application
