// Contract tests for the ported `ConversationResumeSanitizer`
// (`app/src/main/java/cn/lineai/mvp/ConversationResumeSanitizer.java`).
//
// Every branch of the legacy sanitizer is pinned here with a positive case and
// a "must not change" counter-case, plus the JSON round-trip guarantees the
// legacy `JSONObject` rewrite provided.

#include <cassert>
#include <cstdint>
#include <iostream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "domain/conversation_resume_sanitizer.h"
#include "infrastructure/archive_json.h"

namespace {

namespace json = linecode::infrastructure::archive_json;

using linecode::domain::AssistantToolEvent;
using linecode::domain::ChatMessage;
using linecode::domain::ChatToolResult;
using linecode::domain::MessageRole;
using linecode::domain::ResolveTerminatedMessage;
using linecode::domain::ResumeConversationRecord;
using linecode::domain::ResumeMessageRecord;
using linecode::domain::ResumeSanitizeResult;
using linecode::domain::Sanitize;
using linecode::domain::SanitizeResumeId;
using linecode::domain::SanitizeResumeMessages;
using linecode::domain::SanitizedPayload;
using linecode::domain::SanitizeToolContent;

constexpr std::string_view kFallback = "上次生成已中断。";

// The injected clock stands in for `System.currentTimeMillis()`; the harness
// conversation carries `updated_at = 777` so the recovered timestamps are
// deterministic.
constexpr std::int64_t kNowMillis = 4242;
constexpr std::int64_t kUpdatedAt = 777;

// Parses into a function-local static that the next call overwrites, so the
// returned reference is only valid until another `ObjectOf`, `FieldText`,
// `FieldBool`, `FieldInteger` or `HasField` call. Tests that need to keep
// pointers into a payload must parse it into a local instead.
const json::Object &ObjectOf(std::string_view payload) {
  static json::Object scratch;
  auto parsed = json::Parse(payload);
  assert(parsed.has_value());
  const auto *object = json::AsObject(&*parsed);
  assert(object != nullptr);
  scratch = *object;
  return scratch;
}

std::string FieldText(std::string_view payload, std::string_view key) {
  const auto *value = json::AsString(json::Find(ObjectOf(payload), key));
  assert(value != nullptr);
  return *value;
}

std::int64_t FieldInteger(std::string_view payload, std::string_view key) {
  const auto *value =
      std::get_if<std::int64_t>(json::Find(ObjectOf(payload), key));
  assert(value != nullptr);
  return *value;
}

bool FieldBool(std::string_view payload, std::string_view key) {
  const auto *value = std::get_if<bool>(json::Find(ObjectOf(payload), key));
  assert(value != nullptr);
  return *value;
}

bool HasField(std::string_view payload, std::string_view key) {
  return json::Find(ObjectOf(payload), key) != nullptr;
}

ResumeMessageRecord Message(std::string id, MessageRole role,
                            std::string content) {
  ResumeMessageRecord message;
  message.id = std::move(id);
  message.role = role;
  message.content = std::move(content);
  message.timestamp = 10;
  return message;
}

ResumeMessageRecord ToolMessage(std::string id, std::string content,
                                std::string raw_json) {
  auto message = Message(std::move(id), MessageRole::tool, std::move(content));
  message.raw_json = std::move(raw_json);
  return message;
}

ResumeConversationRecord Conversation(
    std::vector<ResumeMessageRecord> messages) {
  ResumeConversationRecord conversation;
  conversation.id = "conv:1";
  conversation.title = "会话标题";
  conversation.project_id = "project-1";
  conversation.created_at = 100;
  conversation.updated_at = kUpdatedAt;
  conversation.current = true;
  conversation.raw_json = R"({"pinned":true})";
  conversation.messages = std::move(messages);
  return conversation;
}

ResumeSanitizeResult Run(ResumeConversationRecord conversation,
                         std::string_view terminated_message = kFallback) {
  return Sanitize(std::move(conversation), terminated_message, kNowMillis);
}

// --- entry point / fallback text ------------------------------------------

void ResolveTerminatedMessageFallsBack() {
  assert(ResolveTerminatedMessage("") == kFallback);
  assert(ResolveTerminatedMessage("   ") == kFallback);
  assert(ResolveTerminatedMessage("\n\t ") == kFallback);
  assert(ResolveTerminatedMessage("自定义中断说明") == "自定义中断说明");
  // Only surrounding whitespace is trimmed away; the message itself travels
  // verbatim, exactly like the legacy `terminatedMessage` parameter.
  assert(ResolveTerminatedMessage("  自定义 ") == "  自定义 ");

  // A blank message reaches the fallback through the whole sanitizer.
  auto blank = Run(Conversation({ToolMessage("tool-1", "", R"({"review_state":"running"})")}),
                   "");
  assert(blank.changed);
  assert(blank.conversation.messages.at(0).content == kFallback);
}

void SanitizeIdMatchesLegacyReplaceAll() {
  assert(SanitizeResumeId("call_1-A") == "call_1-A");
  assert(SanitizeResumeId("conv:1/2 3") == "conv_1_2_3");
  // One `_` per non-ASCII code point, like Java's per-code-unit replacement.
  assert(SanitizeResumeId("会话") == "__");
  assert(SanitizeResumeId("") == "unknown");
}

// --- per-record repairs ----------------------------------------------------

void StreamingFlagIsCleared() {
  auto message = Message("assistant-1", MessageRole::assistant, "回答");
  message.streaming = true;
  const auto result = Run(Conversation({message}));
  assert(result.changed);
  assert(!result.conversation.messages.at(0).streaming);
  // Every other field survives untouched.
  assert(result.conversation.messages.at(0).content == "回答");
  assert(result.conversation.messages.at(0).id == "assistant-1");

  // Counter-case: an already finished message is not reported as changed.
  auto finished = Message("assistant-2", MessageRole::assistant, "回答");
  const auto untouched = Run(Conversation({finished}));
  assert(!untouched.changed);
}

void CompactStatusRunningBecomesError() {
  auto running = Message("assistant-1", MessageRole::assistant, "");
  running.streaming = true;
  running.exclude_from_context = true;
  running.raw_json = R"({"compact_status":"running","other":1})";
  const auto result = Run(Conversation({running}));
  assert(result.changed);
  const auto &repaired = result.conversation.messages.at(0);
  assert(!repaired.streaming);
  assert(FieldText(repaired.raw_json, "compact_status") == "error");
  // Unrelated raw_json keys survive the rewrite.
  assert(FieldInteger(repaired.raw_json, "other") == 1);

  // Counter-case: a finished compact block keeps its status byte-for-byte.
  auto done = Message("assistant-2", MessageRole::assistant, "");
  done.raw_json = R"({"compact_status":"done"})";
  const auto untouched = Run(Conversation({done}));
  assert(!untouched.changed);
  assert(untouched.conversation.messages.at(0).raw_json ==
         R"({"compact_status":"done"})");

  // `error` is terminal as well and must not be rewritten.
  auto errored = Message("assistant-3", MessageRole::assistant, "");
  errored.raw_json = R"({"compact_status":"error"})";
  assert(!Run(Conversation({errored})).changed);
}

void ToolReviewStatesAreRepaired() {
  // (1) running / (2) pending / (3) accepted with blank content are unfinished.
  for (const std::string_view state : {"running", "pending"}) {
    auto message = ToolMessage("tool-1", "",
                               R"({"review_state":"{{state}}","diff_id":"d1"})");
    const auto marker = message.raw_json.find("{{state}}");
    message.raw_json.replace(marker, 9, state);
    const auto result = Run(Conversation({message}));
    assert(result.changed);
    const auto &repaired = result.conversation.messages.at(0);
    assert(repaired.error);
    assert(repaired.content == kFallback);
    assert(FieldText(repaired.raw_json, "review_state").empty());
    // The unrelated `diff_id` survives the raw_json rewrite.
    assert(FieldText(repaired.raw_json, "diff_id") == "d1");
  }

  auto accepted_empty = ToolMessage("tool-2", "   ",
                                    R"({"review_state":"accepted"})");
  const auto accepted_result = Run(Conversation({accepted_empty}));
  assert(accepted_result.changed);
  assert(accepted_result.conversation.messages.at(0).error);
  assert(accepted_result.conversation.messages.at(0).content == kFallback);
  assert(FieldText(accepted_result.conversation.messages.at(0).raw_json,
                   "review_state")
             .empty());

  // Counter-cases: settled review states stay put.
  for (const std::string_view raw :
       {R"({"review_state":"accepted","content":"ok"})",
        R"({"review_state":"rejected"})", R"({"review_state":""})", R"({})"}) {
    auto message = ToolMessage("tool-3", "工具结果", std::string{raw});
    const auto result = Run(Conversation({message}));
    assert(!result.changed);
    assert(result.conversation.messages.at(0).content == "工具结果");
    assert(!result.conversation.messages.at(0).error);
  }

  // An "accepted" state whose *content* already carries text is finished even
  // when `content` and `raw_json.content` disagree.
  auto accepted_text = ToolMessage("tool-4", "有内容",
                                   R"({"review_state":"accepted"})");
  assert(!Run(Conversation({accepted_text})).changed);

  // A missing tool result already marked as an error still gets the terminated
  // text and a cleared review state.
  auto already_error = ToolMessage("tool-5", "",
                                   R"({"review_state":"running"})");
  already_error.error = true;
  const auto error_result = Run(Conversation({already_error}));
  assert(error_result.changed);
  assert(error_result.conversation.messages.at(0).content == kFallback);
  assert(error_result.conversation.messages.at(0).error);
}

// --- tool content JSON -----------------------------------------------------

void AgentProgressRepairsUnfinishedPayloads() {
  const std::string payload = R"({
    "linecode_agent_progress": true,
    "status": "running",
    "name": "explorer",
    "model_content": "  ",
    "meta": {"step": 2, "tags": ["a", "b"]}
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(sanitized.changed);
  assert(sanitized.error);
  assert(FieldText(sanitized.content, "status") == "error");
  assert(FieldBool(sanitized.content, "error"));
  assert(FieldText(sanitized.content, "output") == kFallback);
  assert(FieldText(sanitized.content, "model_content") == kFallback);
  // Round-trip fidelity: unrelated fields of every JSON type survive. `meta`
  // is an object, so it is checked for presence rather than read as a number
  // (the numeric helpers assert on a type mismatch instead of returning a
  // sentinel, so they cannot be used as a fallible probe).
  assert(FieldText(sanitized.content, "name") == "explorer");
  assert(HasField(sanitized.content, "meta"));
  assert(sanitized.content.find("\"explorer\"") != std::string::npos);
  assert(sanitized.content.find("\"step\":2") != std::string::npos);
  assert(sanitized.content.find("[\"a\",\"b\"]") != std::string::npos);
}

void AgentProgressAppendsTerminatedNoticeToOutput() {
  const std::string payload = R"({
    "linecode_agent_progress": true,
    "status": "waiting_unlock",
    "output": "  已经跑了一半  "
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(sanitized.changed);
  // The legacy code appends to the *trimmed* output.
  assert(FieldText(sanitized.content, "output") ==
         std::string{"已经跑了一半"} + "\n\n" + std::string{kFallback});

  // An output that already mentions the notice is not duplicated, and the
  // original (untrimmed) value is preserved.
  const std::string contained = R"({
    "linecode_agent_progress": true,
    "status": "pending",
    "output": " 上次生成已中断。 "
  })";
  const auto kept = SanitizeToolContent(contained, kFallback);
  assert(kept.changed);
  assert(FieldText(kept.content, "output") == " 上次生成已中断。 ");
  assert(FieldText(kept.content, "model_content") == kFallback);
}

