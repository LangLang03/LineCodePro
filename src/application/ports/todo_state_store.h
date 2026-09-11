#pragma once

#include <algorithm>
#include <cctype>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <ranges>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

namespace linecode::application {

// Legacy status vocabulary, ported from cn.lineai.model.TodoItem.STATUS_*.
inline constexpr std::string_view kTodoStatusPending = "pending";
inline constexpr std::string_view kTodoStatusInProgress = "in_progress";
inline constexpr std::string_view kTodoStatusCompleted = "completed";

// Literal port of TodoItem.normalizeStatus(): trim, lower-case and accept the
// historical spellings the legacy Android build tolerated. Unknown values
// degrade to pending instead of failing the whole update.
[[nodiscard]] inline std::string NormalizeTodoStatus(std::string_view raw) {
  const auto is_space = [](char character) {
    return std::isspace(static_cast<unsigned char>(character)) != 0;
  };
  while (!raw.empty() && is_space(raw.front()))
    raw.remove_prefix(1U);
  while (!raw.empty() && is_space(raw.back()))
    raw.remove_suffix(1U);
  std::string value;
  value.reserve(raw.size());
  for (const char character : raw) {
    value.push_back(static_cast<char>(
        std::tolower(static_cast<unsigned char>(character))));
  }
  if (value == kTodoStatusInProgress || value == "inprogress" ||
      value == "in-progress") {
    return std::string{kTodoStatusInProgress};
  }
  if (value == kTodoStatusCompleted || value == "done" || value == "complete" ||
      value == "finished") {
    return std::string{kTodoStatusCompleted};
  }
  return std::string{kTodoStatusPending};
}

// Value shape of one legacy TodoItem entry.
struct TodoItem final {
  std::string content;
  std::string status{std::string{kTodoStatusPending}};

  bool operator==(const TodoItem &) const = default;
};

// Whole-list snapshot (legacy TodoStateStore owns replace/snapshot/counts).
struct TodoState final {
  std::vector<TodoItem> items;

  [[nodiscard]] std::size_t total_count() const noexcept {
    return items.size();
  }
  [[nodiscard]] std::size_t completed_count() const noexcept {
    return static_cast<std::size_t>(std::ranges::count_if(
        items, [](const TodoItem &item) {
          return item.status == kTodoStatusCompleted;
        }));
  }
  [[nodiscard]] bool empty() const noexcept { return items.empty(); }

  bool operator==(const TodoState &) const = default;
};

// Literal port of ModelPromptController.renderTodoStateForPrompt(), the text
// injected as {{TODO_STATE}} / TODO_LIST into the next system prompt.
[[nodiscard]] inline std::string RenderTodoState(const TodoState &state) {
  std::string rendered;
  for (std::size_t index = 0; index < state.items.size(); ++index) {
    const auto &item = state.items[index];
    if (item.content.empty())
      continue;
    if (!rendered.empty())
      rendered.push_back('\n');
    rendered += std::to_string(index + 1U);
    rendered += ". [";
    rendered += item.status;
    rendered += "] ";
    rendered += item.content;
  }
  return rendered;
}

enum class TodoStateErrorCode : std::uint8_t {
  unavailable,
  read_failed,
  write_failed,
};

struct TodoStateError final {
  TodoStateErrorCode code{TodoStateErrorCode::write_failed};
  std::string message;

  bool operator==(const TodoStateError &) const = default;
};

template <typename T>
using TodoStateResult = std::expected<T, TodoStateError>;

// Asynchronous TODO state boundary (legacy cn.lineai.state.TodoStateStore).
// Replace() returns the state after the write so callers can render their
// summary without a second round trip; Load() feeds the {{TODO_STATE}}
// projection. Platform backends are provided elsewhere; the tool registry only
// depends on this abstraction.
class TodoStateStore {
public:
  virtual ~TodoStateStore() = default;

  [[nodiscard]] virtual huxerui::Task<TodoStateResult<TodoState>>
  Replace(std::vector<TodoItem> items) = 0;

  [[nodiscard]] virtual huxerui::Task<TodoStateResult<TodoState>> Load() = 0;
};

} // namespace linecode::application
