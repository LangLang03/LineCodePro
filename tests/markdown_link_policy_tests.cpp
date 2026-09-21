#include "presentation/markdown_link_policy.h"

#include "gtest_support.h"

TEST(markdown_link_policy_tests, LegacySuite) {
  using linecode::presentation::IsHttpsMarkdownLink;
  using linecode::presentation::ParseNavigableMarkdownLink;

  const auto https = ParseNavigableMarkdownLink(
      "https://example.com/docs?q=cpp23#templates");
  EXPECT_EXPRESSION(https);
  EXPECT_EXPRESSION(https->ToString() ==
         "https://example.com/docs?q=cpp23#templates");
  EXPECT_EXPRESSION(IsHttpsMarkdownLink(*https));

  const auto mixed_case = ParseNavigableMarkdownLink("HtTp://127.0.0.1:8080");
  EXPECT_EXPRESSION(mixed_case);
  EXPECT_EXPRESSION(mixed_case->ToString() == "HtTp://127.0.0.1:8080");
  EXPECT_EXPRESSION(!IsHttpsMarkdownLink(*mixed_case));

  EXPECT_EXPRESSION(!ParseNavigableMarkdownLink("javascript:alert(1)"));
  EXPECT_EXPRESSION(!ParseNavigableMarkdownLink("file:///data/user/0/secret"));
  EXPECT_EXPRESSION(!ParseNavigableMarkdownLink("/relative/path"));
  EXPECT_EXPRESSION(!ParseNavigableMarkdownLink("not a uri"));
}
