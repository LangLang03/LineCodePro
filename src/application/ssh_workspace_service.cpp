#include "application/ssh_workspace_service.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cctype>
#include <chrono>
#include <functional>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>

namespace linecode::application {
namespace {

using namespace std::chrono_literals;

constexpr std::size_t kMaximumTreeDepth = 64;
constexpr std::size_t kMaximumTreeNodes = 20'000;
constexpr std::size_t kMaximumDirectoryEntries = 1'200;
std::atomic<std::uint64_t> next_stage{1};

SshError Error(SshErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

std::string Basename(std::string_view path) {
  while (path.size() > 1U && path.back() == '/')
    path.remove_suffix(1);
  const auto separator = path.find_last_of('/');
  return std::string{separator == std::string_view::npos
                         ? path
                         : path.substr(separator + 1U)};
}

std::string Parent(std::string_view path) {
  while (path.size() > 1U && path.back() == '/')
    path.remove_suffix(1);
  const auto separator = path.find_last_of('/');
  if (separator == std::string_view::npos)
    return ".";
  if (separator == 0)
    return "/";
  return std::string{path.substr(0, separator)};
}

std::string Join(std::string_view parent, std::string_view child) {
  while (parent.size() > 1U && parent.back() == '/')
    parent.remove_suffix(1);
  return parent == "/" ? "/" + std::string{child}
                       : std::string{parent} + "/" + std::string{child};
}

SshResult<std::vector<std::string>> Segments(std::string_view relative) {
  if (relative.empty() || relative.front() == '/' ||
      relative.find('\0') != std::string_view::npos) {
    return std::unexpected(Error(SshErrorCode::outside_workspace,
                                 "SSH workspace path must be relative"));
  }
  std::vector<std::string> result;
  for (std::size_t offset{}; offset <= relative.size();) {
    const auto end = relative.find('/', offset);
    const auto part = relative.substr(
        offset, end == std::string_view::npos ? relative.size() - offset
                                              : end - offset);
    if (part.empty() || part == "." || part == "..") {
      return std::unexpected(Error(SshErrorCode::outside_workspace,
                                   "SSH workspace path traversal is not allowed"));
    }
    result.emplace_back(part);
    if (end == std::string_view::npos)
      break;
    offset = end + 1U;
  }
  return result;
}

bool Protected(std::span<const std::string> segments) {
  return !segments.empty() && segments.front() == ".linecode";
}

SshResult<std::string> Root(SshSession &session, std::string_view requested,
                            std::stop_token stop) {
  auto root = session.CanonicalPath(requested.empty() ? "~" : requested, stop);
  if (!root)
    return std::unexpected(std::move(root.error()));
  auto info = session.Stat(*root, false, stop);
  if (!info)
    return std::unexpected(std::move(info.error()));
  if (info->kind != SshFileKind::directory)
    return std::unexpected(Error(SshErrorCode::invalid_argument,
                                 "SSH workspace root is not a directory"));
  return root;
}

SshResult<std::string> Resolve(SshSession &session, std::string_view root,
                               std::string_view relative,
                               bool allow_missing_leaf, bool mutation,
                               std::stop_token stop) {
  auto canonical_root = Root(session, root, stop);
  if (!canonical_root)
    return std::unexpected(std::move(canonical_root.error()));
  auto parts = Segments(relative);
  if (!parts)
    return std::unexpected(std::move(parts.error()));
  if (mutation && Protected(*parts)) {
    return std::unexpected(Error(SshErrorCode::protected_path,
                                 "SSH workspace metadata is protected"));
  }
  std::string current = *canonical_root;
  for (std::size_t index{}; index < parts->size(); ++index) {
    current = Join(current, (*parts)[index]);
    auto info = session.Stat(current, false, stop);
    if (!info) {
      if (allow_missing_leaf && index + 1U == parts->size() &&
          info.error().code == SshErrorCode::not_found)
        return current;
      return std::unexpected(std::move(info.error()));
    }
    if (info->kind == SshFileKind::symbolic_link) {
      return std::unexpected(Error(SshErrorCode::symbolic_link,
                                   "SSH workspace symbolic links cannot be followed"));
    }
    if (index + 1U < parts->size() && info->kind != SshFileKind::directory) {
      return std::unexpected(Error(SshErrorCode::not_found,
                                   "SSH workspace parent is not a directory"));
    }
  }
  return current;
}

SshResult<void> RequireMissing(SshSession &session, std::string_view path,
                               std::stop_token stop) {
  auto info = session.Stat(path, false, stop);
  if (info)
    return std::unexpected(
        Error(SshErrorCode::conflict, "SSH destination already exists"));
  if (info.error().code == SshErrorCode::not_found)
    return {};
  return std::unexpected(std::move(info.error()));
}

SshResult<domain::ProjectFileNode>
LoadNode(SshSession &session, std::string path, std::size_t depth,
         std::size_t &nodes, std::stop_token stop) {
  if (depth > kMaximumTreeDepth || ++nodes > kMaximumTreeNodes) {
    return std::unexpected(Error(SshErrorCode::size_limit,
                                 "SSH workspace tree exceeds safety limits"));
  }
  auto info = session.Stat(path, false, stop);
  if (!info)
    return std::unexpected(std::move(info.error()));
  const bool directory = info->kind == SshFileKind::directory;
  const bool symbolic_link = info->kind == SshFileKind::symbolic_link;
  domain::ProjectFileNode node{
      .name = Basename(path),
      .path = path,
      .directory = directory,
      .symbolic_link = symbolic_link,
      .expanded = depth == 0,
      .children = {},
  };
  if (!directory || symbolic_link)
    return node;
  auto entries = session.List(path, stop);
  if (!entries)
    return std::unexpected(std::move(entries.error()));
  std::ranges::sort(*entries, [](const auto &left, const auto &right) {
    if ((left.kind == SshFileKind::directory) !=
        (right.kind == SshFileKind::directory))
      return left.kind == SshFileKind::directory;
    auto lower = [](std::string value) {
      std::ranges::transform(value, value.begin(), [](unsigned char byte) {
        return static_cast<char>(std::tolower(byte));
      });
      return value;
    };
    return lower(left.name) < lower(right.name);
  });
  if (entries->size() > kMaximumDirectoryEntries)
    entries->resize(kMaximumDirectoryEntries);
  node.children.reserve(entries->size());
  for (auto &entry : *entries) {
    auto child = LoadNode(session, std::move(entry.path), depth + 1U, nodes, stop);
    if (!child)
      return std::unexpected(std::move(child.error()));
    node.children.push_back(std::move(*child));
  }
  return node;
}

bool ValidUtf8(std::span<const std::byte> bytes) {
  std::size_t index{};
  while (index < bytes.size()) {
    const auto lead = std::to_integer<unsigned char>(bytes[index]);
    std::size_t continuation{};
    std::uint32_t codepoint{};
    if (lead < 0x80U) {
      ++index;
      continue;
    }
    if ((lead & 0xE0U) == 0xC0U) {
      continuation = 1;
      codepoint = lead & 0x1FU;
    } else if ((lead & 0xF0U) == 0xE0U) {
      continuation = 2;
      codepoint = lead & 0x0FU;
    } else if ((lead & 0xF8U) == 0xF0U) {
      continuation = 3;
      codepoint = lead & 0x07U;
    } else {
      return false;
    }
    if (index + continuation >= bytes.size())
      return false;
    for (std::size_t offset = 1; offset <= continuation; ++offset) {
      const auto value = std::to_integer<unsigned char>(bytes[index + offset]);
      if ((value & 0xC0U) != 0x80U)
        return false;
      codepoint = (codepoint << 6U) | (value & 0x3FU);
    }
    const std::array minimum{0U, 0x80U, 0x800U, 0x10000U};
    if (codepoint < minimum[continuation] || codepoint > 0x10FFFFU ||
        (codepoint >= 0xD800U && codepoint <= 0xDFFFU))
      return false;
    index += continuation + 1U;
  }
  return true;
}

SshResult<void> DeleteEntry(SshSession &session, std::string_view path,
                            std::size_t depth, std::size_t &nodes,
                            std::stop_token stop) {
  if (depth > kMaximumTreeDepth || ++nodes > kMaximumTreeNodes)
    return std::unexpected(Error(SshErrorCode::size_limit,
                                 "SSH deletion exceeds safety limits"));
  auto info = session.Stat(path, false, stop);
  if (!info)
    return std::unexpected(std::move(info.error()));
  if (info->kind != SshFileKind::directory ||
      info->kind == SshFileKind::symbolic_link)
    return session.RemoveFile(path, stop);
  auto children = session.List(path, stop);
  if (!children)
    return std::unexpected(std::move(children.error()));
  for (const auto &child : *children) {
    auto removed = DeleteEntry(session, child.path, depth + 1U, nodes, stop);
    if (!removed)
      return removed;
  }
  return session.RemoveDirectory(path, stop);
}

SshResult<void> CopyEntry(SshSession &session, std::string_view source,
                          std::string_view destination, std::size_t depth,
                          std::size_t &nodes, std::stop_token stop) {
  if (depth > kMaximumTreeDepth || ++nodes > kMaximumTreeNodes)
    return std::unexpected(Error(SshErrorCode::size_limit,
                                 "SSH copy exceeds safety limits"));
  auto info = session.Stat(source, false, stop);
  if (!info)
    return std::unexpected(std::move(info.error()));
  if (info->kind == SshFileKind::symbolic_link)
    return std::unexpected(Error(SshErrorCode::symbolic_link,
                                 "SSH workspace symbolic links cannot be copied"));
  if (info->kind == SshFileKind::regular) {
    auto bytes = session.Read(source, 64U * 1024U * 1024U, stop);
    if (!bytes)
      return std::unexpected(std::move(bytes.error()));
    return session.Write(destination, *bytes, false, stop);
  }
  if (info->kind != SshFileKind::directory)
    return std::unexpected(Error(SshErrorCode::invalid_argument,
                                 "Unsupported SSH workspace entry type"));
  auto created = session.CreateDirectory(destination, stop);
  if (!created)
    return created;
  auto children = session.List(source, stop);
  if (!children)
    return std::unexpected(std::move(children.error()));
  for (const auto &child : *children) {
    auto copied = CopyEntry(session, child.path, Join(destination, child.name),
                            depth + 1U, nodes, stop);
    if (!copied)
      return copied;
  }
  return {};
}

std::string ManagedName(std::string value) {
  while (!value.empty() && static_cast<unsigned char>(value.front()) <= 0x20U)
    value.erase(value.begin());
  while (!value.empty() && static_cast<unsigned char>(value.back()) <= 0x20U)
    value.pop_back();
  // Keep the generated component comfortably below common NAME_MAX values,
  // but never cut through a UTF-8 code unit sequence.  If the first omitted
  // byte is a continuation byte, back up to the sequence's leading byte.
  std::size_t prefix = std::min<std::size_t>(value.size(), 180U);
  if (prefix < value.size()) {
    while (prefix > 0U &&
           (static_cast<unsigned char>(value[prefix]) & 0xC0U) == 0x80U) {
      --prefix;
    }
  }
  value.resize(prefix);

  std::string result;
  bool separator{};
  for (const unsigned char byte : value) {
    const bool forbidden = byte < 0x20U || byte == '/' || byte == '\\' ||
                           byte == ':' || byte == '*' || byte == '?' ||
                           byte == '"' || byte == '<' || byte == '>' ||
                           byte == '|' || std::isspace(byte);
    if (forbidden) {
      if (!result.empty() && !separator)
        result.push_back('-');
      separator = true;
    } else {
      result.push_back(static_cast<char>(byte));
      separator = false;
    }
  }
  while (!result.empty() && result.back() == '-')
    result.pop_back();
  return result;
}

SshResult<void> EnsureDirectory(SshSession &session, std::string path,
                                std::stop_token stop) {
  auto info = session.Stat(path, false, stop);
  if (info)
    return info->kind == SshFileKind::directory
               ? SshResult<void>{}
               : SshResult<void>{std::unexpected(Error(
                     SshErrorCode::conflict,
                     "SSH managed project path is not a directory"))};
  if (info.error().code != SshErrorCode::not_found)
    return std::unexpected(std::move(info.error()));
  return session.CreateDirectory(path, stop);
}

template <class Value, class Operation>
SshResult<Value> WithSession(SshTransport &transport,
                             const domain::SshConfig &config,
                             std::chrono::milliseconds timeout,
                             std::stop_token stop, Operation operation) {
  auto session = transport.Connect(config, timeout, stop);
  if (!session)
    return std::unexpected(std::move(session.error()));
  return std::invoke(std::move(operation), **session, stop);
}

} // namespace

SshWorkspaceService::SshWorkspaceService(std::shared_ptr<SshTransport> transport)
    : transport_(std::move(transport)) {
  if (!transport_)
    throw std::invalid_argument("SSH workspace transport must not be empty");
}

huxerui::Task<SshResult<std::string>>
SshWorkspaceService::ResolveDirectory(domain::SshConfig config,
                                      std::string root) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config),
       root = std::move(root)](std::stop_token stop) {
        return WithSession<std::string>(
            *transport, config, 30s, stop,
            [root](SshSession &session, std::stop_token token) {
              return Root(session, root, token);
            });
      });
}

