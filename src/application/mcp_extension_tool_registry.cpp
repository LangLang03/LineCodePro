#include "application/mcp_extension_tool_registry.h"

#include <algorithm>
#include <format>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

[[nodiscard]] std::string Description(const domain::McpExtension &extension,
                                      const domain::McpToolSummary &tool) {
  auto description = std::format(
      "Invoke the tool {} of the custom HTTP MCP \"{}\".", tool.name,
      extension.name);
  if (!tool.description.empty()) {
    description.push_back('\n');
    description += tool.description;
  }
  description += "\nMCP address: ";
  description += extension.url;
  return description;
}

[[nodiscard]] McpToolRegistryError Error(McpToolRegistryErrorCode code,
                                         std::string message) {
  return {.code = code, .message = std::move(message)};
}

} // namespace

McpExtensionToolRegistry::McpExtensionToolRegistry(
    std::shared_ptr<McpExtensionStore> store,
    std::shared_ptr<McpToolInvoker> invoker,
    std::shared_ptr<const McpToolSchemaPolicy> schemas)
    : store_(std::move(store)), invoker_(std::move(invoker)),
      schemas_(std::move(schemas)) {
  if (!store_ || !invoker_ || !schemas_)
    throw std::invalid_argument(
        "McpExtensionToolRegistry requires store, invoker, and schema policy");
}

huxerui::Task<std::expected<void, McpToolRegistryError>>
McpExtensionToolRegistry::Refresh() {
  auto extensions = co_await store_->ListMcps();
  if (!extensions) {
    co_return std::unexpected(
        Error(McpToolRegistryErrorCode::load_failed,
              std::move(extensions.error().message)));
  }
  Update(std::move(*extensions));
  co_return std::expected<void, McpToolRegistryError>{};
}

void McpExtensionToolRegistry::Update(
    std::vector<domain::McpExtension> extensions) {
  std::vector<Binding> next;
  for (auto &extension : extensions) {
    if (!extension.enabled)
      continue;
    for (auto &tool : extension.tools) {
      if (!tool.enabled)
        continue;
      const auto name = domain::McpExtensionToolName(extension.id, tool.name);
      Binding binding{
          .descriptor = RegisteredMcpTool{
              .name = name,
              .description = Description(extension, tool),
              .parameters_json = schemas_->Normalize(tool.input_schema_json),
              .agent_category = AgentToolCategory::system,
              .category = "mcp",
              .agent_selectable = false,
              .agent_scope_ids = {"custom:" + extension.id},
              .presentation = {
                  .english_name = tool.name,
                  .english_description = tool.description.empty()
                                             ? extension.name
                                             : tool.description,
                  .chinese_name = tool.name,
                  .chinese_description = tool.description.empty()
                                             ? extension.name
                                             : tool.description,
              },
          },
          .extension = extension,
          .tool = tool,
      };
      const auto duplicate =
          std::ranges::find_if(next, [&](const Binding &candidate) {
            return candidate.descriptor.name == name;
          });
      if (duplicate == next.end())
        next.push_back(std::move(binding));
      else
        *duplicate = std::move(binding);
    }
  }
  bindings_ = std::move(next);
  RebuildDescriptors();
}

std::span<const RegisteredMcpTool>
McpExtensionToolRegistry::Tools() const noexcept {
  return descriptors_;
}

bool McpExtensionToolRegistry::Contains(std::string_view name) const noexcept {
  return Find(name) != nullptr;
}

huxerui::Task<
    std::expected<ToolInvocationResult, McpToolRegistryError>>
McpExtensionToolRegistry::Invoke(std::string name,
                                 std::string arguments_json) {
  const auto *binding = Find(name);
  if (!binding) {
    co_return std::unexpected(Error(McpToolRegistryErrorCode::unknown_tool,
                                    "Unknown MCP extension tool: " + name));
  }
  auto invoked = co_await invoker_->Invoke(binding->extension, binding->tool,
                                           std::move(arguments_json));
  if (!invoked) {
    co_return std::unexpected(
        Error(McpToolRegistryErrorCode::invocation_failed,
              std::move(invoked.error().message)));
  }
  co_return ToolInvocationResult{.content = std::move(invoked->content),
                                 .error = invoked->error};
}

const McpExtensionToolRegistry::Binding *
McpExtensionToolRegistry::Find(std::string_view name) const noexcept {
  const auto found =
      std::ranges::find(bindings_, name, [](const Binding &binding) {
        return std::string_view{binding.descriptor.name};
      });
  return found == bindings_.end() ? nullptr : &*found;
}

void McpExtensionToolRegistry::RebuildDescriptors() {
  descriptors_.clear();
  descriptors_.reserve(bindings_.size());
  std::ranges::transform(bindings_, std::back_inserter(descriptors_),
                         &Binding::descriptor);
}

} // namespace linecode::application
