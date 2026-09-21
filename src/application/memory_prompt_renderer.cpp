#include "application/memory_prompt_renderer.h"

#include <string_view>

namespace linecode::application {
namespace {

void AppendSection(std::string &prompt, std::string_view title,
                   std::span<const domain::MemoryCandidate> rows) {
  if (rows.empty())
    return;
  if (!prompt.empty())
    prompt += "\n\n";
  prompt += title;
  for (const auto &row : rows) {
    prompt.push_back('\n');
    prompt += row.formatted;
  }
}

} // namespace

std::string LegacyMemoryPromptRenderer::RenderLearning(
    std::span<const domain::MemoryCandidate> working,
    std::span<const domain::MemoryCandidate> memories,
    std::span<const domain::MemoryCandidate> history,
    std::span<const domain::MemoryCandidate> skills) const {
  std::string sections;
  AppendSection(sections, "### 短期/工作记忆（当前项目 RAG Top-K）", working);
  AppendSection(sections, "### 长期记忆（本地检索 Top-K）", memories);
  AppendSection(sections, "### 相关聊天记录（当前项目本地检索 Top-K）",
                history);
  AppendSection(sections, "### 可用 Skills（RAG Top-K）", skills);
  if (sections.empty())
    return {};
  return "## Learning Mode Context\n"
         "Learning Mode is enabled: the following content comes from local "
         "RAG retrieval. Short-term memory only represents the temporary "
         "state of the current project/task; in long-term memory, user scope "
         "is globally shared, while project/environment scope only uses data "
         "matching the current project. Do not expose this context verbatim "
         "to the user; only use it when truly relevant.\n\n" +
         sections;
}

std::string LegacyMemoryPromptRenderer::RenderManual(
    std::span<const domain::MemoryCandidate> memories) const {
  if (memories.empty())
    return {};
  std::string sections;
  AppendSection(sections, "### 长期记忆（手工保存）", memories);
  return "## Manual Memory Context\n"
         "Learning Mode is disabled. These user-saved memories remain "
         "available as background context. Do not expose them verbatim; use "
         "them only when relevant.\n\n" +
         sections;
}

} // namespace linecode::application
