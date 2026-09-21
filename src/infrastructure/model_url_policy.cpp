#include "infrastructure/model_url_policy.h"

#include <algorithm>
#include <array>
#include <charconv>
#include <cctype>
#include <cstdint>
#include <optional>
#include <ranges>
#include <system_error>

namespace linecode::infrastructure {
namespace {

std::string_view Trim(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos) {
    return {};
  }
  const auto last = value.find_last_not_of(" \t\r\n");
  return value.substr(first, last - first + 1U);
}

std::string Lower(std::string_view value) {
  std::string result(value);
  std::ranges::transform(result, result.begin(), [](const unsigned char value) {
    return static_cast<char>(std::tolower(value));
  });
  return result;
}

bool IsPrivateIpv4(std::string_view host) {
  std::array<unsigned, 4> octets{};
  std::size_t start = 0U;
  for (std::size_t index = 0U; index < octets.size(); ++index) {
    const auto separator = host.find('.', start);
    const auto end = separator == std::string_view::npos ? host.size()
                                                         : separator;
    if (end == start || end - start > 3U ||
        (index < 3U) != (separator != std::string_view::npos)) {
      return false;
    }
    unsigned value{};
    const auto [parsed, error] =
        std::from_chars(host.data() + start, host.data() + end, value);
    if (error != std::errc{} || parsed != host.data() + end || value > 255U) {
      return false;
    }
    octets[index] = value;
    start = end + 1U;
  }
  return octets[0] == 10U || octets[0] == 127U ||
         (octets[0] == 172U && octets[1] >= 16U && octets[1] <= 31U) ||
         (octets[0] == 192U && octets[1] == 168U);
}

std::optional<std::string> ParseHost(std::string_view authority) {
  if (authority.empty() || authority.find('@') != std::string_view::npos) {
    return std::nullopt;
  }
  if (authority.starts_with('[')) {
    const auto closing = authority.find(']');
    if (closing == std::string_view::npos) {
      return std::nullopt;
    }
    const auto suffix = authority.substr(closing + 1U);
    if (!suffix.empty() &&
        (!suffix.starts_with(':') ||
         suffix.substr(1U).find_first_not_of("0123456789") !=
             std::string_view::npos)) {
      return std::nullopt;
    }
    return Lower(authority.substr(1U, closing - 1U));
  }
  const auto colon = authority.rfind(':');
  auto host = authority;
  if (colon != std::string_view::npos) {
    const auto port = authority.substr(colon + 1U);
    if (port.empty() ||
        port.find_first_not_of("0123456789") != std::string_view::npos) {
      return std::nullopt;
    }
    host = authority.substr(0U, colon);
  }
  if (host.empty() || host.find(':') != std::string_view::npos) {
    return std::nullopt;
  }
  return Lower(host);
}

} // namespace

std::expected<std::string, ModelUrlValidationError>
ValidateModelBaseUrl(const std::string_view value) {
  const auto trimmed = Trim(value);
  const auto scheme_end = trimmed.find("://");
  if (scheme_end == std::string_view::npos) {
    return std::unexpected(ModelUrlValidationError{
        .code = ModelUrlError::invalid,
        .message = "Model API URL must start with http:// or https://"});
  }
  const auto scheme = Lower(trimmed.substr(0U, scheme_end));
  if (scheme != "http" && scheme != "https") {
    return std::unexpected(ModelUrlValidationError{
        .code = ModelUrlError::invalid,
        .message = "Model API URL must start with http:// or https://"});
  }
  const auto authority_start = scheme_end + 3U;
  const auto authority_end = trimmed.find_first_of("/?#", authority_start);
  const auto authority = trimmed.substr(
      authority_start,
      authority_end == std::string_view::npos
          ? std::string_view::npos
          : authority_end - authority_start);
  const auto host = ParseHost(authority);
  if (!host.has_value()) {
    return std::unexpected(ModelUrlValidationError{
        .code = ModelUrlError::invalid,
        .message = "Model API URL has an invalid host"});
  }
  if (scheme == "http" && *host != "localhost" && *host != "::1" &&
      !IsPrivateIpv4(*host)) {
    return std::unexpected(ModelUrlValidationError{
        .code = ModelUrlError::cleartext_not_allowed,
        .message = "HTTP model URLs are limited to localhost and private IP addresses"});
  }
  return std::string{trimmed};
}

} // namespace linecode::infrastructure
