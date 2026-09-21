#include "gtest_support.h"
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/agent_extension_draft.h"
#include "infrastructure/json_agent_extension_draft_codec.h"

namespace {

using namespace linecode;

domain::ModelConfig Model() {
  return {.id = "selected-model",
          .name = "Selected",
          .protocol = domain::ModelProtocol::openai_compatible,
          .provider_label = "OpenAI",
          .base_url = "https://models.example.test/v1",
          .api_key = "secret",
          .model_id = "draft-model",
          .tool_call_limit = domain::ModelConfig::default_tool_call_limit,
          .compression_model_enabled = false,
          .compression_model_auto = true,
          .compression_model_id = {},
          .context_size = domain::ModelConfig::context_size_unset};
}

class StubModels final : public application::ModelStore {
public:
  huxerui::Task<std::expected<std::vector<domain::ModelConfig>,
                               application::ModelStoreError>>
  List() override {
    co_return std::vector{model};
  }
  huxerui::Task<std::expected<std::optional<domain::ModelConfig>,
                               application::ModelStoreError>>
  Find(std::string id) override {
    co_return id == model.id ? std::optional{model} : std::nullopt;
  }
  huxerui::Task<std::expected<domain::ModelConfig,
                               application::ModelStoreError>>
  Save(domain::ModelConfig value) override {
    model = std::move(value);
    co_return model;
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

  domain::ModelConfig model{Model()};
  std::string selected{"selected-model"};
};

class StubCompletion final : public application::CompletionGateway {
public:
  huxerui::Task<std::expected<application::CompletionResponse,
                               application::CompletionError>>
  Complete(application::CompletionRequest value,
           application::CompletionObserver) override {
    request = std::move(value);
    co_return application::CompletionResponse{.text = response,
                                               .reasoning_content = {},
                                               .tool_calls = {},
                                               .input_tokens = 0,
                                               .output_tokens = 0};
  }

  std::optional<application::CompletionRequest> request;
  std::string response;
};

class StubTools final : public application::ToolRegistry {
public:
  huxerui::Task<std::expected<void, application::ToolRegistryError>>
  Refresh() override {
    refreshed = true;
    co_return std::expected<void, application::ToolRegistryError>{};
  }
  std::span<const application::RegisteredTool> Tools() const noexcept override {
    return tools;
  }
  huxerui::Task<std::expected<application::ToolInvocationResult,
                               application::ToolRegistryError>>
  Invoke(std::string, std::string) override {
    co_return application::ToolInvocationResult{};
  }

  bool refreshed{};
  std::vector<application::RegisteredTool> tools;
};

class StubMcps final : public application::McpExtensionStore {
public:
  huxerui::Task<application::ExtensionStoreResult<
      std::vector<domain::McpExtension>>>
  ListMcps() override {
    co_return mcps;
  }
  huxerui::Task<application::ExtensionStoreResult<
      std::optional<domain::McpExtension>>>
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

  std::vector<domain::McpExtension> mcps;
};

struct Scenario final {
  std::shared_ptr<StubModels> models{std::make_shared<StubModels>()};
  std::shared_ptr<StubCompletion> completion{
      std::make_shared<StubCompletion>()};
  std::shared_ptr<StubTools> tools{std::make_shared<StubTools>()};
  std::shared_ptr<StubMcps> mcps{std::make_shared<StubMcps>()};
  std::shared_ptr<application::CompletionAgentExtensionDraftGenerator> draft;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto context = co_await scenario->draft->LoadContext();
      EXPECT_EXPRESSION(context);
      EXPECT_EXPRESSION(scenario->tools->refreshed);
      EXPECT_EXPRESSION(context->tools.size() == 2U);
      EXPECT_EXPRESSION(context->tools[0].category == "file");
      EXPECT_EXPRESSION(context->tools[0].name == "file_read");
      EXPECT_EXPRESSION(context->tools[0].display_name == "读取文件");
      EXPECT_EXPRESSION(context->tools[0].display_description == "读取工作区文件");
      EXPECT_EXPRESSION(!context->tools[0].display_name.contains("file_read"));
      EXPECT_EXPRESSION(!context->tools[0].display_description.contains("read files"));
      EXPECT_EXPRESSION(context->mcps.size() == 1U);
      EXPECT_EXPRESSION(context->mcps[0].id == "custom:mcp-on");

