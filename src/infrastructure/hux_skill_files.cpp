#include "infrastructure/hux_skill_files.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <concepts>
#include <cctype>
#include <filesystem>
#include <functional>
#include <stdexcept>
#include <string_view>
#include <unordered_set>
#include <utility>

#include "infrastructure/linecode_zip.h"

namespace linecode::infrastructure {
namespace {

using application::SkillError;
template <class Value>
using Result = application::SkillResult<Value>;

constexpr int kMaximumScanDepth = 4;
constexpr std::size_t kMaximumSkillMarkdownBytes = 512U * 1024U;
constexpr std::size_t kMaximumPackageFiles = 512;
constexpr std::size_t kMaximumPackageFileBytes = 8U * 1024U * 1024U;
constexpr std::size_t kMaximumPackageTotalBytes = 40U * 1024U * 1024U;

class SystemSkillClock final : public application::SkillClock {
public:
  std::int64_t NowMilliseconds() const noexcept override {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
  }
};

SkillError Error(std::string message) {
  return {.message = std::move(message)};
}

Result<huxerui::File> RootFor(const application::SkillRoots &roots,
                              const domain::SkillLocation location) {
  if (location == domain::SkillLocation::ssh)
    return std::unexpected(
        Error("SSH mode cannot write the local Skill directory"));
  if (location == domain::SkillLocation::project) {
    if (!roots.project)
      return std::unexpected(
          Error("The current workspace path is empty; project Skills are unavailable"));
    return roots.project->Resolve(".linecode/skills");
  }
  return roots.app;
}

std::int64_t ModifiedMilliseconds(const huxerui::File &file,
                                  const std::int64_t fallback) {
  const auto stat = file.Stat();
  if (!stat.Succeeded() || !stat.Value().modified_at)
    return fallback;
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             stat.Value().modified_at->time_since_epoch())
      .count();
}

Result<std::string> ReadMarkdown(const huxerui::File &file) {
  const auto stat = file.Stat();
  if (!stat.Succeeded())
    return std::unexpected(Error(stat.Error().message));
  if (stat.Value().type != huxerui::FileType::File)
    return std::unexpected(Error("SKILL.md is not an ordinary file"));
  if (stat.Value().size > kMaximumSkillMarkdownBytes)
    return std::unexpected(Error("SKILL.md exceeds the 512 KiB safety limit"));
  auto text = file.ReadString();
  if (!text.Succeeded())
    return std::unexpected(Error(text.Error().message));
  return std::move(text).Value();
}

std::string SkillId(const domain::SkillLocation location,
                    const std::string_view path) {
  std::string id{domain::SerializeSkillLocation(location)};
  id.push_back(':');
  for (const unsigned char c : path) {
    if (std::isalnum(c) != 0 || c == '_' || c == ':' || c == '/' ||
        c == '.' || c == '-')
      id.push_back(static_cast<char>(c));
    else
      id.push_back('_');
  }
  return id;
}

std::string AsciiLower(std::string value) {
  std::ranges::transform(value, value.begin(), [](const unsigned char c) {
    return static_cast<char>(std::tolower(c));
  });
  return value;
}

bool IsSymbolicLink(const huxerui::File &file);

Result<domain::SkillRecord>
RecordFrom(const huxerui::File &directory, const huxerui::File &markdown,
           const domain::SkillLocation location, const std::int64_t now) {
  auto text = ReadMarkdown(markdown);
  if (!text)
    return std::unexpected(std::move(text.error()));
  const auto metadata = domain::ParseSkillMetadata(*text, directory.Name());
  const auto updated = ModifiedMilliseconds(markdown, now);
  return domain::SkillRecord{
      .id = SkillId(location, markdown.Path()),
      .name = metadata.name,
      .description = metadata.description,
      .root_path = directory.Path(),
      .skill_markdown_path = markdown.Path(),
      .location = location,
      .enabled = true,
      .discovered_at = updated,
      .updated_at = updated,
  };
}

Result<void> Scan(const huxerui::File &directory,
                  const domain::SkillLocation location,
                  std::vector<domain::SkillRecord> &found, const int depth,
                  const std::int64_t now) {
  if (depth > kMaximumScanDepth || !directory.IsDirectory())
    return {};
  const auto direct = directory.Child("SKILL.md");
  if (direct.IsFile()) {
    auto record = RecordFrom(directory, direct, location, now);
    if (!record)
      return std::unexpected(std::move(record.error()));
    found.push_back(std::move(*record));
    return {};
  }
  auto children = directory.ListChildren();
  if (!children.Succeeded())
    return std::unexpected(Error(children.Error().message));
  auto values = std::move(children).Value();
  std::ranges::sort(values, {}, [](const huxerui::File &file) {
    return file.Path();
  });
  for (const auto &child : values) {
    if (!child.IsDirectory() || IsSymbolicLink(child))
      continue;
    auto scanned = Scan(child, location, found, depth + 1, now);
    if (!scanned)
      return scanned;
  }
  return {};
}

huxerui::File UniqueDirectory(const huxerui::File &root,
                              const std::string_view raw_name,
                              const std::int64_t now) {
  const auto name = domain::SanitizeSkillDirectoryName(raw_name, now);
  auto candidate = root.Child(name);
  if (!candidate.Exists())
    return candidate;
  for (std::uint32_t suffix = 1; suffix < 10'000; ++suffix) {
    candidate = root.Child(name + "_" + std::to_string(now) + "_" +
                           std::to_string(suffix));
    if (!candidate.Exists())
      return candidate;
  }
  throw std::runtime_error("cannot allocate a unique Skill directory");
}

bool IsContained(const std::filesystem::path &root,
                 const std::filesystem::path &candidate) {
  std::error_code error;
  const auto canonical_root = std::filesystem::weakly_canonical(root, error);
  if (error)
    return false;
  const auto canonical_candidate =
      std::filesystem::weakly_canonical(candidate, error);
  if (error)
    return false;
  const auto mismatch = std::ranges::mismatch(canonical_root,
                                               canonical_candidate);
  return mismatch.in1 == canonical_root.end() &&
         canonical_candidate != canonical_root;
}

bool IsSymbolicLink(const huxerui::File &file) {
  std::error_code error;
  const auto status = std::filesystem::symlink_status(file.Path(), error);
  return !error && std::filesystem::is_symlink(status);
}

Result<void> CopyDirectory(const huxerui::File &source,
                           const huxerui::File &destination,
                           std::size_t &files, std::size_t &total_bytes) {
  if (!destination.CreateDirectories())
    return std::unexpected(Error("cannot create Skill destination directory"));
  auto children = source.ListChildren();
  if (!children.Succeeded())
    return std::unexpected(Error(children.Error().message));
  for (const auto &child : children.Value()) {
    const std::filesystem::path child_path{child.Path()};
    std::error_code status_error;
    const auto status = std::filesystem::symlink_status(child_path, status_error);
    if (status_error || std::filesystem::is_symlink(status))
      return std::unexpected(Error("Skill directories cannot contain symbolic links"));
    if (!IsContained(std::filesystem::path{source.Path()}, child_path))
      return std::unexpected(Error("Skill source escapes its directory"));
    const auto target = destination.Child(child.Name());
    if (child.IsDirectory()) {
      auto copied = CopyDirectory(child, target, files, total_bytes);
      if (!copied)
        return copied;
      continue;
    }
    const auto stat = child.Stat();
    if (!stat.Succeeded() || stat.Value().type != huxerui::FileType::File)
      return std::unexpected(Error("Skill source contains an unsupported entry"));
    if (++files > kMaximumPackageFiles ||
        stat.Value().size > kMaximumPackageFileBytes ||
        stat.Value().size > kMaximumPackageTotalBytes - total_bytes)
      return std::unexpected(Error("Skill package exceeds safety limits"));
    total_bytes += stat.Value().size;
    if (!child.CopyTo(target))
      return std::unexpected(Error("cannot copy Skill file: " + child.Name()));
  }
  return {};
}

Result<huxerui::File> FindSkillMarkdown(const huxerui::File &path,
                                       const int depth = 0) {
  if (depth > kMaximumScanDepth)
    return std::unexpected(Error("installed package does not contain SKILL.md"));
  if (path.IsFile()) {
    if (domain::IsSkillMarkdownName(path.Name()))
      return path;
    return std::unexpected(Error("installed package does not contain SKILL.md"));
  }
  const auto direct = path.Child("SKILL.md");
  if (direct.IsFile())
    return direct;
  auto children = path.ListChildren();
  if (!children.Succeeded())
    return std::unexpected(Error(children.Error().message));
  auto values = std::move(children).Value();
  std::ranges::sort(values, {}, [](const huxerui::File &file) {
    return file.Path();
  });
  for (const auto &child : values) {
    if (!child.IsDirectory())
      continue;
    auto nested = FindSkillMarkdown(child, depth + 1);
    if (nested)
      return nested;
  }
  return std::unexpected(Error("installed package does not contain SKILL.md"));
}

std::string CommonArchiveRoot(
    const std::vector<ZipEntryData> &entries) {
  std::string root;
  for (const auto &entry : entries) {
    const auto slash = entry.name.find('/');
    if (slash == std::string::npos)
      return {};
    const auto candidate = entry.name.substr(0, slash);
    if (candidate.empty())
      return {};
    if (root.empty())
      root = candidate;
    else if (root != candidate)
      return {};
  }
  return root.empty() ? std::string{} : root + "/";
}

Result<void> ExtractZip(const application::SkillZipPackage &package,
                        const huxerui::File &destination) {
  auto decoded = ReadLineCodeZip(package.archive);
  if (!decoded)
    return std::unexpected(Error(decoded.error().message));
  if (decoded->empty() || decoded->size() > kMaximumPackageFiles)
    return std::unexpected(Error("Skill ZIP is empty or contains too many files"));
  const auto prefix = package.strip_common_root ? CommonArchiveRoot(*decoded)
                                                 : std::string{};
  std::size_t total{};
  std::unordered_set<std::string> output_names;
  bool has_markdown{};
  for (const auto &entry : *decoded) {
    if (entry.content.size() > kMaximumPackageFileBytes ||
        entry.content.size() > kMaximumPackageTotalBytes - total)
      return std::unexpected(Error("Skill ZIP expanded size exceeds safety limits"));
    total += entry.content.size();
    auto relative = prefix.empty() ? entry.name : entry.name.substr(prefix.size());
    if (relative.empty() || !IsSafeArchivePath(relative) ||
        !output_names.insert(relative).second)
      return std::unexpected(Error("Skill ZIP contains an unsafe path"));
    has_markdown = has_markdown ||
                   domain::IsSkillMarkdownName(
                       relative.substr(relative.find_last_of('/') + 1));
  }
  if (!has_markdown)
    return std::unexpected(Error("Skill ZIP does not contain SKILL.md"));
  if (!destination.CreateDirectories())
    return std::unexpected(Error("cannot create Skill destination directory"));
  for (const auto &entry : *decoded) {
    auto relative = prefix.empty() ? entry.name : entry.name.substr(prefix.size());
    auto output = destination.Resolve(relative);
    const auto parent = output.Parent();
    if (!parent || !parent->CreateDirectories() ||
        !output.WriteBytes(entry.content))
      return std::unexpected(Error("cannot extract Skill ZIP entry: " + relative));
  }
  return {};
}

Result<void> EnsureBuiltinSkills(const huxerui::File &root) {
  if (!root.CreateDirectories())
    return std::unexpected(Error("cannot create the application Skill root"));
  constexpr std::array builtins{
      std::pair{
          std::string_view{"skill-creator"},
          std::string_view{
              "---\nname: skill-creator\ndescription: 创建和维护 LineCode "
              "SKILL.md 技能。\n---\n\n# Skill Creator\n\n"
              "当用户要求沉淀流程、复用经验或创建新 Skill 时使用。\n\n"
              "## 步骤\n- Skills 的创建属于扩展系统，不是可调用 Tool；优先通过扩展页创建，学习模式也可以自动沉淀。\n"
              "- 明确触发条件、适用范围、输入、输出和验证方式。\n"
              "- 需要维护已授权 Skills 目录时，只使用普通文件读写、搜索和列目录工具操作 `SKILL.md`。\n"
              "- `SKILL.md` 应包含 name、description、触发条件、步骤、常见坑和验证方式。\n"
              "- 不要写入 API key、token、密码或一次性任务进度。\n"}},
      std::pair{
          std::string_view{"skill-installer"},
          std::string_view{
              "---\nname: skill-installer\ndescription: 安装本地目录、SKILL.md 或 ZIP 技能包。\n---\n\n"
              "# Skill Installer\n\n当用户提供技能包路径，或需要把当前工作区的技能安装到全局/项目 Skills 目录时使用。\n\n"
              "## 步骤\n- Skills 的安装属于扩展系统，不是可调用 Tool；优先通过扩展页安装本地目录、`SKILL.md` 或 `.zip`。\n"
              "- `location=app` 安装到应用私有全局 Skills 目录。\n"
              "- `location=project` 安装到当前工作区 `.linecode/skills`。\n"
              "- SSH 模式的目标路径是 `~/.linecode/skills`，可通过 SSH Shell 操作。\n"
              "- 安装后检查 `SKILL.md` 可读，并确认扩展页列表中已启用。\n"}},
  };
  for (const auto &[name, markdown] : builtins) {
    const auto directory = root.Child(name);
    const auto file = directory.Child("SKILL.md");
    if (IsSymbolicLink(directory) || IsSymbolicLink(file))
      return std::unexpected(
          Error("built-in Skill path cannot be a symbolic link"));
    if (file.Exists()) {
      const auto existing = file.ReadString();
      if (!existing.Succeeded() ||
          (!existing.Value().contains("skill_create") &&
           !existing.Value().contains("skill_install")))
        continue;
    }
    if (!directory.CreateDirectories() || !file.WriteString(markdown))
      return std::unexpected(Error("cannot install built-in Skill: " +
                                   std::string{name}));
  }
  return {};
}

} // namespace

