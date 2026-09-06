#include "infrastructure/sqlite_archive_database.h"

#include <algorithm>
#include <array>
#include <cctype>
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

constexpr int kDatabaseSchemaVersion = 4;
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

DataArchiveError DatabaseError(const huxerui::sqlite::Error &error) {
  return {error.Message()};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  for (const unsigned char c : value) {
    lowered.push_back(static_cast<char>(std::tolower(c)));
  }
  return lowered;
}

bool IsSensitiveName(std::string_view value) {
  constexpr std::array keywords{
      "apikey",      "api_key",     "api-key",    "authorization",
      "password",    "passwd",      "passphrase", "privatekey",
      "private_key", "private-key", "secret",     "token",
      "cookie",
  };
  const auto lowered = Lower(value);
  return std::ranges::any_of(
      keywords, [&](std::string_view keyword) { return lowered.contains(keyword); });
}

void RedactRecursive(json::Value &value) {
  if (auto *object = std::get_if<json::Object>(&value)) {
    for (auto &[key, child] : *object) {
      if (IsSensitiveName(key)) {
        child = std::string{};
      } else {
        RedactRecursive(child);
      }
    }
  } else if (auto *array = std::get_if<json::Array>(&value)) {
    for (auto &child : *array) {
      RedactRecursive(child);
    }
  }
}

std::string RedactObjectFields(std::string_view raw,
                               std::span<const std::string_view> fields) {
  if (raw.empty())
    return {};
  auto parsed = json::Parse(raw);
  if (!parsed)
    return {};
  auto *object = std::get_if<json::Object>(&*parsed);
  if (!object)
    return {};
  for (const auto field : fields) {
    if (auto found = object->find(field); found != object->end())
      found->second = std::string{};
  }
  return json::Serialize(*parsed);
}

std::string RedactRecursiveJson(std::string_view raw) {
  if (raw.empty())
    return {};
  auto parsed = json::Parse(raw);
  if (!parsed)
    return IsSensitiveName(raw) ? std::string{} : std::string{raw};
  RedactRecursive(*parsed);
  return json::Serialize(*parsed);
}

std::string RedactHeaders(std::string_view raw) {
  if (raw.empty())
    return "[]";
  auto parsed = json::Parse(raw);
  if (!parsed)
    return {};
  auto *array = std::get_if<json::Array>(&*parsed);
  if (!array)
    return {};
  for (auto &value : *array) {
    auto *object = std::get_if<json::Object>(&value);
    if (!object)
      continue;
    const auto *name_value = json::Find(*object, "name");
    const auto *name = json::AsString(name_value);
    if (name && IsSensitiveName(*name))
      (*object)["value"] = std::string{};
  }
  return json::Serialize(*parsed);
}

std::string RedactSettingValue(std::string_view key, std::string_view raw) {
  if (key == "@lineai_ssh_config") {
    constexpr std::array<std::string_view, 3> fields{"password", "privateKey",
                                                     "passphrase"};
    return RedactObjectFields(raw, fields);
  }
  if (key == "@lineai_web_search_config") {
    constexpr std::array<std::string_view, 1> fields{"apiKey"};
    return RedactObjectFields(raw, fields);
  }
  return IsSensitiveName(key) ? std::string{} : std::string{raw};
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

void RedactRow(std::string_view table, json::Object &row) {
  if (table == "messages") {
    SetStringCell(row, "content", {});
    SetStringCell(row, "reasoning_content", {});
    SetStringCell(row, "raw_json", {});
  } else if (table == "model_configs") {
    SetStringCell(row, "api_key", {});
    constexpr std::array<std::string_view, 2> fields{"apiKey", "api_key"};
    SetStringCell(row, "raw_json",
                  RedactObjectFields(RowString(row, "raw_json").value_or(""),
                                     fields));
  } else if (table == "settings") {
    const auto key = RowString(row, "key").value_or("");
    SetStringCell(row, "value",
                  RedactSettingValue(key, RowString(row, "value").value_or("")));
  } else if (table == "extension_mcps") {
    SetStringCell(
        row, "request_headers_json",
        RedactHeaders(RowString(row, "request_headers_json").value_or("")));
    SetStringCell(row, "raw_json",
                  RedactRecursiveJson(RowString(row, "raw_json").value_or("")));
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
    PreparedTable output{.name = std::string{table_name}};
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
                          .mode = huxerui::sqlite::OpenMode::ReadOnly});
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
    if (table_name == "model_configs")
      summary.models = table.rows.size();
    else if (table_name == "conversations")
      summary.conversations = table.rows.size();
    else if (table_name == "settings")
      summary.settings = table.rows.size();
    table_json.emplace(std::string{table_name}, EncodeTable(table));
  }

  json::Object root{{"format", "linecode-database"},
                    {"schemaVersion", std::int64_t{kDatabaseSchemaVersion}},
                    {"tables", std::move(table_json)}};
  co_return ArchiveDatabaseExport{.json = json::Serialize(root),
                                  .summary = summary};
}

huxerui::Task<DataArchiveResult<domain::ArchiveSummary>>
SqliteArchiveDatabase::ReplaceFromSnapshot(std::string text) {
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
  if (*version > kDatabaseSchemaVersion) {
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

} // namespace linecode::infrastructure
