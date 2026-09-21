#include "infrastructure/sqlite_archive_database.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <map>
#include <optional>
#include <set>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <variant>
#include <vector>

#include <huxerui/sqlite.h>

#include "infrastructure/archive_json.h"
#include "infrastructure/archive_redaction.h"
#include "infrastructure/archive_validation.h"
#include "infrastructure/legacy_conversation_schema.h"
#include "infrastructure/legacy_feature_schema.h"
#include "infrastructure/legacy_project_schema.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
using application::ArchiveDatabaseExport;
using application::DataArchiveError;
using application::DataArchiveResult;
using huxerui::Bytes;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;
using SqlValue = huxerui::sqlite::Value;

constexpr std::size_t kMessageTextChunkBytes = 64U * 1024U;
constexpr std::array<std::string_view, 18> kTables{
    "settings",          "projects",          "model_configs",
    "conversations",     "messages",          "message_text_chunks",
    "message_blocks",    "tool_calls",        "tool_results",
    "attachments",       "diff_records",      "memories",
    "working_memory",    "conversation_index", "skills",
    "skill_usage",       "extension_agents",  "extension_mcps",
};

struct TableData final {
  std::vector<std::string> columns;
  std::vector<json::Object> rows;
};

struct PreparedTable final {
  std::string name;
  std::vector<std::string> columns;
  std::vector<std::vector<SqlValue>> rows;
};

std::size_t Utf8ChunkEnd(std::string_view text, std::size_t start) {
  std::size_t end = std::min(text.size(), start + kMessageTextChunkBytes);
  if (end == text.size())
    return end;
  while (end > start &&
         (static_cast<unsigned char>(text[end]) & 0xC0U) == 0x80U) {
    --end;
  }
  return end == start ? std::min(text.size(), start + kMessageTextChunkBytes)
                      : end;
}

DataArchiveError DatabaseError(const huxerui::sqlite::Error &error) {
  return {error.Message()};
}

std::string Base64Encode(std::span<const std::byte> bytes) {
  constexpr std::string_view alphabet =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  std::string result;
  result.reserve(((bytes.size() + 2U) / 3U) * 4U);
  for (std::size_t index = 0; index < bytes.size(); index += 3) {
    const auto first = std::to_integer<unsigned>(bytes[index]);
    const auto second = index + 1 < bytes.size()
                            ? std::to_integer<unsigned>(bytes[index + 1])
                            : 0U;
    const auto third = index + 2 < bytes.size()
                           ? std::to_integer<unsigned>(bytes[index + 2])
                           : 0U;
    const unsigned combined = (first << 16U) | (second << 8U) | third;
    result.push_back(alphabet[(combined >> 18U) & 63U]);
    result.push_back(alphabet[(combined >> 12U) & 63U]);
    result.push_back(index + 1 < bytes.size()
                         ? alphabet[(combined >> 6U) & 63U]
                         : '=');
    result.push_back(index + 2 < bytes.size() ? alphabet[combined & 63U]
                                               : '=');
  }
  return result;
}

std::optional<unsigned> Base64Digit(char value) {
  if (value >= 'A' && value <= 'Z')
    return static_cast<unsigned>(value - 'A');
  if (value >= 'a' && value <= 'z')
    return static_cast<unsigned>(value - 'a' + 26);
  if (value >= '0' && value <= '9')
    return static_cast<unsigned>(value - '0' + 52);
  if (value == '+')
    return 62U;
  if (value == '/')
    return 63U;
  return std::nullopt;
}

std::expected<Bytes, DataArchiveError> Base64Decode(std::string_view text) {
  if (text.size() % 4U != 0U)
    return std::unexpected(DataArchiveError{"invalid base64 database cell"});
  Bytes output;
  output.reserve((text.size() / 4U) * 3U);
  for (std::size_t index = 0; index < text.size(); index += 4) {
    const auto a = Base64Digit(text[index]);
    const auto b = Base64Digit(text[index + 1]);
    const auto c = text[index + 2] == '=' ? std::optional<unsigned>{0U}
                                          : Base64Digit(text[index + 2]);
    const auto d = text[index + 3] == '=' ? std::optional<unsigned>{0U}
                                          : Base64Digit(text[index + 3]);
    const bool final = index + 4U == text.size();
    if (!a || !b || !c || !d ||
        ((text[index + 2] == '=' || text[index + 3] == '=') && !final) ||
        (text[index + 2] == '=' && text[index + 3] != '=')) {
      return std::unexpected(DataArchiveError{"invalid base64 database cell"});
    }
    const unsigned combined = (*a << 18U) | (*b << 12U) | (*c << 6U) | *d;
    output.push_back(static_cast<std::byte>((combined >> 16U) & 0xFFU));
    if (text[index + 2] != '=')
      output.push_back(static_cast<std::byte>((combined >> 8U) & 0xFFU));
    if (text[index + 3] != '=')
      output.push_back(static_cast<std::byte>(combined & 0xFFU));
  }
  return output;
}

