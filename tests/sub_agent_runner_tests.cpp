// Contract tests for the sub-agent execution engine (`SubAgentRunner`).

#include <algorithm>
#include <cassert>
#include <chrono>
#include <cstddef>
#include <expected>
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/ports/agent_runner.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/model_store.h"
#include "application/ports/settings_store.h"
#include "application/ports/tool_registry.h"
#include "application/prompt_template_repository.h"
#include "application/sub_agent_runner.h"
#include "domain/agent_pipeline.h"

namespace {

using namespace linecode;

using Request = application::CompletionRequest;
using Response =
    std::expected<application::CompletionResponse, application::CompletionError>;

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
    invoked.push_back(
        Invocation{.name = std::move(name),
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
                                     bool allowed_in_read_only = false) {
  application::RegisteredTool tool;
  tool.name = std::move(name);
  tool.description = tool.name + " description";
  tool.parameters_json = R"({"type":"object"})";
  tool.allowed_in_read_only = allowed_in_read_only;
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
        MakeTool("file_read", "file_ops"),
        MakeTool("file_write", "file_ops"),
        MakeTool("file_edit", "file_ops"),
        MakeTool("glob", "file_ops"),
        MakeTool("list_dir", "file_ops"),
        MakeTool("image_understanding", "image"),
        MakeTool("image_generation", "image"),
        MakeTool("web_search", "web_search"),
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
        std::move(background), std::move(environment));
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
    assert(scenario->done);
  }
  active.reset();
}

// ---------------------------------------------------------------------------
// Pure helpers (no coroutine): the ported legacy wording
// ---------------------------------------------------------------------------

