#pragma once

#include <cstdint>
#include <expected>
#include <memory>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/extension_store.h"
#include "application/ports/mcp_tool_invoker.h"
#include "application/ports/mcp_tool_schema_policy.h"
#include "application/ports/tool_registry.h"

namespace linecode::application {

using RegisteredMcpTool = RegisteredTool;
using McpToolRegistryErrorCode = ToolRegistryErrorCode;
using McpToolRegistryError = ToolRegistryError;

class McpExtensionToolRegistry final : public ToolRegistry {
public:
  McpExtensionToolRegistry(std::shared_ptr<McpExtensionStore> store,
                           std::shared_ptr<McpToolInvoker> invoker,
                           std::shared_ptr<const McpToolSchemaPolicy> schemas);

  // Refresh is transactional: a failed store read keeps the last good index.
  [[nodiscard]] huxerui::Task<std::expected<void, McpToolRegistryError>>
  Refresh() override;
  void Update(std::vector<domain::McpExtension> extensions);

  [[nodiscard]] std::span<const RegisteredMcpTool>
  Tools() const noexcept override;
  [[nodiscard]] bool Contains(std::string_view name) const noexcept;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, McpToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  struct Binding final {
    RegisteredMcpTool descriptor;
    domain::McpExtension extension;
    domain::McpToolSummary tool;
  };

  [[nodiscard]] const Binding *Find(std::string_view name) const noexcept;
  void RebuildDescriptors();

  std::shared_ptr<McpExtensionStore> store_;
  std::shared_ptr<McpToolInvoker> invoker_;
  std::shared_ptr<const McpToolSchemaPolicy> schemas_;
  std::vector<Binding> bindings_;
  std::vector<RegisteredMcpTool> descriptors_;
};

} // namespace linecode::application
