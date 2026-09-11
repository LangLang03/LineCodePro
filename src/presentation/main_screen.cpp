#include "presentation/main_screen.h"

#include <algorithm>
#include <cstddef>
#include <expected>
#include <filesystem>
#include <iterator>
#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/behavior_settings_repository.h"
#include "application/chat_mode_service.h"
#include "application/chat_session.h"
#include "application/generation_controller.h"
#include "application/mcp_execution_settings.h"
#include "application/memory_context_service.h"
#include "application/memory_prompt_renderer.h"
#include "application/output_settings.h"
#include "application/pending_message_queue.h"
#include "application/ports/completion_gateway.h"
#include "application/ports/data_archive.h"
#include "application/ports/extension_store.h"
#include "application/ports/mcp_tool_catalog.h"
#include "application/ports/memory_store.h"
#include "application/ports/model_store.h"
#include "application/ports/storage_permission.h"
#include "application/project_workspace_service.h"
#include "application/prompt_request_composer.h"
#include "application/ssh_settings_service.h"
#include "application/tool_permission_service.h"
#include "domain/app_state.h"
#include "domain/memory_rag.h"
#include "presentation/components/chat_screen.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"
#include "presentation/project_workspace_presentation.h"
#include "presentation/screens/about_screen.h"
#include "presentation/screens/browser_screen.h"
#include "presentation/screens/data_settings_screen.h"
#include "presentation/screens/error_logs_screen.h"
#include "presentation/screens/extensions_screen.h"
#include "presentation/screens/image_model_picker_screen.h"
#include "presentation/screens/input_settings_screen.h"
#include "presentation/screens/licenses_screen.h"
#include "presentation/screens/llm_settings_screen.h"
#include "presentation/screens/mcp_settings_screen.h"
#include "presentation/screens/memory_screen.h"
#include "presentation/screens/model_management_screen.h"
#include "presentation/screens/output_settings_screen.h"
#include "presentation/screens/prompt_templates_screen.h"
#include "presentation/screens/security_settings_screen.h"
#include "presentation/screens/settings_screen.h"
#include "presentation/screens/skill_hub_screens.h"
#include "presentation/screens/ssh_settings_screen.h"
#include "presentation/screens/storage_screen.h"
#include "presentation/screens/terminal_provider_screen.h"
#include "presentation/screens/termux_integration_screen.h"
#include "presentation/screens/theme_settings_screen.h"
#include "presentation/screens/tool_call_preview_screen.h"
#include "presentation/screens/tool_settings_screen.h"
#include "presentation/screens/tutorial_screen.h"
#if defined(__ANDROID__)
#include "presentation/screens/keep_alive_screen.h"
#endif