void DependencyContextMatchesLegacyWording() {
  assert(application::AgentScopeSummary({}) == "未声明");
  assert(application::AgentScopeSummary(
             std::vector<std::string>{" src ", "", "docs", "  "}) ==
         "src, docs");

  const auto workspace = application::AgentWorkspacePrompt("/workspace", false);
  assert(workspace ==
         "当前工作区: /workspace\n所有文件路径默认相对此工作区。不要访问未授权路径，"
         "不要读取 API key、token、密码等敏感数据。");
  assert(application::AgentWorkspacePrompt("/srv/app", true) ==
         "当前工作区: /srv/app\n当前是 SSH 远端或终端提供者模式：所有命令作用于远端"
         "主机上的工作区，文件路径按远端约定解析。");
  assert(application::AgentWorkspacePrompt("", true) ==
         "当前工作区: ~\n当前是 SSH 远端或终端提供者模式：所有命令作用于远端主机上"
         "的工作区，文件路径按远端约定解析。");
  assert(application::AgentWorkspacePrompt("", false) ==
         "当前工作区: .\n所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 "
         "API key、token、密码等敏感数据。");

  assert(application::AgentScopePrompt("explore", {"src"}, {"ignored"},
                                       false) ==
         "## Agent 范围\nread_scope: src\nwrite_scope: ignored\n"
         "这是 explore Agent，write_scope 必须视为无效，禁止任何写入。");
  assert(application::AgentScopePrompt("sub-coding", {"src"}, {}, false) ==
         "## Agent 范围\nread_scope: src\nwrite_scope: 未声明\n"
         "没有授权写入范围。禁止写入文件；如果任务需要修改文件，直接说明需要主模型重"
         "新分配 write_scope。");
  assert(application::AgentScopePrompt("sub-coding", {}, {"src"}, false)
             .find("只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把多个 "
                   "Agent 的职责混到同一个文件里。") != std::string::npos);
  assert(application::AgentScopePrompt("sub-coding", {}, {"src"}, true)
             .find("\n注意：所有路径都是远端主机上的路径；写入/读取都要通过 "
                   "shell_execute 调用 sed/awk/python heredoc/cat 等命令，不要尝试"
                   "调用本地 file 类工具。") != std::string::npos);

  assert(application::NormalizeAgentType(" sub_coding ") == "sub-coding");
  assert(application::NormalizeAgentType("SUBCODING") == "sub-coding");
  assert(application::NormalizeAgentType("Coding") == "sub-coding");
  assert(application::NormalizeAgentType("EXPLORE") == "explore");
  assert(application::NormalizeAgentType("weird") == "weird");
  assert(application::IsExploreAgentType("explore"));
  assert(!application::IsExploreAgentType("Explore"));

  application::AgentRunResults results;
  results.Put("a", application::AgentRunResult{
                       .output = "OUTPUT-A", .tool_call_count = 0, .error = false});
  results.Put("b", application::AgentRunResult{
                       .output = "OUTPUT-B", .tool_call_count = 2, .error = false});
  assert(results.size() == 2U);
  assert(results.Find("a") != nullptr && results.Find("a")->output == "OUTPUT-A");
  assert(results.Find("missing") == nullptr);
  assert(application::AgentDependencyOutputContext(MakeAgent("c", "TASK-C"),
                                                   results)
             .empty());
  assert(application::AgentDependencyOutputContext(
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
  assert(compact.find("\"linecode_agent_ref\":true") != std::string::npos);
  assert(compact.find("\"agent_id\":\"ag_1_1\"") != std::string::npos);
  assert(compact.find("\"status\":\"done\"") != std::string::npos);
  assert(compact.find("\"tool_call_id\":\"call-1\"") != std::string::npos);
  assert(compact.find("preview") != std::string::npos);
  assert(compact.find("the full transcript") == std::string::npos);

  const auto allocated = sink.AllocateId();
  assert(allocated.starts_with("ag_"));
  assert(sink.AllocateId() != allocated);
}

// ---------------------------------------------------------------------------
// Tool trimming + prompt assembly
// ---------------------------------------------------------------------------

void ExploreSeesReadToolsOnly() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("explore done")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "summarize the file tool", "Explore the repository", false,
        {"src", "include"}));
    assert(!result.error);

    assert(harness->gateway->requests.size() == 1U);
    const auto &request = harness->gateway->requests.front();
    // Legacy `defaultAgentAllowedCategories("explore")` = {READ}: only
    // READ-class tools survive and the dispatch tools never do.
    assert(ToolNames(request.tools) ==
           std::vector<std::string>({"file_read", "glob", "list_dir",
                                     "image_understanding", "web_search"}));

    const auto &system = SystemPrompt(request);
    assert(system.starts_with(
        "You are a code exploration Agent. Your task is to quickly locate and "
        "analyze code, and answer the user's questions."));
    assert(system.find("你的任务: Explore the repository") != std::string::npos);
    assert(system.find("当前工作区: /workspace") != std::string::npos);
    assert(system.find("## Agent 范围") != std::string::npos);
    assert(system.find("read_scope: src, include") != std::string::npos);
    assert(system.find("write_scope: 未声明") != std::string::npos);
    assert(system.find(
               "这是 explore Agent，write_scope 必须视为无效，禁止任何写入。") !=
           std::string::npos);
    assert(system.find("#### Skill: Demo") != std::string::npos);
    assert(system.find("## Available tools") != std::string::npos);
    assert(system.find("- file_read: file_read description") !=
           std::string::npos);
    assert(system.find("Permission mode: automatic.") != std::string::npos);
    assert(UserPrompt(request) == "summarize the file tool");
    co_return;
  });
  assert(harness->sink->size() == 1U);
  assert(harness->sink->records().front().output == "explore done");
  assert(harness->sink->records().front().status == "done");
}

void SubCodingSeesReadAndWriteTools() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("coding done")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK: edit the file", "Edit one file", false, {"src"},
        {"src"}));
    assert(!result.error);
    const auto &request = harness->gateway->requests.front();
    // Legacy READ + WRITE set: the file_ops READ/WRITE members, the READ
    // image/web tools and `shell_execute` through its read-only opt-in
    // (line 835).
    assert(ToolNames(request.tools) ==
           std::vector<std::string>({"file_read", "file_write", "file_edit",
                                     "glob", "list_dir", "image_understanding",
                                     "web_search", "shell_execute"}));
    const auto &system = SystemPrompt(request);
    assert(system.starts_with("You are a coding Agent. Your task is to complete "
                              "well-scoped coding subtasks."));
    assert(system.find("只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把"
                       "多个 Agent 的职责混到同一个文件里。") != std::string::npos);
    assert(system.find("write_scope: src") != std::string::npos);
    co_return;
  });
}

