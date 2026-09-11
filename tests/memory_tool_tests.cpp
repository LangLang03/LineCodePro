#include <algorithm>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/memory_tool_registry.h"
#include "application/ports/memory_store.h"
#include "application/ports/project_workspace_controller.h"
#include "domain/memory.h"
#include "domain/project_workspace.h"
#include "infrastructure/archive_json.h"

namespace {

using namespace linecode;

using application::MemoryStoreResult;
using application::McpExecutionSettingsService;
using application::ProjectWorkspaceResult;
using application::SettingsResult;
using domain::McpExecutionSettings;
using domain::McpExecutionMode;
using domain::McpExecutionModeMask;
using domain::McpToolGroupState;
using domain::MemoryConversationTurn;
using domain::MemoryOverview;
using domain::MemoryRecord;
using domain::MemoryScope;
using domain::ProjectCatalog;
using domain::ProjectFileNode;
using domain::ProjectRecord;

constexpr std::string_view kMemoryGroup = "memory";

// Legacy cn.lineai.tool.builtin.MemoryUpdateTool literals kept next to the
// assertions so an accidental edit of the registry shows up as a test failure.
constexpr std::string_view kExpectedDescription =
    "Save a durable long-term memory for future sessions. Call only when the "
    "user states a lasting preference, project constraint, or environment "
    "fact that should persist across chats. Do not save one-off tasks, "
    "temporary progress, logs, secrets, or ordinary conversation content. "
    "scope: user (cross-project preference), project (this workspace only), "
    "environment (device/build setup).";
constexpr std::string_view kExpectedParameters =
    R"({"properties":{"content":{"description":"Independent durable memory statement, max 320 chars","type":"string"},"scope":{"description":"user | project | environment; default user","enum":["user","project","environment"],"type":"string"}},"required":["content"],"type":"object"})";

class StubExecutionSettings final : public McpExecutionSettingsService {
public:
  huxerui::Task<SettingsResult<McpExecutionSettings>> Load() override {
    if (fail_load) {
      co_return std::unexpected(
          application::SettingsStoreError{.message = "settings unavailable"});
    }
    co_return value;
  }

  huxerui::Task<SettingsResult<void>>
  SetMode(McpExecutionMode mode) override {
    value.mode = mode;
    co_return SettingsResult<void>{};
  }

  huxerui::Task<SettingsResult<void>>
  SetToolGroupEnabled(McpExecutionMode, std::string id, bool enabled) override {
    const auto found =
        std::ranges::find(value.groups, id, &McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return SettingsResult<void>{};
  }

  McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
  bool fail_load{};
};

class StubMemoryStore final : public application::MemoryStore {
public:
  huxerui::Task<MemoryStoreResult<MemoryOverview>>
  LoadOverview(std::string) override {
    co_return MemoryOverview{};
  }

  huxerui::Task<MemoryStoreResult<MemoryRecord>>
  SaveManual(MemoryRecord memory) override {
    if (fail_save) {
      co_return std::unexpected(
          application::MemoryStoreError{.message = "memory write failed"});
    }
    ++save_count;
    saved.push_back(std::move(memory));
    co_return saved.back();
  }

  huxerui::Task<MemoryStoreResult<void>>
  Delete(std::vector<std::string>) override {
    co_return MemoryStoreResult<void>{};
  }

  huxerui::Task<MemoryStoreResult<application::MemoryRetrievalCorpus>>
  LoadRetrievalCorpus(std::string, std::string) override {
    co_return application::MemoryRetrievalCorpus{};
  }

  huxerui::Task<MemoryStoreResult<std::vector<MemoryRecord>>>
  LoadManualMemories(std::string) override {
    co_return std::vector<MemoryRecord>{};
  }

  huxerui::Task<MemoryStoreResult<void>>
  MarkUsed(std::vector<std::string>) override {
    co_return MemoryStoreResult<void>{};
  }

  huxerui::Task<MemoryStoreResult<MemoryRecord>>
  SaveExtracted(MemoryRecord memory) override {
    co_return std::move(memory);
  }

  huxerui::Task<MemoryStoreResult<void>>
  IndexConversationTurn(MemoryConversationTurn) override {
    co_return MemoryStoreResult<void>{};
  }

