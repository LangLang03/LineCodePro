#include "application/agent_extension_tool_registry.h"

#include "application/agent_run_progress_codec.h"

#include <algorithm>
#include <optional>
#include <ranges>
#include <string>
#include <utility>

#include "application/custom_agent_tool.h"
#include "domain/chat_image.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// The delegated input forces `sub-coding`, matching
// `CustomAgentExtensionTool.execute` (`CustomAgentExtensionTool.java:80-96`).
constexpr std::string_view kSubCodingType = "sub-coding";

[[nodiscard]] std::vector<std::string> StringArray(const json::Value *value) {
  std::vector<std::string> values;
  const auto *array = json::AsArray(value);
  if (array == nullptr)
    return values;
  for (const auto &item : *array) {
    if (const auto *text = json::AsString(&item);
        text != nullptr && !text->empty())
      values.push_back(*text);
  }
  return values;
}

[[nodiscard]] std::string OptString(const json::Value *value) {
  const auto *text = value == nullptr ? nullptr : json::AsString(value);
  return text == nullptr ? std::string{} : *text;
}

// Java `String.trim()`: the legacy trimmed the task before testing it
// (`CustomAgentExtensionTool.java:69-71`), so a whitespace-only task is empty.
[[nodiscard]] std::string TrimCopy(const std::string_view value) {
  const auto first = value.find_first_not_of(" \t\n\r\f\v");
  if (first == std::string_view::npos)
    return {};
  const auto last = value.find_last_not_of(" \t\n\r\f\v");
  return std::string{value.substr(first, last - first + 1)};
}

} // namespace

AgentExtensionToolRegistry::AgentExtensionToolRegistry(
    std::shared_ptr<AgentExtensionStore> store,
    std::shared_ptr<AgentRunner> runner, const ToolTextLanguage language)
    : store_(std::move(store)), runner_(std::move(runner)),
      language_(language) {}

void AgentExtensionToolRegistry::SetRunner(
    std::shared_ptr<AgentRunner> runner) {
  runner_ = std::move(runner);
}

huxerui::Task<std::expected<void, ToolRegistryError>>
AgentExtensionToolRegistry::Refresh() {
  if (!store_) {
    co_return std::unexpected(
        ToolRegistryError{.code = ToolRegistryErrorCode::unavailable,
                          .message = "agent extension store is not available"});
  }
  auto listed = co_await store_->ListAgents();
  if (!listed) {
    co_return std::unexpected(
        ToolRegistryError{.code = ToolRegistryErrorCode::load_failed,
                          .message = listed.error().message});
  }
  std::vector<Binding> next;
  next.reserve(listed->size());
  for (auto &agent : *listed) {
    // `ToolRegistry.java:117-122` only registers enabled agents.
    if (!agent.enabled)
      continue;
    Binding binding;
    binding.descriptor = RegisteredTool{
        .name = CustomAgentToolName(agent.slug),
        .description = CustomAgentToolDescription(agent),
        .parameters_json = CustomAgentToolSchemaJson(),
        // `CustomAgentExtensionTool` does not opt into read-only mode, so the
        // default keeps it denied there like every other unmarked tool.
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::system,
        .category = "agent",
        .agent_selectable = false,
        .presentation =
            {
                .english_name = agent.name,
                .english_description = agent.trigger,
                .chinese_name = agent.name,
                .chinese_description = agent.trigger,
            },
    };
    binding.agent = std::move(agent);
    next.push_back(std::move(binding));
  }
  bindings_ = std::move(next);
  RebuildDescriptors();
  co_return std::expected<void, ToolRegistryError>{};
}

void AgentExtensionToolRegistry::RebuildDescriptors() {
  descriptors_.clear();
  descriptors_.reserve(bindings_.size());
  for (const auto &binding : bindings_)
    descriptors_.push_back(binding.descriptor);
}

std::span<const RegisteredTool>
AgentExtensionToolRegistry::Tools() const noexcept {
  return descriptors_;
}

bool AgentExtensionToolRegistry::Contains(
    const std::string_view name) const noexcept {
  return Find(name) != nullptr;
}

const AgentExtensionToolRegistry::Binding *
AgentExtensionToolRegistry::Find(const std::string_view name) const noexcept {
  const auto found =
      std::ranges::find(bindings_, name, [](const Binding &binding) {
        return std::string_view{binding.descriptor.name};
      });
  return found == bindings_.end() ? nullptr : &*found;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
AgentExtensionToolRegistry::Invoke(std::string name,
                                   std::string arguments_json) {
  co_return co_await InvokeWithContext(std::move(name),
                                       std::move(arguments_json), {});
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
AgentExtensionToolRegistry::InvokeWithContext(std::string name,
                                              std::string arguments_json,
                                              ToolInvocationContext context) {
  const auto *binding = Find(name);
  if (binding == nullptr) {
    co_return std::unexpected(
        ToolRegistryError{.code = ToolRegistryErrorCode::unknown_tool,
                          .message = "unknown tool: " + std::move(name)});
  }
  auto parsed = json::Parse(arguments_json);
  if (!parsed) {
    co_return std::unexpected(ToolRegistryError{
        .code = ToolRegistryErrorCode::invalid_arguments,
        .message = "custom agent arguments are not valid JSON"});
  }
  const auto *input = json::AsObject(&*parsed);
  if (input == nullptr) {
    co_return std::unexpected(ToolRegistryError{
        .code = ToolRegistryErrorCode::invalid_arguments,
        .message = "custom agent arguments must be an object"});
  }
  const auto task = TrimCopy(OptString(json::Find(*input, "task")));
  // `CustomAgentExtensionTool.java:69-71`.
  if (task.empty()) {
    co_return ToolInvocationResult{
        .content =
            ToolText(ToolTextKey::tool_custom_agent_task_empty, {}, language_),
        .error = true};
  }
  if (!runner_) {
    co_return ToolInvocationResult{
        .content = ToolText(ToolTextKey::tool_custom_agent_runner_not_available,
                            {}, language_),
        .error = true};
  }

  AgentRunRequest request;
  request.type = std::string{kSubCodingType};
  request.description = binding->agent.name;
  request.prompt = BuildCustomAgentPrompt(
      binding->agent, task, OptString(json::Find(*input, "context")));
  request.read_scope = StringArray(json::Find(*input, "read_scope"));
  request.write_scope = StringArray(json::Find(*input, "write_scope"));
  request.custom_tool_names = binding->agent.tool_names;
  request.custom_mcp_ids = binding->agent.mcp_ids;
  request.tool_call_id = context.call_id;
  request.on_progress = [publish = std::move(context.on_progress_json)](
                            const domain::AgentExecutionSnapshot &progress) {
    if (publish)
      publish(SerializeAgentProgress(progress));
  };

  auto result = co_await runner_->RunAgent(std::move(request));
  if (result.error && result.output.empty()) {
    // `CustomAgentExtensionTool.java:97-99`.
    co_return ToolInvocationResult{
        .content =
            ToolText(ToolTextKey::tool_custom_agent_failed,
                     std::array<std::string, 1>{result.output}, language_),
        .error = true};
  }
  co_return ToolInvocationResult{.content = std::move(result.output),
                                 .error = result.error};
}

} // namespace linecode::application