void RemoteModeSelectsTheRemoteRoleAndScope() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("remote done")};
  harness->runner = harness->MakeRunner(
      {}, application::SubAgentEnvironment{.workspace_path = "/srv/app",
                                           .remote_mode = true,
                                           .permission_mode = "confirm"});
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK", "Remote edit", false, {}, {"src"}));
    assert(!result.error);
    const auto &system = SystemPrompt(harness->gateway->requests.front());
    assert(system.starts_with(
        "You are a coding Agent in a remote Shell environment"));
    assert(system.find("Permission mode: confirmation.") != std::string::npos);
    assert(system.find("当前是 SSH 远端或终端提供者模式") != std::string::npos);
    co_return;
  });
}

// ---------------------------------------------------------------------------
// Model loop
// ---------------------------------------------------------------------------

void ToolLoopFeedsResultsBackAndCountsCalls() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {
      ToolCallResponse("call-9", "file_read", R"({"file_path":"src/main.cpp"})"),
      TextResponse("final answer"),
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "read the entry point", "Read main", false, {"src"}));
    assert(!result.error);
    assert(result.tool_call_count == 1);
    assert(harness->gateway->requests.size() == 2U);
    const auto &second = harness->gateway->requests[1];
    assert(second.messages.size() == 4U);
    assert(second.messages[2].role == application::CompletionRole::assistant);
    assert(second.messages[2].tool_calls.size() == 1U);
    assert(second.messages[3].role == application::CompletionRole::tool);
    assert(second.messages[3].tool_result);
    assert(second.messages[3].tool_result->call_id == "call-9");
    assert(second.messages[3].tool_result->content == "tool-result:file_read");
    assert(!second.messages[3].tool_result->error);
    assert(harness->tools->invoked.size() == 1U);
    assert(harness->tools->invoked.front().name == "file_read");
    assert(harness->tools->invoked.front().arguments_json ==
           R"({"file_path":"src/main.cpp"})");
    // The model-visible content is the compact ref, not the transcript.
    assert(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    co_return;
  });
  assert(harness->sink->size() == 1U);
  const auto &record = harness->sink->records().front();
  assert(record.status == "done");
  assert(record.output == "final answer");
  assert(record.tool_call_count == 1);
  assert(!record.error);
  assert(record.tool_call_id == "call-1");
  assert(record.preview == "final answer");
}

void WriteScopeGuardsEveryWriteCall() {
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
    assert(!result.error);
    assert(result.tool_call_count == 2);
    assert(harness->tools->invoked.size() == 1U);
    const auto &rejected = harness->gateway->requests.back().messages[5];
    assert(rejected.role == application::CompletionRole::tool);
    assert(rejected.tool_result);
    assert(rejected.tool_result->content ==
           "Agent 写入路径超出 write_scope: docs/readme.md\n允许写入范围: src\n"
           "请停止写入并让主模型重新分配。");
    assert(rejected.tool_result->error);
    co_return;
  });

  auto without_scope = std::make_shared<Harness>();
  without_scope->gateway->script = {write_call("src/main.cpp"),
                                    TextResponse("ok")};
  RunScenario([without_scope](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await without_scope->runner->RunAgent(
        without_scope->AgentRequest("sub-coding", "TASK", "Write without scope",
                                    false, {}, {}));
    assert(!result.error);
    assert(without_scope->tools->invoked.empty());
    const auto &rejected =
        without_scope->gateway->requests.back().messages[3];
    assert(rejected.tool_result);
    assert(rejected.tool_result->content ==
           "Agent 未声明 write_scope，禁止写入文件。请让主模型重新分配明确的写入"
           "范围。");
    co_return;
  });

  auto explore = std::make_shared<Harness>();
  explore->gateway->script = {write_call("src/main.cpp"), TextResponse("ok")};
  RunScenario([explore](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await explore->runner->RunAgent(explore->AgentRequest(
        "explore", "TASK", "Try to write", false, {}, {"src"}));
    assert(!result.error);
    assert(explore->tools->invoked.empty());
    const auto &rejected = explore->gateway->requests.back().messages[3];
    assert(rejected.tool_result);
    assert(rejected.tool_result->content == "Agent 不允许调用此工具: file_write");
    co_return;
  });
}