  bool fail_save{};
  int save_count{};
  std::vector<MemoryRecord> saved;
};

application::ProjectWorkspaceError Unsupported() {
  return {.code = application::ProjectWorkspaceErrorCode::io,
          .message = "unsupported in this test"};
}

class StubProjectWorkspace final
    : public application::ProjectWorkspaceController {
public:
  huxerui::Task<ProjectWorkspaceResult<std::vector<ProjectRecord>>>
  ListProjects() override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<ProjectRecord>>
  SelectedProject() override {
    if (!selected)
      co_return std::unexpected(Unsupported());
    co_return *selected;
  }

  huxerui::Task<ProjectWorkspaceResult<ProjectRecord>>
  CreateManagedProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<ProjectRecord>>
  RegisterExternalProject(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<ProjectRecord>>
  SelectProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  DeleteProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<ProjectFileNode>>
  LoadTree(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  CreateFile(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  CreateDirectory(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<std::string>>
  ReadText(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  WriteText(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  Rename(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  Copy(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  Move(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<ProjectWorkspaceResult<void>>
  Delete(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  std::optional<ProjectRecord> selected{ProjectRecord{
      .id = "project-7",
      .label = "LineCodePro",
      .path = "/tmp/linecodepro",
      .source = domain::ProjectSource::managed,
      .description = {},
      .selected = true,
      .created_at = 0,
      .updated_at = 0,
  }};
};

struct Scenario final {
  std::shared_ptr<StubExecutionSettings> settings;
  std::shared_ptr<StubMemoryStore> store;
  std::shared_ptr<StubProjectWorkspace> projects;
  std::shared_ptr<application::MemoryToolRegistry> tools;
  bool done{};
};

std::shared_ptr<Scenario> active;

namespace json = infrastructure::archive_json;

void ExpectRegistryError(const std::expected<application::ToolInvocationResult,
                                             application::ToolRegistryError>
                             &result,
                         application::ToolRegistryErrorCode code,
                         std::string_view message) {
  assert(!result);
  assert(result.error().code == code);
  assert(result.error().message == message);
}

void CatalogContract(const Scenario &scenario) {
  const auto catalog = scenario.tools->Tools();
  assert(catalog.size() == 1U);
  const auto &descriptor = catalog.front();
  assert(descriptor.name == application::kMemoryUpdateToolName);
  assert(descriptor.name == "memory_update");
  assert(descriptor.description == kExpectedDescription);
  assert(descriptor.parameters_json == kExpectedParameters);
  assert(descriptor.category == kMemoryGroup);
  // BaseTool.isAllowedInReadonlyMode() defaults to false (BaseTool.java:29-31)
  // and MemoryUpdateTool never overrides it.
  assert(!descriptor.allowed_in_read_only);
  assert(!descriptor.permanent_grant_supported);

  auto parameters = json::Parse(descriptor.parameters_json);
  assert(parameters);
  const auto *object = json::AsObject(&*parameters);
  assert(object != nullptr);
  const auto *type = json::AsString(json::Find(*object, "type"));
  assert(type != nullptr && *type == "object");
  const auto *properties = json::AsObject(json::Find(*object, "properties"));
  assert(properties != nullptr && properties->size() == 2U);
  const auto *content = json::AsObject(json::Find(*properties, "content"));
  assert(content != nullptr);
  const auto *content_type = json::AsString(json::Find(*content, "type"));
  assert(content_type != nullptr && *content_type == "string");
  const auto *content_description =
      json::AsString(json::Find(*content, "description"));
  assert(content_description != nullptr &&
         *content_description ==
             "Independent durable memory statement, max 320 chars");
  const auto *scope = json::AsObject(json::Find(*properties, "scope"));
  assert(scope != nullptr);
  const auto *scope_description =
      json::AsString(json::Find(*scope, "description"));
  assert(scope_description != nullptr &&
         *scope_description == "user | project | environment; default user");
  const auto *scopes = json::AsArray(json::Find(*scope, "enum"));
  assert(scopes != nullptr && scopes->size() == 3U);
  assert(json::AsString(&scopes->at(0)) != nullptr &&
         *json::AsString(&scopes->at(0)) == "user");
  assert(json::AsString(&scopes->at(1)) != nullptr &&
         *json::AsString(&scopes->at(1)) == "project");
  assert(json::AsString(&scopes->at(2)) != nullptr &&
         *json::AsString(&scopes->at(2)) == "environment");
  const auto *required = json::AsArray(json::Find(*object, "required"));
  assert(required != nullptr && required->size() == 1U);
  assert(json::AsString(&required->front()) != nullptr &&
         *json::AsString(&required->front()) == "content");
}

} // namespace

namespace {

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto refreshed = co_await scenario->tools->Refresh();
      assert(refreshed);
      CatalogContract(*scenario);

      // Success: the descriptor's scope and the selected project are stored.
      auto invoked = co_await scenario->tools->Invoke(
          "memory_update",
          R"({"content":"Prefer C++23 for new code.","scope":"project"})");
      assert(invoked);
      assert(!invoked->error);
      assert(invoked->content == "Memory updated.");
      assert(scenario->store->save_count == 1);
      const auto &saved = scenario->store->saved.front();
      assert(saved.content == "Prefer C++23 for new code.");
      assert(saved.scope == MemoryScope::project);
      assert(saved.project_id == "project-7");
      assert(saved.source == "manual");
      assert(saved.confidence == 1.0);
      assert(saved.id.empty());

      // An unknown or missing scope falls back to the user scope.
      invoked = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Cross project preference."})");
      assert(invoked);
      assert(scenario->store->saved.back().scope == MemoryScope::user);
      invoked = co_await scenario->tools->Invoke(
          "memory_update",
          R"({"content":"Mixed case scope.","scope":"PROJECT"})");
      assert(invoked);
      assert(scenario->store->saved.back().scope == MemoryScope::project);
      invoked = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Unknown scope.","scope":"planet"})");
      assert(invoked);
      assert(scenario->store->saved.back().scope == MemoryScope::user);

      // Legacy truncation: substring(0, MAX_CONTENT_CHARS - 1).trim() plus the
      // full stop marker, i.e. 320 characters (319 ASCII bytes plus one
      // three-byte marker here).
      const std::string long_content(400U, 'a');
      invoked = co_await scenario->tools->Invoke(
          "memory_update",
          json::Serialize(json::Object{{"content", long_content}}));
      assert(invoked);
      const auto &truncated = scenario->store->saved.back().content;
      assert(truncated.size() == 319U + std::string_view{"。"}.size());
      assert(truncated.ends_with("。"));
      assert(truncated.starts_with(std::string(10U, 'a')));

      // Sensitive content is refused before touching the store.
      const auto saves_before = scenario->store->save_count;
      auto rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"The API key is sk-1234567890"})");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Refused to store sensitive content as memory.");
      rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"记住我的密码是 123456"})");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Refused to store sensitive content as memory.");
      assert(scenario->store->save_count == saves_before);

      // Argument parsing branches.
      rejected = co_await scenario->tools->Invoke("memory_update", "{}");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Memory content cannot be empty.");
      rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"   "})");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Memory content cannot be empty.");
      rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":42})");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Memory content cannot be empty.");
      rejected = co_await scenario->tools->Invoke("memory_update", "not json");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Parameters cannot be empty.");
      rejected = co_await scenario->tools->Invoke("memory_update", "[]");
      ExpectRegistryError(rejected,
                          application::ToolRegistryErrorCode::invalid_arguments,
                          "Parameters cannot be empty.");

      // Unknown tools are rejected before any storage work.
      rejected = co_await scenario->tools->Invoke("memory_delete", "{}");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unknown_tool);

      // Store failures surface as invocation_failed.
      scenario->store->fail_save = true;
      auto failed = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Store failure."})");
      ExpectRegistryError(failed,
                          application::ToolRegistryErrorCode::invocation_failed,
                          "memory write failed");
      scenario->store->fail_save = false;

      // An unreadable project catalog falls back to the default project id.
      scenario->projects->selected = std::nullopt;
      invoked = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Default project memory."})");
      assert(invoked);
      assert(scenario->store->saved.back().project_id ==
             std::string{domain::default_project_id});

      // Group disabled: not exposed and not invokable.
      const auto disable_group = [scenario](bool enabled) {
        const auto found = std::ranges::find(scenario->settings->value.groups,
                                             std::string{kMemoryGroup},
                                             &McpToolGroupState::id);
        assert(found != scenario->settings->value.groups.end());
        found->enabled = enabled;
      };
      disable_group(false);
      refreshed = co_await scenario->tools->Refresh();
      assert(refreshed);
      assert(scenario->tools->Tools().empty());
      rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Disabled."})");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unavailable);

      // Enabled but unsupported for the active execution mode.
      scenario->settings->value.groups.clear();
      scenario->settings->value.groups.push_back(McpToolGroupState{
          .id = std::string{kMemoryGroup},
          .enabled = true,
          .supported_modes = McpExecutionModeMask::local,
      });
      scenario->settings->value.mode = McpExecutionMode::ssh;
      refreshed = co_await scenario->tools->Refresh();
      assert(refreshed);
      assert(scenario->tools->Tools().empty());
      rejected = co_await scenario->tools->Invoke(
          "memory_update", R"({"content":"Unsupported mode."})");
      assert(!rejected);
      assert(rejected.error().code ==
             application::ToolRegistryErrorCode::unavailable);

      // A settings failure fails the refresh instead of exposing tools.
      scenario->settings->fail_load = true;
      auto load_failed = co_await scenario->tools->Refresh();
      assert(!load_failed);
      assert(load_failed.error().code ==
             application::ToolRegistryErrorCode::load_failed);
      assert(scenario->tools->Tools().empty());
      scenario->settings->fail_load = false;

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("memory-tool-probe");
}

} // namespace

int main() {
  active = std::make_shared<Scenario>();
  active->settings = std::make_shared<StubExecutionSettings>();
  active->store = std::make_shared<StubMemoryStore>();
  active->projects = std::make_shared<StubProjectWorkspace>();
  active->tools = std::make_shared<application::MemoryToolRegistry>(
      active->settings, active->store, active->projects);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}
