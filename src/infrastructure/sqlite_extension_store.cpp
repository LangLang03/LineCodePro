#include "infrastructure/sqlite_extension_store.h"

#include <array>
#include <chrono>
#include <cstdint>
#include <format>
#include <optional>
#include <random>
#include <stdexcept>
#include <string>
#include <utility>

#include <huxerui/sqlite.h>

#include "infrastructure/extension_config_codec.h"
#include "infrastructure/legacy_feature_schema.h"

namespace linecode::infrastructure {

class SqliteExtensionStoreState final {
public:
  explicit SqliteExtensionStoreState(huxerui::File database_file)
      : database_file(std::move(database_file)) {}

  huxerui::File database_file;
  std::optional<huxerui::sqlite::Database> database;
};

namespace {

using application::ExtensionStoreError;
using application::ExtensionStoreResult;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

class SystemExtensionClock final : public application::ExtensionClock {
public:
  std::int64_t NowMilliseconds() const noexcept override {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
  }
};

class RandomExtensionIdGenerator final
    : public application::ExtensionIdGenerator {
public:
  std::string NewId(std::string_view prefix) override {
    std::array<std::uint32_t, 4> words{};
    for (auto &word : words)
      word = engine_();
    return std::format("{}_{:08x}{:08x}{:08x}{:08x}", prefix, words[0],
                       words[1], words[2], words[3]);
  }

private:
  std::mt19937 engine_{std::random_device{}()};
};

[[nodiscard]] ExtensionStoreError
StoreError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

[[nodiscard]] application::TerminalProviderError
TerminalStoreError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

[[nodiscard]] huxerui::Task<ExtensionStoreResult<Database>>
Open(const std::shared_ptr<SqliteExtensionStoreState> &state) {
  if (state->database)
    co_return *state->database;
  auto opened = co_await Database::OpenAsync(
      state->database_file,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(StoreError(opened.Error()));
  auto schema = co_await opened->TransactionAsync([](Transaction &transaction) {
    return legacy_feature_schema::Ensure(transaction);
  });
  if (!schema)
    co_return std::unexpected(StoreError(schema.Error()));
  state->database = *opened;
  co_return *state->database;
}

template <class Value>
[[nodiscard]] Result<Value> Required(const RowView &row, std::size_t index) {
  return row.Get<Value>(index);
}

[[nodiscard]] Result<std::string> OptionalText(const RowView &row,
                                               std::size_t index) {
  auto value = row.Get<std::optional<std::string>>(index);
  if (!value)
    return value.Error();
  return value->value_or("");
}

[[nodiscard]] Result<domain::AgentExtension> DecodeAgent(const RowView &row) {
  auto id = Required<std::string>(row, 0);
  if (!id)
    return id.Error();
  auto enabled = Required<bool>(row, 1);
  if (!enabled)
    return enabled.Error();
  auto name = Required<std::string>(row, 2);
  if (!name)
    return name.Error();
  auto slug = Required<std::string>(row, 3);
  if (!slug)
    return slug.Error();
  auto prompt = Required<std::string>(row, 4);
  if (!prompt)
    return prompt.Error();
  auto trigger = OptionalText(row, 5);
  if (!trigger)
    return trigger.Error();
  auto tools = OptionalText(row, 6);
  if (!tools)
    return tools.Error();
  auto mcps = OptionalText(row, 7);
  if (!mcps)
    return mcps.Error();
  auto created_at = Required<std::int64_t>(row, 8);
  if (!created_at)
    return created_at.Error();
  auto updated_at = Required<std::int64_t>(row, 9);
  if (!updated_at)
    return updated_at.Error();
  return domain::AgentExtension{
      .id = std::move(*id),
      .enabled = *enabled,
      .name = std::move(*name),
      .slug = std::move(*slug),
      .prompt = std::move(*prompt),
      .trigger = std::move(*trigger),
      .tool_names = DecodeExtensionStringList(*tools),
      .mcp_ids = DecodeExtensionStringList(*mcps),
      .created_at = *created_at,
      .updated_at = *updated_at,
  };
}

[[nodiscard]] Result<domain::McpExtension> DecodeMcp(const RowView &row) {
  auto id = Required<std::string>(row, 0);
  if (!id)
    return id.Error();
  auto enabled = Required<bool>(row, 1);
  if (!enabled)
    return enabled.Error();
  auto name = Required<std::string>(row, 2);
  if (!name)
    return name.Error();
  auto url = Required<std::string>(row, 3);
  if (!url)
    return url.Error();
  auto headers = OptionalText(row, 4);
  if (!headers)
    return headers.Error();
  auto tools = OptionalText(row, 5);
  if (!tools)
    return tools.Error();
  auto created_at = Required<std::int64_t>(row, 6);
  if (!created_at)
    return created_at.Error();
  auto updated_at = Required<std::int64_t>(row, 7);
  if (!updated_at)
    return updated_at.Error();
  return domain::McpExtension{
      .id = std::move(*id),
      .enabled = *enabled,
      .name = std::move(*name),
      .url = std::move(*url),
      .request_headers = DecodeMcpRequestHeaders(*headers),
      .tools = DecodeMcpTools(*tools),
      .created_at = *created_at,
      .updated_at = *updated_at,
  };
}

constexpr std::string_view kAgentColumns =
    "id, enabled, name, slug, prompt, trigger, tool_names_json, mcp_ids_json, "
    "created_at, updated_at";
constexpr std::string_view kMcpColumns =
    "id, enabled, name, url, request_headers_json, tools_json, created_at, "
    "updated_at";
constexpr std::string_view kTerminalProviderColumns =
    "id, enabled, provider_type, name, package_name, service_class, "
    "created_at, updated_at";

[[nodiscard]] Result<domain::TerminalProviderConfig>
DecodeTerminalProvider(const RowView &row) {
  auto id = Required<std::string>(row, 0);
  if (!id)
    return id.Error();
  auto enabled = Required<bool>(row, 1);
  if (!enabled)
    return enabled.Error();
  auto provider_type = Required<std::string>(row, 2);
  if (!provider_type)
    return provider_type.Error();
  auto name = Required<std::string>(row, 3);
  if (!name)
    return name.Error();
  auto package_name = Required<std::string>(row, 4);
  if (!package_name)
    return package_name.Error();
  auto service_class = Required<std::string>(row, 5);
  if (!service_class)
    return service_class.Error();
  auto created_at = Required<std::int64_t>(row, 6);
  if (!created_at)
    return created_at.Error();
  auto updated_at = Required<std::int64_t>(row, 7);
  if (!updated_at)
    return updated_at.Error();
  return domain::TerminalProviderConfig{
      .id = std::move(*id),
      .enabled = *enabled,
      .provider_type = std::move(*provider_type),
      .name = std::move(*name),
      .package_name = std::move(*package_name),
      .service_class = std::move(*service_class),
      .created_at = *created_at,
      .updated_at = *updated_at,
  };
}

} // namespace

SqliteExtensionStore::SqliteExtensionStore(huxerui::File database_file)
    : SqliteExtensionStore(std::move(database_file),
                           std::make_shared<SystemExtensionClock>(),
                           std::make_shared<RandomExtensionIdGenerator>()) {}

SqliteExtensionStore::SqliteExtensionStore(
    huxerui::File database_file,
    std::shared_ptr<application::ExtensionClock> clock,
    std::shared_ptr<application::ExtensionIdGenerator> id_generator)
    : state_(std::make_shared<SqliteExtensionStoreState>(
          std::move(database_file))),
      clock_(std::move(clock)), id_generator_(std::move(id_generator)) {
  if (!clock_ || !id_generator_)
    throw std::invalid_argument("extension store services must not be empty");
}

huxerui::Task<ExtensionStoreResult<std::vector<domain::AgentExtension>>>
SqliteExtensionStore::ListAgents() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::AgentExtension>(
      "SELECT " + std::string{kAgentColumns} +
          " FROM extension_agents ORDER BY updated_at DESC",
      DecodeAgent);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<ExtensionStoreResult<std::optional<domain::AgentExtension>>>
SqliteExtensionStore::FindAgent(std::string id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::AgentExtension>(
      "SELECT " + std::string{kAgentColumns} +
          " FROM extension_agents WHERE id = ? LIMIT 1",
      DecodeAgent, id);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::optional<domain::AgentExtension>{};
  co_return std::optional<domain::AgentExtension>{std::move(rows->front())};
}

huxerui::Task<ExtensionStoreResult<domain::AgentExtension>>
SqliteExtensionStore::SaveAgent(domain::AgentExtension value) {
  value = domain::NormalizeAgentExtension(std::move(value));
  const auto now = clock_->NowMilliseconds();
  if (value.id.empty())
    value.id = id_generator_->NewId("agent");
  if (value.slug.empty())
    value.slug = "agent-" + std::to_string(now);
  if (value.created_at <= 0)
    value.created_at = now;
  value.updated_at = now;

  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto saved = co_await database->ExecuteAsync(
      "INSERT INTO extension_agents "
      "(id, enabled, name, slug, prompt, trigger, tool_names_json, "
      "mcp_ids_json, created_at, updated_at, raw_json) "
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '') "
      "ON CONFLICT(id) DO UPDATE SET enabled = excluded.enabled, "
      "name = excluded.name, slug = excluded.slug, prompt = excluded.prompt, "
      "trigger = excluded.trigger, tool_names_json = excluded.tool_names_json, "
      "mcp_ids_json = excluded.mcp_ids_json, created_at = excluded.created_at, "
      "updated_at = excluded.updated_at, raw_json = excluded.raw_json",
      value.id, value.enabled, value.name, value.slug, value.prompt,
      value.trigger, EncodeExtensionStringList(value.tool_names),
      EncodeExtensionStringList(value.mcp_ids), value.created_at,
      value.updated_at);
  if (!saved)
    co_return std::unexpected(StoreError(saved.Error()));
  co_return value;
}

huxerui::Task<ExtensionStoreResult<void>>
SqliteExtensionStore::SetAgentEnabled(std::string id, bool enabled) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto updated = co_await database->ExecuteAsync(
      "UPDATE extension_agents SET enabled = ?, updated_at = ? WHERE id = ?",
      enabled, clock_->NowMilliseconds(), id);
  if (!updated)
    co_return std::unexpected(StoreError(updated.Error()));
  co_return ExtensionStoreResult<void>{};
}

