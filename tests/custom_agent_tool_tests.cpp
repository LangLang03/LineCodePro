// Contract tests for the custom Agent extension tool: the naming rules that
// make it addressable, the prompt the model sees, and the delegation it hands
// to the sub-agent runner.

#include <cassert>
#include <cstdint>
#include <iostream>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/testing/ui_test.h>

#include "application/agent_extension_tool_registry.h"
#include "application/custom_agent_tool.h"
#include "application/ports/agent_runner.h"
#include "application/ports/extension_store.h"
#include "domain/extension_config.h"

namespace {

using namespace linecode;

using application::AgentExtensionToolRegistry;
using application::AgentRunRequest;
using application::AgentRunResult;
using application::AgentRunner;
using application::BuildCustomAgentPrompt;
using application::CustomAgentToolDescription;
using application::CustomAgentToolName;
using application::CustomAgentToolSchemaJson;
using application::ExtensionStoreError;
using application::ExtensionStoreResult;
using application::SafeCustomToolNamePart;

domain::AgentExtension Agent() {
  domain::AgentExtension agent;
  agent.id = "agent-1";
  agent.enabled = true;
  agent.name = "Code Reviewer";
  agent.slug = "code-reviewer";
  agent.prompt = "Review the diff and report findings.";
  agent.trigger = "when a review is requested";
  agent.tool_names = {"file_read", "glob"};
  agent.mcp_ids = {"mcp_1"};
  return agent;
}

// ---------------------------------------------------------------------------
// Naming rules (`ToolRegistry.safeToolNamePart` / `customAgentToolName`)
// ---------------------------------------------------------------------------

void SafeNamesMatchTheLegacyRules() {
  // Allowed characters survive untouched.
  assert(SafeCustomToolNamePart("code-reviewer", "agent", 55) ==
         "code-reviewer");
  // Anything else becomes an underscore, runs collapse, the ends are trimmed.
  assert(SafeCustomToolNamePart("  Code Reviewer!  ", "agent", 55) ==
         "Code_Reviewer");
  assert(SafeCustomToolNamePart("a..b", "agent", 55) == "a_b");
  assert(SafeCustomToolNamePart("__x__", "agent", 55) == "x");
  // An empty or all-punctuation slug falls back.
  assert(SafeCustomToolNamePart("", "agent", 55) == "agent");
  assert(SafeCustomToolNamePart("!!!", "agent", 55) == "agent");
  // A name that does not start with a letter is prefixed with the fallback.
  assert(SafeCustomToolNamePart("9lives", "agent", 55) == "agent_9lives");
  // The length cap applies to the kept characters, not the input.
  assert(SafeCustomToolNamePart(std::string(80, 'a'), "agent", 55).size() ==
         55U);
}

void ToolNamesUseTheLegacyPrefix() {
  assert(CustomAgentToolName("code-reviewer") == "agentx_code-reviewer");
  // A slug that sanitizes to nothing still yields an addressable name.
  assert(CustomAgentToolName("") == "agentx_agent");
}

// ---------------------------------------------------------------------------
// Prompt and description
// ---------------------------------------------------------------------------

void PromptMatchesTheLegacyShape() {
  const auto agent = Agent();
  const auto prompt = BuildCustomAgentPrompt(agent, "look at src/", "");
  assert(prompt.starts_with("You are the custom Agent \"Code Reviewer\" "
                            "(code-reviewer).\n\n"));
  assert(prompt.find("## Agent Definition\nReview the diff and report "
                     "findings.\n\n") != std::string::npos);
  assert(prompt.find("## Trigger\nwhen a review is requested\n\n") !=
         std::string::npos);
  // The legacy spliced the collection's `toString()`, i.e. its bracketed form.
  assert(prompt.find("## Expected Tool Scope\n[file_read, glob]\n\n") !=
         std::string::npos);
  assert(prompt.find("## Expected MCP Scope\n[mcp_1]\n\n") !=
         std::string::npos);
  assert(prompt.ends_with("## Current Task\nlook at src/"));
  assert(prompt.find("Supplementary Context") == std::string::npos);

  // Supplementary context is trimmed and appended last.
  const auto with_context =
      BuildCustomAgentPrompt(agent, "task", "  extra  ");
  assert(with_context.ends_with("## Supplementary Context\nextra"));
  // Blank context adds nothing.
  assert(BuildCustomAgentPrompt(agent, "task", "   ") ==
         BuildCustomAgentPrompt(agent, "task", ""));
}

void PromptOmitsEmptySections() {
  auto agent = Agent();
  agent.trigger.clear();
  agent.tool_names.clear();
  agent.mcp_ids.clear();
  const auto prompt = BuildCustomAgentPrompt(agent, "task", "");
  assert(prompt.find("## Trigger") == std::string::npos);
  assert(prompt.find("Expected Tool Scope") == std::string::npos);
  assert(prompt.find("Expected MCP Scope") == std::string::npos);
  assert(prompt.ends_with("## Current Task\ntask"));
}

void DescriptionCapsTheCapabilityExcerpt() {
  auto agent = Agent();
  agent.trigger.clear();
  agent.prompt = std::string(1200, 'p');
  const auto description = CustomAgentToolDescription(agent);
  assert(description.starts_with("Invoke the custom Agent \"Code Reviewer\"."));
  // `substring(0, 900)` on the raw prompt, untrimmed.
  assert(description == "Invoke the custom Agent \"Code Reviewer\"."
                        "\nCapabilities: " +
                            std::string(900, 'p'));

  agent.trigger = "trig";
  const auto with_trigger = CustomAgentToolDescription(agent);
  assert(with_trigger.find("\nTrigger: trig\nCapabilities: ") !=
         std::string::npos);
}

void SchemaRequiresOnlyTheTask() {
  const auto schema = CustomAgentToolSchemaJson();
  assert(schema.find("\"required\":[\"task\"]") != std::string::npos);
  for (const auto key : {"\"task\"", "\"context\"", "\"read_scope\"",
                         "\"write_scope\""}) {
    assert(schema.find(key) != std::string::npos);
  }
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

class FakeExtensionStore final : public application::AgentExtensionStore {
public:
  [[nodiscard]] huxerui::Task<
      ExtensionStoreResult<std::vector<domain::AgentExtension>>>
  ListAgents() override {
    co_return agents;
  }
  [[nodiscard]] huxerui::Task<
      ExtensionStoreResult<std::optional<domain::AgentExtension>>>
  FindAgent(std::string id) override {
    for (const auto &agent : agents) {
      if (agent.id == id)
        co_return std::optional<domain::AgentExtension>{agent};
    }
    co_return std::optional<domain::AgentExtension>{};
  }
  [[nodiscard]] huxerui::Task<ExtensionStoreResult<domain::AgentExtension>>
  SaveAgent(domain::AgentExtension value) override {
    co_return value;
  }
  [[nodiscard]] huxerui::Task<ExtensionStoreResult<void>>
  SetAgentEnabled(std::string, bool) override {
    co_return ExtensionStoreResult<void>{};
  }
  [[nodiscard]] huxerui::Task<ExtensionStoreResult<void>>
  DeleteAgents(std::vector<std::string>) override {
    co_return ExtensionStoreResult<void>{};
  }

  std::vector<domain::AgentExtension> agents;
};

class FakeAgentRunner final : public AgentRunner {
public:
  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunAgent(AgentRunRequest request) override {
    requests.push_back(std::move(request));
    co_return result;
  }
  [[nodiscard]] huxerui::Task<AgentRunResult>
  RunAgentPipeline(application::AgentPipelineRunRequest) override {
    co_return result;
  }

  std::vector<AgentRunRequest> requests;
  AgentRunResult result{.output = "agent output", .tool_call_count = 2};
};

struct Harness final {
  std::shared_ptr<FakeExtensionStore> store =
      std::make_shared<FakeExtensionStore>();
  std::shared_ptr<FakeAgentRunner> runner = std::make_shared<FakeAgentRunner>();
  std::shared_ptr<AgentExtensionToolRegistry> registry;
  bool refreshed{};
  std::optional<application::ToolInvocationResult> invoked;
  std::optional<application::ToolRegistryError> invoke_error;
  bool done{};
  // Set to invoke a tool after the refresh instead of only refreshing.
  std::string invoke_name;
  std::string invoke_arguments;

  Harness() {
    registry = std::make_shared<AgentExtensionToolRegistry>(store, runner);
  }
};

std::shared_ptr<Harness> harness;

huxerui::View RegistryProbe() {
  const auto current = harness;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    if (current->done)
      return;
    tasks.Launch([current]() -> huxerui::Task<void> {
      auto refreshed = co_await current->registry->Refresh();
      current->refreshed = static_cast<bool>(refreshed);
      if (!current->invoke_name.empty()) {
        auto invoked = co_await current->registry->Invoke(
            current->invoke_name, current->invoke_arguments);
        if (invoked)
          current->invoked = std::move(*invoked);
        else
          current->invoke_error = std::move(invoked.error());
      }
      current->done = true;
    });
  });
  return huxerui::Text("agent-extension-registry-probe");
}

