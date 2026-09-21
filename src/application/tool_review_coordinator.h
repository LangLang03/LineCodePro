#pragma once

#include <deque>
#include <functional>
#include <memory>
#include <optional>
#include <string_view>

#include <huxerui/task.h>

#include "application/tool_review_broker.h"

namespace linecode::application {

// Serializes tool-review prompts from the main completion loop and sub-agents.
// Every waiter owns a separate entry, so concurrent requests cannot overwrite
// each other's decision. The presentation observes only the queue head.
class ToolReviewCoordinator final
    : public std::enable_shared_from_this<ToolReviewCoordinator> {
public:
  using Request = CompletionObserver::ToolReviewRequest;
  using Decision = CompletionObserver::ToolReviewDecision;

  struct Prompt final {
    Request request;
    bool submitted{};
  };

  explicit ToolReviewCoordinator(std::function<void()> on_changed = {});

  [[nodiscard]] ToolReviewBroker::Handler ReviewHandler();
  [[nodiscard]] std::optional<Prompt> Current() const;

  bool Resolve(std::string_view tool_call_id, Decision decision);
  void RejectAll();
  void Close();

private:
  struct Entry final {
    Request request;
    std::optional<Decision> decision;
    bool submitted{};
  };

  [[nodiscard]] static huxerui::Task<Decision>
  Await(std::shared_ptr<ToolReviewCoordinator> owner, Request request);
  void Remove(const std::shared_ptr<Entry> &entry);
  void NotifyChanged() const;

  std::function<void()> on_changed_;
  std::deque<std::shared_ptr<Entry>> entries_;
  bool closed_{};
};

} // namespace linecode::application
