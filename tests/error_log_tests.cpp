#include <cassert>
#include <string>

#include "application/error_log_policy.h"
#include "application/error_log_redactor.h"

namespace {

void TestEntryIdentityPolicy() {
  using linecode::application::IsValidErrorLogEntryId;
  using linecode::application::SafeTemporaryErrorLogFileName;
  assert(IsValidErrorLogEntryId("20260906-http-123.log"));
  assert(IsValidErrorLogEntryId("a.log"));
  assert(!IsValidErrorLogEntryId(".log"));
  assert(!IsValidErrorLogEntryId("error.txt"));
  assert(!IsValidErrorLogEntryId("../error.log"));
  assert(!IsValidErrorLogEntryId("nested/error.log"));
  assert(!IsValidErrorLogEntryId("nested\\error.log"));
  assert(!IsValidErrorLogEntryId(std::string("bad\0.log", 8)));
  assert(!IsValidErrorLogEntryId(std::string(256, 'a') + ".log"));
  assert(SafeTemporaryErrorLogFileName("20260906-http.log") ==
         "20260906-http.log");
  assert(SafeTemporaryErrorLogFileName("../../token: secret") ==
         "_.._token__secret.log");
  assert(SafeTemporaryErrorLogFileName("日志") == "______.log");
  assert(SafeTemporaryErrorLogFileName("") == "linecode-error.log");
  assert(SafeTemporaryErrorLogFileName(std::string(200, 'a')).size() == 100U);
  const std::string nul_title("line\0code", 9);
  for (const std::string &title :
       {std::string("../secret"), std::string("a/b"), std::string("a\\b"),
        std::string(".hidden"), nul_title}) {
    const std::string safe = SafeTemporaryErrorLogFileName(title);
    assert(IsValidErrorLogEntryId(safe));
    assert(safe.find('/') == std::string::npos);
    assert(safe.find('\\') == std::string::npos);
  }
}

void TestSecretRedaction() {
  using linecode::application::RedactErrorLogText;
  const std::string input =
      "Authorization: Bearer secret-token\n"
      "x-api-key = key-value\n"
      "api-key: second-key\n"
      R"({"password":"hello","access_token":"token","ok":"visible"})";
  const std::string redacted = RedactErrorLogText(input);
  assert(redacted.find("secret-token") == std::string::npos);
  assert(redacted.find("key-value") == std::string::npos);
  assert(redacted.find("second-key") == std::string::npos);
  assert(redacted.find("hello") == std::string::npos);
  assert(redacted.find("\"access_token\":\"token\"") == std::string::npos);
  assert(redacted.find("visible") != std::string::npos);
  assert(redacted.find("Bearer [REDACTED]") != std::string::npos);
}

void TestBase64AndSizeBounds() {
  using linecode::application::kMaximumSafeErrorLogText;
  using linecode::application::RedactErrorLogText;
  const std::string payload(96, 'A');
  const std::string inline_image =
      "data:image/png;base64," + payload;
  const std::string json = "{\"b64_json\":\"" + payload + "\"}";
  assert(RedactErrorLogText(inline_image).find("[BASE64_REDACTED]") !=
         std::string::npos);
  assert(RedactErrorLogText(json).find("[BASE64_REDACTED]") !=
         std::string::npos);

  const std::string oversized(kMaximumSafeErrorLogText + 16U, 'x');
  const std::string bounded = RedactErrorLogText(oversized);
  assert(bounded.size() < oversized.size());
  assert(bounded.ends_with("... [REDACTED_TRUNCATED]"));
}

} // namespace

int main() {
  TestEntryIdentityPolicy();
  TestSecretRedaction();
  TestBase64AndSizeBounds();
  return 0;
}
