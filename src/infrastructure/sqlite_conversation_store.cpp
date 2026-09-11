#include "infrastructure/sqlite_conversation_store.h"

#include <algorithm>
#include <charconv>
#include <chrono>
#include <concepts>
#include <exception>
#include <iterator>
#include <limits>
#include <map>
#include <optional>
#include <string_view>
#include <system_error>
#include <utility>
#include <vector>

#include "infrastructure/attachment_json_codec.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/legacy_conversation_schema.h"

namespace linecode::infrastructure {
namespace {

using huxerui::sqlite::Database;
using huxerui::sqlite::Error;
using huxerui::sqlite::ErrorCode;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

constexpr std::string_view kOwnedMessagePrefix = "linecodepro:";

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

std::uint64_t InitialMessageId() noexcept {
  const auto now = std::chrono::duration_cast<std::chrono::microseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
  return now > 0 ? static_cast<std::uint64_t>(now) : 1U;
}

std::string RoleName(domain::MessageRole role) {
  switch (role) {
  case domain::MessageRole::user:
    return "user";
  case domain::MessageRole::assistant:
    return "assistant";
  case domain::MessageRole::tool:
    return "tool";
  }
  return "assistant";
}

domain::MessageRole ParseRole(std::string_view role) noexcept {
  if (role == "user") {
    return domain::MessageRole::user;
  }
  if (role == "tool") {
    return domain::MessageRole::tool;
  }
  // Legacy system/developer/unknown roles remain readable as non-user output.
  return domain::MessageRole::assistant;
}

[[nodiscard]] std::string_view ToolStatusName(
    domain::ToolCallStatus status) noexcept {
  switch (status) {
  case domain::ToolCallStatus::requested:
    return "requested";
  case domain::ToolCallStatus::awaiting_review:
    return "awaiting_review";
  case domain::ToolCallStatus::running:
    return "running";
  case domain::ToolCallStatus::completed:
    return "completed";
  case domain::ToolCallStatus::failed:
    return "failed";
  case domain::ToolCallStatus::rejected:
    return "rejected";
  }
  return "failed";
}

std::optional<std::uint64_t> ParseOwnedMessageId(std::string_view id) noexcept {
  if (!id.starts_with(kOwnedMessagePrefix)) {
    return std::nullopt;
  }
  id.remove_prefix(kOwnedMessagePrefix.size());
  std::uint64_t value{};
  const auto [end, error] =
      std::from_chars(id.data(), id.data() + id.size(), value);
  if (error != std::errc{} || end != id.data() + id.size() || value == 0) {
    return std::nullopt;
  }
  return value;
}

struct StoredMessage final {
  std::string id;
  std::int64_t local_order{};
  std::string role;
  std::string content;
  std::string attachments_json;
  std::vector<domain::InputAttachment> attachments;
  std::string reasoning_content;
  bool streaming{};
  bool exclude_from_context{};
  bool error{};
  std::int64_t timestamp{};
  std::int64_t finished_at{};
  std::string error_message;
  std::string tool_call_id;
  std::string tool_name;
  std::vector<domain::AssistantTimelineEvent> timeline;
};

void DecodeLegacyMetadata(StoredMessage &message) {
  namespace json = archive_json;
  if (message.attachments_json.empty())
    return;
  auto parsed = json::Parse(message.attachments_json);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  if (object == nullptr)
    return;
  if (const auto *started = json::Find(*object, "processing_started_at")) {
    if (const auto *value = std::get_if<std::int64_t>(started))
      message.timestamp = *value;
  }
  if (const auto *finished = json::Find(*object, "processing_finished_at")) {
    if (const auto *value = std::get_if<std::int64_t>(finished))
      message.finished_at = *value;
  }
  if (const auto *error =
          json::AsString(json::Find(*object, "error_message")))
    message.error_message = *error;
  const auto *calls = json::AsArray(json::Find(*object, "tool_calls"));
  if (calls == nullptr)
    return;
  for (const auto &value : *calls) {
    const auto *item = json::AsObject(&value);
    if (item == nullptr)
      continue;
    const auto *id = json::AsString(json::Find(*item, "id"));
    if (id == nullptr || id->empty())
      continue;
    const auto *name = json::AsString(json::Find(*item, "name"));
    const auto *arguments = json::AsString(json::Find(*item, "arguments"));
    domain::AssistantToolEvent event{};
    event.call.id = *id;
    event.call.name = name == nullptr ? std::string{} : *name;
    event.call.arguments_json =
        arguments == nullptr ? std::string{"{}"} : *arguments;
    message.timeline.push_back(std::move(event));
  }
}

Result<StoredMessage> DecodeStoredMessage(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id) {
    return id.Error();
  }
  auto local_order = row.Get<std::int64_t>(1);
  if (!local_order) {
    return local_order.Error();
  }
  auto role = row.Get<std::string>(2);
  if (!role) {
    return role.Error();
  }
  auto content = row.Get<std::string>(3);
  if (!content) {
    return content.Error();
  }
  auto attachments_json = row.Get<std::string>(4);
  if (!attachments_json) {
    return attachments_json.Error();
  }
  auto attachments = DecodeAttachmentJson(*attachments_json);
  auto reasoning = row.Get<std::string>(5);
  if (!reasoning)
    return reasoning.Error();
  auto streaming = row.Get<std::int64_t>(6);
  if (!streaming)
    return streaming.Error();
  auto excluded = row.Get<std::int64_t>(7);
  if (!excluded)
    return excluded.Error();
  auto error = row.Get<std::int64_t>(8);
  if (!error)
    return error.Error();
  auto timestamp = row.Get<std::int64_t>(9);
  if (!timestamp)
    return timestamp.Error();
  auto tool_call_id = row.Get<std::string>(10);
  if (!tool_call_id)
    return tool_call_id.Error();
  auto tool_name = row.Get<std::string>(11);
  if (!tool_name)
    return tool_name.Error();
  StoredMessage message{.id = std::move(*id),
                        .local_order = *local_order,
                        .role = std::move(*role),
                        .content = std::move(*content),
                        .attachments_json = std::move(*attachments_json),
                        .attachments = std::move(attachments),
                        .reasoning_content = std::move(*reasoning),
                        .streaming = *streaming != 0,
                        .exclude_from_context = *excluded != 0,
                        .error = *error != 0,
                        .timestamp = *timestamp,
                        .finished_at = 0,
                        .error_message = {},
                        .tool_call_id = std::move(*tool_call_id),
                        .tool_name = std::move(*tool_name),
                        .timeline = {}};
  DecodeLegacyMetadata(message);
  return message;
}

[[nodiscard]] domain::ChatMessage HydrateMessage(const StoredMessage &row) {
  const auto fallback_id = static_cast<std::uint64_t>(row.local_order) + 1U;
  domain::ChatMessage message{};
  message.id = ParseOwnedMessageId(row.id).value_or(fallback_id);
  message.role = ParseRole(row.role);
  message.content = row.content;
  message.attachments = row.attachments;
  message.reasoning_content = row.reasoning_content;
  message.timeline = row.timeline;
  message.streaming = row.streaming;
  message.exclude_from_context = row.exclude_from_context;
  message.error = row.error;
  message.error_message = row.error_message;
  message.processing_started_at = row.timestamp;
  message.processing_finished_at = row.finished_at;
  return message;
}

[[nodiscard]] std::string
EncodeMessageRawJson(const domain::ChatMessage &message) {
  namespace json = archive_json;
  json::Object object;
  if (!message.attachments.empty()) {
    json::Array attachments;
    attachments.reserve(message.attachments.size());
    for (const auto &attachment : message.attachments) {
      attachments.emplace_back(json::Object{{"name", attachment.Name()},
                                            {"path", attachment.Path()},
                                            {"source", attachment.Source()}});
    }
    object.insert_or_assign("attachments", std::move(attachments));
  }
  if (message.processing_started_at > 0) {
    object.insert_or_assign("processing_started_at",
                            message.processing_started_at);
    object.insert_or_assign("processing_finished_at",
                            message.processing_finished_at);
  }
  if (!message.error_message.empty())
    object.insert_or_assign("error_message", message.error_message);

  json::Array tool_calls;
  for (const auto &event : message.timeline) {
    const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
    if (tool == nullptr)
      continue;
    tool_calls.emplace_back(json::Object{{"id", tool->call.id},
                                         {"name", tool->call.name},
                                         {"arguments",
                                          tool->call.arguments_json}});
  }
  if (!tool_calls.empty())
    object.insert_or_assign("tool_calls", std::move(tool_calls));
  return object.empty() ? std::string{} : json::Serialize(object);
}

struct StoredAttachment final {
  std::string message_id;
  domain::InputAttachment attachment;
};

Result<StoredAttachment> DecodeStoredAttachment(const RowView &row) {
  auto message_id = row.Get<std::string>(0);
  if (!message_id) {
    return message_id.Error();
  }
  auto name = row.Get<std::string>(1);
  if (!name) {
    return name.Error();
  }
  auto path = row.Get<std::string>(2);
  if (!path) {
    return path.Error();
  }
  auto source = row.Get<std::string>(3);
  if (!source) {
    return source.Error();
  }
  return StoredAttachment{
      .message_id = std::move(*message_id),
      .attachment = domain::InputAttachment{
          std::move(*name), std::move(*path), std::move(*source)},
  };
}

struct StoredBlock final {
  std::string message_id;
  std::string type;
  std::string content;
  std::string status;
  std::size_t turn_index{};
};

Result<StoredBlock> DecodeStoredBlock(const RowView &row) {
  auto message_id = row.Get<std::string>(0);
  auto type = row.Get<std::string>(1);
  auto content = row.Get<std::string>(2);
  auto status = row.Get<std::string>(3);
  auto turn = row.Get<std::string>(4);
  if (!message_id)
    return message_id.Error();
  if (!type)
    return type.Error();
  if (!content)
    return content.Error();
  if (!status)
    return status.Error();
  if (!turn)
    return turn.Error();
  std::size_t turn_index{};
  const auto [end, error] =
      std::from_chars(turn->data(), turn->data() + turn->size(), turn_index);
  if (error != std::errc{} || end != turn->data() + turn->size())
    turn_index = 0;
  return StoredBlock{.message_id = std::move(*message_id),
                     .type = std::move(*type),
                     .content = std::move(*content),
                     .status = std::move(*status),
                     .turn_index = turn_index};
}

struct StoredToolCall final {
  std::string message_id;
  domain::ChatToolCall call;
};

Result<StoredToolCall> DecodeStoredToolCall(const RowView &row) {
  auto message_id = row.Get<std::string>(0);
  auto id = row.Get<std::string>(1);
  auto name = row.Get<std::string>(2);
  auto arguments = row.Get<std::string>(3);
  auto created_at = row.Get<std::int64_t>(4);
  auto duration = row.Get<std::int64_t>(5);
  auto error_message = row.Get<std::string>(6);
  if (!message_id)
    return message_id.Error();
  if (!id)
    return id.Error();
  if (!name)
    return name.Error();
  if (!arguments)
    return arguments.Error();
  if (!created_at)
    return created_at.Error();
  if (!duration)
    return duration.Error();
  if (!error_message)
    return error_message.Error();
  return StoredToolCall{
      .message_id = std::move(*message_id),
      .call = domain::ChatToolCall{
          .id = std::move(*id),
          .name = std::move(*name),
          .arguments_json = std::move(*arguments),
          .status = error_message->empty() ? domain::ToolCallStatus::requested
                                          : domain::ToolCallStatus::failed,
          .created_at_millis = *created_at,
          .duration_millis = *duration,
          .error_message = std::move(*error_message),
      }};
}

struct StoredToolResult final {
  std::string message_id;
  domain::ChatToolResult result;
};

Result<StoredToolResult> DecodeStoredToolResult(const RowView &row) {
  auto message_id = row.Get<std::string>(0);
  auto call_id = row.Get<std::string>(1);
  auto content = row.Get<std::string>(2);
  auto error = row.Get<std::int64_t>(3);
  auto diff_id = row.Get<std::string>(4);
  auto review_state = row.Get<std::string>(5);
  if (!message_id)
    return message_id.Error();
  if (!call_id)
    return call_id.Error();
  if (!content)
    return content.Error();
  if (!error)
    return error.Error();
  if (!diff_id)
    return diff_id.Error();
  if (!review_state)
    return review_state.Error();
  domain::ChatToolResult result{};
  result.call_id = std::move(*call_id);
  result.content = std::move(*content);
  result.error = *error != 0;
  result.diff_id = std::move(*diff_id);
  result.review_state = std::move(*review_state);
  return StoredToolResult{.message_id = std::move(*message_id),
                          .result = std::move(result)};
}

[[nodiscard]] domain::ToolCallStatus ParseToolStatus(
    std::string_view status, bool has_result, bool result_error) noexcept {
  if (status == "awaiting_review")
    return domain::ToolCallStatus::awaiting_review;
  if (status == "running")
    return domain::ToolCallStatus::running;
  if (status == "completed")
    return domain::ToolCallStatus::completed;
  if (status == "failed")
    return domain::ToolCallStatus::failed;
  if (status == "rejected")
    return domain::ToolCallStatus::rejected;
  if (has_result)
    return result_error ? domain::ToolCallStatus::failed
                        : domain::ToolCallStatus::completed;
  return domain::ToolCallStatus::requested;
}

void AppendAttachmentIfMissing(
    std::vector<domain::InputAttachment> &attachments,
    domain::InputAttachment attachment) {
  if (attachments.size() >= max_attachments_per_message ||
      attachment.Path().empty() ||
      attachment.Path().size() > max_attachment_path_bytes ||
      attachment.Name().size() > max_attachment_name_bytes) {
    return;
  }
  const bool duplicate = std::ranges::any_of(
      attachments, [&attachment](const domain::InputAttachment &current) {
        return current.Matches(attachment.Path(), attachment.Source());
      });
  if (!duplicate) {
    attachments.push_back(std::move(attachment));
  }
}

huxerui::Task<Result<std::vector<StoredMessage>>>
LoadStoredMessagesAsync(const Database &database,
                        const std::string &conversation_id) {
  auto messages = co_await database.QueryAsync<StoredMessage>(
      std::string{legacy_schema::load_visible_messages}, DecodeStoredMessage,
      conversation_id);
  if (!messages) {
    co_return messages.Error();
  }
  auto attachment_rows = co_await database.QueryAsync<StoredAttachment>(
      "SELECT a.message_id, a.name, a.path, a.source "
      "FROM attachments AS a "
      "JOIN messages AS m ON m.id = a.message_id "
      "WHERE m.conversation_id = ? ORDER BY m.local_order, a.id",
      DecodeStoredAttachment, conversation_id);
  if (!attachment_rows) {
    co_return attachment_rows.Error();
  }
  auto blocks = co_await database.QueryAsync<StoredBlock>(
      "SELECT b.message_id, b.type, COALESCE(b.content, ''), "
      "COALESCE(b.status, ''), COALESCE(b.raw_json, '0') "
      "FROM message_blocks AS b JOIN messages AS m ON m.id = b.message_id "
      "WHERE m.conversation_id = ? ORDER BY m.local_order, b.block_order",
      DecodeStoredBlock, conversation_id);
  if (!blocks)
    co_return blocks.Error();
  auto calls = co_await database.QueryAsync<StoredToolCall>(
      "SELECT c.message_id, c.id, c.name, c.arguments, c.created_at, "
      "c.duration_ms, COALESCE(c.error_message, '') "
      "FROM tool_calls AS c JOIN messages AS m ON m.id = c.message_id "
      "WHERE m.conversation_id = ? ORDER BY m.local_order, c.created_at, c.id",
      DecodeStoredToolCall, conversation_id);
  if (!calls)
    co_return calls.Error();
  auto results = co_await database.QueryAsync<StoredToolResult>(
      "SELECT r.message_id, COALESCE(r.tool_call_id, ''), r.content, "
      "r.is_error, COALESCE(r.diff_id, ''), COALESCE(r.review_state, '') "
      "FROM tool_results AS r JOIN messages AS m ON m.id = r.message_id "
      "WHERE m.conversation_id = ? ORDER BY m.local_order, r.id",
      DecodeStoredToolResult, conversation_id);
  if (!results)
    co_return results.Error();

  std::map<std::string_view, std::size_t, std::less<>> message_indexes;
  for (std::size_t index = 0; index < messages->size(); ++index) {
    message_indexes.emplace((*messages)[index].id, index);
  }
  for (auto &row : *attachment_rows) {
    const auto found = message_indexes.find(row.message_id);
    if (found == message_indexes.end()) {
      continue;
    }
    AppendAttachmentIfMissing((*messages)[found->second].attachments,
                              std::move(row.attachment));
  }
  std::map<std::pair<std::string, std::string>, domain::ChatToolCall>
      calls_by_message;
  for (auto &stored : *calls) {
    calls_by_message.insert_or_assign(
        std::pair{stored.message_id, stored.call.id}, std::move(stored.call));
  }
  std::map<std::pair<std::string, std::string>, domain::ChatToolResult>
      results_by_message;
  for (auto &stored : *results) {
    results_by_message.insert_or_assign(
        std::pair{stored.message_id, stored.result.call_id},
        std::move(stored.result));
  }
  std::map<std::string, std::vector<std::string>, std::less<>> referenced_calls;
  for (const auto &block : *blocks) {
    const auto found = message_indexes.find(block.message_id);
    if (found == message_indexes.end())
      continue;
    auto &timeline = (*messages)[found->second].timeline;
    if (block.type == "reasoning") {
      timeline.push_back(domain::AssistantReasoningEvent{
          .turn_index = block.turn_index,
          .text = block.content,
          .kind = block.status.starts_with("summary")
                      ? domain::ReasoningKind::summary
                      : domain::ReasoningKind::thinking,
          .starts_new_segment = block.status.ends_with(":new"),
      });
    } else if (block.type == "text") {
      timeline.push_back(domain::AssistantTextEvent{
          .turn_index = block.turn_index, .text = block.content});
    } else if (block.type == "tool") {
      const auto key = std::pair{block.message_id, block.content};
      const auto stored_call = calls_by_message.find(key);
      if (stored_call == calls_by_message.end())
        continue;
      domain::AssistantToolEvent event{};
      event.turn_index = block.turn_index;
      event.call = stored_call->second;
      const auto result = results_by_message.find(key);
      if (result != results_by_message.end()) {
        event.result = result->second;
        event.result->name = event.call.name;
      }
      event.call.status = ParseToolStatus(
          block.status, event.result.has_value(),
          event.result.has_value() && event.result->error);
      std::erase_if(timeline, [&block](const auto &candidate) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&candidate);
        return tool != nullptr && tool->call.id == block.content;
      });
      timeline.push_back(std::move(event));
      referenced_calls[block.message_id].push_back(block.content);
    }
  }
  // Legacy databases may contain tool tables without message_blocks. Recover
  // those calls after ordered blocks instead of dropping protocol history.
  for (const auto &[key, call] : calls_by_message) {
    const auto found = message_indexes.find(key.first);
    if (found == message_indexes.end())
      continue;
    const auto referenced = referenced_calls.find(key.first);
    if (referenced != referenced_calls.end() &&
        std::ranges::find(referenced->second, key.second) !=
            referenced->second.end())
      continue;
    domain::AssistantToolEvent event{};
    event.call = call;
    const auto result = results_by_message.find(key);
    if (result != results_by_message.end()) {
      event.result = result->second;
      event.result->name = event.call.name;
    }
    event.call.status = ParseToolStatus(
        {}, event.result.has_value(),
        event.result.has_value() && event.result->error);
    (*messages)[found->second].timeline.push_back(std::move(event));
  }
  for (std::size_t index = 0; index < messages->size(); ++index) {
    auto &tool_message = (*messages)[index];
    if (tool_message.role != "tool" || tool_message.tool_call_id.empty())
      continue;
    for (std::size_t candidate_index = index; candidate_index > 0;
         --candidate_index) {
      auto &candidate = (*messages)[candidate_index - 1U];
      if (candidate.role != "assistant")
        continue;
      auto found = std::ranges::find_if(
          candidate.timeline, [&tool_message](const auto &event) {
            const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
            return tool != nullptr &&
                   tool->call.id == tool_message.tool_call_id;
          });
      if (found == candidate.timeline.end()) {
        domain::AssistantToolEvent event{};
        event.call.id = tool_message.tool_call_id;
        event.call.name = tool_message.tool_name;
        candidate.timeline.push_back(std::move(event));
        found = std::prev(candidate.timeline.end());
      }
      auto *tool = std::get_if<domain::AssistantToolEvent>(&*found);
      if (tool == nullptr)
        break;
      domain::ChatToolResult result{};
      result.call_id = tool_message.tool_call_id;
      result.name = tool_message.tool_name.empty() ? tool->call.name
                                                   : tool_message.tool_name;
      result.content = tool_message.content;
      result.error = tool_message.error;
      tool->result = std::move(result);
      tool->call.status = tool_message.error
                              ? domain::ToolCallStatus::failed
                              : domain::ToolCallStatus::completed;
      break;
    }
  }
  for (auto &message : *messages) {
    if (message.timeline.empty() && !message.reasoning_content.empty()) {
      message.timeline.push_back(domain::AssistantReasoningEvent{
          .turn_index = 0,
          .text = message.reasoning_content,
          .kind = domain::ReasoningKind::thinking,
          .starts_new_segment = false,
      });
    }
  }
  co_return std::move(*messages);
}