huxerui::Task<ExtensionStoreResult<void>>
SqliteExtensionStore::DeleteAgents(std::vector<std::string> ids) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto deleted = co_await database->TransactionAsync(
      [ids = std::move(ids)](Transaction &transaction) -> Result<void> {
        for (const auto &id : ids) {
          if (id.empty())
            continue;
          auto row = transaction.Execute(
              "DELETE FROM extension_agents WHERE id = ?", id);
          if (!row)
            return row.Error();
        }
        return {};
      });
  if (!deleted)
    co_return std::unexpected(StoreError(deleted.Error()));
  co_return ExtensionStoreResult<void>{};
}

huxerui::Task<ExtensionStoreResult<std::vector<domain::McpExtension>>>
SqliteExtensionStore::ListMcps() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::McpExtension>(
      "SELECT " + std::string{kMcpColumns} +
          " FROM extension_mcps ORDER BY updated_at DESC",
      DecodeMcp);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<ExtensionStoreResult<std::optional<domain::McpExtension>>>
SqliteExtensionStore::FindMcp(std::string id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::McpExtension>(
      "SELECT " + std::string{kMcpColumns} +
          " FROM extension_mcps WHERE id = ? LIMIT 1",
      DecodeMcp, id);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::optional<domain::McpExtension>{};
  co_return std::optional<domain::McpExtension>{std::move(rows->front())};
}

