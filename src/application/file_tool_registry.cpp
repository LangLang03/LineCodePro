#include "application/file_tool_registry.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <charconv>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <functional>
#include <iterator>
#include <locale>
#include <ranges>
#include <span>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <system_error>
#include <type_traits>
#include <utility>
#include <vector>

#include <huxerui/task.h>

#include "application/tool_file_path_policy.h"
#include "application/tool_text_catalog.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// Names of the built-in file tools (FileReadTool.NAME and friends).
constexpr std::string_view kFileReadToolName = "file_read";
constexpr std::string_view kFileWriteToolName = "file_write";
constexpr std::string_view kFileEditToolName = "file_edit";
constexpr std::string_view kFileDeleteToolName = "file_delete";
constexpr std::string_view kGlobToolName = "glob";
constexpr std::string_view kListDirectoryToolName = "list_dir";

// Legacy BuiltInToolProviders group id and its Agent editor category.
constexpr std::string_view kFileOpsGroupId = "file_ops";

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

// Verbatim legacy getParameters() schemas (name/type/properties/required and
// every description string), serialized in the canonical sorted-key form
// produced by archive_json::Serialize for the other native registries.
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

// Argument errors and per-tool failure texts live in the migrated string
// resources; the exception texts below are the legacy ToolArgs.requireNonEmpty
// messages that the old tools reported through their own *_failed resource.
constexpr std::string_view kParametersEmptyMessage =
    "Parameters cannot be empty.";
constexpr std::string_view kFilePathEmptyMessage = "file_path cannot be empty";
constexpr std::string_view kPatternEmptyMessage = "pattern cannot be empty";
constexpr std::string_view kWorkspaceUnavailableMessage =
    "No project workspace is selected for the built-in file tools";

// FileReadTool.LARGE_FILE_THRESHOLD_BYTES / MAX_KB_RANGE /
// MAX_DIRECTORY_ITEMS and GlobTool.MAX_RESULTS.
constexpr std::uint64_t kLargeFileThresholdBytes = 50ULL * 1024ULL;
constexpr std::int64_t kMaximumKbRange = 1024;
constexpr std::size_t kMaximumDirectoryItems = 400;
constexpr std::size_t kMaximumGlobResults = 1000;

// Bounds the parsed start_kb/end_kb before the KB-to-byte conversion, so a
// hallucinated argument can never overflow the range arithmetic. Anything
// above this is already larger than every supported file.
constexpr std::int64_t kMaximumParsedKb = 1'000'000'000;

// FileReadTool.countNewlines() scans with a 64KB buffer.
constexpr std::uint64_t kNewlineScanChunkBytes = 64ULL * 1024ULL;

// Legacy ToolResult.MAX_TOOL_RESULT_CHARS and its half-size truncation window.
constexpr std::size_t kMaximumResultCharacters = 50U * 1024U;
constexpr std::size_t kTruncationHalf = kMaximumResultCharacters / 2U;

