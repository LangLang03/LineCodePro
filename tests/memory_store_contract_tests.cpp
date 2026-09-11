#include <cassert>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <string_view>

#include <sqlite3.h>

#include "domain/memory.h"
#include "infrastructure/legacy_feature_schema.h"

namespace {

class TestDatabase final {
public:
  TestDatabase() {
    if (sqlite3_open(":memory:", &database_) != SQLITE_OK)
      throw std::runtime_error("sqlite3_open failed");
    Execute(linecode::infrastructure::legacy_feature_schema::create_memories);
    Execute(
        linecode::infrastructure::legacy_feature_schema::create_working_memory);
    Execute(linecode::infrastructure::legacy_feature_schema::
                create_conversation_index);
    Execute(linecode::infrastructure::legacy_feature_schema::create_skills);
    Execute(linecode::infrastructure::legacy_feature_schema::
                create_memories_scope_project_index);
    Execute(linecode::infrastructure::legacy_feature_schema::
                create_working_memory_project_index);
    Execute(linecode::infrastructure::legacy_feature_schema::
                create_conversation_index_project_index);
  }

  ~TestDatabase() { sqlite3_close(database_); }

  TestDatabase(const TestDatabase &) = delete;
  TestDatabase &operator=(const TestDatabase &) = delete;

  void Execute(std::string_view sql) {
    char *error{};
    const std::string owned{sql};
    if (sqlite3_exec(database_, owned.c_str(), nullptr, nullptr, &error) ==
        SQLITE_OK) {
      return;
    }
    const std::string message =
        error == nullptr ? sqlite3_errmsg(database_) : std::string{error};
    sqlite3_free(error);
    throw std::runtime_error(message);
  }

  std::int64_t Scalar(std::string_view sql) const {
    sqlite3_stmt *statement{};
    const std::string owned{sql};
    Check(
        sqlite3_prepare_v2(database_, owned.c_str(), -1, &statement, nullptr));
    if (sqlite3_step(statement) != SQLITE_ROW) {
      sqlite3_finalize(statement);
      throw std::runtime_error("scalar query returned no row");
    }
    const auto value = sqlite3_column_int64(statement, 0);
    sqlite3_finalize(statement);
    return value;
  }

  std::string ScalarText(std::string_view sql) const {
    sqlite3_stmt *statement{};
    const std::string owned{sql};
    Check(
        sqlite3_prepare_v2(database_, owned.c_str(), -1, &statement, nullptr));
    if (sqlite3_step(statement) != SQLITE_ROW) {
      sqlite3_finalize(statement);
      throw std::runtime_error("scalar text query returned no row");
    }
    const auto *value = sqlite3_column_text(statement, 0);
    const std::string result = value == nullptr
                                   ? std::string{}
                                   : reinterpret_cast<const char *>(value);
    sqlite3_finalize(statement);
    return result;
  }

private:
  void Check(int result) const {
    if (result != SQLITE_OK)
      throw std::runtime_error(sqlite3_errmsg(database_));
  }

