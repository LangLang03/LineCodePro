#include <cassert>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/mcp_completion_loop.h"
#include "application/mcp_extension_tool_registry.h"
#include "application/tool_permission_service.h"
#include "infrastructure/json_mcp_tool_schema_policy.h"

namespace {

using namespace linecode;

class LoopStore final : public application::McpExtensionStore {
public:
  huxerui::Task<
      application::ExtensionStoreResult<std::vector<domain::McpExtension>>>
  ListMcps() override {
    co_return std::vector<domain::McpExtension>{domain::McpExtension{
        .id = "server-1",
        .enabled = true,
        .name = "Parity MCP",
        .url = "https://mcp.example/rpc",
        .request_headers = {},
        .tools = {{.name = "echo",
                   .enabled = true,
                   .description = "Echo",
                   .input_schema_json = R"({"type":"object"})"}},
        .created_at = 0,
        .updated_at = 0,
    }};
  }
  huxerui::Task<
      application::ExtensionStoreResult<std::optional<domain::McpExtension>>>
  FindMcp(std::string) override {
    co_return std::optional<domain::McpExtension>{};
  }
  huxerui::Task<application::ExtensionStoreResult<domain::McpExtension>>
  SaveMcp(domain::McpExtension value) override {
    co_return value;
  }
  huxerui::Task<application::ExtensionStoreResult<void>>
  SetMcpEnabled(std::string, bool) override {
    co_return application::ExtensionStoreResult<void>{};
  }
  huxerui::Task<application::ExtensionStoreResult<void>>
  DeleteMcps(std::vector<std::string>) override {
    co_return application::ExtensionStoreResult<void>{};
  }
};

class LoopInvoker final : public application::McpToolInvoker {
public:
  huxerui::Task<std::expected<application::McpToolInvocationResult,
                              application::McpToolInvocationError>>
  Invoke(domain::McpExtension extension, domain::McpToolSummary tool,
         std::string arguments) override {
    extension_names.push_back(std::move(extension.name));
    tool_names.push_back(std::move(tool.name));
    arguments_json.push_back(std::move(arguments));
    co_return application::McpToolInvocationResult{
        .content = "fixed tool result", .error = false};
  }

  std::vector<std::string> extension_names;
  std::vector<std::string> tool_names;
  std::vector<std::string> arguments_json;
};

class LoopCompletion final : public application::CompletionGateway {
public:
  huxerui::Task<std::expected<application::CompletionResponse,
                              application::CompletionError>>
  Complete(application::CompletionRequest request,
           application::CompletionObserver) override {
    requests.push_back(std::move(request));
    if (requests.size() == 1U) {
      co_return application::CompletionResponse{
          .text = {},
          .reasoning_content = {},
          .tool_calls = {{.id = "call-1",
                          .name = "mcpx_42svxm_echo",
                          .arguments_json = R"({"text":"hello"})"}},
          .input_tokens = 1,
          .output_tokens = 1,
      };
    }
    co_return application::CompletionResponse{
        .text = "final answer",
        .reasoning_content = {},
        .tool_calls = {},
        .input_tokens = 2,
        .output_tokens = 3,
    };
  }

  std::vector<application::CompletionRequest> requests;
};

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

class RecordingRequestComposer final
    : public application::CompletionRequestComposer {
public:
  huxerui::Task<application::SettingsResult<application::CompletionRequest>>
  Compose(application::CompletionRequest request,
          application::PromptAssemblyContext context) override {
    observed_tool_count = request.tools.size();
    observed_work_directory = std::move(context.work_directory);
    request.messages.insert(request.messages.begin(),
                            application::CompletionMessage{
                                .role = application::CompletionRole::system,
                                .content = "composed after tools",
                            });
    co_return request;
  }

  std::size_t observed_tool_count{};
  std::string observed_work_directory;
};

struct Scenario final {
  std::shared_ptr<LoopCompletion> completion;
  std::shared_ptr<LoopInvoker> invoker;
  std::shared_ptr<MemorySettings> settings;
  std::shared_ptr<application::ToolPermissionService> permissions;
  std::shared_ptr<RecordingRequestComposer> composer;
  std::shared_ptr<application::McpCompletionLoop> loop;
  std::optional<std::expected<application::CompletionResponse,
                              application::CompletionError>>
      result;
  bool done{};
  std::size_t review_count{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      application::CompletionRequest request;
      request.model.model_id = "test-model";
      request.model.tool_call_limit = 2;
      request.messages.push_back({.role = application::CompletionRole::user,
                                  .content = "use the tool",
                                  .tool_calls = {},
                                  .tool_result = std::nullopt});
      request.tools = {};
      request.stream = false;
      request.permission_scope = "/workspace";
      assert(co_await scenario->permissions->SetMode(
          domain::ToolPermissionMode::confirm));
      scenario->result = co_await scenario->loop->Complete(
          request,
          application::PromptAssemblyContext{
              .chat_mode = application::PromptChatMode::agent,
              .work_directory = "/workspace",
              .linecode_root = {},
              .global_skills_root = {},
              .workspace_private_root = {},
              .workspace_skills_root = {},
              .learning_context = {},
              .todo_state = {},
              .permission_mode = "auto",
              .tools_context = {},
              .attachment_history = {},
          },
          application::CompletionObserver{
              .on_event = {},
              .on_text_delta = {},
              .on_tool_review = [scenario](auto review)
                  -> huxerui::Task<
                      application::CompletionObserver::ToolReviewDecision> {
                ++scenario->review_count;
                assert(review.call.name == "mcpx_42svxm_echo");
                assert(!review.can_allow_always);
                co_return application::CompletionObserver::ToolReviewDecision::
                    allow_once;
              },
          });
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("mcp-loop-probe");
}

} // namespace

int main() {
  auto store = std::make_shared<LoopStore>();
  auto invoker = std::make_shared<LoopInvoker>();
  auto completion = std::make_shared<LoopCompletion>();
  auto registry = std::make_shared<application::McpExtensionToolRegistry>(
      store, invoker,
      std::make_shared<infrastructure::JsonMcpToolSchemaPolicy>());
  active = std::make_shared<Scenario>();
  active->completion = completion;
  active->invoker = invoker;
  active->settings = std::make_shared<MemorySettings>();
  active->permissions =
      std::make_shared<application::ToolPermissionService>(active->settings);
  active->composer = std::make_shared<RecordingRequestComposer>();
  active->loop = std::make_shared<application::McpCompletionLoop>(
      completion, registry, active->permissions, active->composer);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });

  assert(active->result && active->result->has_value());
  assert((*active->result)->text == "final answer");
  assert(completion->requests.size() == 2U);
  assert(completion->requests[0].tools.size() == 1U);
  assert(completion->requests[0].tools[0].name == "mcpx_42svxm_echo");
  assert(completion->requests[0].messages.front().role ==
         application::CompletionRole::system);
  assert(active->composer->observed_tool_count == 1U);
  assert(active->composer->observed_work_directory == "/workspace");
  assert(completion->requests[1].messages.size() == 4U);
  assert(completion->requests[1].messages[2].tool_calls.size() == 1U);
  assert(completion->requests[1].messages[3].tool_result);
  assert(completion->requests[1].messages[3].tool_result->content ==
         "fixed tool result");
  assert(invoker->extension_names == std::vector<std::string>{"Parity MCP"});
  assert(invoker->tool_names == std::vector<std::string>{"echo"});
  assert(invoker->arguments_json ==
         std::vector<std::string>{R"({"text":"hello"})"});
  assert(active->review_count == 1U);
  active.reset();
}
