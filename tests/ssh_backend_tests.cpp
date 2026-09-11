#include <algorithm>
#include <cassert>
#include <chrono>
#include <cstddef>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/execution_mode_project_workspace.h"
#include "application/ssh_project_workspace.h"
#include "application/ssh_runtime_service.h"
#include "application/ssh_tool_registry.h"
#include "application/ssh_workspace_service.h"

namespace {

using namespace std::chrono_literals;
using namespace linecode;

application::SshError Missing() {
  return {.code = application::SshErrorCode::not_found, .message = "missing"};
}

struct FakeRemote final {
  std::unordered_map<std::string, application::SshFileEntry> entries{
      {"/home/line",
       {.name = "line",
        .path = "/home/line",
        .kind = application::SshFileKind::directory}},
      {"/home/line/src",
       {.name = "src",
        .path = "/home/line/src",
        .kind = application::SshFileKind::directory}},
      {"/home/line/src/main.cpp",
       {.name = "main.cpp",
        .path = "/home/line/src/main.cpp",
        .kind = application::SshFileKind::regular,
        .size = 12}},
      {"/home/line/readme.md",
       {.name = "readme.md",
        .path = "/home/line/readme.md",
        .kind = application::SshFileKind::regular,
        .size = 6}},
      {"/home/line/link",
       {.name = "link",
        .path = "/home/line/link",
        .kind = application::SshFileKind::symbolic_link}},
  };
  std::unordered_map<std::string, std::string> contents{
      {"/home/line/src/main.cpp", "int main(){}"},
      {"/home/line/readme.md", "readme"},
  };
  std::optional<application::SshCommandRequest> command;
};

class FakeSession final : public application::SshSession {
public:
  explicit FakeSession(std::shared_ptr<FakeRemote> remote)
      : remote_(std::move(remote)) {}

  application::SshResult<application::SshCommandOutput>
  Execute(const application::SshCommandRequest &request,
          std::stop_token stop) override {
    if (stop.stop_requested())
      return std::unexpected(
          application::SshError{.code = application::SshErrorCode::cancelled,
                                .message = "cancelled"});
    remote_->command = request;
    return application::SshCommandOutput{
        .exit_status = request.command == "false" ? 1 : 0,
        .standard_output = "remote output\n",
        .standard_error = request.command == "false" ? "failed\n" : "",
    };
  }

  application::SshResult<std::string> CanonicalPath(std::string_view path,
                                                    std::stop_token) override {
    if (path == "~" || path == "/home/line")
      return std::string{"/home/line"};
    return std::unexpected(Missing());
  }

  application::SshResult<application::SshFileEntry>
  Stat(std::string_view path, bool, std::stop_token) override {
    const auto found = remote_->entries.find(std::string{path});
    if (found == remote_->entries.end())
      return std::unexpected(Missing());
    return found->second;
  }

  application::SshResult<std::vector<application::SshFileEntry>>
  List(std::string_view path, std::stop_token) override {
    std::vector<application::SshFileEntry> result;
    const std::string prefix = std::string{path} + "/";
    for (const auto &[entry_path, entry] : remote_->entries) {
      if (entry_path.starts_with(prefix) &&
          entry_path.find('/', prefix.size()) == std::string::npos)
        result.push_back(entry);
    }
    return result;
  }

  application::SshResult<std::vector<std::byte>>
  Read(std::string_view path, std::size_t maximum_bytes,
       std::stop_token) override {
    const auto found = remote_->contents.find(std::string{path});
    if (found == remote_->contents.end())
      return std::unexpected(Missing());
    if (found->second.size() > maximum_bytes)
      return std::unexpected(application::SshError{
          .code = application::SshErrorCode::size_limit, .message = "large"});
    const auto bytes = std::as_bytes(std::span{found->second});
    return std::vector<std::byte>{bytes.begin(), bytes.end()};
  }

  application::SshResult<void> Write(std::string_view path,
                                     std::span<const std::byte> value,
                                     bool overwrite, std::stop_token) override {
    const std::string target{path};
    if (!overwrite && remote_->entries.contains(target))
      return std::unexpected(application::SshError{
          .code = application::SshErrorCode::conflict, .message = "exists"});
    remote_->contents[target] =
        std::string{reinterpret_cast<const char *>(value.data()), value.size()};
    remote_->entries[target] = {
        .name = target.substr(target.find_last_of('/') + 1U),
        .path = target,
        .kind = application::SshFileKind::regular,
        .size = value.size(),
    };
    return {};
  }

  application::SshResult<void> CreateDirectory(std::string_view path,
                                               std::stop_token) override {
    const std::string target{path};
    if (remote_->entries.contains(target))
      return std::unexpected(application::SshError{
          .code = application::SshErrorCode::conflict, .message = "exists"});
    remote_->entries[target] = {
        .name = target.substr(target.find_last_of('/') + 1U),
        .path = target,
        .kind = application::SshFileKind::directory,
    };
    return {};
  }

