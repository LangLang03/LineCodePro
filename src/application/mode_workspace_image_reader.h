#pragma once

#include <memory>
#include <vector>

#include "application/mcp_execution_settings.h"
#include "application/ports/image_understanding.h"

namespace linecode::application {

struct WorkspaceImageReaderRoute final {
  domain::McpExecutionMode mode{domain::McpExecutionMode::local};
  std::shared_ptr<WorkspaceImageReader> reader;
};

// Open-ended mode dispatch: adding a workspace transport registers one route
// instead of adding protocol conditionals to image_understanding.
class ModeWorkspaceImageReader final : public WorkspaceImageReader {
public:
  ModeWorkspaceImageReader(
      std::shared_ptr<McpExecutionSettingsService> settings,
      std::vector<WorkspaceImageReaderRoute> routes);

  [[nodiscard]] huxerui::Task<
      ImageUnderstandingResult<domain::RawWorkspaceImage>>
  Read(std::string path) override;

private:
  std::shared_ptr<McpExecutionSettingsService> settings_;
  std::vector<WorkspaceImageReaderRoute> routes_;
};

} // namespace linecode::application
