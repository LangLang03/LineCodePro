#pragma once

#include <memory>

#include <huxerui/file.h>

#include "application/ports/skill_services.h"

namespace linecode::infrastructure {

class SqliteSkillRecordStoreState;

class SqliteSkillRecordStore final : public application::SkillRecordStore {
public:
  explicit SqliteSkillRecordStore(huxerui::File database_file);

  [[nodiscard]] huxerui::Task<
      application::SkillResult<std::vector<domain::SkillRecord>>>
  List() override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  UpsertDiscovered(std::vector<domain::SkillRecord> skills) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  SetEnabled(std::string id, bool enabled) override;
  [[nodiscard]] huxerui::Task<application::SkillResult<void>>
  Delete(std::vector<std::string> ids) override;

private:
  std::shared_ptr<SqliteSkillRecordStoreState> state_;
};

} // namespace linecode::infrastructure