json::Value EncodeCell(const SqlValue &value) {
  json::Object cell;
  std::visit(
      [&](const auto &stored) {
        using T = std::decay_t<decltype(stored)>;
        if constexpr (std::same_as<T, huxerui::sqlite::Null>) {
          cell.emplace("type", "null");
        } else if constexpr (std::same_as<T, std::int64_t>) {
          cell.emplace("type", "integer");
          cell.emplace("value", stored);
        } else if constexpr (std::same_as<T, double>) {
          cell.emplace("type", "float");
          cell.emplace("value", stored);
        } else if constexpr (std::same_as<T, bool>) {
          cell.emplace("type", "integer");
          cell.emplace("value", std::int64_t{stored ? 1 : 0});
        } else if constexpr (std::same_as<T, std::string>) {
          cell.emplace("type", "string");
          cell.emplace("value", stored);
        } else if constexpr (std::same_as<T, Bytes>) {
          cell.emplace("type", "blob");
          cell.emplace("value", Base64Encode(stored));
        }
      },
      value);
  return cell;
}

std::optional<std::string> RowString(const json::Object &row,
                                     std::string_view column) {
  const auto *cell_value = json::Find(row, column);
  const auto *cell = json::AsObject(cell_value);
  if (!cell)
    return std::nullopt;
  const auto *value = json::AsString(json::Find(*cell, "value"));
  return value ? std::optional<std::string>{*value} : std::nullopt;
}

void SetStringCell(json::Object &row, std::string_view column,
                   std::string value) {
  auto found = row.find(column);
  if (found == row.end())
    return;
  auto *cell = std::get_if<json::Object>(&found->second);
  if (!cell)
    return;
  const auto *type = json::AsString(json::Find(*cell, "type"));
  if (type && *type == "string")
    (*cell)["value"] = std::move(value);
}

void RedactMessageRow(json::Object &row) {
  SetStringCell(row, "content", {});
  SetStringCell(row, "reasoning_content", {});
  SetStringCell(row, "raw_json", {});
}

void RedactMessageTextChunkRow(json::Object &row) {
  const auto field = RowString(row, "field_name").value_or("");
  if (field == "raw_json")
    SetStringCell(row, "content", {});
}

void RedactModelRow(json::Object &row) {
  SetStringCell(row, "api_key", {});
  SetStringCell(
      row, "raw_json",
      RedactArchiveJsonSecrets(RowString(row, "raw_json").value_or("")));
}

void RedactSettingRow(json::Object &row) {
  const auto key = RowString(row, "key").value_or("");
  SetStringCell(
      row, "value",
      RedactArchiveSettingValue(key, RowString(row, "value").value_or("")));
}

void RedactMcpRow(json::Object &row) {
  SetStringCell(
      row, "request_headers_json",
      RedactArchiveHeaders(RowString(row, "request_headers_json").value_or("")));
  SetStringCell(
      row, "raw_json",
      RedactArchiveJsonSecrets(RowString(row, "raw_json").value_or("")));
}

using RowRedactor = void (*)(json::Object &);

struct TableRedactionRule final {
  std::string_view table;
  RowRedactor redact;
};

constexpr std::array kTableRedactionRules{
    TableRedactionRule{"messages", RedactMessageRow},
    TableRedactionRule{"message_text_chunks", RedactMessageTextChunkRow},
    TableRedactionRule{"model_configs", RedactModelRow},
    TableRedactionRule{"settings", RedactSettingRow},
    TableRedactionRule{"extension_mcps", RedactMcpRow},
};

void RedactRow(std::string_view table, json::Object &row) {
  const auto rule = std::ranges::find(kTableRedactionRules, table,
                                      &TableRedactionRule::table);
  if (rule != kTableRedactionRules.end()) {
    rule->redact(row);
  }
}

void AppendLegacyMessageText(TableData &chunks,
                             const std::vector<TableData> &messages) {
  std::set<std::pair<std::string, std::string>> existing;
  for (const auto &row : chunks.rows) {
    const auto message_id = RowString(row, "message_id");
    const auto field_name = RowString(row, "field_name");
    if (message_id && field_name)
      existing.emplace(*message_id, *field_name);
  }

  // raw_json can contain provider response payloads, cookies, tokens, or
  // encrypted reasoning continuations.  Attachments have their own typed
  // table, so compatibility export must never recreate raw_json after the
  // table redaction pass above.
  constexpr std::array<std::string_view, 2> fields{"content",
                                                    "reasoning_content"};
  for (const auto &message : messages) {
    if (message.rows.empty())
      continue;
    const auto &row = message.rows.front();
    const auto message_id = RowString(row, "id");
    if (!message_id)
      continue;
    for (const auto field : fields) {
      const auto content = RowString(row, field);
      if (!content || content->empty() || existing.contains({*message_id, std::string{field}}))
        continue;
      std::int64_t order{};
      for (std::size_t start = 0; start < content->size();) {
        const auto end = Utf8ChunkEnd(*content, start);
        chunks.rows.push_back(json::Object{
            {"id", EncodeCell(SqlValue{huxerui::sqlite::Null{}})},
            {"message_id", EncodeCell(SqlValue{*message_id})},
            {"field_name", EncodeCell(SqlValue{std::string{field}})},
            {"chunk_order", EncodeCell(SqlValue{order++})},
            {"content", EncodeCell(SqlValue{content->substr(start, end - start)})},
        });
        start = end;
      }
      existing.emplace(*message_id, field);
    }
  }
}