void AgentProgressLeavesFinishedPayloadsAlone() {
  for (const std::string_view status :
       {"completed", "done", "error", "waiting"}) {
    std::string payload = R"({"linecode_agent_progress":true,"status":""})";
    const auto marker = payload.find("\"\"");
    payload.replace(marker, 2, "\"" + std::string{status} + "\"");
    const auto sanitized = SanitizeToolContent(payload, kFallback);
    // `waiting` is *not* an unfinished agent status: only running /
    // waiting_unlock / pending are.
    assert(!sanitized.changed);
    assert(!sanitized.error);
    assert(sanitized.content == payload);
  }
}

void PipelineProgressRepairsAgentsAndCountsFailures() {
  const std::string payload = R"({
    "linecode_agent_pipeline_progress": true,
    "status": "running",
    "title": "流水线",
    "agents": [
      {"id": "a", "status": "running", "output": ""},
      {"id": "b", "status": "waiting", "output": "半个输出"},
      {"id": "c", "status": "completed", "output": "done"}
    ]
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(sanitized.changed);
  assert(FieldText(sanitized.content, "status") == "error");
  assert(FieldBool(sanitized.content, "error"));
  // Two agents were unfinished, so `failed` counts two and nothing runs.
  assert(FieldInteger(sanitized.content, "failed") == 2);
  assert(FieldInteger(sanitized.content, "running") == 0);
  assert(FieldText(sanitized.content, "title") == "流水线");
  assert(sanitized.content.find("\"半个输出\"") != std::string::npos);
}

