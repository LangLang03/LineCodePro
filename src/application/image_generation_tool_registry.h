#pragma once

#include <memory>
#include <vector>

#include "application/ports/image_generation.h"
#include "application/ports/model_store.h"
#include "application/ports/tool_registry.h"
#include "application/mcp_execution_settings.h"
#include "application/tool_settings_service.h"

namespace linecode::application {

// Application-level adapter from the generic completion tool contract to the
// selected image model. JSON and HTTP details remain behind injected ports.
class ImageGenerationToolRegistry final : public ToolRegistry {
public:
  ImageGenerationToolRegistry(
      std::shared_ptr<McpExecutionSettingsService> execution_settings,
      std::shared_ptr<ToolSettingsService> settings,
      std::shared_ptr<ModelStore> models,
      std::shared_ptr<ImageGenerationToolCodec> codec,
      std::shared_ptr<ImageGenerationGateway> gateway);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> execution_settings_;
  std::shared_ptr<ToolSettingsService> settings_;
  std::shared_ptr<ModelStore> models_;
  std::shared_ptr<ImageGenerationToolCodec> codec_;
  std::shared_ptr<ImageGenerationGateway> gateway_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
