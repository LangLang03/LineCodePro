#include "application/skill_repository.h"

#include <algorithm>
#include <stdexcept>
#include <utility>

#include <huxerui/task.h>

namespace linecode::application {

SkillRepository::SkillRepository(std::shared_ptr<SkillFiles> files,
                                 std::shared_ptr<SkillRecordStore> records)
    : files_(std::move(files)), records_(std::move(records)) {
  if (!files_ || !records_)
    throw std::invalid_argument(
        "SkillRepository requires files and record store");
}

huxerui::Task<SkillResult<std::vector<domain::SkillRecord>>>
SkillRepository::List(SkillRoots roots) const {
  auto files = files_;
  auto discovered = co_await huxerui::RunWorker(
      [files, roots = std::move(roots)] { return files->Discover(roots); });
  if (!discovered)
    co_return std::unexpected(std::move(discovered.error()));
  auto saved = records_->UpsertDiscovered(std::move(*discovered));
  auto saved_result = co_await std::move(saved);
  if (!saved_result)
    co_return std::unexpected(std::move(saved_result.error()));
  co_return co_await records_->List();
}

huxerui::Task<SkillResult<domain::SkillRecord>> SkillRepository::Create(
    SkillRoots roots, const domain::SkillLocation location, std::string name,
    std::string description, std::string markdown_body) const {
  auto files = files_;
  auto created = co_await huxerui::RunWorker(
      [files, roots = std::move(roots), location, name = std::move(name),
       description = std::move(description),
       markdown_body = std::move(markdown_body)]() mutable {
        return files->Create(roots, location, std::move(name),
                             std::move(description), std::move(markdown_body));
      });
  if (!created)
    co_return std::unexpected(std::move(created.error()));
  auto saved = co_await records_->UpsertDiscovered({*created});
  if (!saved)
    co_return std::unexpected(std::move(saved.error()));
  co_return std::move(*created);
}

huxerui::Task<SkillResult<domain::SkillRecord>>
SkillRepository::Install(SkillInstallRequest request) const {
  auto files = files_;
  auto installed = co_await huxerui::RunWorker(
      [files, request = std::move(request)]() mutable {
        return files->Install(std::move(request));
      });
  if (!installed)
    co_return std::unexpected(std::move(installed.error()));
  auto saved = co_await records_->UpsertDiscovered({*installed});
  if (!saved)
    co_return std::unexpected(std::move(saved.error()));
  co_return std::move(*installed);
}

huxerui::Task<SkillResult<void>>
SkillRepository::SetEnabled(std::string id, const bool enabled) const {
  co_return co_await records_->SetEnabled(std::move(id), enabled);
}

huxerui::Task<SkillResult<void>>
SkillRepository::Delete(SkillRoots roots, std::string id) const {
  auto records = co_await records_->List();
  if (!records)
    co_return std::unexpected(std::move(records.error()));
  const auto target = std::ranges::find(records.value(), id,
                                        &domain::SkillRecord::id);
  if (target == records->end())
    co_return SkillResult<void>{};
  auto files = files_;
  auto deleted = co_await huxerui::RunWorker(
      [files, roots = std::move(roots), skill = *target] {
        return files->Delete(roots, skill);
      });
  if (!deleted)
    co_return std::unexpected(std::move(deleted.error()));
  co_return co_await records_->Delete({std::move(id)});
}

huxerui::Task<SkillResult<std::string>>
SkillRepository::BuildExtensionPrompt() const {
  auto records = co_await records_->List();
  if (!records)
    co_return std::unexpected(std::move(records.error()));
  std::erase_if(*records,
                [](const domain::SkillRecord &skill) { return !skill.enabled; });
  std::ranges::sort(*records, {}, &domain::SkillRecord::id);
  auto files = files_;
  co_return co_await huxerui::RunWorker(
      [files, skills = std::move(*records)]() -> SkillResult<std::string> {
        constexpr std::size_t maximum_prompt_characters = 18'000;
        std::string prompt;
        std::size_t used{};
        for (const auto &skill : skills) {
          auto body = files->ReadPrompt(skill, 6000);
          if (!body)
            return std::unexpected(std::move(body.error()));
          std::string block = "#### Skill: " + skill.name +
                              "\n安装位置: " +
                              std::string{domain::SkillLocationLabel(
                                  skill.location)} +
                              "\nSKILL.md: " + skill.skill_markdown_path +
                              "\nRoot: " + skill.root_path;
          if (!body->empty())
            block += "\n\n" + *body;
          if (block.size() > maximum_prompt_characters -
                                 std::min(maximum_prompt_characters, used)) {
            prompt +=
                "#### Skills 提示词已截断\n已达到提示词长度上限，剩余 "
                "Skills 仅按路径和工具描述处理。\n";
            break;
          }
          if (!prompt.empty())
            prompt += "\n\n";
          prompt += block;
          used += block.size();
        }
        return prompt;
      });
}

} // namespace linecode::application