void PipelineProgressKeepsFinishedPayloads() {
  const std::string payload = R"({
    "linecode_agent_pipeline_progress": true,
    "status": "completed",
    "agents": [
      {"id": "a", "status": "completed", "output": "ok"},
      {"id": "b", "status": "error", "output": "boom", "error": true}
    ]
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(!sanitized.changed);
  assert(!sanitized.error);
  // Byte-for-byte: an untouched payload is never re-serialized.
  assert(sanitized.content == payload);
}

void PipelineAgentFailureCountUsesErrorFlags() {
  const std::string payload = R"({
    "linecode_agent_pipeline_progress": true,
    "status": "pending",
    "agents": [
      {"id": "a", "status": "completed", "error": true},
      {"id": "b", "status": "error"},
      {"id": "c", "status": "completed"}
    ]
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(sanitized.changed);
  // `countFailedAgents` counts `error == true` *or* `status == "error"`.
  assert(FieldInteger(sanitized.content, "failed") == 2);
  assert(FieldInteger(sanitized.content, "running") == 0);
}

void NestedToolCallsAreRepaired() {
  const std::string payload = R"({
    "linecode_agent_progress": true,
    "status": "running",
    "output": "working",
    "tool_calls": [
      {"id": "call_1", "name": "read_file"},
      {"id": "call_2", "name": "write_file",
       "result": {"content": "", "is_error": false, "review_state": "running",
                  "diff_id": "d9", "review_message": "pending"}}
    ]
  })";
  const auto sanitized = SanitizeToolContent(payload, kFallback);
  assert(sanitized.changed);
  // Parsed locally: the helpers below re-parse and would overwrite the shared
  // scratch object these pointers walk into.
  const auto sanitized_doc = json::Parse(sanitized.content);
  assert(sanitized_doc.has_value());
  const auto &object = *json::AsObject(&*sanitized_doc);
  const auto *calls = json::AsArray(json::Find(object, "tool_calls"));
  assert(calls != nullptr);
  assert(calls->size() == 2);

  const auto *first = json::AsObject(&calls->at(0));
  assert(first != nullptr);
  const auto *recovered = json::AsObject(json::Find(*first, "result"));
  assert(recovered != nullptr);
  assert(FieldText(json::Serialize(json::Value{*recovered}), "content") ==
         kFallback);
  assert(FieldBool(json::Serialize(json::Value{*recovered}), "is_error"));
  assert(FieldText(json::Serialize(json::Value{*recovered}), "diff_id")
             .empty());
  assert(FieldText(json::Serialize(json::Value{*recovered}), "review_state")
             .empty());
  assert(FieldText(json::Serialize(json::Value{*recovered}), "review_message")
             .empty());

  const auto *second = json::AsObject(&calls->at(1));
  assert(second != nullptr);
  const auto *repaired = json::AsObject(json::Find(*second, "result"));
  assert(repaired != nullptr);
  const auto repaired_text = json::Serialize(json::Value{*repaired});
  assert(FieldText(repaired_text, "content") == kFallback);
  assert(FieldBool(repaired_text, "is_error"));
  assert(FieldText(repaired_text, "review_state").empty());
  // The unrelated `review_message` / `diff_id` keys survive.
  assert(FieldText(repaired_text, "review_message") == "pending");
  assert(FieldText(repaired_text, "diff_id") == "d9");
}