Result<TableData> DecodeTableRows(const RowView &row) {
  TableData output;
  output.columns.reserve(row.ColumnCount());
  json::Object encoded;
  for (std::size_t index = 0; index < row.ColumnCount(); ++index) {
    output.columns.emplace_back(row.ColumnName(index));
    encoded.emplace(output.columns.back(), EncodeCell(row.Value(index)));
  }
  output.rows.push_back(std::move(encoded));
  return output;
}

json::Value EncodeTable(const TableData &table) {
  json::Array columns;
  columns.reserve(table.columns.size());
  for (const auto &column : table.columns)
    columns.emplace_back(column);
  json::Array rows;
  rows.reserve(table.rows.size());
  for (const auto &row : table.rows)
    rows.emplace_back(row);
  return json::Object{{"columns", std::move(columns)}, {"rows", std::move(rows)}};
}

std::string QuoteIdentifier(std::string_view identifier) {
  std::string quoted{'"'};
  for (const char c : identifier) {
    if (c == '"')
      quoted.push_back('"');
    quoted.push_back(c);
  }
  quoted.push_back('"');
  return quoted;
}

Result<std::set<std::string, std::less<>>> ExistingTables(Transaction &tx) {
  auto names = tx.Query<std::string>(
      "SELECT name FROM sqlite_master WHERE type = 'table'",
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!names)
    return names.Error();
  return std::set<std::string, std::less<>>(names->begin(), names->end());
}

Result<std::vector<std::string>> ExistingColumns(Transaction &tx,
                                                 std::string_view table) {
  return tx.Query<std::string>(
      "PRAGMA table_info(" + QuoteIdentifier(table) + ")",
      [](const RowView &row) { return row.Get<std::string>(1); });
}

std::expected<SqlValue, DataArchiveError> DecodeCell(const json::Value &value) {
  const auto *cell = json::AsObject(&value);
  if (!cell)
    return std::unexpected(DataArchiveError{"database row cell is not an object"});
  const auto *type = json::AsString(json::Find(*cell, "type"));
  if (!type)
    return std::unexpected(DataArchiveError{"database row cell has no type"});
  const auto *stored = json::Find(*cell, "value");
  if (*type == "null")
    return SqlValue{huxerui::sqlite::Null{}};
  if (*type == "integer") {
    const auto *integer = stored ? std::get_if<std::int64_t>(stored) : nullptr;
    if (!integer)
      return std::unexpected(DataArchiveError{"invalid integer database cell"});
    return SqlValue{*integer};
  }
  if (*type == "float") {
    if (const auto *real = stored ? std::get_if<double>(stored) : nullptr)
      return SqlValue{*real};
    if (const auto *integer = stored ? std::get_if<std::int64_t>(stored) : nullptr)
      return SqlValue{static_cast<double>(*integer)};
    return std::unexpected(DataArchiveError{"invalid float database cell"});
  }
  const auto *string = json::AsString(stored);
  if (!string)
    return std::unexpected(DataArchiveError{"invalid text database cell"});
  if (*type == "blob") {
    auto bytes = Base64Decode(*string);
    if (!bytes)
      return std::unexpected(bytes.error());
    return SqlValue{std::move(*bytes)};
  }
  if (*type == "string")
    return SqlValue{*string};
  return std::unexpected(DataArchiveError{"unsupported database cell type"});
}

