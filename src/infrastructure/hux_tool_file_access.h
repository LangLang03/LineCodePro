#pragma once

#include <memory>

#include "application/ports/tool_file_access.h"

namespace linecode::infrastructure {

// Serves the built-in file tools through the HuxerUI file system, so the
// application layer stays free of platform I/O. Every path handed to this
// adapter has already been resolved and containment-checked by
// `application::FileToolPathPolicy`.
class HuxToolFileAccess final : public application::ToolFileAccess {
public:
  HuxToolFileAccess() = default;

  [[nodiscard]] huxerui::Task<
      application::ToolFileResult<application::ToolFileInfo>>
  Stat(std::string path) const override;
  [[nodiscard]] huxerui::Task<
      application::ToolFileResult<std::string>>
  ReadText(std::string path) const override;
  [[nodiscard]] huxerui::Task<
      application::ToolFileResult<std::string>>
  ReadBytes(std::string path, std::uint64_t offset,
            std::uint64_t length) const override;
  [[nodiscard]] huxerui::Task<application::ToolFileResult<void>>
  WriteText(std::string path, std::string content) override;
  [[nodiscard]] huxerui::Task<application::ToolFileResult<void>>
  CreateDirectories(std::string path) override;
  [[nodiscard]] huxerui::Task<
      application::ToolFileResult<std::vector<application::ToolFileEntry>>>
  ListDirectory(std::string path) const override;
  [[nodiscard]] huxerui::Task<application::ToolFileResult<void>>
  Delete(std::string path) override;
  [[nodiscard]] huxerui::Task<
      application::ToolFileResult<std::vector<std::string>>>
  Glob(std::string root, std::string pattern,
       std::size_t maximum_results) const override;
};

} // namespace linecode::infrastructure
