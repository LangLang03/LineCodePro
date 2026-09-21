#pragma once

#include <expected>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/task.h>

#include "domain/mcp_execution_settings.h"

namespace linecode::application {

class McpExecutionSettingsService;
class ProjectWorkspaceController;
class ToolPermissionService;

// Immutable facts captured at the start of one Agent/Agent Pipeline run. A
// snapshot prevents a mid-run settings change from mixing local paths with a
// remote prompt while ensuring the next run observes the latest selection.
struct SubAgentEnvironment final {
  std::string workspace_path;
  bool remote_mode{};
  std::string permission_mode{"auto"};
  // Part of the permanent-grant key, exactly as the main loop uses it.
  std::string permission_scope{};

  bool operator==(const SubAgentEnvironment &) const = default;
};

struct SubAgentEnvironmentError final {
  std::string message;

  bool operator==(const SubAgentEnvironmentError &) const = default;
};

using SubAgentEnvironmentResult =
    std::expected<SubAgentEnvironment, SubAgentEnvironmentError>;

class SubAgentEnvironmentProvider {
public:
  virtual ~SubAgentEnvironmentProvider() = default;

  [[nodiscard]] virtual huxerui::Task<SubAgentEnvironmentResult> Snapshot() = 0;
};

class StaticSubAgentEnvironmentProvider final
    : public SubAgentEnvironmentProvider {
public:
  explicit StaticSubAgentEnvironmentProvider(SubAgentEnvironment value = {})
      : value_(std::move(value)) {}

  [[nodiscard]] huxerui::Task<SubAgentEnvironmentResult> Snapshot() override {
    co_return value_;
  }

  void Update(SubAgentEnvironment value) { value_ = std::move(value); }

private:
  SubAgentEnvironment value_;
};

struct SubAgentEnvironmentRoute final {
  domain::McpExecutionMode mode{domain::McpExecutionMode::local};
  std::shared_ptr<ProjectWorkspaceController> workspace;
  bool remote_mode{};
};

// Runtime adapter for the application root. Mode routing is registered, not
// hard-coded: adding another backend supplies another route. It loads mode,
// selected workspace and permission state for every run.
class RuntimeSubAgentEnvironmentProvider final
    : public SubAgentEnvironmentProvider {
public:
  RuntimeSubAgentEnvironmentProvider(
      std::shared_ptr<McpExecutionSettingsService> execution_settings,
      std::shared_ptr<ToolPermissionService> permissions,
      std::vector<SubAgentEnvironmentRoute> routes);

  [[nodiscard]] huxerui::Task<SubAgentEnvironmentResult> Snapshot() override;

private:
  std::shared_ptr<McpExecutionSettingsService> execution_settings_;
  std::shared_ptr<ToolPermissionService> permissions_;
  std::vector<SubAgentEnvironmentRoute> routes_;
};

} // namespace linecode::application