void ToolLimitStopsTheLoopWithTheLegacyMessage() {
  auto harness = std::make_shared<Harness>();
  harness->models->model.tool_call_limit = 1;
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "TASK", "Budget limited", false, {}));
    assert(result.error);
    assert(result.tool_call_count == 1);
    // The budget check runs before the next model turn.
    assert(harness->gateway->requests.size() == 1U);
    // The model only sees the compact ref; the legacy tool-limit message is
    // the recorded output of the failed run.
    assert(harness->sink->records().front().output ==
           std::string{application::kAgentToolLimitMessage} + "\n");
    co_return;
  });
  assert(harness->runner->budget().limit() == 1);
  assert(harness->runner->budget().used() == 1);
  assert(harness->sink->records().front().status == "error");
  assert(harness->sink->records().front().output ==
         std::string{application::kAgentToolLimitMessage} + "\n");
}

void CancellationReturnsTheTerminatedResult() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})"),
      TextResponse("never reached"),
  };
  const auto runner = harness->runner;
  harness->gateway->before_response =
      [runner](const Request &, std::size_t) -> huxerui::Task<void> {
    runner->RequestStop();
    co_return;
  };
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "TASK", "Cancelled run", false, {}));
    assert(result.error);
    assert(result.tool_call_count == 0);
    assert(harness->tools->invoked.empty());
    assert(harness->sink->records().front().output ==
           std::string{application::kAgentTerminatedMessage});
    co_return;
  });
  assert(harness->sink->records().front().status == "error");
  assert(harness->sink->records().front().output ==
         std::string{application::kAgentTerminatedMessage});
}

void MissingModelIsReportedBeforeAnyLoop() {
  auto harness = std::make_shared<Harness>();
  harness->models->selected.clear();
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(
        harness->AgentRequest("explore", "TASK", "No model", false, {}));
    assert(result.error);
    assert(result.output == "当前没有可用模型，无法运行 Agent。");
    assert(harness->gateway->requests.empty());
    assert(harness->sink->size() == 0U);
    co_return;
  });
}

// ---------------------------------------------------------------------------
// Async explore
// ---------------------------------------------------------------------------

void AsyncExploreReturnsTheRunningRefImmediately() {
  auto harness = std::make_shared<Harness>();
  harness->gateway->script = {TextResponse("background output")};
  harness->runner = harness->MakeRunner(harness->launcher);
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "explore", "TASK", "Async explore", true, {"src"}));
    assert(!result.error);
    assert(result.tool_call_count == 0);
    // The ref is the *running* record and the loop has not started yet.
    assert(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    assert(result.output.find("\"status\":\"running\"") != std::string::npos);
    assert(result.output.find("\"async\":true") != std::string::npos);
    assert(harness->gateway->requests.empty());
    assert(harness->launcher->pending_count() == 1U);
    assert(harness->sink->records().front().status == "running");
    const auto agent_id = harness->sink->records().front().agent_id;
    assert(!agent_id.empty());

    // The background child finishes later and updates the same record.
    co_await harness->launcher->Drain();
    assert(harness->gateway->requests.size() == 1U);
    const auto *record = harness->sink->Find(agent_id);
    assert(record != nullptr);
    assert(record->status == "done");
    assert(record->output == "background output");
    assert(record->async);
    co_return;
  });
}

void AsyncSubCodingIsRejected() {
  auto harness = std::make_shared<Harness>();
  harness->runner = harness->MakeRunner(harness->launcher);
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    auto result = co_await harness->runner->RunAgent(harness->AgentRequest(
        "sub-coding", "TASK", "Async coding", true, {}, {"src"}));
    assert(result.error);
    assert(result.output == "async=true is not allowed for sub-coding agents.");
    assert(harness->launcher->pending_count() == 0U);
    assert(harness->gateway->requests.empty());
    co_return;
  });
}

// ---------------------------------------------------------------------------
// Pipeline
// ---------------------------------------------------------------------------