HuxSkillFiles::HuxSkillFiles()
    : HuxSkillFiles(std::make_shared<SystemSkillClock>()) {}

HuxSkillFiles::HuxSkillFiles(
    std::shared_ptr<application::SkillClock> clock)
    : clock_(std::move(clock)) {
  if (!clock_)
    throw std::invalid_argument("HuxSkillFiles requires a clock");
}

Result<std::vector<domain::SkillRecord>>
HuxSkillFiles::Discover(const application::SkillRoots &roots) const {
  std::vector<domain::SkillRecord> found;
  const auto now = clock_->NowMilliseconds();
  auto builtins = EnsureBuiltinSkills(roots.app);
  if (!builtins)
    return std::unexpected(std::move(builtins.error()));
  auto scanned = Scan(roots.app, domain::SkillLocation::app, found, 0, now);
  if (!scanned)
    return std::unexpected(std::move(scanned.error()));
  if (roots.project) {
    const auto root = roots.project->Resolve(".linecode/skills");
    if (!root.CreateDirectories())
      return std::unexpected(Error("cannot create the project Skill root"));
    scanned = Scan(root, domain::SkillLocation::project, found, 0, now);
    if (!scanned)
      return std::unexpected(std::move(scanned.error()));
  }
  std::ranges::sort(found, {}, [](const domain::SkillRecord &skill) {
    return AsciiLower(skill.name);
  });
  return found;
}

