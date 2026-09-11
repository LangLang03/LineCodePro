#include "application/todo_tool_registry.h"

#include <array>
#include <cctype>
#include <cstddef>
#include <expected>
#include <format>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// Verbatim legacy TodoUpdateTool.getDescription() (English source string).
constexpr std::string_view kTodoUpdateDescription =
    "Maintain the current session's TODO list. Each call replaces the old "
    "list with the full list; the state is injected as {{TODO_STATE}} into "
    "the next system prompt, helping the model proceed in order and update "
    "progress promptly. Status values: pending (not started) / in_progress "
    "(in progress) / completed (done). At most 1 in_progress at a time; new "
    "tasks should be placed at the bottom of the list; when a task is "
    "complete, remove it from the list or set it to completed immediately, "
    "do not keep intermediate states.";

// Verbatim legacy TodoUpdateTool.getParameters() (name/type/properties/required
// and every description string), serialized in the canonical sorted-key form
// produced by archive_json::Serialize for the other native registries.
constexpr std::string_view kTodoUpdateSchema =
    R"({"properties":{"items":{"description":"Complete TODO list; replaces the old list.","items":{"properties":{"content":{"description":"Task content, concise and verifiable","type":"string"},"status":{"description":"Task status: pending / in_progress / completed","enum":["pending","in_progress","completed"],"type":"string"}},"type":"object"},"type":"array"}},"required":["items"],"type":"object"})";

constexpr std::string_view kTodoGroupId = "todo";
constexpr std::string_view kTodoCategory = "todo";

// Legacy string resources of the Android tool.
constexpr std::string_view kParametersEmpty = "Parameters cannot be empty.";
constexpr std::string_view kItemsMissing = "Missing items array.";
constexpr std::string_view kStoreUnavailable =
    "TODO state store not initialized.";
constexpr std::string_view kListCleared = "TODO list cleared.";

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

ToolRegistryError Adapt(const TodoStateError &error) {
  using enum TodoStateErrorCode;
  switch (error.code) {
  case unavailable:
    return Error(ToolRegistryErrorCode::unavailable,
                 error.message.empty() ? std::string{kStoreUnavailable}
                                       : error.message);
  case read_failed:
  case write_failed:
    return Error(ToolRegistryErrorCode::invocation_failed, error.message);
  }
  std::unreachable();
}

std::string Trim(std::string_view text) {
  const auto visible = [](char character) {
    return std::isspace(static_cast<unsigned char>(character)) == 0;
  };
  const auto begin = std::ranges::find_if(text, visible);
  const auto end =
      std::ranges::find_if(text | std::views::reverse, visible).base();
  return begin < end ? std::string{begin, end} : std::string{};
}

std::expected<std::vector<TodoItem>, ToolRegistryError>
ParseItems(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr)
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kParametersEmpty}));
  const auto *raw_items = json::AsArray(json::Find(*object, "items"));
  if (raw_items == nullptr)
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kItemsMissing}));
  std::vector<TodoItem> items;
  items.reserve(raw_items->size());
  for (const auto &value : *raw_items) {
    const auto *entry = json::AsObject(&value);
    if (entry == nullptr)
      continue;
    // Legacy TodoItem.fromJson() drops entries without usable content and
    // normalizes the status vocabulary.
    if (const auto *content = json::AsString(json::Find(*entry, "content"))) {
      auto trimmed = Trim(*content);
      if (trimmed.empty())
        continue;
      const auto *status = json::AsString(json::Find(*entry, "status"));
      items.push_back(TodoItem{
          .content = std::move(trimmed),
          .status = status == nullptr ? std::string{kTodoStatusPending}
                                      : NormalizeTodoStatus(*status),
      });
    }
  }
  return items;
}

std::string Summary(const TodoState &state) {
  if (state.total_count() == 0U)
    return std::string{kListCleared};
  return std::format("TODO list updated, {} item(s) total, {} completed.",
                     state.total_count(), state.completed_count());
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteTodoUpdate(TodoStateStore &store, std::string_view arguments_json);

// Declarative tool catalog: Invoke() dispatches through this table instead of
// switching on tool names, so new todo tools are added by extending the table.
struct TodoToolDescriptor final {
  std::string_view name;
  std::string_view description;
  std::string_view parameters_json;
  bool allowed_in_read_only{};
  huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>> (
      *execute)(TodoStateStore &store, std::string_view arguments_json);
};

constexpr std::array<TodoToolDescriptor, 1> kTodoTools{{
    {
        .name = kTodoUpdateToolName,
        .description = kTodoUpdateDescription,
        .parameters_json = kTodoUpdateSchema,
        // TodoUpdateTool does not override isAllowedInReadonlyMode(), so the
        // BaseTool default (false) applies: mutating session state stays denied
        // in read-only mode.
        .allowed_in_read_only = false,
        .execute = &ExecuteTodoUpdate,
    },
}};

const TodoToolDescriptor *FindDescriptor(std::string_view name) {
  const auto found =
      std::ranges::find(kTodoTools, name, &TodoToolDescriptor::name);
  return found == kTodoTools.end() ? nullptr : &*found;
}

RegisteredTool CatalogEntry(const TodoToolDescriptor &descriptor) {
  return RegisteredTool{
      .name = std::string{descriptor.name},
      .description = std::string{descriptor.description},
      .parameters_json = std::string{descriptor.parameters_json},
      .allowed_in_read_only = descriptor.allowed_in_read_only,
      .permanent_grant_supported = false,
      .category = std::string{kTodoCategory},
  };
}

bool TodoGroupEnabled(const domain::McpExecutionSettings &settings) {
  const auto found = std::ranges::find(
      settings.groups, kTodoGroupId,
      [](const domain::McpToolGroupState &group) {
        return std::string_view{group.id};
      });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

bool Exposed(const std::vector<RegisteredTool> &tools, std::string_view name) {
  return std::ranges::any_of(tools, [name](const RegisteredTool &tool) {
    return tool.name == name;
  });
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteTodoUpdate(TodoStateStore &store, std::string_view arguments_json) {
  auto items = ParseItems(arguments_json);
  if (!items)
    co_return std::unexpected(std::move(items.error()));
  auto stored = co_await store.Replace(std::move(*items));
  if (!stored)
    co_return std::unexpected(Adapt(stored.error()));
  co_return ToolInvocationResult{
      .content = Summary(*stored),
      .error = false,
  };
}

} // namespace

TodoToolRegistry::TodoToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<TodoStateStore> state)
    : settings_(std::move(settings)), state_(std::move(state)) {
  if (!settings_ || !state_)
    throw std::invalid_argument("TodoToolRegistry requires settings and state");
}

huxerui::Task<std::expected<void, ToolRegistryError>>
TodoToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  std::vector<RegisteredTool> next_tools;
  if (TodoGroupEnabled(*settings)) {
    next_tools.reserve(kTodoTools.size());
    for (const auto &descriptor : kTodoTools)
      next_tools.push_back(CatalogEntry(descriptor));
  }
  tools_ = std::move(next_tools);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> TodoToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
TodoToolRegistry::Invoke(std::string name, std::string arguments_json) {
  const auto *descriptor = FindDescriptor(name);
  if (descriptor == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown todo tool: " + name));
  }
  if (!Exposed(tools_, descriptor->name)) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "The todo tool group is disabled for the current execution mode"));
  }
  co_return co_await descriptor->execute(*state_, arguments_json);
}

} // namespace linecode::application
