#include "application/prompt_template_repository.h"

#include <algorithm>
#include <ranges>
#include <stdexcept>
#include <utility>

#include "application/file_content_hash.h"

namespace linecode::application {
namespace {
constexpr auto kRevisionKey = "@linecode_prompt_template_revision";
constexpr std::string_view kTemplateKeyPrefix = "@linecode_prompt_template_";
} // namespace

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

huxerui::Task<SettingsResult<int>>
PromptTemplateRepository::MigrateSupersededDefaults(
    std::vector<domain::SupersededPromptDefault> superseded,
    std::int64_t revision) {
  auto applied = co_await store_->GetInteger(kRevisionKey, 0);
  if (!applied) co_return std::unexpected(applied.error());
  if (*applied >= revision) co_return 0;

  int removed = 0;
  for (const auto &definition : definitions_) {
    const auto key = Key(definition.id);
    auto stored = co_await store_->GetString(key, {});
    if (!stored) co_return std::unexpected(stored.error());
    if (stored->empty())
      continue;
    // A stored copy of the current default is not a customization either, but
    // leaving it alone keeps a deliberate "save without edits" harmless.
    const bool superseded_default = std::ranges::any_of(
        superseded, [&](const auto &entry) {
          return entry.id == definition.id &&
                 entry.sha256_hex == Sha256Hex(*stored);
        });
    if (!superseded_default)
      continue;
    auto dropped = co_await store_->Remove(key);
    if (!dropped) co_return std::unexpected(dropped.error());
    ++removed;
  }

  // Keys whose template was renamed or removed would otherwise sit in the
  // database forever and reappear if an id is ever reused.
  auto all = co_await store_->LineCodeSettings();
  if (!all) co_return std::unexpected(all.error());
  for (const auto &[key, value] : *all) {
    static_cast<void>(value);
    if (!key.starts_with(kTemplateKeyPrefix))
      continue;
    const std::string_view id{key.data() + kTemplateKeyPrefix.size(),
                              key.size() - kTemplateKeyPrefix.size()};
    if (std::ranges::any_of(definitions_, [id](const auto &definition) {
          return definition.id == id;
        }))
      continue;
    auto dropped = co_await store_->Remove(key);
    if (!dropped) co_return std::unexpected(dropped.error());
    ++removed;
  }

  auto saved = co_await store_->SetInteger(kRevisionKey, revision);
  if (!saved) co_return std::unexpected(saved.error());
  co_return removed;
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
