#pragma once

#include <memory>
#include <string>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/tool_file_access.h"
#include "application/ports/tool_registry.h"
#include "application/tool_text_catalog.h"

namespace linecode::application {

// Built-in file tool group ("file_ops"): file_read, file_write, file_edit,
// file_delete, glob and list_dir.
//
// The tool catalog is declarative: every tool is one entry of the descriptor
// table inside the implementation, so adding a tool never adds a dispatch
// branch. Enablement follows the "file_ops" group state and its local-only
// execution mode, matching the legacy tool registry.
//
// Tool output is localized through the application-owned text catalog rather
// than app::strings::*, because the built-in tools run outside composition
// where huxerui::UseString is unavailable. The language is fixed at
// construction; the default keeps the English product strings.
class FileToolRegistry final : public ToolRegistry {
public:
  FileToolRegistry(std::shared_ptr<McpExecutionSettingsService> settings,
                   std::shared_ptr<ProjectWorkspaceController> workspace,
                   std::shared_ptr<ToolFileAccess> files,
                   ToolTextLanguage language = ToolTextLanguage::english);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::shared_ptr<ProjectWorkspaceController> workspace_;
  std::shared_ptr<ToolFileAccess> files_;
  ToolTextLanguage language_{ToolTextLanguage::english};
  std::vector<RegisteredTool> tools_;
};

} // namespace linecode::application