void PipelineFeedsUpstreamOutputIntoDownstreamPrompts() {
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
    auto result = co_await harness->runner->RunAgentPipeline(std::move(request));
    assert(!result.error);
    assert(result.tool_call_count == 0);
    assert(result.output.find("\"linecode_agent_ref\":true") !=
           std::string::npos);
    assert(result.output.find("\"type\":\"pipeline\"") != std::string::npos);
    assert(harness->gateway->requests.size() == 2U);

    // Level order: the dependency-free agent runs first.
    assert(UserPrompt(harness->gateway->requests[0]).starts_with("TASK-A"));
    const auto &downstream = UserPrompt(harness->gateway->requests[1]);
    assert(downstream ==
           "TASK-B\n\n## 上游 Agent 输出\n\n### a\nOUTPUT-A\n"
           "\n请基于以上结果继续你的任务。");
    co_return;
  });

  // Every stage plus the pipeline itself is recorded.
  assert(harness->sink->size() == 3U);
  const auto *first = harness->sink->Find("a");
  const auto *second = harness->sink->Find("b");
  assert(first != nullptr && first->status == "done" &&
         first->output == "OUTPUT-A");
  assert(second != nullptr && second->status == "done" &&
         second->output == "OUTPUT-B");
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  assert(pipeline != nullptr);
  assert(pipeline->type == "pipeline");
  assert(pipeline->description == "2 agents");
  assert(pipeline->status == "done");
  assert(!pipeline->error);
  assert(pipeline->output.starts_with("Agent pipeline completed: 2 个任务"));
  assert(pipeline->output.find("\n\n## a · task a\n类型: explore\n状态: done\n"
                               "工具调用: 0\nOUTPUT-A") != std::string::npos);
  assert(pipeline->output.find("\n\n## b · task b\n类型: sub-coding\n状态: done\n"
                               "工具调用: 0\nOUTPUT-B") != std::string::npos);
  assert(pipeline->output.ends_with("\n\n总工具调用: 0"));
}

void SameLevelAgentsRunConcurrently() {
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
  RunScenario([harness, order](huxerui::TaskScope scope) -> huxerui::Task<void> {
    // The real TaskScope launcher: the level fans out into child tasks.
    harness->runner = harness->MakeRunner(
        std::make_shared<application::TaskScopeSubAgentLauncher>(
            std::move(scope)));
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"), MakeAgent("b", "TASK-B")};
    auto result = co_await harness->runner->RunAgentPipeline(std::move(request));
    assert(!result.error);
    assert(harness->gateway->requests.size() == 2U);
    assert(order->size() == 4U);
    // Both agents entered their model turn before either finished, which a
    // sequential level cannot produce.
    assert((*order)[0] == "start-A");
    assert((*order)[1] == "start-B");
    co_return;
  });
  assert(harness->sink->Find("a") != nullptr);
  assert(harness->sink->Find("b") != nullptr);
}

void FailedStageIsRecordedAndPropagates() {
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
    auto result = co_await harness->runner->RunAgentPipeline(std::move(request));
    // The error propagates to the pipeline result...
    assert(result.error);
    // ...and the next level still runs, mirroring the legacy `hasError` flow.
    assert(harness->gateway->requests.size() == 2U);
    assert(UserPrompt(harness->gateway->requests[1])
               .find("Agent 模型通信失败：\nnetwork down") != std::string::npos);
    co_return;
  });
  const auto *failed = harness->sink->Find("a");
  assert(failed != nullptr && failed->status == "error" && failed->error);
  assert(failed->output == "Agent 模型通信失败：\nnetwork down");
  const auto *recovered = harness->sink->Find("b");
  assert(recovered != nullptr && recovered->status == "done");
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  assert(pipeline != nullptr && pipeline->error);
  assert(pipeline->output.find("状态: error") != std::string::npos);
  assert(pipeline->output.find("状态: done") != std::string::npos);
}