huxerui::Task<ExtensionStoreResult<domain::McpExtension>>
SqliteExtensionStore::SaveMcp(domain::McpExtension value) {
  value = domain::NormalizeMcpExtension(std::move(value));
  if (!domain::IsHttpMcpUrl(value.url)) {
    co_return std::unexpected(ExtensionStoreError{
        .message = "MCP URL must start with http:// or https://"});
  }
  const auto now = clock_->NowMilliseconds();
  if (value.id.empty())
    value.id = id_generator_->NewId("mcp");
  if (value.created_at <= 0)
    value.created_at = now;
  value.updated_at = now;

  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto saved = co_await database->ExecuteAsync(
      "INSERT INTO extension_mcps "
      "(id, enabled, name, url, request_headers_json, tools_json, created_at, "
      "updated_at, raw_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '') "
      "ON CONFLICT(id) DO UPDATE SET enabled = excluded.enabled, "
      "name = excluded.name, url = excluded.url, "
      "request_headers_json = excluded.request_headers_json, "
      "tools_json = excluded.tools_json, created_at = excluded.created_at, "
      "updated_at = excluded.updated_at, raw_json = excluded.raw_json",
      value.id, value.enabled, value.name, value.url,
      EncodeMcpRequestHeaders(value.request_headers),
      EncodeMcpTools(value.tools), value.created_at, value.updated_at);
  if (!saved)
    co_return std::unexpected(StoreError(saved.Error()));
  co_return value;
}

