#include "app/app_root.h"

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <utility>
#include <vector>

#include <huxerui/http.h>
#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "app/bootstrap.h"
#include "application/agent_extension_draft.h"
#include "application/agent_extension_tool_registry.h"
#include "application/agent_result_registry.h"
#include "application/agent_result_registry_sink.h"
#include "application/agent_tool_registry.h"
#include "application/sub_agent_runner.h"
#include "application/behavior_settings_repository.h"
#include "application/chat_mode_service.h"
#include "application/composite_tool_registry.h"
#include "application/context_compaction.h"
#include "application/tool_loop_compactor.h"
#include "application/diff_review_service.h"
#include "application/error_log_service.h"
#include "application/execution_mode_project_workspace.h"
#include "application/file_tool_registry.h"
#include "application/image_generation_tool_registry.h"
#include "application/image_understanding_tool_registry.h"
#include "application/in_memory_todo_state_store.h"
#include "application/legacy_attachment_prompt_renderer.h"
#include "application/mcp_completion_loop.h"
#include "application/mcp_execution_settings.h"
#include "application/mcp_extension_tool_registry.h"
#include "application/memory_tool_registry.h"
#include "application/mode_workspace_image_reader.h"
#include "application/output_settings.h"
#include "application/todo_tool_registry.h"
#include "application/web_tool_registry.h"
#include "application/ports/workspace_directory_share.h"
#include "application/project_workspace_service.h"
#include "application/prompt_request_composer.h"
#include "application/prompt_template_repository.h"
#include "application/skill_management_service.h"
#include "application/skill_repository.h"
#include "application/ssh_project_workspace.h"
#include "application/ssh_runtime_service.h"
#include "application/ssh_tool_registry.h"
#include "application/ssh_workspace_service.h"
#include "application/skill_hub_reading_settings.h"
#include "application/terminal_provider_tool_registry.h"
#include "application/theme_settings.h"
#include "application/tool_permission_service.h"
#include "application/user_agreement.h"
#if defined(__ANDROID__)
#include "application/ports/keep_alive.h"
#include "application/ports/todo_state_store.h"
#include "application/ports/web_tools.h"
#endif
#include "application/ports/storage_permission.h"
#include "application/ports/terminal_provider.h"
#include "application/ports/termux_integration.h"
#include "infrastructure/hux_completion_gateway.h"
#include "infrastructure/hux_data_archive_service.h"
#include "infrastructure/hux_error_log_store.h"
#include "infrastructure/hux_image_generation_gateway.h"
#include "infrastructure/hux_image_understanding_gateway.h"
#include "infrastructure/hux_known_hosts_store.h"
#include "infrastructure/hux_mcp_tool_catalog.h"
#include "infrastructure/hux_mcp_tool_invoker.h"
#include "infrastructure/hux_model_catalog_gateway.h"
#include "infrastructure/hux_skill_files.h"
#include "infrastructure/hux_skill_hub_gateway.h"
#include "infrastructure/hux_skill_hub_session_gateway.h"
#include "infrastructure/hux_tool_file_access.h"
#include "infrastructure/sqlite_diff_file_restorer.h"
#include "infrastructure/sqlite_diff_store.h"
#include "infrastructure/hux_web_tools_gateway.h"
#include "infrastructure/hux_storage_stats_repository.h"
#include "infrastructure/hux_workspace_file_store.h"
#include "infrastructure/image_generation_codec.h"
#include "infrastructure/image_understanding_codec.h"
#include "infrastructure/json_mcp_tool_schema_policy.h"
#include "infrastructure/json_agent_extension_draft_codec.h"
#include "infrastructure/libssh2_transport.h"
#include "infrastructure/persisted_ssh_settings.h"
#include "infrastructure/sqlite_archive_database.h"
#include "infrastructure/sqlite_extension_store.h"
#include "infrastructure/sqlite_memory_store.h"
#include "infrastructure/sqlite_model_store.h"
#include "infrastructure/sqlite_project_catalog_store.h"
#include "infrastructure/sqlite_settings_store.h"
#include "infrastructure/sqlite_skill_record_store.h"
#include "infrastructure/theme_file_settings_store.h"
#include "infrastructure/tool_settings_repository.h"
#include "infrastructure/workspace_image_readers.h"
#include "presentation/components/drawer.h"
#include "presentation/line_theme.h"
#include "presentation/main_screen.h"
#include "presentation/platform_features.h"
#include "presentation/screens/skill_hub_screens.h"

