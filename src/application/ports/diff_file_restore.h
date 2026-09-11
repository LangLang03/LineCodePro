#pragma once

#include <string>

#include <huxerui/task.h>

#include "domain/diff_record.h"

namespace linecode::application {

// Outcome of one file restoration. Failure carries the legacy message so the
// review service can publish it verbatim.
struct DiffRestoreResult final {
  bool success{};
  std::string message;
};

// File-system side of a revert, mirroring the legacy `FileRestorer`.
//
// The port keeps the review service independent of the platform file API so
// tests can drive both restore paths without touching a real disk.
class DiffFileRestore {
public:
  virtual ~DiffFileRestore() = default;

  // Writes `record.old_content` back, or deletes the file when the change
  // created it. Returns `"Cannot delete file: " + path` or
  // `"Cannot create parent directory: " + path` on failure.
  [[nodiscard]] virtual huxerui::Task<DiffRestoreResult>
  RestoreOldContent(domain::DiffRecord record) = 0;
};

} // namespace linecode::application
