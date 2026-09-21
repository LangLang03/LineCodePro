#include "application/prompt_template_repository.h"

#include <algorithm>
#include <stdexcept>
#include <utility>

namespace linecode::application {

PromptTemplateRepository::PromptTemplateRepository(
    std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)), definitions_(domain::BuiltInPromptTemplates()) {
  if (!store_) throw std::invalid_argument("settings store must not be empty");
}

huxerui::Task<SettingsResult<std::vector<domain::PromptTemplateItem>>>
PromptTemplateRepository::Load() {
  std::vector<domain::PromptTemplateItem> result;
  result.reserve(definitions_.size());
  for (const auto &definition : definitions_) {
    auto current = co_await store_->GetString(Key(definition.id), definition.default_text);
    if (!current) co_return std::unexpected(current.error());
    result.push_back({.definition = definition,
                      .current_text = *current,
                      .customized = *current != definition.default_text});
  }
  co_return result;
}

huxerui::Task<SettingsResult<void>>
PromptTemplateRepository::Save(std::string id, std::string value) {
  if (!Find(id)) {
    co_return std::unexpected(SettingsStoreError{.message = "Unknown prompt template: " + id});
  }
  co_return co_await store_->SetString(Key(id), std::move(value));
}

huxerui::Task<SettingsResult<void>> PromptTemplateRepository::Reset(std::string id) {
  if (!Find(id)) {
    co_return std::unexpected(SettingsStoreError{.message = "Unknown prompt template: " + id});
  }
  co_return co_await store_->Remove(Key(id));
}

const domain::PromptTemplateDefinition *
PromptTemplateRepository::Find(std::string_view id) const noexcept {
  const auto found = std::ranges::find(definitions_, id,
                                       &domain::PromptTemplateDefinition::id);
  return found == definitions_.end() ? nullptr : &*found;
}

std::string PromptTemplateRepository::Key(std::string_view id) {
  return "@linecode_prompt_template_" + std::string{id};
}

} // namespace linecode::application
