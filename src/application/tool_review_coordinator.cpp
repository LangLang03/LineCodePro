#include "application/tool_review_coordinator.h"

#include <algorithm>
#include <chrono>
#include <utility>

namespace linecode::application {

ToolReviewCoordinator::ToolReviewCoordinator(std::function<void()> on_changed)
    : on_changed_(std::move(on_changed)) {}

ToolReviewBroker::Handler ToolReviewCoordinator::ReviewHandler() {
  auto owner = shared_from_this();
  return [owner = std::move(owner)](Request request) {
    return Await(owner, std::move(request));
  };
}

std::optional<ToolReviewCoordinator::Prompt>
ToolReviewCoordinator::Current() const {
  if (entries_.empty())
    return std::nullopt;
  return Prompt{.request = entries_.front()->request,
                .submitted = entries_.front()->submitted};
}

bool ToolReviewCoordinator::Resolve(std::string_view tool_call_id,
                                    Decision decision) {
  if (entries_.empty())
    return false;
  const auto &entry = entries_.front();
  if (entry->submitted || entry->request.call.id != tool_call_id)
    return false;
  entry->submitted = true;
  entry->decision = decision;
  NotifyChanged();
  return true;
}

void ToolReviewCoordinator::RejectAll() {
  bool changed = false;
  for (const auto &entry : entries_) {
    if (entry->decision)
      continue;
    entry->submitted = true;
    entry->decision = Decision::reject;
    changed = true;
  }
  if (changed)
    NotifyChanged();
}

void ToolReviewCoordinator::Close() {
  closed_ = true;
  RejectAll();
}

huxerui::Task<ToolReviewCoordinator::Decision>
ToolReviewCoordinator::Await(std::shared_ptr<ToolReviewCoordinator> owner,
                             Request request) {
  if (owner->closed_)
    co_return Decision::reject;

  auto entry = std::make_shared<Entry>(Entry{.request = std::move(request),
                                             .decision = std::nullopt,
                                             .submitted = false});
  owner->entries_.push_back(entry);
  owner->NotifyChanged();

  struct RemovalGuard final {
    std::shared_ptr<ToolReviewCoordinator> owner;
    std::shared_ptr<Entry> entry;
    ~RemovalGuard() { owner->Remove(entry); }
  };
  const RemovalGuard guard{owner, entry};

  while (!entry->decision)
    co_await huxerui::Delay(std::chrono::milliseconds{20});
  co_return *entry->decision;
}

void ToolReviewCoordinator::Remove(const std::shared_ptr<Entry> &entry) {
  const auto found = std::ranges::find(entries_, entry);
  if (found == entries_.end())
    return;
  entries_.erase(found);
  NotifyChanged();
}

void ToolReviewCoordinator::NotifyChanged() const {
  if (on_changed_)
    on_changed_();
}

} // namespace linecode::application