void NestedToolCallsRespectTerminateFlagAndRecurse() {
  // A finished agent keeps its unresolved calls: `terminateMissingResults` is
  // `unfinished`, which is false here.
  const std::string finished = R"({
    "linecode_agent_progress": true,
    "status": "completed",
    "tool_calls": [{"id": "call_1", "name": "read_file"}]
  })";
  const auto untouched = SanitizeToolContent(finished, kFallback);
  assert(!untouched.changed);
  assert(untouched.content == finished);

  // A settled review state on an otherwise unfinished agent is still not
  // rewritten.
  const std::string settled = R"({
    "linecode_agent_progress": true,
    "status": "running",
    "tool_calls": [{"id": "call_1", "name": "read_file",
                    "result": {"content": "ok", "is_error": false,
                               "review_state": "accepted"}}]
  })";
  const auto sanitized = SanitizeToolContent(settled, kFallback);
  assert(sanitized.changed);
  assert(sanitized.content.find("\"content\":\"ok\"") != std::string::npos);

  // Recursion reaches tool calls nested inside a call.
  const std::string nested = R"({
    "linecode_agent_progress": true,
    "status": "running",
    "tool_calls": [
      {"id": "call_1", "name": "spawn",
       "tool_calls": [{"id": "call_2", "name": "inner"}]}
    ]
  })";
  const auto recursed = SanitizeToolContent(nested, kFallback);
  assert(recursed.changed);
  assert(recursed.content.find("\"call_2\"") != std::string::npos);
  // Parsed locally: the helpers below re-parse and would overwrite the shared
  // scratch object these pointers walk into.
  const auto recursed_doc = json::Parse(recursed.content);
  assert(recursed_doc.has_value());
  const auto &object = *json::AsObject(&*recursed_doc);
  const auto *outer = json::AsArray(json::Find(object, "tool_calls"));
  assert(outer != nullptr);
  const auto *outer_item = json::AsObject(&outer->at(0));
  assert(outer_item != nullptr);
  const auto *inner = json::AsArray(json::Find(*outer_item, "tool_calls"));
  assert(inner != nullptr);
  const auto *inner_item = json::AsObject(&inner->at(0));
  assert(inner_item != nullptr);
  assert(json::Find(*inner_item, "result") != nullptr);
}