ToolRegistryError Error(ToolRegistryErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

// Legacy ToolResult with error=true: the message is the model-visible output.
ToolInvocationResult ToolFailure(std::string content) {
  return {.content = std::move(content), .error = true};
}

ToolInvocationResult ToolSuccess(std::string content) {
  return {.content = std::move(content), .error = false};
}

// JSONObject.has(): a missing key and an explicit null both count as absent.
const json::Value *FindPresent(const json::Object &object,
                               std::string_view key) {
  const auto *value = json::Find(object, key);
  if (value == nullptr || std::holds_alternative<json::Null>(*value))
    return nullptr;
  return value;
}

// JSONObject.optString(): a missing or non-string value yields "".
std::string StringArgument(const json::Object &object, std::string_view key) {
  const auto *value = FindPresent(object, key);
  const auto *text =
      value == nullptr ? nullptr : std::get_if<std::string>(value);
  return text == nullptr ? std::string{} : *text;
}

// JSONObject.optBoolean(): booleans and the strings "true"/"false" are read,
// every other value falls back.
bool BoolArgument(const json::Object &object, std::string_view key,
                  bool fallback) {
  const auto *value = FindPresent(object, key);
  if (value == nullptr)
    return fallback;
  if (const auto *flag = std::get_if<bool>(value))
    return *flag;
  const auto *text = std::get_if<std::string>(value);
  if (text == nullptr)
    return fallback;
  if (*text == "true")
    return true;
  if (*text == "false")
    return false;
  return fallback;
}

// JSONObject.optInt(): numbers and numeric strings are read, every other value
// falls back.
std::int64_t NumberArgument(const json::Object &object, std::string_view key,
                            std::int64_t fallback) {
  const auto *value = FindPresent(object, key);
  if (value == nullptr)
    return fallback;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    return *integer;
  if (const auto *number = std::get_if<double>(value))
    return static_cast<std::int64_t>(*number);
  const auto *text = std::get_if<std::string>(value);
  if (text == nullptr)
    return fallback;
  std::int64_t parsed{};
  const auto result =
      std::from_chars(text->data(), text->data() + text->size(), parsed);
  return result.ec == std::errc{} && result.ptr == text->data() + text->size()
             ? parsed
             : fallback;
}

// JSONObject.optString("path", "."): the fallback applies only when the key is
// absent or null, so an explicit empty string stays empty.
std::string PathArgument(const json::Object &object) {
  const auto *value = FindPresent(object, "path");
  const auto *text =
      value == nullptr ? nullptr : std::get_if<std::string>(value);
  return text == nullptr ? std::string{"."} : *text;
}

// Legacy String.trim(): strips every character at or below U+0020.
std::string Trim(std::string_view value) {
  std::size_t begin = 0;
  std::size_t end = value.size();
  while (begin < end && static_cast<unsigned char>(value[begin]) <= ' ')
    ++begin;
  while (end > begin && static_cast<unsigned char>(value[end - 1]) <= ' ')
    --end;
  return std::string{value.substr(begin, end - begin)};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(
      value, std::back_inserter(lowered),
      [](unsigned char byte) { return static_cast<char>(std::tolower(byte)); });
  return lowered;
}

// Legacy directory listing order: directories first, then case-insensitive by
// name.
bool DirectoryOrder(const ToolFileEntry &left, const ToolFileEntry &right) {
  const bool left_directory = left.kind == ToolFileKind::directory;
  const bool right_directory = right.kind == ToolFileKind::directory;
  if (left_directory != right_directory)
    return left_directory;
  return Lower(left.name) < Lower(right.name);
}

// Java String.split("\n", -1): every newline separates, and a trailing newline
// keeps one empty element.
std::vector<std::string_view> SplitLines(std::string_view content) {
  std::vector<std::string_view> lines;
  std::size_t begin = 0;
  while (true) {
    const auto end = content.find('\n', begin);
    if (end == std::string_view::npos) {
      lines.push_back(content.substr(begin));
      return lines;
    }
    lines.push_back(content.substr(begin, end - begin));
    begin = end + 1;
  }
}

// FileReadTool.addLineNumbers(): "<line>\t<text>" rows, no trailing newline.
std::string AddLineNumbers(std::string_view content, std::int64_t start_line) {
  if (content.empty())
    return {};
  const bool ends_with_newline = content.ends_with('\n');
  const auto lines = SplitLines(content);
  const std::size_t count = lines.size() - (ends_with_newline ? 1U : 0U);
  std::string numbered;
  for (std::size_t index = 0; index < count; ++index) {
    numbered += std::to_string(start_line + static_cast<std::int64_t>(index));
    numbered += '\t';
    numbered += lines[index];
    if (index + 1U < count)
      numbered += '\n';
  }
  return numbered;
}

// input.optString("content").split("\n", -1).length.
std::size_t LineCount(std::string_view content) {
  return static_cast<std::size_t>(std::ranges::count(content, '\n')) + 1U;
}

std::size_t CountOccurrences(std::string_view content, std::string_view value) {
  // FileEditTool rejects an empty old_string before counting, so this only
  // keeps the scan finite for a future caller.
  if (value.empty())
    return 0;
  std::size_t count = 0;
  std::size_t index = 0;
  while ((index = content.find(value, index)) != std::string_view::npos) {
    ++count;
    index += value.size();
  }
  return count;
}

std::string ReplaceFirst(std::string_view content, std::string_view old_string,
                         std::string_view new_string) {
  const auto index = content.find(old_string);
  if (index == std::string_view::npos)
    return std::string{content};
  std::string next{content.substr(0, index)};
  next += new_string;
  next += content.substr(index + old_string.size());
  return next;
}

std::string ReplaceAll(std::string_view content, std::string_view old_string,
                       std::string_view new_string) {
  if (old_string.empty())
    return std::string{content};
  std::string next;
  std::size_t index = 0;
  while (true) {
    const auto found = content.find(old_string, index);
    if (found == std::string_view::npos) {
      next += content.substr(index);
      return next;
    }
    next += content.substr(index, found - index);
    next += new_string;
    index = found + old_string.size();
  }
}

// Legacy ToolResult.truncateContent(): middle truncation with a marker when the
// text exceeds the 50KB single-result limit.
std::string TruncateContent(std::string content) {
  if (content.size() <= kMaximumResultCharacters)
    return content;
  const std::size_t truncated = content.size() - kMaximumResultCharacters;
  std::string next{content.substr(0, kTruncationHalf)};
  next += "\n... (";
  next += std::to_string(truncated);
  next += " chars truncated) ...\n";
  next += content.substr(content.size() - kTruncationHalf);
  return next;
}

// File.getParentFile().getPath() of the resolved target; empty when the target
// has no parent component.
std::string ParentPath(std::string_view path) {
  const auto separator = path.find_last_of('/');
  if (separator == std::string_view::npos)
    return {};
  if (separator == 0)
    return "/";
  return std::string{path.substr(0, separator)};
}

struct TargetPath final {
  // Canonical path handed to the ToolFileAccess port.
  std::string absolute;
  // Workspace-relative path used by the legacy output strings.
  std::string display;
};

std::expected<TargetPath, FileToolPathError>
ResolveTarget(std::string_view root, std::string_view input) {
  auto absolute = FileToolPathPolicy::Resolve(root, input);
  if (!absolute)
    return std::unexpected(absolute.error());
  return TargetPath{.absolute = *absolute,
                    .display = FileToolPathPolicy::Display(root, *absolute)};
}

// Everything a file tool needs: the workspace that owns path policy, the
// platform file capability and the language of the produced text. No
// filesystem access happens in this translation unit.
struct FileToolContext final {
  ProjectWorkspaceController *projects{};
  ToolFileAccess *files{};
  ToolTextLanguage language{ToolTextLanguage::english};
  // Records revertable changes for the write family, mirroring the legacy
  // `DiffRecorder` that wrapped FileWriteTool and FileEditTool. Optional: a
  // null store simply means "no review history".
  DiffStore *diffs{};
};

// Converts one catalog argument exactly like huxerui::StringVariant::Format:
// text is carried through verbatim and every other value is streamed with the
// classic locale, so the rendered text is locale independent.
template <class Argument> std::string TextArgument(Argument &&argument) {
  if constexpr (std::is_convertible_v<Argument, std::string_view>) {
    return std::string{std::string_view{std::forward<Argument>(argument)}};
  } else {
    std::ostringstream stream;
    stream.imbue(std::locale::classic());
    stream << std::forward<Argument>(argument);
    return std::move(stream).str();
  }
}

// Resolves a migrated legacy string resource in one language. Arguments are
// supplied in the same order the old getString(...) call used, which is also
// the {0}/{1} placeholder order of the application-owned catalog.
template <class... Arguments>
std::string Text(ToolTextLanguage language, ToolTextKey key,
                 Arguments &&...arguments) {
  const std::array<std::string, sizeof...(Arguments)> values{
      TextArgument(std::forward<Arguments>(arguments))...};
  return ToolText(key,
                  std::span<const std::string>{values.data(), values.size()},
                  language);
}

// Same lookup for the language the current invocation was started with.
template <class... Arguments>
std::string Text(const FileToolContext &context, ToolTextKey key,
                 Arguments &&...arguments) {
  return Text(context.language, key, std::forward<Arguments>(arguments)...);
}

// Legacy ToolContext.getHomePath() is the selected project directory.
huxerui::Task<std::expected<std::string, ToolRegistryError>>
WorkspaceRoot(FileToolContext context) {
  if (context.projects == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unavailable,
                                    std::string{kWorkspaceUnavailableMessage}));
  }
  auto selected = co_await context.projects->SelectedProject();
  if (!selected) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::unavailable, selected.error().message));
  }
  if (selected->path.empty()) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unavailable,
                                    std::string{kWorkspaceUnavailableMessage}));
  }
  co_return std::move(selected->path);
}

