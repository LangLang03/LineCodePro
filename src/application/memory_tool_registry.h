#pragma once

#include <memory>
#include <string_view>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/memory_store.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/tool_registry.h"

namespace linecode::application {

inline constexpr std::string_view kMemoryToolGroupId = "memory";
inline constexpr std::string_view kMemoryUpdateToolName = "memory_update";

// Runtime tool source for the "memory" group. The durable store stays behind
// the existing MemoryStore port and the active project id behind
// ProjectWorkspaceController, so this adapter owns only exposure policy,
// argument validation and result projection.
class MemoryToolRegistry final : public ToolRegistry {
public:
  MemoryToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                     std::shared_ptr<MemoryStore> store,
                     std::shared_ptr<ProjectWorkspaceController> projects);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<MemoryStore> store_;
  std::shared_ptr<ProjectWorkspaceController> projects_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