Result<domain::SkillRecord>
HuxSkillFiles::Create(const application::SkillRoots &roots,
                      const domain::SkillLocation location, std::string name,
                      std::string description,
                      std::string markdown_body) const {
  const auto now = clock_->NowMilliseconds();
  if (name.empty())
    name = "linecode-skill-" + std::to_string(now);
  auto markdown = domain::BuildSkillMarkdown(name, description, markdown_body);
  if (!markdown)
    return std::unexpected(Error(markdown.error().message));
  auto root = RootFor(roots, location);
  if (!root)
    return std::unexpected(std::move(root.error()));
  if (!root->CreateDirectories())
    return std::unexpected(Error("cannot create the Skill root directory"));
  const auto directory = UniqueDirectory(*root, name, now);
  if (!directory.CreateDirectories())
    return std::unexpected(Error("cannot create the Skill directory"));
  const auto skill_file = directory.Child("SKILL.md");
  if (!skill_file.WriteString(*markdown)) {
    static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(Error("cannot write SKILL.md"));
  }
  return RecordFrom(directory, skill_file, location, now);
}

Result<domain::SkillRecord>
HuxSkillFiles::Install(application::SkillInstallRequest request) const {
  const auto now = clock_->NowMilliseconds();
  auto root = RootFor(request.roots, request.location);
  if (!root)
    return std::unexpected(std::move(root.error()));
  if (!root->CreateDirectories())
    return std::unexpected(Error("cannot create the Skill root directory"));
  const auto directory = UniqueDirectory(*root, request.name, now);
  Result<void> installed = std::visit(
      [&](auto &&package) -> Result<void> {
        using Package = std::remove_cvref_t<decltype(package)>;
        if constexpr (std::same_as<Package, application::SkillDirectoryPackage>) {
          if (!package.directory.IsDirectory())
            return std::unexpected(Error("Skill source directory was not found"));
          if (IsSymbolicLink(package.directory))
            return std::unexpected(
                Error("Skill source directory cannot be a symbolic link"));
          if (IsContained(std::filesystem::path{package.directory.Path()},
                          std::filesystem::path{directory.Path()}))
            return std::unexpected(
                Error("Skill destination cannot be inside its source directory"));
          std::size_t files{};
          std::size_t total{};
          return CopyDirectory(package.directory, directory, files, total);
        } else if constexpr (std::same_as<Package,
                                                 application::SkillMarkdownPackage>) {
          if (package.markdown.empty())
            return std::unexpected(Error("SKILL.md is empty"));
          if (!directory.CreateDirectories() ||
              !directory.Child("SKILL.md").WriteString(package.markdown))
            return std::unexpected(Error("cannot install SKILL.md"));
          return {};
        } else {
          return ExtractZip(package, directory);
        }
      },
      request.package);
  if (!installed) {
    static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(std::move(installed.error()));
  }
  auto skill_markdown = FindSkillMarkdown(directory);
  if (!skill_markdown) {
    static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(std::move(skill_markdown.error()));
  }
  const auto parent = skill_markdown->Parent();
  if (!parent) {
    static_cast<void>(directory.DeleteRecursively());
    return std::unexpected(Error("installed SKILL.md has no parent directory"));
  }
  auto record = RecordFrom(*parent, *skill_markdown, request.location, now);
  if (!record)
    static_cast<void>(directory.DeleteRecursively());
  return record;
}

