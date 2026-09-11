#include <cassert>
#include <string>
#include <string_view>

#include "infrastructure/archive_redaction.h"

namespace {

void AssertMissing(std::string_view value, std::string_view secret) {
  assert(!value.contains(secret));
}

void TestSensitiveNamePolicy() {
  using linecode::infrastructure::IsSensitiveArchiveName;
  assert(IsSensitiveArchiveName("Authorization"));
  assert(IsSensitiveArchiveName("X-Api-Key"));
  assert(IsSensitiveArchiveName("refresh_token"));
  assert(IsSensitiveArchiveName("privateKey"));
  assert(!IsSensitiveArchiveName("model_id"));
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
  assert(redacted.contains("kept"));
  assert(linecode::infrastructure::RedactArchiveJsonSecrets(
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
  assert(redacted.contains("application/json"));
  assert(linecode::infrastructure::RedactArchiveHeaders("invalid").empty());
}

void TestSettingRedactionRegistry() {
  const auto ssh = linecode::infrastructure::RedactArchiveSettingValue(
      "@lineai_ssh_config",
      R"({"host":"example.test","password":"ssh-secret","privateKey":"private-secret","passphrase":"phrase-secret"})");
  AssertMissing(ssh, "ssh-secret");
  AssertMissing(ssh, "private-secret");
  AssertMissing(ssh, "phrase-secret");
  assert(ssh.contains("example.test"));

  const auto web = linecode::infrastructure::RedactArchiveSettingValue(
      "@lineai_web_search_config",
      R"({"provider":"test","apiKey":"web-secret"})");
  AssertMissing(web, "web-secret");
  assert(web.contains("test"));

  assert(linecode::infrastructure::RedactArchiveSettingValue(
             "@lineai_access_token", "setting-secret")
             .empty());
  assert(linecode::infrastructure::RedactArchiveSettingValue(
             "@lineai_theme", "dark") == "dark");
}

} // namespace

int main() {
  TestSensitiveNamePolicy();
  TestRecursiveJsonRedaction();
  TestHeaderRedaction();
  TestSettingRedactionRegistry();
}
