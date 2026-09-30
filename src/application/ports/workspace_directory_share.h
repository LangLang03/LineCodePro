#pragma once

namespace linecode::application {

class WorkspaceDirectoryShareService {
public:
  virtual ~WorkspaceDirectoryShareService() = default;

  // Opens the system directory picker at the application-owned .linecode root.
  [[nodiscard]] virtual bool MountWorkspace() = 0;
};

} // namespace linecode::application
