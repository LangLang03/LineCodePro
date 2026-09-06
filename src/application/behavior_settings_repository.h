#pragma once

#include <memory>
#include <string>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/behavior_settings.h"

namespace linecode::application {

class AiBehaviorSettingsRepository final {
public:
  explicit AiBehaviorSettingsRepository(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<SettingsResult<domain::AiBehaviorSettings>> Load();
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetTone(domain::ToneMode value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetReasoning(domain::ReasoningEffort value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetThinkingScroll(bool value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetThinkingAutoExpand(bool value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetPreserveReasoning(bool value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetLearningMode(bool value);
  [[nodiscard]] huxerui::Task<SettingsResult<void>> SetSoftCompaction(bool value);

private:
  std::shared_ptr<AsyncSettingsStore> store_;
};

class InputSettingsRepository final {
public:
  explicit InputSettingsRepository(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<SettingsResult<domain::InputSettings>> Load();
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  SetEnterKeyBehavior(domain::EnterKeyBehavior value);

private:
  std::shared_ptr<AsyncSettingsStore> store_;
};

} // namespace linecode::application
