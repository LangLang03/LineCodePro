#include "app/app_root.h"

#include <cstddef>
#include <memory>
#include <mutex>
#include <utility>

#include <app_resources.h>
#include <huxerui/http.h>
#include <huxerui/huxerui.h>

#include "app/app_root_support.h"
#include "app/bootstrap.h"
#include "app/data_runtime.h"
#include "app/project_runtime.h"
#include "app/tool_runtime.h"
#include "application/agent_result_registry.h"
#include "application/behavior_settings_repository.h"
#include "application/chat_mode_service.h"
#include "application/error_log_service.h"
#include "application/legacy_attachment_prompt_renderer.h"
#include "application/mcp_execution_settings.h"
#include "application/output_settings.h"
#include "application/ports/storage_permission.h"
#include "application/ports/terminal_provider.h"
#include "application/ports/termux_integration.h"
#include "application/ports/workspace_directory_share.h"
#include "application/prompt_request_composer.h"
#include "application/prompt_template_repository.h"
#include "application/skill_hub_reading_settings.h"
#include "application/ssh_runtime_service.h"
#include "application/ssh_workspace_service.h"
#include "application/theme_settings.h"
#include "application/theme_settings_migration.h"
#include "application/tool_permission_service.h"
#include "application/tool_review_broker.h"
#include "application/user_agreement.h"
#include "infrastructure/app_logger.h"
#include "infrastructure/hux_completion_gateway.h"
#include "infrastructure/hux_known_hosts_store.h"
#include "infrastructure/hux_mcp_tool_invoker.h"
#include "infrastructure/libssh2_transport.h"
#include "infrastructure/persisted_ssh_settings.h"
#include "infrastructure/theme_file_settings_store.h"
#include "infrastructure/tool_settings_repository.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/main_screen.h"
#include "presentation/platform_features.h"
#include "presentation/screens/skill_hub_screens.h"

