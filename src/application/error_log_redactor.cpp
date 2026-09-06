#include "application/error_log_redactor.h"

#include <regex>
#include <string>

namespace linecode::application {
namespace {

const std::regex kAuthorization{
    R"((Authorization\s*[:=]\s*)(Bearer\s+)?[^\r\n,}]+)",
    std::regex::ECMAScript | std::regex::icase};
const std::regex kApiKeyHeader{
    R"(((x-api-key|api-key)\s*[:=]\s*)[^\r\n,}]+)",
    std::regex::ECMAScript | std::regex::icase};
const std::regex kJsonSecret{
    R"secret(("(api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|password|secret|private[_-]?key)"\s*:\s*")[^"]*("))secret",
    std::regex::ECMAScript | std::regex::icase};
const std::regex kLongInlineImage{
    R"((data:image/[^;]+;base64,)[A-Za-z0-9+/=]{80,})",
    std::regex::ECMAScript | std::regex::icase};
const std::regex kBase64Json{
    R"b64(("b64_json"\s*:\s*")[^"]{80,}("))b64",
    std::regex::ECMAScript | std::regex::icase};

constexpr std::string_view kTruncatedSuffix =
    "\n... [REDACTED_TRUNCATED]";

} // namespace

std::string RedactErrorLogText(std::string_view value) {
  const bool input_truncated = value.size() > kMaximumSafeErrorLogText;
  std::string redacted{value.substr(0, kMaximumSafeErrorLogText)};
  redacted = std::regex_replace(redacted, kAuthorization,
                                "$1$2[REDACTED]");
  redacted =
      std::regex_replace(redacted, kApiKeyHeader, "$1[REDACTED]");
  redacted = std::regex_replace(redacted, kJsonSecret,
                                "$1[REDACTED]$3");
  redacted = std::regex_replace(redacted, kLongInlineImage,
                                "$1[BASE64_REDACTED]");
  redacted =
      std::regex_replace(redacted, kBase64Json, "$1[BASE64_REDACTED]$2");
  if (input_truncated || redacted.size() > kMaximumSafeErrorLogText) {
    const auto retained = kMaximumSafeErrorLogText - kTruncatedSuffix.size();
    if (redacted.size() > retained) {
      redacted.resize(retained);
    }
    redacted.append(kTruncatedSuffix);
  }
  return redacted;
}

} // namespace linecode::application
