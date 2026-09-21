#include "infrastructure/legacy_project_schema.h"

#include <string>

#include <huxerui/sqlite.h>

namespace linecode::infrastructure::legacy_project_schema {

huxerui::sqlite::Result<void>
Ensure(huxerui::sqlite::Transaction &transaction) {
  auto settings = transaction.Execute(std::string{create_settings});
  if (!settings)
    return settings.Error();
  auto projects = transaction.Execute(std::string{create_projects});
  if (!projects)
    return projects.Error();
  return {};
}

} // namespace linecode::infrastructure::legacy_project_schema