void ToolContentIgnoresEverythingItDoesNotOwn() {
  // Blank, non-object, unparsable and plain payloads are returned verbatim.
  for (const std::string_view content :
       {"", "   ", "not json", R"([1,2,3])", R"("text")", R"({"a":1})",
        R"({"linecode_agent_progress":false,"status":"running"})"}) {
    const auto sanitized = SanitizeToolContent(content, kFallback);
    assert(!sanitized.changed);
    assert(!sanitized.error);
    assert(sanitized.content == content);
  }

  // A tool message carrying a plain (non-progress) payload is untouched, even
  // while its review state is settled.
  auto message =
      ToolMessage("tool-1", R"({"result":"plain"})", R"({"review_state":""})");
  const auto result = Run(Conversation({message}));
  assert(!result.changed);

  // A tool message carrying a running progress payload becomes an error and
  // keeps its (repaired) JSON as content.
  auto progress = ToolMessage(
      "tool-2",
      R"({"linecode_agent_progress":true,"status":"running"})", R"({})");
  const auto repaired = Run(Conversation({progress}));
  assert(repaired.changed);
  assert(repaired.conversation.messages.at(0).error);
  assert(FieldText(repaired.conversation.messages.at(0).content, "status") ==
         "error");
}

// --- recovered tool results ------------------------------------------------

