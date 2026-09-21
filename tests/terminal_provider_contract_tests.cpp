#include "gtest_support.h"
#include <stdexcept>
#include <string>

#include <sqlite3.h>

#include "domain/terminal_provider.h"
#include "infrastructure/legacy_feature_schema.h"

namespace {

void Check(int code, sqlite3 *database) {
  if (code != SQLITE_OK)
    throw std::runtime_error(sqlite3_errmsg(database));
}

void Execute(sqlite3 *database, const std::string &sql) {
  char *error{};
  if (sqlite3_exec(database, sql.c_str(), nullptr, nullptr, &error) ==
      SQLITE_OK)
    return;
  const std::string message = error == nullptr ? sqlite3_errmsg(database)
                                                : std::string{error};
  sqlite3_free(error);
  throw std::runtime_error(message);
}

long long Scalar(sqlite3 *database, const std::string &sql) {
  sqlite3_stmt *statement{};
  Check(sqlite3_prepare_v2(database, sql.c_str(), -1, &statement, nullptr),
        database);
  EXPECT_EXPRESSION(sqlite3_step(statement) == SQLITE_ROW);
  const auto value = sqlite3_column_int64(statement, 0);
  sqlite3_finalize(statement);
  return value;
}

void VerifyLegacyCompatibleSchemaAndCrud() {
  sqlite3 *database{};
  Check(sqlite3_open(":memory:", &database), database);
  Execute(database,
          std::string{linecode::infrastructure::legacy_feature_schema::
                          create_ipc_providers});
  Execute(database,
          std::string{linecode::infrastructure::legacy_feature_schema::
                          create_ipc_providers_enabled_index});
  Execute(database,
          "INSERT INTO ipc_providers "
          "(id,enabled,provider_type,name,package_name,service_class,"
          "created_at,updated_at,raw_json) VALUES "
          "('ipc_a',1,'terminal','A','dev.a','.Service',1,2,'')");
  EXPECT_EXPRESSION(Scalar(database,
                "SELECT COUNT(*) FROM ipc_providers WHERE provider_type='terminal'") ==
         1);
  Execute(database,
          "UPDATE ipc_providers SET enabled=0,updated_at=3 WHERE id='ipc_a'");
  EXPECT_EXPRESSION(Scalar(database,
                "SELECT enabled FROM ipc_providers WHERE id='ipc_a'") == 0);
  Execute(database, "DELETE FROM ipc_providers WHERE id='ipc_a'");
  EXPECT_EXPRESSION(Scalar(database, "SELECT COUNT(*) FROM ipc_providers") == 0);
  sqlite3_close(database);
}

void VerifyStrongDomainTypes() {
  using namespace linecode::domain;
  const TerminalProviderConfig config{
      .id = "ipc_a",
      .enabled = true,
      .provider_type = kTerminalProviderType,
      .name = "Provider",
      .package_name = "dev.provider",
      .service_class = "dev.provider.TerminalService",
  };
  EXPECT_EXPRESSION(config.provider_type == "terminal");
  EXPECT_EXPRESSION(config.package_name == "dev.provider");
  EXPECT_EXPRESSION(ScannedTerminalProvider{.package_name = config.package_name,
                                 .service_class = config.service_class,
                                 .label = config.name} ==
         ScannedTerminalProvider{.package_name = "dev.provider",
                                 .service_class =
                                     "dev.provider.TerminalService",
                                 .label = "Provider"});
}

} // namespace

TEST(terminal_provider_contract_tests, LegacySuite) {
  VerifyLegacyCompatibleSchemaAndCrud();
  VerifyStrongDomainTypes();
}
