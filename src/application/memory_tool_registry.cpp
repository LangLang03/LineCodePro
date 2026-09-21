#include "application/memory_tool_registry.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstddef>
#include <expected>
#include <iterator>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>

#include "domain/memory.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// cn.lineai.tool.builtin.MemoryUpdateTool.MAX_CONTENT_CHARS (line 19).
constexpr std::size_t kMaximumContentCharacters = 320;

// MemoryUpdateTool.getDescription() (lines 27-33), verbatim.
constexpr std::string_view kMemoryUpdateDescription =
    "Save a durable long-term memory for future sessions. "
    "Call only when the user states a lasting preference, project constraint, "
    "or environment fact "
    "that should persist across chats. Do not save one-off tasks, temporary "
    "progress, logs, secrets, "
    "or ordinary conversation content. "
    "scope: user (cross-project preference), project (this workspace only), "
    "environment (device/build setup).";

// MemoryUpdateTool.getParameters() (lines 66-81): properties content/scope with
// the legacy descriptions and the user|project|environment scope enum, required
// content only.
constexpr std::string_view kMemoryUpdateParameters =
    R"({"properties":{"content":{"description":"Independent durable memory statement, max 320 chars","type":"string"},"scope":{"description":"user | project | environment; default user","enum":["user","project","environment"],"type":"string"}},"required":["content"],"type":"object"})";

// feature-tool/src/main/res/values/strings.xml lines 84-88.
constexpr std::string_view kParametersEmptyMessage =
    "Parameters cannot be empty.";
constexpr std::string_view kContentEmptyMessage =
    "Memory content cannot be empty.";
constexpr std::string_view kStoreMissingMessage =
    "Memory store not initialized.";
constexpr std::string_view kSensitiveMessage =
    "Refused to store sensitive content as memory.";
constexpr std::string_view kUpdatedMessage = "Memory updated.";

struct MemoryToolContext final {
  MemoryStore *store{};
  ProjectWorkspaceController *projects{};
};

using MemoryToolExecutor =
    huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>> (*)(
        MemoryToolContext context, std::string arguments_json);

// Declarative tool table: Invoke dispatches through this row and Refresh
// projects it into the catalog, so adding a memory tool never edits dispatch
// code.
struct MemoryToolPlan final {
  std::string_view name;
  std::string_view description;
  std::string_view parameters_json;
  ToolPresentation presentation;
  bool allowed_in_read_only;
  AgentToolCategory agent_category;
  MemoryToolExecutor execute;
};

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(
      value, std::back_inserter(lowered),
      [](unsigned char byte) { return static_cast<char>(std::tolower(byte)); });
  return lowered;
}

std::size_t Utf8CharacterCount(std::string_view value) noexcept {
  return static_cast<std::size_t>(std::ranges::count_if(
      value, [](unsigned char byte) { return (byte & 0xC0U) != 0x80U; }));
}

std::size_t Utf8PrefixBytes(std::string_view value,
                            std::size_t characters) noexcept {
  std::size_t byte_index{};
  std::size_t count{};
  while (byte_index < value.size() && count < characters) {
    ++byte_index;
    while (byte_index < value.size() &&
           (static_cast<unsigned char>(value[byte_index]) & 0xC0U) == 0x80U) {
      ++byte_index;
    }
    ++count;
  }
  return byte_index;
}

// MemoryUpdateTool.looksSensitive (lines 119-132). The legacy needles are
// matched against the lowercased content; ASCII lowering leaves the Chinese
// needles untouched, exactly like Locale.ROOT lowering does.
bool LooksSensitive(std::string_view content) {
  constexpr std::array<std::string_view, 11> kNeedles{
      "api key", "apikey", "password", "passwd", "secret", "cookie",
      "token",   "私钥",   "密码",     "密钥",   "sk-"};
  const auto lowered = Lower(content);
  return std::ranges::any_of(kNeedles, [&lowered](std::string_view needle) {
    return lowered.find(needle) != std::string::npos;
  });
}

struct MemoryArguments final {
  domain::MemoryScope scope{domain::MemoryScope::user};
  std::string content;
};

// MemoryUpdateTool.execute (lines 84-106): empty parameters, empty content and
// the 320 character cap with the legacy truncation marker.
std::expected<MemoryArguments, ToolRegistryError>
ParseArguments(std::string_view text) {
  auto parsed = json::Parse(text);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kParametersEmptyMessage}));
  }
  const auto *raw = json::AsString(json::Find(*object, "content"));
  if (raw == nullptr) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kContentEmptyMessage}));
  }
  auto content = domain::NormalizeMemoryContent(*raw);
  if (content.empty()) {
    return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                 std::string{kContentEmptyMessage}));
  }
  if (Utf8CharacterCount(content) > kMaximumContentCharacters) {
    content = domain::NormalizeMemoryContent(std::string{content.substr(
        0U, Utf8PrefixBytes(content, kMaximumContentCharacters - 1U))});
    content += "。";
  }
  const auto *scope = json::AsString(json::Find(*object, "scope"));
  return MemoryArguments{
      .scope = domain::ParseMemoryScope(
          scope == nullptr ? std::string_view{} : std::string_view{*scope}),
      .content = std::move(content),
  };
}

