#pragma once

#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::presentation {

/// One run of text produced by splitting a plain-text fragment on bare web
/// URLs, mirroring what the legacy
/// `Linkify.addLinks(builder, Linkify.WEB_URLS)` call turned into links.
struct LinkifiedTextRun final {
  /// Displayed text; for a URL run this is the matched source text, exactly
  /// like the `URLSpan` range Linkify created.
  std::string text;
  /// Navigation target for a URL run, or `std::nullopt` for plain text. The
  /// value follows `Linkify.makeUrl()`: a match without a scheme is completed
  /// with `http://`, which is the legacy prefix table's first entry.
  std::optional<std::string> url;

  bool operator==(const LinkifiedTextRun&) const = default;
};

/// Splits one plain-text fragment into text and bare-URL runs.
///
/// Recognized forms are `scheme://…` for any RFC 3986 scheme (the legacy
/// pattern's `PROTOCOL` group covers `http|https|rtsp|ftp`), `http://` and
/// `https://` targets, and `www.`-prefixed hosts. A URL only starts at a word
/// boundary and is never split off after an `@`, reproducing
/// `Linkify.sUrlMatchFilter`. Trailing sentence punctuation is not part of the
/// link.
[[nodiscard]] std::vector<LinkifiedTextRun>
LinkifyWebUrls(std::string_view text);

/// Whether a parsed inline part takes part in bare-URL recognition.
///
/// Inline code stays literal so a code sample never turns into a link, and a
/// part that already carries an explicit `[text](url)` destination keeps that
/// single link instead of being split again.
[[nodiscard]] bool LinkifiesBareUrls(bool is_code,
                                     bool has_explicit_link) noexcept;

} // namespace linecode::presentation