namespace linecode::presentation {
namespace {

std::vector<DrawerConversation>
ToDrawerConversations(const application::ChatSession &session) {
  std::vector<DrawerConversation> conversations;
  conversations.reserve(session.Conversations().size());
  std::ranges::transform(session.Conversations(),
                         std::back_inserter(conversations),
                         [](const application::ConversationSummary &summary) {
                           return DrawerConversation{
                               .id = summary.id,
                               .title = summary.title,
                               .updated_at_millis = summary.updated_at_millis,
                           };
                         });
  return conversations;
}

#if defined(__ANDROID__)
[[huxerui::composable]] huxerui::View PlatformKeepAliveDestination() {
  return KeepAliveSettingsScreen();
}
#else
[[huxerui::composable]] huxerui::View PlatformKeepAliveDestination() {
  return SettingsScreen();
}
#endif

huxerui::Task<void>
LoadSelectedModel(std::shared_ptr<application::ModelStore> store,
                  huxerui::State<std::optional<bool>> available) {
  auto selected = co_await store->SelectedId();
  if (selected.has_value()) {
    available = !selected->empty();
  }
}

constexpr application::PromptChatMode
PromptMode(domain::ChatMode mode) noexcept {
  switch (mode) {
  case domain::ChatMode::chat:
    return application::PromptChatMode::chat;
  case domain::ChatMode::plan:
    return application::PromptChatMode::plan;
  case domain::ChatMode::agent:
    return application::PromptChatMode::agent;
  }
  return application::PromptChatMode::agent;
}

SshSettingsPresentation LocalizedSshPresentation() {
  return SshSettingsPresentation{
      .title = app::strings::screen_ssh_title,
      .server_section = app::strings::screen_ssh_section_server,
      .server_description = app::strings::screen_ssh_server_desc,
      .termux = app::strings::screen_ssh_termux,
      .form_title = app::strings::screen_ssh_form_title,
      .host = app::strings::screen_ssh_field_host,
      .host_hint = app::strings::screen_ssh_hint_host,
      .port = app::strings::screen_ssh_field_port,
      .port_hint = app::strings::screen_ssh_hint_port,
      .username = app::strings::screen_ssh_field_username,
      .username_hint = app::strings::screen_ssh_hint_username,
      .password = app::strings::screen_ssh_field_password_optional,
      .password_hint = app::strings::screen_ssh_hint_password,
      .private_key = app::strings::screen_ssh_field_private_key,
      .private_key_hint = app::strings::screen_ssh_hint_private_key,
      .passphrase = app::strings::screen_ssh_field_key_passphrase_optional,
      .passphrase_hint = app::strings::screen_ssh_hint_passphrase,
      .save = app::strings::screen_ssh_save,
      .test = app::strings::screen_ssh_test,
      .saved_title = app::strings::screen_ssh_status_saved_title,
      .saved_message = app::strings::screen_ssh_status_saved_message,
      .testing_title = app::strings::screen_ssh_status_testing_title,
      .testing_message = app::strings::screen_ssh_status_testing_message,
      .success_title = app::strings::screen_ssh_status_success_title,
      .success_message = app::strings::screen_ssh_status_success_message,
      .failed_title = app::strings::screen_ssh_status_failed_title,
  };
}

MemoryScreenPresentation LocalizedMemoryPresentation() {
  return MemoryScreenPresentation{
      .title = app::strings::screen_memory_title,
      .long_term = app::strings::screen_memory_section_long_term,
      .project = app::strings::screen_memory_section_project,
      .environment = app::strings::screen_memory_section_environment,
      .short_term = app::strings::screen_memory_section_short_term,
      .chat_index = app::strings::screen_memory_section_chat_index,
      .delete_title = app::strings::screen_memory_delete_title,
      .delete_prompt = app::strings::screen_memory_delete_prompt,
      .batch_delete_prefix = app::strings::screen_memory_batch_delete_prefix,
      .batch_delete_suffix = app::strings::screen_memory_batch_delete_suffix,
      .editor_add = app::strings::screen_memory_editor_add,
      .editor_edit = app::strings::screen_memory_editor_edit,
      .scope_user = app::strings::screen_memory_scope_user,
      .scope_project = app::strings::screen_memory_scope_project,
      .scope_environment = app::strings::screen_memory_scope_environment,
      .input_hint = app::strings::screen_memory_hint,
      .empty_toast = app::strings::screen_memory_empty_toast,
      .action_title = app::strings::screen_memory_action_title,
      .action_edit = app::strings::screen_memory_action_edit,
      .action_delete = app::strings::screen_memory_action_delete,
      .action_multi_select = app::strings::screen_memory_action_multi_select,
      .selected_prefix = app::strings::screen_memory_selected_prefix,
      .selected_suffix = app::strings::screen_memory_selected_suffix,
      .current_project = app::strings::screen_memory_current_project,
      .project_unselected = app::strings::screen_memory_project_unselected,
      .empty = app::strings::screen_memory_empty,
      .source = app::strings::screen_memory_source,
      .used_prefix = app::strings::screen_memory_used_prefix,
      .used_suffix = app::strings::screen_memory_used_suffix,
      .scope = app::strings::screen_memory_scope,
      .project_field = app::strings::screen_memory_project_field,
      .confidence = app::strings::screen_memory_confidence,
      .use_count = app::strings::screen_memory_use_count,
      .created = app::strings::screen_memory_created,
      .updated = app::strings::screen_memory_updated,
      .last_used = app::strings::screen_memory_last_used,
      .expires = app::strings::screen_memory_expires,
      .title_field = app::strings::screen_memory_title_field,
      .conversation = app::strings::screen_memory_conversation,
      .message = app::strings::screen_memory_message,
      .global = app::strings::screen_memory_global,
      .not_used = app::strings::screen_memory_not_used,
      .no_expiry = app::strings::screen_memory_no_expiry,
      .empty_value = app::strings::screen_memory_empty_value,
      .unknown_time = app::strings::screen_memory_unknown_time,
      .loading = app::strings::screen_memory_loading,
      .retry = app::strings::screen_memory_retry,
      .save = app::strings::common_save,
      .cancel = app::strings::common_cancel,
      .close = app::strings::common_close,
  };
}

[[huxerui::composable]]
huxerui::View HomeScreen(
    std::shared_ptr<application::ChatSession> initial_session,
    std::shared_ptr<application::ProjectWorkspaceController> project_workspace,
    std::shared_ptr<application::ModelStore> model_store,
    std::shared_ptr<application::McpCompletionLoop> completion_loop,
    std::shared_ptr<application::StoragePermissionService> storage_permission,
    std::shared_ptr<application::MemoryContextService> memory_context,
    std::shared_ptr<application::AiBehaviorSettingsRepository>
        behavior_settings,
    std::shared_ptr<application::ToolPermissionService> tool_permissions,
    std::shared_ptr<application::ChatModeService> chat_modes,
    huxerui::State<application::ChatInteractionModeState> interaction_mode,
    domain::InputSettings input_settings, std::string linecode_root,
    huxerui::State<ProjectWorkspacePresentationState> workspace_state,
    huxerui::State<std::optional<bool>> selected_model_available,
    std::shared_ptr<application::GenerationController> generation,
    huxerui::State<huxerui::TaskHandle> active_generation,
    std::shared_ptr<application::PendingMessageQueue> pending_messages,
    huxerui::State<std::size_t> revision,
    huxerui::State<std::size_t> workspace_revision) {
  using namespace huxerui;

  auto drawer_open = UseState(false);
  auto selected_drawer_tab = UseState(DrawerTab::conversations);
  auto draft = UseState(TextEditingValue::FromText(""));
  auto session = UseState(std::move(initial_session));
  auto drawer_model = UseState(DrawerModel{});
  auto workspace_clipboard = UseState(WorkspaceClipboard{});
  const auto tasks = UseTaskScope();
  const auto sheets = UseBottomSheet();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  const auto picker = UseService<FilePicker>();
  const ProjectWorkspaceCoordinator workspace_coordinator{project_workspace,
                                                          workspace_state,
                                                          drawer_model,
                                                          workspace_clipboard,
                                                          tasks,
                                                          sheets,
                                                          dialogs,
                                                          toast,
                                                          picker};

  Lifecycle(
      [tasks, workspace_coordinator, model_store, selected_model_available] {
        workspace_coordinator.Refresh();
        tasks.Launch([model_store, selected_model_available]() -> Task<void> {
          co_await LoadSelectedModel(model_store, selected_model_available);
        });
      },
      workspace_revision);

  const DrawerActions drawer_actions{
      .on_new_conversation =
          [session, generation, active_generation, pending_messages, revision] {
            active_generation.Get().Cancel();
            generation->Reset();
            pending_messages->Clear();
            session.Get()->StartNewConversation();
            revision += 1;
          },
      .on_conversation_selected =
          [session, generation, active_generation, pending_messages,
           revision](std::string_view id) {
            active_generation.Get().Cancel();
            generation->Reset();
            pending_messages->Clear();
            session.Get()->SelectConversation(id);
            revision += 1;
          },
      .on_conversation_deleted =
          [session, generation, active_generation, pending_messages,
           revision](std::string_view id) {
            active_generation.Get().Cancel();
            generation->Reset();
            pending_messages->Clear();
            session.Get()->DeleteConversation(id);
            revision += 1;
          },
      .on_project_remove_requested =
          [workspace_state, workspace_coordinator] {
            if (workspace_state->selected)
              workspace_coordinator.ConfirmDeleteProject(
                  *workspace_state->selected);
          },
      .on_file_node_selected =
          [workspace_coordinator](DrawerFileTarget target) {
            workspace_coordinator.ToggleNode(target);
          },
      .on_file_node_long_pressed =
          [workspace_coordinator](DrawerFileTarget target) {
            workspace_coordinator.ShowFileActions(std::move(target));
          },
      .on_file_tree_activated =
          [drawer_model, workspace_coordinator] {
            if (!drawer_model->file_tree)
              workspace_coordinator.Refresh();
          },
      .on_file_tree_refresh =
          [workspace_coordinator] { workspace_coordinator.Refresh(); },
  };

  DrawerModel visible_drawer = drawer_model.Get();
  visible_drawer.conversations = ToDrawerConversations(*session.Get());
  visible_drawer.selected_conversation_id =
      std::string{session.Get()->CurrentConversationId()};

  const auto selected_project = workspace_state->selected;
  const std::string project_id = selected_project
                                     ? selected_project->id
                                     : std::string{domain::default_project_id};
  const std::string project_path =
      selected_project
          ? selected_project->path
          : (std::filesystem::path{linecode_root} / "home").string();
  application::PromptAssemblyContext prompt_context{
      .chat_mode = PromptMode(interaction_mode->chat_mode),
      .work_directory = project_path,
      .linecode_root = linecode_root,
      .global_skills_root =
          (std::filesystem::path{linecode_root} / "skills").string(),
      .workspace_private_root =
          (std::filesystem::path{project_path} / ".linecode").string(),
      .workspace_skills_root =
          (std::filesystem::path{project_path} / ".linecode" / "skills")
              .string(),
      .learning_context = {},
      .todo_state = {},
      .permission_mode = "auto",
      .tools_context = {},
      .attachment_history = {},
  };

  View centered_chat =
      Stack{
          ChatScreen([drawer_open] { drawer_open = true; }, draft,
                     session.Get(), generation, model_store, completion_loop,
                     storage_permission, selected_model_available.Get(),
                     active_generation, revision, pending_messages,
                     drawer_model, memory_context, behavior_settings,
                     tool_permissions, chat_modes, interaction_mode,
                     input_settings, project_id, std::move(prompt_context),
                     visible_drawer.project_label,
                     [workspace_coordinator] {
                       workspace_coordinator.ShowProjectPicker();
                     })
              .With(Frame{.max_width = 792.0F}),
      }
          .With(Align(HorizontalAlignment::Center, VerticalAlignment::Stretch),
                Background(colors::background), SafeAreaPadding{});

  return DrawerLayout(
      centered_chat,
      StartDrawer(Drawer(drawer_open, selected_drawer_tab, visible_drawer,
                         drawer_actions))
          .Open(drawer_open.Get())
          .OnOpenChanged([drawer_open](bool open) { drawer_open = open; }));
}

} // namespace

[[huxerui::composable]]
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
    std::shared_ptr<application::AgentExtensionStore> agent_extensions,
    std::shared_ptr<application::McpExtensionStore> mcp_extensions,
    std::shared_ptr<application::McpToolCatalog> mcp_tool_catalog,
    std::string linecode_root, SkillHubScreenServices skill_hub_services,
    domain::McpExecutionCapabilities mcp_capabilities,
    PlatformCapabilities platform_capabilities,
    std::shared_ptr<application::TermuxIntegrationGateway> termux_integration,
    std::shared_ptr<application::TerminalProviderStore> terminal_providers,
    std::shared_ptr<application::TerminalProviderDiscovery>
        terminal_provider_discovery,
    std::shared_ptr<application::StoragePermissionService> storage_permission,
    std::shared_ptr<application::StorageStatsRepository> storage_stats,
    std::shared_ptr<application::ErrorLogService> error_logs,
    std::shared_ptr<application::DataArchiveService> data_archive,
    DataSettingsCallbacks data_callbacks) {
  using namespace huxerui;

  auto navigation_path = UseState(NavigationPath<domain::AppRoute>{});
  auto selected_model_available = UseState(std::optional<bool>{});
  auto generation = UseState(
      std::make_shared<application::GenerationController>(*initial_session));
  auto active_generation = UseState(TaskHandle{});
  auto pending_messages =
      UseState(std::make_shared<application::PendingMessageQueue>());
  auto chat_revision = UseState(std::size_t{0});
  auto workspace_revision = UseState(std::size_t{0});
  auto tool_settings_revision = UseState(std::size_t{0});
  auto extension_revision = UseState(std::size_t{0});
  auto active_input_settings = UseState(domain::InputSettings{});
  auto interaction_mode = UseState(application::ChatInteractionModeState{});
  auto workspace_state = UseState(ProjectWorkspacePresentationState{});
  auto memory_context =
      UseState(std::make_shared<application::MemoryContextService>(
          memory_store,
          std::make_shared<domain::ExplicitMemoryExtractionPolicy>(),
          std::make_shared<application::LegacyMemoryPromptRenderer>()));
  const auto settings_tasks = UseTaskScope();

  Lifecycle([settings_tasks, input_settings, active_input_settings, chat_modes,
             interaction_mode] {
    settings_tasks.Launch(
        [input_settings, active_input_settings]() -> Task<void> {
          const auto loaded = co_await input_settings->Load();
          if (loaded)
            active_input_settings = *loaded;
        });
    settings_tasks.Launch([chat_modes, interaction_mode]() -> Task<void> {
      auto loaded = co_await chat_modes->Load();
      if (loaded)
        interaction_mode = *loaded;
    });
  });

  auto root =
      [initial_session, project_workspace = std::move(project_workspace),
       model_store, completion_loop = std::move(completion_loop),
       storage_permission = std::move(storage_permission),
       memory_context = memory_context.Get(), ai_behavior_settings,
       tool_permissions, chat_modes, interaction_mode, active_input_settings,
       linecode_root, workspace_state, selected_model_available,
       generation = generation.Get(), active_generation, chat_revision,
       pending_messages = pending_messages.Get(),
       workspace_revision]() -> View {
    return HomeScreen(initial_session, project_workspace, model_store,
                      completion_loop, storage_permission, memory_context,
                      ai_behavior_settings, tool_permissions, chat_modes,
                      interaction_mode, active_input_settings.Get(),
                      linecode_root, workspace_state, selected_model_available,
                      generation, active_generation, pending_messages,
                      chat_revision, workspace_revision);
  };

  auto destination =
      [model_store = std::move(model_store),
       model_catalog = std::move(model_catalog),
       ai_behavior_settings = std::move(ai_behavior_settings),
       input_settings = std::move(input_settings), active_input_settings,
       prompt_templates = std::move(prompt_templates),
       output_settings_service = std::move(output_settings_service),
       theme_service = std::move(theme_service), theme_settings,
       mcp_settings = std::move(mcp_settings),
       tool_settings = std::move(tool_settings),
       ssh_settings = std::move(ssh_settings),
       memory_store = std::move(memory_store),
       agent_extensions = std::move(agent_extensions),
       mcp_extensions = std::move(mcp_extensions),
       mcp_tool_catalog = std::move(mcp_tool_catalog), workspace_state,
       skill_hub_services = std::move(skill_hub_services), mcp_capabilities,
       platform_capabilities,
       termux_integration = std::move(termux_integration),
       terminal_providers = std::move(terminal_providers),
       terminal_provider_discovery = std::move(terminal_provider_discovery),
       storage_stats = std::move(storage_stats),
       error_logs = std::move(error_logs),
       data_archive = std::move(data_archive),
       data_callbacks = std::move(data_callbacks), selected_model_available,
       generation = generation.Get(), active_generation, chat_revision,
       workspace_revision, tool_settings_revision,
       extension_revision](domain::AppRoute route) mutable -> View {
    auto current_skill_hub_services = skill_hub_services;
    current_skill_hub_services.roots.project =
        workspace_state->selected &&
                workspace_state->selected->source !=
                    domain::ProjectSource::ssh &&
                !workspace_state->selected->path.empty()
            ? std::optional<huxerui::File>{
                  huxerui::File{workspace_state->selected->path}}
            : std::nullopt;
    if (const auto *detail = route.SkillStoreDetailValue()) {
      return SkillStoreDetailScreen(current_skill_hub_services, *detail);
    }
    if (const auto *site = route.SkillHubSiteValue()) {
      return SkillHubWebScreen(*site);
    }
    if (const auto *extension = route.ExtensionDetailValue()) {
      return ExtensionDetailScreen(
          extension->kind,
          ExtensionScreenServices{
              .agents = agent_extensions,
              .mcps = mcp_extensions,
              .mcp_tools = mcp_tool_catalog,
              .skills = current_skill_hub_services.repository,
              .skill_sources = current_skill_hub_services.management,
              .skill_roots = current_skill_hub_services.roots,
              .revision = extension_revision.Get(),
              .on_changed = [extension_revision] { extension_revision += 1; }});
    }
    if (const auto *editor = route.AgentExtensionEditorValue()) {
      return AgentExtensionEditorScreen(
          editor->id,
          ExtensionScreenServices{
              .agents = agent_extensions,
              .mcps = mcp_extensions,
              .mcp_tools = mcp_tool_catalog,
              .skills = current_skill_hub_services.repository,
              .skill_sources = current_skill_hub_services.management,
              .skill_roots = current_skill_hub_services.roots,
              .revision = extension_revision.Get(),
              .on_changed = [extension_revision] { extension_revision += 1; }});
    }
    if (const auto *editor = route.McpExtensionEditorValue()) {
      return McpExtensionEditorScreen(
          editor->id,
          ExtensionScreenServices{
              .agents = agent_extensions,
              .mcps = mcp_extensions,
              .mcp_tools = mcp_tool_catalog,
              .skills = current_skill_hub_services.repository,
              .skill_sources = current_skill_hub_services.management,
              .skill_roots = current_skill_hub_services.roots,
              .revision = extension_revision.Get(),
              .on_changed = [extension_revision] { extension_revision += 1; }});
    }
    if (const auto *picker = route.ImageModelPickerValue()) {
      return ImageModelPickerScreen(
          picker->purpose, tool_settings, model_store,
          [tool_settings_revision] { tool_settings_revision += 1; });
    }
    if (const auto *browser = route.BrowserValue()) {
      return BrowserScreen(*browser);
    }
    if (route == domain::AppRoute::settings) {
      return SettingsScreen();
    }
    if (route == domain::AppRoute::skill_store) {
      return SkillStoreScreen(current_skill_hub_services);
    }
    if (route == domain::AppRoute::skill_hub_login) {
      return SkillHubLoginScreen(current_skill_hub_services);
    }
    if (route == domain::AppRoute::skill_hub_center) {
      return SkillHubCenterScreen();
    }
    if (route == domain::AppRoute::skill_hub_publish) {
      return SkillHubPublishScreen(current_skill_hub_services);
    }
    if (route == domain::AppRoute::keep_alive) {
      return PlatformKeepAliveDestination();
    }
    if (route == domain::AppRoute::models) {
      return ModelManagementScreen(model_store, model_catalog,
                                   [selected_model_available](bool available) {
                                     selected_model_available = available;
                                   });
    }
    if (route == domain::AppRoute::llm) {
      return LlmSettingsScreen(ai_behavior_settings);
    }
    if (route == domain::AppRoute::mcp) {
      return McpSettingsScreen(
          mcp_settings, mcp_capabilities, platform_capabilities,
          [workspace_revision] { workspace_revision += 1; });
    }
    if (route == domain::AppRoute::ssh_settings) {
      return SshSettingsScreen(ssh_settings,
                               platform_capabilities.termux_integration,
                               LocalizedSshPresentation());
    }
    if (route == domain::AppRoute::termux_integration &&
        platform_capabilities.termux_integration) {
      return TermuxIntegrationScreen(termux_integration, ssh_settings);
    }
    if (route == domain::AppRoute::tool_settings) {
      return ToolSettingsScreen(tool_settings, model_store,
                                tool_settings_revision.Get());
    }
    if (route == domain::AppRoute::extensions) {
      return ExtensionsScreen(mcp_capabilities.terminal_provider);
    }
    if (route == domain::AppRoute::terminal_provider) {
      return TerminalProviderScreen(terminal_providers,
                                    terminal_provider_discovery);
    }
    if (route == domain::AppRoute::memory) {
      const std::string project_id =
          workspace_state->selected ? workspace_state->selected->id
                                    : std::string{domain::default_project_id};
      return MemoryScreen(memory_store, project_id,
                          LocalizedMemoryPresentation());
    }
    if (route == domain::AppRoute::prompt_templates) {
      return PromptTemplatesScreen(prompt_templates);
    }
    if (route == domain::AppRoute::input) {
      return InputSettingsScreen(input_settings, active_input_settings);
    }
    if (route == domain::AppRoute::theme) {
      return ThemeSettingsScreen(theme_service, theme_settings);
    }
    if (route == domain::AppRoute::output) {
      return OutputSettingsScreen(output_settings_service);
    }
    if (route == domain::AppRoute::security) {
      return SecuritySettingsScreen(output_settings_service);
    }
    if (route == domain::AppRoute::tool_call_preview) {
      return ToolCallPreviewScreen();
    }
    if (route == domain::AppRoute::storage) {
      return StorageScreen(storage_stats);
    }
    if (route == domain::AppRoute::error_logs) {
      return ErrorLogsScreen(error_logs);
    }
    if (route == domain::AppRoute::tutorial) {
      return TutorialScreen();
    }
    if (route == domain::AppRoute::data) {
      auto callbacks = data_callbacks;
      const auto before_import = callbacks.before_import;
      callbacks.before_import = [before_import, generation, active_generation,
                                 chat_revision] {
        active_generation.Get().Cancel();
        generation->Cancel();
        chat_revision += 1;
        if (before_import) {
          before_import();
        }
      };
      const auto after_import = callbacks.after_import;
      callbacks.after_import =
          [after_import, model_store, selected_model_available, generation,
           chat_revision,
           workspace_revision]() -> Task<DataSettingsCallbackResult> {
        if (after_import) {
          auto reloaded = co_await after_import();
          if (!reloaded) {
            co_return std::unexpected(std::move(reloaded.error()));
          }
        }
        auto selected = co_await model_store->SelectedId();
        if (!selected) {
          co_return std::unexpected(selected.error().message);
        }
        selected_model_available = !selected->empty();
        generation->Reset();
        chat_revision += 1;
        workspace_revision += 1;
        co_return DataSettingsCallbackResult{};
      };
      return DataSettingsScreen(data_archive, std::move(callbacks));
    }
    if (route == domain::AppRoute::about) {
      return AboutScreen(domain::AppRoute::licenses);
    }
    if (route == domain::AppRoute::licenses) {
      return LicensesScreen();
    }
    return PendingScreen(route);
  };

  return Stack{
      NavigationStack(std::move(root), navigation_path, std::move(destination)),
  }
      .With(Align(HorizontalAlignment::Stretch, VerticalAlignment::Stretch));
}

} // namespace linecode::presentation