Result<void> HuxSkillFiles::Delete(const application::SkillRoots &roots,
                                   const domain::SkillRecord &skill) const {
  if (skill.location == domain::SkillLocation::ssh)
    return std::unexpected(Error("SSH Skills cannot be deleted locally"));
  auto root = RootFor(roots, skill.location);
  if (!root)
    return std::unexpected(std::move(root.error()));
  if (!IsContained(std::filesystem::path{root->Path()},
                   std::filesystem::path{skill.root_path}))
    return std::unexpected(Error("Skill path is outside its configured root"));
  const huxerui::File target{skill.root_path};
  if (!target.Exists())
    return {};
  if (!target.IsDirectory() || IsSymbolicLink(target))
    return std::unexpected(Error("Skill path is not a removable directory"));
  if (!target.DeleteRecursively())
    return std::unexpected(Error("cannot delete the Skill directory"));
  return {};
}

Result<std::string>
HuxSkillFiles::ReadPrompt(const domain::SkillRecord &skill,
                          const std::size_t maximum_characters) const {
  if (skill.location == domain::SkillLocation::ssh)
    return std::string{};
  const huxerui::File file{skill.skill_markdown_path};
  if (!file.IsFile())
    return std::string{};
  auto content = ReadMarkdown(file);
  if (!content)
    return std::string{};
  if (content->size() > maximum_characters)
    content->resize(maximum_characters);
  return content;
}

} // namespace linecode::infrastructure