Result<std::int64_t> ReadUserVersion(Transaction &transaction) {
  auto versions = transaction.Query<std::int64_t>(
      "PRAGMA user_version",
      [](const RowView &row) { return row.Get<std::int64_t>(0); });
  if (!versions) {
    return versions.Error();
  }
  if (versions->size() != 1) {
    return Error{ErrorCode::Decode, "PRAGMA user_version returned no value",
                 "read PRAGMA user_version"};
  }
  return versions->front();
}

Result<bool> ColumnExists(Transaction &transaction, std::string_view table,
                          std::string_view expected_column) {
  auto columns = transaction.Query<std::string>(
      "PRAGMA table_info(" + std::string{table} + ")",
      [](const RowView &row) { return row.Get<std::string>(1); });
  if (!columns) {
    return columns.Error();
  }
  return std::ranges::find(*columns, expected_column) != columns->end();
}

Result<void> ExecuteSchema(Transaction &transaction) {
  auto version = ReadUserVersion(transaction);
  if (!version) {
    return version.Error();
  }
  if (*version < 0 || *version > legacy_schema::user_version) {
    return Error{
        ErrorCode::SchemaMismatch,
        "linecode.db uses a schema newer than supported legacy version 4",
        "validate PRAGMA user_version"};
  }

  for (const std::string_view statement : legacy_schema::table_statements) {
    auto executed = transaction.Execute(std::string{statement});
    if (!executed) {
      return executed.Error();
    }
  }

  for (const auto &required : legacy_schema::required_columns) {
    auto exists = ColumnExists(transaction, required.table, required.name);
    if (!exists) {
      return exists.Error();
    }
    if (!*exists) {
      auto added = transaction.Execute(std::string{required.add_statement});
      if (!added) {
        return added.Error();
      }
    }
  }

  for (const std::string_view statement : legacy_schema::index_statements) {
    auto executed = transaction.Execute(std::string{statement});
    if (!executed) {
      return executed.Error();
    }
  }

  auto versioned = transaction.Execute("PRAGMA user_version = 4");
  if (!versioned) {
    return versioned.Error();
  }
  return {};
}

