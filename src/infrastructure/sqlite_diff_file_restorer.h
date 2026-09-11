#pragma once

#include <huxerui/task.h>

#include "application/ports/diff_file_restore.h"

namespace linecode::infrastructure {

// HuxerUI-backed `FileRestorer`: writes the recorded old content back through
// the SDK file API so Web and virtual file systems keep working. Local paths
// are resolved by the platform adapter rather than by std::filesystem.
class SqliteDiffFileRestorer final : public application::DiffFileRestore {
public:
  [[nodiscard]] huxerui::Task<application::DiffRestoreResult>
  RestoreOldContent(domain::DiffRecord record) override;
};

} // namespace linecode::infrastructure