huxerui::Task<SshResult<domain::ProjectFileNode>>
SshWorkspaceService::LoadTree(domain::SshConfig config, std::string root) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config),
       root = std::move(root)](std::stop_token stop) {
        return WithSession<domain::ProjectFileNode>(
            *transport, config, 120s, stop,
            [root](SshSession &session, std::stop_token token) {
              auto resolved = Root(session, root, token);
              if (!resolved)
                return SshResult<domain::ProjectFileNode>{
                    std::unexpected(std::move(resolved.error()))};
              std::size_t nodes{};
              return LoadNode(session, std::move(*resolved), 0, nodes, token);
            });
      });
}

huxerui::Task<SshResult<std::string>> SshWorkspaceService::ReadText(
    domain::SshConfig config, std::string root, std::string relative_path,
    std::size_t maximum_bytes) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path), maximum_bytes](std::stop_token stop) {
        return WithSession<std::string>(
            *transport, config, 120s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto path = Resolve(session, root, relative_path, false, false, token);
              if (!path)
                return SshResult<std::string>{
                    std::unexpected(std::move(path.error()))};
              auto bytes = session.Read(*path, maximum_bytes, token);
              if (!bytes)
                return SshResult<std::string>{
                    std::unexpected(std::move(bytes.error()))};
              if (!ValidUtf8(*bytes))
                return SshResult<std::string>{std::unexpected(Error(
                    SshErrorCode::io, "SSH workspace file is not valid UTF-8"))};
              return SshResult<std::string>{std::string{
                  reinterpret_cast<const char *>(bytes->data()), bytes->size()}};
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::WriteText(
    domain::SshConfig config, std::string root, std::string relative_path,
    std::string value) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path), value = std::move(value)](
          std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 120s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto path = Resolve(session, root, relative_path, true, true, token);
              if (!path)
                return SshResult<void>{std::unexpected(std::move(path.error()))};
              const auto stage = Join(
                  Parent(*path), ".linecode-write-stage-" +
                                     std::to_string(next_stage.fetch_add(
                                         1, std::memory_order_relaxed)));
              const auto bytes = std::as_bytes(std::span{value});
              auto written = session.Write(stage, bytes, false, token);
              if (!written)
                return written;
              auto renamed = session.Rename(stage, *path, true, token);
              if (!renamed)
                static_cast<void>(session.RemoveFile(stage, token));
              return renamed;
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::CreateFile(
    domain::SshConfig config, std::string root, std::string relative_path) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path)](std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 30s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto path = Resolve(session, root, relative_path, true, true, token);
              if (!path)
                return SshResult<void>{std::unexpected(std::move(path.error()))};
              if (auto missing = RequireMissing(session, *path, token); !missing)
                return missing;
              return session.Write(*path, {}, false, token);
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::CreateDirectory(
    domain::SshConfig config, std::string root, std::string relative_path) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path)](std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 30s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto path = Resolve(session, root, relative_path, true, true, token);
              if (!path)
                return SshResult<void>{std::unexpected(std::move(path.error()))};
              if (auto missing = RequireMissing(session, *path, token); !missing)
                return missing;
              return session.CreateDirectory(*path, token);
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::Rename(
    domain::SshConfig config, std::string root, std::string relative_path,
    std::string new_name) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path), new_name = std::move(new_name)](
          std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 30s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto name = Segments(new_name);
              if (!name || name->size() != 1U)
                return SshResult<void>{std::unexpected(Error(
                    SshErrorCode::invalid_argument,
                    "SSH workspace name must be one path segment"))};
              if (Protected(*name))
                return SshResult<void>{std::unexpected(Error(
                    SshErrorCode::protected_path,
                    "SSH workspace metadata is protected"))};
              auto source = Resolve(session, root, relative_path, false, true, token);
              if (!source)
                return SshResult<void>{std::unexpected(std::move(source.error()))};
              const auto destination = Join(Parent(*source), new_name);
              if (auto missing = RequireMissing(session, destination, token); !missing)
                return missing;
              return session.Rename(*source, destination, false, token);
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::Copy(
    domain::SshConfig config, std::string root,
    std::string source_relative_path, std::string destination_relative_path) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       source_relative_path = std::move(source_relative_path),
       destination_relative_path = std::move(destination_relative_path)](
          std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 120s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto source = Resolve(session, root, source_relative_path, false,
                                    true, token);
              if (!source)
                return SshResult<void>{std::unexpected(std::move(source.error()))};
              auto destination = Resolve(session, root, destination_relative_path,
                                         true, true, token);
              if (!destination)
                return SshResult<void>{
                    std::unexpected(std::move(destination.error()))};
              if (destination->starts_with(*source + "/"))
                return SshResult<void>{std::unexpected(Error(
                    SshErrorCode::conflict,
                    "SSH directory cannot be copied into itself"))};
              if (auto missing = RequireMissing(session, *destination, token);
                  !missing)
                return missing;
              std::size_t nodes{};
              auto copied = CopyEntry(session, *source, *destination, 0, nodes, token);
              if (!copied) {
                std::size_t cleanup_nodes{};
                static_cast<void>(DeleteEntry(session, *destination, 0,
                                              cleanup_nodes, token));
              }
              return copied;
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::Move(
    domain::SshConfig config, std::string root,
    std::string source_relative_path, std::string destination_relative_path) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       source_relative_path = std::move(source_relative_path),
       destination_relative_path = std::move(destination_relative_path)](
          std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 30s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto source = Resolve(session, root, source_relative_path, false,
                                    true, token);
              if (!source)
                return SshResult<void>{std::unexpected(std::move(source.error()))};
              auto destination = Resolve(session, root, destination_relative_path,
                                         true, true, token);
              if (!destination)
                return SshResult<void>{
                    std::unexpected(std::move(destination.error()))};
              if (destination->starts_with(*source + "/"))
                return SshResult<void>{std::unexpected(Error(
                    SshErrorCode::conflict,
                    "SSH directory cannot be moved into itself"))};
              if (auto missing = RequireMissing(session, *destination, token);
                  !missing)
                return missing;
              return session.Rename(*source, *destination, false, token);
            });
      });
}

