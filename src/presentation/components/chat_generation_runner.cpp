#include "presentation/components/chat_generation_runner.h"

#include <chrono>
#include <optional>
#include <ranges>
#include <utility>
#include <variant>
#include <vector>

#include "application/auto_compaction_service.h"
#include "application/behavior_settings_repository.h"
#include "application/chat_session.h"
#include "application/context_compaction.h"
#include "application/generation_controller.h"
#include "application/mcp_completion_loop.h"
#include "application/memory_context_service.h"
#include "application/memory_conversation_snapshot.h"
#include "application/ports/model_store.h"
#include "application/ports/todo_state_store.h"
#include "application/skill_repository.h"
#include "application/token_usage_tracker.h"
#include "application/tool_review_coordinator.h"
#include "domain/compaction_progress.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

std::int64_t NowMilliseconds() noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

// Legacy `GenerationFlowController.RETRY_DELAY_MS`.
constexpr auto kRetryDelay = std::chrono::milliseconds{5000};

class DefaultChatGenerationRunner final
    : public ChatGenerationRunner,
      public std::enable_shared_from_this<DefaultChatGenerationRunner> {
public:
  DefaultChatGenerationRunner(
      ChatGenerationDependencies dependencies,
      std::shared_ptr<application::ToolReviewCoordinator> tool_reviews,
      std::shared_ptr<application::PendingMessageQueue> pending_messages,
      TaskScope tasks, State<TaskHandle> active_generation,
      State<std::size_t> revision, std::string current_project_id,
      application::PromptAssemblyContext prompt_context,
      domain::ToolPermissionMode permission_mode, ToastHandle toast,
      RetryLabels retry_labels)
      : dependencies_(std::move(dependencies)),
        retry_labels_(std::move(retry_labels)),
        tool_reviews_(std::move(tool_reviews)),
        pending_messages_(std::move(pending_messages)),
        tasks_(std::move(tasks)),
        active_generation_(std::move(active_generation)),
        revision_(std::move(revision)),
        current_project_id_(std::move(current_project_id)),
        prompt_context_(std::move(prompt_context)),
        permission_mode_(permission_mode), toast_(std::move(toast)) {}

  [[nodiscard]] bool Start(application::PendingMessage message) override {
    if (dependencies_.generation->State().phase ==
        application::GenerationPhase::running) {
      return false;
    }

    const std::string user_text = message.text;
    auto work = dependencies_.generation->Begin(
        user_text, std::move(message.attachments), std::move(message.image));
    if (!work)
      return false;

    last_stream_update_ = {};
    stream_update_scheduled_ = false;
    revision_ += 1;
    const std::string conversation_id{
        dependencies_.session->CurrentConversationId()};
    auto turn = application::BuildMemoryConversationTurn(
        dependencies_.session->Conversations(),
        dependencies_.session->Messages(), current_project_id_, conversation_id,
        NowMilliseconds());
    auto self = shared_from_this();
    active_generation_ = tasks_.Launch([self = std::move(self), conversation_id,
                                        user_text, turn = std::move(turn),
                                        work = std::move(*work)]() mutable {
      return self->Run(std::move(work), std::move(turn),
                       std::move(conversation_id), std::move(user_text));
    });
    return true;
  }

  void CancelAndContinue() override {
    active_generation_.Get().Cancel();
    dependencies_.generation->Cancel();
    ResetToolReview();
    revision_ += 1;
    StartNext();
  }

private:
  // Resets the live progress block when the send path leaves `compacting`,
  // including the cancellation path where HuxerUI destroys the coroutine.
  class AutoCompactionGuard final {
  public:
    explicit AutoCompactionGuard(
        std::shared_ptr<AutoCompactionUiState> state) noexcept
        : state_(std::move(state)) {}

    ~AutoCompactionGuard() {
      if (state_)
        state_->running = false;
    }

    AutoCompactionGuard(const AutoCompactionGuard &) = delete;
    AutoCompactionGuard &operator=(const AutoCompactionGuard &) = delete;

    void Finish(std::string status) const {
      if (state_)
        state_->Finish(std::move(status));
    }

  private:
    std::shared_ptr<AutoCompactionUiState> state_;
  };

  // Port of the pre-request trigger in `ChatInteractionController.send()`
  // (lines 223-244) together with
  // `ContextCompactionController.startContextCompaction()` /
  // `startSoftContextCompaction()`: decide the trigger, show the running
  // progress block, summarize, then write the result back. The request then
  // continues with the refreshed transcript.
  Task<void>
  AutoCompactBeforeRequest(application::GenerationWork &work,
                           const domain::ModelConfig &model,
                           const domain::AiBehaviorSettings &behavior) {
    if (!dependencies_.compaction || !dependencies_.auto_compaction ||
        !dependencies_.session)
      co_return;

    auto snapshot = [this] {
      const auto messages = dependencies_.session->Messages();
      return std::vector<domain::ChatMessage>{messages.begin(), messages.end()};
    };
    auto messages = snapshot();
    // Legacy lines 222-223: the active user message is the last entry of the
    // conversation, because `send()` appended it before checking the trigger.
    std::optional<std::uint64_t> active_user_message_id;
    for (auto entry = messages.rbegin(); entry != messages.rend(); ++entry) {
      if (entry->role == domain::MessageRole::user) {
        active_user_message_id = entry->id;
        break;
      }
    }
    const auto preserved =
        application::PreservedTail(messages, active_user_message_id);
    const auto preserved_ids = application::MessageIdSet(preserved);
    const std::optional<domain::ModelConfig> model_option{model};
    // Legacy `ContextCompactionController` measured the live context with
    // `tokenUsageTracker.lastInputTokens()`; 0 means no protocol has reported
    // usage yet, and the checks fall back to the local estimate.
    const int observed_tokens =
        dependencies_.token_usage ? dependencies_.token_usage->LastInputTokens()
                                  : 0;
    const bool hard = application::ShouldAutoCompactBeforeRequest(
        model_option, messages, observed_tokens, preserved_ids,
        behavior.preserve_reasoning);
    const bool soft = !hard && application::ShouldAutoSoftCompactBeforeRequest(
                                   model_option, messages, observed_tokens,
                                   behavior.soft_compaction, preserved_ids,
                                   behavior.preserve_reasoning);
    if (!hard && !soft)
      co_return;

    std::vector<domain::ChatMessage> base;
    base.reserve(messages.size());
    for (const auto &message : messages) {
      if (std::ranges::contains(preserved_ids, message.id))
        continue;
      base.push_back(message);
    }
    std::vector<std::uint64_t> retained_ids;
    std::uint64_t insert_after_id = 0;
    if (soft) {
      // Legacy lines 388-389: only the oldest slice is summarized.
      base =
          application::ContextCompactionService::SplitForSoftCompact(base).head;
      // ... and its summary belongs right after that slice, before the tail
      // the split left untouched.
      if (!base.empty())
        insert_after_id = base.back().id;
    } else {
      // Legacy lines 479-481: keep the recent user messages verbatim.
      retained_ids = application::RetainedUserMessageIds(base);
    }
    if (!application::HasCompactableBaseMessages(base))
      co_return;
    // Legacy `finishContextCompaction` (lines 476-521) leaves the summarized
    // base and the retained user messages in place and appends the summary,
    // then the preserved tail. The append-only conversation port reproduces
    // that order by excluding the tail too and re-appending it after the
    // summary (see `ChatSession::ApplyCompaction`), so the model reads
    // "summary -> recent context -> current question" instead of finding the
    // summary after the question it is supposed to answer.
    std::vector<std::uint64_t> excluded_ids;
    excluded_ids.reserve(base.size() + preserved.size());
    for (const auto &message : base) {
      if (!std::ranges::contains(retained_ids, message.id))
        excluded_ids.push_back(message.id);
    }
    for (const auto &message : preserved)
      excluded_ids.push_back(message.id);

    // Legacy lines 311-315: the running progress block is pushed before the
    // compaction starts so the transcript shows it while the model works.
    dependencies_.auto_compaction->Begin(work.generation_id);
    revision_ += 1;
    const AutoCompactionGuard guard{dependencies_.auto_compaction};

    auto compacted =
        co_await dependencies_.compaction->Compact(model, std::move(base));
    if (!compacted) {
      // Legacy lines 333-343 / 630-662: the block fails and the failure text is
      // appended; the original request still runs.
      guard.Finish(std::string{domain::compact_status_error});
      static_cast<void>(dependencies_.session->AppendAssistant(
          domain::CompactProgressMessage(0U, domain::compact_status_error)));
      revision_ += 1;
      toast_.Show(
          application::CompactFailureMessage(compacted.error().message));
    } else if (compacted->Empty() || compacted->summary_content.empty()) {
      // Cancelled (`("", "")`) or "the model returned no summary": the block
      // becomes an error and nothing is written back. Legacy line 473.
      guard.Finish(std::string{domain::compact_status_error});
      static_cast<void>(dependencies_.session->AppendAssistant(
          domain::CompactProgressMessage(0U, domain::compact_status_error)));
      revision_ += 1;
      if (!compacted->Empty())
        toast_.Show(application::CompactFailureNoSummary());
    } else {
      // The summarized messages leave the context and the summary joins it;
      // the retained user messages stay in place and the preserved tail is
      // re-appended after the summary, both verbatim.
      dependencies_.session->ApplyCompaction(
          std::move(excluded_ids), compacted->summary_content,
          std::move(preserved), insert_after_id);
      // Legacy lines 517-522: the completed block closes the transcript.
      static_cast<void>(dependencies_.session->AppendAssistant(
          domain::CompactProgressMessage(0U, domain::compact_status_done)));
      guard.Finish(std::string{domain::compact_status_done});
      revision_ += 1;
    }
    // The request snapshot predates the compaction; rebuild it so the model
    // sees the summary instead of the summarized history (legacy
    // `Host.startInitialModelRequest`).
    dependencies_.generation->RefreshMessages(work);
  }

  // The same prompt serves the main loop and the sub-agent runner, so a
  // sub-agent's writes are reviewed rather than silently executed.
  application::ToolReviewBroker::Handler ToolReviewer() const {
    return tool_reviews_->ReviewHandler();
  }

  void ResetToolReview() const { tool_reviews_->RejectAll(); }

  void FailAndContinue(std::uint64_t generation_id,
                       application::CompletionError error) {
    if (dependencies_.generation->Fail(generation_id, std::move(error)))
      revision_ += 1;
    StartNext();
  }

  void StartNext() {
    auto next = pending_messages_->TakeNext();
    if (!next)
      return;
    revision_ += 1;
    static_cast<void>(Start(std::move(*next)));
  }

  Task<void> Run(application::GenerationWork work,
                 domain::MemoryConversationTurn turn,
                 std::string conversation_id, std::string user_text) {
    // Legacy `ContextCompactionController.onConversationChanged()`.
    if (dependencies_.token_usage) {
      dependencies_.token_usage->BeginConversation(
          std::string{dependencies_.session->CurrentConversationId()},
          dependencies_.session->Messages().empty());
    }
    const auto selected_id = co_await dependencies_.model_store->SelectedId();
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!selected_id) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = selected_id.error().message});
      co_return;
    }
    if (selected_id->empty()) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = retry_labels_.no_model});
      co_return;
    }

    auto selected_model =
        co_await dependencies_.model_store->Find(*selected_id);
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!selected_model) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = selected_model.error().message});
      co_return;
    }
    if (!selected_model->has_value()) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = retry_labels_.model_missing});
      co_return;
    }

    auto behavior = co_await dependencies_.behavior_settings->Load();
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!behavior) {
      FailAndContinue(
          work.generation_id,
          application::CompletionError{
              .code = application::CompletionErrorCode::invalid_configuration,
              .message = behavior.error().message});
      co_return;
    }

    // Legacy `ChatInteractionController.send()` (lines 223-244) compacted the
    // conversation before starting the model request: the 80% hard trigger
    // first, then the 50%-80% soft trigger when the user switch allows it. The
    // request continues afterwards, so the model sees the summarized history.
    co_await AutoCompactBeforeRequest(work, **selected_model, *behavior);
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;

    auto context = co_await dependencies_.memory_context->Prepare(
        current_project_id_, user_text, conversation_id,
        behavior->learning_mode);
    if (!dependencies_.generation->IsCurrent(work.generation_id))
      co_return;
    if (!context) {
      FailAndContinue(work.generation_id,
                      application::CompletionError{
                          .code = application::CompletionErrorCode::transport,
                          .message = context.error().message});
      co_return;
    }

    auto prompt_context = prompt_context_;
    if (dependencies_.todo_state) {
      auto todo = co_await dependencies_.todo_state->Load();
      if (todo)
        prompt_context.todo_state = application::RenderTodoState(*todo);
    }
    prompt_context.learning_context = context->prompt;
    if (dependencies_.skills) {
      auto extensions = co_await dependencies_.skills->BuildExtensionPrompt();
      if (extensions && !extensions->empty()) {
        // Legacy `SystemPromptProvider.build(homePath, tone, chatMode,
        // extensionContext, ...)`: the extension block occupied the slot the
        // template renders as {{LEARNING_CONTEXT}}.
        if (!prompt_context.learning_context.empty())
          prompt_context.learning_context += "\n\n";
        prompt_context.learning_context += *extensions;
      }
    }
    prompt_context.permission_mode =
        domain::SerializeToolPermissionMode(permission_mode_);
    prompt_context.attachment_history.assign(
        dependencies_.session->Messages().begin(),
        dependencies_.session->Messages().end());
    // Legacy `retryableModelStream` reused one request snapshot for every
    // attempt, so the retry loop below does the same.
    const application::CompletionRequest request{
        .model = std::move(**selected_model),
        .messages = std::move(work.messages),
        .tools = {},
        .reasoning_effort = behavior->reasoning,
        .preserve_reasoning = behavior->preserve_reasoning,
        .stream = true,
        .permission_scope = current_project_id_,
    };
    std::optional<application::CompletionResponse> response;
    // Legacy `MAX_RETRIES = 3` / `RETRY_DELAY_MS = 5000`: the first failure
    // announces attempt 2 of 3, the second announces 3 of 3, and the third
    // gives up.
    for (int attempt = 0; attempt < kMaxGenerationAttempts; ++attempt) {
      auto current_response = co_await dependencies_.completion_loop->Complete(
          request, prompt_context,
          application::CompletionObserver{
              .on_event =
                  [this, generation_id = work.generation_id](
                      const application::CompletionEvent &event) {
                    if (dependencies_.generation->Observe(generation_id, event))
                      PublishStreamUpdate(event, generation_id);
                  },
              .on_tool_review = ToolReviewer(),
          });
      if (!dependencies_.generation->IsCurrent(work.generation_id))
        co_return;
      if (current_response) {
        response = std::move(*current_response);
        break;
      }
      const int next_attempt = attempt + 1;
      if (next_attempt >= kMaxGenerationAttempts) {
        auto failure = std::move(current_response.error());
        failure.message =
            FormatModelFailed(retry_labels_.failed, failure.message);
        FailAndContinue(work.generation_id, std::move(failure));
        co_return;
      }
      // The failed attempt leaves nothing behind; the notice below is what the
      // user sees while the request is re-issued.
      static_cast<void>(
          dependencies_.generation->ResetAttempt(work.generation_id));
      domain::ChatMessage notice;
      notice.role = domain::MessageRole::assistant;
      notice.content = FormatRetryNotice(retry_labels_, next_attempt + 1,
                                         current_response.error().message);
      notice.retry_notice = true;
      notice.processing_started_at = NowMilliseconds();
      notice.processing_finished_at = notice.processing_started_at;
      static_cast<void>(
          dependencies_.session->AppendAssistant(std::move(notice)));
      revision_ += 1;
      co_await Delay(kRetryDelay);
      if (!dependencies_.generation->IsCurrent(work.generation_id))
        co_return;
    }
    if (!response)
      co_return;

    if (dependencies_.token_usage)
      dependencies_.token_usage->Record(*response);
    const std::string assistant_text = response->text;
    const bool completed = dependencies_.generation->Complete(
        work.generation_id, std::move(*response));
    revision_ += 1;
    if (!completed)
      co_return;

    turn.messages.push_back(domain::MemoryConversationMessage{
        .id = "generation:" + std::to_string(work.generation_id),
        .role = "assistant",
        .content = assistant_text,
        .timestamp = NowMilliseconds(),
    });
    turn.updated_at = NowMilliseconds();
    StartNext();
    auto committed = co_await dependencies_.memory_context->CommitTurn(
        context->learning_enabled, std::move(turn), user_text);
    if (!committed)
      toast_.Show(committed.error().message);
  }

  void PublishStreamUpdate(const application::CompletionEvent &event,
                           std::uint64_t generation_id) {
    const auto now = std::chrono::steady_clock::now();
    constexpr auto kMinimumInterval = std::chrono::milliseconds{50};
    const bool tool_event =
        std::holds_alternative<application::CompletionToolCallEvent>(event);
    if (tool_event || last_stream_update_ ==
                          std::chrono::steady_clock::time_point{} ||
        now - last_stream_update_ >= kMinimumInterval) {
      last_stream_update_ = now;
      revision_ += 1;
      return;
    }
    if (stream_update_scheduled_)
      return;
    stream_update_scheduled_ = true;
    tasks_.Launch([self = shared_from_this(), generation_id,
                   remaining = kMinimumInterval - (now - last_stream_update_)]()
                      -> Task<void> {
      co_await Delay(remaining);
      if (self->dependencies_.generation->State().generation_id != generation_id)
        co_return;
      self->stream_update_scheduled_ = false;
      if (self->dependencies_.generation->State().phase !=
          application::GenerationPhase::running)
        co_return;
      self->last_stream_update_ = std::chrono::steady_clock::now();
      self->revision_ += 1;
    });
  }

  ChatGenerationDependencies dependencies_;
  std::chrono::steady_clock::time_point last_stream_update_{};
  bool stream_update_scheduled_{};
  RetryLabels retry_labels_;
  std::shared_ptr<application::ToolReviewCoordinator> tool_reviews_;
  std::shared_ptr<application::PendingMessageQueue> pending_messages_;
  TaskScope tasks_;
  State<TaskHandle> active_generation_;
  State<std::size_t> revision_;
  std::string current_project_id_;
  application::PromptAssemblyContext prompt_context_;
  domain::ToolPermissionMode permission_mode_;
  ToastHandle toast_;
};

} // namespace

std::shared_ptr<ChatGenerationRunner> MakeChatGenerationRunner(
    ChatGenerationDependencies dependencies,
    std::shared_ptr<application::ToolReviewCoordinator> tool_reviews,
    std::shared_ptr<application::PendingMessageQueue> pending_messages,
    TaskScope tasks, State<TaskHandle> active_generation,
    State<std::size_t> revision, std::string current_project_id,
    application::PromptAssemblyContext prompt_context,
    domain::ToolPermissionMode permission_mode, ToastHandle toast,
    RetryLabels retry_labels) {
  return std::make_shared<DefaultChatGenerationRunner>(
      std::move(dependencies), std::move(tool_reviews),
      std::move(pending_messages), std::move(tasks),
      std::move(active_generation), std::move(revision),
      std::move(current_project_id), std::move(prompt_context), permission_mode,
      std::move(toast), std::move(retry_labels));
}

} // namespace linecode::presentation
