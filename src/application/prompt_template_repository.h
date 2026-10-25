#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/prompt_template.h"

namespace linecode::application {

class PromptTemplateRepository final {
public:
  explicit PromptTemplateRepository(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<SettingsResult<std::vector<domain::PromptTemplateItem>>> Load();
  [[nodiscard]] huxerui::Task<SettingsResult<void>> Save(std::string id, std::string value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> Reset(std::string id);
  [[nodiscard]] const domain::PromptTemplateDefinition *Find(std::string_view id) const noexcept;

  // Drops stored overrides that are not a real customization: copies of a
  // superseded built-in default (identified by hash) and keys whose template no
  // longer exists. Text the user actually edited is never touched. Runs at most
  // once per `revision` and returns how many rows it removed.
  //
  // The superseded table is a parameter so tests can inject their own; production
  // passes `domain::SupersededPromptDefaults()`.
  [[nodiscard]] huxerui::Task<SettingsResult<int>> MigrateSupersededDefaults(
      std::vector<domain::SupersededPromptDefault> superseded,
      std::int64_t revision);

private:
  [[nodiscard]] static std::string Key(std::string_view id);

  std::shared_ptr<AsyncSettingsStore> store_;
  std::vector<domain::PromptTemplateDefinition> definitions_;
};

} // namespace linecode::application
