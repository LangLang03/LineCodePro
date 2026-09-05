#include "infrastructure/sqlite_conversation_store.h"

#include <algorithm>
#include <charconv>
#include <chrono>
#include <exception>
#include <iterator>
#include <limits>
#include <optional>
#include <string_view>
#include <system_error>
#include <utility>
#include <vector>

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
};

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
  return StoredMessage{std::move(*id), *local_order, std::move(*role),
                       std::move(*content)};
}

Result<void> ExecuteSchema(Transaction &transaction, std::int64_t version) {
  if (version != 0 && version != legacy_schema::user_version) {
    return Error{
        ErrorCode::SchemaMismatch,
        "linecode.db must be an empty database or legacy schema version 4",
        "validate PRAGMA user_version"};
  }

  for (const std::string_view statement : {
           legacy_schema::create_conversations,
           legacy_schema::create_messages,
           legacy_schema::create_conversations_updated_index,
           legacy_schema::create_messages_order_index,
       }) {
    auto executed = transaction.Execute(std::string{statement});
    if (!executed) {
      return executed.Error();
    }
  }

  if (version == 0) {
    auto versioned = transaction.Execute("PRAGMA user_version = 4");
    if (!versioned) {
      return versioned.Error();
    }
  }
  return {};
}

huxerui::Task<Result<void>>
EnsureCompatibleSchemaAsync(const Database &database) {
  auto versions = co_await database.QueryAsync<std::int64_t>(
      "PRAGMA user_version",
      [](const RowView &row) { return row.Get<std::int64_t>(0); });
  if (!versions) {
    co_return versions.Error();
  }
  if (versions->size() != 1) {
    co_return Error{ErrorCode::Decode, "PRAGMA user_version returned no value",
                    "read PRAGMA user_version"};
  }

  const auto version = versions->front();
  co_return co_await database.TransactionAsync(
      [version](Transaction &transaction) {
        return ExecuteSchema(transaction, version);
      });
}

huxerui::Task<Result<std::string>>
FindOrCreateCurrentConversationAsync(const Database &database) {
  auto current = co_await database.QueryAsync<std::string>(
      "SELECT id FROM conversations WHERE current <> 0 "
      "ORDER BY updated_at DESC LIMIT 1",
      [](const RowView &row) { return row.Get<std::string>(0); });
  if (!current) {
    co_return current.Error();
  }
  if (!current->empty()) {
    co_return std::move(current->front());
  }

  const auto timestamp = NowMilliseconds();
  std::string id = "linecodepro-conversation:" + std::to_string(timestamp);
  auto inserted = co_await database.ExecuteAsync(
      "INSERT INTO conversations "
      "(id, title, project_id, created_at, updated_at, current, raw_json) "
      "VALUES (?, ?, NULL, ?, ?, 1, NULL)",
      id, std::string{"New conversation"}, timestamp, timestamp);
  if (!inserted) {
    co_return inserted.Error();
  }
  co_return id;
}

} // namespace

struct SqliteConversationStore::State final {
  enum class Phase : std::uint8_t { waiting, hydrating, ready, failed };
  enum class Operation : std::uint8_t { append, clear };

  struct Event final {
    Operation operation{Operation::append};
    domain::ChatMessage message;
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
  std::vector<domain::ChatMessage> messages;
  std::vector<Event> pending;
  std::int64_t next_local_order{};
  std::uint64_t next_message_id{};
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

  auto schema = co_await EnsureCompatibleSchemaAsync(*opened);
  if (!schema) {
    state_->Fail(schema.Error());
    co_return schema.Error();
  }

  auto conversation = co_await FindOrCreateCurrentConversationAsync(*opened);
  if (!conversation) {
    state_->Fail(conversation.Error());
    co_return conversation.Error();
  }

  auto stored = co_await opened->QueryAsync<StoredMessage>(
      "SELECT id, local_order, role, content FROM messages "
      "WHERE conversation_id = ? AND hidden = 0 ORDER BY local_order",
      DecodeStoredMessage, *conversation);
  if (!stored) {
    state_->Fail(stored.Error());
    co_return stored.Error();
  }