huxerui::Task<SshResult<void>> SshWorkspaceService::Delete(
    domain::SshConfig config, std::string root, std::string relative_path) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config), root = std::move(root),
       relative_path = std::move(relative_path)](std::stop_token stop) {
        return WithSession<void>(
            *transport, config, 120s, stop,
            [&](SshSession &session, std::stop_token token) {
              auto path = Resolve(session, root, relative_path, false, true, token);
              if (!path)
                return SshResult<void>{std::unexpected(std::move(path.error()))};
              std::size_t nodes{};
              return DeleteEntry(session, *path, 0, nodes, token);
            });
      });
}

huxerui::Task<SshResult<std::string>> SshWorkspaceService::CreateManagedProject(
    domain::SshConfig config, std::string name) {
  co_return co_await operations_.Run(
      [transport = transport_, config = std::move(config),
       name = std::move(name)](std::stop_token stop) {
        return WithSession<std::string>(
            *transport, config, 30s, stop,
            [&](SshSession &session, std::stop_token token) {
              const auto clean = ManagedName(name);
              if (clean.empty())
                return SshResult<std::string>{std::unexpected(Error(
                    SshErrorCode::invalid_argument,
                    "SSH managed project name is empty"))};
              auto home = Root(session, "~", token);
              if (!home)
                return SshResult<std::string>{
                    std::unexpected(std::move(home.error()))};
              const auto metadata = Join(*home, ".linecode");
              const auto projects = Join(metadata, "project");
              const auto project = Join(projects, clean);
              const auto skills = Join(Join(project, ".linecode"), "skills");
              for (const auto &path : {metadata, projects, project,
                                       Join(project, ".linecode"), skills}) {
                if (auto ensured = EnsureDirectory(session, path, token); !ensured)
                  return SshResult<std::string>{
                      std::unexpected(std::move(ensured.error()))};
              }
              return SshResult<std::string>{project};
            });
      });
}

} // namespace linecode::application