// FileReadTool.countNewlines(): newlines strictly below upToByte, read in 64KB
// chunks so the scan never loads the whole file at once.
huxerui::Task<ToolFileResult<std::uint64_t>>
CountNewlines(ToolFileAccess &files, const std::string &path,
              std::uint64_t up_to_byte) {
  std::uint64_t count = 0;
  std::uint64_t offset = 0;
  while (offset < up_to_byte) {
    const std::uint64_t length =
        std::min(kNewlineScanChunkBytes, up_to_byte - offset);
    auto chunk = co_await files.ReadBytes(path, offset, length);
    if (!chunk)
      co_return std::unexpected(chunk.error());
    count += static_cast<std::uint64_t>(std::ranges::count(*chunk, '\n'));
    const auto read = static_cast<std::uint64_t>(chunk->size());
    if (read < length)
      break;
    offset += read;
  }
  co_return count;
}

// FileReadTool.lastByteIsNewline().
huxerui::Task<ToolFileResult<bool>> LastByteIsNewline(ToolFileAccess &files,
                                                      const std::string &path,
                                                      std::uint64_t size) {
  if (size == 0)
    co_return false;
  auto last = co_await files.ReadBytes(path, size - 1U, 1U);
  if (!last)
    co_return std::unexpected(last.error());
  co_return (*last == "\n");
}

