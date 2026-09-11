#include <algorithm>
#include <cassert>
#include <cctype>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <filesystem>
#include <fstream>
#include <map>
#include <memory>
#include <optional>
#include <ranges>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/file_tool_registry.h"
#include "application/ports/tool_file_access.h"
#include "application/tool_text_catalog.h"
#include "infrastructure/archive_json.h"

namespace {

using namespace linecode;

namespace json = infrastructure::archive_json;

// Verbatim legacy getDescription() strings of the six built-in file tools.
constexpr std::string_view kFileReadDescription =
    "Read file contents. Returns line-numbered content; for large files, read "
    "in segments via start_kb/end_kb (end_kb may exceed 50, up to the file "
    "size). Returns a directory tree when reading a directory.";
constexpr std::string_view kFileWriteDescription =
    "Write content to a file. Automatically creates the file or directory if "
    "it does not exist.";
constexpr std::string_view kFileEditDescription =
    "Edit file contents. Search and replace via old_string/new_string.";
constexpr std::string_view kFileDeleteDescription =
    "Delete a file or directory. A deletion reason is required; user "
    "confirmation is requested before execution.";
constexpr std::string_view kGlobDescription =
    "Search for matching files. Supports * ** ? wildcards.";
constexpr std::string_view kListDirectoryDescription =
    "List the immediate files and folders under a directory.";

// Verbatim legacy getParameters() schemas of the same six tools.
constexpr std::string_view kFileReadSchema =
    R"({"properties":{"end_kb":{"description":"End position in KB, default 50; may exceed 50, clamped to the file size","type":"number"},"file_path":{"description":"Absolute or relative file path","type":"string"},"start_kb":{"description":"Start position in KB, default 0","type":"number"}},"required":["file_path"],"type":"object"})";
constexpr std::string_view kFileWriteSchema =
    R"({"properties":{"content":{"description":"Content to write","type":"string"},"file_path":{"description":"Absolute or relative file path","type":"string"}},"required":["content","file_path"],"type":"object"})";
constexpr std::string_view kFileEditSchema =
    R"({"properties":{"file_path":{"description":"Absolute or relative file path","type":"string"},"new_string":{"description":"Replacement text","type":"string"},"old_string":{"description":"Original text to search for; must be unique unless replace_all is true","type":"string"},"replace_all":{"description":"If true, replace every occurrence of old_string. Default false: require a unique match and replace only once.","type":"boolean"}},"required":["file_path","new_string","old_string"],"type":"object"})";
constexpr std::string_view kFileDeleteSchema =
    R"({"properties":{"paths":{"description":"List of file or directory paths to delete","items":{"type":"string"},"type":"array"},"reason":{"description":"Deletion reason, shown to the user for confirmation","type":"string"}},"required":["paths","reason"],"type":"object"})";
constexpr std::string_view kGlobSchema =
    R"({"properties":{"path":{"description":"Search root directory, optional, defaults to the home directory","type":"string"},"pattern":{"description":"File match pattern, e.g. *.java, app/src/**/*.java","type":"string"}},"required":["pattern"],"type":"object"})";
constexpr std::string_view kListDirectorySchema =
    R"({"properties":{"path":{"description":"Absolute or relative directory path, optional, defaults to the home directory","type":"string"}},"type":"object"})";

// The packaged strings the catalog must copy verbatim.
constexpr std::string_view kEnglishPropertiesFile = "default.properties";
constexpr std::string_view kChinesePropertiesFile = "zh.properties";

std::string_view TrimView(std::string_view value) {
  std::size_t begin = 0;
  std::size_t end = value.size();
  while (begin < end && static_cast<unsigned char>(value[begin]) <= ' ')
    ++begin;
  while (end > begin && static_cast<unsigned char>(value[end - 1]) <= ' ')
    --end;
  return value.substr(begin, end - begin);
}

// The HuxerUI resource compiler keeps inner quotes and {{ / }} verbatim, strips
// the surrounding quotes and expands the C-style escapes the properties files
// use.
std::string UnescapeProperty(std::string_view value) {
  if (value.size() >= 2U && value.front() == '"' && value.back() == '"')
    value = value.substr(1, value.size() - 2U);
  std::string unescaped;
  unescaped.reserve(value.size());
  for (std::size_t index = 0; index < value.size(); ++index) {
    const char character = value[index];
    if (character != '\\' || index + 1U >= value.size()) {
      unescaped += character;
      continue;
    }
    switch (const char escaped = value[++index]) {
    case 'n':
      unescaped += '\n';
      break;
    case 't':
      unescaped += '\t';
      break;
    case 'r':
      unescaped += '\r';
      break;
    case 'b':
      unescaped += '\b';
      break;
    case 'f':
      unescaped += '\f';
      break;
    default:
      unescaped += escaped;
      break;
    }
  }
  return unescaped;
}

std::map<std::string, std::string, std::less<>>
ParseProperties(std::string_view text) {
  std::map<std::string, std::string, std::less<>> entries;
  std::size_t begin = 0;
  while (begin < text.size()) {
    auto end = text.find('\n', begin);
    if (end == std::string_view::npos)
      end = text.size();
    std::string_view line = TrimView(text.substr(begin, end - begin));
    begin = end + 1;
    if (line.empty() || line.front() == '#' || line.front() == '!')
      continue;
    const auto separator = line.find('=');
    if (separator == std::string_view::npos)
      continue;
    entries[std::string{TrimView(line.substr(0, separator))}] =
        UnescapeProperty(TrimView(line.substr(separator + 1)));
  }
  return entries;
}

std::optional<std::string> ReadFile(const std::filesystem::path &path) {
  std::ifstream stream{path, std::ios::binary};
  if (!stream)
    return std::nullopt;
  std::ostringstream buffer;
  buffer << stream.rdbuf();
  return buffer.str();
}

application::ToolFileError Missing(const std::string &path) {
  return {.code = application::ToolFileErrorCode::not_found,
          .message = "No such file or directory: " + path};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(lowered),
                         [](unsigned char byte) {
                           return static_cast<char>(std::tolower(byte));
                         });
  return lowered;
}

