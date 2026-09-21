#pragma once

#include <memory>
#include <string_view>
#include <vector>

#include "application/ports/tool_registry.h"

namespace linecode::application {

// Composes independent tool sources without coupling the completion loop to a
// concrete tool family. Tool names must be globally unique.
class CompositeToolRegistry final : public ToolRegistry {
public:
  explicit CompositeToolRegistry(
      std::vector<std::shared_ptr<ToolRegistry>> sources);

  [[nodiscard]] huxerui::Task<std::expected<void, ToolRegistryError>>
  Refresh() override;
  [[nodiscard]] std::span<const RegisteredTool>
  Tools() const noexcept override;
  [[nodiscard]] huxerui::Task<
      std::expected<ToolInvocationResult, ToolRegistryError>>
  Invoke(std::string name, std::string arguments_json) override;

private:
  struct Binding final {
    RegisteredTool descriptor;
    std::shared_ptr<ToolRegistry> source;
  };

  [[nodiscard]] const Binding *Find(std::string_view name) const noexcept;

  std::vector<std::shared_ptr<ToolRegistry>> sources_;
  std::vector<Binding> bindings_;
  std::vector<RegisteredTool> descriptors_;
};

} // namespace linecode::application
