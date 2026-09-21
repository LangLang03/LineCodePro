#include "infrastructure/legacy_feature_schema.h"

#include <string>

#include <huxerui/sqlite.h>

namespace linecode::infrastructure::legacy_feature_schema {

huxerui::sqlite::Result<void>
Ensure(huxerui::sqlite::Transaction &transaction) {
  for (const auto statement : table_statements) {
    auto created = transaction.Execute(std::string{statement});
    if (!created)
      return created.Error();
  }
  for (const auto statement : index_statements) {
    auto created = transaction.Execute(std::string{statement});
    if (!created)
      return created.Error();
  }
  return {};
}

} // namespace linecode::infrastructure::legacy_feature_schema
