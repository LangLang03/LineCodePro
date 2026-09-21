#pragma once

namespace linecode::application {

class WorkspaceDirectoryShareService {
public:
  virtual ~WorkspaceDirectoryShareService() = default;

  // Returns whether a native surface accepted the request to expose the
  // application-owned .linecode/home directory.
  [[nodiscard]] virtual bool OpenHome() = 0;
};

} // namespace linecode::application