class StubExecutionSettings final
    : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    if (fail_load)
      co_return std::unexpected(application::SettingsStoreError{
          .message = "injected execution-mode settings failure"});
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string id,
                      bool enabled) override {
    const auto found =
        std::ranges::find(value.groups, id, &domain::McpToolGroupState::id);
    if (found != value.groups.end())
      found->enabled = enabled;
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
  bool fail_load{};
};

application::ProjectWorkspaceError Unsupported() {
  return {.code = application::ProjectWorkspaceErrorCode::io,
          .message = "unsupported in this test"};
}

class StubProjectWorkspace final
    : public application::ProjectWorkspaceController {
public:
  huxerui::Task<
      application::ProjectWorkspaceResult<std::vector<domain::ProjectRecord>>>
  ListProjects() override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectedProject() override {
    if (fail)
      co_return std::unexpected(application::ProjectWorkspaceError{
          .code = application::ProjectWorkspaceErrorCode::not_found,
          .message = "injected workspace failure"});
    co_return record;
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  CreateManagedProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  RegisterExternalProject(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  DeleteProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectFileNode>>
  LoadTree(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateFile(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateDirectory(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<std::string>>
  ReadText(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  WriteText(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Rename(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Copy(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Move(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Delete(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  domain::ProjectRecord record;
  bool fail{};
};

// In-memory stand-in for the platform file capability. Entries are keyed by
// workspace-relative path, so the registry's canonical absolute paths stay an
// implementation detail of the path policy under test.
class MemoryFileAccess final : public application::ToolFileAccess {
public:
  struct Entry final {
    application::ToolFileKind kind{application::ToolFileKind::file};
    std::string content;
  };

  void Seed(std::string path, std::string content) {
    entries[std::move(path)] = Entry{.kind = application::ToolFileKind::file,
                                     .content = std::move(content)};
  }

  void SeedDirectory(std::string path) {
    entries[std::move(path)] =
        Entry{.kind = application::ToolFileKind::directory, .content = {}};
  }

  [[nodiscard]] std::string Content(std::string_view path) const {
    const auto found = entries.find(path);
    return found == entries.end() ? std::string{} : found->second.content;
  }

  [[nodiscard]] bool Has(std::string_view path) const {
    return entries.contains(path);
  }

  huxerui::Task<application::ToolFileResult<application::ToolFileInfo>>
  Stat(std::string path) const override {
    if (fail_stat)
      co_return std::unexpected(*fail_stat);
    const auto found = entries.find(Relative(path));
    if (found != entries.end()) {
      co_return application::ToolFileInfo{
          .kind = found->second.kind,
          .size = found->second.kind == application::ToolFileKind::file
                      ? found->second.content.size()
                      : std::uint64_t{0}};
    }
    if (!Children(Relative(path)).empty()) {
      co_return application::ToolFileInfo{
          .kind = application::ToolFileKind::directory, .size = 0};
    }
    co_return std::unexpected(Missing(path));
  }

  huxerui::Task<application::ToolFileResult<std::string>>
  ReadText(std::string path) const override {
    if (fail_read)
      co_return std::unexpected(*fail_read);
    const auto found = entries.find(Relative(path));
    if (found == entries.end() ||
        found->second.kind != application::ToolFileKind::file)
      co_return std::unexpected(Missing(path));
    co_return found->second.content;
  }

  huxerui::Task<application::ToolFileResult<std::string>>
  ReadBytes(std::string path, std::uint64_t offset,
            std::uint64_t length) const override {
    if (fail_read)
      co_return std::unexpected(*fail_read);
    const auto found = entries.find(Relative(path));
    if (found == entries.end() ||
        found->second.kind != application::ToolFileKind::file)
      co_return std::unexpected(Missing(path));
    const std::string &content = found->second.content;
    if (offset >= content.size())
      co_return std::string{};
    co_return content.substr(static_cast<std::size_t>(offset),
                             static_cast<std::size_t>(length));
  }

  huxerui::Task<application::ToolFileResult<void>>
  WriteText(std::string path, std::string content) override {
    if (fail_write)
      co_return std::unexpected(*fail_write);
    auto relative = Relative(path);
    written.push_back(relative);
    entries[relative] = Entry{.kind = application::ToolFileKind::file,
                              .content = std::move(content)};
    co_return application::ToolFileResult<void>{};
  }

  huxerui::Task<application::ToolFileResult<void>>
  CreateDirectories(std::string path) override {
    if (fail_create_directories)
      co_return std::unexpected(*fail_create_directories);
    const std::string relative = Relative(path);
    created_directories.push_back(relative);
    std::string current;
    for (const auto &part : std::views::split(relative, '/')) {
      const std::string name{part.begin(), part.end()};
      current = current.empty() ? name : current + "/" + name;
      entries.try_emplace(
          current,
          Entry{.kind = application::ToolFileKind::directory, .content = {}});
    }
    co_return application::ToolFileResult<void>{};
  }

  huxerui::Task<
      application::ToolFileResult<std::vector<application::ToolFileEntry>>>
  ListDirectory(std::string path) const override {
    if (fail_list)
      co_return std::unexpected(*fail_list);
    co_return Children(Relative(path));
  }

  huxerui::Task<application::ToolFileResult<void>>
  Delete(std::string path) override {
    if (fail_delete)
      co_return std::unexpected(*fail_delete);
    const std::string relative = Relative(path);
    if (!entries.contains(relative) && Children(relative).empty())
      co_return std::unexpected(Missing(path));
    const std::string prefix = relative + "/";
    std::erase_if(entries, [&](const auto &item) {
      return item.first == relative || item.first.starts_with(prefix);
    });
    deleted.push_back(relative);
    co_return application::ToolFileResult<void>{};
  }

  huxerui::Task<application::ToolFileResult<std::vector<std::string>>>
  Glob(std::string root, std::string pattern,
       std::size_t maximum_results) const override {
    if (fail_glob)
      co_return std::unexpected(*fail_glob);
    if (glob_override)
      co_return *glob_override;
    std::vector<std::string> results;
    // Results stay relative to the requested search root.
    const std::string base = Relative(root);
    Collect(base, base, pattern, pattern.find('/') == std::string::npos,
            maximum_results, results);
    co_return results;
  }

  // Canonical workspace root; every absolute path below it is reduced to the
  // relative key used by the maps above.
  std::string root;
  std::map<std::string, Entry, std::less<>> entries;
  std::vector<std::string> written;
  std::vector<std::string> created_directories;
  std::vector<std::string> deleted;
  std::optional<std::vector<std::string>> glob_override;
  std::optional<application::ToolFileError> fail_stat;
  std::optional<application::ToolFileError> fail_read;
  std::optional<application::ToolFileError> fail_write;
  std::optional<application::ToolFileError> fail_create_directories;
  std::optional<application::ToolFileError> fail_list;
  std::optional<application::ToolFileError> fail_delete;
  std::optional<application::ToolFileError> fail_glob;

private:
  [[nodiscard]] std::string Relative(const std::string &path) const {
    if (path == root)
      return ".";
    if (path.starts_with(root + "/"))
      return path.substr(root.size() + 1U);
    return path;
  }

  [[nodiscard]] std::vector<application::ToolFileEntry>
  Children(std::string_view directory) const {
    const std::string prefix =
        directory == "." ? std::string{} : std::string{directory} + "/";
    std::vector<application::ToolFileEntry> children;
    for (const auto &[path, entry] : entries) {
      if (!path.starts_with(prefix))
        continue;
      const std::string_view rest{path.data() + prefix.size(),
                                  path.size() - prefix.size()};
      const auto slash = rest.find('/');
      const std::string name{rest.substr(0, slash)};
      if (name.empty())
        continue;
      const bool is_directory = slash != std::string_view::npos ||
                                entry.kind == application::ToolFileKind::directory;
      if (std::ranges::any_of(children, [&name](const auto &child) {
            return child.name == name;
          })) {
        continue;
      }
      children.push_back(application::ToolFileEntry{
          .name = name,
          .kind = is_directory ? application::ToolFileKind::directory
                               : entry.kind});
    }
    return children;
  }

  // Legacy depth-first glob walk: children sorted case-insensitively, hidden
  // directories and node_modules never traversed. Results are reported relative
  // to `base`, the requested search root.
  void Collect(std::string_view base, std::string_view directory,
               std::string_view pattern, bool bare, std::size_t maximum,
               std::vector<std::string> &results) const {
    if (results.size() >= maximum)
      return;
    auto children = Children(directory);
    std::ranges::sort(children, [](const auto &left, const auto &right) {
      return Lower(left.name) < Lower(right.name);
    });
    for (const auto &child : children) {
      if (results.size() >= maximum)
        return;
      const std::string relative = directory == "."
                                       ? child.name
                                       : std::string{directory} + "/" + child.name;
      const std::string reported =
          base == "." ? relative : relative.substr(base.size() + 1U);
      if (child.kind == application::ToolFileKind::directory) {
        if (!child.name.starts_with('.') && child.name != "node_modules")
          Collect(base, relative, pattern, bare, maximum, results);
        continue;
      }
      const std::string_view candidate =
          bare ? std::string_view{child.name} : std::string_view{reported};
      if (application::GlobMatch(pattern, candidate))
        results.push_back(reported);
    }
  }
};

struct Scenario final {
  Scenario() {
    const auto base = std::filesystem::temp_directory_path() /
                      "linecode-file-tool-registry-tests";
    std::error_code error;
    std::filesystem::remove_all(base, error);
    std::filesystem::create_directories(base, error);
    assert(!error);
    root = std::filesystem::weakly_canonical(base, error).generic_string();
    assert(!error);

    settings = std::make_shared<StubExecutionSettings>();
    workspace = std::make_shared<StubProjectWorkspace>();
    workspace->record.id = "workspace";
    workspace->record.label = "Workspace";
    workspace->record.path = root;
    files = std::make_shared<MemoryFileAccess>();
    files->root = root;
    SeedWorkspace(*files);
    tools = std::make_shared<application::FileToolRegistry>(
        settings, workspace, files);
    chinese_tools = std::make_shared<application::FileToolRegistry>(
        settings, workspace, files, application::ToolTextLanguage::chinese);
  }

  ~Scenario() {
    std::error_code error;
    std::filesystem::remove_all(root, error);
  }

  static void SeedWorkspace(MemoryFileAccess &files) {
    files.Seed("notes.txt", "alpha\nbeta\ngamma\n");
    // 60 KB on 60 whole lines, above the 50 KB single-read limit.
    std::string large;
    for (int line = 0; line < 60; ++line) {
      large.append(1023, 'x');
      large.push_back('\n');
    }
    files.Seed("large.txt", std::move(large));
    // 2 KB on two whole lines, so one KB page covers exactly one line.
    std::string segment;
    segment.append(1023, 'a');
    segment.push_back('\n');
    segment.append(1023, 'b');
    segment.push_back('\n');
    files.Seed("segment.txt", std::move(segment));
    files.Seed("edit.txt", "hello world");
    files.Seed("multi.txt", "cat cat cat");
    files.Seed("remove_a.txt", "A");
    files.Seed("remove_b.txt", "B");
    files.SeedDirectory("empty");
    files.SeedDirectory("pkg");
    files.Seed("pkg/a.txt", "A");
    files.Seed("pkg/b.txt", "B");
    files.Seed("pkg/sub/deep.txt", "D");
  }

  std::string root;
  std::shared_ptr<StubExecutionSettings> settings;
  std::shared_ptr<StubProjectWorkspace> workspace;
  std::shared_ptr<MemoryFileAccess> files;
  std::shared_ptr<application::FileToolRegistry> tools;
  std::shared_ptr<application::FileToolRegistry> chinese_tools;
  bool done{};
};

std::shared_ptr<Scenario> active;

domain::McpToolGroupState &
FileOpsGroup(const std::shared_ptr<StubExecutionSettings> &settings) {
  const auto found = std::ranges::find(settings->value.groups,
                                       std::string{"file_ops"},
                                       &domain::McpToolGroupState::id);
  assert(found != settings->value.groups.end());
  return *found;
}

const application::RegisteredTool *
FindTool(const application::FileToolRegistry &registry, std::string_view name) {
  const auto found = std::ranges::find(registry.Tools(), name,
                                       &application::RegisteredTool::name);
  return found == registry.Tools().end() ? nullptr : &*found;
}

// One descriptor row: the tool must expose the verbatim legacy name,
// description and schema, and the whole file_ops group must stay denied in
// read-only mode without any permanent-grant key.
void CheckDescriptor(const application::FileToolRegistry &registry,
                     std::string_view name, std::string_view description,
                     std::string_view schema,
                     std::initializer_list<std::string_view> required) {
  const auto *tool = FindTool(registry, name);
  assert(tool != nullptr);
  assert(tool->description == description);
  assert(tool->parameters_json == schema);
  assert(tool->category == "file_ops");
  assert(!tool->allowed_in_read_only);
  assert(!tool->permanent_grant_supported);
  assert(tool->agent_selectable);
  assert(!tool->agent_selected_by_default);

  auto parsed = json::Parse(tool->parameters_json);
  assert(parsed);
  const auto *object = json::AsObject(&*parsed);
  assert(object != nullptr);
  assert(json::AsString(json::Find(*object, "type")) != nullptr);
  assert(*json::AsString(json::Find(*object, "type")) == "object");
  const auto *required_array = json::AsArray(json::Find(*object, "required"));
  if (required.size() == 0U) {
    assert(required_array == nullptr);
    return;
  }
  assert(required_array != nullptr);
  assert(required_array->size() == required.size());
  std::size_t index = 0;
  for (const auto expected : required) {
    const auto *value = json::AsString(&required_array->at(index));
    assert(value != nullptr && *value == expected);
    ++index;
  }
}

std::string Text(application::ToolTextKey key,
                 std::initializer_list<std::string> arguments,
                 application::ToolTextLanguage language =
                     application::ToolTextLanguage::english) {
  const std::vector<std::string> values{arguments};
  return application::ToolText(
      key, std::span<const std::string>{values.data(), values.size()},
      language);
}

// Every catalog key must be a verbatim copy of the packaged English and Chinese
// strings; this is the automated half of the manual spot check.
void CheckCatalogMatchesProperties() {
  const std::filesystem::path directory{LINECODE_STRING_RESOURCES_DIR};
  const auto english_text =
      ReadFile(directory / std::string{kEnglishPropertiesFile});
  const auto chinese_text =
      ReadFile(directory / std::string{kChinesePropertiesFile});
  assert(english_text.has_value());
  assert(chinese_text.has_value());
  const auto english = ParseProperties(*english_text);
  const auto chinese = ParseProperties(*chinese_text);

  // The catalog also carries the agent / agent_pipeline / agent_output tool
  // strings, so the count covers every migrated tool_* resource.
  assert(application::kToolTextKeyCount >= 42U);
  std::size_t matched = 0;
  for (std::size_t index = 0; index < application::kToolTextKeyCount; ++index) {
    const auto key = static_cast<application::ToolTextKey>(index);
    const std::string name{application::ToolTextName(key)};
    assert(application::IsToolTextKey(name));
    assert(name.starts_with("tool_"));
    const auto english_entry = english.find(name);
    const auto chinese_entry = chinese.find(name);
    assert(english_entry != english.end());
    assert(chinese_entry != chinese.end());
    assert(application::ToolTextTemplate(key) == english_entry->second);
    assert(application::ToolTextTemplate(
               key, application::ToolTextLanguage::chinese) ==
           chinese_entry->second);
    ++matched;
  }
  assert(matched == application::kToolTextKeyCount);

  // The built-in file tool keys the file tools actually produce.
  assert(english.contains("tool_file_read_not_found"));
  assert(english.contains("tool_call_action_match"));
}

void CheckCatalogFormatting() {
  using application::ToolTextKey;
  using application::ToolTextLanguage;

  // Literal braces survive formatting; placeholders are zero based.
  assert(Text(ToolTextKey::tool_file_read_exceed_50kb,
              {"big.txt", "60", "big.txt"}) ==
         "File big.txt is 60KB, exceeding the 50KB single-read limit.\n"
         "Use start_kb and end_kb to specify the range, e.g.: "
         "{\"file_path\":\"big.txt\",\"start_kb\":0,\"end_kb\":50}");
  assert(Text(ToolTextKey::tool_file_read_exceed_50kb,
              {"big.txt", "60", "big.txt"}, ToolTextLanguage::chinese) ==
         "文件 big.txt 大小为 60KB，单次读取超过 50KB。\n"
         "请使用 start_kb 和 end_kb 指定读取范围，例如："
         "{\"file_path\":\"big.txt\",\"start_kb\":0,\"end_kb\":50}");
  // A placeholder without a matching argument stays visible.
  assert(Text(ToolTextKey::tool_file_write_created, {"a.txt"}) ==
         "Successfully created file a.txt ({1} lines)");
  // An unknown key echoes the key instead of silently rendering nothing.
  assert(application::ToolText("tool_unknown_key", {}) == "tool_unknown_key");
  assert(!application::IsToolTextKey("tool_unknown_key"));
}

huxerui::Task<void> RunFileToolChecks(std::shared_ptr<Scenario> scenario) {
  using application::ToolRegistryErrorCode;
  const auto &tools = *scenario->tools;

  // 1. Catalog descriptors: name, description and schema verbatim.
  auto refreshed = co_await scenario->tools->Refresh();
  assert(refreshed);
  assert(scenario->tools->Tools().size() == 6U);
  CheckDescriptor(tools, "file_read", kFileReadDescription, kFileReadSchema,
                  {"file_path"});
  CheckDescriptor(tools, "file_write", kFileWriteDescription, kFileWriteSchema,
                  {"content", "file_path"});
  CheckDescriptor(tools, "file_edit", kFileEditDescription, kFileEditSchema,
                  {"file_path", "new_string", "old_string"});
  CheckDescriptor(tools, "file_delete", kFileDeleteDescription,
                  kFileDeleteSchema, {"paths", "reason"});
  CheckDescriptor(tools, "glob", kGlobDescription, kGlobSchema, {"pattern"});
  CheckDescriptor(tools, "list_dir", kListDirectoryDescription,
                  kListDirectorySchema, {});

  // 2. Localized templates: spot checks in both languages.
  assert(Text(application::ToolTextKey::tool_file_read_failed, {"boom"}) ==
         "Failed to read file: boom");
  assert(Text(application::ToolTextKey::tool_file_read_failed, {"boom"},
              application::ToolTextLanguage::chinese) == "读取文件失败: boom");
  assert(Text(application::ToolTextKey::tool_file_delete_none, {}) ==
         "No files were deleted");
  assert(Text(application::ToolTextKey::tool_file_delete_none, {},
              application::ToolTextLanguage::chinese) == "没有删除任何文件");
  assert(Text(application::ToolTextKey::tool_list_dir_empty, {"pkg"}) ==
         "Directory pkg:\n(empty directory)");
  assert(Text(application::ToolTextKey::tool_list_dir_empty, {"pkg"},
              application::ToolTextLanguage::chinese) == "目录 pkg:\n(空目录)");
  assert(Text(application::ToolTextKey::tool_glob_found, {"2", "."}) ==
         "Found 2 matching file(s) in .:\n");
  assert(Text(application::ToolTextKey::tool_glob_found, {"2", "."},
              application::ToolTextLanguage::chinese) ==
         "在 . 目录下找到 2 个匹配文件:\n");
  // {1} is the "." display of the workspace root, so the template's own
  // sentence period follows it.
  assert(Text(application::ToolTextKey::tool_glob_no_match, {"*.md", "."}) ==
         "No files matching \"*.md\" found in ..");
  assert(Text(application::ToolTextKey::tool_call_action_read, {}) == "Read");

  // 3. file_read: numbered content, defaults and the 50KB single-read limit.
  auto invoked = co_await scenario->tools->Invoke("file_read",
                                                  R"({"file_path":"notes.txt"})");
  assert(invoked && !invoked->error);
  assert(invoked->content == "1\talpha\n2\tbeta\n3\tgamma");

  invoked = co_await scenario->tools->Invoke("file_read",
                                             R"({"file_path":"missing.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "File not found: missing.txt");

  invoked = co_await scenario->tools->Invoke(
      "file_read", R"({"file_path":"../escape.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Failed to read file: Path is outside the current workspace: "
         "../escape.txt");

  // A directory argument returns the depth-first tree plus the legacy hint.
  invoked =
      co_await scenario->tools->Invoke("file_read", R"({"file_path":"pkg"})");
  assert(invoked && !invoked->error);
  // The tree is depth-first: a directory's children follow it immediately.
  assert(invoked->content ==
         "Directory pkg:\n"
         "[DIR]  sub/\n"
         "[FILE] sub/deep.txt\n"
         "[FILE] a.txt\n"
         "[FILE] b.txt"
         "\n\nTo read a file, specify the exact file path.");

  invoked =
      co_await scenario->tools->Invoke("file_read", R"({"file_path":"empty"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Directory empty:\n(empty directory)"
         "\n\nTo read a file, specify the exact file path.");

  // 60 KB without a KB range is refused, not loaded.
  invoked = co_await scenario->tools->Invoke("file_read",
                                             R"({"file_path":"large.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "File large.txt is 60KB, exceeding the 50KB single-read limit.\n"
         "Use start_kb and end_kb to specify the range, e.g.: "
         "{\"file_path\":\"large.txt\",\"start_kb\":0,\"end_kb\":50}");

  // One KB page covers exactly one whole line and reports the range footer.
  invoked = co_await scenario->tools->Invoke(
      "file_read", R"({"file_path":"segment.txt","start_kb":0,"end_kb":1})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "1\t" + std::string(1023, 'a') +
             "\n\n… (total 2 lines, showing KB 0-1 / total 2KB)");

  // The page is not truncated by the 50KB result limit, so a 60 KB range read
  // still returns the middle-truncation marker.
  invoked = co_await scenario->tools->Invoke(
      "file_read", R"({"file_path":"large.txt","start_kb":0,"end_kb":60})");
  assert(invoked && !invoked->error);
  assert(invoked->content.contains("chars truncated"));

  invoked = co_await scenario->tools->Invoke(
      "file_read", R"({"file_path":"segment.txt","start_kb":10})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "start_kb=10 exceeds file size (file is 2KB)");

  // Read failures surface through the localized template.
  scenario->files->fail_read = application::ToolFileError{
      .code = application::ToolFileErrorCode::io, .message = "injected read"};
  invoked = co_await scenario->tools->Invoke("file_read",
                                             R"({"file_path":"notes.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Failed to read file: injected read");
  scenario->files->fail_read.reset();

  // 4. file_write: create, update, parent creation and failures.
  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"fresh.txt","content":"one\ntwo"})");
  assert(invoked && !invoked->error);
  assert(invoked->content == "Successfully created file fresh.txt (2 lines)");
  assert(scenario->files->Content("fresh.txt") == "one\ntwo");

  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"fresh.txt","content":"one"})");
  assert(invoked && !invoked->error);
  assert(invoked->content == "Successfully updated file fresh.txt (1 lines)");

  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"deep/nested/leaf.txt","content":"x"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Successfully created file deep/nested/leaf.txt (1 lines)");
  assert(scenario->files->Content("deep/nested/leaf.txt") == "x");
  assert(scenario->files->created_directories.size() == 1U);

  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"   ","content":"x"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Failed to write file: file_path cannot be empty");

  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"pkg","content":"x"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Path is a directory, cannot write file: pkg\n"
         "To create a file, specify the full file path.");

  scenario->files->fail_create_directories = application::ToolFileError{
      .code = application::ToolFileErrorCode::io, .message = "injected mkdir"};
  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"other/leaf.txt","content":"x"})");
  assert(invoked && invoked->error);
  assert(invoked->content.starts_with("Failed to create parent directory: "));
  assert(invoked->content.ends_with("/other"));
  scenario->files->fail_create_directories.reset();

  scenario->files->fail_write = application::ToolFileError{
      .code = application::ToolFileErrorCode::io, .message = "injected write"};
  invoked = co_await scenario->tools->Invoke(
      "file_write", R"({"file_path":"fresh.txt","content":"x"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Failed to write file: injected write");
  scenario->files->fail_write.reset();

  // 5. file_edit: unique match, no match, ambiguity and replace_all.
  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"edit.txt","old_string":"world","new_string":"there"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Successfully edited edit.txt (1 match(es) replaced)");
  assert(scenario->files->Content("edit.txt") == "hello there");

  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"edit.txt","old_string":"absent","new_string":"x"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "No matching text found");

  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"multi.txt","old_string":"cat","new_string":"dog"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "old_string matched 3 places. Provide a more unique old_string, or "
         "set replace_all=true to replace every occurrence.");
  assert(scenario->files->Content("multi.txt") == "cat cat cat");

  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"multi.txt","old_string":"cat","new_string":"dog","replace_all":true})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Successfully edited multi.txt (3 match(es) replaced)");
  assert(scenario->files->Content("multi.txt") == "dog dog dog");

  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"multi.txt","old_string":"","new_string":"dog"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "old_string cannot be empty");

  invoked = co_await scenario->tools->Invoke(
      "file_edit",
      R"({"file_path":"missing.txt","old_string":"a","new_string":"b"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "File not found: missing.txt");

  invoked = co_await scenario->tools->Invoke(
      "file_edit", R"({"file_path":"pkg","old_string":"a","new_string":"b"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Path is a directory, cannot edit: pkg\n"
         "To edit a file, specify the exact file path.");

  // 6. file_delete: full success, partial failure and total failure.
  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":["remove_a.txt","remove_b.txt"],"reason":"cleanup"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Successfully deleted 2 item(s):\n"
         "- remove_a.txt\n"
         "- remove_b.txt");
  assert(!scenario->files->Has("remove_a.txt"));
  assert(!scenario->files->Has("remove_b.txt"));

  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":["fresh.txt","gone.txt"],"reason":"cleanup"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Successfully deleted 1 item(s):\n"
         "- fresh.txt\n"
         "\n"
         "Failed 1 item(s):\n"
         "- Path not found: gone.txt");

  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":["gone_a.txt","gone_b.txt"],"reason":"cleanup"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Failed 2 item(s):\n"
         "- Path not found: gone_a.txt\n"
         "- Path not found: gone_b.txt");

  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":["../escape.txt"],"reason":"cleanup"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Failed 1 item(s):\n"
         "- Failed to delete ../escape.txt: Path is outside the current "
         "workspace: ../escape.txt");

  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":["notes.txt"],"reason":"   "})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Deletion reason cannot be empty");

  invoked = co_await scenario->tools->Invoke(
      "file_delete", R"({"paths":[],"reason":"cleanup"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "paths cannot be empty");

  // 7. glob: matches, no matches, unusable root, truncation and failure.
  invoked = co_await scenario->tools->Invoke(
      "glob", R"({"pattern":"*.txt","path":"pkg"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Found 3 matching file(s) in pkg:\n"
         "a.txt\n"
         "b.txt\n"
         "sub/deep.txt");

  invoked = co_await scenario->tools->Invoke("glob", R"({"pattern":"*.md"})");
  assert(invoked && !invoked->error);
  assert(invoked->content == "No files matching \"*.md\" found in ..");

  invoked = co_await scenario->tools->Invoke(
      "glob", R"({"pattern":"*.txt","path":"notes.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content ==
         "Search root directory does not exist or is not a directory: "
         "notes.txt");

  invoked = co_await scenario->tools->Invoke("glob", R"({"pattern":"  "})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Search failed: pattern cannot be empty");

  scenario->files->glob_override = std::vector<std::string>(1000U, "hit.txt");
  invoked = co_await scenario->tools->Invoke("glob", R"({"pattern":"*.txt"})");
  assert(invoked && !invoked->error);
  assert(invoked->content.starts_with("Found 1000 matching file(s) in .:\n"));
  assert(invoked->content.ends_with("… (too many results, truncated)"));
  scenario->files->glob_override.reset();

  scenario->files->fail_glob = application::ToolFileError{
      .code = application::ToolFileErrorCode::io, .message = "injected glob"};
  invoked = co_await scenario->tools->Invoke("glob", R"({"pattern":"*.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Search failed: injected glob");
  scenario->files->fail_glob.reset();

  // 8. list_dir: content, empty directory and failure paths.
  invoked = co_await scenario->tools->Invoke("list_dir", R"({"path":"pkg"})");
  assert(invoked && !invoked->error);
  assert(invoked->content ==
         "Directory pkg:\n"
         "[DIR]  sub/\n"
         "[FILE] a.txt\n"
         "[FILE] b.txt");

  invoked =
      co_await scenario->tools->Invoke("list_dir", R"({"path":"empty"})");
  assert(invoked && !invoked->error);
  assert(invoked->content == "Directory empty:\n(empty directory)");

  invoked = co_await scenario->tools->Invoke("list_dir", "{}");
  assert(invoked && !invoked->error);
  assert(invoked->content.starts_with(
      "Directory .:\n[DIR]  deep/\n[DIR]  empty/\n[DIR]  pkg/\n[FILE] "));
  assert(invoked->content.contains("[FILE] notes.txt"));

  invoked = co_await scenario->tools->Invoke("list_dir",
                                             R"({"path":"notes.txt"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Path is not a directory: notes.txt");

  invoked = co_await scenario->tools->Invoke("list_dir", R"({"path":"nope"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Directory not found: nope");

  scenario->files->fail_list = application::ToolFileError{
      .code = application::ToolFileErrorCode::io, .message = "injected list"};
  invoked = co_await scenario->tools->Invoke("list_dir", R"({"path":"pkg"})");
  assert(invoked && invoked->error);
  assert(invoked->content == "Failed to list directory: injected list");
  scenario->files->fail_list.reset();

  // 9. Argument and dispatch failures.
  invoked = co_await scenario->tools->Invoke("file_read", "{");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::invalid_arguments);
  assert(invoked.error().message == "Parameters cannot be empty.");
  invoked = co_await scenario->tools->Invoke("file_read", "[]");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::invalid_arguments);
  invoked = co_await scenario->tools->Invoke("unknown_tool", "{}");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::unknown_tool);
  assert(invoked.error().message == "Unknown file tool: unknown_tool");

  // 10. Chinese product language for the same tools.
  const auto &chinese = *scenario->chinese_tools;
  auto chinese_refreshed = co_await scenario->chinese_tools->Refresh();
  assert(chinese_refreshed);
  assert(scenario->chinese_tools->Tools().size() == 6U);
  CheckDescriptor(chinese, "file_read", kFileReadDescription, kFileReadSchema,
                  {"file_path"});
  auto chinese_invoked =
      co_await scenario->chinese_tools->Invoke("file_read",
                                               R"({"file_path":"missing.txt"})");
  assert(chinese_invoked && chinese_invoked->error);
  assert(chinese_invoked->content == "文件不存在: missing.txt");
  chinese_invoked = co_await scenario->chinese_tools->Invoke(
      "file_write", R"({"file_path":"zh.txt","content":"one\ntwo"})");
  assert(chinese_invoked && !chinese_invoked->error);
  assert(chinese_invoked->content == "成功创建文件 zh.txt (2 行)");
  chinese_invoked =
      co_await scenario->chinese_tools->Invoke("list_dir", R"({"path":"empty"})");
  assert(chinese_invoked && !chinese_invoked->error);
  assert(chinese_invoked->content == "目录 empty:\n(空目录)");
  chinese_invoked = co_await scenario->chinese_tools->Invoke(
      "file_delete", R"({"paths":[],"reason":""})");
  assert(chinese_invoked && chinese_invoked->error);
  assert(chinese_invoked->content == "删除原因 reason 不能为空");
  chinese_invoked = co_await scenario->chinese_tools->Invoke(
      "glob", R"({"pattern":"*.md"})");
  assert(chinese_invoked && !chinese_invoked->error);
  assert(chinese_invoked->content == "在 . 目录下未找到匹配 \"*.md\" 的文件。");

  // 11. Enablement: disabled group and unsupported execution mode hide every
  // tool and reject invocation before touching the file capability.
  auto &group = FileOpsGroup(scenario->settings);
  group.enabled = false;
  refreshed = co_await scenario->tools->Refresh();
  assert(refreshed);
  assert(scenario->tools->Tools().empty());
  const auto writes_before = scenario->files->written.size();
  invoked = co_await scenario->tools->Invoke("file_read",
                                             R"({"file_path":"notes.txt"})");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::unavailable);
  assert(scenario->files->written.size() == writes_before);

  group.enabled = true;
  group.supported_modes = domain::McpExecutionModeMask::local;
  scenario->settings->value.mode = domain::McpExecutionMode::ssh;
  refreshed = co_await scenario->tools->Refresh();
  assert(refreshed);
  assert(scenario->tools->Tools().empty());
  invoked = co_await scenario->tools->Invoke("list_dir", "{}");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::unavailable);

  scenario->settings->value.mode = domain::McpExecutionMode::local;
  refreshed = co_await scenario->tools->Refresh();
  assert(refreshed);
  assert(scenario->tools->Tools().size() == 6U);

  // 12. Workspace and settings failures.
  scenario->workspace->fail = true;
  invoked = co_await scenario->tools->Invoke("file_read",
                                             R"({"file_path":"notes.txt"})");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::unavailable);
  assert(invoked.error().message == "injected workspace failure");
  scenario->workspace->fail = false;

  scenario->workspace->record.path.clear();
  invoked = co_await scenario->tools->Invoke("list_dir", "{}");
  assert(!invoked);
  assert(invoked.error().code == ToolRegistryErrorCode::unavailable);
  assert(invoked.error().message ==
         "No project workspace is selected for the built-in file tools");
  scenario->workspace->record.path = scenario->root;

  scenario->settings->fail_load = true;
  auto failed = co_await scenario->tools->Refresh();
  assert(!failed);
  assert(failed.error().code == ToolRegistryErrorCode::load_failed);
  assert(failed.error().message.contains("injected"));
  scenario->settings->fail_load = false;

  scenario->done = true;
}

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch(
        [scenario]() -> huxerui::Task<void> {
          return RunFileToolChecks(scenario);
        });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("file-tool-registry-probe");
}

} // namespace

int main() {
  CheckCatalogMatchesProperties();
  CheckCatalogFormatting();

  active = std::make_shared<Scenario>();

  // Dependencies are mandatory: the registry fails fast on null services.
  bool rejected_settings = false;
  try {
    application::FileToolRegistry invalid(nullptr, active->workspace,
                                          active->files);
  } catch (const std::invalid_argument &) {
    rejected_settings = true;
  }
  assert(rejected_settings);
  bool rejected_workspace = false;
  try {
    application::FileToolRegistry invalid(active->settings, nullptr,
                                          active->files);
  } catch (const std::invalid_argument &) {
    rejected_workspace = true;
  }
  assert(rejected_workspace);
  bool rejected_files = false;
  try {
    application::FileToolRegistry invalid(active->settings, active->workspace,
                                          nullptr);
  } catch (const std::invalid_argument &) {
    rejected_files = true;
  }
  assert(rejected_files);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });

  active.reset();
}