void MissingToolResultsAreAppended() {
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file","arguments":"{}"},)"
      R"({"id":"call_2","name":"write_file","arguments":"{\"p\":1}"}]})";
  const auto result = Run(Conversation({assistant}));
  assert(result.changed);
  assert(result.conversation.messages.size() == 3);

  const auto &first = result.conversation.messages.at(1);
  assert(first.id == "recovered_tool_conv_1_call_1");
  assert(first.role == MessageRole::tool);
  assert(first.content == kFallback);
  assert(first.reasoning_content.empty());
  assert(!first.streaming);
  assert(first.hidden);
  assert(!first.exclude_from_context);
  assert(first.tool_call_id == "call_1");
  assert(first.tool_name == "read_file");
  assert(first.error);
  assert(first.raw_json.empty());
  // The recovered timestamp comes from the conversation's `updated_at`.
  assert(first.timestamp == kUpdatedAt);

  const auto &second = result.conversation.messages.at(2);
  assert(second.id == "recovered_tool_conv_1_call_2");
  assert(second.tool_name == "write_file");
  assert(second.timestamp == kUpdatedAt);
}

void MissingToolResultsSkipExistingAndDuplicateCalls() {
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto answered = ToolMessage("tool-1", "结果", R"({})");
  answered.tool_call_id = "call_1";

  // A tool message with the same `tool_call_id` already answers the call.
  const auto answered_result = Run(Conversation({assistant, answered}));
  assert(!answered_result.changed);
  assert(answered_result.conversation.messages.size() == 2);

  // Counter-case: an empty / missing call id is never recovered.
  auto nameless = Message("assistant-2", MessageRole::assistant, "");
  nameless.raw_json = R"({"tool_calls":[{"id":"  ","name":"x"},{"name":"y"}]})";
  assert(!Run(Conversation({nameless})).changed);

  // The same call referenced twice only produces one result.
  auto twice = Message("assistant-3", MessageRole::assistant, "");
  twice.raw_json =
      R"({"tool_calls":[{"id":"call_9","name":"a"},{"id":"call_9","name":"a"}]})";
  const auto twice_result = Run(Conversation({twice}));
  assert(twice_result.conversation.messages.size() == 2);

  // Two assistant messages sharing a call id also produce a single result.
  auto first = Message("assistant-4", MessageRole::assistant, "");
  first.raw_json = R"({"tool_calls":[{"id":"call_8","name":"a"}]})";
  auto second = Message("assistant-5", MessageRole::assistant, "");
  second.raw_json = R"({"tool_calls":[{"id":"call_8","name":"a"}]})";
  const auto shared = Run(Conversation({first, second}));
  assert(shared.conversation.messages.size() == 3);
}

void RecoveredToolIdsAvoidCollisions() {
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto conflicting = Message("recovered_tool_conv_1_call_1",
                             MessageRole::tool, "占位");
  conflicting.tool_call_id = "other";

  const auto once = Run(Conversation({assistant, conflicting}));
  assert(once.changed);
  assert(once.conversation.messages.size() == 3);
  assert(once.conversation.messages.at(2).id ==
         "recovered_tool_conv_1_call_1_1");

  auto second_conflict =
      Message("recovered_tool_conv_1_call_1_1", MessageRole::tool, "占位2");
  second_conflict.tool_call_id = "other-2";
  const auto twice = Run(Conversation({assistant, conflicting, second_conflict}));
  assert(twice.conversation.messages.at(3).id ==
         "recovered_tool_conv_1_call_1_2");

  // `sanitizeId` also normalizes the conversation and call ids.
  auto conversation = Conversation({assistant});
  conversation.id = "a b/c";
  const auto normalized = Run(std::move(conversation));
  assert(normalized.conversation.messages.at(1).id ==
         "recovered_tool_a_b_c_call_1");

  auto unnamed = Conversation({assistant});
  unnamed.id.clear();
  const auto unknown = Run(std::move(unnamed));
  assert(unknown.conversation.messages.at(1).id ==
         "recovered_tool_unknown_call_1");
}

