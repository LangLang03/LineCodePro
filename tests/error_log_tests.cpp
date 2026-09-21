#include "gtest_support.h"
#include <string>

#include "application/error_log_policy.h"
#include "application/error_log_redactor.h"

namespace {

void TestEntryIdentityPolicy() {
  using linecode::application::IsValidErrorLogEntryId;
  using linecode::application::SafeTemporaryErrorLogFileName;
  EXPECT_EXPRESSION(IsValidErrorLogEntryId("20260906-http-123.log"));
  EXPECT_EXPRESSION(IsValidErrorLogEntryId("a.log"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId(".log"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId("error.txt"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId("../error.log"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId("nested/error.log"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId("nested\\error.log"));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId(std::string("bad\0.log", 8)));
  EXPECT_EXPRESSION(!IsValidErrorLogEntryId(std::string(256, 'a') + ".log"));
  EXPECT_EXPRESSION(SafeTemporaryErrorLogFileName("20260906-http.log") ==
         "20260906-http.log");
  EXPECT_EXPRESSION(SafeTemporaryErrorLogFileName("../../token: secret") ==
         "_.._token__secret.log");
  EXPECT_EXPRESSION(SafeTemporaryErrorLogFileName("日志") == "______.log");
  EXPECT_EXPRESSION(SafeTemporaryErrorLogFileName("") == "linecode-error.log");
  EXPECT_EXPRESSION(SafeTemporaryErrorLogFileName(std::string(200, 'a')).size() == 100U);
  const std::string nul_title("line\0code", 9);
  for (const std::string &title :
       {std::string("../secret"), std::string("a/b"), std::string("a\\b"),
        std::string(".hidden"), nul_title}) {
    const std::string safe = SafeTemporaryErrorLogFileName(title);
    EXPECT_EXPRESSION(IsValidErrorLogEntryId(safe));
    EXPECT_EXPRESSION(safe.find('/') == std::string::npos);
    EXPECT_EXPRESSION(safe.find('\\') == std::string::npos);
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
  EXPECT_EXPRESSION(redacted.find("secret-token") == std::string::npos);
  EXPECT_EXPRESSION(redacted.find("key-value") == std::string::npos);
  EXPECT_EXPRESSION(redacted.find("second-key") == std::string::npos);
  EXPECT_EXPRESSION(redacted.find("hello") == std::string::npos);
  EXPECT_EXPRESSION(redacted.find("\"access_token\":\"token\"") == std::string::npos);
  EXPECT_EXPRESSION(redacted.find("visible") != std::string::npos);
  EXPECT_EXPRESSION(redacted.find("Bearer [REDACTED]") != std::string::npos);
}

void TestBase64AndSizeBounds() {
  using linecode::application::kMaximumSafeErrorLogText;
  using linecode::application::RedactErrorLogText;
  const std::string payload(96, 'A');
  const std::string inline_image =
      "data:image/png;base64," + payload;
  const std::string json = "{\"b64_json\":\"" + payload + "\"}";
  EXPECT_EXPRESSION(RedactErrorLogText(inline_image).find("[BASE64_REDACTED]") !=
         std::string::npos);
  EXPECT_EXPRESSION(RedactErrorLogText(json).find("[BASE64_REDACTED]") !=
         std::string::npos);

  const std::string oversized(kMaximumSafeErrorLogText + 16U, 'x');
  const std::string bounded = RedactErrorLogText(oversized);
  EXPECT_EXPRESSION(bounded.size() < oversized.size());
  EXPECT_EXPRESSION(bounded.ends_with("... [REDACTED_TRUNCATED]"));
}

} // namespace

TEST(error_log_tests, LegacySuite) {
  TestEntryIdentityPolicy();
  TestSecretRedaction();
  TestBase64AndSizeBounds();
  return;
}
