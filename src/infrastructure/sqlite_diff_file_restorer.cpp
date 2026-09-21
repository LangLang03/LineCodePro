#include "infrastructure/sqlite_diff_file_restorer.h"

#include <string>
#include <utility>

#include <huxerui/file.h>

namespace linecode::infrastructure {
namespace {

using application::DiffRestoreResult;

DiffRestoreResult Restored() {
  return {.success = true, .message = std::string{}};
}

// Verbatim legacy messages from `FileRestorer.restoreOldContent`.
DiffRestoreResult DeleteRefused(const std::string &file_path) {
  return {.success = false, .message = "Cannot delete file: " + file_path};
}

DiffRestoreResult ParentRefused(const std::string &file_path) {
  return {.success = false,
          .message = "Cannot create parent directory: " + file_path};
}

} // namespace

huxerui::Task<DiffRestoreResult>
SqliteDiffFileRestorer::RestoreOldContent(domain::DiffRecord record) {
  const huxerui::File file{record.file_path};
  if (!record.old_exists) {
    // The change created the file, so reverting removes it. A missing file is
    // not a failure: the legacy code only reports a refused deletion.
    if (file.Exists() && !file.Delete())
      co_return DeleteRefused(record.file_path);
    co_return Restored();
  }

  if (const auto parent = file.Parent()) {
    // Legacy `FileRestorer` reports the parent path, not the file path, and
    // only attempts creation when the parent is missing; an existing regular
    // file in that position makes the subsequent write fail instead.
    if (!parent->Exists() && !parent->CreateDirectories())
      co_return ParentRefused(parent->Path());
  }
  // `WriteString` creates or truncates, matching `new FileOutputStream(file,
  // false)`; an empty `old_content` therefore restores an empty file.
  if (!file.WriteString(record.old_content))
    co_return DiffRestoreResult{.success = false,
                                .message = "Cannot write file: " +
                                           record.file_path};
  co_return Restored();
}

} // namespace linecode::infrastructure
