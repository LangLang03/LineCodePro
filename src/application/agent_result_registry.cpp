#include "application/agent_result_registry.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <ranges>
#include <span>
#include <string>
#include <string_view>
#include <utility>

#include "application/agent_run_progress_codec.h"
#include "application/utf8_text.h"
#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

// org.json `Object.toString()`: a string yields its text, JSON null yields the
// caller's fallback, and every other value (number, boolean, array, object)
// yields its JSON text. The legacy tools relied on that coercion because
// `JSONObject.optString` accepts non-string values.
std::string OptString(const json::Value *value) {
  if (value == nullptr)
    return {};
  if (const auto *text = json::AsString(value))
    return *text;
  if (std::holds_alternative<json::Null>(*value))
    return {};
  return json::Serialize(*value);
}

// Java `String.trim()`: removes every leading and trailing character <= U+0020.
// Trailing bytes of a multi-byte sequence are always >= 0x80, so a UTF-8
// continuation byte can never be mistaken for whitespace.
std::string Trim(std::string_view text) {
  const auto visible = [](char character) {
    return static_cast<unsigned char>(character) > 0x20U;
  };
  const auto begin = std::ranges::find_if(text, visible);
  const auto end =
      std::ranges::find_if(text | std::views::reverse, visible).base();
  return begin < end ? std::string{begin, end} : std::string{};
}

bool IsBlank(std::string_view text) { return Trim(text).empty(); }

std::string Base36(std::uint64_t value) {
  constexpr std::string_view kDigits = "0123456789abcdefghijklmnopqrstuvwxyz";
  if (value == 0U)
    return "0";
  std::string text;
  while (value > 0U) {
    text.insert(text.begin(), kDigits[value % 36U]);
    value /= 36U;
  }
  return text;
}

// Legacy `ToolResult.truncateContent()`: middle truncation with a marker once
// the body exceeds the 50KB single-result limit. Like the ported file tools,
// the limit counts bytes rather than the legacy UTF-16 units.
std::string TruncateContent(std::string content) {
  if (content.size() <= kAgentOutputMaxResultChars)
    return content;
  constexpr std::size_t kTruncationHalf = kAgentOutputMaxResultChars / 2U;
  const std::size_t truncated = content.size() - kAgentOutputMaxResultChars;
  std::string next{content.substr(0, kTruncationHalf)};
  next += "\n... (";
  next += std::to_string(truncated);
  next += " chars truncated) ...\n";
  next += content.substr(content.size() - kTruncationHalf);
  return next;
}

std::string Text(ToolTextLanguage language, ToolTextKey key,
                 std::span<const std::string> arguments) {
  return ToolText(key, arguments, language);
}

std::string Text(ToolTextLanguage language, ToolTextKey key) {
  return Text(language, key, {});
}

std::string Text(ToolTextLanguage language, ToolTextKey key,
                 const std::string &argument) {
  const std::array<std::string, 1> arguments{argument};
  return Text(language, key, arguments);
}

std::string JsonString(const json::Object &object, std::string_view key,
                       std::string fallback = {}) {
  const auto *value = json::Find(object, key);
  if (value == nullptr)
    return fallback;
  return OptString(value);
}

bool JsonBool(const json::Object &object, std::string_view key, bool fallback) {
  const auto *value = json::Find(object, key);
  if (value == nullptr)
    return fallback;
  if (const auto *flag = std::get_if<bool>(value))
    return *flag;
  if (const auto *number = std::get_if<std::int64_t>(value))
    return *number != 0;
  if (const auto *text = json::AsString(value))
    return *text == "true";
  return fallback;
}

int JsonInt(const json::Object &object, std::string_view key, int fallback) {
  const auto *value = json::Find(object, key);
  if (value == nullptr)
    return fallback;
  if (const auto *number = std::get_if<std::int64_t>(value))
    return static_cast<int>(*number);
  if (const auto *real = std::get_if<double>(value))
    return static_cast<int>(*real);
  if (const auto *text = json::AsString(value)) {
    try {
      return std::stoi(*text);
    } catch (...) {
      return fallback;
    }
  }
  return fallback;
}

} // namespace

std::int64_t AgentResultNowMillis() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

std::string ClampAgentPreview(std::string_view value) {
  if (value.empty())
    return {};
  const auto trimmed = Trim(value);
  if (utf8::Utf16CodeUnitLength(trimmed) <= kAgentPreviewMaxChars)
    return trimmed;
  return trimmed.substr(
      0U, utf8::PrefixBytesForUtf16Units(trimmed, kAgentPreviewMaxChars));
}

