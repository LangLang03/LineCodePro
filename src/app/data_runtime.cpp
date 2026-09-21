#include "app/data_runtime.h"

#include <utility>
#include <vector>

#include "app/app_root_support.h"
#include "application/diff_review_service.h"
#include "application/error_log_service.h"
#include "application/skill_management_service.h"
#include "application/skill_repository.h"
#include "infrastructure/hux_data_archive_service.h"
#include "infrastructure/hux_error_log_store.h"
#include "infrastructure/hux_mcp_tool_catalog.h"
#include "infrastructure/hux_model_catalog_gateway.h"
#include "infrastructure/hux_skill_files.h"
#include "infrastructure/hux_skill_hub_gateway.h"
#include "infrastructure/hux_skill_hub_session_gateway.h"
#include "infrastructure/hux_storage_stats_repository.h"
#include "infrastructure/sqlite_archive_database.h"
#include "infrastructure/sqlite_diff_file_restorer.h"
#include "infrastructure/sqlite_diff_store.h"
#include "infrastructure/sqlite_extension_store.h"
#include "infrastructure/sqlite_memory_store.h"
#include "infrastructure/sqlite_model_store.h"
#include "infrastructure/sqlite_settings_store.h"
#include "infrastructure/sqlite_skill_record_store.h"

namespace linecode::app {

std::shared_ptr<DataRuntime>
BuildDataRuntime(DataRuntimeDependencies dependencies) {
  const auto data_directory = dependencies.directories.data_directory;
  const auto database_file = DatabaseFileFor(data_directory);
  const auto linecode_directory = data_directory.Child(".linecode");

  auto models =
      std::make_shared<infrastructure::SqliteModelStore>(database_file);
  auto memories =
      std::make_shared<infrastructure::SqliteMemoryStore>(database_file);
  auto extensions =
      std::make_shared<infrastructure::SqliteExtensionStore>(database_file);
  auto skills = std::make_shared<application::SkillRepository>(
      std::make_shared<infrastructure::HuxSkillFiles>(),
      std::make_shared<infrastructure::SqliteSkillRecordStore>(database_file));
  auto skill_hub =
      std::make_shared<infrastructure::HuxSkillHubGateway>(dependencies.http);
  auto skill_hub_session =
      std::make_shared<infrastructure::HuxSkillHubSessionGateway>(
          dependencies.http);
  auto skill_management =
      std::make_shared<application::SkillManagementService>(skills, skill_hub);
  auto settings =
      std::make_shared<infrastructure::SQLiteSettingsStore>(database_file);
  auto storage_stats =
      std::make_shared<infrastructure::HuxStorageStatsRepository>(
          database_file,
          std::vector<huxerui::File>{data_directory.Child("settings")},
          linecode_directory.Child("home"));
  auto error_logs = std::make_shared<application::ErrorLogService>(
      std::make_shared<infrastructure::HuxErrorLogStore>(data_directory),
      std::move(dependencies.error_log_platform));
  auto archive_database =
      std::make_shared<infrastructure::SqliteArchiveDatabase>(database_file);
  auto data_archive = std::make_shared<infrastructure::HuxDataArchiveService>(
      std::move(archive_database),
      dependencies.directories.temporary_directory.Child("linecode-archives"),
      linecode_directory.Child("home"), linecode_directory.Child("project"),
      linecode_directory.Child("skills"));
  auto diffs = std::make_shared<infrastructure::SqliteDiffStore>(database_file);
  auto diff_restore =
      std::make_shared<infrastructure::SqliteDiffFileRestorer>();
  auto diff_review =
      std::make_shared<application::DiffReviewService>(*diffs, *diff_restore);

  return std::make_shared<DataRuntime>(DataRuntime{
      .database_file = database_file,
      .linecode_directory = linecode_directory,
      .models = std::move(models),
      .memories = std::move(memories),
      .agent_extensions = extensions,
      .mcp_extensions = extensions,
      .terminal_providers = std::move(extensions),
      .mcp_catalog = std::make_shared<infrastructure::HuxMcpToolCatalog>(
          dependencies.http),
      .model_catalog = std::make_shared<infrastructure::HuxModelCatalogGateway>(
          dependencies.http),
      .skills = std::move(skills),
      .skill_hub_catalog = skill_hub,
      .skill_hub_session = std::move(skill_hub_session),
      .skill_management = std::move(skill_management),
      .settings = std::move(settings),
      .storage_stats = std::move(storage_stats),
      .error_logs = std::move(error_logs),
      .data_archive = std::move(data_archive),
      .diffs = std::move(diffs),
      .diff_restore = std::move(diff_restore),
      .diff_review = std::move(diff_review),
  });
}

} // namespace linecode::app
