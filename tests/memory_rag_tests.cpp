#include <cassert>
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
  assert(!keywords.empty());
  assert(std::ranges::find(keywords, "android") != keywords.end());
  assert(std::ranges::find(keywords, "sqlite") != keywords.end());

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
  assert(ranked.size() == 1);
  assert(ranked.front().id == "match");

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
  assert(recent_fallback.size() == 1);
  assert(recent_fallback.front().id == "new");

  assert(linecode::domain::RankMemoryCandidates(
             {MemoryCandidate{.id = "ignored",
                              .search_text = "match",
                              .formatted = "ignored",
                              .updated_at = kNow}},
             "match", 0, true, 0.0, kNow)
             .empty());

  const auto stop_words =
      linecode::domain::ExtractMemoryKeywords("需要进行使用");
  assert(std::ranges::find(stop_words, "进行") == stop_words.end());
  assert(std::ranges::find(stop_words, "使用") == stop_words.end());

  const auto hiragana = linecode::domain::ExtractMemoryKeywords("あいうえお");
  assert(hiragana.empty());
}

void ExplicitExtractionIsScopedAndSensitiveSafe() {
  const ExplicitMemoryExtractionPolicy policy;
  const auto user = policy.Extract("请记住：我偏好中文回答");
  assert(user.has_value());
  assert(user->scope == MemoryScope::user);
  assert(user->content == "我偏好中文回答");

  const auto project = policy.Extract("记住项目: 使用 C++23");
  assert(project.has_value());
  assert(project->scope == MemoryScope::project);

  const auto environment = policy.Extract("请记住环境：NDK r29");
  assert(environment.has_value());
  assert(environment->scope == MemoryScope::environment);

  assert(!policy.Extract("这只是普通对话，不应自动保存").has_value());
  assert(!policy.Extract("记住：password = hunter2").has_value());
  assert(!policy.Extract("remember: sk-secret-token").has_value());
}

void ExtractedContentIsUnicodeBounded() {
  const ExplicitMemoryExtractionPolicy policy;
  std::string input = "记住：";
  for (int index = 0; index < 400; ++index)
    input += "好";
  const auto extracted = policy.Extract(input);
  assert(extracted.has_value());
  const auto keywords =
      linecode::domain::ExtractMemoryKeywords(extracted->content);
  assert(!keywords.empty());
  assert(extracted->content.ends_with("。"));
  assert(extracted->content.size() <= 320U * 3U);
}

void NormalizedKeySupportsLegacyDeduplication() {
  assert(linecode::domain::NormalizedMemoryKey(" Use C++23! ") == "usec23");
  assert(linecode::domain::NormalizedMemoryKey("偏好：中文") == "偏好中文");
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
  assert(manual.contains("Learning Mode is disabled"));
  assert(manual.contains("手工保存"));
  assert(!manual.contains("短期/工作记忆"));

  const std::vector skills{
      MemoryCandidate{.id = "",
                      .search_text = "android",
                      .formatted = "- android - Android workflow",
                      .updated_at = kNow},
  };
  const auto learning = renderer.RenderLearning({}, rows, {}, skills);
  assert(learning.contains("Learning Mode is enabled"));
  assert(learning.contains("本地检索 Top-K"));
  assert(learning.contains("可用 Skills（RAG Top-K）"));
  assert(renderer.RenderLearning({}, {}, {}, {}).empty());
}

} // namespace

int main() {
  TokenizerAndRankingMatchLegacySemantics();
  ExplicitExtractionIsScopedAndSensitiveSafe();
  ExtractedContentIsUnicodeBounded();
  NormalizedKeySupportsLegacyDeduplication();
  PromptRenderingKeepsModeBoundariesExplicit();
  return 0;
}
