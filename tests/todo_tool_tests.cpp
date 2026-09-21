#include "gtest_support.h"
#include <algorithm>
#include <cstddef>
#include <expected>
#include <memory>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/todo_tool_registry.h"
#include "infrastructure/archive_json.h"

namespace {

using namespace linecode;

namespace json = infrastructure::archive_json;

// Verbatim legacy TodoUpdateTool.getDescription() (English source string).
constexpr std::string_view kLegacyDescription =
    "Maintain the current session's TODO list. Each call replaces the old list "
    "with the full list; "
    "the state is injected as {{TODO_STATE}} into the next system prompt, "
    "helping the model proceed in order and update progress promptly. "
    "Status values: pending (not started) / in_progress (in progress) / "
    "completed (done). "
    "At most 1 in_progress at a time; new tasks should be placed at the bottom "
    "of the list; "
    "when a task is complete, remove it from the list or set it to completed "
    "immediately, do not keep intermediate states.";

class FakeMcpSettings final : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    if (fail_load) {
      co_return std::unexpected(application::SettingsStoreError{
          .message = "injected execution-mode settings failure"});
    }
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
  bool fail_load{};
};

// In-memory stand-in for the platform TODO store (legacy TodoStateStore).
class MemoryTodoStore final : public application::TodoStateStore {
public:
  huxerui::Task<application::TodoStateResult<application::TodoState>>
  Replace(std::vector<application::TodoItem> items) override {
    if (failure)
      co_return std::unexpected(*failure);
    ++replaces;
    state.items = std::move(items);
    co_return state;
  }

  huxerui::Task<application::TodoStateResult<application::TodoState>>
  Load() override {
    if (failure)
      co_return std::unexpected(*failure);
    co_return state;
  }

  application::TodoState state;
  std::optional<application::TodoStateError> failure;
  std::size_t replaces{};
};

struct Scenario final {
  std::shared_ptr<FakeMcpSettings> mcp{std::make_shared<FakeMcpSettings>()};
  std::shared_ptr<MemoryTodoStore> store{std::make_shared<MemoryTodoStore>()};
  std::shared_ptr<application::TodoToolRegistry> tools{
      std::make_shared<application::TodoToolRegistry>(mcp, store)};
  bool done{};
};

std::shared_ptr<Scenario> active;

domain::McpToolGroupState &
TodoGroup(const std::shared_ptr<FakeMcpSettings> &settings) {
  const auto found =
      std::ranges::find(settings->value.groups, std::string{"todo"},
                        &domain::McpToolGroupState::id);
  EXPECT_EXPRESSION(found != settings->value.groups.end());
  return *found;
}

std::string StringAt(const json::Object *object, std::string_view key) {
  if (object == nullptr)
    return {};
  const auto *value = json::AsString(json::Find(*object, key));
  return value == nullptr ? std::string{} : *value;
}

std::string StringAt(const json::Value *value) {
  const auto *text = json::AsString(value);
  return text == nullptr ? std::string{} : *text;
}

