#include "gtest_support.h"
#include <chrono>
#include <ranges>
#include <string>
#include <vector>

#include "application/memory_prompt_renderer.h"
#include "domain/memory_rag.h"

namespace {

using linecode::domain::ExplicitMemoryExtractionPolicy;
using linecode::domain::MemoryCandidate;
using linecode::domain::MemoryScope;

constexpr std::int64_t kNow = 2'000'000'000'000;

void TokenizerAndRankingMatchLegacySemantics() {
  const auto keywords =
      linecode::domain::ExtractMemoryKeywords("Android SQLite 数据迁移");
  EXPECT_EXPRESSION(!keywords.empty());
  EXPECT_EXPRESSION(std::ranges::find(keywords, "android") != keywords.end());
  EXPECT_EXPRESSION(std::ranges::find(keywords, "sqlite") != keywords.end());

  auto ranked = linecode::domain::RankMemoryCandidates(
      {
          MemoryCandidate{.id = "irrelevant",
                          .search_text = "Rust desktop renderer",
                          .formatted = "irrelevant",
                          .updated_at = kNow},
          MemoryCandidate{.id = "match",
                          .search_text = "Android SQLite migration plan",
                          .formatted = "match",
                          .updated_at = kNow - 86'400'000},
      },
      "SQLite migration", 2, true, 0.0, kNow);
  EXPECT_EXPRESSION(ranked.size() == 1);
  EXPECT_EXPRESSION(ranked.front().id == "match");

  auto recent_fallback = linecode::domain::RankMemoryCandidates(
      {
          MemoryCandidate{.id = "old",
                          .search_text = "alpha",
                          .formatted = "old",
                          .updated_at = 1},
          MemoryCandidate{.id = "new",
                          .search_text = "beta",
                          .formatted = "new",
                          .updated_at = kNow},
      },
      "unmatched", 1, true, 0.0, kNow);
  EXPECT_EXPRESSION(recent_fallback.size() == 1);
  EXPECT_EXPRESSION(recent_fallback.front().id == "new");

  EXPECT_EXPRESSION(linecode::domain::RankMemoryCandidates(
             {MemoryCandidate{.id = "ignored",
                              .search_text = "match",
                              .formatted = "ignored",
                              .updated_at = kNow}},
             "match", 0, true, 0.0, kNow)
             .empty());

  const auto stop_words =
      linecode::domain::ExtractMemoryKeywords("需要进行使用");
  EXPECT_EXPRESSION(std::ranges::find(stop_words, "进行") == stop_words.end());
  EXPECT_EXPRESSION(std::ranges::find(stop_words, "使用") == stop_words.end());

  const auto hiragana = linecode::domain::ExtractMemoryKeywords("あいうえお");
  EXPECT_EXPRESSION(hiragana.empty());
}

void ExplicitExtractionIsScopedAndSensitiveSafe() {
  const ExplicitMemoryExtractionPolicy policy;
  const auto user = policy.Extract("请记住：我偏好中文回答");
  EXPECT_EXPRESSION(user.has_value());
  EXPECT_EXPRESSION(user->scope == MemoryScope::user);
  EXPECT_EXPRESSION(user->content == "我偏好中文回答");

  const auto project = policy.Extract("记住项目: 使用 C++23");
  EXPECT_EXPRESSION(project.has_value());
  EXPECT_EXPRESSION(project->scope == MemoryScope::project);

  const auto environment = policy.Extract("请记住环境：NDK r29");
  EXPECT_EXPRESSION(environment.has_value());
  EXPECT_EXPRESSION(environment->scope == MemoryScope::environment);

  EXPECT_EXPRESSION(!policy.Extract("这只是普通对话，不应自动保存").has_value());
  EXPECT_EXPRESSION(!policy.Extract("记住：password = hunter2").has_value());
  EXPECT_EXPRESSION(!policy.Extract("remember: sk-secret-token").has_value());
}

void ExtractedContentIsUnicodeBounded() {
  const ExplicitMemoryExtractionPolicy policy;
  std::string input = "记住：";
  for (int index = 0; index < 400; ++index)
    input += "好";
  const auto extracted = policy.Extract(input);
  EXPECT_EXPRESSION(extracted.has_value());
  const auto keywords =
      linecode::domain::ExtractMemoryKeywords(extracted->content);
  EXPECT_EXPRESSION(!keywords.empty());
  EXPECT_EXPRESSION(extracted->content.ends_with("。"));
  EXPECT_EXPRESSION(extracted->content.size() <= 320U * 3U);
}

void NormalizedKeySupportsLegacyDeduplication() {
  EXPECT_EXPRESSION(linecode::domain::NormalizedMemoryKey(" Use C++23! ") == "usec23");
  EXPECT_EXPRESSION(linecode::domain::NormalizedMemoryKey("偏好：中文") == "偏好中文");
}

void PromptRenderingKeepsModeBoundariesExplicit() {
  const linecode::application::LegacyMemoryPromptRenderer renderer;
  const std::vector rows{
      MemoryCandidate{.id = "m",
                      .search_text = "preference",
                      .formatted = "- [user/manual] preference",
                      .updated_at = kNow},
  };
  const auto manual = renderer.RenderManual(rows);
  EXPECT_EXPRESSION(manual.contains("Learning Mode is disabled"));
  EXPECT_EXPRESSION(manual.contains("手工保存"));
  EXPECT_EXPRESSION(!manual.contains("短期/工作记忆"));

  const std::vector skills{
      MemoryCandidate{.id = "",
                      .search_text = "android",
                      .formatted = "- android - Android workflow",
                      .updated_at = kNow},
  };
  const auto learning = renderer.RenderLearning({}, rows, {}, skills);
  EXPECT_EXPRESSION(learning.contains("Learning Mode is enabled"));
  EXPECT_EXPRESSION(learning.contains("本地检索 Top-K"));
  EXPECT_EXPRESSION(learning.contains("可用 Skills（RAG Top-K）"));
  EXPECT_EXPRESSION(renderer.RenderLearning({}, {}, {}, {}).empty());
}

} // namespace

TEST(memory_rag_tests, LegacySuite) {
  TokenizerAndRankingMatchLegacySemantics();
  ExplicitExtractionIsScopedAndSensitiveSafe();
  ExtractedContentIsUnicodeBounded();
  NormalizedKeySupportsLegacyDeduplication();
  PromptRenderingKeepsModeBoundariesExplicit();
  return;
}