      scenario->completion->response = R"(```json
{"name":"测试修复","slug":"123 TEST fixer!","prompt":"修复测试","trigger":"测试失败时","toolNames":["file_read","hidden_mcp_tool","unknown","file_read"],"mcpIds":["custom:mcp-on","custom:mcp-off"]}
```)";
      auto generated = co_await scenario->draft->Generate("  修复测试  ");
      EXPECT_EXPRESSION(generated);
      EXPECT_EXPRESSION(generated->name == "测试修复");
      EXPECT_EXPRESSION(generated->slug == "agent-123-test-fixer");
      EXPECT_EXPRESSION(generated->tool_names == std::vector<std::string>{"file_read"});
      EXPECT_EXPRESSION(generated->mcp_ids ==
             std::vector<std::string>{"custom:mcp-on"});
      EXPECT_EXPRESSION(scenario->completion->request);
      EXPECT_EXPRESSION(!scenario->completion->request->stream);
      EXPECT_EXPRESSION(scenario->completion->request->messages.size() == 2U);
      EXPECT_EXPRESSION(scenario->completion->request->messages[1].content.contains(
          "修复测试"));
      EXPECT_EXPRESSION(scenario->completion->request->messages[1].content.contains(
          "\"name\":\"file_read\""));
      EXPECT_EXPRESSION(scenario->completion->request->messages[1].content.contains(
          "读取文件"));
      EXPECT_EXPRESSION(!scenario->completion->request->messages[1].content.contains(
          "read files"));
      EXPECT_EXPRESSION(!scenario->completion->request->messages[1].content.contains(
          "hidden_mcp_tool"));

      scenario->completion->response =
          R"({"name":"默认","slug":"default","prompt":"prompt","toolNames":["unknown"],"mcpIds":[]})";
      generated = co_await scenario->draft->Generate("default tools");
      EXPECT_EXPRESSION(generated);
      EXPECT_EXPRESSION(generated->tool_names ==
             std::vector<std::string>{"file_read", "glob"});

      scenario->completion->response =
          R"({"name":"中文名称","prompt":"prompt","toolNames":[],"mcpIds":[]})";
      generated = co_await scenario->draft->Generate("missing slug");
      EXPECT_EXPRESSION(generated);
      EXPECT_EXPRESSION(generated->slug == "custom-agent");

      scenario->completion->response = "not json";
      generated = co_await scenario->draft->Generate("invalid");
      EXPECT_EXPRESSION(!generated);
      EXPECT_EXPRESSION(generated.error().code ==
             application::AgentDraftErrorCode::invalid_json);

      scenario->models->selected.clear();
      generated = co_await scenario->draft->Generate("missing model");
      EXPECT_EXPRESSION(!generated);
      EXPECT_EXPRESSION(generated.error().code ==
             application::AgentDraftErrorCode::missing_model);

      generated = co_await scenario->draft->Generate("   ");
      EXPECT_EXPRESSION(!generated);
      EXPECT_EXPRESSION(generated.error().code ==
             application::AgentDraftErrorCode::empty_description);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("agent-extension-draft-probe");
}

} // namespace

TEST(agent_extension_draft_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  active->tools->tools = {
      {.name = "file_read",
       .description = "read files",
       .parameters_json = "{}",
       .category = "file",
       .agent_selected_by_default = true,
       .presentation = {.english_name = "Read file",
                        .english_description = "read files",
                        .chinese_name = "读取文件",
                        .chinese_description = "读取工作区文件"}},
      {.name = "glob",
       .description = "search files",
       .parameters_json = "{}",
       .category = "file",
       .agent_selected_by_default = true,
       .presentation = {.english_name = "Find files",
                        .english_description = "search files",
                        .chinese_name = "查找文件",
                        .chinese_description = "查找工作区文件"}},
      {.name = "hidden_mcp_tool",
       .description = "custom MCP runtime tool",
       .parameters_json = "{}",
       .category = "mcp",
       .agent_selectable = false},
  };
  active->mcps->mcps = {
      {.id = "mcp-on",
       .enabled = true,
       .name = "Enabled MCP",
       .url = "https://mcp.example/rpc",
       .request_headers = {},
       .tools = {{.name = "search",
                  .enabled = true,
                  .description = {},
                  .input_schema_json = {}}},
       .created_at = 0,
       .updated_at = 0},
      {.id = "mcp-off",
       .enabled = false,
       .name = "Disabled MCP",
       .url = "https://off.example/rpc",
       .request_headers = {},
       .tools = {},
       .created_at = 0,
       .updated_at = 0},
  };
  active->draft =
      std::make_shared<application::CompletionAgentExtensionDraftGenerator>(
          active->models, active->completion, active->tools, active->mcps,
          std::make_shared<infrastructure::JsonAgentExtensionDraftCodec>(),
          application::ToolTextLanguage::chinese);

  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
