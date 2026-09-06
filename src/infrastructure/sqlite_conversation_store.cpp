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
  enum class Operation : std::uint8_t { append, clear, select, erase };

  struct Event final {
    Operation operation{Operation::append};
    std::string conversation_id;
    std::string conversation_title{"New conversation"};
    std::int64_t conversation_created_at{};
    std::uint64_t selection_generation{};
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
    auto loaded = co_await opened->QueryAsync<StoredMessage>(
        std::string{legacy_schema::load_visible_messages},
        DecodeStoredMessage, current->front().first);
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
          auto message = transaction.Execute(
              "INSERT INTO messages "
              "(id, conversation_id, local_order, role, content, "
              "reasoning_content, timestamp, streaming, hidden, "
              "exclude_from_context, tool_call_id, tool_name, is_error, "
              "raw_json) VALUES (?, ?, ?, ?, '', NULL, ?, 0, 0, 0, NULL, "
              "NULL, 0, NULL) "
              "ON CONFLICT(id) DO UPDATE SET "
              "conversation_id = excluded.conversation_id, "
              "local_order = excluded.local_order, role = excluded.role, "
              "content = '', timestamp = excluded.timestamp",
              message_id, event.conversation_id, local_order,
              RoleName(event.message.role), event.timestamp);
          if (!message) {
            return message.Error();
          }
          auto old_chunks = transaction.Execute(
              "DELETE FROM message_text_chunks "
              "WHERE message_id = ? AND field_name = 'content'",
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
          return {};
        });
  } else if (event.operation == State::Operation::clear) {
    const auto cleared = co_await state->database->ExecuteAsync(
        "DELETE FROM messages WHERE conversation_id = ?",
        event.conversation_id);
    result = cleared ? Result<void>{} : Result<void>{cleared.Error()};
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
      auto stored = co_await state->database->QueryAsync<StoredMessage>(
          std::string{legacy_schema::load_visible_messages},
          DecodeStoredMessage, event.conversation_id);
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
            const auto fallback_id =
                static_cast<std::uint64_t>(row.local_order) + 1U;
            loaded.push_back(domain::ChatMessage{
                .id = ParseOwnedMessageId(row.id).value_or(fallback_id),
                .role = ParseRole(row.role),
                .content = row.content,
            });
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
