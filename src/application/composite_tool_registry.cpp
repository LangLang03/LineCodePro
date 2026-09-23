#include "application/composite_tool_registry.h"

#include <algorithm>
#include <format>
#include <stdexcept>
#include <utility>

namespace linecode::application {

CompositeToolRegistry::CompositeToolRegistry(
    std::vector<std::shared_ptr<ToolRegistry>> sources)
    : sources_(std::move(sources)) {
  if (sources_.empty() ||
      std::ranges::any_of(sources_, [](const auto &source) {
        return !source;
      })) {
    throw std::invalid_argument(
        "CompositeToolRegistry requires non-empty tool sources");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
CompositeToolRegistry::Refresh() {
  for (const auto &source : sources_) {
    auto refreshed = co_await source->Refresh();
    if (!refreshed)
      co_return std::unexpected(std::move(refreshed.error()));
  }

  std::vector<Binding> next;
  for (const auto &source : sources_) {
    for (const auto &descriptor : source->Tools()) {
      const auto duplicate =
          std::ranges::find(next, descriptor.name, [](const Binding &binding) {
            return std::string_view{binding.descriptor.name};
          });
      if (duplicate != next.end()) {
        co_return std::unexpected(ToolRegistryError{
            .code = ToolRegistryErrorCode::duplicate_tool,
            .message = std::format("Duplicate runtime tool name: {}",
                                   descriptor.name),
        });
      }
      next.push_back(
          Binding{.descriptor = descriptor, .source = source});
    }
  }

  std::vector<RegisteredTool> descriptors;
  descriptors.reserve(next.size());
  std::ranges::transform(next, std::back_inserter(descriptors),
                         &Binding::descriptor);
  bindings_ = std::move(next);
  descriptors_ = std::move(descriptors);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool>
CompositeToolRegistry::Tools() const noexcept {
  return descriptors_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
CompositeToolRegistry::Invoke(std::string name,
                              std::string arguments_json) {
  co_return co_await InvokeWithContext(std::move(name),
                                       std::move(arguments_json), {});
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
CompositeToolRegistry::InvokeWithContext(std::string name,
                                         std::string arguments_json,
                                         ToolInvocationContext context) {
  const auto *binding = Find(name);
  if (!binding) {
    co_return std::unexpected(ToolRegistryError{
        .code = ToolRegistryErrorCode::unknown_tool,
        .message = "Unknown runtime tool: " + name,
    });
  }
  co_return co_await binding->source->InvokeWithContext(
      std::move(name), std::move(arguments_json), std::move(context));
}

const CompositeToolRegistry::Binding *
CompositeToolRegistry::Find(std::string_view name) const noexcept {
  const auto found =
      std::ranges::find(bindings_, name, [](const Binding &binding) {
        return std::string_view{binding.descriptor.name};
      });
  return found == bindings_.end() ? nullptr : &*found;
}

} // namespace linecode::application
