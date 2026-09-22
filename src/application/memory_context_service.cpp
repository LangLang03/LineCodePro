#include "application/memory_context_service.h"

#include <algorithm>
#include <chrono>
#include <iomanip>
#include <ranges>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

constexpr std::size_t kWorkingLimit = 5;
constexpr std::size_t kMemoryLimit = 6;
constexpr std::size_t kHistoryLimit = 6;
constexpr std::size_t kSkillLimit = 8;
constexpr double kWorkingBoost = 0.30;

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

std::string ScopeLabel(const domain::MemoryRecord &memory) {
  std::string result{domain::MemoryScopeDefinition(memory.scope).storage_name};
  if (!memory.project_id.empty()) {
    result.push_back(':');
    result += memory.project_id;
  }
  return result;
}

std::string HistoryTitle(const domain::ConversationIndexRecord &history) {
  const auto &value =
      history.title.empty() ? history.conversation_id : history.title;
  return value.empty() ? "相关对话" : "「" + value + "」";
}

std::vector<domain::MemoryCandidate>
WorkingCandidates(std::span<const domain::WorkingMemoryRecord> records) {
  std::vector<domain::MemoryCandidate> result;
  result.reserve(records.size());
  std::ranges::transform(
      records, std::back_inserter(result), [](const auto &row) {
        return domain::MemoryCandidate{
            .id = {},
            .search_text = row.content + " " + row.source,
            .formatted = "- [" + row.source + "] " +
                         domain::PreviewMemoryText(row.content, 420),
            .updated_at = row.updated_at,
        };
      });
  return result;
}

std::vector<domain::MemoryCandidate>
MemoryCandidates(std::span<const domain::MemoryRecord> records) {
  std::vector<domain::MemoryCandidate> result;
  result.reserve(records.size());
  std::ranges::transform(
      records, std::back_inserter(result), [](const auto &row) {
        std::ostringstream confidence;
        confidence << std::fixed << std::setprecision(2) << row.confidence;
        const auto scope = ScopeLabel(row);
        return domain::MemoryCandidate{
            .id = row.id,
            .search_text = row.content + " " + row.source + " " + scope,
            .formatted = "- [" + scope + "/" + row.source + ", confidence " +
                         confidence.str() + "] " +
                         domain::PreviewMemoryText(row.content, 520),
            .updated_at = row.updated_at,
        };
      });
  return result;
}

std::vector<domain::MemoryCandidate>
HistoryCandidates(std::span<const domain::ConversationIndexRecord> records) {
  std::vector<domain::MemoryCandidate> result;
  result.reserve(records.size());
  std::ranges::transform(
      records, std::back_inserter(result), [](const auto &row) {
        const auto text = domain::PreviewMemoryText(row.text, 320);
        return domain::MemoryCandidate{
            .id = {},
            .search_text = row.title + " " + text,
            .formatted =
                "- " + HistoryTitle(row) + " " + row.role + ": " + text,
            .updated_at = row.updated_at,
        };
      });
  return result;
}

std::vector<domain::MemoryCandidate>
SkillCandidates(std::span<const domain::MemorySkillRecord> records) {
  std::vector<domain::MemoryCandidate> result;
  result.reserve(records.size());
  std::ranges::transform(
      records, std::back_inserter(result), [](const auto &row) {
        std::string formatted = "- " + row.name;
        if (!row.description.empty())
          formatted += " - " + row.description;
        if (!row.path.empty()) {
          formatted += "\n  - SKILL.md: " + row.path;
          if (!row.path.ends_with('/'))
            formatted.push_back('/');
          formatted += "SKILL.md\n  - Root: " + row.path;
        }
        return domain::MemoryCandidate{
            .id = {},
            .search_text = row.name + " " + row.description + " " + row.path,
            .formatted = std::move(formatted),
            .updated_at = row.updated_at,
        };
      });
  return result;
}

} // namespace

MemoryContextService::MemoryContextService(
    std::shared_ptr<MemoryStore> store,
    std::shared_ptr<const domain::MemoryExtractionPolicy> extraction_policy,
    std::shared_ptr<const MemoryPromptRenderer> prompt_renderer)
    : store_(std::move(store)),
      extraction_policy_(std::move(extraction_policy)),
      prompt_renderer_(std::move(prompt_renderer)) {
  if (!store_ || !extraction_policy_ || !prompt_renderer_)
    throw std::invalid_argument(
        "memory context dependencies must not be empty");
}

huxerui::Task<MemoryStoreResult<PreparedMemoryContext>>
MemoryContextService::Prepare(std::string project_id, std::string query,
                              std::string exclude_conversation_id,
                              bool learning_enabled) {
  if (!learning_enabled) {
    auto manual = co_await store_->LoadManualMemories(std::move(project_id));
    if (!manual)
      co_return std::unexpected(manual.error());
    co_return PreparedMemoryContext{
        .prompt = prompt_renderer_->RenderManual(MemoryCandidates(*manual)),
        .learning_enabled = false};
  }

  auto corpus = co_await store_->LoadRetrievalCorpus(
      std::move(project_id), std::move(exclude_conversation_id));
  if (!corpus)
    co_return std::unexpected(corpus.error());
  const auto now = NowMilliseconds();
  auto working =
      domain::RankMemoryCandidates(WorkingCandidates(corpus->working), query,
                                   kWorkingLimit, true, kWorkingBoost, now);
  auto memories = domain::RankMemoryCandidates(
      MemoryCandidates(corpus->memories), query, kMemoryLimit, true, 0.0, now);
  auto history = domain::RankMemoryCandidates(
      HistoryCandidates(corpus->history), query, kHistoryLimit, true, 0.0, now);
  auto skills = domain::RankMemoryCandidates(
      SkillCandidates(corpus->skills), query, kSkillLimit, true, 0.0, now);

  std::vector<std::string> used;
  for (const auto &memory : memories) {
    if (!memory.id.empty())
      used.push_back(memory.id);
  }
  if (!used.empty()) {
    auto marked = co_await store_->MarkUsed(std::move(used));
    if (!marked)
      co_return std::unexpected(marked.error());
  }
  co_return PreparedMemoryContext{
      .prompt =
          prompt_renderer_->RenderLearning(working, memories, history, skills),
      .learning_enabled = true,
  };
}

huxerui::Task<MemoryStoreResult<void>>
MemoryContextService::CommitTurn(bool learning_enabled,
                                 domain::MemoryConversationTurn turn,
                                 std::string user_text) {
  if (!learning_enabled)
    co_return MemoryStoreResult<void>{};

  auto indexed = co_await store_->IndexConversationTurn(turn);
  if (!indexed)
    co_return std::unexpected(indexed.error());

  const auto extracted = extraction_policy_->Extract(user_text);
  if (!extracted)
    co_return MemoryStoreResult<void>{};
  auto saved = co_await store_->SaveExtracted(domain::MemoryRecord{
      .id = {},
      .scope = extracted->scope,
      .project_id = std::move(turn.project_id),
      // Auto-extracted memories carry no label; the list falls back to the
      // statement preview for them.
      .title = {},
      .content = extracted->content,
      .source = "auto",
      .confidence = extracted->confidence,
  });
  if (!saved)
    co_return std::unexpected(saved.error());
  co_return MemoryStoreResult<void>{};
}

} // namespace linecode::application
