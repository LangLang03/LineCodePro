// Contract tests for the sub-agent execution engine (`SubAgentRunner`).

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <expected>
#include <functional>
#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <utility>
#include <vector>

#include "gtest_support.h"
#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/agent_result_registry_sink.h"
#include "application/mcp_execution_settings.h"
#include "application/ports/agent_runner.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/model_store.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/settings_store.h"
#include "application/ports/tool_registry.h"
#include "application/prompt_template_repository.h"
#include "application/sub_agent_runner.h"
#include "domain/agent_pipeline.h"

namespace {

using namespace linecode;

using Request = application::CompletionRequest;
using Response = std::expected<application::CompletionResponse,
                               application::CompletionError>;

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class MemorySettings final : public application::AsyncSettingsStore {
public:
  huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    const auto found = values.find(key);
    co_return found == values.end() ? std::move(fallback) : found->second;
  }
  huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    values.insert_or_assign(std::move(key), std::move(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>> SetBoolean(std::string,
                                                              bool) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string, std::int64_t) override {
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  Remove(std::string key) override {
    values.erase(key);
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override {
    values.clear();
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return values;
  }

  std::map<std::string, std::string, std::less<>> values;
};

class FakeExecutionSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    auto settings = domain::DefaultMcpExecutionSettings();
    settings.mode = mode;
    co_return settings;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode value) override {
    mode = value;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionMode mode{domain::McpExecutionMode::local};
};

template <class T>
huxerui::Task<application::ProjectWorkspaceResult<T>>
UnavailableWorkspaceOperation() {
  co_return std::unexpected(application::ProjectWorkspaceError{
      .code = application::ProjectWorkspaceErrorCode::io,
      .message = "unused in environment test"});
}

class SelectedProjectWorkspace final
    : public application::ProjectWorkspaceController {
public:
  explicit SelectedProjectWorkspace(domain::ProjectRecord project)
      : project_(std::move(project)) {}

  huxerui::Task<application::ProjectWorkspaceResult<
      std::vector<domain::ProjectRecord>>>
  ListProjects() override {
    co_return std::vector<domain::ProjectRecord>{project_};
  }
  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectedProject() override {
    co_return project_;
  }
  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  CreateManagedProject(std::string) override {
    return UnavailableWorkspaceOperation<domain::ProjectRecord>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  RegisterExternalProject(std::string, std::string) override {
    return UnavailableWorkspaceOperation<domain::ProjectRecord>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectProject(std::string) override {
    return UnavailableWorkspaceOperation<domain::ProjectRecord>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  DeleteProject(std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectFileNode>>
  LoadTree(std::string) override {
    return UnavailableWorkspaceOperation<domain::ProjectFileNode>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateFile(std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateDirectory(std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<std::string>>
  ReadText(std::string, std::string) override {
    return UnavailableWorkspaceOperation<std::string>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  WriteText(std::string, std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Rename(std::string, std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Copy(std::string, std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Move(std::string, std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }
  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Delete(std::string, std::string) override {
    return UnavailableWorkspaceOperation<void>();
  }

private:
  domain::ProjectRecord project_;
};

class FakeModelStore final : public application::ModelStore {
public:
  FakeModelStore() {
    model.id = "m1";
    model.name = "Fake model";
    model.model_id = "fake-model";
    model.tool_call_limit = 200;
    selected = "m1";
  }

  huxerui::Task<std::expected<std::vector<domain::ModelConfig>,
                              application::ModelStoreError>>
  List() override {
    co_return std::vector<domain::ModelConfig>{model};
  }
  huxerui::Task<std::expected<std::optional<domain::ModelConfig>,
                              application::ModelStoreError>>
  Find(std::string id) override {
    if (id == model.id)
      co_return std::optional<domain::ModelConfig>{model};
    co_return std::optional<domain::ModelConfig>{};
  }
  huxerui::Task<
      std::expected<domain::ModelConfig, application::ModelStoreError>>
  Save(domain::ModelConfig value) override {
    co_return value;
  }
  huxerui::Task<std::expected<void, application::ModelStoreError>>
  Delete(std::vector<std::string>) override {
    co_return std::expected<void, application::ModelStoreError>{};
  }
  huxerui::Task<std::expected<void, application::ModelStoreError>>
  Select(std::string id) override {
    selected = std::move(id);
    co_return std::expected<void, application::ModelStoreError>{};
  }
  huxerui::Task<std::expected<std::string, application::ModelStoreError>>
  SelectedId() override {
    co_return selected;
  }

  domain::ModelConfig model;
  std::string selected;
};

class FakeToolRegistry final : public application::ToolRegistry {
public:
  struct Invocation final {
    std::string name;
    std::string arguments_json;
  };

  huxerui::Task<std::expected<void, application::ToolRegistryError>>
  Refresh() override {
    ++refresh_count;
    co_return std::expected<void, application::ToolRegistryError>{};
  }

  std::span<const application::RegisteredTool> Tools() const noexcept override {
    return catalog;
  }

  huxerui::Task<std::expected<application::ToolInvocationResult,
                              application::ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override {
    invoked.push_back(Invocation{.name = std::move(name),
                                 .arguments_json = std::move(arguments_json)});
    co_return application::ToolInvocationResult{
        .content = "tool-result:" + invoked.back().name,
        .error = false,
        .diff_id = {},
    };
  }

  std::vector<application::RegisteredTool> catalog;
  std::vector<Invocation> invoked;
  std::size_t refresh_count{};
};

class FakeExtensionPromptSource final
    : public application::AgentExtensionPromptSource {
public:
  huxerui::Task<application::SkillResult<std::string>>
  BuildExtensionPrompt() const override {
    co_return prompt;
  }

  std::string prompt{"#### Skill: Demo\nInstalled skill body."};
};

class FakeCompletionGateway final : public application::CompletionGateway {
public:
  huxerui::Task<Response>
  Complete(Request request,
           application::CompletionObserver /*observer*/) override {
    // Index-based access: a concurrently running child may append while this
    // turn is suspended, which invalidates a held reference.
    const auto index = requests.size();
    requests.push_back(std::move(request));
    if (before_response)
      co_await before_response(requests[index], index);
    if (responder)
      co_return responder(requests[index], index);
    if (script.empty()) {
      co_return Response{application::CompletionResponse{
          .text = "empty script",
          .reasoning_content = {},
          .tool_calls = {},
          .input_tokens = 0,
          .output_tokens = 0,
      }};
    }
    co_return script[std::min(index, script.size() - 1U)];
  }

  // Optional coroutine hook: lets a test suspend a turn (fan-out ordering) or
  // cancel the runner while a model turn is in flight.
  std::function<huxerui::Task<void>(const Request &, std::size_t)>
      before_response;
  std::function<Response(const Request &, std::size_t)> responder;
  std::vector<Response> script;
  std::vector<Request> requests;
};

class FakeBackgroundLauncher final
    : public application::SubAgentBackgroundLauncher {
public:
  void Launch(std::function<huxerui::Task<void>()> factory) override {
    pending.push_back(std::move(factory));
  }

  // Runs every queued child inside the awaiting task, which keeps the "return
  // immediately, finish later" contract observable.
  huxerui::Task<void> Drain() {
    while (!pending.empty()) {
      auto factory = std::move(pending.front());
      pending.erase(pending.begin());
      co_await factory();
    }
  }

  [[nodiscard]] std::size_t pending_count() const noexcept {
    return pending.size();
  }

  std::vector<std::function<huxerui::Task<void>()>> pending;
};

// ---------------------------------------------------------------------------
// Builders
// ---------------------------------------------------------------------------

application::RegisteredTool MakeTool(std::string name, std::string category,
                                     bool allowed_in_read_only = false,
                                     application::AgentToolCategory
                                         agent_category = application::
                                             AgentToolCategory::system) {
  application::RegisteredTool tool;
  tool.name = std::move(name);
  tool.description = tool.name + " description";
  tool.parameters_json = R"({"type":"object"})";
  tool.allowed_in_read_only = allowed_in_read_only;
  tool.agent_category = agent_category;
  tool.category = std::move(category);
  return tool;
}

Response TextResponse(std::string text) {
  return Response{application::CompletionResponse{
      .text = std::move(text),
      .reasoning_content = {},
      .tool_calls = {},
      .input_tokens = 0,
      .output_tokens = 0,
  }};
}

Response ToolCallResponse(std::string id, std::string name,
                          std::string arguments_json) {
  return Response{application::CompletionResponse{
      .text = {},
      .reasoning_content = {},
      .tool_calls = {{.id = std::move(id),
                      .name = std::move(name),
                      .arguments_json = std::move(arguments_json)}},
      .input_tokens = 0,
      .output_tokens = 0,
  }};
}

Response FailureResponse(std::string message) {
  return Response{std::unexpected(application::CompletionError{
      .code = application::CompletionErrorCode::transport,
      .message = std::move(message),
      .http_status = 0,
  })};
}

domain::PipelineAgent MakeAgent(std::string id, std::string prompt,
                                std::string type = "explore",
                                std::vector<std::string> dependencies = {}) {
  domain::PipelineAgent agent;
  agent.id = std::move(id);
  agent.type = std::move(type);
  agent.description = "task " + agent.id;
  agent.prompt = std::move(prompt);
  agent.dependencies = std::move(dependencies);
  return agent;
}

std::vector<std::string>
ToolNames(const std::vector<application::CompletionTool> &tools) {
  std::vector<std::string> names;
  names.reserve(tools.size());
  for (const auto &tool : tools)
    names.push_back(tool.name);
  return names;
}

const std::string &SystemPrompt(const Request &request) {
  return request.messages.front().content;
}

const std::string &UserPrompt(const Request &request) {
  return request.messages.back().content;
}

std::string TagOf(const Request &request) {
  const auto &prompt = UserPrompt(request);
  if (prompt.find("TASK-A") != std::string::npos)
    return "A";
  if (prompt.find("TASK-B") != std::string::npos)
    return "B";
  return "?";
}

struct Harness final {
  Harness() {
    settings = std::make_shared<MemorySettings>();
    gateway = std::make_shared<FakeCompletionGateway>();
    tools = std::make_shared<FakeToolRegistry>();
    models = std::make_shared<FakeModelStore>();
    extensions = std::make_shared<FakeExtensionPromptSource>();
    launcher = std::make_shared<FakeBackgroundLauncher>();
    sink = std::make_shared<application::InMemorySubAgentResultSink>();
    templates =
        std::make_shared<application::PromptTemplateRepository>(settings);
    tools->catalog = {
        MakeTool("file_read", "file_ops", false,
                 application::AgentToolCategory::read),
        MakeTool("file_write", "file_ops", false,
                 application::AgentToolCategory::write),
        MakeTool("file_edit", "file_ops", false,
                 application::AgentToolCategory::write),
        MakeTool("glob", "file_ops", false,
                 application::AgentToolCategory::read),
        MakeTool("list_dir", "file_ops", false,
                 application::AgentToolCategory::read),
        MakeTool("image_understanding", "image", false,
                 application::AgentToolCategory::read),
        MakeTool("image_generation", "image", false,
                 application::AgentToolCategory::generate),
        MakeTool("web_search", "web_search", false,
                 application::AgentToolCategory::read),
        MakeTool("todo_update", "todo"),
        MakeTool("shell_execute", "shell", true),
        MakeTool("agent", "agent"),
        MakeTool("agent_pipeline", "agent"),
    };
    runner = MakeRunner({});
  }

  [[nodiscard]] std::shared_ptr<application::SubAgentRunner> MakeRunner(
      std::shared_ptr<application::SubAgentBackgroundLauncher> background,
      application::SubAgentEnvironment environment = {
          .workspace_path = "/workspace",
          .remote_mode = false,
          .permission_mode = "auto"}) const {
    return std::make_shared<application::SubAgentRunner>(
        gateway, tools, templates, models, sink, extensions,
        std::move(background),
        std::make_shared<application::StaticSubAgentEnvironmentProvider>(
            std::move(environment)));
  }

  void UseRegistrySink() {
    registry = std::make_shared<application::AgentResultRegistry>();
    runner = std::make_shared<application::SubAgentRunner>(
        gateway, tools, templates, models,
        std::make_shared<application::AgentResultRegistrySink>(registry),
        extensions, nullptr,
        std::make_shared<application::StaticSubAgentEnvironmentProvider>(
            application::SubAgentEnvironment{.workspace_path = "/workspace",
                                             .remote_mode = false,
                                             .permission_mode = "auto"}));
  }

  [[nodiscard]] application::AgentRunRequest
  AgentRequest(std::string type, std::string prompt, std::string description,
               bool async, std::vector<std::string> read_scope = {},
               std::vector<std::string> write_scope = {}) const {
    application::AgentRunRequest request;
    request.type = std::move(type);
    request.agent_id = {};
    request.description = std::move(description);
    request.prompt = std::move(prompt);
    request.read_scope = std::move(read_scope);
    request.write_scope = std::move(write_scope);
    request.async = async;
    request.tool_call_id = "call-1";
    return request;
  }

  std::shared_ptr<MemorySettings> settings;
  std::shared_ptr<FakeCompletionGateway> gateway;
  std::shared_ptr<FakeToolRegistry> tools;
  std::shared_ptr<FakeModelStore> models;
  std::shared_ptr<FakeExtensionPromptSource> extensions;
  std::shared_ptr<FakeBackgroundLauncher> launcher;
  std::shared_ptr<application::InMemorySubAgentResultSink> sink;
  std::shared_ptr<application::AgentResultRegistry> registry;
  std::shared_ptr<application::PromptTemplateRepository> templates;
  std::shared_ptr<application::SubAgentRunner> runner;
};

// ---------------------------------------------------------------------------
// Scenario harness
// ---------------------------------------------------------------------------

struct Scenario final {
  std::function<huxerui::Task<void>(huxerui::TaskScope)> body;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle =
        tasks.Launch([scenario, tasks]() -> huxerui::Task<void> {
          co_await scenario->body(tasks);
          scenario->done = true;
        });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("sub-agent-probe");
}

void RunScenario(std::function<huxerui::Task<void>(huxerui::TaskScope)> body) {
  auto scenario = std::make_shared<Scenario>();
  scenario->body = std::move(body);
  active = scenario;
  {
    const huxerui::Application app(Probe, {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 4'000 && !scenario->done; ++attempt)
      ui.Pump(std::chrono::milliseconds{1});
    EXPECT_EXPRESSION(scenario->done);
  }
  active.reset();
}

// ---------------------------------------------------------------------------
// Pure helpers (no coroutine): the ported legacy wording
// ---------------------------------------------------------------------------

TEST(SubAgentRunnerTest, DependencyContextMatchesLegacyWording) {
  EXPECT_EXPRESSION(application::AgentScopeSummary({}) == "未声明");
  EXPECT_EXPRESSION(application::AgentScopeSummary(std::vector<std::string>{
             " src ", "", "docs", "  "}) == "src, docs");

  const auto workspace = application::AgentWorkspacePrompt("/workspace", false);
  EXPECT_EXPRESSION(workspace ==
         "当前工作区: "
         "/workspace\n所有文件路径默认相对此工作区。不要访问未授权路径，"
         "不要读取 API key、token、密码等敏感数据。");
  EXPECT_EXPRESSION(application::AgentWorkspacePrompt("/srv/app", true) ==
         "当前工作区: /srv/app\n当前是 SSH "
         "远端或终端提供者模式：所有命令作用于远端"
         "主机上的工作区，文件路径按远端约定解析。");
  EXPECT_EXPRESSION(
      application::AgentWorkspacePrompt("", true) ==
      "当前工作区: ~\n当前是 SSH 远端或终端提供者模式：所有命令作用于远端主机上"
      "的工作区，文件路径按远端约定解析。");
  EXPECT_EXPRESSION(application::AgentWorkspacePrompt("", false) ==
         "当前工作区: "
         ".\n所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 "
         "API key、token、密码等敏感数据。");

  EXPECT_EXPRESSION(
      application::AgentScopePrompt("explore", {"src"}, {"ignored"}, false) ==
      "## Agent 范围\nread_scope: src\nwrite_scope: ignored\n"
      "这是 explore Agent，write_scope 必须视为无效，禁止任何写入。");
  EXPECT_EXPRESSION(application::AgentScopePrompt("sub-coding", {"src"}, {}, false) ==
         "## Agent 范围\nread_scope: src\nwrite_scope: 未声明\n"
         "没有授权写入范围。禁止写入文件；如果任务需要修改文件，直接说明需要主"
         "模型重"
         "新分配 write_scope。");
  EXPECT_EXPRESSION(
      application::AgentScopePrompt("sub-coding", {}, {"src"}, false)
          .find("只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把多个 "
                "Agent 的职责混到同一个文件里。") != std::string::npos);
  EXPECT_EXPRESSION(
      application::AgentScopePrompt("sub-coding", {}, {"src"}, true)
          .find("\n注意：所有路径都是远端主机上的路径；写入/读取都要通过 "
                "shell_execute 调用 sed/awk/python heredoc/cat 等命令，不要尝试"
                "调用本地 file 类工具。") != std::string::npos);

  EXPECT_EXPRESSION(application::NormalizeAgentType(" sub_coding ") == "sub-coding");
  EXPECT_EXPRESSION(application::NormalizeAgentType("SUBCODING") == "sub-coding");
  EXPECT_EXPRESSION(application::NormalizeAgentType("Coding") == "sub-coding");
  EXPECT_EXPRESSION(application::NormalizeAgentType("EXPLORE") == "explore");
  EXPECT_EXPRESSION(application::NormalizeAgentType("weird") == "weird");
  EXPECT_EXPRESSION(application::IsExploreAgentType("explore"));
  EXPECT_EXPRESSION(!application::IsExploreAgentType("Explore"));

  auto scoped_mcp = MakeTool("mcpx_42svxm_lookup", "mcp");
  scoped_mcp.agent_scope_ids = {"custom:server-1"};
  const auto access = application::DefaultAgentToolAccessPolicy();
  EXPECT_EXPRESSION(application::IsAgentToolAllowed(
      scoped_mcp, "explore", {},
      std::vector<std::string>{"custom:server-1"}, *access));
  EXPECT_EXPRESSION(!application::IsAgentToolAllowed(
      scoped_mcp, "explore", {},
      std::vector<std::string>{"custom:different-server"}, *access));

  auto extension_reader =
      MakeTool("brand_new_reader", "future_group", false,
               application::AgentToolCategory::read);
  EXPECT_EXPRESSION(application::IsAgentToolAllowed(extension_reader, "explore", {}, {},
                                         *access));
  auto misleading_legacy_name =
      MakeTool("file_read", "file_ops", false,
               application::AgentToolCategory::system);
  EXPECT_EXPRESSION(!application::IsAgentToolAllowed(misleading_legacy_name, "explore",
                                          {}, {}, *access));

  application::AgentRunResults results;
  results.Put("a", application::AgentRunResult{.output = "OUTPUT-A",
                                               .tool_call_count = 0,
                                               .error = false});
  results.Put("b", application::AgentRunResult{.output = "OUTPUT-B",
                                               .tool_call_count = 2,
                                               .error = false});
  EXPECT_EXPRESSION(results.size() == 2U);
  EXPECT_EXPRESSION(results.Find("a") != nullptr &&
         results.Find("a")->output == "OUTPUT-A");
  EXPECT_EXPRESSION(results.Find("missing") == nullptr);
  EXPECT_EXPRESSION(application::AgentDependencyOutputContext(MakeAgent("c", "TASK-C"),
                                                   results)
             .empty());
  EXPECT_EXPRESSION(application::AgentDependencyOutputContext(
             MakeAgent("c", "TASK-C", "explore", {"a", "missing", "b"}),
             results) ==
         "\n\n## 上游 Agent 输出\n\n### a\nOUTPUT-A\n\n### b\nOUTPUT-B\n"
         "\n请基于以上结果继续你的任务。");

  // The compact ref carries the legacy marker and never the transcript.
  application::InMemorySubAgentResultSink sink;
  application::SubAgentRunRecord record;
  record.agent_id = "ag_1_1";
  record.tool_call_id = "call-1";
  record.type = "explore";
  record.description = "find it";
  record.status = "done";
  record.output = "the full transcript";
  record.preview = "preview";
  const auto compact = sink.ToCompactRef(record);
  EXPECT_EXPRESSION(compact.find("\"linecode_agent_ref\":true") != std::string::npos);
  EXPECT_EXPRESSION(compact.find("\"agent_id\":\"ag_1_1\"") != std::string::npos);
  EXPECT_EXPRESSION(compact.find("\"status\":\"done\"") != std::string::npos);
  EXPECT_EXPRESSION(compact.find("\"tool_call_id\":\"call-1\"") != std::string::npos);
  EXPECT_EXPRESSION(compact.find("preview") != std::string::npos);
  EXPECT_EXPRESSION(compact.find("the full transcript") == std::string::npos);

  const auto allocated = sink.AllocateId();
  EXPECT_EXPRESSION(allocated.starts_with("ag_"));
  EXPECT_EXPRESSION(sink.AllocateId() != allocated);
}

// ---------------------------------------------------------------------------
// Tool trimming + prompt assembly
// ---------------------------------------------------------------------------

TEST(SubAgentRunnerTest, ExploreSeesReadToolsOnly) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("explore done")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "summarize the file tool", "Explore the repository", false,
        {"src", "include"}));
    EXPECT_EXPRESSION(!result.error);

    EXPECT_EXPRESSION(harness->gateway->requests.size() == 1U);
    const auto &request = harness->gateway->requests.front();
    // Legacy `defaultAgentAllowedCategories("explore")` = {READ}: only
    // READ-class tools survive and the dispatch tools never do.
    EXPECT_EXPRESSION(ToolNames(request.tools) ==
           std::vector<std::string>({"file_read", "glob", "list_dir",
                                     "image_understanding", "web_search"}));

    const auto &system = SystemPrompt(request);
    EXPECT_EXPRESSION(system.starts_with(
        "You are a code exploration Agent. Your task is to quickly locate and "
        "analyze code, and answer the user's questions."));
    EXPECT_EXPRESSION(system.find("你的任务: Explore the repository") !=
           std::string::npos);
    EXPECT_EXPRESSION(system.find("当前工作区: /workspace") != std::string::npos);
    EXPECT_EXPRESSION(system.find("## Agent 范围") != std::string::npos);
    EXPECT_EXPRESSION(system.find("read_scope: src, include") != std::string::npos);
    EXPECT_EXPRESSION(system.find("write_scope: 未声明") != std::string::npos);
    EXPECT_EXPRESSION(
        system.find(
            "这是 explore Agent，write_scope 必须视为无效，禁止任何写入。") !=
        std::string::npos);
    EXPECT_EXPRESSION(system.find("#### Skill: Demo") != std::string::npos);
    EXPECT_EXPRESSION(system.find("## Available tools") != std::string::npos);
    EXPECT_EXPRESSION(system.find("- file_read: file_read description") !=
           std::string::npos);
    EXPECT_EXPRESSION(system.find("Permission mode: automatic.") != std::string::npos);
    EXPECT_EXPRESSION(UserPrompt(request) == "summarize the file tool");
    co_return;
  });
  EXPECT_EXPRESSION(harness->sink->size() == 1U);
  EXPECT_EXPRESSION(harness->sink->records().front().output == "explore done");
  EXPECT_EXPRESSION(harness->sink->records().front().status == "done");
}

TEST(SubAgentRunnerTest, SubCodingSeesReadAndWriteTools) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("coding done")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("sub-coding", "TASK: edit the file",
                              "Edit one file", false, {"src"}, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    const auto &request = harness->gateway->requests.front();
    // Legacy READ + WRITE set: the file_ops READ/WRITE members, the READ
    // image/web tools and `shell_execute` through its read-only opt-in
    // (line 835).
    EXPECT_EXPRESSION(ToolNames(request.tools) ==
           std::vector<std::string>({"file_read", "file_write", "file_edit",
                                     "glob", "list_dir", "image_understanding",
                                     "web_search", "shell_execute"}));
    const auto &system = SystemPrompt(request);
    EXPECT_EXPRESSION(
        system.starts_with("You are a coding Agent. Your task is to complete "
                           "well-scoped coding subtasks."));
    EXPECT_EXPRESSION(system.find(
               "只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把"
               "多个 Agent 的职责混到同一个文件里。") != std::string::npos);
    EXPECT_EXPRESSION(system.find("write_scope: src") != std::string::npos);
    co_return;
  });
}

TEST(SubAgentRunnerTest, RemoteModeSelectsTheRemoteRoleAndScope) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("remote done")};
  harness->runner = harness->MakeRunner(
      {}, application::SubAgentEnvironment{.workspace_path = "/srv/app",
                                           .remote_mode = true,
                                           .permission_mode = "confirm"});
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK", "Remote edit", false, {}, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    const auto &system = SystemPrompt(harness->gateway->requests.front());
    EXPECT_EXPRESSION(system.starts_with(
        "You are a coding Agent in a remote Shell environment"));
    EXPECT_EXPRESSION(system.find("Permission mode: confirmation.") != std::string::npos);
    EXPECT_EXPRESSION(system.find("当前是 SSH 远端或终端提供者模式") != std::string::npos);
    co_return;
  });
}

TEST(SubAgentRunnerTest, EnvironmentProviderIsSnapshottedForEveryRun) {
  auto harness = std::make_shared<Harness>();
  auto environment =
      std::make_shared<application::StaticSubAgentEnvironmentProvider>(
          application::SubAgentEnvironment{
              .workspace_path = "/local/project",
              .remote_mode = false,
              .permission_mode = "auto",
              .permission_scope = "local-project"});
  harness->runner = std::make_shared<application::SubAgentRunner>(
      harness->gateway, harness->tools, harness->templates, harness->models,
      harness->sink, harness->extensions, nullptr, environment);
  harness->gateway->responder = [](const Request &, std::size_t index) {
    return TextResponse("done-" + std::to_string(index));
  };

  RunScenario([harness, environment](huxerui::TaskScope) -> huxerui::Task<void> {
    auto local = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "LOCAL", "Local environment", false));
    EXPECT_EXPRESSION(!local.error);
    environment->Update(application::SubAgentEnvironment{
        .workspace_path = "/remote/workspace",
        .remote_mode = true,
        .permission_mode = "confirm",
        .permission_scope = "remote-project"});
    auto remote = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "REMOTE", "Remote environment", false));
    EXPECT_EXPRESSION(!remote.error);
    co_return;
  });

  EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[0])
             .contains("当前工作区: /local/project"));
  EXPECT_EXPRESSION(!SystemPrompt(harness->gateway->requests[0])
              .contains("远端或终端提供者模式"));
  EXPECT_EXPRESSION(harness->gateway->requests[0].permission_scope == "local-project");
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[1])
             .contains("当前工作区: /remote/workspace"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[1])
             .contains("远端或终端提供者模式"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[1])
             .contains("Permission mode: confirmation."));
  EXPECT_EXPRESSION(harness->gateway->requests[1].permission_scope == "remote-project");
  // Remote explore follows the legacy Host rule and can use the active
  // backend's shell tool.
  EXPECT_EXPRESSION(std::ranges::contains(ToolNames(harness->gateway->requests[1].tools),
                               "shell_execute"));
}

TEST(SubAgentRunnerTest, RuntimeEnvironmentRoutesLocalSshAndTerminalProvider) {
  auto harness = std::make_shared<Harness>();
  auto execution = std::make_shared<FakeExecutionSettings>();
  auto permission_settings = std::make_shared<MemorySettings>();
  auto permissions = std::make_shared<application::ToolPermissionService>(
      permission_settings);
  const auto workspace = [](std::string id, std::string path,
                            domain::ProjectSource source) {
    domain::ProjectRecord project;
    project.id = std::move(id);
    project.path = std::move(path);
    project.source = source;
    project.selected = true;
    return std::make_shared<SelectedProjectWorkspace>(std::move(project));
  };
  auto provider =
      std::make_shared<application::RuntimeSubAgentEnvironmentProvider>(
          execution, permissions,
          std::vector<application::SubAgentEnvironmentRoute>{
              {.mode = domain::McpExecutionMode::local,
               .workspace = workspace("local-id", "/local",
                                      domain::ProjectSource::managed),
               .remote_mode = false},
              {.mode = domain::McpExecutionMode::ssh,
               .workspace = workspace("ssh-id", "/srv/ssh",
                                      domain::ProjectSource::ssh),
               .remote_mode = true},
              {.mode = domain::McpExecutionMode::terminal_provider,
               .workspace = workspace("terminal-id", "/srv/terminal",
                                      domain::ProjectSource::external),
               .remote_mode = true},
          });
  harness->runner = std::make_shared<application::SubAgentRunner>(
      harness->gateway, harness->tools, harness->templates, harness->models,
      harness->sink, harness->extensions, nullptr, provider);
  harness->gateway->responder = [](const Request &, std::size_t index) {
    return TextResponse("route-" + std::to_string(index));
  };

  RunScenario([harness, execution,
               permissions](huxerui::TaskScope) -> huxerui::Task<void> {
    const auto saved = co_await permissions->SetMode(
        domain::ToolPermissionMode::confirm);
    EXPECT_EXPRESSION(saved);
    for (const auto mode : {domain::McpExecutionMode::local,
                            domain::McpExecutionMode::ssh,
                            domain::McpExecutionMode::terminal_provider}) {
      execution->mode = mode;
      auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
          "explore", "ROUTE", "Route environment", false));
      EXPECT_EXPRESSION(!result.error);
    }
    co_return;
  });

  EXPECT_EXPRESSION(harness->gateway->requests.size() == 3U);
  EXPECT_EXPRESSION(harness->gateway->requests[0].permission_scope == "local-id");
  EXPECT_EXPRESSION(harness->gateway->requests[1].permission_scope == "ssh-id");
  EXPECT_EXPRESSION(harness->gateway->requests[2].permission_scope == "terminal-id");
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[0]).contains("当前工作区: /local"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[1]).contains("当前工作区: /srv/ssh"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[2])
             .contains("当前工作区: /srv/terminal"));
  EXPECT_EXPRESSION(!SystemPrompt(harness->gateway->requests[0])
              .contains("远端或终端提供者模式"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[1])
             .contains("远端或终端提供者模式"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[2])
             .contains("远端或终端提供者模式"));
  EXPECT_EXPRESSION(SystemPrompt(harness->gateway->requests[2])
             .contains("Permission mode: confirmation."));
}

