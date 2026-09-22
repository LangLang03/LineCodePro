#include "infrastructure/sqlite_memory_store.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <iomanip>
#include <optional>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/sqlite.h>

#include "domain/memory_rag.h"
#include "infrastructure/legacy_feature_schema.h"

namespace linecode::infrastructure {

class SqliteMemoryStoreState final {
public:
  explicit SqliteMemoryStoreState(huxerui::File file)
      : database_file(std::move(file)) {}

  huxerui::File database_file;
  std::optional<huxerui::sqlite::Database> database;
};

namespace {

using application::MemoryStoreError;
using application::MemoryStoreResult;
using huxerui::Task;
using huxerui::sqlite::Database;
using huxerui::sqlite::Result;
using huxerui::sqlite::RowView;
using huxerui::sqlite::Transaction;

constexpr std::int64_t kOverviewLimit = 200;
constexpr std::int64_t kScanLimit = 120;

// Column list every `domain::MemoryRecord` projection shares, so `DecodeMemory`
// can keep positional indices. `title` stays last: it was added after the
// legacy columns and existing indices must not shift.
constexpr std::string_view kMemoryColumns =
    "id, scope, project_id, content, source, confidence, created_at, "
    "updated_at, last_used_at, use_count, title";

// `memories` predates the memory title, so an existing database needs the
// column added on open. New databases run the legacy DDL first and then the
// same migration, which keeps one code path for both.
constexpr std::string_view kAddMemoryTitleColumn =
    "ALTER TABLE memories ADD COLUMN title TEXT NOT NULL DEFAULT ''";

MemoryStoreError StoreError(const huxerui::sqlite::Error &error) {
  return {.message = error.Message()};
}

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

std::string NewMemoryId() {
  static std::atomic<std::uint64_t> sequence{};
  const auto time = std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::system_clock::now().time_since_epoch())
                        .count();
  const auto mix = [](std::uint64_t value) {
    value += 0x9e3779b97f4a7c15ULL;
    value = (value ^ (value >> 30U)) * 0xbf58476d1ce4e5b9ULL;
    value = (value ^ (value >> 27U)) * 0x94d049bb133111ebULL;
    return value ^ (value >> 31U);
  };
  const auto counter = sequence.fetch_add(1, std::memory_order_relaxed);
  const auto first = mix(static_cast<std::uint64_t>(time) ^ counter);
  const auto second = mix(first ^ (counter + 0xd1b54a32d192ed03ULL));
  std::ostringstream id;
  id << "mem_" << std::hex << std::setfill('0') << std::setw(16) << first
     << std::setw(16) << second;
  return id.str();
}

std::string Utf8Prefix(std::string_view value, std::size_t characters) {
  std::size_t offset{};
  for (std::size_t count{}; offset < value.size() && count < characters;
       ++count) {
    const auto first = static_cast<unsigned char>(value[offset]);
    const std::size_t width = first < 0x80U   ? 1U
                              : first < 0xE0U ? 2U
                              : first < 0xF0U ? 3U
                                              : 4U;
    offset += std::min(width, value.size() - offset);
  }
  return std::string{value.substr(0, offset)};
}

template <typename T>
Result<T> ReadRequired(const RowView &row, std::size_t index) {
  return row.Get<T>(index);
}

Result<domain::MemoryRecord> DecodeMemory(const RowView &row) {
  auto id = ReadRequired<std::string>(row, 0);
  if (!id)
    return id.Error();
  auto scope = ReadRequired<std::string>(row, 1);
  if (!scope)
    return scope.Error();
  auto project = ReadRequired<std::optional<std::string>>(row, 2);
  if (!project)
    return project.Error();
  auto content = ReadRequired<std::string>(row, 3);
  if (!content)
    return content.Error();
  auto source = ReadRequired<std::string>(row, 4);
  if (!source)
    return source.Error();
  auto confidence = ReadRequired<double>(row, 5);
  if (!confidence)
    return confidence.Error();
  auto created = ReadRequired<std::int64_t>(row, 6);
  if (!created)
    return created.Error();
  auto updated = ReadRequired<std::int64_t>(row, 7);
  if (!updated)
    return updated.Error();
  auto last_used = ReadRequired<std::optional<std::int64_t>>(row, 8);
  if (!last_used)
    return last_used.Error();
  auto use_count = ReadRequired<std::int64_t>(row, 9);
  if (!use_count)
    return use_count.Error();
  auto title = ReadRequired<std::string>(row, 10);
  if (!title)
    return title.Error();
  return domain::MemoryRecord{
      .id = std::move(*id),
      .scope = domain::ParseMemoryScope(*scope),
      .project_id = project->value_or(""),
      .title = std::move(*title),
      .content = std::move(*content),
      .source = std::move(*source),
      .confidence = *confidence,
      .created_at = *created,
      .updated_at = *updated,
      .last_used_at = last_used->value_or(0),
      .use_count = *use_count,
  };
}

Result<domain::WorkingMemoryRecord> DecodeWorkingMemory(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id)
    return id.Error();
  auto project = row.Get<std::optional<std::string>>(1);
  if (!project)
    return project.Error();
  auto content = row.Get<std::string>(2);
  if (!content)
    return content.Error();
  auto source = row.Get<std::string>(3);
  if (!source)
    return source.Error();
  auto expires = row.Get<std::optional<std::int64_t>>(4);
  if (!expires)
    return expires.Error();
  auto created = row.Get<std::int64_t>(5);
  if (!created)
    return created.Error();
  auto updated = row.Get<std::int64_t>(6);
  if (!updated)
    return updated.Error();
  return domain::WorkingMemoryRecord{
      .id = std::move(*id),
      .project_id = project->value_or(""),
      .content = std::move(*content),
      .source = std::move(*source),
      .expires_at = expires->value_or(0),
      .created_at = *created,
      .updated_at = *updated,
  };
}

