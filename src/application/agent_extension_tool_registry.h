#pragma once

#include <expected>
#include <memory>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/agent_runner.h"
#include "application/ports/extension_store.h"
#include "application/ports/tool_registry.h"
#include "application/tool_text_catalog.h"

namespace linecode::application {

// Exposes every enabled custom Agent extension as a callable tool.
//
// Port of the `CustomAgentExtensionTool` registration in
// `ToolRegistry.reloadExtensions` (`ToolRegistry.java:117-122`) plus the tool
// itself: one `agentx_<slug>` tool per enabled agent, whose call becomes a
// `sub-coding` run carrying the agent's own prompt, prompt template and its
// selected tool/MCP names.
class AgentExtensionToolRegistry final : public ToolRegistry {
public:
  AgentExtensionToolRegistry(std::shared_ptr<AgentExtensionStore> store,
                             std::shared_ptr<AgentRunner> runner,
                             ToolTextLanguage language =
                                 ToolTextLanguage::english);

  // A failed store read keeps the last good index, like the MCP registry.
  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool> Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

  [[nodiscard]] bool Contains(std::string_view name) const noexcept;
  void SetRunner(std::shared_ptr<AgentRunner> runner);

private:
  struct Binding final {
    RegisteredTool descriptor;
    domain::AgentExtension agent;
  };

  [[nodiscard]] const Binding *Find(std::string_view name) const noexcept;
  void RebuildDescriptors();

  std::shared_ptr<AgentExtensionStore> store_;
  std::shared_ptr<AgentRunner> runner_;
  ToolTextLanguage language_;
  std::vector<Binding> bindings_;
  std::vector<RegisteredTool> descriptors_;
};

} // namespace linecode::application
