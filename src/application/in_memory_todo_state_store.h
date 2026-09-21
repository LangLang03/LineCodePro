#pragma once

#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/todo_state_store.h"

namespace linecode::application {

// Task-scoped TODO snapshot.
//
// The legacy `cn.lineai.state.TodoStateStore` keeps the list in a plain
// synchronized `ArrayList`; it is deliberately *not* persisted, so a process
// restart starts with an empty list. This port keeps that lifetime exactly:
// one in-memory store per runtime, shared by the `todo_update` tool (writer)
// and the prompt projection (reader).
class InMemoryTodoStateStore final : public TodoStateStore {
public:
  [[nodiscard]] huxerui::Task<TodoStateResult<TodoState>>
  Replace(std::vector<TodoItem> items) override {
    std::scoped_lock lock(mutex_);
    state_.items = std::move(items);
    co_return state_;
  }

  [[nodiscard]] huxerui::Task<TodoStateResult<TodoState>> Load() override {
    std::scoped_lock lock(mutex_);
    co_return state_;
  }

private:
  std::mutex mutex_;
  TodoState state_;
};

} // namespace linecode::application
