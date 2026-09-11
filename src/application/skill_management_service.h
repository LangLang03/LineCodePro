#pragma once

#include <memory>

#include "application/skill_repository.h"

namespace linecode::application {

class SkillManagementService final : public SkillSourceInstaller {
public:
  SkillManagementService(std::shared_ptr<SkillRepository> repository,
                         std::shared_ptr<SkillPackageGateway> packages);

  [[nodiscard]] huxerui::Task<SkillResult<domain::SkillRecord>>
  InstallGitHub(SkillRoots roots, domain::SkillLocation location,
                std::string url) const override;
  [[nodiscard]] huxerui::Task<SkillResult<domain::SkillRecord>>
  InstallSkillHub(SkillRoots roots, domain::SkillLocation location,
                  std::string slug, std::string version) const override;

private:
  std::shared_ptr<SkillRepository> repository_;
  std::shared_ptr<SkillPackageGateway> packages_;
};

} // namespace linecode::application
