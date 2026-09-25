#pragma once

#include <functional>
#include <string>

#include <huxerui/file.h>

namespace linecode::application {

struct StoragePermissionResult final {
  bool granted{};
  std::string error;

  [[nodiscard]] bool Succeeded() const noexcept { return error.empty(); }
};

struct StorageDirectoryPathResult final {
  std::string path;
  std::string error;
  bool permission_required{};
};

class StoragePermissionService {
public:
  using Completion = std::function<void(StoragePermissionResult)>;
  using DirectoryPathCompletion =
      std::function<void(StorageDirectoryPathResult)>;

  virtual ~StoragePermissionService() = default;

  virtual void Query(Completion completion) = 0;
  virtual void OpenManagementSettings(Completion completion) = 0;
  // A document-tree grant is not a filesystem path. Android can resolve only
  // directories on storage volumes that the app can also access by path.
  virtual void ResolveLocalDirectory(huxerui::FileReference directory,
                                     DirectoryPathCompletion completion) = 0;
};

} // namespace linecode::application
