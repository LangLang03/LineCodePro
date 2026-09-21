#include "gtest_support.h"
#include <string>
#include <string_view>

#include "infrastructure/archive_redaction.h"

namespace {

void AssertMissing(std::string_view value, std::string_view secret) {
  EXPECT_EXPRESSION(!value.contains(secret));
}

void TestSensitiveNamePolicy() {
  using linecode::infrastructure::IsSensitiveArchiveName;
  EXPECT_EXPRESSION(IsSensitiveArchiveName("Authorization"));
  EXPECT_EXPRESSION(IsSensitiveArchiveName("X-Api-Key"));
  EXPECT_EXPRESSION(IsSensitiveArchiveName("refresh_token"));
  EXPECT_EXPRESSION(IsSensitiveArchiveName("privateKey"));
  EXPECT_EXPRESSION(!IsSensitiveArchiveName("model_id"));
}

void TestRecursiveJsonRedaction() {
  const auto redacted =
      linecode::infrastructure::RedactArchiveJsonSecrets(R"({
        "authorization":"Bearer top-secret",
        "nested":{"token":"nested-secret","safe":"kept"},
        "items":[{"apiKey":"array-secret"}]
      })");
  AssertMissing(redacted, "top-secret");
  AssertMissing(redacted, "nested-secret");
  AssertMissing(redacted, "array-secret");
  EXPECT_EXPRESSION(redacted.contains("kept"));
  EXPECT_EXPRESSION(linecode::infrastructure::RedactArchiveJsonSecrets(
             "unparseable secret payload")
             .empty());
}

void TestHeaderRedaction() {
  const auto redacted = linecode::infrastructure::RedactArchiveHeaders(R"([
    {"name":"Authorization","value":"Bearer header-secret"},
    {"name":"Accept","value":"application/json"},
    {"name":"X-Api-Key","value":"key-secret"}
  ])");
  AssertMissing(redacted, "header-secret");
  AssertMissing(redacted, "key-secret");
  EXPECT_EXPRESSION(redacted.contains("application/json"));
  EXPECT_EXPRESSION(linecode::infrastructure::RedactArchiveHeaders("invalid").empty());
}

void TestSettingRedactionRegistry() {
  const auto ssh = linecode::infrastructure::RedactArchiveSettingValue(
      "@lineai_ssh_config",
      R"({"host":"example.test","password":"ssh-secret","privateKey":"private-secret","passphrase":"phrase-secret"})");
  AssertMissing(ssh, "ssh-secret");
  AssertMissing(ssh, "private-secret");
  AssertMissing(ssh, "phrase-secret");
  EXPECT_EXPRESSION(ssh.contains("example.test"));

  const auto web = linecode::infrastructure::RedactArchiveSettingValue(
      "@lineai_web_search_config",
      R"({"provider":"test","apiKey":"web-secret"})");
  AssertMissing(web, "web-secret");
  EXPECT_EXPRESSION(web.contains("test"));

  EXPECT_EXPRESSION(linecode::infrastructure::RedactArchiveSettingValue(
             "@lineai_access_token", "setting-secret")
             .empty());
  EXPECT_EXPRESSION(linecode::infrastructure::RedactArchiveSettingValue(
             "@lineai_theme", "dark") == "dark");
}

} // namespace

TEST(archive_redaction_tests, LegacySuite) {
  TestSensitiveNamePolicy();
  TestRecursiveJsonRedaction();
  TestHeaderRedaction();
  TestSettingRedactionRegistry();
}