Result<domain::ConversationIndexRecord>
DecodeConversationIndex(const RowView &row) {
  auto id = row.Get<std::string>(0);
  if (!id)
    return id.Error();
  auto project = row.Get<std::optional<std::string>>(1);
  if (!project)
    return project.Error();
  auto conversation = row.Get<std::string>(2);
  if (!conversation)
    return conversation.Error();
  auto message = row.Get<std::optional<std::string>>(3);
  if (!message)
    return message.Error();
  auto role = row.Get<std::string>(4);
  if (!role)
    return role.Error();
  auto text = row.Get<std::string>(5);
  if (!text)
    return text.Error();
  auto title = row.Get<std::optional<std::string>>(6);
  if (!title)
    return title.Error();
  auto created = row.Get<std::int64_t>(7);
  if (!created)
    return created.Error();
  auto updated = row.Get<std::int64_t>(8);
  if (!updated)
    return updated.Error();
  return domain::ConversationIndexRecord{
      .id = std::move(*id),
      .project_id = project->value_or(""),
      .conversation_id = std::move(*conversation),
      .message_id = message->value_or(""),
      .role = std::move(*role),
      .text = std::move(*text),
      .title = title->value_or(""),
      .created_at = *created,
      .updated_at = *updated,
  };
}

Result<domain::MemorySkillRecord> DecodeSkill(const RowView &row) {
  auto name = row.Get<std::string>(0);
  if (!name)
    return name.Error();
  auto path = row.Get<std::optional<std::string>>(1);
  if (!path)
    return path.Error();
  auto description = row.Get<std::optional<std::string>>(2);
  if (!description)
    return description.Error();
  auto updated = row.Get<std::int64_t>(3);
  if (!updated)
    return updated.Error();
  return domain::MemorySkillRecord{
      .name = std::move(*name),
      .path = path->value_or(""),
      .description = description->value_or(""),
      .updated_at = *updated,
  };
}

Task<MemoryStoreResult<Database>>
Open(const std::shared_ptr<SqliteMemoryStoreState> &state) {
  if (state->database)
    co_return *state->database;
  auto opened = co_await Database::OpenAsync(
      state->database_file,
      huxerui::sqlite::OpenOptions{.create_parent_directories = true});
  if (!opened)
    co_return std::unexpected(StoreError(opened.Error()));
  auto schema = co_await opened->TransactionAsync(
      [](Transaction &transaction) -> Result<void> {
        constexpr std::array statements{
            legacy_feature_schema::create_memories,
            legacy_feature_schema::create_working_memory,
            legacy_feature_schema::create_conversation_index,
            legacy_feature_schema::create_skills,
            legacy_feature_schema::create_memories_scope_project_index,
            legacy_feature_schema::create_working_memory_project_index,
            legacy_feature_schema::create_conversation_index_project_index,
        };
        for (const auto statement : statements) {
          auto result = transaction.Execute(std::string{statement});
          if (!result)
            return result.Error();
        }
        // `memories.title` postdates the legacy schema, so a database created
        // before it needs the column added once.
        auto columns = transaction.Query<std::string>(
            "PRAGMA table_info(memories)",
            [](const RowView &row) { return row.Get<std::string>(1); });
        if (!columns)
          return columns.Error();
        if (!std::ranges::contains(*columns, std::string_view{"title"})) {
          auto altered = transaction.Execute(std::string{kAddMemoryTitleColumn});
          if (!altered)
            return altered.Error();
        }
        return {};
      });
  if (!schema)
    co_return std::unexpected(StoreError(schema.Error()));
  state->database = *opened;
  co_return *state->database;
}