std::string AgentPreviewFrom(std::string_view full_output) {
  if (full_output.empty())
    return {};
  return ClampAgentPreview(full_output);
}

AgentResultRecord CreateAgentResultRecord(
    std::string agent_id, std::string tool_call_id, std::string tool_name,
    std::string status, std::string type, std::string description,
    std::string preview, std::string full_output, std::string thinking,
    std::string progress_json, int tool_call_count, bool error, bool async,
    int generation_id, std::int64_t updated_at_ms) {
  return AgentResultRecord{
      .agent_id = std::move(agent_id),
      .tool_call_id = std::move(tool_call_id),
      .tool_name = std::move(tool_name),
      .status = status.empty() ? std::string{"running"} : std::move(status),
      .type = std::move(type),
      .description = std::move(description),
      .preview = ClampAgentPreview(preview),
      .full_output = std::move(full_output),
      .thinking = std::move(thinking),
      .progress_json = std::move(progress_json),
      .tool_call_count = std::max(0, tool_call_count),
      .error = error,
      .async = async,
      .generation_id = generation_id,
      .updated_at_ms =
          updated_at_ms <= 0 ? AgentResultNowMillis() : updated_at_ms,
  };
}

AgentResultRecord AgentResultRecord::Running(
    std::string agent_id, std::string tool_call_id, std::string tool_name,
    std::string type, std::string description, bool async, int generation_id) {
  return CreateAgentResultRecord(
      std::move(agent_id), std::move(tool_call_id), std::move(tool_name),
      "running", std::move(type), std::move(description), "", "", "", "", 0,
      false, async, generation_id, AgentResultNowMillis());
}

AgentResultRecord
AgentResultRecord::WithPreview(std::string next_preview) const {
  return CreateAgentResultRecord(
      agent_id, tool_call_id, tool_name, status, type, description,
      std::move(next_preview), full_output, thinking, progress_json,
      tool_call_count, error, async, generation_id, AgentResultNowMillis());
}

AgentResultRecord
AgentResultRecord::WithStatus(std::string next_status, bool next_error,
                              std::string next_preview) const {
  return CreateAgentResultRecord(agent_id, tool_call_id, tool_name,
                                 std::move(next_status), type, description,
                                 std::move(next_preview), full_output, thinking,
                                 progress_json, tool_call_count, next_error,
                                 async, generation_id, AgentResultNowMillis());
}

AgentResultRecord AgentResultRecord::WithFullOutput(
    std::string next_full_output, std::string next_thinking,
    std::string next_progress_json, int next_tool_call_count,
    bool next_error) const {
  // Legacy `withFullOutput` derives the status from the error flag and the
  // preview from the full output.
  auto next_preview = AgentPreviewFrom(next_full_output);
  return CreateAgentResultRecord(
      agent_id, tool_call_id, tool_name, next_error ? "error" : "done", type,
      description, std::move(next_preview), std::move(next_full_output),
      std::move(next_thinking), std::move(next_progress_json),
      next_tool_call_count, next_error, async, generation_id,
      AgentResultNowMillis());
}

bool AgentResultRecord::IsRunning() const noexcept {
  return status == "running" || status == "pending" ||
         status == "waiting_unlock";
}

AgentOutputInclude ParseAgentOutputInclude(std::string_view value) noexcept {
  return Trim(value) == "meta" ? AgentOutputInclude::meta
                               : AgentOutputInclude::output;
}

std::string AgentResultRegistry::AllocateId() {
  const std::uint64_t sequence = sequence_++;
  std::string id{"ag_"};
  id += Base36(static_cast<std::uint64_t>(AgentResultNowMillis()));
  id += '_';
  id += Base36(sequence);
  return id;
}

void AgentResultRegistry::Put(AgentResultRecord record) {
  if (record.agent_id.empty())
    return;
  record = CreateAgentResultRecord(
      record.agent_id, record.tool_call_id, record.tool_name, record.status,
      record.type, record.description, record.preview, record.full_output,
      record.thinking, record.progress_json, record.tool_call_count,
      record.error, record.async, record.generation_id, record.updated_at_ms);
  const std::scoped_lock guard{lock_};
  const auto found = std::ranges::find(records_, record.agent_id,
                                       &AgentResultRecord::agent_id);
  if (found == records_.end())
    records_.push_back(std::move(record));
  else
    *found = std::move(record);
}

