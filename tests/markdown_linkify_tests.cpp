// Tests for the `Linkify.WEB_URLS` equivalent that turns bare URLs in Markdown
// body text and table cells into links, plus the policy that decides which
// inline parts participate.
#include "gtest_support.h"
#include <cstddef>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "presentation/markdown_linkify.h"

namespace {

using linecode::presentation::LinkifiedTextRun;
using linecode::presentation::LinkifiesBareUrls;
using linecode::presentation::LinkifyWebUrls;

std::vector<LinkifiedTextRun> Runs(std::string_view text) {
  return LinkifyWebUrls(text);
}

/// Asserts that the runs reassemble the input and that exactly one of them is a
/// link whose displayed text and target match.
void AssertUrl(std::string_view text, std::string_view displayed,
               std::string_view resource) {
  const auto runs = Runs(text);
  std::string joined;
  std::size_t links = 0;
  for (const auto& run : runs) {
    joined += run.text;
    if (!run.url)
      continue;
    ++links;
    EXPECT_EXPRESSION(run.text == displayed);
    EXPECT_EXPRESSION(*run.url == resource);
  }
  EXPECT_EXPRESSION(joined == text);
  EXPECT_EXPRESSION(links == 1);
}

/// Asserts that a text is never split into links.
void AssertPlain(std::string_view text) {
  const auto runs = Runs(text);
  EXPECT_EXPRESSION(runs.size() == 1);
  EXPECT_EXPRESSION(!runs.front().url.has_value());
  EXPECT_EXPRESSION(runs.front().text == text);
}

} // namespace

TEST(markdown_linkify_tests, LegacySuite) {
  // `http://` and `https://` are recognized as-is
  // (`Linkify.java:313-316` gathers `Patterns.AUTOLINK_WEB_URL`).
  AssertUrl("http://example.com", "http://example.com", "http://example.com");
  AssertUrl("https://example.com/a/b?c=1#d", "https://example.com/a/b?c=1#d",
            "https://example.com/a/b?c=1#d");
  AssertUrl("https://example.com:8443/x", "https://example.com:8443/x",
            "https://example.com:8443/x");
  // The legacy `PROTOCOL` group is `(?i:http|https|rtsp|ftp)://`, so those
  // schemes keep their own spelling (lower-cased the way `makeUrl()` rewrites a
  // differently-cased prefix).
  AssertUrl("ftp://files.example.com/pub", "ftp://files.example.com/pub",
            "ftp://files.example.com/pub");
  AssertUrl("RTSP://example.com/stream", "RTSP://example.com/stream",
            "rtsp://example.com/stream");
  // Any other RFC 3986 scheme form is recognized too and stays link-styled;
  // whether it may be opened is decided by `ParseNavigableMarkdownLink`.
  AssertUrl("git://example.com/repo", "git://example.com/repo",
            "git://example.com/repo");
  // An explicit `http://localhost` has no top-level domain; the legacy
  // `WEB_URL_WITH_PROTOCOL` branch still accepts it.
  AssertUrl("http://localhost:8080/health", "http://localhost:8080/health",
            "http://localhost:8080/health");

  // A `www.` host has no scheme, so `Linkify.makeUrl()` completes it with the
  // first legacy prefix, `http://`.
  AssertUrl("www.example.com", "www.example.com", "http://www.example.com");
  AssertUrl("www.example.com:8080/path", "www.example.com:8080/path",
            "http://www.example.com:8080/path");
  AssertUrl("WWW.Example.COM", "WWW.Example.COM", "http://WWW.Example.COM");

  // Sentences keep their punctuation; only the URL is linked.
  const auto sentence = Runs("see https://example.com now");
  EXPECT_EXPRESSION(sentence.size() == 3);
  EXPECT_EXPRESSION(sentence[0].text == "see ");
  EXPECT_EXPRESSION(!sentence[0].url.has_value());
  EXPECT_EXPRESSION(sentence[1].text == "https://example.com");
  EXPECT_EXPRESSION(sentence[1].url == std::optional<std::string>("https://example.com"));
  EXPECT_EXPRESSION(sentence[2].text == " now");
  EXPECT_EXPRESSION(!sentence[2].url.has_value());

  // Trailing sentence punctuation is trimmed, balanced brackets are kept.
  AssertUrl("https://example.com.", "https://example.com",
            "https://example.com");
  AssertUrl("https://example.com,", "https://example.com",
            "https://example.com");
  AssertUrl("(https://example.com)", "https://example.com",
            "https://example.com");
  AssertUrl("https://example.com/a_(b)", "https://example.com/a_(b)",
            "https://example.com/a_(b)");
  // A Chinese full stop or comma is never part of the URL.
  AssertUrl("https://example.com。", "https://example.com",
            "https://example.com");
  const auto chinese = Runs("见 https://example.com，谢谢");
  EXPECT_EXPRESSION(chinese.size() == 3);
  EXPECT_EXPRESSION(chinese[1].text == "https://example.com");
  EXPECT_EXPRESSION(chinese[2].text == "，谢谢");
  // Chinese text glued to the URL still starts a link, because the boundary
  // only rejects ASCII word characters.
  AssertUrl("见https://example.com", "https://example.com",
            "https://example.com");

  // Several URLs in one fragment stay separate runs.
  const auto many = Runs("https://a.example and www.b.example");
  EXPECT_EXPRESSION(many.size() == 3);
  EXPECT_EXPRESSION(many[0].url == std::optional<std::string>("https://a.example"));
  EXPECT_EXPRESSION(many[1].text == " and ");
  EXPECT_EXPRESSION(!many[1].url.has_value());
  EXPECT_EXPRESSION(many[2].url == std::optional<std::string>("http://www.b.example"));

  // Non-matches stay plain text: an e-mail address (`Linkify.sUrlMatchFilter`
  // rejects a match right after `@`), a bare domain without a scheme or `www.`
  // prefix, and incomplete forms.
  AssertPlain("user@example.com");
  AssertPlain("user@www.example.com");
  AssertPlain("example.com");
  AssertPlain("see README.md and build.gradle");
  AssertPlain("https://");
  AssertPlain("www.");
  AssertPlain("plain summary without any link");
  // Documented divergence: text glued to `scheme://` is read as that longer
  // scheme instead of being rejected like the legacy `\b` word boundary would.
  AssertUrl("xhttps://example.com", "xhttps://example.com",
            "xhttps://example.com");

  // The inline-part policy: inline code and explicit links are excluded from
  // bare-URL recognition so code samples and existing destinations survive.
  EXPECT_EXPRESSION(!LinkifiesBareUrls(true, false));
  EXPECT_EXPRESSION(!LinkifiesBareUrls(false, true));
  EXPECT_EXPRESSION(!LinkifiesBareUrls(true, true));
  EXPECT_EXPRESSION(LinkifiesBareUrls(false, false));
}