TEST(SubAgentRunnerTest, SelectedCustomMcpScopeIsAdvertisedAndInvoked) {
  auto harness = std::make_shared<Harness>();
  auto selected = MakeTool("mcpx_selected_lookup", "mcp");
  selected.agent_scope_ids = {"custom:server-1"};
  auto other = MakeTool("mcpx_other_lookup", "mcp");
  other.agent_scope_ids = {"custom:server-2"};
  harness->tools->catalog.push_back(selected);
  harness->tools->catalog.push_back(other);
  harness->gateway->script = {
      ToolCallResponse("mcp-call", "mcpx_selected_lookup",
                       R"({"query":"needle"})"),
      TextResponse("mcp done"),
  };

  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto request = harness->AgentRequest("explore", "LOOKUP", "Use MCP", false);
    request.custom_tool_names = {"file_read"};
    request.custom_mcp_ids = {"custom:server-1"};
    auto result = co_await harness->runner->RunAgent(std::move(request));
    EXPECT_EXPRESSION(!result.error);
    co_return;
  });

  const auto advertised = ToolNames(harness->gateway->requests.front().tools);
  EXPECT_EXPRESSION(std::ranges::contains(advertised, "file_read"));
  EXPECT_EXPRESSION(std::ranges::contains(advertised, "mcpx_selected_lookup"));
  EXPECT_EXPRESSION(!std::ranges::contains(advertised, "mcpx_other_lookup"));
  EXPECT_EXPRESSION(harness->tools->invoked.size() == 1U);
  EXPECT_EXPRESSION(harness->tools->invoked.front().name == "mcpx_selected_lookup");
  EXPECT_EXPRESSION(harness->tools->invoked.front().arguments_json ==
         R"({"query":"needle"})");
}

