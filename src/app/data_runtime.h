#pragma once

#include <memory>

#include <huxerui/file.h>
#include <huxerui/http.h>

namespace linecode::application {
class AgentExtensionStore;
class AsyncSettingsStore;
class DataArchiveService;
class DiffFileRestore;
class DiffReviewService;
class DiffStore;
class ErrorLogPlatformActions;
class ErrorLogService;
class McpExtensionStore;
class McpToolCatalog;
class MemoryStore;
class ModelCatalogGateway;
class ModelStore;
class SkillHubCatalog;
class SkillHubSessionGateway;
class SkillManagementService;
class SkillRepository;
class StorageStatsRepository;
class TerminalProviderStore;
} // namespace linecode::application

namespace linecode::app {

struct DataRuntimeDependencies final {
  huxerui::AppDirectories directories;
  std::shared_ptr<huxerui::HttpClient> http;
  std::shared_ptr<application::ErrorLogPlatformActions> error_log_platform;
};

struct DataRuntime final {
  huxerui::File database_file;
  huxerui::File linecode_directory;
  std::shared_ptr<application::ModelStore> models;
  std::shared_ptr<application::MemoryStore> memories;
  std::shared_ptr<application::AgentExtensionStore> agent_extensions;
  std::shared_ptr<application::McpExtensionStore> mcp_extensions;
  std::shared_ptr<application::TerminalProviderStore> terminal_providers;
  std::shared_ptr<application::McpToolCatalog> mcp_catalog;
  std::shared_ptr<application::ModelCatalogGateway> model_catalog;
  std::shared_ptr<application::SkillRepository> skills;
  std::shared_ptr<application::SkillHubCatalog> skill_hub_catalog;
  std::shared_ptr<application::SkillHubSessionGateway> skill_hub_session;
  std::shared_ptr<application::SkillManagementService> skill_management;
  std::shared_ptr<application::AsyncSettingsStore> settings;
  std::shared_ptr<application::StorageStatsRepository> storage_stats;
  std::shared_ptr<application::ErrorLogService> error_logs;
  std::shared_ptr<application::DataArchiveService> data_archive;
  std::shared_ptr<application::DiffStore> diffs;
  std::shared_ptr<application::DiffFileRestore> diff_restore;
  std::shared_ptr<application::DiffReviewService> diff_review;
};

[[nodiscard]] std::shared_ptr<DataRuntime>
BuildDataRuntime(DataRuntimeDependencies dependencies);

} // namespace linecode::app
