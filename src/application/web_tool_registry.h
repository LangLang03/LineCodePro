#pragma once

#include <memory>
#include <string_view>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/tool_registry.h"
#include "application/ports/web_tools.h"
#include "application/tool_settings_service.h"

namespace linecode::application {

inline constexpr std::string_view kWebToolGroupId = "web_search";
inline constexpr std::string_view kWebFetchToolName = "web_fetch";
inline constexpr std::string_view kWebSearchToolName = "web_search";

// Runtime tool source for the "web_search" group (web_fetch + web_search).
// Search configuration is read through ToolSettingsService, the same
// abstraction that persists @lineai_web_search_config
// (WebSearchConfigRepository.java:6,21-23), and every network call crosses the
// narrow WebToolsGateway port. Group enablement mirrors the legacy
// "web_search" MCP group, which owns web_search and web_fetch
// (app/.../ToolSettingsRepository.java:109-114).
class WebToolRegistry final : public ToolRegistry {
public:
  WebToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                  std::shared_ptr<ToolSettingsService> tool_settings,
                  std::shared_ptr<WebToolsGateway> gateway);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<ToolSettingsService> tool_settings_;
  std::shared_ptr<WebToolsGateway> gateway_;
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