std::optional<AgentResultRecord>
AgentResultRegistry::GetRecord(std::string_view agent_id) const {
  if (agent_id.empty())
    return std::nullopt;
  const std::scoped_lock guard{lock_};
  const auto found =
      std::ranges::find(records_, agent_id, &AgentResultRecord::agent_id);
  return found == records_.end() ? std::nullopt
                                 : std::optional<AgentResultRecord>{*found};
}

std::optional<AgentResultView>
AgentResultRegistry::Read(const std::string_view agent_id) const {
  const auto record = GetRecord(agent_id);
  if (!record)
    return std::nullopt;
  auto progress = ParseAgentProgress(record->progress_json);
  return AgentResultView{
      .agent_id = record->agent_id,
      .status = record->status,
      .type = record->type,
      .description = record->description,
      .preview = record->preview,
      .full_output = record->full_output,
      .thinking = record->thinking,
      .tool_call_count = record->tool_call_count,
      .error = record->error,
      .async = record->async,
      .progress =
          progress ? std::move(*progress) : domain::AgentProgressSnapshot{},
  };
}

std::optional<AgentResultVersion>
AgentResultRegistry::ReadVersion(const std::string_view agent_id) const {
  if (agent_id.empty())
    return std::nullopt;
  const std::scoped_lock guard{lock_};
  const auto found =
      std::ranges::find(records_, agent_id, &AgentResultRecord::agent_id);
  if (found == records_.end())
    return std::nullopt;
  return AgentResultVersion{
      .updated_at_ms = found->updated_at_ms,
      .output_bytes = found->full_output.size(),
      .thinking_bytes = found->thinking.size(),
      .tool_call_count = found->tool_call_count,
      .error = found->error,
      .status_fingerprint = std::hash<std::string_view>{}(found->status),
  };
}

bool AgentResultRegistry::Contains(std::string_view agent_id) const {
  return GetRecord(agent_id).has_value();
}

std::vector<std::string> AgentResultRegistry::AgentIds() const {
  const std::scoped_lock guard{lock_};
  std::vector<std::string> ids;
  ids.reserve(records_.size());
  for (const auto &record : records_)
    ids.push_back(record.agent_id);
  return ids;
}

std::size_t AgentResultRegistry::Size() const {
  const std::scoped_lock guard{lock_};
  return records_.size();
}

void AgentResultRegistry::UpdateStatus(std::string_view agent_id,
                                       std::string status, bool error,
                                       std::string preview) {
  const std::scoped_lock guard{lock_};
  const auto found =
      std::ranges::find(records_, agent_id, &AgentResultRecord::agent_id);
  if (found == records_.end())
    return;
  *found = found->WithStatus(std::move(status), error, std::move(preview));
}

void AgentResultRegistry::UpdateFullOutput(std::string_view agent_id,
                                           std::string full_output,
                                           std::string thinking,
                                           std::string progress_json,
                                           int tool_call_count, bool error) {
  const std::scoped_lock guard{lock_};
  const auto found =
      std::ranges::find(records_, agent_id, &AgentResultRecord::agent_id);
  if (found == records_.end())
    return;
  *found =
      found->WithFullOutput(std::move(full_output), std::move(thinking),
                            std::move(progress_json), tool_call_count, error);
}

void AgentResultRegistry::ClearGeneration(int generation_id) {
  const std::scoped_lock guard{lock_};
  std::erase_if(records_, [generation_id](const AgentResultRecord &record) {
    return record.generation_id == generation_id;
  });
}

void AgentResultRegistry::Clear() {
  const std::scoped_lock guard{lock_};
  records_.clear();
}

std::string
AgentResultRegistry::ToCompactJson(const AgentResultRecord &record) {
  json::Object object;
  object.emplace(std::string{kCompactMarker}, json::Value{true});
  object.emplace("agent_id", json::Value{record.agent_id});
  object.emplace("status", json::Value{record.status});
  object.emplace("type", json::Value{record.type});
  object.emplace("description", json::Value{record.description});
  object.emplace("preview", json::Value{record.preview});
  object.emplace("tool_call_count", json::Value{static_cast<std::int64_t>(
                                        record.tool_call_count)});
  object.emplace("error", json::Value{record.error});
  object.emplace("async", json::Value{record.async});
  // Legacy only emits the originating tool call id when it has one.
  if (!record.tool_call_id.empty())
    object.emplace("tool_call_id", json::Value{record.tool_call_id});
  return json::Serialize(json::Value{std::move(object)});
}