  sqlite3 *database_{};
};

void DomainContract() {
  using namespace linecode::domain;
  assert(ParseMemoryScope(" user ") == MemoryScope::user);
  assert(ParseMemoryScope("PROJECT") == MemoryScope::project);
  assert(ParseMemoryScope("environment") == MemoryScope::environment);
  assert(ParseMemoryScope("unknown") == MemoryScope::user);
  assert(MemoryScopeDefinition(MemoryScope::user).global);
  assert(!MemoryScopeDefinition(MemoryScope::project).global);
  assert(NormalizeMemoryContent(" \n keep this \t") == "keep this");
  assert(PreviewMemoryText("a\n  b\r\n c", 80) == "a b c");
  assert(PreviewMemoryText("一二三四五六", 5) == "一二...");
}

void LegacyOverviewFilteringAndExpiry() {
  TestDatabase database;
  database.Execute(
      "INSERT INTO memories (id,scope,project_id,content,source,confidence,"
      "created_at,updated_at,use_count,raw_json) VALUES "
      "('u','user',NULL,'user','manual',1,1,10,0,''),"
      "('p','project','/p','project','manual',1,1,20,0,''),"
      "('other','project','/other','other','manual',1,1,30,0,'')");
  assert(database.Scalar("SELECT COUNT(*) FROM memories WHERE scope='user'") ==
         1);
  assert(
      database.Scalar("SELECT COUNT(*) FROM memories WHERE scope='project' AND "
                      "('/p'='' OR project_id='/p' OR project_id IS NULL OR "
                      "project_id='')") == 1);
  assert(
      database.Scalar("SELECT COUNT(*) FROM memories WHERE scope='project' AND "
                      "(''='' OR project_id='' OR project_id IS NULL OR "
                      "project_id='')") == 2);

  database.Execute("INSERT INTO working_memory "
                   "(id,project_id,content,source,expires_at,created_at,"
                   "updated_at,raw_json) "
                   "VALUES ('active','/p','a','system',200,1,2,''),"
                   "('expired','/p','b','system',50,1,3,'')");
  assert(database.Scalar(
             "SELECT COUNT(*) FROM working_memory WHERE project_id='/p' AND "
             "(expires_at IS NULL OR expires_at=0 OR expires_at>100)") == 1);
}

void ManualUpsertPreservesLegacyMetadata() {
  TestDatabase database;
  database.Execute(
      "INSERT INTO memories (id,scope,project_id,content,source,confidence,"
      "created_at,updated_at,last_used_at,use_count,raw_json) VALUES "
      "('same','project','/p','old','auto',0.88,10,20,30,4,'legacy')");
  database.Execute(
      "INSERT INTO memories "
      "(id,scope,project_id,content,source,confidence,created_at,updated_at,"
      "last_used_at,use_count,raw_json) VALUES "
      "('same','user',NULL,'new','manual',1,99,100,NULL,0,'') "
      "ON CONFLICT(id) DO UPDATE SET scope=excluded.scope,"
      "project_id=excluded.project_id,content=excluded.content,"
      "updated_at=excluded.updated_at,raw_json=''");
  assert(database.ScalarText(
             "SELECT scope || ':' || content || ':' || source FROM memories "
             "WHERE id='same'") == "user:new:auto");
  assert(database.Scalar("SELECT created_at FROM memories WHERE id='same'") ==
         10);
  assert(database.Scalar("SELECT last_used_at FROM memories WHERE id='same'") ==
         30);
  assert(database.Scalar("SELECT use_count FROM memories WHERE id='same'") ==
         4);
  assert(database.Scalar(
             "SELECT project_id IS NULL FROM memories WHERE id='same'") == 1);
}

void BatchDeleteIsAtomic() {
  TestDatabase database;
  database.Execute(
      "INSERT INTO memories (id,scope,content,source,created_at,updated_at) "
      "VALUES ('a','user','a','manual',1,1),"
      "('b','user','b','manual',1,1),"
      "('c','user','c','manual',1,1)");
  database.Execute("BEGIN IMMEDIATE");
  database.Execute("DELETE FROM memories WHERE id='a'");
  database.Execute("DELETE FROM memories WHERE id='b'");
  database.Execute("COMMIT");
  assert(database.Scalar("SELECT COUNT(*) FROM memories") == 1);
  assert(database.ScalarText("SELECT id FROM memories") == "c");
}

void SkillRetrievalUsesOnlyEnabledLegacyRows() {
  TestDatabase database;
  database.Execute(
      "INSERT INTO skills "
      "(id,name,scope,path,description,enabled,updated_at,raw_json) VALUES "
      "('on','Android','app','/skills/android','Android workflow',1,20,''),"
      "('off','Hidden','app','/skills/hidden','Disabled',0,30,'')");
  assert(database.Scalar("SELECT COUNT(*) FROM skills WHERE enabled = 1") == 1);
  assert(database.ScalarText("SELECT name FROM skills WHERE enabled = 1 "
                             "ORDER BY updated_at DESC LIMIT 1") == "Android");
}

} // namespace

int main() {
  DomainContract();
  LegacyOverviewFilteringAndExpiry();
  ManualUpsertPreservesLegacyMetadata();
  BatchDeleteIsAtomic();
  SkillRetrievalUsesOnlyEnabledLegacyRows();
  return 0;
}
