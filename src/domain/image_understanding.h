#pragma once

#include <cstddef>
#include <string>
#include <vector>

namespace linecode::domain {

struct ImageUnderstandingRequest final {
  std::string path;
  std::string prompt;

  bool operator==(const ImageUnderstandingRequest &) const = default;
};

struct WorkspaceImage final {
  std::string resolved_path;
  std::string mime_type;
  std::vector<std::byte> bytes;

  bool operator==(const WorkspaceImage &) const = default;
};

struct RawWorkspaceImage final {
  std::string resolved_path;
  std::vector<std::byte> bytes;

  bool operator==(const RawWorkspaceImage &) const = default;
};

} // namespace linecode::domain
