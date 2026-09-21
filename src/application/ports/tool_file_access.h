#pragma once

#include <cstddef>
#include <cstdint>
#include <expected>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

namespace linecode::application {

enum class ToolFileErrorCode : std::uint8_t {
  invalid_argument,
  not_found,
  not_a_directory,
  is_a_directory,
  too_large,
  io,
};

struct ToolFileError final {
  ToolFileErrorCode code{ToolFileErrorCode::io};
  std::string message;

  bool operator==(const ToolFileError &) const = default;
};

template <class Value>
using ToolFileResult = std::expected<Value, ToolFileError>;

enum class ToolFileKind : std::uint8_t {
  file,
  directory,
  other,
};

struct ToolFileInfo final {
  ToolFileKind kind{ToolFileKind::other};
  // File length in bytes; zero for a directory or any other entry kind.
  std::uint64_t size{};

  bool operator==(const ToolFileInfo &) const = default;
};

struct ToolFileEntry final {
  std::string name;
  ToolFileKind kind{ToolFileKind::other};

  bool operator==(const ToolFileEntry &) const = default;
};

// Case-insensitive, whole-value glob comparison with the legacy tool syntax:
// '*' matches any run of characters other than '/', '**' matches any run
// including '/', and '?' matches exactly one character other than '/'.
[[nodiscard]] bool GlobMatch(std::string_view pattern, std::string_view value);

// Platform-independent file capability used by the built-in file tools. The
// application layer owns path policy and output formatting; this port only
// performs entry operations, so Android can serve every call through the
// HuxerUI file system service instead of std::filesystem.
class ToolFileAccess {
public:
  virtual ~ToolFileAccess() = default;

  // Entry kind and size. Fails with not_found when the path does not exist.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<ToolFileInfo>>
  Stat(std::string path) const = 0;
  // Entire file decoded as UTF-8 text.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<std::string>>
  ReadText(std::string path) const = 0;
  // Raw bytes of [offset, offset + length) returned as an undecoded byte
  // container; a short result means the file ended early.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<std::string>>
  ReadBytes(std::string path, std::uint64_t offset,
            std::uint64_t length) const = 0;
  // Creates or truncates the file. The parent directory must already exist.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<void>>
  WriteText(std::string path, std::string content) = 0;
  // Creates the directory and every missing ancestor.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<void>>
  CreateDirectories(std::string path) = 0;
  // Immediate children in unspecified order.
  [[nodiscard]] virtual huxerui::Task<
      ToolFileResult<std::vector<ToolFileEntry>>>
  ListDirectory(std::string path) const = 0;
  // Removes the entry; a directory is removed with all of its descendants.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<void>>
  Delete(std::string path) = 0;
  // Files below root whose workspace-relative path (or bare file name when the
  // pattern contains no '/') matches pattern, using GlobMatch. Directories
  // whose name starts with '.' and directories named "node_modules" are not
  // traversed. At most maximum_results entries are returned, in the legacy
  // depth-first order: each directory's children sorted case-insensitively.
  [[nodiscard]] virtual huxerui::Task<ToolFileResult<std::vector<std::string>>>
  Glob(std::string root, std::string pattern,
       std::size_t maximum_results) const = 0;
};

} // namespace linecode::application
