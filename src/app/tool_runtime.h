#pragma once

#include <memory>

#include <huxerui/http.h>
#include <huxerui/task.h>

#include "application/tool_text_catalog.h"

namespace linecode::application {
class AgentExtensionDraftGenerator;
class AgentExtensionStore;
class AgentResultRegistry;
class ChatSession;
class CompletionGateway;
class CompletionRequestComposer;
class ContextCompactionService;
class DiffStore;
class McpCompletionLoop;
class McpExecutionSettingsService;
class McpExtensionStore;
class McpToolInvoker;
class MemoryStore;
class ModelStore;
class ProjectWorkspaceController;
class PromptTemplateRepository;
class SkillRepository;
class SshRuntimeService;
class SshSettingsService;
class TerminalProviderGateway;
class TerminalProviderStore;
class TodoStateStore;
class ToolPermissionService;
class ToolReviewBroker;
class ToolSettingsService;
class WorkspaceImageReader;
} // namespace linecode::application

namespace linecode::app {

struct ToolRuntimeDependencies final {
  std::shared_ptr<huxerui::HttpClient> http;
  huxerui::TaskScope tasks;
  std::shared_ptr<application::McpExecutionSettingsService> execution_settings;
  std::shared_ptr<application::ToolSettingsService> tool_settings;
  std::shared_ptr<application::ModelStore> models;
  std::shared_ptr<application::PromptTemplateRepository> prompt_templates;
  std::shared_ptr<application::MemoryStore> memories;
  std::shared_ptr<application::ProjectWorkspaceController> project_workspace;
  std::shared_ptr<application::ProjectWorkspaceController> local_workspace;
  std::shared_ptr<application::ProjectWorkspaceController> ssh_workspace;
  std::shared_ptr<application::WorkspaceImageReader> workspace_images;
  std::shared_ptr<application::DiffStore> diffs;
  std::shared_ptr<application::SshSettingsService> ssh_settings;
  std::shared_ptr<application::SshRuntimeService> ssh_runtime;
  std::shared_ptr<application::TerminalProviderStore> terminal_providers;
  std::shared_ptr<application::TerminalProviderGateway> terminal_gateway;
  std::shared_ptr<application::McpExtensionStore> mcp_extensions;
  std::shared_ptr<application::McpToolInvoker> mcp_invoker;
  std::shared_ptr<application::AgentExtensionStore> agent_extensions;
  std::shared_ptr<application::CompletionGateway> completion;
  std::shared_ptr<application::CompletionRequestComposer> request_composer;
  std::shared_ptr<application::ToolPermissionService> permissions;
  std::shared_ptr<application::ToolReviewBroker> reviews;
  std::shared_ptr<application::SkillRepository> skills;
  std::shared_ptr<application::ChatSession> conversation;
  application::ToolTextLanguage language{
      application::ToolTextLanguage::english};
};

struct ToolRuntime final {
  std::shared_ptr<application::ContextCompactionService> compaction;
  std::shared_ptr<application::TodoStateStore> todos;
  std::shared_ptr<application::AgentResultRegistry> agent_results;
  std::shared_ptr<application::AgentExtensionDraftGenerator> agent_drafts;
  std::shared_ptr<application::McpCompletionLoop> completion_loop;
};

[[nodiscard]] std::shared_ptr<ToolRuntime>
BuildToolRuntime(ToolRuntimeDependencies dependencies);

} // namespace linecode::app