// FileReadTool.appendDirectory(): a depth-first tree of "[DIR]  <path>/" and
// "[FILE] <path>" rows, capped at MAX_DIRECTORY_ITEMS entries.
huxerui::Task<ToolFileResult<void>>
AppendDirectory(ToolFileAccess &files, ToolTextLanguage language,
                const std::string &directory, std::string_view parent,
                std::size_t &count, std::string &builder) {
  if (count >= kMaximumDirectoryItems)
    co_return ToolFileResult<void>{};
  auto entries = co_await files.ListDirectory(directory);
  if (!entries)
    co_return std::unexpected(entries.error());
  std::ranges::stable_sort(*entries, DirectoryOrder);
  for (const auto &entry : *entries) {
    if (count >= kMaximumDirectoryItems) {
      builder += Text(language, ToolTextKey::tool_file_read_dir_truncated);
      co_return ToolFileResult<void>{};
    }
    const std::string relative =
        parent.empty() ? entry.name : std::string{parent} + "/" + entry.name;
    if (entry.kind == ToolFileKind::directory) {
      builder += "[DIR]  ";
      builder += relative;
      builder += "/\n";
      ++count;
      auto nested = co_await AppendDirectory(files, language,
                                             directory + "/" + entry.name,
                                             relative, count, builder);
      if (!nested)
        co_return std::unexpected(nested.error());
    } else {
      builder += "[FILE] ";
      builder += relative;
      builder += '\n';
      ++count;
    }
  }
  co_return ToolFileResult<void>{};
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteFileRead(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  const std::string input_path = StringArgument(*object, "file_path");
  auto target = ResolveTarget(*workspace, input_path);
  if (!target) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                               target.error().message));
  }
  auto info = co_await context.files->Stat(target->absolute);
  if (!info) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_read_not_found, target->display));
  }
  if (info->kind == ToolFileKind::directory) {
    std::string builder;
    std::size_t count = 0;
    auto appended = co_await AppendDirectory(
        *context.files, context.language, target->absolute, std::string_view{},
        count, builder);
    if (!appended) {
      co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                                 appended.error().message));
    }
    const std::string list =
        builder.empty() ? Text(context, ToolTextKey::tool_file_read_empty_dir)
                        : Trim(builder);
    co_return ToolSuccess(
        Text(context, ToolTextKey::tool_file_read_dir_content, target->display,
             list) +
        Text(context, ToolTextKey::tool_file_read_dir_specify_file));
  }

  const std::uint64_t file_length = info->size;
  const std::int64_t start_kb =
      std::clamp(NumberArgument(*object, "start_kb", 0), std::int64_t{0},
                 kMaximumParsedKb);
  std::int64_t end_kb = std::clamp(NumberArgument(*object, "end_kb", 50),
                                   std::int64_t{0}, kMaximumParsedKb);
  end_kb = std::max(start_kb + 1, end_kb);
  // Caps a single read at MAX_KB_RANGE regardless of the requested end_kb.
  if (end_kb - start_kb > kMaximumKbRange)
    end_kb = start_kb + kMaximumKbRange;
  const bool has_kb_range = FindPresent(*object, "start_kb") != nullptr ||
                            FindPresent(*object, "end_kb") != nullptr;

  if (!has_kb_range) {
    // Small file: read it whole. Large file: refuse and point at the range
    // arguments instead of loading megabytes into one result.
    if (file_length > kLargeFileThresholdBytes) {
      co_return ToolFailure(
          Text(context, ToolTextKey::tool_file_read_exceed_50kb,
               target->display, file_length / 1024U, input_path));
    }
    auto content = co_await context.files->ReadText(target->absolute);
    if (!content) {
      co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                                 content.error().message));
    }
    co_return ToolSuccess(TruncateContent(AddLineNumbers(*content, 1)));
  }

  // Range read: only the requested bytes are fetched, so files larger than the
  // single-read limit stay readable page by page.
  const std::uint64_t start_byte =
      std::min(static_cast<std::uint64_t>(start_kb) * 1024U, file_length);
  const std::uint64_t end_byte =
      std::min(static_cast<std::uint64_t>(end_kb) * 1024U, file_length);
  if (start_byte >= file_length) {
    co_return ToolFailure(Text(context,
                               ToolTextKey::tool_file_read_start_out_of_range,
                               start_kb, file_length / 1024U));
  }
  auto chunk = co_await context.files->ReadBytes(target->absolute, start_byte,
                                                 end_byte - start_byte);
  if (!chunk) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                               chunk.error().message));
  }
  const std::string_view content{*chunk};
  std::size_t start_char = 0;
  std::size_t end_char = content.size();
  if (start_byte > 0) {
    // The chunk may begin inside a line; drop the incomplete first line.
    auto previous = co_await context.files->ReadBytes(target->absolute,
                                                      start_byte - 1U, 1U);
    if (!previous) {
      co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                                 previous.error().message));
    }
    if (*previous != "\n") {
      const auto line_start = content.find('\n');
      if (line_start != std::string_view::npos)
        start_char = line_start + 1U;
    }
  }
  if (end_byte < file_length) {
    // Keep whole lines: end at the last newline inside the chunk.
    const auto line_end = content.rfind('\n');
    if (line_end != std::string_view::npos)
      end_char = line_end + 1U;
  }
  start_char = std::min(start_char, end_char);

  auto leading = co_await CountNewlines(*context.files, target->absolute,
                                        start_byte + start_char);
  if (!leading) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                               leading.error().message));
  }
  std::string result =
      AddLineNumbers(content.substr(start_char, end_char - start_char),
                     1 + static_cast<std::int64_t>(*leading));

  auto total =
      co_await CountNewlines(*context.files, target->absolute, file_length);
  if (!total) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                               total.error().message));
  }
  std::int64_t total_lines = 1 + static_cast<std::int64_t>(*total);
  auto trailing =
      co_await LastByteIsNewline(*context.files, target->absolute, file_length);
  if (!trailing) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_read_failed,
                               trailing.error().message));
  }
  if (*trailing)
    --total_lines;
  result += Text(context, ToolTextKey::tool_file_read_range_info, total_lines,
                 start_kb, end_kb, file_length / 1024U);
  co_return ToolSuccess(TruncateContent(std::move(result)));
}

