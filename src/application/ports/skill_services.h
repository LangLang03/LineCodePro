#pragma once

#include <cstddef>
#include <expected>
#include <memory>
#include <optional>
#include <string>
#include <variant>
#include <vector>

#include <huxerui/data.h>
#include <huxerui/file.h>
#include <huxerui/task.h>

#include "domain/skill.h"
#include "domain/skill_hub.h"

namespace linecode::application {

struct SkillError final {
  std::string message;

  bool operator==(const SkillError &) const = default;
};

template <class Value>
using SkillResult = std::expected<Value, SkillError>;

struct SkillRoots final {
  huxerui::File app;
  std::optional<huxerui::File> project;
};

struct SkillDirectoryPackage final {
  huxerui::File directory;
};

struct SkillMarkdownPackage final {
  std::string markdown;
};

struct SkillZipPackage final {
  huxerui::Bytes archive;
  bool strip_common_root{};
};

using SkillPackage =
    std::variant<SkillDirectoryPackage, SkillMarkdownPackage, SkillZipPackage>;

struct SkillInstallRequest final {
  SkillRoots roots;
  domain::SkillLocation location{domain::SkillLocation::app};
  std::string name;
  SkillPackage package;
};

class SkillFiles {
public:
  virtual ~SkillFiles() = default;

  [[nodiscard]] virtual SkillResult<std::vector<domain::SkillRecord>>
  Discover(const SkillRoots &roots) const = 0;
  [[nodiscard]] virtual SkillResult<domain::SkillRecord>
  Create(const SkillRoots &roots, domain::SkillLocation location,
         std::string name, std::string description,
         std::string markdown_body) const = 0;
  [[nodiscard]] virtual SkillResult<domain::SkillRecord>
  Install(SkillInstallRequest request) const = 0;
  [[nodiscard]] virtual SkillResult<void>
  Delete(const SkillRoots &roots, const domain::SkillRecord &skill) const = 0;
  [[nodiscard]] virtual SkillResult<std::string>
  ReadPrompt(const domain::SkillRecord &skill,
             std::size_t maximum_characters = 6000) const = 0;
};

class SkillClock {
public:
  virtual ~SkillClock() = default;
  [[nodiscard]] virtual std::int64_t NowMilliseconds() const noexcept = 0;
};

class SkillRecordStore {
public:
  virtual ~SkillRecordStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      SkillResult<std::vector<domain::SkillRecord>>>
  List() = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  UpsertDiscovered(std::vector<domain::SkillRecord> skills) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  SetEnabled(std::string id, bool enabled) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  Delete(std::vector<std::string> ids) = 0;
};

/// Application-facing boundary used by the extensions UI.  Keeping this
/// interface separate from filesystem/SQLite implementations lets the page
/// exercise the same workflow with an in-memory fake in tests.
class SkillExtensionStore {
public:
  virtual ~SkillExtensionStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      SkillResult<std::vector<domain::SkillRecord>>>
  List(SkillRoots roots) const = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillRecord>>
  Create(SkillRoots roots, domain::SkillLocation location, std::string name,
         std::string description, std::string markdown_body) const = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillRecord>>
  Install(SkillInstallRequest request) const = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  SetEnabled(std::string id, bool enabled) const = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  Delete(SkillRoots roots, std::string id) const = 0;
};

struct GitHubSkillSource final {
  std::string suggested_name;
  std::string primary_url;
  std::optional<std::string> fallback_url;
  bool markdown{};

  bool operator==(const GitHubSkillSource &) const = default;
};

class SkillPackageGateway {
public:
  virtual ~SkillPackageGateway() = default;

  [[nodiscard]] virtual huxerui::Task<SkillResult<SkillInstallRequest>>
  FetchGitHub(SkillRoots roots, domain::SkillLocation location,
              std::string url) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<SkillInstallRequest>>
  FetchSkillHub(SkillRoots roots, domain::SkillLocation location,
                std::string slug, std::string version) = 0;
};

/// Source-oriented install boundary.  The UI chooses a source and location;
/// download, validation and final installation remain application concerns.
class SkillSourceInstaller {
public:
  virtual ~SkillSourceInstaller() = default;

  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillRecord>>
  InstallGitHub(SkillRoots roots, domain::SkillLocation location,
                std::string url) const = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillRecord>>
  InstallSkillHub(SkillRoots roots, domain::SkillLocation location,
                  std::string slug, std::string version) const = 0;
};

struct SkillHubListQuery final {
  int page{1};
  int page_size{20};
  std::string keyword;
  std::string category;
  std::string source{"all"};
  std::string sort_by;
  std::string order;
};

class SkillHubCatalog {
public:
  virtual ~SkillHubCatalog() = default;

  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillHubPage>>
  List(SkillHubListQuery query) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillHubDetail>>
  Detail(std::string slug) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<std::string>>
  FileContent(std::string slug, std::string version, std::string path) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<huxerui::Bytes>>
  Download(std::string slug, std::string version) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<huxerui::Bytes>>
  Icon(std::string url) = 0;
  [[nodiscard]] virtual huxerui::Task<
      SkillResult<std::vector<domain::SkillHubComment>>>
  CommentReplies(std::string slug, std::int64_t comment_id,
                 std::string name_space) = 0;
};

struct SkillHubPublishRequest final {
  domain::SkillRecord skill;
  std::string slug;
  std::string display_name;
  std::string version;
};

class SkillHubSessionGateway {
public:
  virtual ~SkillHubSessionGateway() = default;

  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillHubSession>>
  CurrentSession(std::string cookie) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  Publish(std::string cookie, SkillHubPublishRequest request) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<domain::SkillHubComment>>
  PostComment(std::string cookie, std::string slug, std::string name_space,
              std::string content, std::optional<std::int64_t> reply_to) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  SetCommentLiked(std::string cookie, std::string slug,
                  std::int64_t comment_id, std::string name_space,
                  bool liked) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  DeleteComment(std::string cookie, std::string slug,
                std::int64_t comment_id, std::string name_space) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<bool>>
  Starred(std::string cookie, std::string slug, std::string name_space) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  SetStarred(std::string cookie, std::string slug, std::string name_space,
             bool starred) = 0;
  [[nodiscard]] virtual huxerui::Task<SkillResult<void>>
  Logout(std::string cookie) = 0;
};

} // namespace linecode::application
