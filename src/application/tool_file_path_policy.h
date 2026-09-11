#pragma once

#include <cstdint>
#include <expected>
#include <string>
#include <string_view>

namespace linecode::application {

enum class FileToolPathErrorCode : std::uint8_t {
  empty_workspace,
  outside_workspace,
  io,
};

struct FileToolPathError final {
  FileToolPathErrorCode code{FileToolPathErrorCode::io};
  std::string message;

  bool operator==(const FileToolPathError &) const = default;
};

// Resolves a tool-supplied path against the current workspace directory.
//
// This is the C++ port of the legacy Android FileToolPathPolicy.resolve():
// the workspace root is canonicalized, the requested path is resolved against
// it (absolute requests are taken as-is), the result is canonicalized again,
// and it must stay inside the root. Canonicalization follows symbolic links,
// so ".." traversal, absolute paths outside the workspace, and symbolic links
// that escape the workspace are all rejected, while a link that stays inside
// the workspace resolves to its target exactly like the legacy
// getCanonicalFile() did. The legacy extra-write-roots and bypass-protection
// switches have no equivalent in the C++ product, so they are not modelled.
//
// Resolution is lexical for a missing leaf: the tools create files that do not
// exist yet.
class FileToolPathPolicy final {
public:
  FileToolPathPolicy() = delete;

  [[nodiscard]] static std::expected<std::string, FileToolPathError>
  Resolve(std::string_view workspace_root, std::string_view input_path);
  // Legacy FileToolPathPolicy.displayPath(): "." for the root itself, the
  // root-relative path inside the workspace, or the absolute path otherwise.
  [[nodiscard]] static std::string Display(std::string_view workspace_root,
                                           std::string_view path);
  [[nodiscard]] static bool IsInside(std::string_view root,
                                     std::string_view candidate) noexcept;
};

} // namespace linecode::application