// Port of `DiffRecorder.executeWithDiff`'s tail: only a real content change is
// recorded, and the resulting identifier travels back on the tool result so
// the card can offer Accept / Revert.
huxerui::Task<std::expected<std::string, ToolRegistryError>>
RecordChange(const FileToolContext &context, std::string absolute_path,
             std::string old_content, std::string new_content,
             const bool old_exists) {
  if (context.diffs == nullptr || old_content == new_content)
    co_return std::string{};
  auto recorded = co_await context.diffs->Record(
      std::move(absolute_path), std::move(old_content), std::move(new_content),
      old_exists);
  if (!recorded) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::invocation_failed,
        "The file was changed, but its review history could not be recorded: " +
            recorded.error().message));
  }
  co_return std::move(recorded->id);
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteFileWrite(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  const std::string input_path = StringArgument(*object, "file_path");
  if (Trim(input_path).empty()) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_write_failed,
                               std::string{kFilePathEmptyMessage}));
  }
  auto target = ResolveTarget(*workspace, input_path);
  if (!target) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_write_failed,
                               target.error().message));
  }
  const auto info = co_await context.files->Stat(target->absolute);
  if (info && info->kind == ToolFileKind::directory) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_write_is_directory, input_path));
  }
  // Legacy FileWriteTool creates the missing parent chain before writing.
  const std::string parent = ParentPath(target->absolute);
  if (!parent.empty()) {
    const auto parent_info = co_await context.files->Stat(parent);
    if (!parent_info || parent_info->kind != ToolFileKind::directory) {
      auto created = co_await context.files->CreateDirectories(parent);
      if (!created) {
        co_return ToolFailure(
            Text(context, ToolTextKey::tool_file_write_mkdir_failed, parent));
      }
    }
  }
  std::string old_content;
  if (info.has_value()) {
    auto previous = co_await context.files->ReadText(target->absolute);
    if (previous)
      old_content = std::move(*previous);
  }
  const std::string content = StringArgument(*object, "content");
  auto written = co_await context.files->WriteText(target->absolute, content);
  if (!written) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_write_failed,
                               written.error().message));
  }
  auto result =
      ToolSuccess(Text(context,
                       info.has_value() ? ToolTextKey::tool_file_write_updated
                                        : ToolTextKey::tool_file_write_created,
                       input_path, LineCount(content)));
  auto diff_id =
      co_await RecordChange(context, target->absolute, std::move(old_content),
                            content, info.has_value());
  if (!diff_id)
    co_return std::unexpected(std::move(diff_id.error()));
  result.diff_id = std::move(*diff_id);
  co_return result;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteFileEdit(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  const std::string input_path = StringArgument(*object, "file_path");
  const std::string old_string = StringArgument(*object, "old_string");
  const std::string new_string = StringArgument(*object, "new_string");
  const bool replace_all = BoolArgument(*object, "replace_all", false);
  if (Trim(input_path).empty()) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_edit_failed,
                               std::string{kFilePathEmptyMessage}));
  }
  if (old_string.empty()) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_edit_old_string_empty));
  }
  auto target = ResolveTarget(*workspace, input_path);
  if (!target) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_edit_failed,
                               target.error().message));
  }
  const auto info = co_await context.files->Stat(target->absolute);
  if (!info) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_edit_not_found, target->display));
  }
  if (info->kind == ToolFileKind::directory) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_edit_is_directory, input_path));
  }
  auto content = co_await context.files->ReadText(target->absolute);
  if (!content) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_edit_failed,
                               content.error().message));
  }
  if (content->find(old_string) == std::string::npos) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_edit_no_match));
  }
  const std::size_t count = CountOccurrences(*content, old_string);
  if (count > 1U && !replace_all) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_edit_multiple_matches, count));
  }
  std::string next = replace_all
                         ? ReplaceAll(*content, old_string, new_string)
                         : ReplaceFirst(*content, old_string, new_string);
  const std::size_t replaced = replace_all ? count : 1U;
  auto updated = next;
  auto written =
      co_await context.files->WriteText(target->absolute, std::move(next));
  if (!written) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_file_edit_failed,
                               written.error().message));
  }
  auto result = ToolSuccess(Text(context, ToolTextKey::tool_file_edit_success,
                                 target->display, replaced));
  auto diff_id = co_await RecordChange(context, target->absolute, *content,
                                       std::move(updated), true);
  if (!diff_id)
    co_return std::unexpected(std::move(diff_id.error()));
  result.diff_id = std::move(*diff_id);
  co_return result;
}