void Run(Harness &target) {
  harness = std::make_shared<Harness>(target);
  const huxerui::Application application(RegistryProbe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  for (std::size_t frame = 0; frame < 20'000U && !harness->done; ++frame)
    ui.Pump(std::chrono::milliseconds{1});
  assert(harness->done);
  target.refreshed = harness->refreshed;
  target.invoked = harness->invoked;
  target.invoke_error = harness->invoke_error;
  harness.reset();
}

void OnlyEnabledAgentsBecomeTools() {
  Harness target;
  auto disabled = Agent();
  disabled.id = "agent-2";
  disabled.slug = "disabled-one";
  disabled.enabled = false;
  target.store->agents = {Agent(), disabled};
  Run(target);

  assert(target.refreshed);
  const auto tools = target.registry->Tools();
  assert(tools.size() == 1U);
  assert(tools.front().name == "agentx_code-reviewer");
  assert(tools.front().category == "agent");
  // Not opted into read-only mode, matching the legacy tool.
  assert(!tools.front().allowed_in_read_only);
  assert(target.registry->Contains("agentx_code-reviewer"));
  assert(!target.registry->Contains("agentx_disabled-one"));
}

// The delegated run has to carry the agent's own prompt, its selected tools
// and the forced `sub-coding` type (`CustomAgentExtensionTool.java:80-96`).
void InvokeDelegatesTheAgentsConfiguration() {
  Harness target;
  target.store->agents = {Agent()};
  target.invoke_name = "agentx_code-reviewer";
  target.invoke_arguments =
      R"json({"task":"look at src/","context":" extra ","read_scope":["src"],"write_scope":[]})json";
  Run(target);

  assert(target.invoked.has_value());
  assert(!target.invoked->error);
  assert(target.invoked->content == "agent output");
  assert(target.runner->requests.size() == 1U);
  const auto &request = target.runner->requests.front();
  assert(request.type == "sub-coding");
  assert(request.description == "Code Reviewer");
  assert(request.prompt.starts_with("You are the custom Agent \"Code "
                                    "Reviewer\" (code-reviewer)."));
  assert(request.prompt.ends_with("## Supplementary Context\nextra"));
  assert(request.read_scope == std::vector<std::string>{"src"});
  assert(request.write_scope.empty());
  // The agent's own selection travels with the run.
  assert(request.custom_tool_names ==
         (std::vector<std::string>{"file_read", "glob"}));
  assert(request.custom_mcp_ids == std::vector<std::string>{"mcp_1"});
}

void InvokeRejectsAnEmptyTask() {
  Harness target;
  target.store->agents = {Agent()};
  target.invoke_name = "agentx_code-reviewer";
  target.invoke_arguments = R"json({"task":"   "})json";
  Run(target);

  assert(target.invoked.has_value());
  assert(target.invoked->error);
  assert(target.invoked->content == "Custom Agent task cannot be empty.");
  // The runner is never reached.
  assert(target.runner->requests.empty());
}

void InvokeReportsAMissingRunner() {
  Harness target;
  target.store->agents = {Agent()};
  target.registry->SetRunner(nullptr);
  target.invoke_name = "agentx_code-reviewer";
  target.invoke_arguments = R"json({"task":"do it"})json";
  Run(target);

  assert(target.invoked.has_value());
  assert(target.invoked->error);
  assert(target.invoked->content ==
         "Agent runner not available, cannot run custom Agent.");
}

void InvokeRejectsAnUnknownTool() {
  Harness target;
  target.store->agents = {Agent()};
  target.invoke_name = "agentx_nope";
  target.invoke_arguments = R"json({"task":"do it"})json";
  Run(target);

  assert(target.invoke_error.has_value());
  assert(target.invoke_error->code ==
         application::ToolRegistryErrorCode::unknown_tool);
}

} // namespace

int main() {
  SafeNamesMatchTheLegacyRules();
  ToolNamesUseTheLegacyPrefix();
  PromptMatchesTheLegacyShape();
  PromptOmitsEmptySections();
  DescriptionCapsTheCapabilityExcerpt();
  SchemaRequiresOnlyTheTask();
  OnlyEnabledAgentsBecomeTools();
  InvokeDelegatesTheAgentsConfiguration();
  InvokeRejectsAnEmptyTask();
  InvokeReportsAMissingRunner();
  InvokeRejectsAnUnknownTool();
  std::cout << "custom_agent_tool_tests passed\n";
  return 0;
}