std::expected<std::vector<PreparedTable>, DataArchiveError>
PrepareImport(const json::Object &tables,
              const std::map<std::string, std::vector<std::string>, std::less<>>
                  &live_columns) {
  std::vector<PreparedTable> prepared;
  for (const auto table_name : kTables) {
    const auto table_json = tables.find(table_name);
    const auto live = live_columns.find(table_name);
    if (table_json == tables.end() || live == live_columns.end())
      continue;
    const auto *table = json::AsObject(&table_json->second);
    const auto *rows = table ? json::AsArray(json::Find(*table, "rows")) : nullptr;
    if (!rows)
      continue;
    PreparedTable output{.name = std::string{table_name},
                         .columns = {},
                         .rows = {}};
    for (const auto &row_value : *rows) {
      const auto *row = json::AsObject(&row_value);
      if (!row)
        return std::unexpected(DataArchiveError{"database row is not an object"});
      std::vector<std::pair<std::string, SqlValue>> cells;
      for (const auto &[column, cell] : *row) {
        if (std::ranges::find(live->second, column) == live->second.end())
          continue;
        auto decoded = DecodeCell(cell);
        if (!decoded)
          return std::unexpected(decoded.error());
        cells.emplace_back(column, std::move(*decoded));
      }
      if (cells.empty())
        continue;
      if (output.columns.empty()) {
        for (const auto &[column, value] : cells)
          output.columns.push_back(column);
      } else if (!std::ranges::equal(output.columns, cells,
                                     {}, std::identity{},
                                     &std::pair<std::string, SqlValue>::first)) {
        // SQLite accepts a different column set per row, but normalize rows to
        // the live table columns so one prepared statement shape stays valid.
        std::map<std::string, SqlValue, std::less<>> by_name;
        for (auto &[column, value] : cells)
          by_name.emplace(std::move(column), std::move(value));
        std::vector<SqlValue> normalized;
        normalized.reserve(output.columns.size());
        bool complete = true;
        for (const auto &column : output.columns) {
          auto found = by_name.find(column);
          if (found == by_name.end()) {
            complete = false;
            break;
          }
          normalized.push_back(std::move(found->second));
        }
        if (!complete)
          return std::unexpected(DataArchiveError{
              "database rows use inconsistent columns in table " + output.name});
        output.rows.push_back(std::move(normalized));
        continue;
      }
      std::vector<SqlValue> values;
      values.reserve(cells.size());
      for (auto &[column, value] : cells)
        values.push_back(std::move(value));
      output.rows.push_back(std::move(values));
    }
    prepared.push_back(std::move(output));
  }
  return prepared;
}

std::string InsertSql(const PreparedTable &table) {
  std::string sql = "INSERT OR REPLACE INTO " + QuoteIdentifier(table.name) + " (";
  for (std::size_t i = 0; i < table.columns.size(); ++i) {
    if (i)
      sql += ',';
    sql += QuoteIdentifier(table.columns[i]);
  }
  sql += ") VALUES (";
  for (std::size_t i = 0; i < table.columns.size(); ++i) {
    if (i)
      sql += ',';
    sql += '?';
  }
  sql += ')';
  return sql;
}

std::uint64_t CountRows(const std::vector<PreparedTable> &tables,
                        std::string_view name) {
  const auto found =
      std::ranges::find(tables, name, &PreparedTable::name);
  return found == tables.end() ? 0U : found->rows.size();
}

} // namespace

huxerui::Task<DataArchiveResult<ArchiveDatabaseExport>>
SqliteArchiveDatabase::ExportRedacted() {
  auto opened = co_await Database::OpenAsync(
      database_file_, huxerui::sqlite::OpenOptions{
                          // Lib-SQLite verifies PRAGMA journal_mode during
                          // open, which SQLite rejects on a read-only handle.
                          // ReadWrite still refuses a missing database and
                          // this export path issues no mutating statements.
                          .mode = huxerui::sqlite::OpenMode::ReadWrite});
  if (!opened)
    co_return std::unexpected(DatabaseError(opened.Error()));

  auto names = co_await opened->QueryAsync<std::string>(
      "SELECT name FROM sqlite_master WHERE type = 'table'",
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!names)
    co_return std::unexpected(DatabaseError(names.Error()));
  const std::set<std::string, std::less<>> existing(names->begin(), names->end());

  json::Object table_json;
  domain::ArchiveSummary summary;
  for (const auto table_name : kTables) {
    if (!existing.contains(table_name))
      continue;
    auto decoded = co_await opened->QueryAsync<TableData>(
        "SELECT * FROM " + QuoteIdentifier(table_name), DecodeTableRows);
    if (!decoded)
      co_return std::unexpected(DatabaseError(decoded.Error()));
    TableData table;
    if (!decoded->empty()) {
      table.columns = decoded->front().columns;
      table.rows.reserve(decoded->size());
      for (auto &row : *decoded) {
        auto encoded = std::move(row.rows.front());
        RedactRow(table_name, encoded);
        table.rows.push_back(std::move(encoded));
      }
    } else {
      auto columns = co_await opened->QueryAsync<std::string>(
          "PRAGMA table_info(" + QuoteIdentifier(table_name) + ")",
          [](const RowView &row) { return row.Get<std::string>(1); });
      if (!columns)
        co_return std::unexpected(DatabaseError(columns.Error()));
      table.columns = std::move(*columns);
    }
    if (table_name == "message_text_chunks" && existing.contains("messages")) {
      auto legacy_messages = co_await opened->QueryAsync<TableData>(
          "SELECT id, content, reasoning_content, raw_json FROM messages",
          DecodeTableRows);
      if (!legacy_messages)
        co_return std::unexpected(DatabaseError(legacy_messages.Error()));
      AppendLegacyMessageText(table, *legacy_messages);
    }
    if (table_name == "model_configs")
      summary.models = table.rows.size();
    else if (table_name == "conversations")
      summary.conversations = table.rows.size();
    else if (table_name == "settings")
      summary.settings = table.rows.size();
    table_json.emplace(std::string{table_name}, EncodeTable(table));
  }

  json::Object root{{"format", "linecode-database"},
                    {"schemaVersion", kCurrentArchiveDatabaseSchemaVersion},
                    {"tables", std::move(table_json)}};
  co_return ArchiveDatabaseExport{.json = json::Serialize(root),
                                  .summary = summary};
}

huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
SqliteArchiveDatabase::ReplaceFromSnapshot(std::string text) {
  auto validated =
      ValidateDatabaseSnapshot(text, kCurrentArchiveDatabaseSchemaVersion);
  if (!validated) {
    co_return std::unexpected(DataArchiveError{validated.error().message});
  }
  auto parsed = json::Parse(text);
  const auto *root = parsed ? json::AsObject(&*parsed) : nullptr;
  const auto *format = root ? json::AsString(json::Find(*root, "format")) : nullptr;
  if (!format || *format != "linecode-database")
    co_return std::unexpected(DataArchiveError{"invalid .linecode database snapshot"});
  const auto *version_value = json::Find(*root, "schemaVersion");
  const auto *version = version_value ? std::get_if<std::int64_t>(version_value)
                                      : nullptr;
  if (!version || *version < 0)
    co_return std::unexpected(DataArchiveError{"invalid database schemaVersion"});
  if (*version > kCurrentArchiveDatabaseSchemaVersion) {
    co_return std::unexpected(DataArchiveError{
        "archive was created by a newer LineCode database schema"});
  }
  const auto *tables = json::AsObject(json::Find(*root, "tables"));
  if (!tables)
    co_return std::unexpected(DataArchiveError{"database snapshot has no tables"});

  auto opened = co_await Database::OpenAsync(
      database_file_, huxerui::sqlite::OpenOptions{
                          .create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(DatabaseError(opened.Error()));

  std::map<std::string, std::vector<std::string>, std::less<>> live_columns;
  auto schema = co_await opened->TransactionAsync(
      [&](Transaction &transaction) -> Result<void> {
        auto ensured = legacy_feature_schema::Ensure(transaction);
        if (!ensured)
          return ensured.Error();
        auto projects = legacy_project_schema::Ensure(transaction);
        if (!projects)
          return projects.Error();
        auto existing = ExistingTables(transaction);
        if (!existing)
          return existing.Error();
        for (const auto table : kTables) {
          if (!existing->contains(table))
            continue;
          auto columns = ExistingColumns(transaction, table);
          if (!columns)
            return columns.Error();
          live_columns.emplace(std::string{table}, std::move(*columns));
        }
        return {};
      });
  if (!schema)
    co_return std::unexpected(DatabaseError(schema.Error()));

  auto prepared = PrepareImport(*tables, live_columns);
  if (!prepared)
    co_return std::unexpected(prepared.error());

  auto replaced = co_await opened->TransactionAsync(
      [&](Transaction &transaction) -> Result<void> {
        for (auto table = kTables.rbegin(); table != kTables.rend(); ++table) {
          if (!live_columns.contains(*table))
            continue;
          auto removed = transaction.Execute("DELETE FROM " + QuoteIdentifier(*table));
          if (!removed)
            return removed.Error();
        }
        for (const auto &table : *prepared) {
          if (table.columns.empty())
            continue;
          const auto sql = InsertSql(table);
          for (const auto &row : table.rows) {
            auto inserted = transaction.Execute(sql, row);
            if (!inserted)
              return inserted.Error();
          }
        }
        return {};
      });
  if (!replaced)
    co_return std::unexpected(DatabaseError(replaced.Error()));

  co_return domain::ArchiveSummary{
      .conversations = CountRows(*prepared, "conversations"),
      .models = CountRows(*prepared, "model_configs"),
      .settings = CountRows(*prepared, "settings"),
  };
}

huxerui::Task<
    application::DataArchiveResult<application::LegacyArchiveData>>
SqliteArchiveDatabase::ExportLegacy() {
  auto opened = co_await Database::OpenAsync(
      database_file_, huxerui::sqlite::OpenOptions{
                          .mode = huxerui::sqlite::OpenMode::ReadWrite});
  if (!opened)
    co_return std::unexpected(DatabaseError(opened.Error()));

  application::LegacyArchiveData output;

  // Models: the legacy shape is rebuilt from the columns instead of reusing
  // `raw_json`, so an export never carries a stale copy of an edited row.
  auto models = co_await opened->QueryAsync<application::LegacyArchiveModel>(
      "SELECT id, name, protocol_type, provider_label, base_url, api_key, "
      "model_id, tool_call_limit, compression_model_enabled, "
      "compression_model_auto, compression_model_id, context_size, selected "
      "FROM model_configs ORDER BY selected DESC, updated_at DESC",
      [](const RowView &row) -> Result<application::LegacyArchiveModel> {
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
        auto selected = row.Get<bool>(12);
        if (!selected)
          return selected.Error();

        domain::ModelConfig config{
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
        config.Normalize();
        application::LegacyArchiveModel model;
        model.raw_json = EncodeLegacyModelJson(config);
        model.selected = *selected;
        model.config = std::move(config);
        return model;
      });
  if (!models)
    co_return std::unexpected(DatabaseError(models.Error()));
  output.models = std::move(*models);
  for (const auto &model : output.models) {
    if (model.selected) {
      output.selected_model_id = model.config.id;
      break;
    }
  }

  auto conversations =
      co_await opened->QueryAsync<application::LegacyArchiveConversation>(
          "SELECT id, title, created_at, updated_at FROM conversations "
          "ORDER BY updated_at DESC",
          [](const RowView &row) -> Result<application::LegacyArchiveConversation> {
            auto id = row.Get<std::string>(0);
            if (!id)
              return id.Error();
            auto title = row.Get<std::string>(1);
            if (!title)
              return title.Error();
            auto created_at = row.Get<std::int64_t>(2);
            if (!created_at)
              return created_at.Error();
            auto updated_at = row.Get<std::int64_t>(3);
            if (!updated_at)
              return updated_at.Error();
            application::LegacyArchiveConversation conversation;
            conversation.id = std::move(*id);
            conversation.title = std::move(*title);
            conversation.created_at = *created_at;
            conversation.updated_at = *updated_at;
            return conversation;
          });
  if (!conversations)
    co_return std::unexpected(DatabaseError(conversations.Error()));
  output.conversations = std::move(*conversations);

  auto current = co_await opened->QueryAsync<std::string>(
      "SELECT id FROM conversations WHERE current = 1 LIMIT 1",
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!current)
    co_return std::unexpected(DatabaseError(current.Error()));
  if (!current->empty())
    output.current_conversation_id = current->front();

  // Message bodies live in chunk rows rather than in `messages.content`, so
  // they are reassembled per message and field before the per-conversation
  // reads below.
  auto chunks = co_await opened->QueryAsync<std::pair<std::string, std::string>>(
      "SELECT message_id, field_name, content FROM message_text_chunks "
      "ORDER BY message_id, field_name, chunk_order",
      [](const RowView &row) -> Result<std::pair<std::string, std::string>> {
        auto id = row.Get<std::string>(0);
        if (!id)
          return id.Error();
        auto field = row.Get<std::string>(1);
        if (!field)
          return field.Error();
        auto content = row.Get<std::string>(2);
        if (!content)
          return content.Error();
        return std::pair<std::string, std::string>{
            std::move(*id) + '\x1f' + std::move(*field), std::move(*content)};
      });
  if (!chunks)
    co_return std::unexpected(DatabaseError(chunks.Error()));
  std::map<std::string, std::string, std::less<>> text;
  for (auto &[key, value] : *chunks)
    text[key] += value;

  for (auto &conversation : output.conversations) {
    auto messages =
        co_await opened->QueryAsync<application::LegacyArchiveMessage>(
            "SELECT id, role, timestamp, streaming, hidden, "
            "exclude_from_context, tool_call_id, tool_name, is_error "
            "FROM messages WHERE conversation_id = ? ORDER BY local_order",
            [&text](const RowView &row) -> Result<application::LegacyArchiveMessage> {
              auto id = row.Get<std::string>(0);
              if (!id)
                return id.Error();
              auto role = row.Get<std::string>(1);
              if (!role)
                return role.Error();
              auto timestamp = row.Get<std::int64_t>(2);
              if (!timestamp)
                return timestamp.Error();
              auto streaming = row.Get<bool>(3);
              if (!streaming)
                return streaming.Error();
              auto hidden = row.Get<bool>(4);
              if (!hidden)
                return hidden.Error();
              auto excluded = row.Get<bool>(5);
              if (!excluded)
                return excluded.Error();
              auto tool_call_id = row.Get<std::optional<std::string>>(6);
              if (!tool_call_id)
                return tool_call_id.Error();
              auto tool_name = row.Get<std::optional<std::string>>(7);
              if (!tool_name)
                return tool_name.Error();
              auto is_error = row.Get<bool>(8);
              if (!is_error)
                return is_error.Error();

              application::LegacyArchiveMessage message;
              message.id = std::move(*id);
              message.role = std::move(*role);
              message.timestamp = *timestamp;
              message.streaming = *streaming;
              message.hidden = *hidden;
              message.exclude_from_context = *excluded;
              message.tool_call_id = tool_call_id->value_or("");
              message.tool_name = tool_name->value_or("");
              message.is_error = *is_error;
              const auto field = [&text, &message](std::string_view name) {
                const auto found =
                    text.find(message.id + '\x1f' + std::string{name});
                return found == text.end() ? std::string{} : found->second;
              };
              message.content = field("content");
              message.reasoning_content = field("reasoning_content");
              message.raw_json = field("raw_json");
              return message;
            },
            conversation.id);
    if (!messages)
      co_return std::unexpected(DatabaseError(messages.Error()));
    conversation.messages = std::move(*messages);
  }

  auto settings =
      co_await opened->QueryAsync<std::pair<std::string, std::string>>(
          "SELECT key, value FROM settings",
          [](const RowView &row) -> Result<std::pair<std::string, std::string>> {
            auto key = row.Get<std::string>(0);
            if (!key)
              return key.Error();
            auto value = row.Get<std::string>(1);
            if (!value)
              return value.Error();
            return std::pair<std::string, std::string>{std::move(*key),
                                                       std::move(*value)};
          });
  if (!settings)
    co_return std::unexpected(DatabaseError(settings.Error()));
  for (auto &[key, value] : *settings) {
    if (key == "@lineai_selected_model") {
      if (output.selected_model_id.empty())
        output.selected_model_id = value;
      continue;
    }
    output.settings.emplace(std::move(key), std::move(value));
  }

  co_return output;
}

huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
SqliteArchiveDatabase::ImportLegacy(application::LegacyArchiveData data,
                                    domain::ArchiveImportMode mode) {
  auto opened = co_await Database::OpenAsync(
      database_file_, huxerui::sqlite::OpenOptions{
                          .create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(DatabaseError(opened.Error()));

  const bool replace = mode == domain::ArchiveImportMode::replace;
  auto imported = co_await opened->TransactionAsync(
      [data = std::move(data), replace](Transaction &transaction)
          -> Result<domain::ArchiveSummary> {
        auto existing = ExistingTables(transaction);
        if (!existing)
          return existing.Error();
        constexpr std::array required{"settings", "model_configs",
                                      "conversations", "messages",
                                      "message_text_chunks"};
        if (!std::ranges::all_of(required, [&](std::string_view table) {
              return existing->contains(table);
            })) {
          return huxerui::sqlite::Error{
              huxerui::sqlite::ErrorCode::SchemaMismatch,
              "legacy archive target database schema is incomplete",
              "validate legacy archive target schema"};
        }

        if (replace) {
          if (existing->contains("conversation_index")) {
            auto removed = transaction.Execute("DELETE FROM conversation_index");
            if (!removed)
              return removed.Error();
          }
          auto conversations = transaction.Execute("DELETE FROM conversations");
          if (!conversations)
            return conversations.Error();
          auto models = transaction.Execute("DELETE FROM model_configs");
          if (!models)
            return models.Error();
          auto settings = transaction.Execute(
              "DELETE FROM settings WHERE key GLOB ? OR key GLOB ?",
              std::string{"@lineai_*"}, std::string{"@linecode_*"});
          if (!settings)
            return settings.Error();
        }

        const auto imported_at = std::chrono::duration_cast<std::chrono::milliseconds>(
                                     std::chrono::system_clock::now()
                                         .time_since_epoch())
                                     .count();
        for (std::size_t index = 0; index < data.models.size(); ++index) {
          const auto &model = data.models[index];
          auto selected = transaction.Query<std::string>(
              "SELECT id FROM model_configs WHERE selected = 1 "
              "ORDER BY updated_at DESC LIMIT 1",
              [](const RowView &row) { return row.Get<std::string>(0); });
          if (!selected)
            return selected.Error();
          if (selected->empty()) {
            selected = transaction.Query<std::string>(
                "SELECT id FROM model_configs "
                "ORDER BY selected DESC, updated_at DESC LIMIT 1",
                [](const RowView &row) { return row.Get<std::string>(0); });
            if (!selected)
              return selected.Error();
          }
          const std::int64_t selected_value =
              !selected->empty() && selected->front() == model.config.id ? 1 : 0;
          const std::int64_t timestamp =
              imported_at + static_cast<std::int64_t>(index);
          auto saved = transaction.Execute(
              "INSERT OR REPLACE INTO model_configs "
              "(id, name, protocol_type, provider_label, base_url, api_key, "
              "model_id, tool_call_limit, compression_model_enabled, "
              "compression_model_auto, compression_model_id, context_size, "
              "selected, raw_json, created_at, updated_at) "
              "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
              model.config.id, model.config.name,
              std::string{domain::ModelProtocolStorageName(model.config.protocol)},
              model.config.provider_label, model.config.base_url,
              model.config.api_key, model.config.model_id,
              static_cast<std::int64_t>(model.config.tool_call_limit),
              model.config.compression_model_enabled,
              model.config.compression_model_auto,
              model.config.compression_model_id,
              static_cast<std::int64_t>(model.config.context_size),
              selected_value, model.raw_json, timestamp, timestamp);
          if (!saved)
            return saved.Error();
        }
        if (!data.selected_model_id.empty()) {
          auto cleared = transaction.Execute(
              "UPDATE model_configs SET selected = 0");
          if (!cleared)
            return cleared.Error();
          auto selected = transaction.Execute(
              "UPDATE model_configs SET selected = 1, updated_at = ? WHERE id = ?",
              imported_at + static_cast<std::int64_t>(data.models.size()),
              data.selected_model_id);
          if (!selected)
            return selected.Error();
        }

        for (const auto &conversation : data.conversations) {
          auto saved_conversation = transaction.Execute(
              "INSERT OR REPLACE INTO conversations "
              "(id, title, project_id, created_at, updated_at, current, raw_json) "
              "VALUES (?, ?, ?, ?, ?, 0, ?)",
              conversation.id, conversation.title, std::string{},
              conversation.created_at, conversation.updated_at,
              conversation.raw_json);
          if (!saved_conversation)
            return saved_conversation.Error();
          auto removed = transaction.Execute(
              "DELETE FROM messages WHERE conversation_id = ?",
              conversation.id);
          if (!removed)
            return removed.Error();
          for (std::size_t order = 0; order < conversation.messages.size();
               ++order) {
            const auto &message = conversation.messages[order];
            auto saved_message = transaction.Execute(
                "INSERT OR REPLACE INTO messages "
                "(id, conversation_id, local_order, role, content, "
                "reasoning_content, timestamp, streaming, hidden, "
                "exclude_from_context, tool_call_id, tool_name, is_error, "
                "raw_json) VALUES (?, ?, ?, ?, '', '', ?, ?, ?, ?, ?, ?, ?, '')",
                message.id, conversation.id, static_cast<std::int64_t>(order),
                message.role, message.timestamp, message.streaming,
                message.hidden, message.exclude_from_context,
                message.tool_call_id, message.tool_name, message.is_error);
            if (!saved_message)
              return saved_message.Error();
            const std::array fields{
                std::pair<std::string_view, std::string_view>{"content",
                                                              message.content},
                std::pair<std::string_view, std::string_view>{
                    "reasoning_content", message.reasoning_content},
                std::pair<std::string_view, std::string_view>{"raw_json",
                                                              message.raw_json},
            };
            for (const auto &[field, content] : fields) {
              const auto chunks = legacy_schema::SplitMessageText(content);
              for (std::size_t chunk_order = 0; chunk_order < chunks.size();
                   ++chunk_order) {
                auto saved_chunk = transaction.Execute(
                    "INSERT INTO message_text_chunks "
                    "(message_id, field_name, chunk_order, content) "
                    "VALUES (?, ?, ?, ?)",
                    message.id, std::string{field},
                    static_cast<std::int64_t>(chunk_order),
                    std::string{chunks[chunk_order]});
                if (!saved_chunk)
                  return saved_chunk.Error();
              }
            }
          }
        }
        if (!data.current_conversation_id.empty()) {
          auto cleared =
              transaction.Execute("UPDATE conversations SET current = 0");
          if (!cleared)
            return cleared.Error();
          auto selected = transaction.Execute(
              "UPDATE conversations SET current = 1 WHERE id = ?",
              data.current_conversation_id);
          if (!selected)
            return selected.Error();
        }

        for (const auto &[key, value] : data.settings) {
          auto saved = transaction.Execute(
              "INSERT INTO settings (key, value, type, updated_at) "
              "VALUES (?, ?, 'string', ?) "
              "ON CONFLICT(key) DO UPDATE SET value = excluded.value, "
              "type = excluded.type, updated_at = excluded.updated_at",
              key, value, imported_at);
          if (!saved)
            return saved.Error();
        }

        const auto count = [&](std::string sql) -> Result<std::uint64_t> {
          auto rows = transaction.Query<std::int64_t>(
              std::move(sql), [](const RowView &row) {
                return row.Get<std::int64_t>(0);
              });
          if (!rows)
            return rows.Error();
          return rows->empty() ? 0U : static_cast<std::uint64_t>(rows->front());
        };
        auto conversations = count("SELECT COUNT(*) FROM conversations");
        if (!conversations)
          return conversations.Error();
        auto models = count("SELECT COUNT(*) FROM model_configs");
        if (!models)
          return models.Error();
        auto settings = count(
            "SELECT COUNT(*) FROM settings WHERE key GLOB '@lineai_*' OR "
            "key GLOB '@linecode_*'");
        if (!settings)
          return settings.Error();
        return domain::ArchiveSummary{.conversations = *conversations,
                                      .models = *models,
                                      .settings = *settings};
      });
  if (!imported)
    co_return std::unexpected(DatabaseError(imported.Error()));
  co_return *imported;
}

} // namespace linecode::infrastructure