// ---------------------------------------------------------------------------
// Model loop
// ---------------------------------------------------------------------------

TEST(SubAgentRunnerTest, ToolLoopFeedsResultsBackAndCountsCalls) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {
      ToolCallResponse("call-9", "file_read",
                       R"({"file_path":"src/main.cpp"})"),
      TextResponse("final answer"),
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "read the entry point", "Read main", false, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 1);
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);
    const auto &second = harness->gateway->requests[1];
    EXPECT_EXPRESSION(second.messages.size() == 4U);
    EXPECT_EXPRESSION(second.messages[2].role == application::CompletionRole::assistant);
    EXPECT_EXPRESSION(second.messages[2].tool_calls.size() == 1U);
    EXPECT_EXPRESSION(second.messages[3].role == application::CompletionRole::tool);
    EXPECT_EXPRESSION(second.messages[3].tool_result);
    EXPECT_EXPRESSION(second.messages[3].tool_result->call_id == "call-9");
    EXPECT_EXPRESSION(second.messages[3].tool_result->content == "tool-result:file_read");
    EXPECT_EXPRESSION(!second.messages[3].tool_result->error);
    EXPECT_EXPRESSION(harness->tools->invoked.size() == 1U);
    EXPECT_EXPRESSION(harness->tools->invoked.front().name == "file_read");
    EXPECT_EXPRESSION(harness->tools->invoked.front().arguments_json ==
           R"({"file_path":"src/main.cpp"})");
    // The model-visible content is the compact ref, not the transcript.
    EXPECT_EXPRESSION(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    co_return;
  });
  EXPECT_EXPRESSION(harness->sink->size() == 1U);
  const auto &record = harness->sink->records().front();
  EXPECT_EXPRESSION(record.status == "done");
  EXPECT_EXPRESSION(record.output == "final answer");
  EXPECT_EXPRESSION(record.tool_call_count == 1);
  EXPECT_EXPRESSION(!record.error);
  EXPECT_EXPRESSION(record.tool_call_id == "call-1");
  EXPECT_EXPRESSION(record.preview == "final answer");
}