namespace linecode::app {
namespace {

class HostSystemThemeSource final : public application::SystemThemeSource {
public:
  explicit HostSystemThemeSource(bool dark) : dark_(dark) {}

  [[nodiscard]] bool IsDarkModeEnabled() const override { return dark_; }
  void Update(bool dark) { dark_ = dark; }

private:
  bool dark_{};
};

class SystemWorkspaceClock final : public application::WorkspaceClock {
public:
  [[nodiscard]] std::int64_t NowMilliseconds() const noexcept override {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
  }
};

bool IsDark(const huxerui::Color &color) {
  const float luminance =
      color.red * 0.2126F + color.green * 0.7152F + color.blue * 0.0722F;
  return luminance < 0.5F;
}

#if defined(__ANDROID__)
[[huxerui::composable]] huxerui::View PlatformServicesHost() {
  auto keep_alive = huxerui::UseService<application::KeepAliveService>();
  huxerui::Lifecycle(
      [keep_alive] { keep_alive->RefreshPreferences([](auto) {}); });
  return huxerui::Stack{}.With(huxerui::Frame{.width = 0.0F, .height = 0.0F});
}

#else
huxerui::View PlatformServicesHost() {
  return huxerui::Stack{}.With(huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#endif

} // namespace

[[huxerui::composable]] huxerui::View AppContent() {
  const auto application = huxerui::UseApplication();
  const auto directories = application.Directories();
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
                    presentation::PlatformFeature::
                        workspace_directory_share>) {
    workspace_directory_share = huxerui::UseService<
        application::WorkspaceDirectoryShareService>();
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
  auto chat = huxerui::UseState(std::make_shared<ChatSessionBootstrap>(
      tasks, [persistence_revision] { persistence_revision += 1; }));
  const auto data_directory = directories.data_directory;
  const auto database_file = data_directory.Child("linecode.db");
  const auto linecode_directory = data_directory.Child(".linecode");
  auto model_store = huxerui::UseState(std::shared_ptr<application::ModelStore>{
      std::make_shared<infrastructure::SqliteModelStore>(database_file)});
  auto memory_store =
      huxerui::UseState(std::shared_ptr<application::MemoryStore>{
          std::make_shared<infrastructure::SqliteMemoryStore>(database_file)});
  auto extension_store = huxerui::UseState(
      std::make_shared<infrastructure::SqliteExtensionStore>(database_file));
  auto agent_extensions = huxerui::UseState(
      std::shared_ptr<application::AgentExtensionStore>{extension_store.Get()});
  auto mcp_extensions = huxerui::UseState(
      std::shared_ptr<application::McpExtensionStore>{extension_store.Get()});
  auto terminal_providers =
      huxerui::UseState(std::shared_ptr<application::TerminalProviderStore>{
          extension_store.Get()});
  auto mcp_tool_catalog =
      huxerui::UseState(std::shared_ptr<application::McpToolCatalog>{
          std::make_shared<infrastructure::HuxMcpToolCatalog>(http)});
  auto model_catalog =
      huxerui::UseState(std::shared_ptr<application::ModelCatalogGateway>{
          std::make_shared<infrastructure::HuxModelCatalogGateway>(http)});
  auto skill_files = huxerui::UseState(std::shared_ptr<application::SkillFiles>{
      std::make_shared<infrastructure::HuxSkillFiles>()});
  auto skill_records =
      huxerui::UseState(std::shared_ptr<application::SkillRecordStore>{
          std::make_shared<infrastructure::SqliteSkillRecordStore>(
              database_file)});
  auto skill_repository =
      huxerui::UseState(std::make_shared<application::SkillRepository>(
          skill_files.Get(), skill_records.Get()));
  auto skill_hub_gateway = huxerui::UseState(
      std::make_shared<infrastructure::HuxSkillHubGateway>(http));
  auto skill_hub_catalog = huxerui::UseState(
      std::shared_ptr<application::SkillHubCatalog>{skill_hub_gateway.Get()});
  auto skill_hub_packages =
      huxerui::UseState(std::shared_ptr<application::SkillPackageGateway>{
          skill_hub_gateway.Get()});
  auto skill_hub_session =
      huxerui::UseState(std::shared_ptr<application::SkillHubSessionGateway>{
          std::make_shared<infrastructure::HuxSkillHubSessionGateway>(http)});
  auto skill_management =
      huxerui::UseState(std::make_shared<application::SkillManagementService>(
          skill_repository.Get(), skill_hub_packages.Get()));
  auto settings_store =
      huxerui::UseState(std::shared_ptr<application::AsyncSettingsStore>{
          std::make_shared<infrastructure::SQLiteSettingsStore>(
              database_file)});
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
  auto mcp_extension_tools =
      huxerui::UseState(std::make_shared<application::McpExtensionToolRegistry>(
          mcp_extensions.Get(), mcp_tool_invoker.Get(),
          std::make_shared<infrastructure::JsonMcpToolSchemaPolicy>()));
  auto image_generation_tools =
      huxerui::UseState(std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::ImageGenerationToolRegistry>(
              mcp_settings.Get(), tool_settings.Get(), model_store.Get(),
              std::make_shared<
                  infrastructure::JsonImageGenerationToolCodec>(),
              std::make_shared<
                  infrastructure::HuxImageGenerationGateway>(http))});
  std::vector<std::shared_ptr<application::ToolRegistry>> tool_sources{
      mcp_extension_tools.Get(), image_generation_tools.Get()};
  // `UseString` validates the placeholder count, so a zero-placeholder probe
  // must be resolved without arguments (passing a fallback is a runtime error,
  // not a default).
  const auto tool_text_language = application::ToolTextLanguageFor(
      UseString(::app::strings::app_locale_probe));
  tool_sources.push_back(std::make_shared<application::SshToolRegistry>(
      mcp_settings.Get(), ssh_settings.Get(), ssh_runtime.Get(),
      tool_text_language));
  if (terminal_provider_gateway) {
    tool_sources.push_back(
        std::make_shared<application::TerminalProviderToolRegistry>(
            mcp_settings.Get(), terminal_providers.Get(),
            terminal_provider_gateway));
  }
  auto storage_stats =
      huxerui::UseState(std::shared_ptr<application::StorageStatsRepository>{
          std::make_shared<infrastructure::HuxStorageStatsRepository>(
              database_file,
              std::vector<huxerui::File>{data_directory.Child("settings")},
              data_directory.Child(".linecode").Child("home"))});
  auto error_logs =
      huxerui::UseState(std::shared_ptr<application::ErrorLogService>{
          std::make_shared<application::ErrorLogService>(
              std::make_shared<infrastructure::HuxErrorLogStore>(
                  data_directory),
              error_log_platform)});
  auto archive_database =
      huxerui::UseState(std::shared_ptr<application::ArchiveDatabase>{
          std::make_shared<infrastructure::SqliteArchiveDatabase>(
              database_file)});
  auto data_archive =
      huxerui::UseState(std::shared_ptr<application::DataArchiveService>{
          std::make_shared<infrastructure::HuxDataArchiveService>(
              archive_database.Get(),
              directories.temporary_directory.Child("linecode-archives"),
              linecode_directory.Child("home"),
              linecode_directory.Child("project"),
              linecode_directory.Child("skills"))});
  huxerui::Lifecycle([tasks, chat = chat.Get(), database_file] {
    tasks.Launch(
        [chat, database_file] { return chat->InitializeAsync(database_file); });
  });
  static_cast<void>(persistence_revision.Get());

  auto local_project_catalog =
      huxerui::UseState(std::shared_ptr<application::ProjectCatalogStore>{
          std::make_shared<infrastructure::SqliteProjectCatalogStore>(
              database_file)});
  auto project_files =
      huxerui::UseState(std::shared_ptr<application::WorkspaceFileStore>{
          std::make_shared<infrastructure::HuxWorkspaceFileStore>(
              linecode_directory.Child("home"),
              linecode_directory.Child("project"))});
  auto workspace_clock =
      huxerui::UseState(std::shared_ptr<application::WorkspaceClock>{
          std::make_shared<SystemWorkspaceClock>()});
  auto local_project_workspace = huxerui::UseState(
      std::shared_ptr<application::ProjectWorkspaceController>{
          std::make_shared<application::ProjectWorkspaceService>(
              local_project_catalog.Get(), project_files.Get(),
              workspace_clock.Get())});
  auto ssh_project_catalog =
      huxerui::UseState(std::shared_ptr<application::ProjectCatalogStore>{
          std::make_shared<infrastructure::SqliteProjectCatalogStore>(
              database_file, infrastructure::ProjectCatalogScope::ssh)});
  auto ssh_project_workspace = huxerui::UseState(
      std::shared_ptr<application::ProjectWorkspaceController>{
          std::make_shared<application::SshProjectWorkspace>(
              ssh_project_catalog.Get(), ssh_settings.Get(),
              ssh_workspace.Get(), workspace_clock.Get())});
  auto project_workspace = huxerui::UseState(
      std::shared_ptr<application::ProjectWorkspaceController>{
          std::make_shared<application::ExecutionModeProjectWorkspace>(
              mcp_settings.Get(),
              std::vector<application::ProjectWorkspaceRoute>{
                  {.mode = domain::McpExecutionMode::local,
                   .controller = local_project_workspace.Get()},
                  {.mode = domain::McpExecutionMode::ssh,
                   .controller = ssh_project_workspace.Get()},
                  {.mode = domain::McpExecutionMode::terminal_provider,
                   .controller = local_project_workspace.Get()},
              })});
  std::vector<application::WorkspaceImageReaderRoute> image_reader_routes{
      {.mode = domain::McpExecutionMode::local,
       .reader = std::make_shared<
           infrastructure::LocalWorkspaceImageReader>(
           local_project_workspace.Get())},
      {.mode = domain::McpExecutionMode::ssh,
       .reader =
           std::make_shared<infrastructure::SshWorkspaceImageReader>(
               ssh_settings.Get(), ssh_project_workspace.Get(),
               ssh_workspace.Get())},
  };
  if (terminal_provider_gateway) {
    image_reader_routes.push_back(
        {.mode = domain::McpExecutionMode::terminal_provider,
         .reader = std::make_shared<
             infrastructure::TerminalProviderWorkspaceImageReader>(
             terminal_providers.Get(), terminal_provider_gateway)});
  }
  auto workspace_images =
      huxerui::UseState(std::shared_ptr<application::WorkspaceImageReader>{
          std::make_shared<application::ModeWorkspaceImageReader>(
              mcp_settings.Get(), std::move(image_reader_routes))});
  auto image_understanding_tools =
      huxerui::UseState(std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::ImageUnderstandingToolRegistry>(
              mcp_settings.Get(), tool_settings.Get(), model_store.Get(),
              prompt_templates.Get(), workspace_images.Get(),
              std::make_shared<
                  infrastructure::JsonImageUnderstandingToolCodec>(),
              std::make_shared<
                  infrastructure::HuxImageUnderstandingGateway>(http))});
  tool_sources.push_back(image_understanding_tools.Get());
  // Built-in tool groups. The legacy product registered these unconditionally
  // through `BuiltInToolProviders.defaults()`; each registry below still
  // applies its own group enablement and execution-mode gate, so this only
  // decides which families exist at all.
  // File-change history: the write tools record here and the chat card reads
  // the bodies back for its diff view.
  auto diff_store = huxerui::UseState(std::shared_ptr<application::DiffStore>{
      std::make_shared<infrastructure::SqliteDiffStore>(database_file)});
  auto diff_restore = huxerui::UseState(
      std::shared_ptr<application::DiffFileRestore>{
          std::make_shared<infrastructure::SqliteDiffFileRestorer>()});
  auto diff_review = huxerui::UseState(
      std::shared_ptr<application::DiffReviewService>{
          std::make_shared<application::DiffReviewService>(*diff_store.Get(),
                                                           *diff_restore.Get())});
  auto compaction_service = huxerui::UseState(
      std::shared_ptr<application::ContextCompactionService>{
          std::make_shared<application::ContextCompactionService>(
              completion_gateway.Get(), prompt_templates.Get(),
              model_store.Get())});
  auto todo_state = huxerui::UseState(
      std::shared_ptr<application::TodoStateStore>{
          std::make_shared<application::InMemoryTodoStateStore>()});
  auto todo_tools = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::TodoToolRegistry>(mcp_settings.Get(),
                                                         todo_state.Get())});
  auto memory_tools = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::MemoryToolRegistry>(
              mcp_settings.Get(), memory_store.Get(),
              project_workspace.Get())});
  auto web_tools = huxerui::UseState(
      std::shared_ptr<application::WebToolsGateway>{
          std::make_shared<infrastructure::HuxWebToolsGateway>(http)});
  auto web_tool_registry = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::WebToolRegistry>(
              mcp_settings.Get(), tool_settings.Get(), web_tools.Get())});
  // `file_ops` is local-only in the legacy product, so its registry owns the
  // mode gate and the workspace-relative path policy.
  auto tool_file_access = huxerui::UseState(
      std::shared_ptr<application::ToolFileAccess>{
          std::make_shared<infrastructure::HuxToolFileAccess>()});
  // Tool-internal messages are shown to the user on failure, so they follow
  // the UI language. A locale is only observable by resolving a resource
  // during composition, hence the probe.
  auto file_tools = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::FileToolRegistry>(
              mcp_settings.Get(), project_workspace.Get(),
              tool_file_access.Get(), tool_text_language, diff_store.Get())});
  tool_sources.push_back(todo_tools.Get());
  tool_sources.push_back(memory_tools.Get());
  tool_sources.push_back(web_tool_registry.Get());
  tool_sources.push_back(file_tools.Get());
  // Sub-agents never receive the agent tools themselves (legacy
  // `AgentExecutionController.isAgentToolAllowed` excluded `agent` and
  // `agent_pipeline`), so the runner is built against the non-agent composite
  // and the top-level registry adds the agent group on top of it.
  auto base_tools =
      huxerui::UseState(std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::CompositeToolRegistry>(
              std::move(tool_sources))});
  auto agent_results = huxerui::UseState(
      std::make_shared<application::AgentResultRegistry>());
  auto sub_agent_runner = huxerui::UseState(
      std::shared_ptr<application::SubAgentRunner>{
          std::make_shared<application::SubAgentRunner>(
              completion_gateway.Get(), base_tools.Get(),
              prompt_templates.Get(), model_store.Get(),
              std::make_shared<application::AgentResultRegistrySink>(
                  agent_results.Get()),
              std::make_shared<application::SkillRepositoryExtensionPromptSource>(
                  skill_repository.Get()),
              std::make_shared<application::TaskScopeSubAgentLauncher>(
                  tasks),
              application::SubAgentEnvironment{},
              nullptr, tool_permissions.Get(), tool_reviews.Get())});
  auto agent_tools = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::AgentToolRegistry>(
              mcp_settings.Get(), agent_results.Get(),
              sub_agent_runner.Get(), tool_text_language)});
  auto agent_extension_tools = huxerui::UseState(
      std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::AgentExtensionToolRegistry>(
              agent_extensions.Get(), sub_agent_runner.Get(),
              tool_text_language)});
  auto runtime_tools =
      huxerui::UseState(std::shared_ptr<application::ToolRegistry>{
          std::make_shared<application::CompositeToolRegistry>(
              std::vector<std::shared_ptr<application::ToolRegistry>>{
                  base_tools.Get(), agent_tools.Get(),
                  agent_extension_tools.Get()})});
  auto agent_drafts = huxerui::UseState(
      std::shared_ptr<application::AgentExtensionDraftGenerator>{
          std::make_shared<application::CompletionAgentExtensionDraftGenerator>(
              model_store.Get(), completion_gateway.Get(), runtime_tools.Get(),
              mcp_extensions.Get(),
              std::make_shared<infrastructure::JsonAgentExtensionDraftCodec>())});
  auto completion_loop =
      huxerui::UseState(std::make_shared<application::McpCompletionLoop>(
          completion_gateway.Get(), runtime_tools.Get(), tool_permissions.Get(),
          prompt_request_composer.Get(), nullptr,
          std::make_shared<application::ToolLoopCompactor>(
              compaction_service.Get(), model_store.Get(),
              // Product default; `AiBehaviorSettings` loads asynchronously, so
              // the loop uses the same default the legacy controller saw when
              // the setting was untouched.
              domain::AiBehaviorSettings{}.preserve_reasoning,
              // Mid-loop compaction writes the summary back to the same
              // conversation the pre-request path rewrites.
              chat.Get()->Session())));
  const auto line_colors =
      presentation::LineColorsForPalette(theme_settings->palette);
  auto theme = presentation::LineThemeDefinition(line_colors);
  auto drawer_style = presentation::LegacyDrawerStyle();
  drawer_style.background = line_colors.background;
  drawer_style.scrim = line_colors.overlay;
  theme.Set(std::move(drawer_style));
  auto main_content = huxerui::Scope(
      [initial_session = chat.Get()->Session(),
       project_workspace = project_workspace.Get(),
       model_store = model_store.Get(), model_catalog = model_catalog.Get(),
       ai_behavior_settings = ai_behavior_settings.Get(),
       input_settings = input_settings.Get(),
       prompt_templates = prompt_templates.Get(),
       output_settings_service = output_settings_service.Get(),
       user_agreement = user_agreement.Get(),
       completion_loop = completion_loop.Get(),
       theme_service = theme_service.Get(), theme_settings,
       mcp_settings = mcp_settings.Get(), tool_settings = tool_settings.Get(),
       tool_permissions = tool_permissions.Get(),
       tool_reviews = tool_reviews.Get(), chat_modes = chat_modes.Get(),
       ssh_settings = ssh_settings.Get(), memory_store = memory_store.Get(),
       todo_state = todo_state.Get(),
       skills = skill_repository.Get(),
       compaction_service = compaction_service.Get(),
       diff_store = diff_store.Get(), diff_review = diff_review.Get(),
       agent_extensions = agent_extensions.Get(),
       mcp_extensions = mcp_extensions.Get(),
       mcp_tool_catalog = mcp_tool_catalog.Get(),
       agent_drafts = agent_drafts.Get(),
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
            completion_loop, output_settings_service, user_agreement,
            theme_service,
            theme_settings, mcp_settings, tool_settings, tool_permissions,
            tool_reviews, chat_modes, ssh_settings, memory_store, todo_state,
            skills,
            mcp_settings, compaction_service, diff_store, diff_review,
            agent_extensions,
            mcp_extensions, mcp_tool_catalog, agent_drafts, linecode_root,
            skill_hub_services, mcp_capabilities, platform_capabilities,
            termux_integration, terminal_providers,
            terminal_provider_discovery, storage_permission,
            workspace_directory_share, storage_stats, error_logs,
            data_archive, data_callbacks);
      });
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
