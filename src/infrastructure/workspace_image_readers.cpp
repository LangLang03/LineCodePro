#include "infrastructure/workspace_image_readers.h"

#include <chrono>
#include <filesystem>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <system_error>
#include <utility>

#include <huxerui/file.h>
#include <huxerui/huxerui.h>

namespace linecode::infrastructure {
namespace {

namespace fs = std::filesystem;
using application::ImageUnderstandingError;
using application::ImageUnderstandingErrorCode;
using application::ImageUnderstandingResult;
using namespace std::chrono_literals;

constexpr std::uintmax_t kMaximumImageBytes = 10U * 1024U * 1024U;

ImageUnderstandingError Error(ImageUnderstandingErrorCode code,
                              std::string message) {
  return {.code = code, .message = std::move(message)};
}

bool Contains(const fs::path &root, const fs::path &target) {
  auto root_part = root.begin();
  auto target_part = target.begin();
  for (; root_part != root.end() && target_part != target.end();
       ++root_part, ++target_part) {
    if (*root_part != *target_part)
      return false;
  }
  return root_part == root.end();
}

ImageUnderstandingResult<fs::path>
ResolveSafeFile(std::string_view root_text, std::string_view requested_text) {
  std::error_code error;
  const auto root = fs::canonical(fs::path{root_text}, error);
  if (error || !fs::is_directory(root, error))
    return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                 "Workspace root is unavailable"));
  const fs::path requested{requested_text};
  const auto lexical = requested.is_absolute()
                           ? requested.lexically_normal()
                           : (root / requested).lexically_normal();
  const auto relative = lexical.lexically_relative(root);
  if (relative.empty() || relative.is_absolute() ||
      (!relative.empty() && *relative.begin() == ".."))
    return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                 "Image path is outside the workspace"));
  auto current = root;
  for (const auto &component : relative) {
    current /= component;
    const auto status = fs::symlink_status(current, error);
    if (error)
      return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                   "Image file was not found"));
    if (fs::is_symlink(status))
      return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                   "Symbolic links are not allowed"));
  }
  const auto canonical = fs::canonical(lexical, error);
  if (error || !Contains(root, canonical) ||
      !fs::is_regular_file(canonical, error))
    return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                 "Image file was not found"));
  const auto size = fs::file_size(canonical, error);
  if (error)
    return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                 "Unable to inspect image file"));
  if (size > kMaximumImageBytes)
    return std::unexpected(Error(ImageUnderstandingErrorCode::too_large,
                                 "Image exceeds the 10 MB safety limit"));
  return canonical;
}

template <class Value, class Start>
huxerui::Task<application::TerminalProviderResult<Value>>
AwaitTerminal(Start start) {
  auto result = std::make_shared<
      std::optional<application::TerminalProviderResult<Value>>>();
  std::invoke(std::move(start),
              [result](application::TerminalProviderResult<Value> value) {
                result->emplace(std::move(value));
              });
  while (!result->has_value())
    co_await huxerui::Delay(5ms);
  co_return std::move(**result);
}

} // namespace

LocalWorkspaceImageReader::LocalWorkspaceImageReader(
    std::shared_ptr<application::ProjectWorkspaceController> workspace)
    : workspace_(std::move(workspace)) {
  if (!workspace_)
    throw std::invalid_argument(
        "LocalWorkspaceImageReader requires workspace controller");
}

huxerui::Task<ImageUnderstandingResult<domain::RawWorkspaceImage>>
LocalWorkspaceImageReader::Read(std::string path) {
  auto selected = co_await workspace_->SelectedProject();
  if (!selected)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                    selected.error().message));
  auto resolved = ResolveSafeFile(selected->path, path);
  if (!resolved)
    co_return std::unexpected(std::move(resolved.error()));
  auto bytes = co_await huxerui::File{resolved->string()}.ReadBytesAsync();
  if (!bytes.Succeeded())
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                    bytes.Error().message));
  if (bytes.Value().size() > kMaximumImageBytes)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::too_large,
                                    "Image exceeds the 10 MB safety limit"));
  co_return domain::RawWorkspaceImage{
      .resolved_path = resolved->string(),
      .bytes = std::move(bytes).Value(),
  };
}

TerminalProviderWorkspaceImageReader::TerminalProviderWorkspaceImageReader(
    std::shared_ptr<application::TerminalProviderStore> providers,
    std::shared_ptr<application::TerminalProviderGateway> gateway)
    : providers_(std::move(providers)), gateway_(std::move(gateway)) {
  if (!providers_ || !gateway_)
    throw std::invalid_argument(
        "TerminalProviderWorkspaceImageReader requires store and gateway");
}

SshWorkspaceImageReader::SshWorkspaceImageReader(
    std::shared_ptr<application::SshSettingsService> settings,
    std::shared_ptr<application::ProjectWorkspaceController> workspace,
    std::shared_ptr<application::SshWorkspaceService> files)
    : settings_(std::move(settings)), workspace_(std::move(workspace)),
      files_(std::move(files)) {
  if (!settings_ || !workspace_ || !files_)
    throw std::invalid_argument(
        "SshWorkspaceImageReader requires settings, workspace and files");
}

huxerui::Task<ImageUnderstandingResult<domain::RawWorkspaceImage>>
SshWorkspaceImageReader::Read(std::string path) {
  auto config = co_await settings_->Load();
  if (!config)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                    config.error().message));
  auto selected = co_await workspace_->SelectedProject();
  if (!selected)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                    selected.error().message));
  auto bytes = co_await files_->ReadBytes(*config, selected->path, path,
                                         kMaximumImageBytes);
  if (!bytes) {
    const auto code = bytes.error().code == application::SshErrorCode::size_limit
                          ? ImageUnderstandingErrorCode::too_large
                          : ImageUnderstandingErrorCode::not_found;
    co_return std::unexpected(Error(code, bytes.error().message));
  }
  co_return domain::RawWorkspaceImage{.resolved_path = std::move(path),
                                      .bytes = std::move(*bytes)};
}

huxerui::Task<ImageUnderstandingResult<domain::RawWorkspaceImage>>
TerminalProviderWorkspaceImageReader::Read(std::string path) {
  auto providers = co_await providers_->ListTerminalProviders();
  if (!providers)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                    providers.error().message));
  const auto enabled = std::ranges::find(
      *providers, true, &domain::TerminalProviderConfig::enabled);
  if (enabled == providers->end())
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::unavailable,
                                    "No enabled terminal provider is selected"));
  auto bytes = co_await AwaitTerminal<std::vector<std::byte>>(
      [gateway = gateway_, provider = *enabled,
       path](auto completion) mutable {
        gateway->ReadFile(std::move(provider), path, std::move(completion));
      });
  if (!bytes)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::not_found,
                                    bytes.error().message));
  if (bytes->size() > kMaximumImageBytes)
    co_return std::unexpected(Error(ImageUnderstandingErrorCode::too_large,
                                    "Image exceeds the 10 MB safety limit"));
  co_return domain::RawWorkspaceImage{.resolved_path = std::move(path),
                                      .bytes = std::move(*bytes)};
}

} // namespace linecode::infrastructure
