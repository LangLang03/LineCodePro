#pragma once

#include <functional>
#include <string>

namespace linecode::application {

struct StoragePermissionResult final {
  bool granted{};
  std::string error;

  [[nodiscard]] bool Succeeded() const noexcept { return error.empty(); }
};

class StoragePermissionService {
public:
  using Completion = std::function<void(StoragePermissionResult)>;

  virtual ~StoragePermissionService() = default;

  virtual void Query(Completion completion) = 0;
  virtual void OpenManagementSettings(Completion completion) = 0;
};

} // namespace linecode::application