huxerui::Task<ExtensionStoreResult<void>>
SqliteExtensionStore::SetMcpEnabled(std::string id, bool enabled) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto updated = co_await database->ExecuteAsync(
      "UPDATE extension_mcps SET enabled = ?, updated_at = ? WHERE id = ?",
      enabled, clock_->NowMilliseconds(), id);
  if (!updated)
    co_return std::unexpected(StoreError(updated.Error()));
  co_return ExtensionStoreResult<void>{};
}

huxerui::Task<ExtensionStoreResult<void>>
SqliteExtensionStore::DeleteMcps(std::vector<std::string> ids) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto deleted = co_await database->TransactionAsync(
      [ids = std::move(ids)](Transaction &transaction) -> Result<void> {
        for (const auto &id : ids) {
          if (id.empty())
            continue;
          auto row = transaction.Execute(
              "DELETE FROM extension_mcps WHERE id = ?", id);
          if (!row)
            return row.Error();
        }
        return {};
      });
  if (!deleted)
    co_return std::unexpected(StoreError(deleted.Error()));
  co_return ExtensionStoreResult<void>{};
}

huxerui::Task<application::TerminalProviderResult<
    std::vector<domain::TerminalProviderConfig>>>
SqliteExtensionStore::ListTerminalProviders() {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(application::TerminalProviderError{
        .message = database.error().message});
  auto rows = co_await database->QueryAsync<domain::TerminalProviderConfig>(
      "SELECT " + std::string{kTerminalProviderColumns} +
          " FROM ipc_providers WHERE provider_type = ? "
          "ORDER BY updated_at DESC",
      DecodeTerminalProvider, std::string{domain::kTerminalProviderType});
  if (!rows)
    co_return std::unexpected(TerminalStoreError(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<
    application::TerminalProviderResult<domain::TerminalProviderConfig>>
SqliteExtensionStore::SaveTerminalProvider(
    domain::TerminalProviderConfig value) {
  const auto now = clock_->NowMilliseconds();
  if (value.id.empty())
    value.id = id_generator_->NewId("ipc");
  value.provider_type = domain::kTerminalProviderType;
  if (value.created_at <= 0)
    value.created_at = now;
  value.updated_at = now;
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(application::TerminalProviderError{
        .message = database.error().message});
  auto saved = co_await database->ExecuteAsync(
      "INSERT INTO ipc_providers "
      "(id, enabled, provider_type, name, package_name, service_class, "
      "created_at, updated_at, raw_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '') "
      "ON CONFLICT(id) DO UPDATE SET enabled = excluded.enabled, "
      "provider_type = excluded.provider_type, name = excluded.name, "
      "package_name = excluded.package_name, "
      "service_class = excluded.service_class, "
      "created_at = excluded.created_at, updated_at = excluded.updated_at, "
      "raw_json = excluded.raw_json",
      value.id, value.enabled, value.provider_type, value.name,
      value.package_name, value.service_class, value.created_at,
      value.updated_at);
  if (!saved)
    co_return std::unexpected(TerminalStoreError(saved.Error()));
  co_return value;
}

huxerui::Task<application::TerminalProviderResult<void>>
SqliteExtensionStore::SetTerminalProviderEnabled(std::string id,
                                                 bool enabled) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(application::TerminalProviderError{
        .message = database.error().message});
  auto updated = co_await database->ExecuteAsync(
      "UPDATE ipc_providers SET enabled = ?, updated_at = ? WHERE id = ?",
      enabled, clock_->NowMilliseconds(), id);
  if (!updated)
    co_return std::unexpected(TerminalStoreError(updated.Error()));
  co_return application::TerminalProviderResult<void>{};
}

huxerui::Task<application::TerminalProviderResult<void>>
SqliteExtensionStore::DeleteTerminalProvider(std::string id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(application::TerminalProviderError{
        .message = database.error().message});
  auto deleted = co_await database->ExecuteAsync(
      "DELETE FROM ipc_providers WHERE id = ?", id);
  if (!deleted)
    co_return std::unexpected(TerminalStoreError(deleted.Error()));
  co_return application::TerminalProviderResult<void>{};
}

} // namespace linecode::infrastructure
