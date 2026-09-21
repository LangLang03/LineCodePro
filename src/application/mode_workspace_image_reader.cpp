#include "application/mode_workspace_image_reader.h"

#include <algorithm>
#include <ranges>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

ImageUnderstandingError Error(ImageUnderstandingErrorCode code,
                              std::string message) {
  return {.code = code, .message = std::move(message)};
}

} // namespace

ModeWorkspaceImageReader::ModeWorkspaceImageReader(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::vector<WorkspaceImageReaderRoute> routes)
    : settings_(std::move(settings)), routes_(std::move(routes)) {
  if (!settings_)
    throw std::invalid_argument(
        "ModeWorkspaceImageReader requires execution settings");
  std::erase_if(routes_, [](const auto &route) { return !route.reader; });
  std::ranges::sort(routes_, {}, &WorkspaceImageReaderRoute::mode);
  if (std::ranges::adjacent_find(routes_, {},
                                 &WorkspaceImageReaderRoute::mode) !=
      routes_.end())
    throw std::invalid_argument(
        "ModeWorkspaceImageReader routes must have unique modes");
}

huxerui::Task<ImageUnderstandingResult<domain::RawWorkspaceImage>>
ModeWorkspaceImageReader::Read(std::string path) {
  auto settings = co_await settings_->Load();
  if (!settings)
    co_return std::unexpected(
        Error(ImageUnderstandingErrorCode::unavailable,
              "Unable to load execution mode: " + settings.error().message));
  const auto found = std::ranges::find(
      routes_, settings->mode, &WorkspaceImageReaderRoute::mode);
  if (found == routes_.end())
    co_return std::unexpected(Error(
        ImageUnderstandingErrorCode::unavailable,
        "Image reading is unavailable for the current execution mode"));
  co_return co_await found->reader->Read(std::move(path));
}

} // namespace linecode::application