void PlanPipelineErrorsBecomeToolContent() {
  auto harness = std::make_shared<Harness>();
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest empty;
    auto empty_result = co_await harness->runner->RunAgentPipeline(empty);
    assert(empty_result.error);
    assert(empty_result.output == "agent_pipeline.agents 不能为空。");

    application::AgentPipelineRunRequest self;
    self.agents = {MakeAgent("a", "TASK-A", "explore", {"a"})};
    auto self_result = co_await harness->runner->RunAgentPipeline(self);
    assert(self_result.error);
    assert(self_result.output == "Agent 不能依赖自身: a");

    application::AgentPipelineRunRequest unknown;
    unknown.agents = {MakeAgent("a", "TASK-A", "explore", {"ghost"})};
    auto unknown_result = co_await harness->runner->RunAgentPipeline(unknown);
    assert(unknown_result.error);
    assert(unknown_result.output ==
           "Agent 流水线存在循环依赖或重复 id，无法执行。");

    application::AgentPipelineRunRequest cycle;
    cycle.agents = {MakeAgent("a", "TASK-A", "explore", {"b"}),
                    MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto cycle_result = co_await harness->runner->RunAgentPipeline(cycle);
    assert(cycle_result.error);
    assert(cycle_result.output ==
           "Agent 流水线存在循环依赖或重复 id，无法执行。");

    // No agent ever ran.
    assert(harness->gateway->requests.empty());
    assert(harness->sink->size() == 0U);
    co_return;
  });
}

void PipelineLimitStopsTheWholeRun() {
  auto harness = std::make_shared<Harness>();
  harness->models->model.tool_call_limit = 1;
  harness->gateway->script = {
      ToolCallResponse("call-1", "file_read", R"({"file_path":"a"})")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result = co_await harness->runner->RunAgentPipeline(std::move(request));
    assert(result.error);
    // The shared main-flow budget stops the pipeline after the first agent.
    assert(harness->gateway->requests.size() == 1U);
    co_return;
  });
  const auto *pipeline =
      harness->sink->Find(harness->sink->records().back().agent_id);
  assert(pipeline != nullptr && pipeline->error);
  assert(pipeline->output.starts_with(
      "Agent 流水线因工具调用次数达到主流程上限，已提前结束：\n"));
  assert(pipeline->output.find(application::kAgentToolLimitMessage) !=
         std::string::npos);
}

void PipelineCancellationReturnsTheTerminatedMessage() {
  auto harness = std::make_shared<Harness>();
  const auto runner = harness->runner;
  harness->gateway->before_response =
      [runner](const Request &, std::size_t) -> huxerui::Task<void> {
    runner->RequestStop();
    co_return;
  };
  harness->gateway->script = {TextResponse("never")};
  RunScenario([harness](huxerui::TaskScope) -> huxerui::Task<void> {
    application::AgentPipelineRunRequest request;
    request.agents = {MakeAgent("a", "TASK-A"),
                      MakeAgent("b", "TASK-B", "explore", {"a"})};
    auto result = co_await harness->runner->RunAgentPipeline(std::move(request));
    assert(result.error);
    const auto *pipeline =
        harness->sink->Find(harness->sink->records().back().agent_id);
    assert(pipeline != nullptr && pipeline->error);
    // The cancelled level ends the pipeline before the next level starts.
    assert(pipeline->output == "Agent 流水线已终止。");
    co_return;
  });
}

} // namespace

int main() {
  DependencyContextMatchesLegacyWording();
  ExploreSeesReadToolsOnly();
  SubCodingSeesReadAndWriteTools();
  RemoteModeSelectsTheRemoteRoleAndScope();
  ToolLoopFeedsResultsBackAndCountsCalls();
  WriteScopeGuardsEveryWriteCall();
  ToolLimitStopsTheLoopWithTheLegacyMessage();
  CancellationReturnsTheTerminatedResult();
  MissingModelIsReportedBeforeAnyLoop();
  AsyncExploreReturnsTheRunningRefImmediately();
  AsyncSubCodingIsRejected();
  PipelineFeedsUpstreamOutputIntoDownstreamPrompts();
  SameLevelAgentsRunConcurrently();
  FailedStageIsRecordedAndPropagates();
  PlanPipelineErrorsBecomeToolContent();
  PipelineLimitStopsTheWholeRun();
  PipelineCancellationReturnsTheTerminatedMessage();
  std::cout << "sub-agent runner tests passed\n";
}
