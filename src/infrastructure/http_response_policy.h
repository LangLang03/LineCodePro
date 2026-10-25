#pragma once

#include <charconv>
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <system_error>

#include <huxerui/http.h>

namespace linecode::infrastructure {

[[nodiscard]] inline std::string_view TrimHttpValue(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos)
    return {};
  const auto last = value.find_last_not_of(" \t\r\n");
  return value.substr(first, last - first + 1U);
}

// `Retry-After` is either delay-seconds or an HTTP-date. Only the numeric form is
// honoured, because a date would need a clock the retry policy does not own.
// Values outside the accepted range are ignored rather than clamped here; the
// policy caps the delay it actually sleeps.
[[nodiscard]] inline std::optional<std::int64_t>
ParseRetryAfterSeconds(std::span<const huxerui::HttpHeader> headers) {
  for (const auto &header : headers) {
    if (header.name != "Retry-After" && header.name != "retry-after")
      continue;
    const auto value = TrimHttpValue(header.value);
    if (value.empty())
      return std::nullopt;
    std::int64_t seconds{};
    const auto [end, error] =
        std::from_chars(value.data(), value.data() + value.size(), seconds);
    if (error != std::errc{} || end != value.data() + value.size())
      return std::nullopt;
    if (seconds < 0)
      return std::nullopt;
    return seconds;
  }
  return std::nullopt;
}

// A server-sent-events frame whose payload is empty (or only whitespace) is a
// keep-alive heartbeat, not a malformed chunk. Providers that emit them used to
// fail the whole turn and then burn the retry budget on the same heartbeat.
[[nodiscard]] inline bool
IsIgnorableSsePayload(std::string_view payload) noexcept {
  return TrimHttpValue(payload).empty();
}

// Lowercased authority of an absolute http(s) URL, or empty when it cannot be
// parsed. Used to notice that a redirect moved an API-keyed request to another
// host, which would hand the credential to whoever controls that host.
[[nodiscard]] inline std::string HostOfUrl(std::string_view url) {
  const auto scheme_end = url.find("://");
  if (scheme_end == std::string_view::npos)
    return {};
  const auto authority_start = scheme_end + 3U;
  const auto authority_end = url.find_first_of("/?#", authority_start);
  auto authority = url.substr(authority_start,
                              authority_end == std::string_view::npos
                                  ? std::string_view::npos
                                  : authority_end - authority_start);
  if (authority.empty())
    return {};
  // Drop userinfo, keeping the line below simple: credentials in a URL are not
  // a supported configuration, and comparing the rest is still meaningful.
  if (const auto at = authority.rfind('@'); at != std::string_view::npos)
    authority = authority.substr(at + 1U);
  std::string host;
  if (authority.starts_with('[')) {
    const auto closing = authority.find(']');
    if (closing == std::string_view::npos)
      return {};
    host = std::string{authority.substr(0U, closing + 1U)};
  } else {
    const auto colon = authority.rfind(':');
    host = std::string{colon == std::string_view::npos
                           ? authority
                           : authority.substr(0U, colon)};
  }
  for (auto &character : host) {
    if (character >= 'A' && character <= 'Z')
      character = static_cast<char>(character - 'A' + 'a');
  }
  return host;
}

} // namespace linecode::infrastructure