huxerui::Task<Result<void>>
EnsureCompatibleSchemaAsync(const Database &database) {
  co_return co_await database.TransactionAsync(
      [](Transaction &transaction) { return ExecuteSchema(transaction); });
}

std::string NewConversationId() {
  const auto now = std::chrono::duration_cast<std::chrono::microseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
  return "linecodepro-conversation:" + std::to_string(now);
}

Result<application::ConversationSummary>
DecodeConversationSummary(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id) {
    return id.Error();
  }
  auto title = row.Get<std::string>(1);
  if (!title) {
    return title.Error();
  }
  auto updated_at = row.Get<std::int64_t>(2);
  if (!updated_at) {
    return updated_at.Error();
  }
  return application::ConversationSummary{
      .id = std::move(*id),
      .title = std::move(*title),
      .updated_at_millis = *updated_at,
  };
}

} // namespace

struct SqliteConversationStore::State final {
  enum class Phase : std::uint8_t { waiting, hydrating, ready, failed };
  enum class Operation : std::uint8_t {
    append,
    clear,
    recall,
    select,
    erase,
  };

  struct Event final {
    Operation operation{Operation::append};
    std::string conversation_id;
    std::string conversation_title{"New conversation"};
    std::int64_t conversation_created_at{};
    std::uint64_t selection_generation{};
    domain::ChatMessage message{};
    std::int64_t local_order{-1};
    std::int64_t timestamp{NowMilliseconds()};
  };

