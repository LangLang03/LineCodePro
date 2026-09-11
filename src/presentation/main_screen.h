#pragma once

#include <memory>
#include <string>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "application/ports/todo_state_store.h"
#include "application/theme_settings.h"
#include "domain/mcp_execution_settings.h"
#include "presentation/platform_features.h"
#include "presentation/screens/data_settings_screen.h"

namespace linecode::application {
class ChatSession;
class ChatModeService;
class AiBehaviorSettingsRepository;
class AgentExtensionDraftGenerator;
class AgentExtensionStore;
class McpCompletionLoop;
class DataArchiveService;
class ErrorLogService;
class InputSettingsRepository;
class McpExecutionSettingsService;
class McpExtensionStore;
class McpToolCatalog;
class MemoryStore;
class ModelCatalogGateway;
class ModelStore;
class OutputSettingsService;
class ProjectWorkspaceController;
class PromptTemplateRepository;
class SshSettingsService;
class StorageStatsRepository;
class StoragePermissionService;
class WorkspaceDirectoryShareService;
class ToolSettingsService;
class ToolPermissionService;
class TermuxIntegrationGateway;
class TerminalProviderDiscovery;
class TerminalProviderStore;
} // namespace linecode::application

namespace linecode::presentation {

struct SkillHubScreenServices;

huxerui::View MainScreen(
    std::shared_ptr<application::ChatSession> initial_session,
    std::shared_ptr<application::ProjectWorkspaceController> project_workspace,
    std::shared_ptr<application::ModelStore> model_store,
    std::shared_ptr<application::ModelCatalogGateway> model_catalog,
    std::shared_ptr<application::AiBehaviorSettingsRepository>
        ai_behavior_settings,
    std::shared_ptr<application::InputSettingsRepository> input_settings,
    std::shared_ptr<application::PromptTemplateRepository> prompt_templates,
    std::shared_ptr<application::McpCompletionLoop> completion_loop,
    std::shared_ptr<application::OutputSettingsService> output_settings_service,
    std::shared_ptr<application::ThemeSettingsService> theme_service,
    huxerui::State<application::ThemeSettingsState> theme_settings,
    std::shared_ptr<application::McpExecutionSettingsService> mcp_settings,
    std::shared_ptr<application::ToolSettingsService> tool_settings,
    std::shared_ptr<application::ToolPermissionService> tool_permissions,
    std::shared_ptr<application::ChatModeService> chat_modes,
    std::shared_ptr<application::SshSettingsService> ssh_settings,
    std::shared_ptr<application::MemoryStore> memory_store,
    std::shared_ptr<application::TodoStateStore> todo_state,
    std::shared_ptr<application::AgentExtensionStore> agent_extensions,
    std::shared_ptr<application::McpExtensionStore> mcp_extensions,
    std::shared_ptr<application::McpToolCatalog> mcp_tool_catalog,
    std::shared_ptr<application::AgentExtensionDraftGenerator> agent_drafts,
    std::string linecode_root, SkillHubScreenServices skill_hub_services,
    domain::McpExecutionCapabilities mcp_capabilities,
    PlatformCapabilities platform_capabilities,
    std::shared_ptr<application::TermuxIntegrationGateway> termux_integration,
    std::shared_ptr<application::TerminalProviderStore> terminal_providers,
    std::shared_ptr<application::TerminalProviderDiscovery>
        terminal_provider_discovery,
    std::shared_ptr<application::StoragePermissionService> storage_permission,
    std::shared_ptr<application::WorkspaceDirectoryShareService>
        workspace_share,
    std::shared_ptr<application::StorageStatsRepository> storage_stats = {},
    std::shared_ptr<application::ErrorLogService> error_logs = {},
    std::shared_ptr<application::DataArchiveService> data_archive = {},
    DataSettingsCallbacks data_callbacks = {});

} // namespace linecode::presentation
