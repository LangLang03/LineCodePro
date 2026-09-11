#pragma once

#include <cstddef>
#include <functional>
#include <memory>
#include <optional>
#include <string>

#include <huxerui/view.h>

#include "application/ports/skill_services.h"
#include "domain/extension_kind.h"

namespace linecode::application {
class AgentExtensionStore;
class McpExtensionStore;
class McpToolCatalog;
class SkillExtensionStore;
class SkillSourceInstaller;
} // namespace linecode::application

namespace linecode::presentation {

struct ExtensionScreenServices final {
  std::shared_ptr<application::AgentExtensionStore> agents;
  std::shared_ptr<application::McpExtensionStore> mcps;
  std::shared_ptr<application::McpToolCatalog> mcp_tools;
  std::shared_ptr<application::SkillExtensionStore> skills;
  std::shared_ptr<application::SkillSourceInstaller> skill_sources;
  std::optional<application::SkillRoots> skill_roots;
  std::size_t revision{};
  std::function<void()> on_changed;
  std::function<void()> on_share_workspace;
};

[[huxerui::composable]] huxerui::View
ExtensionsScreen(bool terminal_provider_available);

[[huxerui::composable]] huxerui::View
ExtensionDetailScreen(domain::ExtensionKind kind,
                      ExtensionScreenServices services);

[[huxerui::composable]] huxerui::View
AgentExtensionEditorScreen(std::optional<std::string> id,
                           ExtensionScreenServices services);

[[huxerui::composable]] huxerui::View
McpExtensionEditorScreen(std::optional<std::string> id,
                         ExtensionScreenServices services);

} // namespace linecode::presentation