void MissingToolResultsUseTheClockWhenUpdatedAtIsAbsent() {
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto conversation = Conversation({assistant});
  conversation.updated_at = 0;
  const auto result = Run(std::move(conversation));
  assert(result.changed);
  assert(result.conversation.messages.at(1).timestamp == kNowMillis);

  // The two-argument entry point uses the system clock instead.
  auto live_assistant = Message("assistant-1", MessageRole::assistant, "");
  live_assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto live = Conversation({live_assistant});
  live.updated_at = 0;
  const auto real = Sanitize(std::move(live), kFallback);
  assert(real.changed);
  assert(real.conversation.messages.at(1).timestamp > 1'600'000'000'000LL);
}

// --- changed flag / structural fidelity ------------------------------------

void CleanConversationsReportNoChange() {
  auto user = Message("user-1", MessageRole::user, "你好");
  auto assistant = Message("assistant-1", MessageRole::assistant, "你好呀");
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto tool = ToolMessage(
      "tool-1",
      R"({"linecode_agent_progress":true,"status":"completed","output":"ok"})",
      R"({"review_state":"accepted"})");
  tool.tool_call_id = "call_1";
  tool.tool_name = "read_file";
  auto compact = Message("assistant-2", MessageRole::assistant, "");
  compact.raw_json = R"({"compact_status":"done"})";

  auto conversation = Conversation({user, assistant, tool, compact});
  const auto original = conversation;
  const auto result = Run(std::move(conversation));
  assert(!result.changed);
  // The untouched conversation is returned unchanged, field by field.
  assert(result.conversation == original);
}

void RepairedConversationsKeepTheirMetadata() {
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.streaming = true;
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";
  auto conversation = Conversation({assistant});
  const auto id = conversation.id;
  const auto title = conversation.title;
  const auto project = conversation.project_id;
  const auto created = conversation.created_at;
  const auto updated = conversation.updated_at;
  const auto current = conversation.current;
  const auto raw = conversation.raw_json;

  const auto result = Run(std::move(conversation));
  assert(result.changed);
  assert(result.conversation.id == id);
  assert(result.conversation.title == title);
  assert(result.conversation.project_id == project);
  assert(result.conversation.created_at == created);
  assert(result.conversation.updated_at == updated);
  assert(result.conversation.current == current);
  assert(result.conversation.raw_json == raw);
}

void SanitizingTwiceIsStable() {
  auto tool = ToolMessage(
      "tool-1", R"({"linecode_agent_pipeline_progress":true,"status":"running",)"
                R"("agents":[{"id":"a","status":"running","output":""}]})",
      R"({"review_state":"pending"})");
  auto assistant = Message("assistant-1", MessageRole::assistant, "");
  assistant.streaming = true;
  assistant.raw_json =
      R"({"tool_calls":[{"id":"call_1","name":"read_file"}]})";

  const auto first = Run(Conversation({tool, assistant}));
  assert(first.changed);
  const auto second = Run(first.conversation);
  assert(!second.changed);
  assert(second.conversation == first.conversation);
}

// The store hands the session `domain::ChatMessage`s, where a tool call and
// its result share one event, so the record-level rules are mirrored here.
void ResumeMessagesDropStaleStreamingAndProgressFlags() {
  std::vector<ChatMessage> messages(1);
  messages[0].streaming = true;
  messages[0].compact_status = "running";
  assert(SanitizeResumeMessages(messages, kFallback));
  assert(!messages[0].streaming);
  assert(messages[0].compact_status == "error");

  // A finished block is left alone, and a second pass reports no change.
  std::vector<ChatMessage> finished(1);
  finished[0].compact_status = "done";
  assert(!SanitizeResumeMessages(finished, kFallback));
  assert(finished[0].compact_status == "done");
}

