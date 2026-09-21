#pragma once

#include <expected>
#include <memory>
#include <string>

#include <huxerui/task.h>

#include "application/ports/completion_gateway.h"
#include "application/ports/settings_store.h"
#include "application/ports/tool_registry.h"
#include "domain/tool_permission.h"

namespace linecode::application {

enum class ToolPermissionDecision : std::uint8_t {
  execute,
  review,
  deny,
};

struct ToolPermissionState final {
  domain::ToolPermissionMode mode{domain::ToolPermissionMode::automatic};
  bool has_permanent_grants{};

  bool operator==(const ToolPermissionState &) const = default;
};

// Owns the legacy permission keys and exact shell-command grants. The
// completion loop asks this service before every invocation; presentation only
// edits/observes the same state through this boundary.
class ToolPermissionService final {
public:
  explicit ToolPermissionService(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<SettingsResult<ToolPermissionState>> Load();
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  SetMode(domain::ToolPermissionMode mode);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> ClearPermanentGrants();

  [[nodiscard]] huxerui::Task<SettingsResult<ToolPermissionDecision>>
  Evaluate(const RegisteredTool &tool, const CompletionToolCall &call,
           std::string permission_scope);
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  RememberPermanentGrant(const RegisteredTool &tool,
                         const CompletionToolCall &call,
                         std::string permission_scope);

private:
  std::shared_ptr<AsyncSettingsStore> store_;
};

} // namespace linecode::application
