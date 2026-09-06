#include "infrastructure/sqlite_model_store.h"

#include <chrono>
#include <string>
#include <utility>

#include "infrastructure/legacy_model_schema.h"

namespace linecode::infrastructure {
namespace {

using application::ModelStoreError;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

ModelStoreError StoreError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

std::string NewModelId() {
  const auto value = std::chrono::duration_cast<std::chrono::microseconds>(
                         std::chrono::system_clock::now().time_since_epoch())
                         .count();
  return std::to_string(value);
}

Result<domain::ModelConfig> DecodeModel(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id)
    return id.Error();
  auto name = row.Get<std::string>(1);
  if (!name)
    return name.Error();
  auto protocol = row.Get<std::string>(2);
  if (!protocol)
    return protocol.Error();
  auto provider = row.Get<std::string>(3);
  if (!provider)
    return provider.Error();
  auto base_url = row.Get<std::optional<std::string>>(4);
  if (!base_url)
    return base_url.Error();
  auto api_key = row.Get<std::optional<std::string>>(5);
  if (!api_key)
    return api_key.Error();
  auto model_id = row.Get<std::string>(6);
  if (!model_id)
    return model_id.Error();
  auto tool_limit = row.Get<std::int64_t>(7);
  if (!tool_limit)
    return tool_limit.Error();
  auto compression_enabled = row.Get<bool>(8);
  if (!compression_enabled)
    return compression_enabled.Error();
  auto compression_auto = row.Get<bool>(9);
  if (!compression_auto)
    return compression_auto.Error();
  auto compression_id = row.Get<std::optional<std::string>>(10);
  if (!compression_id)
    return compression_id.Error();
  auto context_size = row.Get<std::int64_t>(11);
  if (!context_size)
    return context_size.Error();

  domain::ModelConfig model{
      .id = std::move(*id),
      .name = std::move(*name),
      .protocol = domain::ParseModelProtocol(*protocol),
      .provider_label = std::move(*provider),
      .base_url = base_url->value_or(""),
      .api_key = api_key->value_or(""),
      .model_id = std::move(*model_id),
      .tool_call_limit = static_cast<int>(*tool_limit),
      .compression_model_enabled = *compression_enabled,
      .compression_model_auto = *compression_auto,
      .compression_model_id = compression_id->value_or(""),
      .context_size = static_cast<int>(*context_size),
  };
  model.Normalize();
  return model;
}

constexpr std::string_view kSelectColumns =
    "id, name, protocol_type, provider_label, base_url, api_key, model_id, "
    "tool_call_limit, compression_model_enabled, compression_model_auto, "
    "compression_model_id, context_size";

} // namespace

SqliteModelStore::SqliteModelStore(huxerui::File database_file)
    : database_file_(std::move(database_file)) {}

huxerui::Task<std::expected<Database, ModelStoreError>>
SqliteModelStore::Open() {
  if (database_) {
    co_return *database_;
  }
  auto opened = co_await Database::OpenAsync(
      database_file_,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened) {
    co_return std::unexpected(StoreError(opened.Error()));
  }
  auto schema = co_await opened->ExecuteAsync(
      std::string{legacy_model_schema::create_table});
  if (!schema) {
    co_return std::unexpected(StoreError(schema.Error()));
  }
  database_ = *opened;
  co_return *database_;
}