  explicit State(huxerui::TaskScope task_scope, std::function<void()> changed)
      : tasks(std::move(task_scope)), on_changed(std::move(changed)),
        next_message_id(InitialMessageId()) {}

  void NotifyChanged() const {
    if (on_changed) {
      on_changed();
    }
  }

  void Fail(const Error &error) {
    phase = Phase::failed;
    last_error = error.Message();
    NotifyChanged();
  }

  huxerui::TaskScope tasks;
  std::function<void()> on_changed;
  Phase phase{Phase::waiting};
  std::optional<Database> database;
  std::string conversation_id;
  std::int64_t conversation_created_at{};
  std::vector<application::ConversationSummary> conversations;
  std::vector<domain::ChatMessage> messages;
  std::vector<Event> pending;
  std::int64_t next_local_order{};
  std::uint64_t next_message_id{};
  application::ConversationSelectionBarrier selection_barrier;
  bool flush_running{};
  std::string last_error;
};

SqliteConversationStore::SqliteConversationStore(
    huxerui::TaskScope tasks, std::function<void()> on_changed)
    : state_(std::make_shared<State>(std::move(tasks), std::move(on_changed))) {
}

SqliteConversationStore::~SqliteConversationStore() = default;

huxerui::Task<Result<void>>
SqliteConversationStore::InitializeAsync(huxerui::File database_file) {
  if (state_->phase != State::Phase::waiting) {
    co_return Error{ErrorCode::Transaction,
                    "conversation store was initialized more than once",
                    "initialize conversation store"};
  }
  state_->phase = State::Phase::hydrating;

  huxerui::sqlite::OpenOptions options{
      .journal_mode = huxerui::sqlite::JournalMode::Wal,
      .busy_timeout = std::chrono::seconds{5},
      .create_parent_directories = true,
  };
  auto opened = co_await Database::OpenAsync(std::move(database_file), options);
  if (!opened) {
    state_->Fail(opened.Error());
    co_return opened.Error();
  }

  auto foreign_keys = co_await opened->ExecuteAsync("PRAGMA foreign_keys = ON");
  if (!foreign_keys) {
    state_->Fail(foreign_keys.Error());
    co_return foreign_keys.Error();
  }

  auto schema = co_await EnsureCompatibleSchemaAsync(*opened);
  if (!schema) {
    state_->Fail(schema.Error());
    co_return schema.Error();
  }

  auto summaries =
      co_await opened->QueryAsync<application::ConversationSummary>(
          std::string{legacy_schema::list_visible_conversations},
          DecodeConversationSummary);
  if (!summaries) {
    state_->Fail(summaries.Error());
    co_return summaries.Error();
  }

  auto current = co_await opened->QueryAsync<std::pair<std::string, std::int64_t>>(
      std::string{legacy_schema::find_resume_conversation},
      [](const RowView &row) -> Result<std::pair<std::string, std::int64_t>> {
        auto id = row.Get<std::string>(0);
        if (!id) {
          return id.Error();
        }
        auto created_at = row.Get<std::int64_t>(1);
        if (!created_at) {
          return created_at.Error();
        }
        return std::pair{std::move(*id), *created_at};
      });
  if (!current) {
    state_->Fail(current.Error());
    co_return current.Error();
  }

  std::vector<StoredMessage> stored;
  if (!current->empty()) {
    auto loaded =
        co_await LoadStoredMessagesAsync(*opened, current->front().first);
    if (!loaded) {
      state_->Fail(loaded.Error());
      co_return loaded.Error();
    }
    stored = std::move(*loaded);
  }

  std::vector<domain::ChatMessage> hydrated;
  hydrated.reserve(stored.size());
  std::int64_t next_order = 0;
  for (const auto &row : stored) {
    if (row.local_order < 0) {
      continue;
    }
    auto message = HydrateMessage(row);
    const auto message_id = message.id;
    hydrated.push_back(std::move(message));
    if (message_id >= state_->next_message_id &&
        message_id != std::numeric_limits<std::uint64_t>::max()) {
      state_->next_message_id = message_id + 1U;
    }
    next_order = std::max(next_order, row.local_order + 1);
  }

  const bool has_local_state = !state_->pending.empty() ||
                               !state_->conversation_id.empty() ||
                               !state_->messages.empty();
  if (!has_local_state) {
    state_->messages = std::move(hydrated);
    state_->next_local_order = next_order;
    if (!current->empty()) {
      state_->conversation_id = std::move(current->front().first);
      state_->conversation_created_at = current->front().second;
    }
  }

  for (auto &summary : *summaries) {
    const auto duplicate = std::ranges::find(
        state_->conversations, summary.id,
        &application::ConversationSummary::id);
    if (duplicate == state_->conversations.end()) {
      state_->conversations.push_back(std::move(summary));
    }
  }
  std::ranges::sort(state_->conversations, std::greater{},
                    &application::ConversationSummary::updated_at_millis);
  state_->database = std::move(*opened);
  state_->phase = State::Phase::ready;
  state_->last_error.clear();
  state_->NotifyChanged();
  ScheduleFlush(state_);
  co_return Result<void>{};
}

