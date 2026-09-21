#include "gtest_support.h"
#include <map>
#include <memory>
#include <optional>
#include <ranges>
#include <span>
#include <string>
#include <string_view>
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
           application::CompletionObserver observer) override {
    requests.push_back(std::move(request));
    if (requests.size() == 1U) {
      if (observer.on_event) {
        observer.on_event(application::CompletionReasoningDelta{
            .turn_index = 99,
            .text = "inspect",
            .kind = application::CompletionReasoningKind::thinking,
            .starts_new_segment = true});
      }
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
    if (observer.on_event) {
      observer.on_event(application::CompletionTextDelta{
          .turn_index = 99, .text = "final answer"});
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

constexpr std::string_view kImageResult =
    R"json({"linecode_image_generation":true,"display_markdown":"![fixture](data:image/png;base64,AAAA)","model_content":"Generated image for: fixture"})json";

class ImageToolRegistry final : public application::ToolRegistry {
public:
  huxerui::Task<std::expected<void, application::ToolRegistryError>>
  Refresh() override {
    co_return std::expected<void, application::ToolRegistryError>{};
  }

  std::span<const application::RegisteredTool> Tools() const noexcept override {
    return tools;
  }

  huxerui::Task<std::expected<application::ToolInvocationResult,
                              application::ToolRegistryError>>
  Invoke(std::string name, std::string) override {
    EXPECT_EXPRESSION(name == "image_generation");
    co_return application::ToolInvocationResult{
        .content = std::string{kImageResult}, .error = false};
  }

private:
  const std::vector<application::RegisteredTool> tools{
      {.name = "image_generation",
       .description = "Generate an image",
       .parameters_json = R"({"type":"object"})",
       .allowed_in_read_only = false}};
};

class ImageCompletion final : public application::CompletionGateway {
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
          .tool_calls = {{.id = "image-1",
                          .name = "image_generation",
                          .arguments_json = R"({"prompt":"fixture"})"}},
      };
    }
    co_return application::CompletionResponse{
        .text = "Image complete.",
        .reasoning_content = {},
        .tool_calls = {},
        .input_tokens = 0,
        .output_tokens = 0,
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
  std::vector<application::CompletionEvent> events;
  std::shared_ptr<ImageCompletion> image_completion;
  std::vector<application::CompletionEvent> image_events;
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
      EXPECT_EXPRESSION(co_await scenario->permissions->SetMode(
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
              .on_event =
                  [scenario](const auto &event) {
                    scenario->events.push_back(event);
                  },
              .on_tool_review = [scenario](auto review)
                  -> huxerui::Task<
                      application::CompletionObserver::ToolReviewDecision> {
                ++scenario->review_count;
                EXPECT_EXPRESSION(review.call.name == "mcpx_42svxm_echo");
                EXPECT_EXPRESSION(!review.can_allow_always);
                co_return application::CompletionObserver::ToolReviewDecision::
                    allow_once;
              },
          });

      scenario->image_completion = std::make_shared<ImageCompletion>();
      auto image_loop = std::make_shared<application::McpCompletionLoop>(
          scenario->image_completion, std::make_shared<ImageToolRegistry>());
      application::CompletionRequest image_request;
      image_request.model.model_id = "image-fixture";
      image_request.model.tool_call_limit = 1;
      image_request.messages.push_back({
          .role = application::CompletionRole::user,
          .content = "generate",
      });
      auto image_result = co_await image_loop->Complete(
          std::move(image_request),
          application::CompletionObserver{
              .on_event =
                  [scenario](const auto &event) {
                    scenario->image_events.push_back(event);
                  },
              .on_tool_review = {},
          });
      EXPECT_EXPRESSION(image_result &&
                        image_result->text == "Image complete.");
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("mcp-loop-probe");
}

} // namespace

TEST(mcp_completion_loop_tests, LegacySuite) {
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

  EXPECT_EXPRESSION(active->result && active->result->has_value());
  EXPECT_EXPRESSION((*active->result)->text == "final answer");
  EXPECT_EXPRESSION(completion->requests.size() == 2U);
  EXPECT_EXPRESSION(completion->requests[0].tools.size() == 1U);
  EXPECT_EXPRESSION(completion->requests[0].tools[0].name ==
                    "mcpx_42svxm_echo");
  EXPECT_EXPRESSION(completion->requests[0].messages.front().role ==
                    application::CompletionRole::system);
  EXPECT_EXPRESSION(active->composer->observed_tool_count == 1U);
  EXPECT_EXPRESSION(active->composer->observed_work_directory == "/workspace");
  EXPECT_EXPRESSION(completion->requests[1].messages.size() == 4U);
  EXPECT_EXPRESSION(completion->requests[1].messages[2].tool_calls.size() ==
                    1U);
  EXPECT_EXPRESSION(completion->requests[1].messages[3].tool_result);
  EXPECT_EXPRESSION(completion->requests[1].messages[3].tool_result->content ==
                    "fixed tool result");
  EXPECT_EXPRESSION(invoker->extension_names ==
                    std::vector<std::string>{"Parity MCP"});
  EXPECT_EXPRESSION(invoker->tool_names == std::vector<std::string>{"echo"});
  EXPECT_EXPRESSION(invoker->arguments_json ==
                    std::vector<std::string>{R"({"text":"hello"})"});
  EXPECT_EXPRESSION(active->review_count == 1U);
  EXPECT_EXPRESSION(active->events.size() == 6U);
  const auto *reasoning =
      std::get_if<application::CompletionReasoningDelta>(&active->events[0]);
  EXPECT_EXPRESSION(reasoning && reasoning->turn_index == 0U &&
                    reasoning->text == "inspect");
  const auto *final_text =
      std::get_if<application::CompletionTextDelta>(&active->events.back());
  EXPECT_EXPRESSION(final_text && final_text->turn_index == 1U &&
                    final_text->text == "final answer");
  const std::vector<application::CompletionToolCallStatus> expected_statuses{
      application::CompletionToolCallStatus::requested,
      application::CompletionToolCallStatus::awaiting_review,
      application::CompletionToolCallStatus::running,
      application::CompletionToolCallStatus::completed};
  for (std::size_t index = 0; index < expected_statuses.size(); ++index) {
    const auto *event = std::get_if<application::CompletionToolCallEvent>(
        &active->events[index + 1U]);
    EXPECT_EXPRESSION(event != nullptr);
    EXPECT_EXPRESSION(event->turn_index == 0U);
    EXPECT_EXPRESSION(event->status == expected_statuses[index]);
  }
  const auto *completed =
      std::get_if<application::CompletionToolCallEvent>(&active->events[4]);
  EXPECT_EXPRESSION(completed && completed->result &&
                    completed->result->content == "fixed tool result");
  EXPECT_EXPRESSION(active->image_completion->requests.size() == 2U);
  const auto &model_tool_message =
      active->image_completion->requests[1].messages.back();
  EXPECT_EXPRESSION(model_tool_message.tool_result);
  EXPECT_EXPRESSION(model_tool_message.tool_result->content ==
                    "Generated image for: fixture");
  EXPECT_EXPRESSION(
      !model_tool_message.tool_result->content.contains("data:image/"));
  const auto image_completed =
      std::ranges::find_if(active->image_events, [](const auto &event) {
        const auto *tool =
            std::get_if<application::CompletionToolCallEvent>(&event);
        return tool != nullptr &&
               tool->status == application::CompletionToolCallStatus::completed;
      });
  EXPECT_EXPRESSION(image_completed != active->image_events.end());
  const auto &image_event =
      std::get<application::CompletionToolCallEvent>(*image_completed);
  EXPECT_EXPRESSION(image_event.result &&
                    image_event.result->content == kImageResult);
  EXPECT_EXPRESSION(image_event.display.display_markdown ==
                    "![fixture](data:image/png;base64,AAAA)");
  EXPECT_EXPRESSION(image_event.display.hide_success_card);
  active.reset();
}