TEST(SubAgentRunnerTest, UnicodePreviewNeverSplitsUtf8CodePoints) {
  auto harness = std::make_shared<Harness>();
  const std::string output = std::string(239U, 'a') + "工具";
  harness->gateway->script = {TextResponse(output)};

  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    const auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "read", "UTF-8 preview", false));
    EXPECT_EXPRESSION(!result.error);
    co_return;
  });

  EXPECT_EXPRESSION(harness->sink->size() == 1U);
  const auto &preview = harness->sink->records().front().preview;
  EXPECT_EXPRESSION(preview == std::string(239U, 'a') + "工");
  // A split three-byte character would make compact JSON invalid UTF-8 and
  // poison the next model request, which is the regression this test covers.
  EXPECT_EXPRESSION(preview.ends_with("工"));
}

TEST(SubAgentRunnerTest, AgentNestedCallRoundTripsThroughRegistry) {
  auto harness = std::make_shared<Harness>();
  harness->UseRegistrySink();
  harness->gateway->script = {
      ToolCallResponse("nested-agent", "file_read",
                       R"({"file_path":"src/main.cpp"})"),
      TextResponse("agent final"),
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "read", "Nested call", false, {"src"}));
    EXPECT_EXPRESSION(!result.error);

    const auto view = application::ResolveAgentResultReference(
        result.output, *harness->registry);
    EXPECT_EXPRESSION(view);
    const auto *progress =
        std::get_if<domain::AgentExecutionSnapshot>(&view->progress);
    EXPECT_EXPRESSION(progress != nullptr && progress->tool_calls.size() == 1U);
    const auto &call = progress->tool_calls.front();
    EXPECT_EXPRESSION(call.id == "nested-agent");
    EXPECT_EXPRESSION(call.name == "file_read");
    EXPECT_EXPRESSION(call.arguments_json == R"({"file_path":"src/main.cpp"})");
    EXPECT_EXPRESSION(call.status == domain::AgentToolCallStatus::completed);
    EXPECT_EXPRESSION(call.status_history == (std::vector<domain::AgentToolCallStatus>{
                                      domain::AgentToolCallStatus::requested,
                                      domain::AgentToolCallStatus::running,
                                      domain::AgentToolCallStatus::completed}));
    EXPECT_EXPRESSION(call.result && call.result->content == "tool-result:file_read" &&
           !call.result->error);

    const auto full = harness->registry->GetRecord(view->agent_id);
    EXPECT_EXPRESSION(full && full->progress_json.contains("nested-agent"));
    const auto meta = harness->registry->Fetch(view->agent_id, "meta");
    EXPECT_EXPRESSION(!meta.error && meta.content.contains("nested-agent"));
    const auto fetched = harness->registry->Fetch(view->agent_id, "output");
    EXPECT_EXPRESSION(!fetched.error && fetched.content == "agent final");
    co_return;
  });
}

