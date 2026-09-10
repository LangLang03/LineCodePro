#include "infrastructure/archive_validation.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <set>
#include <string>
#include <string_view>
#include <variant>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;

std::unexpected<ArchiveValidationError> Invalid(std::string message) {
  return std::unexpected(ArchiveValidationError{std::move(message)});
}

const std::int64_t *Integer(const json::Value *value) {
  return value ? std::get_if<std::int64_t>(value) : nullptr;
}

const bool *Boolean(const json::Value *value) {
  return value ? std::get_if<bool>(value) : nullptr;
}

bool IsKnownWorkspaceRoot(std::string_view name) {
  constexpr std::array<std::string_view, 3> names{"home", "project",
                                                  "skills"};
  return std::ranges::find(names, name) != names.end();
}

bool IsValidBase64(std::string_view text) {
  if (text.size() % 4U != 0U) {
    return false;
  }
  const auto digit = [](char value) {
    return (value >= 'A' && value <= 'Z') ||
           (value >= 'a' && value <= 'z') ||
           (value >= '0' && value <= '9') || value == '+' || value == '/';
  };
  for (std::size_t index = 0; index < text.size(); index += 4) {
    const bool final = index + 4U == text.size();
    if (!digit(text[index]) || !digit(text[index + 1])) {
      return false;
    }
    const char third = text[index + 2];
    const char fourth = text[index + 3];
    if ((!digit(third) && third != '=') ||
        (!digit(fourth) && fourth != '=') ||
        ((third == '=' || fourth == '=') && !final) ||
        (third == '=' && fourth != '=')) {
      return false;
    }
  }
  return true;
}

std::expected<void, ArchiveValidationError>
ValidateCell(const json::Value &value) {
  const auto *cell = json::AsObject(&value);
  if (!cell) {
    return Invalid("database row cell is not an object");
  }
  const auto *type = json::AsString(json::Find(*cell, "type"));
  if (!type) {
    return Invalid("database row cell has no type");
  }
  const auto *stored = json::Find(*cell, "value");
  if (*type == "null") {
    if (stored) {
      return Invalid("null database cell unexpectedly has a value");
    }
    return {};
  }
  if (*type == "integer") {
    if (!Integer(stored)) {
      return Invalid("invalid integer database cell");
    }
    return {};
  }
  if (*type == "float") {
    if (!Integer(stored) && !(stored && std::get_if<double>(stored))) {
      return Invalid("invalid float database cell");
    }
    return {};
  }
  const auto *text = json::AsString(stored);
  if (!text) {
    return Invalid("invalid text database cell");
  }
  if (*type == "string") {
    return {};
  }
  if (*type == "blob") {
    return IsValidBase64(*text)
               ? std::expected<void, ArchiveValidationError>{}
               : Invalid("invalid base64 database cell");
  }
  return Invalid("unsupported database cell type");
}

} // namespace

std::expected<ValidatedArchiveManifest, ArchiveValidationError>
ValidateArchiveManifest(std::string_view text) {
  auto parsed = json::Parse(text);
  if (!parsed) {
    return Invalid("invalid .linecode manifest JSON: " +
                   parsed.error().message);
  }
  const auto *root = json::AsObject(&*parsed);
  if (!root) {
    return Invalid(".linecode manifest is not an object");
  }
  const auto *format = json::AsString(json::Find(*root, "format"));
  const auto *version = Integer(json::Find(*root, "formatVersion"));
  const auto *container = json::AsString(json::Find(*root, "container"));
  const auto *created_at = Integer(json::Find(*root, "createdAt"));
  const auto *database = Boolean(json::Find(*root, "database"));
  const auto *roots = json::AsArray(json::Find(*root, "workspaceRoots"));
  if (!format || *format != "linecode" || !version || *version != 1 ||
      !container || *container != "zip" || !created_at || *created_at < 0 ||
      !database || !roots) {
    return Invalid("invalid or unsupported .linecode manifest");
  }
  std::set<std::string, std::less<>> names;
  for (const auto &value : *roots) {
    const auto *name = json::AsString(&value);
    if (!name || !IsKnownWorkspaceRoot(*name) ||
        !names.emplace(*name).second) {
      return Invalid("invalid workspace root in .linecode manifest");
    }
  }
  return ValidatedArchiveManifest{.contains_database = *database};
}

std::expected<void, ArchiveValidationError>
ValidateDatabaseSnapshot(std::string_view text,
                         std::int64_t maximum_schema_version) {
  auto parsed = json::Parse(text);
  if (!parsed) {
    return Invalid("invalid database snapshot JSON: " +
                   parsed.error().message);
  }
  const auto *root = json::AsObject(&*parsed);
  const auto *format = root ? json::AsString(json::Find(*root, "format"))
                            : nullptr;
  const auto *version = root ? Integer(json::Find(*root, "schemaVersion"))
                             : nullptr;
  const auto *tables = root ? json::AsObject(json::Find(*root, "tables"))
                            : nullptr;
  if (!format || *format != "linecode-database" || !version || *version < 0 ||
      !tables) {
    return Invalid("invalid .linecode database snapshot");
  }
  if (*version > maximum_schema_version) {
    return Invalid("archive was created by a newer LineCode database schema");
  }

  for (const auto &[table_name, value] : *tables) {
    const auto *table = json::AsObject(&value);
    const auto *columns = table ? json::AsArray(json::Find(*table, "columns"))
                                : nullptr;
    const auto *rows = table ? json::AsArray(json::Find(*table, "rows"))
                             : nullptr;
    if (!columns || !rows) {
      return Invalid("invalid database table payload: " + table_name);
    }
    std::set<std::string, std::less<>> declared;
    for (const auto &column_value : *columns) {
      const auto *column = json::AsString(&column_value);
      if (!column || column->empty() || !declared.emplace(*column).second) {
        return Invalid("invalid database columns in table " + table_name);
      }
    }
    for (const auto &row_value : *rows) {
      const auto *row = json::AsObject(&row_value);
      if (!row || row->size() != declared.size()) {
        return Invalid("database rows use inconsistent columns in table " +
                       table_name);
      }
      for (const auto &[column, cell] : *row) {
        if (!declared.contains(column)) {
          return Invalid("database row contains an undeclared column in table " +
                         table_name);
        }
        auto valid = ValidateCell(cell);
        if (!valid) {
          return valid;
        }
      }
    }
  }
  return {};
}

} // namespace linecode::infrastructure