huxerui::Task<Result<void>> SqliteConversationStore::FlushPendingAsync() {
  while (state_->phase == State::Phase::waiting ||
         state_->phase == State::Phase::hydrating) {
    co_await huxerui::Delay(std::chrono::milliseconds{5});
  }
  if (state_->phase == State::Phase::failed) {
    co_return Error{ErrorCode::Transaction,
                    state_->last_error.empty()
                        ? "conversation persistence is unavailable"
                        : state_->last_error,
                    "flush conversations before data archive"};
  }

  ScheduleFlush(state_);
  while (state_->flush_running) {
    co_await huxerui::Delay(std::chrono::milliseconds{5});
  }
  if (!state_->pending.empty() || state_->phase == State::Phase::failed) {
    co_return Error{ErrorCode::Transaction,
                    state_->last_error.empty()
                        ? "conversation persistence did not drain"
                        : state_->last_error,
                    "flush conversations before data archive"};
  }
  co_return Result<void>{};
}

huxerui::Task<Result<void>> SqliteConversationStore::ReloadAsync() {
  auto flushed = co_await FlushPendingAsync();
  if (!flushed) {
    co_return flushed.Error();
  }
  if (!state_->database) {
    co_return Error{ErrorCode::Transaction,
                    "conversation database is unavailable",
                    "reload conversations after data import"};
  }

  auto summaries =
      co_await state_->database->QueryAsync<application::ConversationSummary>(
          std::string{legacy_schema::list_visible_conversations},
          DecodeConversationSummary);
  if (!summaries) {
    co_return summaries.Error();
  }
  auto current =
      co_await state_->database
          ->QueryAsync<std::pair<std::string, std::int64_t>>(
              std::string{legacy_schema::find_resume_conversation},
              [](const RowView &row)
                  -> Result<std::pair<std::string, std::int64_t>> {
                auto id = row.Get<std::string>(0);
                if (!id) {
                  return id.Error();
                }
                auto created_at = row.Get<std::int64_t>(1);
                if (!created_at) {
                  return created_at.Error();
                }
                return std::pair{std::move(*id), *created_at};
              });
  if (!current) {
    co_return current.Error();
  }

  std::vector<StoredMessage> stored;
  if (!current->empty()) {
    auto loaded = co_await LoadStoredMessagesAsync(*state_->database,
                                                   current->front().first);
    if (!loaded) {
      co_return loaded.Error();
    }
    stored = std::move(*loaded);
  }

  std::vector<domain::ChatMessage> hydrated;
  hydrated.reserve(stored.size());
  std::int64_t next_order = 0;
  auto next_message_id = InitialMessageId();
  for (const auto &row : stored) {
    if (row.local_order < 0) {
      continue;
    }
    auto message = HydrateMessage(row);
    const auto message_id = message.id;
    hydrated.push_back(std::move(message));
    if (message_id >= next_message_id &&
        message_id != std::numeric_limits<std::uint64_t>::max()) {
      next_message_id = message_id + 1U;
    }
    next_order = std::max(next_order, row.local_order + 1);
  }

  state_->selection_barrier.Invalidate();
  state_->pending.clear();
  state_->conversations = std::move(*summaries);
  state_->messages = std::move(hydrated);
  state_->conversation_id.clear();
  state_->conversation_created_at = 0;
  if (!current->empty()) {
    state_->conversation_id = std::move(current->front().first);
    state_->conversation_created_at = current->front().second;
  }
  state_->next_local_order = next_order;
  state_->next_message_id = next_message_id;
  state_->last_error.clear();
  state_->NotifyChanged();
  co_return Result<void>{};
}

std::span<const domain::ChatMessage>
SqliteConversationStore::Messages() const noexcept {
  return state_->messages;
}

std::uint64_t SqliteConversationStore::AllocateMessageId() noexcept {
  if (state_->next_message_id == std::numeric_limits<std::uint64_t>::max()) {
    state_->next_message_id = 1;
  }
  return state_->next_message_id++;
}

