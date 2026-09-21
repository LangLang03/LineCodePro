#include "presentation/markdown_linkify.h"

#include <cstddef>
#include <optional>
#include <string_view>

namespace linecode::presentation {
namespace {

constexpr std::size_t kNotMatched = static_cast<std::size_t>(-1);

bool IsAsciiAlpha(const char value) noexcept {
  return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
}

bool IsAsciiDigit(const char value) noexcept {
  return value >= '0' && value <= '9';
}

bool IsAsciiAlnum(const char value) noexcept {
  return IsAsciiAlpha(value) || IsAsciiDigit(value);
}

/// Characters the legacy `IRI_LABEL`/`PATH_AND_QUERY` groups accept inside a
/// URL. Deliberately ASCII-only: a URL followed by Chinese punctuation such as
/// `，` or `。` must end before that punctuation instead of swallowing it.
bool IsUrlCharacter(const char value) noexcept {
  if (IsAsciiAlnum(value))
    return true;
  constexpr std::string_view extra{"-._~:/?#[]@!$&'()*+,;=%"};
  return extra.find(value) != std::string_view::npos;
}

/// Characters that may appear in the `user:password@` prefix of an authority.
bool IsUserInfoCharacter(const char value) noexcept {
  if (IsAsciiAlnum(value))
    return true;
  constexpr std::string_view extra{"-._~%!$&'()*+,;=:"};
  return extra.find(value) != std::string_view::npos;
}

/// Characters a host label may contain.
bool IsHostCharacter(const char value) noexcept {
  if (IsAsciiAlnum(value))
    return true;
  constexpr std::string_view extra{"-._~%"};
  return extra.find(value) != std::string_view::npos;
}

bool EqualsAsciiCaseInsensitive(const std::string_view value,
                                const std::string_view expected) noexcept {
  if (value.size() < expected.size())
    return false;
  for (std::size_t index = 0; index < expected.size(); ++index) {
    const char left = value[index];
    const char right = expected[index];
    const char lowered = (left >= 'A' && left <= 'Z')
                             ? static_cast<char>(left - 'A' + 'a')
                             : left;
    if (lowered != right)
      return false;
  }
  return true;
}

/// `Linkify.WORD_BOUNDARY` plus `Linkify.sUrlMatchFilter`: a URL may not start
/// inside a word, and a match right after `@` is rejected so e-mail domains
/// stay plain text.
bool StartsAtWordBoundary(const std::string_view text,
                          const std::size_t start) noexcept {
  if (start == 0U)
    return true;
  const char previous = text[start - 1U];
  return !IsAsciiAlnum(previous) && previous != '_' && previous != '@';
}

/// Length of the leading `scheme://`, or zero when the text does not start with
/// one. Any RFC 3986 scheme is accepted; the app's link policy decides which
/// schemes may actually be opened.
std::size_t SchemePrefixLength(const std::string_view text,
                               const std::size_t start) noexcept {
  if (start >= text.size() || !IsAsciiAlpha(text[start]))
    return 0U;
  std::size_t cursor = start + 1U;
  while (cursor < text.size() &&
         (IsAsciiAlnum(text[cursor]) || text[cursor] == '+' ||
          text[cursor] == '-' || text[cursor] == '.'))
    ++cursor;
  if (cursor + 3U > text.size() || text[cursor] != ':' ||
      text[cursor + 1U] != '/' || text[cursor + 2U] != '/')
    return 0U;
  return cursor + 3U - start;
}

/// Strips `user:password@` and the `:port` suffix from a matched authority.
/// Host check for an explicit `scheme://`. The legacy `WEB_URL_WITH_PROTOCOL`
/// branch makes the top-level domain optional, so `http://localhost:8080` is a
/// link; only an empty or label-less authority is rejected.
bool IsPlausibleHost(const std::string_view host) noexcept {
  if (host.empty())
    return false;
  if (host.starts_with('[') && host.ends_with(']'))
    return host.size() > 2U;
  if (host.starts_with('.') || host.ends_with('.'))
    return false;
  for (std::size_t index = 0; index < host.size(); ++index) {
    const char value = host[index];
    if (value == '.') {
      if (index + 1U < host.size() && host[index + 1U] == '.')
        return false;
      continue;
    }
    if (!IsAsciiAlnum(value) && value != '-' && value != '_' && value != '~' &&
        value != '%')
      return false;
  }
  return true;
}

/// Host check for the `www.`-prefixed form, which the legacy pattern only
/// matches through its strict (top-level-domain bearing) branch.
bool IsWwwHost(const std::string_view host) noexcept {
  if (!IsPlausibleHost(host))
    return false;
  const auto dot = host.rfind('.');
  if (dot == std::string_view::npos || dot + 1U >= host.size())
    return false;
  const auto top_level = host.substr(dot + 1U);
  if (top_level.size() < 2U)
    return false;
  for (const char value : top_level) {
    if (!IsAsciiAlpha(value))
      return false;
  }
  return true;
}

/// Drops the sentence punctuation a URL match may have collected, keeping
/// balanced brackets so `https://example.com/a_(b)` stays intact.
std::size_t TrimTrailingPunctuation(const std::string_view text,
                                    const std::size_t start,
                                    const std::size_t end) noexcept {
  std::size_t cursor = end;
  while (cursor > start) {
    const char last = text[cursor - 1U];
    if (last == '.' || last == ',' || last == ';' || last == ':' ||
        last == '!' || last == '?' || last == '\'' || last == '"' ||
        last == '*' || last == '_') {
      --cursor;
      continue;
    }
    if (last == ')' || last == ']' || last == '}') {
      const char open = last == ')' ? '(' : last == ']' ? '[' : '{';
      std::size_t opens = 0U;
      std::size_t closes = 0U;
      for (std::size_t index = start; index < cursor; ++index) {
        if (text[index] == open)
          ++opens;
        else if (text[index] == last)
          ++closes;
      }
      if (closes > opens) {
        --cursor;
        continue;
      }
    }
    break;
  }
  return cursor;
}

/// One matched authority: where it ends and which host it carries.
struct AuthorityMatch final {
  std::size_t end = 0;
  std::string_view host;
};

/// Matches `[user:password@]host[:port]` at `cursor`. Scanning the authority
/// explicitly (instead of accepting every URI character) keeps a following
/// comma or bracket outside the link, which is where the legacy pattern's
/// `DOMAIN_NAME` plus optional `PORT_NUMBER` stopped too.
std::optional<AuthorityMatch> MatchAuthority(const std::string_view text,
                                             std::size_t cursor) {
  for (std::size_t scan = cursor; scan < text.size(); ++scan) {
    if (text[scan] == '@') {
      cursor = scan + 1U;
      break;
    }
    if (!IsUserInfoCharacter(text[scan]))
      break;
  }
  const std::size_t host_start = cursor;
  std::size_t host_end = cursor;
  if (cursor < text.size() && text[cursor] == '[') {
    const auto close = text.find(']', cursor + 1U);
    if (close == std::string_view::npos)
      return std::nullopt;
    host_end = close + 1U;
  } else {
    while (host_end < text.size() && IsHostCharacter(text[host_end]))
      ++host_end;
    // A sentence-ending dot is not an empty host label.
    while (host_end > host_start && text[host_end - 1U] == '.')
      --host_end;
  }
  if (host_end == host_start)
    return std::nullopt;
  auto end = host_end;
  if (end < text.size() && text[end] == ':') {
    std::size_t digits = end + 1U;
    while (digits < text.size() && IsAsciiDigit(text[digits]) &&
           digits - end <= 5U)
      ++digits;
    if (digits > end + 1U)
      end = digits;
  }
  return AuthorityMatch{end, text.substr(host_start, host_end - host_start)};
}

/// Returns the exclusive end of the URL that starts at `start`, or
/// `kNotMatched`.
std::size_t MatchUrl(const std::string_view text, const std::size_t start) {
  const std::size_t scheme = SchemePrefixLength(text, start);
  std::size_t cursor = start;
  bool require_registered_host = false;
  if (scheme != 0U) {
    cursor = start + scheme;
  } else if (EqualsAsciiCaseInsensitive(text.substr(start), "www.")) {
    cursor = start + 4U;
    require_registered_host = true;
  } else {
    return kNotMatched;
  }

  const auto authority = MatchAuthority(text, cursor);
  if (!authority)
    return kNotMatched;
  if (require_registered_host ? !IsWwwHost(authority->host)
                              : !IsPlausibleHost(authority->host))
    return kNotMatched;
  cursor = authority->end;

  if (cursor < text.size() &&
      (text[cursor] == '/' || text[cursor] == '?' || text[cursor] == '#')) {
    while (cursor < text.size() && IsUrlCharacter(text[cursor]))
      ++cursor;
  }
  return TrimTrailingPunctuation(text, start, cursor);
}

/// `Linkify.makeUrl()`: a match without a scheme is completed with the first
/// entry of the legacy prefix table, and a recognized scheme is lower-cased the
/// way `makeUrl()` rewrites a differently-cased prefix.
std::string NormalizeTarget(const std::string_view raw_uri) {
  constexpr std::string_view kSchemes[]{"http://", "https://", "rtsp://",
                                        "ftp://"};
  for (const auto scheme : kSchemes) {
    if (EqualsAsciiCaseInsensitive(raw_uri, scheme))
      return std::string(scheme) +
             std::string(raw_uri.substr(scheme.size()));
  }
  if (SchemePrefixLength(raw_uri, 0U) != 0U)
    return std::string(raw_uri);
  return "http://" + std::string(raw_uri);
}

} // namespace

std::vector<LinkifiedTextRun>
LinkifyWebUrls(const std::string_view text) {
  std::vector<LinkifiedTextRun> runs;
  std::size_t plain_start = 0U;
  std::size_t cursor = 0U;
  while (cursor < text.size()) {
    if (!StartsAtWordBoundary(text, cursor) || !IsUrlCharacter(text[cursor])) {
      ++cursor;
      continue;
    }
    const auto end = MatchUrl(text, cursor);
    if (end == kNotMatched || end <= cursor) {
      ++cursor;
      continue;
    }
    if (cursor > plain_start)
      runs.push_back(LinkifiedTextRun{
          std::string(text.substr(plain_start, cursor - plain_start)),
          std::nullopt});
    const auto raw = text.substr(cursor, end - cursor);
    runs.push_back(LinkifiedTextRun{std::string(raw), NormalizeTarget(raw)});
    cursor = end;
    plain_start = end;
  }
  if (plain_start < text.size())
    runs.push_back(LinkifiedTextRun{std::string(text.substr(plain_start)),
                                    std::nullopt});
  return runs;
}

bool LinkifiesBareUrls(const bool is_code,
                       const bool has_explicit_link) noexcept {
  return !is_code && !has_explicit_link;
}

} // namespace linecode::presentation
