#include "application/behavior_settings_repository.h"

#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {
constexpr auto kTone = "@lineai_tone";
constexpr auto kThinkingScroll = "@lineai_thinking_scroll";
constexpr auto kThinkingAutoExpand = "@lineai_thinking_auto_expand";
constexpr auto kReasoning = "@lineai_reasoning_effort";
constexpr auto kPreserveReasoning = "@lineai_preserve_reasoning";
constexpr auto kLearningMode = "@linecode_learning_mode_enabled";
constexpr auto kSoftCompaction = "@lineai_soft_compaction";
constexpr auto kEnterKey = "@lineai_enter_key_behavior";
} // namespace

AiBehaviorSettingsRepository::AiBehaviorSettingsRepository(
    std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_) throw std::invalid_argument("settings store must not be empty");
}

huxerui::Task<SettingsResult<domain::AiBehaviorSettings>>
AiBehaviorSettingsRepository::Load() {
  auto tone = co_await store_->GetString(kTone, "coding");
  if (!tone) co_return std::unexpected(tone.error());
  auto scroll = co_await store_->GetBoolean(kThinkingScroll, true);
  if (!scroll) co_return std::unexpected(scroll.error());
  auto expand = co_await store_->GetBoolean(kThinkingAutoExpand, false);
  if (!expand) co_return std::unexpected(expand.error());
  auto reasoning = co_await store_->GetString(kReasoning, "medium");
  if (!reasoning) co_return std::unexpected(reasoning.error());
  auto preserve = co_await store_->GetBoolean(kPreserveReasoning, false);
  if (!preserve) co_return std::unexpected(preserve.error());
  auto learning = co_await store_->GetBoolean(kLearningMode, false);
  if (!learning) co_return std::unexpected(learning.error());
  auto compaction = co_await store_->GetBoolean(kSoftCompaction, true);
  if (!compaction) co_return std::unexpected(compaction.error());
  co_return domain::AiBehaviorSettings{
      .tone = domain::ParseToneMode(*tone),
      .reasoning = domain::ParseReasoningEffort(*reasoning),
      .thinking_scroll = *scroll,
      .thinking_auto_expand = *expand,
      .preserve_reasoning = *preserve,
      .learning_mode = *learning,
      .soft_compaction = *compaction,
  };
}

huxerui::Task<SettingsResult<void>>
AiBehaviorSettingsRepository::SetTone(domain::ToneMode value) {
  co_return co_await store_->SetString(kTone, std::string{domain::SerializeToneMode(value)});
}
huxerui::Task<SettingsResult<void>>
AiBehaviorSettingsRepository::SetReasoning(domain::ReasoningEffort value) {
  co_return co_await store_->SetString(kReasoning, std::string{domain::SerializeReasoningEffort(value)});
}
huxerui::Task<SettingsResult<void>> AiBehaviorSettingsRepository::SetThinkingScroll(bool value) {
  co_return co_await store_->SetBoolean(kThinkingScroll, value);
}
huxerui::Task<SettingsResult<void>> AiBehaviorSettingsRepository::SetThinkingAutoExpand(bool value) {
  co_return co_await store_->SetBoolean(kThinkingAutoExpand, value);
}
huxerui::Task<SettingsResult<void>> AiBehaviorSettingsRepository::SetPreserveReasoning(bool value) {
  co_return co_await store_->SetBoolean(kPreserveReasoning, value);
}
huxerui::Task<SettingsResult<void>> AiBehaviorSettingsRepository::SetLearningMode(bool value) {
  co_return co_await store_->SetBoolean(kLearningMode, value);
}
huxerui::Task<SettingsResult<void>> AiBehaviorSettingsRepository::SetSoftCompaction(bool value) {
  co_return co_await store_->SetBoolean(kSoftCompaction, value);
}

InputSettingsRepository::InputSettingsRepository(std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_) throw std::invalid_argument("settings store must not be empty");
}

huxerui::Task<SettingsResult<domain::InputSettings>> InputSettingsRepository::Load() {
  auto value = co_await store_->GetString(kEnterKey, "send");
  if (!value) co_return std::unexpected(value.error());
  co_return domain::InputSettings{.enter_key = domain::ParseEnterKeyBehavior(*value)};
}

huxerui::Task<SettingsResult<void>>
InputSettingsRepository::SetEnterKeyBehavior(domain::EnterKeyBehavior value) {
  co_return co_await store_->SetString(kEnterKey, std::string{domain::SerializeEnterKeyBehavior(value)});
}

} // namespace linecode::application