const json::Object *ObjectAt(const json::Object *object, std::string_view key) {
  return object == nullptr ? nullptr : json::AsObject(json::Find(*object, key));
}

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      using application::ToolRegistryErrorCode;

      // 1. Legacy descriptor: name, description, JSON Schema and flags.
      auto refreshed = co_await scenario->tools->Refresh();
      EXPECT_EXPRESSION(refreshed);
      EXPECT_EXPRESSION(scenario->tools->Tools().size() == 1U);
      const auto &tool = scenario->tools->Tools().front();
      EXPECT_EXPRESSION(tool.name == application::kTodoUpdateToolName);
      EXPECT_EXPRESSION(tool.name == "todo_update");
      EXPECT_EXPRESSION(tool.description == kLegacyDescription);
      EXPECT_EXPRESSION(tool.category == "todo");
      // TodoUpdateTool does not override isAllowedInReadonlyMode().
      EXPECT_EXPRESSION(!tool.allowed_in_read_only);
      EXPECT_EXPRESSION(!tool.SupportsPermanentGrant());
      EXPECT_EXPRESSION(tool.agent_category ==
                        application::AgentToolCategory::system);
      EXPECT_EXPRESSION(tool.agent_selectable);
      EXPECT_EXPRESSION(!tool.agent_selected_by_default);

      auto parsed = json::Parse(tool.parameters_json);
      EXPECT_EXPRESSION(parsed);
      const auto *schema = json::AsObject(&*parsed);
      EXPECT_EXPRESSION(schema != nullptr);
      EXPECT_EXPRESSION(StringAt(schema, "type") == "object");
      const auto *required = json::AsArray(json::Find(*schema, "required"));
      EXPECT_EXPRESSION(required != nullptr);
      EXPECT_EXPRESSION(required->size() == 1U);
      EXPECT_EXPRESSION(json::AsString(&required->front()) != nullptr);
      EXPECT_EXPRESSION(*json::AsString(&required->front()) == "items");
      const auto *properties = ObjectAt(schema, "properties");
      EXPECT_EXPRESSION(properties != nullptr);
      EXPECT_EXPRESSION(properties->size() == 1U);
      const auto *items_property = ObjectAt(properties, "items");
      EXPECT_EXPRESSION(items_property != nullptr);
      EXPECT_EXPRESSION(StringAt(items_property, "type") == "array");
      EXPECT_EXPRESSION(StringAt(items_property, "description") ==
                        "Complete TODO list; replaces the old list.");
      const auto *entry = ObjectAt(items_property, "items");
      EXPECT_EXPRESSION(entry != nullptr);
      EXPECT_EXPRESSION(StringAt(entry, "type") == "object");
      const auto *entry_properties = ObjectAt(entry, "properties");
      EXPECT_EXPRESSION(entry_properties != nullptr);
      EXPECT_EXPRESSION(entry_properties->size() == 2U);
      const auto *content = ObjectAt(entry_properties, "content");
      EXPECT_EXPRESSION(content != nullptr);
      EXPECT_EXPRESSION(StringAt(content, "type") == "string");
      EXPECT_EXPRESSION(StringAt(content, "description") ==
                        "Task content, concise and verifiable");
      const auto *status = ObjectAt(entry_properties, "status");
      EXPECT_EXPRESSION(status != nullptr);
      EXPECT_EXPRESSION(StringAt(status, "type") == "string");
      EXPECT_EXPRESSION(StringAt(status, "description") ==
                        "Task status: pending / in_progress / completed");
      const auto *statuses = json::AsArray(json::Find(*status, "enum"));
      EXPECT_EXPRESSION(statuses != nullptr);
      EXPECT_EXPRESSION(statuses->size() == 3U);
      EXPECT_EXPRESSION(StringAt(&statuses->at(0)) ==
                        application::kTodoStatusPending);
      EXPECT_EXPRESSION(StringAt(&statuses->at(1)) ==
                        application::kTodoStatusInProgress);
      EXPECT_EXPRESSION(StringAt(&statuses->at(2)) ==
                        application::kTodoStatusCompleted);

      // 2. Success path: empty entries dropped, statuses normalized, legacy
      // summary wording.
      auto invoked = co_await scenario->tools->Invoke(
          "todo_update",
          R"({"items":[{"content":"draft registry","status":"in_progress"},{"content":"wire tests","status":"completed"},{"content":"legacy alias","status":"DONE"},{"content":"  padded content  "},"skip",42]})");
      EXPECT_EXPRESSION(invoked && !invoked->error);
      EXPECT_EXPRESSION(invoked->content ==
                        "TODO list updated, 4 item(s) total, 2 completed.");
      EXPECT_EXPRESSION(scenario->store->state.items.size() == 4U);
      EXPECT_EXPRESSION(scenario->store->state.items.at(0).content ==
                        "draft registry");
      EXPECT_EXPRESSION(scenario->store->state.items.at(0).status ==
                        application::kTodoStatusInProgress);
      EXPECT_EXPRESSION(scenario->store->state.items.at(2).status ==
                        application::kTodoStatusCompleted);
      EXPECT_EXPRESSION(scenario->store->state.items.at(3).content ==
                        "padded content");
      EXPECT_EXPRESSION(scenario->store->state.items.at(3).status ==
                        application::kTodoStatusPending);

      auto loaded = co_await scenario->store->Load();
      EXPECT_EXPRESSION(loaded && *loaded == scenario->store->state);
      EXPECT_EXPRESSION(application::RenderTodoState(*loaded) ==
                        "1. [in_progress] draft registry\n"
                        "2. [completed] wire tests\n"
                        "3. [completed] legacy alias\n"
                        "4. [pending] padded content");

      // 3. Legacy status vocabulary aliases.
      EXPECT_EXPRESSION(application::NormalizeTodoStatus(" In-Progress ") ==
                        application::kTodoStatusInProgress);
      EXPECT_EXPRESSION(application::NormalizeTodoStatus("Finished") ==
                        application::kTodoStatusCompleted);
      EXPECT_EXPRESSION(application::NormalizeTodoStatus("unknown") ==
                        application::kTodoStatusPending);
      EXPECT_EXPRESSION(application::NormalizeTodoStatus("") ==
                        application::kTodoStatusPending);

      // 4. Clearing the list.
      auto cleared =
          co_await scenario->tools->Invoke("todo_update", R"({"items":[]})");
      EXPECT_EXPRESSION(cleared && !cleared->error);
      EXPECT_EXPRESSION(cleared->content == "TODO list cleared.");
      EXPECT_EXPRESSION(scenario->store->state.empty());
      auto blank = co_await scenario->tools->Invoke(
          "todo_update", R"({"items":[{"content":"   ","status":"pending"}]})");
      EXPECT_EXPRESSION(blank && blank->content == "TODO list cleared.");

      // 5. Argument failures.
      auto malformed = co_await scenario->tools->Invoke("todo_update", "{");
      EXPECT_EXPRESSION(!malformed);
      EXPECT_EXPRESSION(malformed.error().code ==
                        ToolRegistryErrorCode::invalid_arguments);
      auto not_object = co_await scenario->tools->Invoke("todo_update", "[]");
      EXPECT_EXPRESSION(!not_object);
      EXPECT_EXPRESSION(not_object.error().code ==
                        ToolRegistryErrorCode::invalid_arguments);
      auto missing = co_await scenario->tools->Invoke("todo_update", "{}");
      EXPECT_EXPRESSION(!missing);
      EXPECT_EXPRESSION(missing.error().code ==
                        ToolRegistryErrorCode::invalid_arguments);
      EXPECT_EXPRESSION(missing.error().message == "Missing items array.");
      auto unknown =
          co_await scenario->tools->Invoke("todo_list", R"({"items":[]})");
      EXPECT_EXPRESSION(!unknown);
      EXPECT_EXPRESSION(unknown.error().code ==
                        ToolRegistryErrorCode::unknown_tool);

      // 6. Store failures surface as invocation failures; an uninitialized
      // store keeps the legacy wording.
      scenario->store->failure = application::TodoStateError{
          .code = application::TodoStateErrorCode::write_failed,
          .message = "injected todo write failure"};
      auto write_failed =
          co_await scenario->tools->Invoke("todo_update", R"({"items":[]})");
      EXPECT_EXPRESSION(!write_failed);
      EXPECT_EXPRESSION(write_failed.error().code ==
                        ToolRegistryErrorCode::invocation_failed);
      EXPECT_EXPRESSION(write_failed.error().message ==
                        "injected todo write failure");
      scenario->store->failure = application::TodoStateError{
          .code = application::TodoStateErrorCode::unavailable, .message = {}};
      auto unavailable =
          co_await scenario->tools->Invoke("todo_update", R"({"items":[]})");
      EXPECT_EXPRESSION(!unavailable);
      EXPECT_EXPRESSION(unavailable.error().code ==
                        ToolRegistryErrorCode::unavailable);
      EXPECT_EXPRESSION(unavailable.error().message ==
                        "TODO state store not initialized.");
      scenario->store->failure.reset();

      // 7. Group disabled: no catalog entry and no invocation.
      TodoGroup(scenario->mcp).enabled = false;
      refreshed = co_await scenario->tools->Refresh();
      EXPECT_EXPRESSION(refreshed);
      EXPECT_EXPRESSION(scenario->tools->Tools().empty());
      const auto writes_before = scenario->store->replaces;
      auto disabled =
          co_await scenario->tools->Invoke("todo_update", R"({"items":[]})");
      EXPECT_EXPRESSION(!disabled);
      EXPECT_EXPRESSION(disabled.error().code ==
                        ToolRegistryErrorCode::unavailable);
      EXPECT_EXPRESSION(scenario->store->replaces == writes_before);

      // 8. Group enabled but the current execution mode is unsupported.
      auto &group = TodoGroup(scenario->mcp);
      group.enabled = true;
      group.supported_modes = domain::McpExecutionModeMask::local;
      scenario->mcp->value.mode = domain::McpExecutionMode::ssh;
      refreshed = co_await scenario->tools->Refresh();
      EXPECT_EXPRESSION(refreshed);
      EXPECT_EXPRESSION(scenario->tools->Tools().empty());
      auto unsupported_mode =
          co_await scenario->tools->Invoke("todo_update", R"({"items":[]})");
      EXPECT_EXPRESSION(!unsupported_mode);
      EXPECT_EXPRESSION(unsupported_mode.error().code ==
                        ToolRegistryErrorCode::unavailable);

      // 9. The todo group is mode agnostic: every supported mode exposes it.
      group.supported_modes = domain::McpExecutionModeMask::all;
      refreshed = co_await scenario->tools->Refresh();
      EXPECT_EXPRESSION(refreshed);
      EXPECT_EXPRESSION(scenario->tools->Tools().size() == 1U);
      auto remote = co_await scenario->tools->Invoke(
          "todo_update", R"({"items":[{"content":"ssh item"}]})");
      EXPECT_EXPRESSION(remote && !remote->error);
      EXPECT_EXPRESSION(remote->content ==
                        "TODO list updated, 1 item(s) total, 0 completed.");
      EXPECT_EXPRESSION(scenario->store->state.items.front().status ==
                        application::kTodoStatusPending);

      // 10. Settings load failures propagate as load_failed.
      scenario->mcp->fail_load = true;
      auto failed = co_await scenario->tools->Refresh();
      EXPECT_EXPRESSION(!failed);
      EXPECT_EXPRESSION(failed.error().code ==
                        ToolRegistryErrorCode::load_failed);
      EXPECT_EXPRESSION(failed.error().message.contains("injected"));
      scenario->mcp->fail_load = false;

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("todo-tool-registry-probe");
}

} // namespace

TEST(todo_tool_tests, LegacySuite) {
  // Dependencies are mandatory: the registry fails fast on null services.
  active = std::make_shared<Scenario>();
  bool rejected_settings = false;
  try {
    application::TodoToolRegistry invalid(nullptr, active->store);
  } catch (const std::invalid_argument &) {
    rejected_settings = true;
  }
  EXPECT_EXPRESSION(rejected_settings);
  bool rejected_state = false;
  try {
    application::TodoToolRegistry invalid(active->mcp, nullptr);
  } catch (const std::invalid_argument &) {
    rejected_state = true;
  }
  EXPECT_EXPRESSION(rejected_state);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  EXPECT_EXPRESSION(active->store->replaces == 4U);
  active.reset();
}