// FileDeleteTool.paths(): the "paths" array plus the legacy file_path/path
// aliases, each trimmed and empty entries dropped.
std::vector<std::string> DeletePaths(const json::Object &object) {
  std::vector<std::string> paths;
  if (const auto *array = json::AsArray(FindPresent(object, "paths"))) {
    for (const auto &value : *array) {
      const auto *text = std::get_if<std::string>(&value);
      if (text == nullptr)
        continue;
      auto trimmed = Trim(*text);
      if (!trimmed.empty())
        paths.push_back(std::move(trimmed));
    }
  }
  for (const std::string_view key : {"file_path", "path"}) {
    auto alias = Trim(StringArgument(object, key));
    if (!alias.empty())
      paths.push_back(std::move(alias));
  }
  return paths;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteFileDelete(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  auto paths = DeletePaths(*object);
  if (Trim(StringArgument(*object, "reason")).empty()) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_delete_reason_empty));
  }
  if (paths.empty()) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_file_delete_paths_empty));
  }

  std::vector<std::string> deleted;
  std::vector<std::string> errors;
  for (const auto &path : paths) {
    auto target = ResolveTarget(*workspace, path);
    if (!target) {
      errors.push_back(Text(context, ToolTextKey::tool_file_delete_item_failed,
                            path, target.error().message));
      continue;
    }
    const auto info = co_await context.files->Stat(target->absolute);
    if (!info) {
      errors.push_back(
          Text(context, ToolTextKey::tool_file_delete_path_not_found, path));
      continue;
    }
    auto removed = co_await context.files->Delete(target->absolute);
    if (!removed) {
      errors.push_back(Text(context, ToolTextKey::tool_file_delete_item_failed,
                            path, removed.error().message));
      continue;
    }
    deleted.push_back(target->display);
  }

  std::string builder;
  if (!deleted.empty()) {
    builder +=
        Text(context, ToolTextKey::tool_file_delete_success, deleted.size());
    for (const auto &path : deleted) {
      builder += "- ";
      builder += path;
      builder += '\n';
    }
  }
  if (!errors.empty()) {
    if (!builder.empty())
      builder += '\n';
    builder += Text(context, ToolTextKey::tool_file_delete_partial_fail,
                    errors.size());
    for (const auto &error : errors) {
      builder += "- ";
      builder += error;
      builder += '\n';
    }
  }
  // A partial failure still reports success; only an all-failed batch is an
  // error result.
  const bool is_error = !errors.empty() && deleted.empty();
  const std::string content =
      builder.empty() ? Text(context, ToolTextKey::tool_file_delete_none)
                      : Trim(builder);
  co_return ToolInvocationResult{.content = content, .error = is_error};
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteGlob(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  const std::string pattern = StringArgument(*object, "pattern");
  if (Trim(pattern).empty()) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_glob_failed,
                               std::string{kPatternEmptyMessage}));
  }
  const std::string display_root_argument = PathArgument(*object);
  auto root = ResolveTarget(*workspace, StringArgument(*object, "path"));
  if (!root) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_glob_failed, root.error().message));
  }
  const auto info = co_await context.files->Stat(root->absolute);
  if (!info || info->kind != ToolFileKind::directory) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_glob_root_not_found,
                               display_root_argument));
  }
  auto results = co_await context.files->Glob(root->absolute, pattern,
                                              kMaximumGlobResults);
  if (!results) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_glob_failed, results.error().message));
  }
  if (results->empty()) {
    co_return ToolSuccess(
        Text(context, ToolTextKey::tool_glob_no_match, pattern, root->display));
  }
  std::string builder = Text(context, ToolTextKey::tool_glob_found,
                             results->size(), root->display);
  for (const auto &result : *results) {
    builder += result;
    builder += '\n';
  }
  if (results->size() >= kMaximumGlobResults)
    builder += Text(context, ToolTextKey::tool_glob_truncated);
  co_return ToolSuccess(Trim(builder));
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
ExecuteListDirectory(FileToolContext context, std::string arguments_json) {
  auto workspace = co_await WorkspaceRoot(context);
  if (!workspace)
    co_return std::unexpected(std::move(workspace.error()));
  auto parsed = json::Parse(arguments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::invalid_arguments,
                                    std::string{kParametersEmptyMessage}));
  }
  const std::string display_argument = PathArgument(*object);
  auto target = ResolveTarget(*workspace, StringArgument(*object, "path"));
  if (!target) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_list_dir_failed,
                               target.error().message));
  }
  const auto info = co_await context.files->Stat(target->absolute);
  if (!info) {
    co_return ToolFailure(
        Text(context, ToolTextKey::tool_list_dir_not_found, display_argument));
  }
  if (info->kind != ToolFileKind::directory) {
    co_return ToolFailure(Text(
        context, ToolTextKey::tool_list_dir_not_directory, display_argument));
  }
  auto entries = co_await context.files->ListDirectory(target->absolute);
  if (!entries) {
    co_return ToolFailure(Text(context, ToolTextKey::tool_list_dir_failed,
                               entries.error().message));
  }
  if (entries->empty()) {
    co_return ToolSuccess(
        Text(context, ToolTextKey::tool_list_dir_empty, target->display));
  }
  std::ranges::stable_sort(*entries, DirectoryOrder);
  std::string builder =
      Text(context, ToolTextKey::tool_list_dir_content, target->display);
  for (const auto &entry : *entries) {
    const bool is_directory = entry.kind == ToolFileKind::directory;
    builder += is_directory ? "[DIR]  " : "[FILE] ";
    builder += entry.name;
    if (is_directory)
      builder += '/';
    builder += '\n';
  }
  co_return ToolSuccess(Trim(builder));
}