TEST(SubAgentRunnerTest, WriteScopeGuardsEveryWriteCall) {
  const auto write_call = [](const std::string &path) {
    return ToolCallResponse("call-w", "file_write",
                            R"({"file_path":")" + path + R"("})");
  };

  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {write_call("src/main.cpp"),
                              write_call("docs/readme.md"),
                              TextResponse("stopped")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK", "Write into src", false, {"src"}, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 2);
    EXPECT_EXPRESSION(harness->tools->invoked.size() == 1U);
    const auto &rejected = harness->gateway->requests.back().messages[5];
    EXPECT_EXPRESSION(rejected.role == application::CompletionRole::tool);
    EXPECT_EXPRESSION(rejected.tool_result);
    EXPECT_EXPRESSION(rejected.tool_result->content ==
           "Agent 写入路径超出 write_scope: docs/readme.md\n允许写入范围: src\n"
           "请停止写入并让主模型重新分配。");
    EXPECT_EXPRESSION(rejected.tool_result->error);
    co_return;
  });

  auto without_scope = std::make_shared<Harness>();
  without_scope->gateway->script = {write_call("src/main.cpp"),
                                    TextResponse("ok")};
  RunScenario([without_scope](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result =
        co_await without_scope->runner->RunAgent(without_scope->AgentRequest(
            "sub-coding", "TASK", "Write without scope", false, {}, {}));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(without_scope->tools->invoked.empty());
    const auto &rejected = without_scope->gateway->requests.back().messages[3];
    EXPECT_EXPRESSION(rejected.tool_result);
    EXPECT_EXPRESSION(
        rejected.tool_result->content ==
        "Agent 未声明 write_scope，禁止写入文件。请让主模型重新分配明确的写入"
        "范围。");
    co_return;
  });

  auto explore = std::make_shared<Harness>();
  explore->gateway->script = {write_call("src/main.cpp"), TextResponse("ok")};
  RunScenario([explore](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await explore->runner->RunAgent(explore->AgentRequest(
        "explore", "TASK", "Try to write", false, {}, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(explore->tools->invoked.empty());
    const auto &rejected = explore->gateway->requests.back().messages[3];
    EXPECT_EXPRESSION(rejected.tool_result);
    EXPECT_EXPRESSION(rejected.tool_result->content ==
           "Agent 不允许调用此工具: file_write");
    co_return;
  });
}

TEST(SubAgentRunnerTest, ToolLimitStopsTheLoopWithTheLegacyMessage) {
  auto harness = std::make_shared<Harness>();
  harness->models->model.tool_call_limit = 1;
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})"),
      TextResponse("fresh budget")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "Budget limited", false, {}));
    EXPECT_EXPRESSION(result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 1);
    // The budget check runs before the next model turn.
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 1U);
    // The model only sees the compact ref; the legacy tool-limit message is
    // the recorded output of the failed run.
    EXPECT_EXPRESSION(harness->sink->records().front().output ==
           std::string{application::kAgentToolLimitMessage} + "\n");

    // Exhaustion is scoped to this invocation. The next top-level Agent starts
    // with a fresh budget even though the runner itself is long-lived.
    auto recovered = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "Fresh budget", false, {}));
    EXPECT_EXPRESSION(!recovered.error);
    EXPECT_EXPRESSION(harness->sink->records().back().output == "fresh budget");
    co_return;
  });
  EXPECT_EXPRESSION(harness->sink->records().front().status == "error");
  EXPECT_EXPRESSION(harness->sink->records().front().output ==
         std::string{application::kAgentToolLimitMessage} + "\n");
}

TEST(SubAgentRunnerTest, CancellationReturnsTheTerminatedResult) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})"),
      TextResponse("never reached"),
  };
  const auto runner = harness->runner;
  harness->gateway->before_response =
      [runner](const Request &, const std::size_t index) -> huxerui::Task<void> {
    if (index == 0U)
      runner->RequestStop();
    co_return;
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "Cancelled run", false, {}));
    EXPECT_EXPRESSION(result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 0);
    EXPECT_EXPRESSION(harness->tools->invoked.empty());
    EXPECT_EXPRESSION(harness->sink->records().front().output ==
           std::string{application::kAgentTerminatedMessage});

    // Cancellation belongs to this invocation, not the long-lived runner.
    auto recovered = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "Recovered run", false, {}));
    EXPECT_EXPRESSION(!recovered.error);
    EXPECT_EXPRESSION(harness->sink->records().back().output == "never reached");
    co_return;
  });
  EXPECT_EXPRESSION(harness->sink->records().front().status == "error");
  EXPECT_EXPRESSION(harness->sink->records().front().output ==
         std::string{application::kAgentTerminatedMessage});
}

TEST(SubAgentRunnerTest, AsyncCancellationStopsTheBackgroundRunOnly) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("next run works")};
  harness->runner = harness->MakeRunner(harness->launcher);
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto launched = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "TASK", "Async cancellation", true, {"src"}));
    EXPECT_EXPRESSION(!launched.error);
    const auto agent_id = harness->sink->records().front().agent_id;

    harness->runner->RequestStop();
    co_await harness->launcher->Drain();
    const auto *cancelled = harness->sink->Find(agent_id);
    EXPECT_EXPRESSION(cancelled != nullptr && cancelled->error);
    EXPECT_EXPRESSION(cancelled->output == application::kAgentTerminatedMessage);
    EXPECT_EXPRESSION(harness->gateway->requests.empty());

    auto recovered = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "After async", false, {}));
    EXPECT_EXPRESSION(!recovered.error);
    EXPECT_EXPRESSION(harness->sink->records().back().output == "next run works");
    co_return;
  });
}

TEST(SubAgentRunnerTest, MissingModelIsReportedBeforeAnyLoop) {
  auto harness = std::make_shared<Harness>();
  harness->models->selected.clear();
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "No model", false, {}));
    EXPECT_EXPRESSION(result.error);
    EXPECT_EXPRESSION(result.output == "当前没有可用模型，无法运行 Agent。");
    EXPECT_EXPRESSION(harness->gateway->requests.empty());
    EXPECT_EXPRESSION(harness->sink->size() == 0U);
    co_return;
  });
}

// ---------------------------------------------------------------------------
// Async explore
// ---------------------------------------------------------------------------

TEST(SubAgentRunnerTest, AsyncExploreReturnsTheRunningRefImmediately) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("background output")};
  harness->runner = harness->MakeRunner(harness->launcher);
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "TASK", "Async explore", true, {"src"}));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 0);
    // The ref is the *running* record and the loop has not started yet.
    EXPECT_EXPRESSION(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    EXPECT_EXPRESSION(result.output.find("\"status\":\"running\"") != std::string::npos);
    EXPECT_EXPRESSION(result.output.find("\"async\":true") != std::string::npos);
    EXPECT_EXPRESSION(harness->gateway->requests.empty());
    EXPECT_EXPRESSION(harness->launcher->pending_count() == 1U);
    EXPECT_EXPRESSION(harness->sink->records().front().status == "running");
    const auto agent_id = harness->sink->records().front().agent_id;
    EXPECT_EXPRESSION(!agent_id.empty());

    // The background child finishes later and updates the same record.
    co_await harness->launcher->Drain();
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 1U);
    const auto *record = harness->sink->Find(agent_id);
    EXPECT_EXPRESSION(record != nullptr);
    EXPECT_EXPRESSION(record->status == "done");
    EXPECT_EXPRESSION(record->output == "background output");
    EXPECT_EXPRESSION(record->async);
    co_return;
  });
}

