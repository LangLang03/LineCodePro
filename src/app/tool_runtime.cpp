#include "app/tool_runtime.h"

#include <utility>
#include <vector>

#include "application/agent_extension_draft.h"
#include "application/agent_extension_tool_registry.h"
#include "application/agent_result_registry.h"
#include "application/agent_result_registry_sink.h"
#include "application/agent_tool_registry.h"
#include "application/composite_tool_registry.h"
#include "application/context_compaction.h"
#include "application/file_tool_registry.h"
#include "application/image_generation_tool_registry.h"
#include "application/image_understanding_tool_registry.h"
#include "application/in_memory_todo_state_store.h"
#include "application/mcp_completion_loop.h"
#include "application/mcp_extension_tool_registry.h"
#include "application/memory_tool_registry.h"
#include "application/ssh_tool_registry.h"
#include "application/sub_agent_runner.h"
#include "application/terminal_provider_tool_registry.h"
#include "application/todo_tool_registry.h"
#include "application/tool_loop_compactor.h"
#include "application/web_tool_registry.h"
#include "domain/prompt_template.h"
#include "infrastructure/hux_image_generation_gateway.h"
#include "infrastructure/hux_image_understanding_gateway.h"
#include "infrastructure/hux_tool_file_access.h"
#include "infrastructure/hux_web_tools_gateway.h"
#include "infrastructure/image_generation_codec.h"
#include "infrastructure/image_understanding_codec.h"
#include "infrastructure/json_agent_extension_draft_codec.h"
#include "infrastructure/json_mcp_tool_schema_policy.h"

namespace linecode::app {

std::shared_ptr<ToolRuntime>
BuildToolRuntime(ToolRuntimeDependencies dependencies) {
  auto mcp_tools = std::make_shared<application::McpExtensionToolRegistry>(
      dependencies.mcp_extensions, dependencies.mcp_invoker,
      std::make_shared<infrastructure::JsonMcpToolSchemaPolicy>());
  auto image_generation =
      std::make_shared<application::ImageGenerationToolRegistry>(
          dependencies.execution_settings, dependencies.tool_settings,
          dependencies.models,
          std::make_shared<infrastructure::JsonImageGenerationToolCodec>(),
          std::make_shared<infrastructure::HuxImageGenerationGateway>(
              dependencies.http));

  std::vector<std::shared_ptr<application::ToolRegistry>> sources{
      std::move(mcp_tools), std::move(image_generation)};
  sources.push_back(std::make_shared<application::SshToolRegistry>(
      dependencies.execution_settings, dependencies.ssh_settings,
      dependencies.ssh_runtime, dependencies.language));
  if (dependencies.terminal_gateway) {
    sources.push_back(
        std::make_shared<application::TerminalProviderToolRegistry>(
            dependencies.execution_settings, dependencies.terminal_providers,
            dependencies.terminal_gateway));
  }

  sources.push_back(
      std::make_shared<application::ImageUnderstandingToolRegistry>(
          dependencies.execution_settings, dependencies.tool_settings,
          dependencies.models, dependencies.prompt_templates,
          dependencies.workspace_images,
          std::make_shared<infrastructure::JsonImageUnderstandingToolCodec>(),
          std::make_shared<infrastructure::HuxImageUnderstandingGateway>(
              dependencies.http)));

  auto todos = std::make_shared<application::InMemoryTodoStateStore>();
  sources.push_back(std::make_shared<application::TodoToolRegistry>(
      dependencies.execution_settings, todos));
  sources.push_back(std::make_shared<application::MemoryToolRegistry>(
      dependencies.execution_settings, dependencies.memories,
      dependencies.project_workspace));

  auto web =
      std::make_shared<infrastructure::HuxWebToolsGateway>(dependencies.http);
  sources.push_back(std::make_shared<application::WebToolRegistry>(
      dependencies.execution_settings, dependencies.tool_settings,
      std::move(web)));
  sources.push_back(std::make_shared<application::FileToolRegistry>(
      dependencies.execution_settings, dependencies.project_workspace,
      std::make_shared<infrastructure::HuxToolFileAccess>(),
      dependencies.language, dependencies.diffs));

  auto base_tools =
      std::make_shared<application::CompositeToolRegistry>(std::move(sources));
  auto agent_results = std::make_shared<application::AgentResultRegistry>();
  auto sub_agent_environment =
      std::make_shared<application::RuntimeSubAgentEnvironmentProvider>(
          dependencies.execution_settings, dependencies.permissions,
          std::vector<application::SubAgentEnvironmentRoute>{
              {.mode = domain::McpExecutionMode::local,
               .workspace = dependencies.local_workspace,
               .remote_mode = false},
              {.mode = domain::McpExecutionMode::ssh,
               .workspace = dependencies.ssh_workspace,
               .remote_mode = true},
              {.mode = domain::McpExecutionMode::terminal_provider,
               .workspace = dependencies.local_workspace,
               .remote_mode = true},
          });
  auto sub_agents = std::make_shared<application::SubAgentRunner>(
      dependencies.completion, base_tools, dependencies.prompt_templates,
      dependencies.models,
      std::make_shared<application::AgentResultRegistrySink>(agent_results),
      std::make_shared<application::SkillRepositoryExtensionPromptSource>(
          dependencies.skills),
      std::make_shared<application::TaskScopeSubAgentLauncher>(
          dependencies.tasks),
      std::move(sub_agent_environment), nullptr, dependencies.permissions,
      dependencies.reviews);

  auto agent_tools = std::make_shared<application::AgentToolRegistry>(
      dependencies.execution_settings, agent_results, sub_agents,
      dependencies.language);
  auto custom_agents =
      std::make_shared<application::AgentExtensionToolRegistry>(
          dependencies.agent_extensions, sub_agents, dependencies.language);
  auto runtime_tools = std::make_shared<application::CompositeToolRegistry>(
      std::vector<std::shared_ptr<application::ToolRegistry>>{
          std::move(base_tools), std::move(agent_tools),
          std::move(custom_agents)});

  auto agent_drafts =
      std::make_shared<application::CompletionAgentExtensionDraftGenerator>(
          dependencies.models, dependencies.completion, runtime_tools,
          dependencies.mcp_extensions,
          std::make_shared<infrastructure::JsonAgentExtensionDraftCodec>(),
          dependencies.language);
  auto compaction = std::make_shared<application::ContextCompactionService>(
      dependencies.completion, dependencies.prompt_templates,
      dependencies.models);
  auto completion_loop = std::make_shared<application::McpCompletionLoop>(
      dependencies.completion, std::move(runtime_tools),
      dependencies.permissions, dependencies.request_composer, nullptr,
      std::make_shared<application::ToolLoopCompactor>(
          compaction, dependencies.models,
          domain::AiBehaviorSettings{}.preserve_reasoning,
          dependencies.conversation));

  return std::make_shared<ToolRuntime>(ToolRuntime{
      .compaction = std::move(compaction),
      .todos = std::move(todos),
      .agent_results = std::move(agent_results),
      .agent_drafts = std::move(agent_drafts),
      .completion_loop = std::move(completion_loop),
  });
}

} // namespace linecode::app