  application::SshResult<void> Rename(std::string_view source,
                                      std::string_view destination,
                                      bool overwrite,
                                      std::stop_token) override {
    const std::string from{source};
    const std::string to{destination};
    const auto found = remote_->entries.find(from);
    if (found == remote_->entries.end())
      return std::unexpected(Missing());
    if (!overwrite && remote_->entries.contains(to))
      return std::unexpected(application::SshError{
          .code = application::SshErrorCode::conflict, .message = "exists"});
    auto entry = found->second;
    remote_->entries.erase(found);
    entry.path = to;
    entry.name = to.substr(to.find_last_of('/') + 1U);
    remote_->entries[to] = entry;
    if (auto moved = remote_->contents.extract(from); !moved.empty()) {
      moved.key() = to;
      remote_->contents.insert(std::move(moved));
    }
    return {};
  }

  application::SshResult<void> RemoveFile(std::string_view path,
                                          std::stop_token) override {
    remote_->contents.erase(std::string{path});
    return remote_->entries.erase(std::string{path}) != 0U
               ? application::SshResult<void>{}
               : application::SshResult<void>{std::unexpected(Missing())};
  }

  application::SshResult<void> RemoveDirectory(std::string_view path,
                                               std::stop_token) override {
    return remote_->entries.erase(std::string{path}) != 0U
               ? application::SshResult<void>{}
               : application::SshResult<void>{std::unexpected(Missing())};
  }

private:
  std::shared_ptr<FakeRemote> remote_;
};

class FakeTransport final : public application::SshTransport {
public:
  explicit FakeTransport(std::shared_ptr<FakeRemote> remote)
      : remote(std::move(remote)) {}

  application::SshResult<std::unique_ptr<application::SshSession>>
  Connect(const domain::SshConfig &config, std::chrono::milliseconds,
          std::stop_token stop) override {
    ++connections;
    if (stop.stop_requested())
      return std::unexpected(
          application::SshError{.code = application::SshErrorCode::cancelled,
                                .message = "cancelled"});
    if (!config.IsConfigured())
      return std::unexpected(application::SshError{
          .code = application::SshErrorCode::not_configured,
          .message = "not configured"});
    return std::unique_ptr<application::SshSession>{
        std::make_unique<FakeSession>(remote)};
  }

  std::shared_ptr<FakeRemote> remote;
  std::size_t connections{};
};

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
  domain::McpExecutionSettings value{
      domain::DefaultMcpExecutionSettings(domain::McpExecutionMode::ssh)};
  bool fail_load{};
};

class FakeSshSettings final : public application::SshSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::SshConfig>>
  Load() override {
    co_return config;
  }
  huxerui::Task<application::SettingsResult<void>>
  Save(domain::SshConfig value) override {
    config = std::move(value);
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SshConnectionResult<std::string>>
  Test(domain::SshConfig) override {
    co_return std::string{"unused"};
  }
  domain::SshConfig config{.host = "host",
                           .port = 22,
                           .username = "line",
                           .password = "password",
                           .private_key = {},
                           .passphrase = {}};
};

class MemoryProjectCatalog final : public application::ProjectCatalogStore {
public:
  MemoryProjectCatalog() = default;
  explicit MemoryProjectCatalog(domain::ProjectCatalog initial)
      : catalog(std::move(initial)) {}

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectCatalog>>
  LoadCatalog() override {
    co_return catalog;
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  ReplaceCatalog(domain::ProjectCatalog value) override {
    catalog = std::move(value);
    co_return application::ProjectWorkspaceResult<void>{};
  }

  domain::ProjectCatalog catalog;
};

class FixedWorkspaceClock final : public application::WorkspaceClock {
public:
  std::int64_t NowMilliseconds() const noexcept override { return 42; }
};

struct Scenario final {
  std::shared_ptr<FakeRemote> remote{std::make_shared<FakeRemote>()};
  std::shared_ptr<FakeTransport> transport{
      std::make_shared<FakeTransport>(remote)};
  std::shared_ptr<application::SshRuntimeService> runtime{
      std::make_shared<application::SshRuntimeService>(transport)};
  std::shared_ptr<FakeMcpSettings> mcp{std::make_shared<FakeMcpSettings>()};
  std::shared_ptr<FakeSshSettings> settings{
      std::make_shared<FakeSshSettings>()};
  std::shared_ptr<application::SshToolRegistry> tools{
      std::make_shared<application::SshToolRegistry>(mcp, settings, runtime)};
  std::shared_ptr<application::SshWorkspaceService> workspace{
      std::make_shared<application::SshWorkspaceService>(transport)};
  std::shared_ptr<MemoryProjectCatalog> catalog{
      std::make_shared<MemoryProjectCatalog>()};
  std::shared_ptr<MemoryProjectCatalog> local_catalog{
      std::make_shared<MemoryProjectCatalog>(domain::ProjectCatalog{
          .projects = {{.id = "local-sentinel",
                        .label = "Local route",
                        .path = "/home/line",
                        .source = domain::ProjectSource::ssh,
                        .description = "routing probe",
                        .selected = true,
                        .created_at = 1,
                        .updated_at = 1}},
          .selected_id = "local-sentinel"})};
  std::shared_ptr<FixedWorkspaceClock> clock{
      std::make_shared<FixedWorkspaceClock>()};
  std::shared_ptr<application::SshProjectWorkspace> ssh_projects{
      std::make_shared<application::SshProjectWorkspace>(catalog, settings,
                                                         workspace, clock)};
  std::shared_ptr<application::SshProjectWorkspace> local_projects{
      std::make_shared<application::SshProjectWorkspace>(
          local_catalog, settings, workspace, clock)};
  std::shared_ptr<application::ExecutionModeProjectWorkspace> projects{
      std::make_shared<application::ExecutionModeProjectWorkspace>(
          mcp, std::vector<application::ProjectWorkspaceRoute>{
                   {.mode = domain::McpExecutionMode::local,
                    .controller = local_projects},
                   {.mode = domain::McpExecutionMode::ssh,
                    .controller = ssh_projects}})};
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto refreshed = co_await scenario->tools->Refresh();
      assert(refreshed);
      assert(scenario->tools->Tools().size() == 1U);
      assert(scenario->tools->Tools().front().name == "shell_execute");