void ResumeMessagesRecoverAMissingToolResult() {
  std::vector<ChatMessage> messages(1);
  AssistantToolEvent event;
  event.call.id = "call_1";
  event.call.name = "file_write";
  messages[0].timeline.push_back(event);
  assert(SanitizeResumeMessages(messages, kFallback));
  const auto *tool = std::get_if<AssistantToolEvent>(&messages[0].timeline[0]);
  assert(tool != nullptr);
  assert(tool->result.has_value());
  assert(tool->result->call_id == "call_1");
  assert(tool->result->name == "file_write");
  assert(tool->result->content == kFallback);
  assert(tool->result->error);
}

void ResumeMessagesClearAnUnfinishedReview() {
  const auto build = [](std::string state, std::string content) {
    std::vector<ChatMessage> messages(1);
    AssistantToolEvent event;
    event.call.id = "call_1";
    ChatToolResult result;
    result.call_id = "call_1";
    result.review_state = std::move(state);
    result.content = std::move(content);
    result.diff_id = "d1";
    event.result = std::move(result);
    messages[0].timeline.push_back(std::move(event));
    return messages;
  };

  for (const auto state : {"running", "pending"}) {
    auto messages = build(state, "wrote the file");
    assert(SanitizeResumeMessages(messages, kFallback));
    const auto *tool = std::get_if<AssistantToolEvent>(&messages[0].timeline[0]);
    assert(tool != nullptr && tool->result.has_value());
    assert(tool->result->error);
    assert(tool->result->content == kFallback);
    assert(tool->result->review_state.empty());
    // The recorded change stays reviewable rather than being dropped.
    assert(tool->result->diff_id == "d1");
  }

  // `accepted` with no content is unfinished; with content it is not.
  auto empty_accepted = build("accepted", "   ");
  assert(SanitizeResumeMessages(empty_accepted, kFallback));
  auto accepted = build("accepted", "done");
  assert(!SanitizeResumeMessages(accepted, kFallback));
  const auto *kept = std::get_if<AssistantToolEvent>(&accepted[0].timeline[0]);
  assert(kept != nullptr && kept->result.has_value());
  assert(kept->result->content == "done");
  assert(!kept->result->error);
}

void CleanResumeMessagesReportNoChange() {
  std::vector<ChatMessage> messages(1);
  AssistantToolEvent event;
  event.call.id = "call_1";
  ChatToolResult result;
  result.call_id = "call_1";
  result.content = "ok";
  event.result = std::move(result);
  messages[0].timeline.push_back(std::move(event));
  assert(!SanitizeResumeMessages(messages, kFallback));
}

} // namespace

int main() {
  ResolveTerminatedMessageFallsBack();
  SanitizeIdMatchesLegacyReplaceAll();
  StreamingFlagIsCleared();
  CompactStatusRunningBecomesError();
  ToolReviewStatesAreRepaired();
  AgentProgressRepairsUnfinishedPayloads();
  AgentProgressAppendsTerminatedNoticeToOutput();
  AgentProgressLeavesFinishedPayloadsAlone();
  PipelineProgressRepairsAgentsAndCountsFailures();
  PipelineProgressKeepsFinishedPayloads();
  PipelineAgentFailureCountUsesErrorFlags();
  NestedToolCallsAreRepaired();
  NestedToolCallsRespectTerminateFlagAndRecurse();
  ToolContentIgnoresEverythingItDoesNotOwn();
  MissingToolResultsAreAppended();
  MissingToolResultsSkipExistingAndDuplicateCalls();
  RecoveredToolIdsAvoidCollisions();
  MissingToolResultsUseTheClockWhenUpdatedAtIsAbsent();
  CleanConversationsReportNoChange();
  RepairedConversationsKeepTheirMetadata();
  SanitizingTwiceIsStable();
  ResumeMessagesDropStaleStreamingAndProgressFlags();
  ResumeMessagesRecoverAMissingToolResult();
  ResumeMessagesClearAnUnfinishedReview();
  CleanResumeMessagesReportNoChange();
  std::cout << "conversation_resume_sanitizer_tests passed\n";
  return 0;
}
