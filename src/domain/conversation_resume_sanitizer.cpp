#include "domain/conversation_resume_sanitizer.h"

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <iterator>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <variant>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::domain {
namespace {

namespace json = infrastructure::archive_json;

// `domain/compaction_progress.h` owns the compact-status vocabulary; the
// sanitizer only needs the two values it compares against.
constexpr std::string_view kRunning = "running";
constexpr std::string_view kPending = "pending";
constexpr std::string_view kAccepted = "accepted";
constexpr std::string_view kWaiting = "waiting";
constexpr std::string_view kWaitingUnlock = "waiting_unlock";
constexpr std::string_view kError = "error";

constexpr std::string_view kStatusKey = "status";
constexpr std::string_view kErrorKey = "error";
constexpr std::string_view kOutputKey = "output";
constexpr std::string_view kModelContentKey = "model_content";
constexpr std::string_view kCompactStatusKey = "compact_status";
constexpr std::string_view kReviewStateKey = "review_state";
constexpr std::string_view kToolCallsKey = "tool_calls";
constexpr std::string_view kAgentsKey = "agents";
constexpr std::string_view kFailedKey = "failed";
constexpr std::string_view kRunningKey = "running";
constexpr std::string_view kAgentProgressKey = "linecode_agent_progress";
constexpr std::string_view kPipelineProgressKey =
    "linecode_agent_pipeline_progress";

constexpr std::string_view kRecoveredToolPrefix = "recovered_tool_";
constexpr std::string_view kUnknownId = "unknown";
constexpr std::string_view kTerminatedSeparator = "\n\n";

[[nodiscard]] std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

// Port of `String.trim()`: every code unit <= U+0020 is whitespace.
[[nodiscard]] bool IsJavaTrimByte(const char value) noexcept {
  return static_cast<unsigned char>(value) <= 0x20U;
}

[[nodiscard]] std::string_view Trim(std::string_view text) noexcept {
  while (!text.empty() && IsJavaTrimByte(text.front())) {
    text.remove_prefix(1);
  }
  while (!text.empty() && IsJavaTrimByte(text.back())) {
    text.remove_suffix(1);
  }
  return text;
}

[[nodiscard]] bool IsBlank(std::string_view text) noexcept {
  return Trim(text).empty();
}

[[nodiscard]] bool EqualsIgnoreCase(std::string_view left,
                                    std::string_view right) noexcept {
  if (left.size() != right.size()) {
    return false;
  }
  for (std::size_t index = 0; index < left.size(); ++index) {
    const auto lhs = static_cast<unsigned char>(left[index]);
    const auto rhs = static_cast<unsigned char>(right[index]);
    const auto folded = lhs >= 'A' && lhs <= 'Z'
                            ? static_cast<unsigned char>(lhs - 'A' + 'a')
                            : lhs;
    if (folded != rhs) {
      return false;
    }
  }
  return true;
}

// Port of `JSON.toBoolean(Object)`: booleans keep their value and the strings
// "true" / "false" (case-insensitive) are accepted; anything else is absent.
[[nodiscard]] std::optional<bool> ToBoolean(const json::Value &value) {
  if (const auto *flag = std::get_if<bool>(&value)) {
    return *flag;
  }
  if (const auto *text = std::get_if<std::string>(&value)) {
    if (EqualsIgnoreCase(*text, "true")) {
      return true;
    }
    if (EqualsIgnoreCase(*text, "false")) {
      return false;
    }
  }
  return std::nullopt;
}

// Port of `JSONObject.optBoolean(String)`.
[[nodiscard]] bool OptBoolean(const json::Object &object,
                              std::string_view key) {
  const auto *value = json::Find(object, key);
  if (value == nullptr) {
    return false;
  }
  return ToBoolean(*value).value_or(false);
}

// Port of `JSON.toString(Object)`: JSON null and missing keys yield the
// fallback, containers fall back to their serialized form.
[[nodiscard]] std::string OptString(const json::Object &object,
                                    std::string_view key,
                                    std::string_view fallback = {}) {
  const auto *value = json::Find(object, key);
  if (value == nullptr || std::holds_alternative<json::Null>(*value)) {
    return std::string{fallback};
  }
  if (const auto *text = std::get_if<std::string>(value)) {
    return *text;
  }
  if (const auto *flag = std::get_if<bool>(value)) {
    return *flag ? std::string{"true"} : std::string{"false"};
  }
  if (const auto *number = std::get_if<std::int64_t>(value)) {
    return std::to_string(*number);
  }
  return json::Serialize(*value);
}

// `JSONObject.optJSONArray` / `optJSONObject` return null for absent keys and
// for values of any other type.
[[nodiscard]] json::Array *MutableArray(json::Object &object,
                                        std::string_view key) noexcept {
  const auto found = object.find(key);
  return found == object.end() ? nullptr
                               : std::get_if<json::Array>(&found->second);
}

[[nodiscard]] json::Object *MutableObject(json::Object &object,
                                          std::string_view key) noexcept {
  const auto found = object.find(key);
  return found == object.end() ? nullptr
                               : std::get_if<json::Object>(&found->second);
}

void PutString(json::Object &object, std::string_view key,
               std::string_view value) {
  object.insert_or_assign(std::string{key}, json::Value{std::string{value}});
}

void PutBool(json::Object &object, std::string_view key, bool value) {
  object.insert_or_assign(std::string{key}, json::Value{value});
}

void PutInteger(json::Object &object, std::string_view key,
                std::int64_t value) {
  object.insert_or_assign(std::string{key}, json::Value{value});
}

// Port of `ConversationResumeSanitizer.readRawString`
// (`ConversationResumeSanitizer.java:352-361`).
[[nodiscard]] std::string ReadRawString(std::string_view raw_json,
                                        std::string_view key) {
  if (IsBlank(raw_json)) {
    return {};
  }
  const auto parsed = json::Parse(raw_json);
  if (!parsed) {
    return {};
  }
  const auto *object = json::AsObject(&*parsed);
  if (object == nullptr) {
    return {};
  }
  return OptString(*object, key);
}

// Port of `ConversationResumeSanitizer.putRawString`
// (`ConversationResumeSanitizer.java:363-373`). Unparsable input is returned
// untouched, and every unrelated key survives the rewrite.
[[nodiscard]] std::string PutRawString(std::string_view raw_json,
                                       std::string_view key,
                                       std::string_view value) {
  json::Object object;
  if (!IsBlank(raw_json)) {
    const auto parsed = json::Parse(raw_json);
    const auto *existing = parsed ? json::AsObject(&*parsed) : nullptr;
    if (existing == nullptr) {
      return std::string{raw_json};
    }
    object = *existing;
  }
  PutString(object, key, value);
  return json::Serialize(json::Value{std::move(object)});
}

// `ConversationResumeSanitizer.java:326-328`.
[[nodiscard]] bool IsUnfinishedAgentStatus(std::string_view status) {
  return status == kRunning || status == kWaitingUnlock || status == kPending;
}

// `ConversationResumeSanitizer.java:330-332`.
[[nodiscard]] bool IsUnfinishedPipelineStatus(std::string_view status) {
  return status == kRunning || status == kPending;
}

// `ConversationResumeSanitizer.java:334-336`.
[[nodiscard]] bool IsUnfinishedPipelineAgentStatus(std::string_view status) {
  return status == kRunning || status == kWaiting || status == kPending ||
         status == kWaitingUnlock;
}

// `ConversationResumeSanitizer.java:317-319`.
[[nodiscard]] bool IsCompactRunning(std::string_view raw_json) {
  return ReadRawString(raw_json, kCompactStatusKey) == kRunning;
}

// Port of `ConversationResumeSanitizer.sanitizeNestedToolCalls`
// (`ConversationResumeSanitizer.java:194-228`).
[[nodiscard]] bool SanitizeNestedToolCalls(
    json::Array &calls, std::string_view terminated_message,
    bool terminate_missing_results) {
  bool changed = false;
  for (auto &value : calls) {
    auto *item = std::get_if<json::Object>(&value);
    if (item == nullptr) {
      continue;
    }
    auto *result = MutableObject(*item, "result");
    if (result == nullptr && terminate_missing_results) {
      json::Object recovered;
      PutString(recovered, "content", terminated_message);
      PutBool(recovered, "is_error", true);
      PutString(recovered, "diff_id", "");
      PutString(recovered, "review_state", "");
      PutString(recovered, "review_message", "");
      item->insert_or_assign("result",
                             json::Value{std::move(recovered)});
      changed = true;
    } else if (result != nullptr &&
               IsUnfinishedReviewState(OptString(*result, kReviewStateKey),
                                       OptString(*result, "content"))) {
      PutString(*result, "content", terminated_message);
      PutBool(*result, "is_error", true);
      PutString(*result, kReviewStateKey, "");
      changed = true;
    }
    if (auto *nested = MutableArray(*item, kToolCallsKey)) {
      if (SanitizeNestedToolCalls(*nested, terminated_message,
                                  terminate_missing_results)) {
        changed = true;
      }
    }
  }
  return changed;
}

// Port of `ConversationResumeSanitizer.sanitizeAgentProgress`
// (`ConversationResumeSanitizer.java:133-154`).
[[nodiscard]] bool SanitizeAgentProgress(
    json::Object &object, std::string_view terminated_message) {
  bool changed = false;
  const bool unfinished =
      IsUnfinishedAgentStatus(OptString(object, kStatusKey));
  if (unfinished) {
    PutString(object, kStatusKey, kError);
    PutBool(object, kErrorKey, true);
    changed = true;
    // The legacy code writes back the *trimmed* output, so a payload whose
    // output is only whitespace becomes the terminated notice.
    // `OptString` returns by value, so the trimmed view has to borrow from a
    // named string rather than from the temporary (which dies at the end of
    // the full expression and left the concatenation reading freed bytes).
    const std::string raw_output = OptString(object, kOutputKey);
    const auto output = Trim(raw_output);
    if (output.empty()) {
      PutString(object, kOutputKey, terminated_message);
    } else if (output.find(terminated_message) == std::string_view::npos) {
      std::string concatenated{output};
      concatenated += kTerminatedSeparator;
      concatenated += terminated_message;
      PutString(object, kOutputKey, concatenated);
    }
    PutString(object, kModelContentKey, terminated_message);
  }
  if (auto *calls = MutableArray(object, kToolCallsKey)) {
    if (SanitizeNestedToolCalls(*calls, terminated_message, unfinished)) {
      changed = true;
    }
  }
  return changed;
}

// Port of `ConversationResumeSanitizer.countFailedAgents`
// (`ConversationResumeSanitizer.java:338-350`).
[[nodiscard]] std::int64_t CountFailedAgents(const json::Array *agents) {
  if (agents == nullptr) {
    return 0;
  }
  std::int64_t count = 0;
  for (const auto &value : *agents) {
    const auto *agent = json::AsObject(&value);
    if (agent == nullptr) {
      continue;
    }
    if (OptBoolean(*agent, kErrorKey) ||
        OptString(*agent, kStatusKey) == kError) {
      ++count;
    }
  }
  return count;
}

// Port of `ConversationResumeSanitizer.sanitizePipelineProgress`
// (`ConversationResumeSanitizer.java:156-192`).
[[nodiscard]] bool SanitizePipelineProgress(
    json::Object &object, std::string_view terminated_message) {
  bool changed = false;
  if (IsUnfinishedPipelineStatus(
          OptString(object, kStatusKey, kRunning))) {
    PutString(object, kStatusKey, kError);
    PutBool(object, kErrorKey, true);
    changed = true;
  }
  auto *agents = MutableArray(object, kAgentsKey);
  if (agents != nullptr) {
    for (auto &value : *agents) {
      auto *agent = std::get_if<json::Object>(&value);
      if (agent == nullptr) {
        continue;
      }
      const bool unfinished_agent = IsUnfinishedPipelineAgentStatus(
          OptString(*agent, kStatusKey, kWaiting));
      if (unfinished_agent) {
        PutString(*agent, kStatusKey, kError);
        PutBool(*agent, kErrorKey, true);
        if (IsBlank(OptString(*agent, kOutputKey))) {
          PutString(*agent, kOutputKey, terminated_message);
        }
        changed = true;
      }
      if (auto *calls = MutableArray(*agent, kToolCallsKey)) {
        if (SanitizeNestedToolCalls(*calls, terminated_message,
                                    unfinished_agent)) {
          PutBool(*agent, kErrorKey, true);
          changed = true;
        }
      }
    }
  }
  if (changed) {
    PutInteger(object, kFailedKey, CountFailedAgents(agents));
    PutInteger(object, kRunningKey, 0);
  }
  return changed;
}

// Port of `ConversationResumeSanitizer.readToolCalls`
// (`ConversationResumeSanitizer.java:291-315`).
struct LegacyToolCall final {
  std::string id;
  std::string name;
  std::string arguments_json;
};

[[nodiscard]] std::vector<LegacyToolCall>
ReadToolCalls(std::string_view raw_json) {
  std::vector<LegacyToolCall> calls;
  if (IsBlank(raw_json)) {
    return calls;
  }
  const auto parsed = json::Parse(raw_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr) {
    return calls;
  }
  const auto *array = json::AsArray(json::Find(*object, kToolCallsKey));
  if (array == nullptr) {
    return calls;
  }
  for (const auto &value : *array) {
    const auto *item = json::AsObject(&value);
    if (item == nullptr) {
      continue;
    }
    std::string id{Trim(OptString(*item, "id"))};
    if (id.empty()) {
      continue;
    }
    calls.push_back(LegacyToolCall{
        .id = std::move(id),
        .name = OptString(*item, "name"),
        .arguments_json = OptString(*item, "arguments", "{}"),
    });
  }
  return calls;
}

// Port of `ConversationResumeSanitizer.sanitizeId`
// (`ConversationResumeSanitizer.java:399-402`). Java replaces one UTF-16 code
// unit at a time; this walk replaces one UTF-8 code point at a time, which is
// identical for every BMP character (including CJK ids) and yields one `_`
// instead of two for astral characters.
[[nodiscard]] bool IsAllowedIdByte(const unsigned char value) noexcept {
  return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') ||
         (value >= '0' && value <= '9') || value == '_' || value == '-';
}

[[nodiscard]] std::size_t Utf8SequenceLength(const unsigned char lead) noexcept {
  if ((lead & 0x80U) == 0U) {
    return 1;
  }
  if ((lead & 0xE0U) == 0xC0U) {
    return 2;
  }
  if ((lead & 0xF0U) == 0xE0U) {
    return 3;
  }
  if ((lead & 0xF8U) == 0xF0U) {
    return 4;
  }
  return 1;
}

// Port of `ConversationResumeSanitizer.containsMessageId`
// (`ConversationResumeSanitizer.java:390-397`).
[[nodiscard]] bool ContainsMessageId(
    const std::vector<ResumeMessageRecord> &records, std::string_view id) {
  for (const auto &record : records) {
    if (record.id == id) {
      return true;
    }
  }
  return false;
}

// Port of `ConversationResumeSanitizer.recoveredToolMessageId`
// (`ConversationResumeSanitizer.java:375-388`).
[[nodiscard]] std::string RecoveredToolMessageId(
    std::string_view conversation_id, std::string_view tool_call_id,
    const std::vector<ResumeMessageRecord> &records,
    const std::vector<ResumeMessageRecord> &pending_records) {
  std::string base{kRecoveredToolPrefix};
  base += SanitizeResumeId(conversation_id);
  base += '_';
  base += SanitizeResumeId(tool_call_id);
  std::string candidate = base;
  std::int64_t suffix = 1;
  while (ContainsMessageId(records, candidate) ||
         ContainsMessageId(pending_records, candidate)) {
    candidate = base + "_" + std::to_string(suffix++);
  }
  return candidate;
}

// Port of `ConversationResumeSanitizer.missingToolResults`
// (`ConversationResumeSanitizer.java:230-266`).
[[nodiscard]] std::vector<ResumeMessageRecord> MissingToolResults(
    const ResumeConversationRecord &conversation,
    const std::vector<ResumeMessageRecord> &records,
    const std::vector<std::string> &existing_tool_result_ids,
    std::string_view terminated_message, std::int64_t now_millis) {
  std::vector<ResumeMessageRecord> results;
  std::vector<std::string> added_ids;
  const std::int64_t timestamp =
      conversation.updated_at > 0 ? conversation.updated_at : now_millis;
  for (const auto &record : records) {
    if (record.role != MessageRole::assistant) {
      continue;
    }
    for (const auto &call : ReadToolCalls(record.raw_json)) {
      if (call.id.empty() ||
          std::ranges::find(existing_tool_result_ids, call.id) !=
              existing_tool_result_ids.end() ||
          std::ranges::find(added_ids, call.id) != added_ids.end()) {
        continue;
      }
      ResumeMessageRecord recovered{};
      recovered.id = RecoveredToolMessageId(conversation.id, call.id, records,
                                            results);
      recovered.role = MessageRole::tool;
      recovered.content = std::string{terminated_message};
      // Legacy stamps every recovered result with the conversation's own
      // update time, falling back to the clock when it has none
      // (`ConversationResumeSanitizer.java:238`).
      recovered.timestamp = timestamp;
      recovered.streaming = false;
      recovered.hidden = true;
      recovered.exclude_from_context = false;
      recovered.tool_call_id = call.id;
      recovered.tool_name = call.name;
      recovered.error = true;
      results.push_back(std::move(recovered));
      added_ids.push_back(call.id);
    }
  }
  return results;
}

// Port of `ConversationResumeSanitizer.sanitizeRecord`
// (`ConversationResumeSanitizer.java:62-111`). The record is repaired in place
// and the return value mirrors the legacy `changed` flag, which is exactly the
// `next != record` reference check the caller performed.
[[nodiscard]] bool SanitizeMessageRecord(
    ResumeMessageRecord &record, std::string_view terminated_message) {
  bool changed = false;
  // Read before any rewrite: `putRawString` rewrites the whole payload.
  const std::string review_state =
      ReadRawString(record.raw_json, kReviewStateKey);
  if (IsCompactRunning(record.raw_json)) {
    record.raw_json = PutRawString(record.raw_json, kCompactStatusKey, kError);
    record.streaming = false;
    changed = true;
  }
  if (record.streaming) {
    record.streaming = false;
    changed = true;
  }
  if (record.role == MessageRole::tool) {
    const SanitizedPayload payload =
        SanitizeToolContent(record.content, terminated_message);
    if (payload.content != record.content) {
      record.content = payload.content;
      changed = true;
    }
    if (payload.error && !record.error) {
      record.error = true;
      changed = true;
    }
    if (IsUnfinishedReviewState(review_state, record.content)) {
      if (!record.error) {
        record.error = true;
        changed = true;
      }
      if (!payload.error && terminated_message != record.content) {
        record.content = std::string{terminated_message};
        changed = true;
      }
      if (!review_state.empty()) {
        record.raw_json =
            PutRawString(record.raw_json, kReviewStateKey, "");
        changed = true;
      }
    }
  }
  return changed;
}

} // namespace

// Port of `ConversationResumeSanitizer.isUnfinishedReviewState`
// (`ConversationResumeSanitizer.java:321-324`).
bool IsUnfinishedReviewState(const std::string_view state,
                             const std::string_view content) {
  return state == "running" || state == "pending" ||
         (state == "accepted" && IsBlank(content));
}

bool SanitizeResumeMessages(std::vector<ChatMessage> &messages,
                            const std::string_view terminated_message) {
  const auto terminated = ResolveTerminatedMessage(terminated_message);
  bool changed = false;
  for (auto &message : messages) {
    if (message.streaming) {
      message.streaming = false;
      changed = true;
    }
    // A progress block left mid-flight would otherwise claim to be compacting
    // forever.
    if (message.compact_status == kRunning) {
      message.compact_status = std::string{kError};
      changed = true;
    }
    for (auto &event : message.timeline) {
      auto *tool = std::get_if<AssistantToolEvent>(&event);
      if (tool == nullptr)
        continue;
      if (!tool->result.has_value()) {
        // Legacy `missingToolResults`: the model asked for a tool and the
        // process died before a result was recorded.
        ChatToolResult recovered;
        recovered.call_id = tool->call.id;
        recovered.name = tool->call.name;
        recovered.content = terminated;
        recovered.error = true;
        tool->result = std::move(recovered);
        changed = true;
        continue;
      }
      auto &result = *tool->result;
      if (!IsUnfinishedReviewState(result.review_state, result.content))
        continue;
      // Legacy cleared the review state and marked the result as failed, so a
      // write that never got its decision stops asking for one.
      result.error = true;
      result.content = terminated;
      result.review_state.clear();
      changed = true;
    }
  }
  return changed;
}

std::string ResolveTerminatedMessage(std::string_view terminated_message) {
  return IsBlank(terminated_message) ? std::string{fallback_terminated_message}
                                     : std::string{terminated_message};
}

std::string SanitizeResumeId(std::string_view value) {
  std::string safe;
  safe.reserve(value.size());
  for (std::size_t index = 0; index < value.size();) {
    const auto lead = static_cast<unsigned char>(value[index]);
    const std::size_t length =
        std::min(Utf8SequenceLength(lead), value.size() - index);
    const bool allowed = length == 1 && IsAllowedIdByte(lead);
    safe.push_back(allowed ? value[index] : '_');
    index += length;
  }
  return safe.empty() ? std::string{kUnknownId} : safe;
}

SanitizedPayload SanitizeToolContent(std::string_view content,
                                     std::string_view terminated_message) {
  const auto unchanged = SanitizedPayload{.content = std::string{content},
                                          .changed = false,
                                          .error = false};
  if (IsBlank(content)) {
    return unchanged;
  }
  auto parsed = json::Parse(content);
  if (!parsed) {
    return unchanged;
  }
  auto *object = std::get_if<json::Object>(&*parsed);
  if (object == nullptr) {
    // `new JSONObject(content)` throws for arrays and scalars, which the
    // legacy catch block turns into an unchanged payload.
    return unchanged;
  }
  if (OptBoolean(*object, kAgentProgressKey)) {
    const bool changed =
        SanitizeAgentProgress(*object, terminated_message);
    return SanitizedPayload{
        .content = changed ? json::Serialize(*parsed) : std::string{content},
        .changed = changed,
        .error = changed,
    };
  }
  if (OptBoolean(*object, kPipelineProgressKey)) {
    const bool changed =
        SanitizePipelineProgress(*object, terminated_message);
    return SanitizedPayload{
        .content = changed ? json::Serialize(*parsed) : std::string{content},
        .changed = changed,
        .error = changed,
    };
  }
  return unchanged;
}

ResumeSanitizeResult Sanitize(ResumeConversationRecord conversation,
                              std::string_view terminated_message,
                              std::int64_t now_millis) {
  const std::string message = ResolveTerminatedMessage(terminated_message);
  std::vector<ResumeMessageRecord> records = conversation.messages;
  std::vector<std::string> existing_tool_result_ids;
  bool changed = false;
  for (auto &record : records) {
    if (SanitizeMessageRecord(record, message)) {
      changed = true;
    }
    if (record.role == MessageRole::tool && !record.tool_call_id.empty()) {
      existing_tool_result_ids.push_back(record.tool_call_id);
    }
  }
  auto terminated_results = MissingToolResults(
      conversation, records, existing_tool_result_ids, message, now_millis);
  if (!terminated_results.empty()) {
    std::move(terminated_results.begin(), terminated_results.end(),
              std::back_inserter(records));
    changed = true;
  }
  if (!changed) {
    // Legacy returns the very same conversation instance when nothing
    // changed.
    return ResumeSanitizeResult{.conversation = std::move(conversation),
                                .changed = false};
  }
  conversation.messages = std::move(records);
  return ResumeSanitizeResult{.conversation = std::move(conversation),
                              .changed = true};
}

ResumeSanitizeResult Sanitize(ResumeConversationRecord conversation,
                              std::string_view terminated_message) {
  return Sanitize(std::move(conversation), terminated_message,
                  NowMilliseconds());
}

} // namespace linecode::domain