TEST(SubAgentRunnerTest, AsyncSubCodingIsRejected) {
  auto harness = std::make_shared<Harness>();
  harness->runner = harness->MakeRunner(harness->launcher);
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK", "Async coding", true, {}, {"src"}));
    EXPECT_EXPRESSION(result.error);
    EXPECT_EXPRESSION(result.output == "async=true is not allowed for sub-coding agents.");
    EXPECT_EXPRESSION(harness->launcher->pending_count() == 0U);
    EXPECT_EXPRESSION(harness->gateway->requests.empty());
    co_return;
  });
}

// ---------------------------------------------------------------------------
// Pipeline
// ---------------------------------------------------------------------------

TEST(SubAgentRunnerTest, PipelineFeedsUpstreamOutputIntoDownstreamPrompts) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->responder = [](const Request &request,
                                   std::size_t) -> Response {
    if (UserPrompt(request).starts_with("TASK-A"))
      return TextResponse("OUTPUT-A");
    return TextResponse("OUTPUT-B");
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.tool_call_id = "pipeline-1";
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "sub-coding", {"a"})};
    auto result =
        co_await harness->runner->RunAgentPipeline(std::move(request));
    EXPECT_EXPRESSION(!result.error);
    EXPECT_EXPRESSION(result.tool_call_count == 0);
    EXPECT_EXPRESSION(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    EXPECT_EXPRESSION(result.output.find("\"type\":\"pipeline\"") != std::string::npos);
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);

    // Level order: the dependency-free agent runs first.
    EXPECT_EXPRESSION(UserPrompt(harness->gateway->requests[0]).starts_with("TASK-A"));
    const auto &downstream = UserPrompt(harness->gateway->requests[1]);
    EXPECT_EXPRESSION(downstream == "TASK-B\n\n## 上游 Agent 输出\n\n### a\nOUTPUT-A\n"
                         "\n请基于以上结果继续你的任务。");
    co_return;
  });

  // Every stage plus the pipeline itself is recorded.
  EXPECT_EXPRESSION(harness->sink->size() == 3U);
  const auto *first = harness->sink->Find("a");
  const auto *second = harness->sink->Find("b");
  EXPECT_EXPRESSION(first != nullptr && first->status == "done" &&
         first->output == "OUTPUT-A");
  EXPECT_EXPRESSION(second != nullptr && second->status == "done" &&
         second->output == "OUTPUT-B");
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  EXPECT_EXPRESSION(pipeline != nullptr);
  EXPECT_EXPRESSION(pipeline->type == "pipeline");
  EXPECT_EXPRESSION(pipeline->description == "2 agents");
  EXPECT_EXPRESSION(pipeline->status == "done");
  EXPECT_EXPRESSION(!pipeline->error);
  EXPECT_EXPRESSION(pipeline->output.starts_with("Agent pipeline completed: 2 个任务"));
  EXPECT_EXPRESSION(pipeline->output.find("\n\n## a · task a\n类型: explore\n状态: done\n"
                               "工具调用: 0\nOUTPUT-A") != std::string::npos);
  EXPECT_EXPRESSION(
      pipeline->output.find("\n\n## b · task b\n类型: sub-coding\n状态: done\n"
                            "工具调用: 0\nOUTPUT-B") != std::string::npos);
  EXPECT_EXPRESSION(pipeline->output.ends_with("\n\n总工具调用: 0"));
}

TEST(SubAgentRunnerTest, PipelineNestedCallRoundTripsThroughRegistry) {
  auto harness = std::make_shared<Harness>();
  harness->UseRegistrySink();
  harness->gateway->script = {
      TextResponse("upstream"),
      ToolCallResponse("nested-pipeline", "file_read",
                       R"({"file_path":"src/dependency.cpp"})"),
      TextResponse("downstream"),
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result =
        co_await harness->runner->RunAgentPipeline(std::move(request));
    EXPECT_EXPRESSION(!result.error);

    const auto view = application::ResolveAgentResultReference(
        result.output, *harness->registry);
    EXPECT_EXPRESSION(view);
    const auto *pipeline =
        std::get_if<domain::AgentPipelineSnapshot>(&view->progress);
    EXPECT_EXPRESSION(pipeline != nullptr && pipeline->agents.size() == 2U);
    const auto &downstream = pipeline->agents[1];
    EXPECT_EXPRESSION(downstream.id == "b");
    EXPECT_EXPRESSION(downstream.type == "explore");
    EXPECT_EXPRESSION(downstream.dependencies == std::vector<std::string>{"a"});
    EXPECT_EXPRESSION(downstream.status == domain::AgentExecutionStatus::done);
    EXPECT_EXPRESSION(downstream.output == "downstream");
    EXPECT_EXPRESSION(downstream.tool_calls.size() == 1U);
    EXPECT_EXPRESSION(downstream.tool_calls.front().id == "nested-pipeline");
    EXPECT_EXPRESSION(downstream.tool_calls.front().result);

    const auto full = harness->registry->GetRecord(view->agent_id);
    EXPECT_EXPRESSION(full && full->progress_json.contains("nested-pipeline"));
    const auto meta = harness->registry->Fetch(view->agent_id, "meta");
    EXPECT_EXPRESSION(!meta.error && meta.content.contains("nested-pipeline"));
    const auto fetched = harness->registry->Fetch(view->agent_id, "output");
    EXPECT_EXPRESSION(!fetched.error && fetched.content.contains("downstream"));
    co_return;
  });
}

TEST(SubAgentRunnerTest, SameLevelAgentsRunConcurrently) {
  auto harness = std::make_shared<Harness>();
  auto order = std::make_shared<std::vector<std::string>>();
  harness->gateway->responder = [](const Request &request,
                                   std::size_t) -> Response {
    return TextResponse("done-" + TagOf(request));
  };
  harness->gateway->before_response =
      [order](const Request &request, std::size_t) -> huxerui::Task<void> {
    const auto tag = TagOf(request);
    order->push_back("start-" + tag);
    co_await huxerui::Delay(std::chrono::milliseconds{20});
    order->push_back("end-" + tag);
    co_return;
  };
  RunScenario(
      [harness, order](huxerui::TaskScope scope) -> huxerui::Task<void> {
        // The real TaskScope launcher: the level fans out into child tasks.
        harness->runner = harness->MakeRunner(
            std::make_shared<application::TaskScopeSubAgentLauncher>(
                std::move(scope)));
        application::AgentPipelineRunRequest request;
        request.agents = {MakeAgent("a", "TASK-A"), MakeAgent("b", "TASK-B")};
        auto result =
            co_await harness->runner->RunAgentPipeline(std::move(request));
        EXPECT_EXPRESSION(!result.error);
        EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);
        EXPECT_EXPRESSION(order->size() == 4U);
        // Both agents entered their model turn before either finished, which a
        // sequential level cannot produce.
        EXPECT_EXPRESSION((*order)[0] == "start-A");
        EXPECT_EXPRESSION((*order)[1] == "start-B");
        co_return;
      });
  EXPECT_EXPRESSION(harness->sink->Find("a") != nullptr);
  EXPECT_EXPRESSION(harness->sink->Find("b") != nullptr);
}