huxerui::Task<std::expected<std::vector<domain::ModelConfig>, ModelStoreError>>
SqliteModelStore::List() {
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::ModelConfig>(
      "SELECT " + std::string{kSelectColumns} +
          " FROM model_configs ORDER BY selected DESC, updated_at DESC",
      DecodeModel);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<
    std::expected<std::optional<domain::ModelConfig>, ModelStoreError>>
SqliteModelStore::Find(std::string id) {
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::ModelConfig>(
      "SELECT " + std::string{kSelectColumns} +
          " FROM model_configs WHERE id = ? LIMIT 1",
      DecodeModel, id);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::optional<domain::ModelConfig>{};
  co_return std::optional<domain::ModelConfig>{std::move(rows->front())};
}

huxerui::Task<std::expected<domain::ModelConfig, ModelStoreError>>
SqliteModelStore::Save(domain::ModelConfig model) {
  model.Normalize();
  if (model.id.empty())
    model.id = NewModelId();
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  const auto timestamp = NowMilliseconds();
  auto saved = co_await database->TransactionAsync([model, timestamp](
                                                       Transaction &transaction)
                                                       -> Result<void> {
    auto selection = transaction.Query<std::string>(
        std::string{legacy_model_schema::selected_id},
        [](const RowView &row) { return row.Get<std::string>(0); });
    if (!selection)
      return selection.Error();
    if (selection->empty()) {
      selection = transaction.Query<std::string>(
          std::string{legacy_model_schema::fallback_model_id},
          [](const RowView &row) { return row.Get<std::string>(0); });
      if (!selection)
        return selection.Error();
    }
    const bool selected =
        !selection->empty() && selection->front() == model.id;
    auto inserted = transaction.Execute(
        "INSERT INTO model_configs "
        "(id, name, protocol_type, provider_label, base_url, api_key, "
        "model_id, "
        "tool_call_limit, compression_model_enabled, compression_model_auto, "
        "compression_model_id, context_size, selected, raw_json, created_at, "
        "updated_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?) "
        "ON CONFLICT(id) DO UPDATE SET "
        "name = excluded.name, protocol_type = excluded.protocol_type, "
        "provider_label = excluded.provider_label, base_url = "
        "excluded.base_url, "
        "api_key = excluded.api_key, model_id = excluded.model_id, "
        "tool_call_limit = excluded.tool_call_limit, "
        "compression_model_enabled = excluded.compression_model_enabled, "
        "compression_model_auto = excluded.compression_model_auto, "
        "compression_model_id = excluded.compression_model_id, "
        "context_size = excluded.context_size, selected = excluded.selected, "
        "updated_at = excluded.updated_at",
        model.id, model.name,
        std::string{domain::ModelProtocolStorageName(model.protocol)},
        model.provider_label, model.base_url, model.api_key, model.model_id,
        static_cast<std::int64_t>(model.tool_call_limit),
        model.compression_model_enabled, model.compression_model_auto,
        model.compression_model_id,
        static_cast<std::int64_t>(model.context_size), selected, timestamp,
        timestamp);
    if (!inserted)
      return inserted.Error();
    return {};
  });
  if (!saved)
    co_return std::unexpected(StoreError(saved.Error()));
  co_return model;
}

huxerui::Task<std::expected<void, ModelStoreError>>
SqliteModelStore::Delete(std::vector<std::string> ids) {
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  auto deleted = co_await database->TransactionAsync(
      [ids = std::move(ids)](Transaction &transaction) -> Result<void> {
        for (const auto &id : ids) {
          if (id.empty())
            continue;
          auto row =
              transaction.Execute("DELETE FROM model_configs WHERE id = ?", id);
          if (!row)
            return row.Error();
        }
        return {};
      });
  if (!deleted)
    co_return std::unexpected(StoreError(deleted.Error()));
  co_return std::expected<void, ModelStoreError>{};
}

huxerui::Task<std::expected<void, ModelStoreError>>
SqliteModelStore::Select(std::string id) {
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  const auto timestamp = NowMilliseconds();
  auto selected = co_await database->TransactionAsync([id = std::move(id),
                                                       timestamp](
                                                          Transaction
                                                              &transaction)
                                                          -> Result<void> {
    auto cleared = transaction.Execute(
        std::string{legacy_model_schema::clear_selection});
    if (!cleared)
      return cleared.Error();
    if (!id.empty()) {
      auto marked = transaction.Execute(
          std::string{legacy_model_schema::select_id},
          timestamp, id);
      if (!marked)
        return marked.Error();
    }
    return {};
  });
  if (!selected)
    co_return std::unexpected(StoreError(selected.Error()));
  co_return std::expected<void, ModelStoreError>{};
}

huxerui::Task<std::expected<std::string, ModelStoreError>>
SqliteModelStore::SelectedId() {
  auto database = co_await Open();
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<std::string>(
      std::string{legacy_model_schema::selected_id},
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (!rows->empty())
    co_return std::move(rows->front());
  rows = co_await database->QueryAsync<std::string>(
      std::string{legacy_model_schema::fallback_model_id},
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return rows->empty() ? std::string{} : std::move(rows->front());
}

} // namespace linecode::infrastructure
