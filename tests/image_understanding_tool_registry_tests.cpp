#include "gtest_support.h"
#include <map>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/image_understanding_tool_registry.h"
#include "infrastructure/image_understanding_codec.h"

namespace {

using namespace linecode;

class MemorySettings final : public application::AsyncSettingsStore {
public:
  huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    const auto found = values.find(key);
    co_return found == values.end() ? std::move(fallback) : found->second;
  }
  huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string, bool fallback) override { co_return fallback; }
  huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string, std::int64_t fallback) override {
    co_return fallback;
  }
  huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    values.insert_or_assign(std::move(key), std::move(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string, bool) override {
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
  LineCodeSettings() override { co_return values; }

  std::map<std::string, std::string, std::less<>> values;
};

class ExecutionSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override { co_return value; }
  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string id,
                      bool enabled) override {
    const auto found =
        std::ranges::find(value.groups, id, &domain::McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return application::SettingsResult<void>{};
  }
  domain::McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
};

class ToolSettings final : public application::ToolSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::ToolSettingsState>>
  Load() override { co_return value; }
  huxerui::Task<application::SettingsResult<void>>
  Persist(application::ToolSettingsChange) override {
    co_return application::SettingsResult<void>{};
  }
  domain::ToolSettingsState value;
};

class Models final : public application::ModelStore {
public:
  huxerui::Task<std::expected<std::vector<domain::ModelConfig>,
                               application::ModelStoreError>>
  List() override { co_return std::vector{model}; }
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
  Select(std::string) override {
    co_return std::expected<void, application::ModelStoreError>{};
  }
  huxerui::Task<std::expected<std::string, application::ModelStoreError>>
  SelectedId() override { co_return model.id; }
  domain::ModelConfig model;
};

class Images final : public application::WorkspaceImageReader {
public:
  huxerui::Task<application::ImageUnderstandingResult<
      domain::RawWorkspaceImage>>
  Read(std::string path) override {
    received_path = path;
    co_return domain::RawWorkspaceImage{
        .resolved_path = "/workspace/" + path,
        .bytes = {std::byte{0x89}, std::byte{0x50}, std::byte{0x4E},
                  std::byte{0x47}, std::byte{0x0D}, std::byte{0x0A},
                  std::byte{0x1A}, std::byte{0x0A}}};
  }
  std::string received_path;
};

class Gateway final : public application::ImageUnderstandingGateway {
public:
  huxerui::Task<application::ImageUnderstandingResult<std::string>>
  Analyze(domain::ModelConfig model, std::string system_prompt,
          domain::ImageUnderstandingRequest request,
          domain::WorkspaceImage image) override {
    received_model = std::move(model);
    received_system = std::move(system_prompt);
    received_request = std::move(request);
    received_image = std::move(image);
    co_return std::string{"deterministic vision reply"};
  }
  std::optional<domain::ModelConfig> received_model;
  std::string received_system;
  std::optional<domain::ImageUnderstandingRequest> received_request;
  std::optional<domain::WorkspaceImage> received_image;
};

struct Scenario final {
  std::shared_ptr<ExecutionSettings> execution;
  std::shared_ptr<ToolSettings> settings;
  std::shared_ptr<Models> models;
  std::shared_ptr<Images> images;
  std::shared_ptr<Gateway> gateway;
  std::shared_ptr<application::ImageUnderstandingToolRegistry> registry;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto refreshed = co_await scenario->registry->Refresh();
      EXPECT_EXPRESSION(refreshed && scenario->registry->Tools().size() == 1U);
      EXPECT_EXPRESSION(scenario->registry->Tools().front().agent_category ==
             application::AgentToolCategory::read);
      auto result = co_await scenario->registry->Invoke(
          "image_understanding",
          R"({"path":"assets/sample.png","prompt":"identify"})");
      EXPECT_EXPRESSION(result && result->content == "deterministic vision reply");
      EXPECT_EXPRESSION(scenario->images->received_path == "assets/sample.png");
      EXPECT_EXPRESSION(scenario->gateway->received_model->id == "vision-model");
      EXPECT_EXPRESSION(scenario->gateway->received_system.contains(
          "LineCode's image understanding tool"));
      EXPECT_EXPRESSION(scenario->gateway->received_image->mime_type == "image/png");

      auto invalid = co_await scenario->registry->Invoke(
          "image_understanding", R"({"path":""})");
      EXPECT_EXPRESSION(!invalid && invalid.error().code ==
                             application::ToolRegistryErrorCode::invalid_arguments);
      auto unknown = co_await scenario->registry->Invoke("other", "{}");
      EXPECT_EXPRESSION(!unknown && unknown.error().code ==
                             application::ToolRegistryErrorCode::unknown_tool);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("image-understanding-registry-probe");
}

} // namespace

TEST(image_understanding_tool_registry_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  active->execution = std::make_shared<ExecutionSettings>();
  auto group = std::ranges::find(active->execution->value.groups,
                                 std::string{"image_understanding"},
                                 &domain::McpToolGroupState::id);
  EXPECT_EXPRESSION(group != active->execution->value.groups.end());
  group->enabled = true;
  active->settings = std::make_shared<ToolSettings>();
  active->settings->value.image_understanding_model_id = "vision-model";
  active->models = std::make_shared<Models>();
  active->models->model.id = "vision-model";
  active->models->model.name = "Vision";
  active->models->model.protocol =
      domain::ModelProtocol::openai_compatible;
  active->models->model.provider_label = "Fixture";
  active->models->model.base_url = "https://example.test/v1";
  active->models->model.api_key = "secret";
  active->models->model.model_id = "vision-model";
  active->images = std::make_shared<Images>();
  active->gateway = std::make_shared<Gateway>();
  auto prompt_templates = std::make_shared<application::PromptTemplateRepository>(
      std::make_shared<MemorySettings>());
  active->registry =
      std::make_shared<application::ImageUnderstandingToolRegistry>(
          active->execution, active->settings, active->models,
          std::move(prompt_templates), active->images,
          std::make_shared<infrastructure::JsonImageUnderstandingToolCodec>(),
          active->gateway);

  const huxerui::Application app(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(app);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