using FileToolExecutor = std::function<
    huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>(
        FileToolContext context, std::string arguments_json)>;

// Declarative tool catalog: Refresh() projects every row into the registry and
// Invoke() dispatches through std::ranges::find, so adding a file tool never
// adds a name-based branch.
struct FileToolDescriptor final {
  std::string_view name;
  std::string_view description;
  std::string_view parameters_json;
  ToolPresentation presentation;
  bool allowed_in_read_only;
  AgentToolCategory agent_category;
  bool agent_selected_by_default;
  FileToolExecutor execute;
};

// FileReadTool/GlobTool/ListDirectoryTool do not override
// isAllowedInReadonlyMode(), so the BaseTool default (false) applies to every
// row: the whole file_ops group stays denied in read-only mode.
const std::array<FileToolDescriptor, 6> kFileTools{{
    {
        .name = kFileReadToolName,
        .description = kFileReadDescription,
        .parameters_json = kFileReadSchema,
        .presentation =
            {.english_name = "Read file",
             .english_description =
                 "Read text or inspect a directory in the current workspace.",
             .chinese_name = "读取文件",
             .chinese_description = "读取当前工作区中的文本，或查看目录内容。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::read,
        .agent_selected_by_default = true,
        .execute = &ExecuteFileRead,
    },
    {
        .name = kFileWriteToolName,
        .description = kFileWriteDescription,
        .parameters_json = kFileWriteSchema,
        .presentation =
            {.english_name = "Write file",
             .english_description =
                 "Create or replace a file in the current workspace.",
             .chinese_name = "写入文件",
             .chinese_description = "在当前工作区中创建文件或替换文件内容。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::write,
        .agent_selected_by_default = false,
        .execute = &ExecuteFileWrite,
    },
    {
        .name = kFileEditToolName,
        .description = kFileEditDescription,
        .parameters_json = kFileEditSchema,
        .presentation = {.english_name = "Edit file",
                         .english_description =
                             "Replace an exact text fragment in a file.",
                         .chinese_name = "编辑文件",
                         .chinese_description =
                             "替换文件中精确匹配的文本片段。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::write,
        .agent_selected_by_default = false,
        .execute = &ExecuteFileEdit,
    },
    {
        .name = kFileDeleteToolName,
        .description = kFileDeleteDescription,
        .parameters_json = kFileDeleteSchema,
        .presentation =
            {.english_name = "Delete files",
             .english_description =
                 "Delete selected files or directories after confirmation.",
             .chinese_name = "删除文件",
             .chinese_description = "经确认后删除指定的文件或目录。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::write,
        .agent_selected_by_default = false,
        .execute = &ExecuteFileDelete,
    },
    {
        .name = kGlobToolName,
        .description = kGlobDescription,
        .parameters_json = kGlobSchema,
        .presentation = {.english_name = "Find files",
                         .english_description =
                             "Find workspace files with a glob pattern.",
                         .chinese_name = "查找文件",
                         .chinese_description =
                             "使用通配模式查找工作区中的文件。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::read,
        .agent_selected_by_default = true,
        .execute = &ExecuteGlob,
    },
    {
        .name = kListDirectoryToolName,
        .description = kListDirectoryDescription,
        .parameters_json = kListDirectorySchema,
        .presentation = {.english_name = "List directory",
                         .english_description =
                             "List files and folders in a workspace directory.",
                         .chinese_name = "列出目录",
                         .chinese_description =
                             "列出工作区目录中的文件和文件夹。"},
        .allowed_in_read_only = false,
        .agent_category = AgentToolCategory::read,
        .agent_selected_by_default = false,
        .execute = &ExecuteListDirectory,
    },
}};

RegisteredTool CatalogEntry(const FileToolDescriptor &descriptor) {
  return RegisteredTool{
      .name = std::string{descriptor.name},
      .description = std::string{descriptor.description},
      .parameters_json = std::string{descriptor.parameters_json},
      .allowed_in_read_only = descriptor.allowed_in_read_only,
      // Every file tool derives its action from a caller-supplied path, so no
      // permanent grant can be narrowed to a stable, reusable action key.
      .agent_category = descriptor.agent_category,
      .category = std::string{kFileOpsGroupId},
      .agent_selected_by_default = descriptor.agent_selected_by_default,
      .presentation = descriptor.presentation,
  };
}

bool FileOpsGroupEnabled(const domain::McpExecutionSettings &settings) {
  const auto found =
      std::ranges::find(settings.groups, kFileOpsGroupId,
                        [](const domain::McpToolGroupState &group) {
                          return std::string_view{group.id};
                        });
  return found != settings.groups.end() && found->enabled &&
         domain::SupportsMcpExecutionMode(found->supported_modes,
                                          settings.mode);
}

} // namespace

FileToolRegistry::FileToolRegistry(
    std::shared_ptr<McpExecutionSettingsService> settings,
    std::shared_ptr<ProjectWorkspaceController> workspace,
    std::shared_ptr<ToolFileAccess> files, ToolTextLanguage language,
    std::shared_ptr<DiffStore> diffs)
    : settings_(std::move(settings)), workspace_(std::move(workspace)),
      files_(std::move(files)), language_(language), diffs_(std::move(diffs)) {
  if (!settings_ || !workspace_ || !files_) {
    throw std::invalid_argument(
        "FileToolRegistry requires settings, project workspace and file access "
        "services");
  }
}

huxerui::Task<std::expected<void, ToolRegistryError>>
FileToolRegistry::Refresh() {
  auto settings = co_await settings_->Load();
  if (!settings) {
    co_return std::unexpected(
        Error(ToolRegistryErrorCode::load_failed, settings.error().message));
  }
  std::vector<RegisteredTool> next_tools;
  if (FileOpsGroupEnabled(*settings)) {
    next_tools.reserve(kFileTools.size());
    for (const auto &descriptor : kFileTools)
      next_tools.push_back(CatalogEntry(descriptor));
  }
  tools_ = std::move(next_tools);
  co_return std::expected<void, ToolRegistryError>{};
}

std::span<const RegisteredTool> FileToolRegistry::Tools() const noexcept {
  return tools_;
}

huxerui::Task<std::expected<ToolInvocationResult, ToolRegistryError>>
FileToolRegistry::Invoke(std::string name, std::string arguments_json) {
  const auto found =
      std::ranges::find(kFileTools, name, &FileToolDescriptor::name);
  if (found == kFileTools.end()) {
    co_return std::unexpected(Error(ToolRegistryErrorCode::unknown_tool,
                                    "Unknown file tool: " + name));
  }
  if (std::ranges::find(tools_, name, &RegisteredTool::name) == tools_.end()) {
    co_return std::unexpected(Error(
        ToolRegistryErrorCode::unavailable,
        "The file tool group is disabled for the current execution mode"));
  }
  co_return co_await found->execute(
      FileToolContext{.projects = workspace_.get(),
                      .files = files_.get(),
                      .language = language_,
                      .diffs = diffs_.get()},
      std::move(arguments_json));
}

} // namespace linecode::application