void SqliteConversationStore::Append(domain::ChatMessage message) {
  if (state_->conversation_id.empty()) {
    state_->conversation_id = NewConversationId();
    state_->conversation_created_at = NowMilliseconds();
    state_->next_local_order = 0;
  }
  if (message.id >= state_->next_message_id &&
      message.id != std::numeric_limits<std::uint64_t>::max()) {
    state_->next_message_id = message.id + 1U;
  }
  state_->messages.push_back(message);
  State::Event event{.operation = State::Operation::append,
                     .conversation_id = state_->conversation_id,
                     .conversation_created_at =
                         state_->conversation_created_at,
                     .selection_generation =
                         state_->selection_barrier.Generation(),
                     .message = std::move(message)};
  if (state_->selection_barrier.DefersAppendTo(state_->conversation_id)) {
    event.local_order = -1;
  } else {
    event.local_order = state_->next_local_order++;
  }
  const auto summary = std::ranges::find(
      state_->conversations, state_->conversation_id,
      &application::ConversationSummary::id);
  if (summary == state_->conversations.end()) {
    state_->conversations.insert(
        state_->conversations.begin(),
        application::ConversationSummary{
            .id = state_->conversation_id,
            .title = event.conversation_title,
            .updated_at_millis = event.timestamp,
        });
  } else {
    summary->updated_at_millis = event.timestamp;
    std::ranges::rotate(state_->conversations.begin(), summary,
                        std::next(summary));
  }
  state_->pending.push_back(std::move(event));
  state_->NotifyChanged();
  ScheduleFlush(state_);
}

void SqliteConversationStore::Clear() {
  state_->messages.clear();
  state_->next_local_order = 0;
  if (!state_->conversation_id.empty()) {
    state_->pending.push_back(State::Event{
        .operation = State::Operation::clear,
        .conversation_id = state_->conversation_id,
    });
  }
  state_->NotifyChanged();
  ScheduleFlush(state_);
}

std::optional<domain::ChatMessage>
SqliteConversationStore::RecallUserMessage(std::uint64_t message_id) {
  const auto found = std::ranges::find(state_->messages, message_id,
                                       &domain::ChatMessage::id);
  if (found == state_->messages.end() ||
      found->role != domain::MessageRole::user ||
      state_->conversation_id.empty()) {
    return std::nullopt;
  }
  const auto cutoff = static_cast<std::int64_t>(
      std::distance(state_->messages.begin(), found));
  auto recalled = *found;
  state_->messages.erase(found, state_->messages.end());
  state_->next_local_order = cutoff;
  state_->pending.push_back(State::Event{
      .operation = State::Operation::recall,
      .conversation_id = state_->conversation_id,
      .local_order = cutoff,
  });
  state_->NotifyChanged();
  ScheduleFlush(state_);
  return recalled;
}

std::span<const application::ConversationSummary>
SqliteConversationStore::Conversations() const noexcept {
  return state_->conversations;
}

std::string_view
SqliteConversationStore::CurrentConversationId() const noexcept {
  return state_->conversation_id;
}

void SqliteConversationStore::StartNewConversation() {
  state_->selection_barrier.Invalidate();
  state_->messages.clear();
  state_->conversation_id = NewConversationId();
  state_->conversation_created_at = NowMilliseconds();
  state_->next_local_order = 0;
  state_->NotifyChanged();
}

void SqliteConversationStore::SelectConversation(std::string_view id) {
  if (id.empty() || id == state_->conversation_id ||
      std::ranges::find(state_->conversations, id,
                        &application::ConversationSummary::id) ==
          state_->conversations.end()) {
    return;
  }
  const auto generation = state_->selection_barrier.Begin(std::string{id});
  state_->messages.clear();
  state_->conversation_id = std::string{id};
  state_->conversation_created_at = 0;
  state_->next_local_order = 0;
  state_->pending.push_back(State::Event{
      .operation = State::Operation::select,
      .conversation_id = std::string{id},
      .selection_generation = generation,
  });
  state_->NotifyChanged();
  ScheduleFlush(state_);
}

void SqliteConversationStore::DeleteConversation(std::string_view id) {
  if (id.empty()) {
    return;
  }
  const std::string owned_id{id};
  const auto summary = std::ranges::find(
      state_->conversations, owned_id,
      &application::ConversationSummary::id);
  if (summary == state_->conversations.end()) {
    return;
  }
  if (owned_id == state_->conversation_id) {
    state_->selection_barrier.Invalidate();
  }
  state_->conversations.erase(summary);
  if (owned_id == state_->conversation_id) {
    state_->messages.clear();
    state_->conversation_id.clear();
    state_->conversation_created_at = 0;
    state_->next_local_order = 0;
  }
  state_->pending.push_back(State::Event{
      .operation = State::Operation::erase,
      .conversation_id = owned_id,
  });
  state_->NotifyChanged();
  ScheduleFlush(state_);
}

std::string SqliteConversationStore::LastPersistenceError() const {
  return state_->last_error;
}

void SqliteConversationStore::ScheduleFlush(
    const std::shared_ptr<State> &state) {
  if (state->phase != State::Phase::ready || state->flush_running ||
      state->pending.empty()) {
    return;
  }
  state->flush_running = true;
  try {
    state->tasks.Launch([state]() { return FlushAsync(state); });
  } catch (const std::exception &exception) {
    state->flush_running = false;
    state->phase = State::Phase::failed;
    state->last_error = exception.what();
    state->NotifyChanged();
  }
}

