#include "presentation/markdown_link_policy.h"

#include <cassert>

int main() {
  using linecode::presentation::IsHttpsMarkdownLink;
  using linecode::presentation::ParseNavigableMarkdownLink;

  const auto https = ParseNavigableMarkdownLink(
      "https://example.com/docs?q=cpp23#templates");
  assert(https);
  assert(https->ToString() ==
         "https://example.com/docs?q=cpp23#templates");
  assert(IsHttpsMarkdownLink(*https));

  const auto mixed_case = ParseNavigableMarkdownLink("HtTp://127.0.0.1:8080");
  assert(mixed_case);
  assert(mixed_case->ToString() == "HtTp://127.0.0.1:8080");
  assert(!IsHttpsMarkdownLink(*mixed_case));

  assert(!ParseNavigableMarkdownLink("javascript:alert(1)"));
  assert(!ParseNavigableMarkdownLink("file:///data/user/0/secret"));
  assert(!ParseNavigableMarkdownLink("/relative/path"));
  assert(!ParseNavigableMarkdownLink("not a uri"));
}
