#pragma once

#include <memory>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/image_understanding.h"
#include "application/ports/model_store.h"
#include "application/ports/tool_registry.h"
#include "application/prompt_template_repository.h"
#include "application/tool_settings_service.h"

namespace linecode::application {

class ImageUnderstandingToolRegistry final : public ToolRegistry {
public:
  ImageUnderstandingToolRegistry(
      std::shared_ptr<McpExecutionSettingsService> execution_settings,
      std::shared_ptr<ToolSettingsService> settings,
      std::shared_ptr<ModelStore> models,
      std::shared_ptr<PromptTemplateRepository> prompt_templates,
      std::shared_ptr<WorkspaceImageReader> images,
      std::shared_ptr<ImageUnderstandingToolCodec> codec,
      std::shared_ptr<ImageUnderstandingGateway> gateway);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool>
  Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> execution_settings_;
  std::shared_ptr<ToolSettingsService> settings_;
  std::shared_ptr<ModelStore> models_;
  std::shared_ptr<PromptTemplateRepository> prompt_templates_;
  std::shared_ptr<WorkspaceImageReader> images_;
  std::shared_ptr<ImageUnderstandingToolCodec> codec_;
  std::shared_ptr<ImageUnderstandingGateway> gateway_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