// Legacy ToolContext.getHomePath() is the active workspace; the C++ app derives
// the memory project id from the selected catalog entry
// (presentation/main_screen.cpp:320-322) and falls back to the default project.
huxerui::Task<std::string> CurrentProjectId(MemoryToolContext context) {
  if (context.projects != nullptr) {
    auto selected = co_await context.projects->SelectedProject();
    if (selected && !selected->id.empty())
      co_return selected->id;
  }
  co_return std::string{domain::default_project_id};
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteMemoryUpdate(MemoryToolContext context, std::string arguments_json) {
  auto arguments = ParseArguments(arguments_json);
  if (!arguments)
    co_return std::unexpected(std::move(arguments.error()));
  if (LooksSensitive(arguments->content)) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kSensitiveMessage}));
  }
  if (context.store == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unavailable,
                                    std::string{kStoreMissingMessage}));
  }
  auto project_id = co_await CurrentProjectId(context);
  domain::MemoryRecord memory{
      .id = {},
      .scope = arguments->scope,
      .project_id = std::move(project_id),
      .content = std::move(arguments->content),
      .source = "manual",
      .confidence = 1.0,
      .created_at = 0,
      .updated_at = 0,
      .last_used_at = 0,
      .use_count = 0,
  };
  auto saved = co_await context.store->SaveManual(std::move(memory));
  if (!saved) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::invocation_failed, saved.error().message));
  }
  co_return ToolInvocationResult{.content = std::string{kUpdatedMessage},
                                 .error = false};
}

const std::array kMemoryToolPlans{
    MemoryToolPlan{
        .name = kMemoryUpdateToolName,
        .description = kMemoryUpdateDescription,
        .parameters_json = kMemoryUpdateParameters,
        .presentation = {.english_name = "Save memory",
                         .english_description =
                             "Save a durable preference, project constraint, "
                             "or environment fact.",
                         .chinese_name = "保存记忆",
                         .chinese_description =
                             "保存长期偏好、项目约束或环境信息。"},
        // BaseTool.isAllowedInReadonlyMode() defaults to false and
        // MemoryUpdateTool does not override it.
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::system,
        .execute = &ExecuteMemoryUpdate,
    },
};

const MemoryToolPlan *FindPlan(std::string_view name) noexcept {
  const auto found =
      std::ranges::find(kMemoryToolPlans, name, &MemoryToolPlan::name);
  return found == kMemoryToolPlans.end() ? nullptr : &*found;
}

bool MemoryGroupEnabled(const domain::McpExecutionSettings &settings) {
  const auto found =
      std::ranges::find(settings.groups, kMemoryToolGroupId,
                        [](const domain::McpToolGroupState &group) {
                          return std::string_view{group.id};
                        });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

} // namespace

MemoryToolRegistry::MemoryToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<MemoryStore> store,
    std::shared_ptr<ProjectWorkspaceController> projects)
    : settings_(std::move(settings)), store_(std::move(store)),
      projects_(std::move(projects)) {
  if (!settings_ || !store_ || !projects_) {
    throw std::invalid_argument(
        "MemoryToolRegistry requires memory store, project workspace and "
        "settings services");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
MemoryToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  tools_.clear();
  if (MemoryGroupEnabled(*settings)) {
    for (const auto &plan : kMemoryToolPlans) {
      tools_.push_back(RegisteredTool{
          .name = std::string{plan.name},
          .description = std::string{plan.description},
          .parameters_json = std::string{plan.parameters_json},
          .allowed_in_read_only = plan.allowed_in_read_only,
          .agent_category = plan.agent_category,
          .category = std::string{kMemoryToolGroupId},
          .agent_selectable = true,
          .agent_selected_by_default = false,
          .presentation = plan.presentation,
      });
    }
  }
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> MemoryToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
MemoryToolRegistry::Invoke(std::string name, std::string arguments_json) {
  const auto *plan = FindPlan(name);
  if (plan == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown memory tool: " + name));
  }
  if (std::ranges::find(tools_, name, &RegisteredTool::name) == tools_.end()) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unavailable,
              "Memory tools are disabled for the current execution mode"));
  }
  co_return co_await plan->execute(
      MemoryToolContext{.store = store_.get(), .projects = projects_.get()},
      std::move(arguments_json));
}

} // namespace linecode::application
