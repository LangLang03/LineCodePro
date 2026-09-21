#include "domain/termux_integration.h"

#include <charconv>
#include <string>
#include <system_error>

namespace linecode::domain {
namespace {

constexpr std::string_view kPrivateKeyBegin = "LINEAI_PRIVATE_KEY_BEGIN";
constexpr std::string_view kPrivateKeyEnd = "LINEAI_PRIVATE_KEY_END";
constexpr std::string_view kVerificationBegin = "LINEAI_TERMUX_TEST_BEGIN";
constexpr std::string_view kVerificationEnd = "LINEAI_TERMUX_TEST_END";

std::string Trim(std::string_view value) {
  while (!value.empty() &&
         static_cast<unsigned char>(value.front()) <= 0x20U)
    value.remove_prefix(1);
  while (!value.empty() &&
         static_cast<unsigned char>(value.back()) <= 0x20U)
    value.remove_suffix(1);
  return std::string(value);
}

std::string ReadValue(std::string_view output, std::string_view key) {
  const std::string prefix = std::string(key) + '=';
  std::size_t cursor{};
  while (cursor <= output.size()) {
    const auto end = output.find('\n', cursor);
    auto line = output.substr(cursor, end == std::string_view::npos
                                         ? output.size() - cursor
                                         : end - cursor);
    if (!line.empty() && line.back() == '\r')
      line.remove_suffix(1);
    if (line.starts_with(prefix))
      return Trim(line.substr(prefix.size()));
    if (end == std::string_view::npos)
      break;
    cursor = end + 1;
  }
  return {};
}

std::string ReadBlock(std::string_view output, std::string_view begin,
                      std::string_view end) {
  const auto begin_at = output.find(begin);
  if (begin_at == std::string_view::npos)
    return {};
  auto content_at = begin_at + begin.size();
  if (content_at < output.size() && output[content_at] == '\r')
    ++content_at;
  if (content_at < output.size() && output[content_at] == '\n')
    ++content_at;
  const auto end_at = output.find(end, content_at);
  if (end_at == std::string_view::npos)
    return {};
  return Trim(output.substr(content_at, end_at - content_at));
}

std::int32_t ReadInteger(std::string_view value,
                         std::int32_t fallback) noexcept {
  std::int32_t parsed{};
  const auto [end, error] =
      std::from_chars(value.data(), value.data() + value.size(), parsed);
  return error == std::errc{} && end == value.data() + value.size()
             ? parsed
             : fallback;
}

} // namespace

TermuxResult<TermuxSetupResult>
ParseTermuxSetupOutput(std::string_view output) {
  auto username = ReadValue(output, "LINEAI_TERMUX_USERNAME");
  auto private_key = ReadBlock(output, kPrivateKeyBegin, kPrivateKeyEnd);
  if (username.empty() || private_key.empty()) {
    return std::unexpected(TermuxError{
        .code = TermuxErrorCode::parse_failed,
        .detail = RedactTermuxPrivateKey(output),
    });
  }

  auto host = ReadValue(output, "LINEAI_TERMUX_HOST");
  auto port = ParseSshPort(ReadValue(output, "LINEAI_TERMUX_PORT"));
  return TermuxSetupResult{
      .config = NormalizeSshConfig(SshConfig{
          .host = host.empty() ? std::string{kDefaultSshHost}
                               : std::move(host),
          .port = port,
          .username = std::move(username),
          .password = {},
          .private_key = std::move(private_key),
          .passphrase = {},
      }),
      .shell = ReadValue(output, "LINEAI_TERMUX_SHELL"),
      .rc_path = ReadValue(output, "LINEAI_TERMUX_RC"),
      .verification_output =
          ReadBlock(output, kVerificationBegin, kVerificationEnd),
      .verification_exit_code = ReadInteger(
          ReadValue(output, "LINEAI_TERMUX_TEST_EXIT"), -1),
  };
}

std::string RedactTermuxPrivateKey(std::string_view output,
                                   std::string_view replacement) {
  std::string redacted;
  std::size_t cursor{};
  while (cursor < output.size()) {
    const auto begin_at = output.find(kPrivateKeyBegin, cursor);
    if (begin_at == std::string_view::npos) {
      redacted.append(output.substr(cursor));
      break;
    }
    redacted.append(output.substr(cursor, begin_at - cursor));
    const auto end_at = output.find(kPrivateKeyEnd,
                                    begin_at + kPrivateKeyBegin.size());
    if (end_at == std::string_view::npos) {
      redacted.append(replacement);
      break;
    }
    redacted.append(replacement);
    cursor = end_at + kPrivateKeyEnd.size();
  }
  return redacted;
}

} // namespace linecode::domain