std::optional<AgentResultRecord>
AgentResultRegistry::ParseCompact(std::string_view content) {
  if (IsBlank(content))
    return std::nullopt;
  auto parsed = json::Parse(content);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr)
    return std::nullopt;
  if (!JsonBool(*object, kCompactMarker, false))
    return std::nullopt;
  auto agent_id = Trim(JsonString(*object, "agent_id"));
  if (agent_id.empty())
    return std::nullopt;
  return CreateAgentResultRecord(
      std::move(agent_id), JsonString(*object, "tool_call_id"), "",
      JsonString(*object, "status", "running"), JsonString(*object, "type"),
      JsonString(*object, "description"), JsonString(*object, "preview"), "",
      "", "", JsonInt(*object, "tool_call_count", 0),
      JsonBool(*object, "error", false), JsonBool(*object, "async", false), 0,
      AgentResultNowMillis());
}

std::string AgentResultRegistry::MetaJson(const AgentResultRecord &record) {
  json::Object object;
  object.emplace("agent_id", json::Value{record.agent_id});
  object.emplace("status", json::Value{record.status});
  object.emplace("type", json::Value{record.type});
  object.emplace("description", json::Value{record.description});
  object.emplace("preview", json::Value{record.preview});
  object.emplace("error", json::Value{record.error});
  object.emplace("async", json::Value{record.async});
  object.emplace("tool_call_count", json::Value{static_cast<std::int64_t>(
                                        record.tool_call_count)});
  if (!record.thinking.empty())
    object.emplace("thinking", json::Value{record.thinking});
  if (!record.progress_json.empty()) {
    auto progress = json::Parse(record.progress_json);
    object.emplace("progress", progress ? std::move(*progress)
                                        : json::Value{record.progress_json});
  }
  return json::Serialize(json::Value{std::move(object)});
}

std::string AgentResultRegistry::RunningJson(const AgentResultRecord &record,
                                             std::string_view message) {
  json::Object object;
  object.emplace("agent_id", json::Value{record.agent_id});
  object.emplace("status", json::Value{record.status});
  object.emplace("type", json::Value{record.type});
  object.emplace("description", json::Value{record.description});
  object.emplace("preview", json::Value{record.preview});
  object.emplace("async", json::Value{record.async});
  object.emplace("message", json::Value{std::string{message}});
  return json::Serialize(json::Value{std::move(object)});
}

AgentOutputResult AgentResultRegistry::Fetch(std::string_view agent_id,
                                             std::string_view include,
                                             ToolTextLanguage language) const {
  // Legacy resolves `include` before the running/body decision, so an async
  // caller can read the status fields without waiting.
  const auto record = GetRecord(agent_id);
  if (!record) {
    return AgentOutputResult{
        .content = Text(language, ToolTextKey::tool_agent_output_not_found,
                        std::string{agent_id}),
        .error = true,
    };
  }
  if (ParseAgentOutputInclude(include) == AgentOutputInclude::meta) {
    return AgentOutputResult{.content = MetaJson(*record), .error = false};
  }
  if (record->IsRunning()) {
    return AgentOutputResult{
        .content = RunningJson(
            *record,
            Text(language, ToolTextKey::tool_agent_output_still_running)),
        .error = false,
    };
  }
  std::string body = record->full_output;
  if (IsBlank(body))
    body = record->preview;
  if (body.size() > kAgentOutputMaxResultChars)
    body = TruncateContent(std::move(body));
  if (record->error) {
    return AgentOutputResult{
        .content = body.empty()
                       ? Text(language, ToolTextKey::tool_agent_output_failed)
                       : std::move(body),
        .error = true,
    };
  }
  return AgentOutputResult{
      .content = body.empty()
                     ? Text(language, ToolTextKey::tool_agent_output_empty)
                     : std::move(body),
      .error = false,
  };
}

std::optional<AgentResultView>
ResolveAgentResultReference(const std::string_view compact_ref,
                            const AgentResultReader &reader) {
  const auto reference = AgentResultRegistry::ParseCompact(compact_ref);
  if (!reference)
    return std::nullopt;
  return reader.Read(reference->agent_id);
}

} // namespace linecode::application