TEST(SubAgentRunnerTest, FailedStageIsRecordedAndPropagates) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->responder = [](const Request &request,
                                   std::size_t) -> Response {
    if (UserPrompt(request).starts_with("TASK-A"))
      return FailureResponse("network down");
    return TextResponse("OUTPUT-B");
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result =
        co_await harness->runner->RunAgentPipeline(std::move(request));
    // The error propagates to the pipeline result...
    EXPECT_EXPRESSION(result.error);
    // ...and the next level still runs, mirroring the legacy `hasError` flow.
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);
    EXPECT_EXPRESSION(UserPrompt(harness->gateway->requests[1])
               .find("Agent 模型通信失败：\nnetwork down") !=
           std::string::npos);
    co_return;
  });
  const auto *failed = harness->sink->Find("a");
  EXPECT_EXPRESSION(failed != nullptr && failed->status == "error" && failed->error);
  EXPECT_EXPRESSION(failed->output == "Agent 模型通信失败：\nnetwork down");
  const auto *recovered = harness->sink->Find("b");
  EXPECT_EXPRESSION(recovered != nullptr && recovered->status == "done");
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  EXPECT_EXPRESSION(pipeline != nullptr && pipeline->error);
  EXPECT_EXPRESSION(pipeline->output.find("状态: error") != std::string::npos);
  EXPECT_EXPRESSION(pipeline->output.find("状态: done") != std::string::npos);
}

TEST(SubAgentRunnerTest, PlanPipelineErrorsBecomeToolContent) {
  auto harness = std::make_shared<Harness>();
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest empty;
    auto empty_result = co_await harness->runner->RunAgentPipeline(empty);
    EXPECT_EXPRESSION(empty_result.error);
    EXPECT_EXPRESSION(empty_result.output == "agent_pipeline.agents 不能为空。");

    application::AgentPipelineRunRequest self;
    self.agents = {MakeAgent("a", "TASK-A", "explore", {"a"})};
    auto self_result = co_await harness->runner->RunAgentPipeline(self);
    EXPECT_EXPRESSION(self_result.error);
    EXPECT_EXPRESSION(self_result.output == "Agent 不能依赖自身: a");

    application::AgentPipelineRunRequest unknown;
    unknown.agents = {MakeAgent("a", "TASK-A", "explore", {"ghost"})};
    auto unknown_result = co_await harness->runner->RunAgentPipeline(unknown);
    EXPECT_EXPRESSION(unknown_result.error);
    EXPECT_EXPRESSION(unknown_result.output ==
           "Agent 流水线存在循环依赖或重复 id，无法执行。");

    application::AgentPipelineRunRequest cycle;
    cycle.agents = {MakeAgent("a", "TASK-A", "explore", {"b"}),
                    MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto cycle_result = co_await harness->runner->RunAgentPipeline(cycle);
    EXPECT_EXPRESSION(cycle_result.error);
    EXPECT_EXPRESSION(cycle_result.output ==
           "Agent 流水线存在循环依赖或重复 id，无法执行。");

    // No agent ever ran.
    EXPECT_EXPRESSION(harness->gateway->requests.empty());
    EXPECT_EXPRESSION(harness->sink->size() == 0U);
    co_return;
  });
}

TEST(SubAgentRunnerTest, PipelineLimitStopsTheWholeRun) {
  auto harness = std::make_shared<Harness>();
  harness->models->model.tool_call_limit = 1;
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result =
        co_await harness->runner->RunAgentPipeline(std::move(request));
    EXPECT_EXPRESSION(result.error);
    // The shared main-flow budget stops the pipeline after the first agent.
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 1U);
    co_return;
  });
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  EXPECT_EXPRESSION(pipeline != nullptr && pipeline->error);
  EXPECT_EXPRESSION(pipeline->output.starts_with(
      "Agent 流水线因工具调用次数达到主流程上限，已提前结束：\n"));
  EXPECT_EXPRESSION(pipeline->output.find(application::kAgentToolLimitMessage) !=
         std::string::npos);
}

TEST(SubAgentRunnerTest, PipelineCancellationReturnsTheTerminatedMessage) {
  auto harness = std::make_shared<Harness>();
  const auto runner = harness->runner;
  harness->gateway->before_response =
      [runner](const Request &, const std::size_t index) -> huxerui::Task<void> {
    if (index == 0U)
      runner->RequestStop();
    co_return;
  };
  harness->gateway->script = {TextResponse("never"), TextResponse("recovered")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result =
        co_await harness->runner->RunAgentPipeline(std::move(request));
    EXPECT_EXPRESSION(result.error);
    const auto *pipeline =
        harness->sink->Find(harness->sink->records().back().agent_id);
    EXPECT_EXPRESSION(pipeline != nullptr && pipeline->error);
    // The cancelled level ends the pipeline before the next level starts.
    EXPECT_EXPRESSION(pipeline->output == "Agent 流水线已终止。");

    auto recovered = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "After pipeline", false, {}));
    EXPECT_EXPRESSION(!recovered.error);
    EXPECT_EXPRESSION(harness->sink->records().back().output == "recovered");
    co_return;
  });
}

TEST(SubAgentRunnerTest, ParentTaskCancellationStopsDetachedPipelineChildren) {
  auto harness = std::make_shared<Harness>();
  harness->gateway->responder = [](const Request &, std::size_t) -> Response {
    return TextResponse("must not complete");
  };
  harness->gateway->before_response =
      [](const Request &, std::size_t) -> huxerui::Task<void> {
    co_await huxerui::Delay(std::chrono::milliseconds{40});
  };

  RunScenario([harness](huxerui::TaskScope scope) -> huxerui::Task<void> {
    harness->runner = harness->MakeRunner(
        std::make_shared<application::TaskScopeSubAgentLauncher>(scope));
    auto parent_returned = std::make_shared<bool>(false);
    const auto parent = scope.Launch(
        [harness, parent_returned]() -> huxerui::Task<void> {
          application::AgentPipelineRunRequest request;
          request.agents = {MakeAgent("a", "TASK-A"),
                            MakeAgent("b", "TASK-B")};
          static_cast<void>(
              co_await harness->runner->RunAgentPipeline(std::move(request)));
          *parent_returned = true;
        });

    for (int attempt = 0;
         attempt < 100 && harness->gateway->requests.size() != 2U; ++attempt) {
      co_await huxerui::Delay(std::chrono::milliseconds{1});
    }
    EXPECT_EXPRESSION(harness->gateway->requests.size() == 2U);
    parent.Cancel();

    // The level children are sibling TaskScope tasks, not descendants of the
    // cancelled handle. They must observe the parent's context cancellation.
    co_await huxerui::Delay(std::chrono::milliseconds{60});
    EXPECT_EXPRESSION(!*parent_returned);
    for (const auto id : {std::string_view{"a"}, std::string_view{"b"}}) {
      const auto *record = harness->sink->Find(id);
      EXPECT_EXPRESSION(record != nullptr && record->error);
      EXPECT_EXPRESSION(record->output == application::kAgentTerminatedMessage);
    }
  });
}

} // namespace
