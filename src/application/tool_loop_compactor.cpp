#include "application/tool_loop_compactor.h"

#include <algorithm>
#include <cstdint>
#include <optional>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

#include "domain/compaction_progress.h"

namespace linecode::application {
namespace {

domain::MessageRole ToDomainRole(const CompletionRole role) {
  switch (role) {
  case CompletionRole::assistant:
    return domain::MessageRole::assistant;
  case CompletionRole::tool:
    return domain::MessageRole::tool;
  case CompletionRole::system:
  case CompletionRole::user:
    break;
  }
  return domain::MessageRole::user;
}

// Only the fields the compaction estimator and the preserved-tail rule read
// are carried over; the loop keeps the original messages for the tail, so this
// view never has to round-trip.
domain::ChatMessage ToDomain(const CompletionMessage &message) {
  domain::ChatMessage converted;
  converted.role = ToDomainRole(message.role);
  converted.content = message.content;
  converted.reasoning_content = message.reasoning_content;
  for (const auto &call : message.tool_calls) {
    domain::AssistantToolEvent event;
    event.call.id = call.id;
    event.call.name = call.name;
    event.call.arguments_json = call.arguments_json;
    converted.timeline.push_back(std::move(event));
  }
  return converted;
}

} // namespace

ToolLoopCompactor::ToolLoopCompactor(
    std::shared_ptr<ContextCompactionService> compaction,
    std::shared_ptr<ModelStore> models, const bool include_reasoning,
    std::shared_ptr<ChatSession> session)
    : compaction_(std::move(compaction)), models_(std::move(models)),
      include_reasoning_(include_reasoning), session_(std::move(session)) {}

huxerui::Task<CompletionRequest>
ToolLoopCompactor::CompactIfNeeded(CompletionRequest request,
                                  const std::int64_t observed_input_tokens) {
  if (!compaction_)
    co_return request;

  // The composer puts the system prompt first; it is never summarized.
  std::size_t head = 0;
  if (!request.messages.empty() &&
      request.messages.front().role == CompletionRole::system) {
    head = 1;
  }
  if (request.messages.size() <= head + 1)
    co_return request;

  // Completion messages carry no ids, but the preserved-tail rule and the
  // compactor both key on them, so give each converted message a stable one
  // before anything reads it.
  std::vector<domain::ChatMessage> converted;
  converted.reserve(request.messages.size() - head);
  for (std::size_t index = head; index < request.messages.size(); ++index) {
    converted.push_back(ToDomain(request.messages[index]));
    // Prefer the conversation row this message came from. Only the in-flight
    // group has none, and those are exactly the messages that must be kept.
    converted.back().id = request.messages[index].source_id != 0
                              ? request.messages[index].source_id
                              : index + 1;
  }

  std::optional<domain::ModelConfig> model;
  if (models_) {
    auto selected = co_await models_->SelectedId();
    if (selected && !selected->empty()) {
      auto found = co_await models_->Find(*selected);
      if (found)
        model = *found;
    }
  }
  // The legacy check used the model the request is running with, so prefer it
  // whenever the caller already resolved one.
  if (!request.model.model_id.empty())
    model = request.model;

  if (!ShouldAutoCompactMidLoop(model, converted,
                                static_cast<int>(observed_input_tokens),
                                include_reasoning_)) {
    co_return request;
  }

  // Legacy `getAutoCompactPreservedTail("")`: keep the in-flight assistant and
  // tool group verbatim.
  auto preserved = PreservedTail(converted, std::nullopt);
  std::vector<std::uint64_t> preserved_ids;
  preserved_ids.reserve(preserved.size());
  for (const auto &message : preserved)
    preserved_ids.push_back(message.id);

  std::vector<domain::ChatMessage> base;
  base.reserve(converted.size());
  // The conversation rows being summarized, named by the ids they actually
  // have. Messages with no `source_id` are the in-flight group and are never
  // in `base`; taking the ids from provenance rather than from position is
  // what makes the write-back below exact.
  std::vector<std::uint64_t> summarized_rows;
  for (std::size_t index = 0; index < converted.size(); ++index) {
    if (std::ranges::contains(preserved_ids, converted[index].id))
      continue;
    base.push_back(converted[index]);
    if (request.messages[head + index].source_id != 0)
      summarized_rows.push_back(request.messages[head + index].source_id);
  }
  if (!HasCompactableBaseMessages(base))
    co_return request;

  auto compacted = co_await compaction_->Compact(request.model, base);
  if (!compacted || compacted->Empty())
    co_return request;

  // Show the block while it is being applied, exactly like the pre-request
  // path, and take it down again whichever way the write-back goes.
  const bool has_session = session_ != nullptr;
  if (has_session) {
    static_cast<void>(session_->AppendAssistant(
        domain::CompactProgressMessage(0U, domain::compact_status_running)));
  }
  if (has_session && !summarized_rows.empty()) {
    // The summarized rows leave the context and the summary joins it. The
    // in-flight group is deliberately not appended: the loop still owns it and
    // the generation persists it when the turn completes, so appending it here
    // would duplicate it.
    session_->ApplyCompaction(summarized_rows, compacted->summary_content,
                              std::vector<domain::ChatMessage>{});
    static_cast<void>(session_->AppendAssistant(
        domain::CompactProgressMessage(0U, domain::compact_status_done)));
  }

  // Rebuild: system prompt, summary, then the untouched in-flight group.
  std::vector<CompletionMessage> rebuilt;
  rebuilt.reserve(2 + preserved.size());
  if (head == 1)
    rebuilt.push_back(request.messages.front());
  // Legacy summary message: role USER, empty reasoning, no tool calls.
  rebuilt.push_back(CompletionMessage{.role = CompletionRole::user,
                                      .content = compacted->summary_content,
                                      .reasoning_content = {},
                                      .tool_calls = {},
                                      .tool_result = std::nullopt});
  for (std::size_t index = 0; index < converted.size(); ++index) {
    if (std::ranges::contains(preserved_ids, converted[index].id))
      rebuilt.push_back(request.messages[head + index]);
  }
  request.messages = std::move(rebuilt);
  co_return request;
}

} // namespace linecode::application