Task<MemoryStoreResult<std::vector<domain::MemoryRecord>>>
ReadMemories(const Database &database, domain::MemoryScope scope,
             const std::string &project_id) {
  constexpr std::string_view columns = kMemoryColumns;
  const auto &definition = domain::MemoryScopeDefinition(scope);
  if (definition.global) {
    auto rows = co_await database.QueryAsync<domain::MemoryRecord>(
        "SELECT " + std::string{columns} +
            " FROM memories WHERE scope = ? ORDER BY updated_at DESC LIMIT ?",
        DecodeMemory, std::string{definition.storage_name}, kOverviewLimit);
    if (!rows)
      co_return std::unexpected(StoreError(rows.Error()));
    co_return std::move(*rows);
  }
  auto rows = co_await database.QueryAsync<domain::MemoryRecord>(
      "SELECT " + std::string{columns} +
          " FROM memories WHERE scope = ? AND "
          "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = "
          "'') ORDER BY updated_at DESC LIMIT ?",
      DecodeMemory, std::string{definition.storage_name}, project_id,
      project_id, kOverviewLimit);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return std::move(*rows);
}

} // namespace

SqliteMemoryStore::SqliteMemoryStore(huxerui::File database_file)
    : state_(
          std::make_shared<SqliteMemoryStoreState>(std::move(database_file))) {}

huxerui::Task<MemoryStoreResult<domain::MemoryOverview>>
SqliteMemoryStore::LoadOverview(std::string project_id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());

  domain::MemoryOverview overview{.project_id = std::move(project_id)};
  auto user = co_await ReadMemories(*database, domain::MemoryScope::user,
                                    overview.project_id);
  if (!user)
    co_return std::unexpected(user.error());
  overview.long_term = std::move(*user);
  auto project = co_await ReadMemories(*database, domain::MemoryScope::project,
                                       overview.project_id);
  if (!project)
    co_return std::unexpected(project.error());
  overview.project = std::move(*project);
  auto environment = co_await ReadMemories(
      *database, domain::MemoryScope::environment, overview.project_id);
  if (!environment)
    co_return std::unexpected(environment.error());
  overview.environment = std::move(*environment);

  auto working = co_await database->QueryAsync<domain::WorkingMemoryRecord>(
      "SELECT id, project_id, content, source, expires_at, created_at, "
      "updated_at FROM working_memory WHERE "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
      "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) "
      "ORDER BY updated_at DESC LIMIT ?",
      DecodeWorkingMemory, overview.project_id, overview.project_id,
      NowMilliseconds(), kOverviewLimit);
  if (!working)
    co_return std::unexpected(StoreError(working.Error()));
  overview.short_term = std::move(*working);

  auto history = co_await database->QueryAsync<domain::ConversationIndexRecord>(
      "SELECT id, project_id, conversation_id, message_id, role, "
      "substr(text, 1, 1000), title, created_at, updated_at "
      "FROM conversation_index WHERE "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
      "ORDER BY updated_at DESC LIMIT ?",
      DecodeConversationIndex, overview.project_id, overview.project_id,
      kOverviewLimit);
  if (!history)
    co_return std::unexpected(StoreError(history.Error()));
  overview.history = std::move(*history);
  co_return overview;
}

