#pragma once

#include <memory>

#include "application/ports/skill_services.h"

namespace linecode::infrastructure {

class HuxSkillFiles final : public application::SkillFiles {
public:
  HuxSkillFiles();
  explicit HuxSkillFiles(std::shared_ptr<application::SkillClock> clock);

  [[nodiscard]] application::SkillResult<std::vector<domain::SkillRecord>>
  Discover(const application::SkillRoots &roots) const override;
  [[nodiscard]] application::SkillResult<domain::SkillRecord>
  Create(const application::SkillRoots &roots, domain::SkillLocation location,
         std::string name, std::string description,
         std::string markdown_body) const override;
  [[nodiscard]] application::SkillResult<domain::SkillRecord>
  Install(application::SkillInstallRequest request) const override;
  [[nodiscard]] application::SkillResult<void>
  Delete(const application::SkillRoots &roots,
         const domain::SkillRecord &skill) const override;
  [[nodiscard]] application::SkillResult<std::string>
  ReadPrompt(const domain::SkillRecord &skill,
             std::size_t maximum_characters) const override;

private:
  std::shared_ptr<application::SkillClock> clock_;
};

} // namespace linecode::infrastructure
