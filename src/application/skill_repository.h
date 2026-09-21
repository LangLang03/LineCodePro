#pragma once

#include <memory>

#include "application/ports/skill_services.h"

namespace linecode::application {

class SkillRepository final : public SkillExtensionStore {
public:
  SkillRepository(std::shared_ptr<SkillFiles> files,
                  std::shared_ptr<SkillRecordStore> records);

  [[nodiscard]] huxerui::Task<
      SkillResult<std::vector<domain::SkillRecord>>>
  List(SkillRoots roots) const override;
  [[nodiscard]] huxerui::Task<SkillResult<domain::SkillRecord>>
  Create(SkillRoots roots, domain::SkillLocation location, std::string name,
         std::string description, std::string markdown_body) const override;
  [[nodiscard]] huxerui::Task<SkillResult<domain::SkillRecord>>
  Install(SkillInstallRequest request) const override;
  [[nodiscard]] huxerui::Task<SkillResult<void>>
  SetEnabled(std::string id, bool enabled) const override;
  [[nodiscard]] huxerui::Task<SkillResult<void>>
  Delete(SkillRoots roots, std::string id) const override;
  [[nodiscard]] huxerui::Task<SkillResult<std::string>>
  BuildExtensionPrompt() const;

private:
  std::shared_ptr<SkillFiles> files_;
  std::shared_ptr<SkillRecordStore> records_;
};

} // namespace linecode::application