huxerui::Task<MemoryStoreResult<domain::MemoryRecord>>
SqliteMemoryStore::SaveManual(domain::MemoryRecord memory) {
  memory.content = domain::NormalizeMemoryContent(memory.content);
  if (memory.content.empty())
    co_return std::unexpected(MemoryStoreError{"memory content is empty"});
  memory.title = domain::NormalizeMemoryContent(memory.title);
  if (memory.id.empty())
    memory.id = NewMemoryId();
  memory.source = "manual";
  memory.confidence = 1.0;
  const auto now = NowMilliseconds();
  const auto &scope = domain::MemoryScopeDefinition(memory.scope);
  std::optional<std::string> project =
      scope.global ? std::nullopt
                   : std::optional<std::string>{memory.project_id};

  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto saved = co_await database->ExecuteAsync(
      "INSERT INTO memories "
      "(id, scope, project_id, content, source, confidence, created_at, "
      "updated_at, last_used_at, use_count, raw_json, title) "
      "VALUES (?, ?, ?, ?, 'manual', 1, ?, ?, NULL, 0, '', ?) "
      "ON CONFLICT(id) DO UPDATE SET scope = excluded.scope, "
      "project_id = excluded.project_id, content = excluded.content, "
      "updated_at = excluded.updated_at, raw_json = '', "
      "title = CASE WHEN excluded.title = '' THEN memories.title "
      "ELSE excluded.title END",
      memory.id, std::string{scope.storage_name}, project, memory.content, now,
      now, memory.title);
  if (!saved)
    co_return std::unexpected(StoreError(saved.Error()));

  auto rows = co_await database->QueryAsync<domain::MemoryRecord>(
      "SELECT " + std::string{kMemoryColumns} +
          " FROM memories WHERE id = ? LIMIT 1",
      DecodeMemory, memory.id);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::unexpected(MemoryStoreError{"saved memory is missing"});
  co_return std::move(rows->front());
}

huxerui::Task<MemoryStoreResult<void>>
SqliteMemoryStore::Delete(std::vector<std::string> ids) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto deleted = co_await database->TransactionAsync(
      [ids = std::move(ids)](Transaction &transaction) -> Result<void> {
        for (const auto &id : ids) {
          if (id.empty())
            continue;
          auto row =
              transaction.Execute("DELETE FROM memories WHERE id = ?", id);
          if (!row)
            return row.Error();
          auto fts =
              transaction.Execute("DELETE FROM memories_fts WHERE id = ?", id);
          if (!fts && fts.Error().Message().find("no such table") ==
                          std::string::npos) {
            return fts.Error();
          }
        }
        return {};
      });
  if (!deleted)
    co_return std::unexpected(StoreError(deleted.Error()));
  co_return MemoryStoreResult<void>{};
}

huxerui::Task<MemoryStoreResult<application::MemoryRetrievalCorpus>>
SqliteMemoryStore::LoadRetrievalCorpus(std::string project_id,
                                       std::string exclude_conversation_id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());

  application::MemoryRetrievalCorpus corpus;
  auto working = co_await database->QueryAsync<domain::WorkingMemoryRecord>(
      "SELECT id, project_id, content, source, expires_at, created_at, "
      "updated_at FROM working_memory WHERE "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
      "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) "
      "ORDER BY updated_at DESC LIMIT ?",
      DecodeWorkingMemory, project_id, project_id, NowMilliseconds(),
      kScanLimit);
  if (!working)
    co_return std::unexpected(StoreError(working.Error()));
  corpus.working = std::move(*working);

  auto memories = co_await database->QueryAsync<domain::MemoryRecord>(
      "SELECT id, scope, project_id, content, source, confidence, created_at, "
      "updated_at, last_used_at, use_count, title FROM memories WHERE "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '' "
      "OR scope = 'user') ORDER BY updated_at DESC LIMIT ?",
      DecodeMemory, project_id, project_id, kScanLimit);
  if (!memories)
    co_return std::unexpected(StoreError(memories.Error()));
  corpus.memories = std::move(*memories);

  auto history = co_await database->QueryAsync<domain::ConversationIndexRecord>(
      "SELECT id, project_id, conversation_id, message_id, role, "
      "substr(text, 1, 320), title, created_at, updated_at "
      "FROM conversation_index WHERE "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
      "AND (? = '' OR conversation_id != ?) "
      "ORDER BY updated_at DESC LIMIT ?",
      DecodeConversationIndex, project_id, project_id, exclude_conversation_id,
      exclude_conversation_id, kScanLimit);
  if (!history)
    co_return std::unexpected(StoreError(history.Error()));
  corpus.history = std::move(*history);
  if (corpus.history.empty()) {
    auto tables = co_await database->QueryAsync<std::string>(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND "
        "name IN ('conversations', 'messages', 'message_text_chunks')",
        [](const RowView &row) { return row.Get<std::string>(0); });
    if (!tables)
      co_return std::unexpected(StoreError(tables.Error()));
    const auto has_table = [&tables](std::string_view name) {
      return std::ranges::find(*tables, name) != tables->end();
    };
    if (has_table("conversations") && has_table("messages")) {
      const std::string text_expression =
          has_table("message_text_chunks")
              ? "substr(COALESCE(NULLIF((SELECT group_concat(mtc.content, '') "
                "OVER (ORDER BY mtc.chunk_order ROWS BETWEEN UNBOUNDED "
                "PRECEDING AND UNBOUNDED FOLLOWING) FROM message_text_chunks "
                "mtc WHERE mtc.message_id = m.id AND mtc.field_name = "
                "'content' LIMIT 1), ''), m.content, ''), 1, 320)"
              : "substr(COALESCE(m.content, ''), 1, 320)";
      const std::string has_content_expression =
          has_table("message_text_chunks")
              ? "(m.content != '' OR EXISTS (SELECT 1 FROM "
                "message_text_chunks mtc WHERE mtc.message_id = m.id AND "
                "mtc.field_name = 'content' LIMIT 1))"
              : "m.content != ''";
      auto messages =
          co_await database->QueryAsync<domain::ConversationIndexRecord>(
              "SELECT c.id || ':' || m.id, c.project_id, c.id, m.id, m.role, " +
                  text_expression +
                  ", c.title, m.timestamp, c.updated_at "
                  "FROM messages m JOIN conversations c ON c.id = "
                  "m.conversation_id WHERE m.hidden = 0 AND "
                  "m.exclude_from_context = 0 AND m.role IN "
                  "('user', 'assistant') AND " +
                  has_content_expression +
                  " AND "
                  "(? = '' OR c.project_id = ? OR c.project_id IS NULL OR "
                  "c.project_id = '') AND (? = '' OR c.id != ?) "
                  "ORDER BY m.timestamp DESC LIMIT ?",
              DecodeConversationIndex, project_id, project_id,
              exclude_conversation_id, exclude_conversation_id, kScanLimit);
      if (!messages)
        co_return std::unexpected(StoreError(messages.Error()));
      corpus.history = std::move(*messages);
    }
  }
  auto skills = co_await database->QueryAsync<domain::MemorySkillRecord>(
      "SELECT name, path, description, updated_at FROM skills "
      "WHERE enabled = 1 ORDER BY updated_at DESC LIMIT ?",
      DecodeSkill, kScanLimit);
  if (!skills)
    co_return std::unexpected(StoreError(skills.Error()));
  corpus.skills = std::move(*skills);
  co_return corpus;
}