  std::vector<domain::ChatMessage> hydrated;
  hydrated.reserve(stored->size() + state_->pending.size());
  std::int64_t next_order = 0;
  for (const auto &row : *stored) {
    if (row.local_order < 0) {
      continue;
    }
    const auto fallback_id = static_cast<std::uint64_t>(row.local_order) + 1U;
    const auto message_id = ParseOwnedMessageId(row.id).value_or(fallback_id);
    hydrated.push_back(domain::ChatMessage{
        .id = message_id,
        .role = ParseRole(row.role),
        .content = row.content,
    });
    if (message_id >= state_->next_message_id &&
        message_id != std::numeric_limits<std::uint64_t>::max()) {
      state_->next_message_id = message_id + 1U;
    }
    next_order = std::max(next_order, row.local_order + 1);
  }

  for (auto &event : state_->pending) {
    if (event.operation == State::Operation::clear) {
      hydrated.clear();
      next_order = 0;
      continue;
    }
    event.local_order = next_order++;
    hydrated.push_back(event.message);
  }

  state_->messages = std::move(hydrated);
  state_->next_local_order = next_order;
  state_->conversation_id = std::move(*conversation);
  state_->database = std::move(*opened);
  state_->phase = State::Phase::ready;
  state_->last_error.clear();
  state_->NotifyChanged();
  ScheduleFlush(state_);
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
  if (message.id >= state_->next_message_id &&
      message.id != std::numeric_limits<std::uint64_t>::max()) {
    state_->next_message_id = message.id + 1U;
  }
  state_->messages.push_back(message);
  State::Event event{.operation = State::Operation::append,
                     .message = std::move(message)};
  if (state_->phase == State::Phase::ready) {
    event.local_order = state_->next_local_order++;
  }
  state_->pending.push_back(std::move(event));
  ScheduleFlush(state_);
}

void SqliteConversationStore::Clear() {
  state_->messages.clear();
  if (state_->phase == State::Phase::ready) {
    state_->next_local_order = 0;
  }
  state_->pending.push_back(State::Event{.operation = State::Operation::clear});
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
SqliteConversationStore::PersistPendingAsync(std::shared_ptr<State> state) {
  if (!state->database || state->pending.empty()) {
    co_return Result<void>{};
  }

  auto batch = std::move(state->pending);
  state->pending.clear();
  const auto conversation_id = state->conversation_id;
  const auto result = co_await state->database->TransactionAsync(
      [batch, conversation_id](Transaction &transaction) -> Result<void> {
        for (const auto &event : batch) {
          Result<huxerui::sqlite::ExecuteResult> executed =
              event.operation == State::Operation::clear
                  ? transaction.Execute(
                        "DELETE FROM messages WHERE conversation_id = ?",
                        conversation_id)
                  : transaction.Execute("INSERT INTO messages "
                                        "(id, conversation_id, local_order, "
                                        "role, content, reasoning_content, "
                                        "timestamp, streaming, hidden, "
                                        "exclude_from_context, tool_call_id, "
                                        "tool_name, is_error, raw_json) "
                                        "VALUES (?, ?, ?, ?, ?, NULL, ?, 0, 0, "
                                        "0, NULL, NULL, 0, NULL)",
                                        std::string{kOwnedMessagePrefix} +
                                            std::to_string(event.message.id),
                                        conversation_id, event.local_order,
                                        RoleName(event.message.role),
                                        event.message.content, event.timestamp);
          if (!executed) {
            return executed.Error();
          }
        }
        auto touched = transaction.Execute(
            "UPDATE conversations SET updated_at = ? WHERE id = ?",
            NowMilliseconds(), conversation_id);
        if (!touched) {
          return touched.Error();
        }
        return {};
      });

  if (!result) {
    batch.insert(batch.end(), std::make_move_iterator(state->pending.begin()),
                 std::make_move_iterator(state->pending.end()));
    state->pending = std::move(batch);
    co_return result.Error();
  }
  co_return Result<void>{};
}

huxerui::Task<void>
SqliteConversationStore::FlushAsync(std::shared_ptr<State> state) {
  while (!state->pending.empty()) {
    auto persisted = co_await PersistPendingAsync(state);
    if (!persisted) {
      state->Fail(persisted.Error());
      state->flush_running = false;
      co_return;
    }
  }
  state->flush_running = false;
}

} // namespace linecode::infrastructure