      auto invoked = co_await scenario->tools->Invoke(
          "shell_execute",
          R"({"command":"pwd","cwd":"/home/line","timeoutMs":50})");
      assert(invoked && !invoked->error);
      assert(invoked->content.contains("remote output"));
      assert(scenario->remote->command);
      assert(scenario->remote->command->timeout == 1s);
      assert(scenario->remote->command->working_directory == "/home/line");

      auto tree = co_await scenario->workspace->LoadTree(
          scenario->settings->config, "~");
      assert(tree && tree->name == "line");
      assert(tree->children.front().directory);
      assert(tree->children.front().name == "src");

      auto text = co_await scenario->workspace->ReadText(
          scenario->settings->config, "~", "readme.md");
      assert(text && *text == "readme");

      auto traversal = co_await scenario->workspace->ReadText(
          scenario->settings->config, "~", "../secret");
      assert(!traversal);
      assert(traversal.error().code ==
             application::SshErrorCode::outside_workspace);

      auto link = co_await scenario->workspace->ReadText(
          scenario->settings->config, "~", "link/secret");
      assert(!link);
      assert(link.error().code == application::SshErrorCode::symbolic_link);

      auto protected_write = co_await scenario->workspace->WriteText(
          scenario->settings->config, "~", ".linecode/config", "secret");
      assert(!protected_write);
      assert(protected_write.error().code ==
             application::SshErrorCode::protected_path);

      auto created = co_await scenario->workspace->CreateFile(
          scenario->settings->config, "~", "new.txt");
      assert(created);
      assert(scenario->remote->entries.contains("/home/line/new.txt"));

      auto protected_rename = co_await scenario->workspace->Rename(
          scenario->settings->config, "~", "new.txt", ".linecode");
      assert(!protected_rename);
      assert(protected_rename.error().code ==
             application::SshErrorCode::protected_path);

      std::string long_name{"a"};
      for (std::size_t index{}; index < 61U; ++index)
        long_name += "界";
      auto managed = co_await scenario->workspace->CreateManagedProject(
          scenario->settings->config, long_name);
      std::string expected_name{"a"};
      for (std::size_t index{}; index < 59U; ++index)
        expected_name += "界";
      assert(managed);
      assert(*managed == "/home/line/.linecode/project/" + expected_name);

      auto projects = co_await scenario->projects->ListProjects();
      assert(projects && projects->size() == 1U);
      assert(projects->front().id == application::kDefaultSshProjectId);
      assert(projects->front().source == domain::ProjectSource::ssh);
      auto selected = co_await scenario->projects->SelectedProject();
      assert(selected && selected->path.empty());
      auto routed_text = co_await scenario->projects->ReadText(
          std::string{application::kDefaultSshProjectId}, "readme.md");
      assert(routed_text && *routed_text == "readme");

      scenario->mcp->value.mode = domain::McpExecutionMode::local;
      auto local = co_await scenario->projects->SelectedProject();
      assert(local && local->id == "local-sentinel");
      scenario->mcp->value.mode = domain::McpExecutionMode::terminal_provider;
      auto unavailable = co_await scenario->projects->ListProjects();
      assert(!unavailable);
      assert(unavailable.error().message.contains("No workspace backend"));
      scenario->mcp->fail_load = true;
      auto settings_failure = co_await scenario->projects->ListProjects();
      assert(!settings_failure);
      assert(settings_failure.error().message.contains("injected"));
      scenario->mcp->fail_load = false;
      scenario->mcp->value.mode = domain::McpExecutionMode::ssh;

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("ssh-backend-probe");
}

} // namespace

int main() {
  active = std::make_shared<Scenario>();
  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  assert(active->transport->connections >= 4U);
  active.reset();
}
