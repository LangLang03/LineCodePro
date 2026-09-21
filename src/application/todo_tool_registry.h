#pragma once

#include <memory>
#include <span>
#include <string_view>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/todo_state_store.h"
#include "application/ports/tool_registry.h"

namespace linecode::application {

inline constexpr std::string_view kTodoUpdateToolName = "todo_update";

class TodoToolRegistry final : public ToolRegistry {
public:
  TodoToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                   std::shared_ptr<TodoStateStore> state);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<TodoStateStore> state_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
