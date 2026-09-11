#include "application/tool_file_path_policy.h"

#include <algorithm>
#include <filesystem>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>

namespace linecode::application {
namespace {

namespace fs = std::filesystem;

// Legacy String.trim(): strips every character at or below U+0020.
std::string Trim(std::string_view value) {
  std::size_t begin = 0;
  std::size_t end = value.size();
  while (begin < end && static_cast<unsigned char>(value[begin]) <= ' ')
    ++begin;
  while (end > begin && static_cast<unsigned char>(value[end - 1]) <= ' ')
    --end;
  return std::string{value.substr(begin, end - begin)};
}

FileToolPathError Error(FileToolPathErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

// Canonical form used by the legacy getCanonicalFile(): existing components are
// resolved through symbolic links, missing trailing components stay lexical.
std::expected<fs::path, FileToolPathError>
Canonical(const fs::path &value) {
  std::error_code error;
  auto resolved = fs::weakly_canonical(value, error);
  if (error) {
    return std::unexpected(
        Error(FileToolPathErrorCode::io,
              "Unable to resolve path: " + value.generic_string()));
  }
  return resolved;
}

bool IsInsidePath(const fs::path &root, const fs::path &candidate) {
  const auto mismatch = std::ranges::mismatch(root, candidate);
  return mismatch.in1 == root.end();
}

} // namespace

std::expected<std::string, FileToolPathError>
FileToolPathPolicy::Resolve(std::string_view workspace_root,
                            std::string_view input_path) {
  const std::string root_text = Trim(workspace_root);
  if (root_text.empty()) {
    return std::unexpected(Error(FileToolPathErrorCode::empty_workspace,
                                 "Workspace path is empty"));
  }
  auto root = Canonical(fs::path{root_text});
  if (!root)
    return std::unexpected(std::move(root.error()));

  const std::string raw_path = Trim(input_path);
  fs::path target = *root;
  if (!raw_path.empty()) {
    const fs::path requested{raw_path};
    target = requested.is_absolute() ? requested : (*root / requested);
  }
  auto canonical = Canonical(target);
  if (!canonical)
    return std::unexpected(std::move(canonical.error()));
  if (!IsInsidePath(*root, *canonical)) {
    return std::unexpected(
        Error(FileToolPathErrorCode::outside_workspace,
              "Path is outside the current workspace: " + raw_path));
  }
  return canonical->generic_string();
}

std::string FileToolPathPolicy::Display(std::string_view workspace_root,
                                        std::string_view path) {
  auto root = Canonical(fs::path{Trim(workspace_root)});
  auto target = Canonical(fs::path{Trim(path)});
  if (!root || !target || !IsInsidePath(*root, *target))
    return target ? target->generic_string() : std::string{path};
  if (*root == *target)
    return ".";
  return target->lexically_relative(*root).generic_string();
}

bool FileToolPathPolicy::IsInside(std::string_view root,
                                  std::string_view candidate) noexcept {
  return IsInsidePath(fs::path{root}.lexically_normal(),
                      fs::path{candidate}.lexically_normal());
}

} // namespace linecode::application