namespace linecode::app {

[[huxerui::composable]] huxerui::View AppContent() {
  const auto application = huxerui::UseApplication();
  const auto directories = application.Directories();
  static std::once_flag logger_setup;
  std::call_once(logger_setup, [&directories] {
    infrastructure::ConfigureAppLogger(
        directories.data_directory.Child("error_logs").Path());
  });
  auto http = huxerui::UseService<huxerui::HttpClient>();
  auto error_log_platform =
      huxerui::UseService<application::ErrorLogPlatformActions>();
  auto termux_integration =
      huxerui::UseService<application::TermuxIntegrationGateway>();
  auto skill_hub_platform =
      huxerui::UseService<application::SkillHubPlatformService>();
  auto share_text = huxerui::UseService<application::ShareTextService>();
  std::shared_ptr<application::StoragePermissionService> storage_permission;
  std::shared_ptr<application::WorkspaceDirectoryShareService>
      workspace_directory_share;
  std::shared_ptr<application::TerminalProviderGateway>
      terminal_provider_gateway;
  std::shared_ptr<application::TerminalProviderDiscovery>
      terminal_provider_discovery;
  if constexpr (presentation::FeatureAvailable<
                    presentation::PlatformFeature::
                        android_storage_permission>) {
    storage_permission =
        huxerui::UseService<application::StoragePermissionService>();
  }
  if constexpr (presentation::FeatureAvailable<
                    presentation::PlatformFeature::terminal_provider>) {
    terminal_provider_gateway =
        huxerui::UseService<application::TerminalProviderGateway>();
    terminal_provider_discovery = terminal_provider_gateway;
  }
  if constexpr (presentation::FeatureAvailable<
                    presentation::PlatformFeature::workspace_directory_share>) {
    workspace_directory_share =
        huxerui::UseService<application::WorkspaceDirectoryShareService>();
  }
  const bool host_is_dark = IsDark(huxerui::UseTheme().colors.background);
  auto system_theme =
      huxerui::UseState(std::make_shared<HostSystemThemeSource>(host_is_dark));
  system_theme.Get()->Update(host_is_dark);
  auto theme_store = huxerui::UseState(
      std::make_shared<infrastructure::ThemeFileSettingsStore>(
          directories.data_directory.Child("settings")));
  auto theme_service =
      huxerui::UseState(std::shared_ptr<application::ThemeSettingsService>{
          std::make_shared<application::ThemeSettingsRepository>(
              theme_store.Get(), system_theme.Get())});
  auto theme_settings = huxerui::UseState(theme_service.Get()->Load());
  auto tasks = huxerui::UseTaskScope();
  auto persistence_revision = huxerui::UseState(std::size_t{0});
  auto bootstrap_status = huxerui::UseState(BootstrapStatus{});
  auto chat = huxerui::UseState(std::make_shared<ChatSessionBootstrap>(
      tasks, [persistence_revision] { persistence_revision += 1; }));
  const auto data_directory = directories.data_directory;
  auto data_runtime = huxerui::UseState(BuildDataRuntime({
      .directories = directories,
      .http = http,
      .error_log_platform = error_log_platform,
  }));
  const auto database_file = data_runtime.Get()->database_file;
  const auto linecode_directory = data_runtime.Get()->linecode_directory;
  auto model_store = huxerui::UseState(data_runtime.Get()->models);
  auto memory_store = huxerui::UseState(data_runtime.Get()->memories);
  auto agent_extensions =
      huxerui::UseState(data_runtime.Get()->agent_extensions);
  auto mcp_extensions = huxerui::UseState(data_runtime.Get()->mcp_extensions);
  auto terminal_providers =
      huxerui::UseState(data_runtime.Get()->terminal_providers);
  auto mcp_tool_catalog = huxerui::UseState(data_runtime.Get()->mcp_catalog);
  auto model_catalog = huxerui::UseState(data_runtime.Get()->model_catalog);
  auto skill_repository = huxerui::UseState(data_runtime.Get()->skills);
  auto skill_hub_catalog =
      huxerui::UseState(data_runtime.Get()->skill_hub_catalog);
  auto skill_hub_session =
      huxerui::UseState(data_runtime.Get()->skill_hub_session);
  auto skill_management =
      huxerui::UseState(data_runtime.Get()->skill_management);
  auto settings_store = huxerui::UseState(data_runtime.Get()->settings);
  huxerui::Lifecycle([tasks, theme_store = theme_store.Get(), settings_store,
                      theme_service = theme_service.Get(), theme_settings] {
    tasks.Launch([theme_store, settings_store = settings_store.Get(),
                  theme_service, theme_settings]() -> huxerui::Task<void> {
      auto migrated =
          co_await application::ThemeSettingsMigration::ImportIfMissing(
              theme_store, settings_store);
      if (migrated && *migrated)
        theme_settings = theme_service->Load();
    });
  });
  auto user_agreement = huxerui::UseState(
      std::make_shared<application::UserAgreement>(settings_store.Get()));
  auto skill_hub_reading =
      huxerui::UseState(std::make_shared<application::SkillHubReadingSettings>(
          settings_store.Get()));
  auto ai_behavior_settings = huxerui::UseState(
      std::make_shared<application::AiBehaviorSettingsRepository>(
          settings_store.Get()));
  auto input_settings =
      huxerui::UseState(std::make_shared<application::InputSettingsRepository>(
          settings_store.Get()));
  auto tool_reviews =
      huxerui::UseState(std::make_shared<application::ToolReviewBroker>());
  auto tool_permissions =
      huxerui::UseState(std::make_shared<application::ToolPermissionService>(
          settings_store.Get()));
  auto chat_modes =
      huxerui::UseState(std::make_shared<application::ChatModeService>(
          settings_store.Get(), tool_permissions.Get()));
  auto prompt_templates =
      huxerui::UseState(std::make_shared<application::PromptTemplateRepository>(
          settings_store.Get()));
  auto prompt_request_composer = huxerui::UseState(
      std::shared_ptr<application::CompletionRequestComposer>{std::make_shared<
          application::PromptRequestComposer>(
          prompt_templates.Get(), ai_behavior_settings.Get(),
          std::make_shared<application::LegacyAttachmentPromptRenderer>())});
  auto output_settings_service =
      huxerui::UseState(std::shared_ptr<application::OutputSettingsService>{
          std::make_shared<application::PersistedOutputSettings>(
              settings_store.Get())});
  const domain::McpExecutionCapabilities mcp_capabilities{
      .terminal_provider = presentation::FeatureAvailable<
          presentation::PlatformFeature::terminal_provider>,
  };
  constexpr auto platform_capabilities =
      presentation::CurrentPlatformCapabilities();
  auto mcp_settings = huxerui::UseState(
      std::shared_ptr<application::McpExecutionSettingsService>{
          std::make_shared<application::McpExecutionSettingsRepository>(
              settings_store.Get(), mcp_capabilities)});
  auto tool_settings =
      huxerui::UseState(std::shared_ptr<application::ToolSettingsService>{
          std::make_shared<infrastructure::PersistedToolSettings>(
              settings_store.Get())});
  auto ssh_known_hosts =
      huxerui::UseState(std::shared_ptr<application::SshKnownHostsStore>{
          std::make_shared<infrastructure::HuxKnownHostsStore>(
              data_directory.Child("ssh").Child("known_hosts"))});
  auto ssh_transport =
      huxerui::UseState(std::shared_ptr<application::SshTransport>{
          std::make_shared<infrastructure::Libssh2Transport>(
              ssh_known_hosts.Get())});
  auto ssh_runtime = huxerui::UseState(
      std::make_shared<application::SshRuntimeService>(ssh_transport.Get()));
  auto ssh_workspace = huxerui::UseState(
      std::make_shared<application::SshWorkspaceService>(ssh_transport.Get()));
  auto ssh_settings =
      huxerui::UseState(std::shared_ptr<application::SshSettingsService>{
          std::make_shared<infrastructure::PersistedSshSettings>(
              settings_store.Get(), ssh_runtime.Get())});
  auto completion_gateway =
      huxerui::UseState(std::shared_ptr<application::CompletionGateway>{
          std::make_shared<infrastructure::HuxCompletionGateway>(http)});
  auto mcp_tool_invoker =
      huxerui::UseState(std::shared_ptr<application::McpToolInvoker>{
          std::make_shared<infrastructure::HuxMcpToolInvoker>(http)});
  // `UseString` validates the placeholder count, so a zero-placeholder probe
  // must be resolved without arguments (passing a fallback is a runtime error,
  // not a default).
  const auto tool_text_language = application::ToolTextLanguageFor(
      UseString(::app::strings::app_locale_probe));
  auto storage_stats = huxerui::UseState(data_runtime.Get()->storage_stats);
  auto error_logs = huxerui::UseState(data_runtime.Get()->error_logs);
  auto data_archive = huxerui::UseState(data_runtime.Get()->data_archive);
  huxerui::Lifecycle(
      [tasks, chat = chat.Get(), database_file, bootstrap_status] {
        tasks.Launch([chat, database_file,
                      bootstrap_status]() -> huxerui::Task<void> {
          auto initialized = co_await chat->InitializeAsync(database_file);
          if (!initialized) {
            bootstrap_status = BootstrapStatus{
                .phase = BootstrapPhase::failed,
                .error = std::move(initialized.error()),
            };
            co_return;
          }
          bootstrap_status = BootstrapStatus{.phase = BootstrapPhase::ready};
        });
      });
  static_cast<void>(persistence_revision.Get());

  auto project_runtime = huxerui::UseState(BuildProjectRuntime({
      .database_file = database_file,
      .linecode_directory = linecode_directory,
      .execution_settings = mcp_settings.Get(),
      .ssh_settings = ssh_settings.Get(),
      .ssh_files = ssh_workspace.Get(),
      .terminal_providers = terminal_providers.Get(),
      .terminal_gateway = terminal_provider_gateway,
  }));
  const auto &local_project_workspace = project_runtime.Get()->local;
  const auto &ssh_project_workspace = project_runtime.Get()->ssh;
  const auto &project_workspace = project_runtime.Get()->routed;
  const auto &workspace_images = project_runtime.Get()->images;
  auto diff_store = huxerui::UseState(data_runtime.Get()->diffs);
  auto diff_review = huxerui::UseState(data_runtime.Get()->diff_review);
  auto tool_runtime = huxerui::UseState(BuildToolRuntime({
      .http = http,
      .tasks = tasks,
      .execution_settings = mcp_settings.Get(),
      .tool_settings = tool_settings.Get(),
      .models = model_store.Get(),
      .prompt_templates = prompt_templates.Get(),
      .memories = memory_store.Get(),
      .project_workspace = project_workspace,
      .local_workspace = local_project_workspace,
      .ssh_workspace = ssh_project_workspace,
      .workspace_images = workspace_images,
      .diffs = diff_store.Get(),
      .ssh_settings = ssh_settings.Get(),
      .ssh_runtime = ssh_runtime.Get(),
      .terminal_providers = terminal_providers.Get(),
      .terminal_gateway = terminal_provider_gateway,
      .mcp_extensions = mcp_extensions.Get(),
      .mcp_invoker = mcp_tool_invoker.Get(),
      .agent_extensions = agent_extensions.Get(),
      .completion = completion_gateway.Get(),
      .request_composer = prompt_request_composer.Get(),
      .permissions = tool_permissions.Get(),
      .reviews = tool_reviews.Get(),
      .skills = skill_repository.Get(),
      .conversation = chat.Get()->Session(),
      .language = tool_text_language,
  }));
  const auto line_colors =
      presentation::LineColorsForPalette(theme_settings->palette);
  auto theme = presentation::LineThemeDefinition(line_colors);
  auto drawer_style = presentation::LegacyDrawerStyle();
  drawer_style.background = line_colors.background;
  drawer_style.scrim = line_colors.overlay;
  theme.Set(std::move(drawer_style));
  huxerui::View main_content = huxerui::Scope(
      [initial_session = chat.Get()->Session(), project_workspace,
       model_store = model_store.Get(), model_catalog = model_catalog.Get(),
       ai_behavior_settings = ai_behavior_settings.Get(),
       input_settings = input_settings.Get(),
       prompt_templates = prompt_templates.Get(),
       output_settings_service = output_settings_service.Get(),
       user_agreement = user_agreement.Get(),
       completion_loop = tool_runtime.Get()->completion_loop,
       agent_results =
           std::shared_ptr<application::AgentResultReader>{
               tool_runtime.Get()->agent_results},
       theme_service = theme_service.Get(), theme_settings,
       mcp_settings = mcp_settings.Get(), tool_settings = tool_settings.Get(),
       tool_permissions = tool_permissions.Get(),
       tool_reviews = tool_reviews.Get(), chat_modes = chat_modes.Get(),
       ssh_settings = ssh_settings.Get(), memory_store = memory_store.Get(),
       todo_state = tool_runtime.Get()->todos, skills = skill_repository.Get(),
       compaction_service = tool_runtime.Get()->compaction,
       diff_store = diff_store.Get(), diff_review = diff_review.Get(),
       agent_extensions = agent_extensions.Get(),
       mcp_extensions = mcp_extensions.Get(),
       mcp_tool_catalog = mcp_tool_catalog.Get(),
       agent_drafts = tool_runtime.Get()->agent_drafts,
       linecode_root = linecode_directory.Path(),
       skill_hub_services =
           presentation::SkillHubScreenServices{
               .catalog = skill_hub_catalog.Get(),
               .session = skill_hub_session.Get(),
               .repository = skill_repository.Get(),
               .management = skill_management.Get(),
               .platform = std::move(skill_hub_platform),
               .share = std::move(share_text),
               .reading = skill_hub_reading.Get(),
               .roots = {.app = linecode_directory.Child("skills")}},
       mcp_capabilities, platform_capabilities,
       termux_integration = std::move(termux_integration),
       terminal_providers = terminal_providers.Get(),
       terminal_provider_discovery = std::move(terminal_provider_discovery),
       storage_permission = std::move(storage_permission),
       workspace_directory_share = std::move(workspace_directory_share),
       storage_stats = storage_stats.Get(), error_logs = error_logs.Get(),
       data_archive = data_archive.Get(),
       data_callbacks = presentation::DataSettingsCallbacks{
           .persist_before_export =
               [chat = chat.Get()] { return chat->PersistAsync(); },
           .before_import = {},
           .after_import = [chat = chat.Get()] { return chat->ReloadAsync(); },
       }] {
        return presentation::MainScreen(
            initial_session, project_workspace, model_store, model_catalog,
            ai_behavior_settings, input_settings, prompt_templates,
            completion_loop, agent_results, output_settings_service,
            user_agreement, theme_service, theme_settings, mcp_settings,
            tool_settings, tool_permissions, tool_reviews, chat_modes,
            ssh_settings, memory_store, todo_state, skills, mcp_settings,
            compaction_service, diff_store, diff_review, agent_extensions,
            mcp_extensions, mcp_tool_catalog, agent_drafts, linecode_root,
            skill_hub_services, mcp_capabilities, platform_capabilities,
            termux_integration, terminal_providers, terminal_provider_discovery,
            storage_permission, workspace_directory_share, storage_stats,
            error_logs, data_archive, data_callbacks);
      });
  if (bootstrap_status->phase == BootstrapPhase::failed) {
    main_content = DatabaseFailureView(
        huxerui::UseString(::app::strings::app_database_failed_title),
        huxerui::UseString(::app::strings::app_database_failed_message,
                           bootstrap_status->error),
        line_colors);
  }
  return huxerui::Stack{
      huxerui::ProvideEnvironment(
          line_colors,
          huxerui::Theme(std::move(theme), std::move(main_content))),
      PlatformServicesHost(),
  }
      .With(huxerui::SystemBarsAppearance{
          .status_bar_background = line_colors.background,
          .navigation_bar_background = line_colors.background,
          .status_bar_content = huxerui::SystemBarContentBrightness::Automatic,
          .navigation_bar_content =
              huxerui::SystemBarContentBrightness::Automatic,
      });
}

huxerui::View AppRoot() { return AppContent(); }

} // namespace linecode::app