huxerui::Task<MemoryStoreResult<std::vector<domain::MemoryRecord>>>
SqliteMemoryStore::LoadManualMemories(std::string project_id) {
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  auto rows = co_await database->QueryAsync<domain::MemoryRecord>(
      "SELECT id, scope, project_id, content, source, confidence, created_at, "
      "updated_at, last_used_at, use_count, title FROM memories WHERE "
      "source = 'manual' AND "
      "(? = '' OR project_id = ? OR project_id IS NULL OR project_id = '' "
      "OR scope = 'user') ORDER BY updated_at DESC LIMIT ?",
      DecodeMemory, project_id, project_id, kScanLimit);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  co_return std::move(*rows);
}

huxerui::Task<MemoryStoreResult<void>>
SqliteMemoryStore::MarkUsed(std::vector<std::string> ids) {
  std::erase_if(ids, [](const auto &id) { return id.empty(); });
  std::ranges::sort(ids);
  ids.erase(std::unique(ids.begin(), ids.end()), ids.end());
  if (ids.empty())
    co_return MemoryStoreResult<void>{};
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  const auto now = NowMilliseconds();
  auto marked = co_await database->TransactionAsync(
      [ids = std::move(ids), now](Transaction &transaction) -> Result<void> {
        for (const auto &id : ids) {
          auto row =
              transaction.Execute("UPDATE memories SET last_used_at = ?, "
                                  "use_count = use_count + 1 WHERE id = ?",
                                  now, id);
          if (!row)
            return row.Error();
        }
        return {};
      });
  if (!marked)
    co_return std::unexpected(StoreError(marked.Error()));
  co_return MemoryStoreResult<void>{};
}

