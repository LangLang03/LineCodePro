#include "infrastructure/hux_tool_file_access.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/file.h>
#include <huxerui/stream.h>

namespace linecode::infrastructure {
namespace {

namespace app = linecode::application;

app::ToolFileError Error(app::ToolFileErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

// HuxerUI reports a portable category; map it onto the tool-facing vocabulary
// so the tools can distinguish "missing" from "denied" without platform types.
app::ToolFileError Translate(const huxerui::IoError &error) {
  using huxerui::IoErrorCode;
  switch (error.code) {
  case IoErrorCode::NotFound:
    return Error(app::ToolFileErrorCode::not_found, error.message);
  case IoErrorCode::NotDirectory:
    return Error(app::ToolFileErrorCode::not_a_directory, error.message);
  case IoErrorCode::IsDirectory:
    return Error(app::ToolFileErrorCode::is_a_directory, error.message);
  case IoErrorCode::TooLarge:
    return Error(app::ToolFileErrorCode::too_large, error.message);
  case IoErrorCode::PermissionDenied:
  case IoErrorCode::InvalidEncoding:
  case IoErrorCode::Unsupported:
  case IoErrorCode::Io:
  case IoErrorCode::AlreadyExists:
  case IoErrorCode::Timeout:
  case IoErrorCode::NoSpace:
    break;
  }
  return Error(app::ToolFileErrorCode::io,
               error.message.empty() ? std::string{"filesystem failure"}
                                     : error.message);
}

app::ToolFileKind Translate(const huxerui::FileType type) {
  switch (type) {
  case huxerui::FileType::File:
    return app::ToolFileKind::file;
  case huxerui::FileType::Directory:
    return app::ToolFileKind::directory;
  case huxerui::FileType::Other:
    break;
  }
  return app::ToolFileKind::other;
}

// The legacy GlobTool walks top-down and skips dot-directories and
// node_modules, mirroring the repository search conventions.
bool ShouldSkipDirectory(const std::string_view name) {
  return name.starts_with('.') || name == "node_modules";
}

bool MatchCandidate(const std::string &relative, const std::string &name,
                    const std::string &pattern) {
  // A pattern without a separator matches the bare file name, which is what
  // the legacy tool did for patterns like "*.java".
  if (pattern.find('/') == std::string::npos)
    return app::GlobMatch(pattern, name);
  return app::GlobMatch(pattern, relative);
}

std::string JoinRelative(const std::string_view parent,
                         const std::string_view name) {
  if (parent.empty())
    return std::string{name};
  std::string result{parent};
  result.push_back('/');
  result.append(name);
  return result;
}

} // namespace

huxerui::Task<app::ToolFileResult<app::ToolFileInfo>>
HuxToolFileAccess::Stat(std::string path) const {
  const huxerui::File file{path};
  auto status = co_await file.StatAsync();
  if (!status.Succeeded())
    co_return std::unexpected(Translate(status.Error()));
  co_return app::ToolFileInfo{.kind = Translate(status.Value().type),
                              .size = status.Value().size};
}

huxerui::Task<app::ToolFileResult<std::string>>
HuxToolFileAccess::ReadText(std::string path) const {
  const huxerui::File file{path};
  auto content = co_await file.ReadStringAsync();
  if (!content.Succeeded())
    co_return std::unexpected(Translate(content.Error()));
  co_return std::move(content).Value();
}

huxerui::Task<app::ToolFileResult<std::string>>
HuxToolFileAccess::ReadBytes(std::string path, std::uint64_t offset,
                             std::uint64_t length) const {
  const huxerui::File file{path};
  auto stream = co_await file.OpenReadAsync();
  if (!stream.Succeeded())
    co_return std::unexpected(Translate(stream.Error()));
  auto &reader = stream.Value();

  // InputStream has no portable seek, so the skipped prefix is consumed in
  // bounded chunks. This keeps memory flat regardless of the requested offset.
  std::uint64_t remaining_skip = offset;
  while (remaining_skip > 0) {
    const auto chunk = static_cast<std::size_t>(
        std::min<std::uint64_t>(remaining_skip, 64U * 1024U));
    auto skipped = co_await reader.ReadAsync(chunk);
    if (!skipped.Succeeded())
      co_return std::unexpected(Translate(skipped.Error()));
    if (skipped.Value().empty())
      co_return std::string{}; // Offset past EOF reads as empty.
    remaining_skip -= skipped.Value().size();
  }

  std::string result;
  result.reserve(static_cast<std::size_t>(
      std::min<std::uint64_t>(length, 4U * 1024U * 1024U)));
  std::uint64_t remaining = length;
  while (remaining > 0) {
    const auto chunk = static_cast<std::size_t>(
        std::min<std::uint64_t>(remaining, 64U * 1024U));
    auto read = co_await reader.ReadAsync(chunk);
    if (!read.Succeeded())
      co_return std::unexpected(Translate(read.Error()));
    const auto &bytes = read.Value();
    if (bytes.empty())
      break;
    result.append(reinterpret_cast<const char *>(bytes.data()), bytes.size());
    remaining -= bytes.size();
  }
  co_return result;
}

huxerui::Task<app::ToolFileResult<void>>
HuxToolFileAccess::WriteText(std::string path, std::string content) {
  const huxerui::File file{path};
  if (!co_await file.WriteStringAsync(std::move(content)))
    co_return std::unexpected(
        Error(app::ToolFileErrorCode::io, "failed to write " + path));
  co_return app::ToolFileResult<void>{};
}

huxerui::Task<app::ToolFileResult<void>>
HuxToolFileAccess::CreateDirectories(std::string path) {
  const huxerui::File directory{path};
  if (!co_await directory.CreateDirectoriesAsync())
    co_return std::unexpected(
        Error(app::ToolFileErrorCode::io, "failed to create " + path));
  co_return app::ToolFileResult<void>{};
}

huxerui::Task<
    app::ToolFileResult<std::vector<app::ToolFileEntry>>>
HuxToolFileAccess::ListDirectory(std::string path) const {
  const huxerui::File directory{path};
  auto children = co_await directory.ListChildrenAsync();
  if (!children.Succeeded())
    co_return std::unexpected(Translate(children.Error()));

  std::vector<app::ToolFileEntry> entries;
  entries.reserve(children.Value().size());
  for (const auto &child : children.Value()) {
    auto status = co_await child.StatAsync();
    entries.push_back(app::ToolFileEntry{
        .name = child.Name(),
        .kind = status.Succeeded() ? Translate(status.Value().type)
                                   : app::ToolFileKind::other});
  }
  co_return entries;
}

huxerui::Task<app::ToolFileResult<void>>
HuxToolFileAccess::Delete(std::string path) {
  const huxerui::File file{path};
  auto status = co_await file.StatAsync();
  if (!status.Succeeded())
    co_return std::unexpected(Translate(status.Error()));
  const bool removed =
      status.Value().type == huxerui::FileType::Directory
          ? co_await file.DeleteRecursivelyAsync()
          : co_await file.DeleteAsync();
  if (!removed)
    co_return std::unexpected(
        Error(app::ToolFileErrorCode::io, "failed to delete " + path));
  co_return app::ToolFileResult<void>{};
}

huxerui::Task<app::ToolFileResult<std::vector<std::string>>>
HuxToolFileAccess::Glob(std::string root, std::string pattern,
                        std::size_t maximum_results) const {
  std::vector<std::string> matches;
  // Depth-first, each directory's children sorted case-insensitively, which is
  // the order the legacy tool produced.
  std::vector<std::pair<std::string, std::string>> pending{
      {root, std::string{}}};
  while (!pending.empty() && matches.size() < maximum_results) {
    auto [directory_path, relative_directory] = pending.back();
    pending.pop_back();

    const huxerui::File directory{directory_path};
    auto children = co_await directory.ListChildrenAsync();
    if (!children.Succeeded())
      continue;

    std::vector<huxerui::File> ordered = std::move(children).Value();
    std::ranges::sort(ordered, [](const huxerui::File &left,
                                  const huxerui::File &right) {
      std::string a = left.Name();
      std::string b = right.Name();
      std::ranges::transform(a, a.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
      });
      std::ranges::transform(b, b.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
      });
      return a < b;
    });

    for (const auto &child : ordered) {
      if (matches.size() >= maximum_results)
        break;
      const std::string name = child.Name();
      const std::string relative = JoinRelative(relative_directory, name);
      auto status = co_await child.StatAsync();
      if (!status.Succeeded())
        continue;
      if (status.Value().type == huxerui::FileType::Directory) {
        if (!ShouldSkipDirectory(name))
          pending.emplace_back(child.Path(), relative);
        continue;
      }
      if (MatchCandidate(relative, name, pattern))
        matches.push_back(relative);
    }
  }
  co_return matches;
}

} // namespace linecode::infrastructure