huxerui::Task<Result<void>>
SqliteConversationStore::ProcessNextAsync(std::shared_ptr<State> state) {
  if (!state->database || state->pending.empty()) {
    co_return Result<void>{};
  }

  const State::Event event = state->pending.front();
  Result<void> result;

  if (event.operation == State::Operation::append) {
    result = co_await state->database->TransactionAsync(
        [event](Transaction &transaction) -> Result<void> {
          std::int64_t local_order = event.local_order;
          if (local_order < 0) {
            auto orders = transaction.Query<std::int64_t>(
                "SELECT COALESCE(MAX(local_order) + 1, 0) FROM messages "
                "WHERE conversation_id = ?",
                [](const RowView &row) { return row.Get<std::int64_t>(0); },
                event.conversation_id);
            if (!orders) {
              return orders.Error();
            }
            if (orders->size() != 1) {
              return Error{ErrorCode::Decode,
                           "message order query returned no value",
                           "append conversation message"};
            }
            local_order = orders->front();
          }
          auto unset_current = transaction.Execute(
              "UPDATE conversations SET current = 0 "
              "WHERE current <> 0 AND id <> ?",
              event.conversation_id);
          if (!unset_current) {
            return unset_current.Error();
          }
          auto conversation = transaction.Execute(
              "INSERT INTO conversations "
              "(id, title, project_id, created_at, updated_at, current, "
              "raw_json) VALUES (?, ?, NULL, ?, ?, 1, NULL) "
              "ON CONFLICT(id) DO UPDATE SET updated_at = excluded.updated_at, "
              "current = 1",
              event.conversation_id, event.conversation_title,
              event.conversation_created_at, event.timestamp);
          if (!conversation) {
            return conversation.Error();
          }
          const std::string message_id =
              std::string{kOwnedMessagePrefix} +
              std::to_string(event.message.id);
          const auto message_timestamp =
              event.message.processing_started_at > 0
                  ? event.message.processing_started_at
                  : event.timestamp;
          auto message = transaction.Execute(
              "INSERT INTO messages "
              "(id, conversation_id, local_order, role, content, "
              "reasoning_content, timestamp, streaming, hidden, "
              "exclude_from_context, tool_call_id, tool_name, is_error, "
              "raw_json) VALUES (?, ?, ?, ?, '', ?, ?, ?, ?, ?, NULL, "
              "NULL, ?, '') "
              "ON CONFLICT(id) DO UPDATE SET "
              "conversation_id = excluded.conversation_id, "
              "local_order = excluded.local_order, role = excluded.role, "
              "content = '', reasoning_content = excluded.reasoning_content, "
              "timestamp = excluded.timestamp, streaming = excluded.streaming, "
              "hidden = excluded.hidden, "
              "exclude_from_context = excluded.exclude_from_context, "
              "is_error = excluded.is_error, raw_json = ''",
              message_id, event.conversation_id, local_order,
              RoleName(event.message.role), event.message.reasoning_content,
              message_timestamp,
              static_cast<std::int64_t>(event.message.streaming),
              static_cast<std::int64_t>(event.message.hidden),
              static_cast<std::int64_t>(event.message.exclude_from_context),
              static_cast<std::int64_t>(event.message.error));
          if (!message) {
            return message.Error();
          }
          auto old_chunks = transaction.Execute(
              "DELETE FROM message_text_chunks "
              "WHERE message_id = ? AND field_name IN ('content', 'raw_json')",
              message_id);
          if (!old_chunks) {
            return old_chunks.Error();
          }
          const auto chunks =
              legacy_schema::SplitMessageText(event.message.content);
          for (std::size_t index = 0; index < chunks.size(); ++index) {
            auto chunk = transaction.Execute(
                "INSERT INTO message_text_chunks "
                "(message_id, field_name, chunk_order, content) "
                "VALUES (?, 'content', ?, ?)",
                message_id, static_cast<std::int64_t>(index),
                std::string{chunks[index]});
            if (!chunk) {
              return chunk.Error();
            }
          }
          const auto attachments_json =
              EncodeMessageRawJson(event.message);
          const auto raw_chunks =
              legacy_schema::SplitMessageText(attachments_json);
          for (std::size_t index = 0; index < raw_chunks.size(); ++index) {
            auto chunk = transaction.Execute(
                "INSERT INTO message_text_chunks "
                "(message_id, field_name, chunk_order, content) "
                "VALUES (?, 'raw_json', ?, ?)",
                message_id, static_cast<std::int64_t>(index),
                std::string{raw_chunks[index]});
            if (!chunk) {
              return chunk.Error();
            }
          }
          auto old_results = transaction.Execute(
              "DELETE FROM tool_results WHERE message_id = ?", message_id);
          if (!old_results)
            return old_results.Error();
          auto old_calls = transaction.Execute(
              "DELETE FROM tool_calls WHERE message_id = ?", message_id);
          if (!old_calls)
            return old_calls.Error();
          auto old_blocks = transaction.Execute(
              "DELETE FROM message_blocks WHERE message_id = ?", message_id);
          if (!old_blocks)
            return old_blocks.Error();
          for (std::size_t index = 0; index < event.message.timeline.size();
               ++index) {
            std::optional<Error> persistence_error;
            std::visit(
                [&](const auto &entry) {
                  using Entry = std::decay_t<decltype(entry)>;
                  const auto block_order = static_cast<std::int64_t>(index);
                  const auto turn = std::to_string(entry.turn_index);
                  if constexpr (std::same_as<
                                    Entry, domain::AssistantReasoningEvent>) {
                    auto status = std::string{
                        entry.kind == domain::ReasoningKind::summary
                            ? "summary"
                            : "thinking"};
                    if (entry.starts_new_segment)
                      status += ":new";
                    auto inserted = transaction.Execute(
                        "INSERT INTO message_blocks "
                        "(message_id, block_order, type, content, status, "
                        "raw_json) VALUES (?, ?, 'reasoning', ?, ?, ?)",
                        message_id, block_order, entry.text, status, turn);
                    if (!inserted)
                      persistence_error = inserted.Error();
                  } else if constexpr (std::same_as<
                                           Entry, domain::AssistantTextEvent>) {
                    auto inserted = transaction.Execute(
                        "INSERT INTO message_blocks "
                        "(message_id, block_order, type, content, status, "
                        "raw_json) VALUES (?, ?, 'text', ?, '', ?)",
                        message_id, block_order, entry.text, turn);
                    if (!inserted)
                      persistence_error = inserted.Error();
                  } else {
                    auto call = transaction.Execute(
                        "INSERT INTO tool_calls "
                        "(id, message_id, name, arguments, created_at, "
                        "duration_ms, error_message, raw_json) "
                        "VALUES (?, ?, ?, ?, ?, ?, ?, '') "
                        "ON CONFLICT(id) DO UPDATE SET "
                        "message_id = excluded.message_id, "
                        "name = excluded.name, arguments = excluded.arguments, "
                        "created_at = excluded.created_at, "
                        "duration_ms = excluded.duration_ms, "
                        "error_message = excluded.error_message",
                        entry.call.id, message_id, entry.call.name,
                        entry.call.arguments_json, entry.call.created_at_millis,
                        entry.call.duration_millis, entry.call.error_message);
                    if (!call) {
                      persistence_error = call.Error();
                      return;
                    }
                    if (entry.result) {
                      auto result = transaction.Execute(
                          "INSERT INTO tool_results "
                          "(message_id, tool_call_id, content, is_error, "
                          "diff_id, review_state, raw_json) "
                          "VALUES (?, ?, ?, ?, ?, ?, '')",
                          message_id, entry.result->call_id,
                          entry.result->content,
                          static_cast<std::int64_t>(entry.result->error),
                          entry.result->diff_id, entry.result->review_state);
                      if (!result) {
                        persistence_error = result.Error();
                        return;
                      }
                    }
                    auto inserted = transaction.Execute(
                        "INSERT INTO message_blocks "
                        "(message_id, block_order, type, content, status, "
                        "raw_json) VALUES (?, ?, 'tool', ?, ?, ?)",
                        message_id, block_order, entry.call.id,
                        std::string{ToolStatusName(entry.call.status)}, turn);
                    if (!inserted)
                      persistence_error = inserted.Error();
                  }
                },
                event.message.timeline[index]);
            if (persistence_error)
              return *persistence_error;
          }
          auto old_attachments = transaction.Execute(
              "DELETE FROM attachments WHERE message_id = ?", message_id);
          if (!old_attachments) {
            return old_attachments.Error();
          }
          for (const auto &attachment : event.message.attachments) {
            auto inserted = transaction.Execute(
                "INSERT INTO attachments "
                "(message_id, name, path, source, raw_json) "
                "VALUES (?, ?, ?, ?, '')",
                message_id, attachment.Name(), attachment.Path(),
                attachment.Source());
            if (!inserted) {
              return inserted.Error();
            }
          }
          return {};
        });
  } else if (event.operation == State::Operation::clear) {
    const auto cleared = co_await state->database->ExecuteAsync(
        "DELETE FROM messages WHERE conversation_id = ?",
        event.conversation_id);
    result = cleared ? Result<void>{} : Result<void>{cleared.Error()};
  } else if (event.operation == State::Operation::recall) {
    result = co_await state->database->TransactionAsync(
        [event](Transaction &transaction) -> Result<void> {
          auto removed = transaction.Execute(
              "DELETE FROM messages WHERE conversation_id = ? "
              "AND local_order >= ?",
              event.conversation_id, event.local_order);
          if (!removed)
            return removed.Error();
          auto touched = transaction.Execute(
              "UPDATE conversations SET updated_at = ? WHERE id = ?",
              event.timestamp, event.conversation_id);
          return touched ? Result<void>{} : Result<void>{touched.Error()};
        });
  } else if (event.operation == State::Operation::erase) {
    result = co_await state->database->TransactionAsync(
        [id = event.conversation_id](Transaction &transaction)
            -> Result<void> {
          auto index_tables = transaction.Query<std::string>(
              "SELECT name FROM sqlite_master WHERE type = 'table' "
              "AND name IN ('conversation_index', "
              "'conversation_index_fts')",
              [](const RowView &row) { return row.Get<std::string>(0); });
          if (!index_tables) {
            return index_tables.Error();
          }
          const auto has_table = [&index_tables](std::string_view name) {
            return std::ranges::find(*index_tables, name) !=
                   index_tables->end();
          };
          if (has_table("conversation_index")) {
            auto removed = transaction.Execute(
                "DELETE FROM conversation_index WHERE conversation_id = ?",
                id);
            if (!removed) {
              return removed.Error();
            }
          }
          if (has_table("conversation_index_fts")) {
            auto removed = transaction.Execute(
                "DELETE FROM conversation_index_fts "
                "WHERE conversation_id = ?",
                id);
            static_cast<void>(removed);
          }
          auto erased = transaction.Execute(
              "DELETE FROM conversations WHERE id = ?", id);
          return erased ? Result<void>{} : Result<void>{erased.Error()};
        });
    if (result && state->conversation_id == event.conversation_id) {
      state->messages.clear();
      state->conversation_id.clear();
      state->conversation_created_at = 0;
      state->next_local_order = 0;
      state->NotifyChanged();
    }
  } else {
    if (!state->selection_barrier.Matches(event.selection_generation,
                                          event.conversation_id) ||
        std::ranges::find(state->conversations, event.conversation_id,
                          &application::ConversationSummary::id) ==
            state->conversations.end()) {
      state->selection_barrier.Settle(event.selection_generation);
      state->pending.erase(state->pending.begin());
      co_return Result<void>{};
    }
    auto metadata =
        co_await state->database->QueryAsync<std::int64_t>(
            "SELECT created_at FROM conversations WHERE id = ? LIMIT 1",
            [](const RowView &row) { return row.Get<std::int64_t>(0); },
            event.conversation_id);
    if (!metadata) {
      result = metadata.Error();
    } else if (metadata->empty()) {
      result = Error{ErrorCode::NotFound, "conversation no longer exists",
                     "select conversation"};
    } else {
      auto stored = co_await LoadStoredMessagesAsync(*state->database,
                                                     event.conversation_id);
      if (!stored) {
        result = stored.Error();
      } else if (!state->selection_barrier.Matches(
                     event.selection_generation, event.conversation_id)) {
        result = Result<void>{};
      } else {
        auto selected = co_await state->database->TransactionAsync(
            [id = event.conversation_id](Transaction &transaction)
                -> Result<void> {
              auto reset = transaction.Execute(
                  "UPDATE conversations SET current = 0 WHERE current <> 0");
              if (!reset) {
                return reset.Error();
              }
              auto set = transaction.Execute(
                  "UPDATE conversations SET current = 1 WHERE id = ?", id);
              return set ? Result<void>{} : Result<void>{set.Error()};
            });
        if (!selected) {
          result = selected.Error();
        } else if (state->selection_barrier.Matches(
                       event.selection_generation, event.conversation_id)) {
          std::vector<domain::ChatMessage> loaded;
          loaded.reserve(stored->size());
          std::int64_t next_order = 0;
          for (const auto &row : *stored) {
            if (row.local_order < 0) {
              continue;
            }
            loaded.push_back(HydrateMessage(row));
            next_order = std::max(next_order, row.local_order + 1);
          }
          for (auto pending = std::next(state->pending.begin());
               pending != state->pending.end(); ++pending) {
            if (pending->operation != State::Operation::append ||
                pending->conversation_id != event.conversation_id ||
                pending->selection_generation != event.selection_generation) {
              continue;
            }
            if (pending->local_order < 0) {
              pending->local_order = next_order++;
            } else {
              next_order = std::max(next_order, pending->local_order + 1);
            }
            loaded.push_back(pending->message);
          }
          state->messages = std::move(loaded);
          state->conversation_id = event.conversation_id;
          state->conversation_created_at = metadata->front();
          state->next_local_order = next_order;
          state->selection_barrier.Settle(event.selection_generation);
          state->next_message_id = InitialMessageId();
          for (const auto &message : state->messages) {
            if (message.id >= state->next_message_id &&
                message.id != std::numeric_limits<std::uint64_t>::max()) {
              state->next_message_id = message.id + 1U;
            }
          }
          state->NotifyChanged();
          result = Result<void>{};
        } else {
          result = Result<void>{};
        }
      }
    }
  }

  if (!result) {
    co_return result.Error();
  }
  state->pending.erase(state->pending.begin());
  co_return Result<void>{};
}

huxerui::Task<void>
SqliteConversationStore::FlushAsync(std::shared_ptr<State> state) {
  while (!state->pending.empty()) {
    auto persisted = co_await ProcessNextAsync(state);
    if (!persisted) {
      state->Fail(persisted.Error());
      state->flush_running = false;
      co_return;
    }
  }
  state->flush_running = false;
}

} // namespace linecode::infrastructure