huxerui::Task<MemoryStoreResult<domain::MemoryRecord>>
SqliteMemoryStore::SaveExtracted(domain::MemoryRecord memory) {
  memory.content = domain::NormalizeMemoryContent(memory.content);
  if (memory.content.empty())
    co_return std::unexpected(MemoryStoreError{"memory content is empty"});
  const auto &scope = domain::MemoryScopeDefinition(memory.scope);
  std::optional<std::string> project =
      scope.global ? std::nullopt
                   : std::optional<std::string>{memory.project_id};
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());

  auto candidates = co_await database->QueryAsync<domain::MemoryRecord>(
      "SELECT id, scope, project_id, content, source, confidence, created_at, "
      "updated_at, last_used_at, use_count, title FROM memories WHERE scope = ? AND "
      "(? = 'user' OR ? = '' OR project_id = ? OR project_id IS NULL OR "
      "project_id = '') ORDER BY updated_at DESC LIMIT ?",
      DecodeMemory, std::string{scope.storage_name},
      std::string{scope.storage_name}, memory.project_id, memory.project_id,
      kOverviewLimit);
  if (!candidates)
    co_return std::unexpected(StoreError(candidates.Error()));

  const auto target = domain::NormalizedMemoryKey(memory.content);
  const auto similar = std::ranges::find_if(*candidates, [&](const auto &row) {
    const auto existing = domain::NormalizedMemoryKey(row.content);
    return !target.empty() && !existing.empty() &&
           (existing == target || existing.contains(target) ||
            target.contains(existing));
  });
  if (similar != candidates->end())
    memory.id = similar->id;
  if (memory.id.empty())
    memory.id = NewMemoryId();
  memory.source = "auto";
  memory.confidence =
      memory.confidence <= 0.0 ? 1.0 : std::clamp(memory.confidence, 0.0, 1.0);
  const auto now = NowMilliseconds();
  auto saved = co_await database->ExecuteAsync(
      "INSERT INTO memories "
      "(id, scope, project_id, content, source, confidence, created_at, "
      "updated_at, last_used_at, use_count, raw_json, title) "
      "VALUES (?, ?, ?, ?, 'auto', ?, ?, ?, NULL, 0, '', ?) "
      "ON CONFLICT(id) DO UPDATE SET scope = excluded.scope, "
      "project_id = excluded.project_id, content = excluded.content, "
      "confidence = MAX(memories.confidence, excluded.confidence), "
      "updated_at = excluded.updated_at, raw_json = '', "
      "title = CASE WHEN excluded.title = '' THEN memories.title "
      "ELSE excluded.title END",
      memory.id, std::string{scope.storage_name}, project, memory.content,
      memory.confidence, now, now, memory.title);
  if (!saved)
    co_return std::unexpected(StoreError(saved.Error()));

  auto rows = co_await database->QueryAsync<domain::MemoryRecord>(
      "SELECT " + std::string{kMemoryColumns} +
          " FROM memories WHERE id = ? LIMIT 1",
      DecodeMemory, memory.id);
  if (!rows)
    co_return std::unexpected(StoreError(rows.Error()));
  if (rows->empty())
    co_return std::unexpected(MemoryStoreError{"saved memory is missing"});
  co_return std::move(rows->front());
}

huxerui::Task<MemoryStoreResult<void>>
SqliteMemoryStore::IndexConversationTurn(domain::MemoryConversationTurn turn) {
  if (turn.conversation_id.empty())
    co_return std::unexpected(
        MemoryStoreError{"conversation id is required for indexing"});
  auto database = co_await Open(state_);
  if (!database)
    co_return std::unexpected(database.error());
  const auto updated_at =
      turn.updated_at > 0 ? turn.updated_at : NowMilliseconds();
  auto indexed = co_await database->TransactionAsync(
      [turn = std::move(turn),
       updated_at](Transaction &transaction) -> Result<void> {
        auto removed = transaction.Execute(
            "DELETE FROM conversation_index WHERE conversation_id = ?",
            turn.conversation_id);
        if (!removed)
          return removed.Error();
        for (const auto &message : turn.messages) {
          if ((message.role != "user" && message.role != "assistant") ||
              message.content.empty()) {
            continue;
          }
          const auto message_id = message.id.empty()
                                      ? std::to_string(message.timestamp)
                                      : message.id;
          const auto id = turn.conversation_id + ":" + message_id;
          const auto timestamp =
              message.timestamp > 0 ? message.timestamp : updated_at;
          auto inserted = transaction.Execute(
              "INSERT INTO conversation_index "
              "(id, project_id, conversation_id, message_id, role, text, "
              "title, created_at, updated_at, raw_json) "
              "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '')",
              id, turn.project_id, turn.conversation_id, message_id,
              message.role, Utf8Prefix(message.content, 4000), turn.title,
              timestamp, updated_at);
          if (!inserted)
            return inserted.Error();
        }
        return {};
      });
  if (!indexed)
    co_return std::unexpected(StoreError(indexed.Error()));
  co_return MemoryStoreResult<void>{};
}

} // namespace linecode::infrastructure
