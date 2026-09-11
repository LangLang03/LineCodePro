#include <cassert>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/image_generation_tool_registry.h"
#include "infrastructure/image_generation_codec.h"

namespace {

using namespace linecode;

class StubExecutionSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string id,
                      bool enabled) override {
    const auto found = std::ranges::find(value.groups, id,
                                         &domain::McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
};

class StubSettings final : public application::ToolSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::ToolSettingsState>>
  Load() override {
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  Persist(application::ToolSettingsChange) override {
    co_return application::SettingsResult<void>{};
  }

  domain::ToolSettingsState value;
};

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
  Select(std::string) override {
    co_return std::expected<void, application::ModelStoreError>{};
  }

  huxerui::Task<std::expected<std::string, application::ModelStoreError>>
  SelectedId() override {
    co_return model.id;
  }

  domain::ModelConfig model;
};

class StubGateway final : public application::ImageGenerationGateway {
public:
  huxerui::Task<application::ImageGenerationResult<domain::GeneratedImage>>
  Generate(domain::ModelConfig model,
           domain::ImageGenerationRequest request) override {
    received_model = std::move(model);
    received_request = std::move(request);
    co_return domain::GeneratedImage{
        .mime_type = "image/png",
        .data_url = "data:image/png;base64,iVBORw0KGgo=",
        .revised_prompt = "revised",
    };
  }

  std::optional<domain::ModelConfig> received_model;
  std::optional<domain::ImageGenerationRequest> received_request;
};

struct Scenario final {
  std::shared_ptr<StubExecutionSettings> execution_settings;
  std::shared_ptr<StubSettings> settings;
  std::shared_ptr<StubModels> models;
  std::shared_ptr<StubGateway> gateway;
  std::shared_ptr<application::ImageGenerationToolRegistry> registry;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto refreshed = co_await scenario->registry->Refresh();
      assert(refreshed);
      assert(scenario->registry->Tools().size() == 1U);
      assert(scenario->registry->Tools().front().name == "image_generation");

      auto invoked = co_await scenario->registry->Invoke(
          "image_generation",
          R"({"prompt":"LineCode icon","size":"1024x1024"})");
      assert(invoked);
      assert(!invoked->error);
      assert(invoked->content.contains("linecode_image_generation"));
      assert(invoked->content.contains("data:image/png;base64"));
      assert(scenario->gateway->received_model);
      assert(scenario->gateway->received_model->id == "image-model");
      assert(scenario->gateway->received_request);
      assert(scenario->gateway->received_request->prompt == "LineCode icon");

      auto invalid = co_await scenario->registry->Invoke(
          "image_generation", R"({"prompt":""})");
      assert(!invalid);
      assert(invalid.error().code ==
             application::ToolRegistryErrorCode::invalid_arguments);

      scenario->settings->value.image_generation_model_id.clear();
      auto missing = co_await scenario->registry->Invoke(
          "image_generation", R"({"prompt":"image"})");
      assert(!missing);
      assert(missing.error().code ==
             application::ToolRegistryErrorCode::unavailable);

      auto unknown = co_await scenario->registry->Invoke("other", "{}");
      assert(!unknown);
      assert(unknown.error().code ==
             application::ToolRegistryErrorCode::unknown_tool);

      const auto image_group = std::ranges::find(
          scenario->execution_settings->value.groups,
          std::string{"image_generation"}, &domain::McpToolGroupState::id);
      assert(image_group != scenario->execution_settings->value.groups.end());
      image_group->enabled = false;
      refreshed = co_await scenario->registry->Refresh();
      assert(refreshed);
      assert(scenario->registry->Tools().empty());
      auto disabled = co_await scenario->registry->Invoke(
          "image_generation", R"({"prompt":"image"})");
      assert(!disabled);
      assert(disabled.error().code ==
             application::ToolRegistryErrorCode::unavailable);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("image-generation-tool-registry-probe");
}

} // namespace

int main() {
  active = std::make_shared<Scenario>();
  active->execution_settings = std::make_shared<StubExecutionSettings>();
  const auto image_group = std::ranges::find(
      active->execution_settings->value.groups,
      std::string{"image_generation"}, &domain::McpToolGroupState::id);
  assert(image_group != active->execution_settings->value.groups.end());
  image_group->enabled = true;
  active->settings = std::make_shared<StubSettings>();
  active->settings->value.image_generation_model_id = "image-model";
  active->models = std::make_shared<StubModels>();
  active->models->model.id = "image-model";
  active->models->model.name = "Image";
  active->models->model.protocol = domain::ModelProtocol::openai_compatible;
  active->models->model.base_url = "https://api.example.test/v1";
  active->models->model.api_key = "secret";
  active->models->model.model_id = "image-model";
  active->gateway = std::make_shared<StubGateway>();
  active->registry =
      std::make_shared<application::ImageGenerationToolRegistry>(
          active->execution_settings, active->settings, active->models,
          std::make_shared<infrastructure::JsonImageGenerationToolCodec>(),
          active->gateway);

  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
