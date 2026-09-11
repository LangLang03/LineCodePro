#pragma once

#include <cstddef>
#include <cstdint>
#include <expected>
#include <memory>
#include <span>
#include <stop_token>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/completion_gateway.h"
#include "application/ports/model_store.h"
#include "application/prompt_template_repository.h"
#include "domain/app_state.h"
#include "domain/model_config.h"

namespace linecode::application {

// Port of `cn.lineai.context.ContextCompactionResult`: the text written back
// into the conversation plus the opaque Responses compaction item the Codex /
// OpenAI Responses compact API returned (empty for the summary protocols).
struct ContextCompactionResult final {
  // `getSummaryContent()`.
  std::string summary_content;
  // `getResponseInputItemJson()`; the legacy value object trims this field.
  std::string response_input_item_json;

  // True when the compaction produced nothing: either the caller cancelled the
  // run (the legacy service returned `("", "")` while cancelled) or no strategy
  // wrote a summary.
  [[nodiscard]] bool Empty() const noexcept {
    return summary_content.empty() && response_input_item_json.empty();
  }

  bool operator==(const ContextCompactionResult &) const = default;
};

enum class ContextCompactionErrorCode : std::uint8_t {
  // Legacy: "No model available, cannot compact context".
  no_model,
  // Legacy: "Context too small, no need to compact".
  nothing_to_compact,
  // The summary / compaction request failed after the legacy retry budget.
  completion_failed,
  // Legacy: "OpenAI compaction API did not return a compaction item".
  missing_compaction_item,
};

struct ContextCompactionError final {
  ContextCompactionErrorCode code{ContextCompactionErrorCode::completion_failed};
  std::string message;

  bool operator==(const ContextCompactionError &) const = default;
};

// Port of the two-element list `splitForSoftCompact` returned: `head` is the
// older slice that gets summarised, `tail` stays verbatim.
struct SoftCompactSplit final {
  std::vector<domain::ChatMessage> head;
  std::vector<domain::ChatMessage> tail;

  bool operator==(const SoftCompactSplit &) const = default;
};

// Port of `cn.lineai.context.ContextCompactionService`. Dependencies are
// injected: the completion gateway performs every model request, the prompt
// template repository supplies the user-editable compaction templates, and the
// model store resolves the currently selected model when the caller has none.
class ContextCompactionService final {
public:
  // `ContextCompactionService.COMPACT_TRIGGER_RATIO` (hard trigger, 80%).
  static constexpr double COMPACT_TRIGGER_RATIO = 0.8;
  // `ContextCompactionService.SOFT_COMPACT_TRIGGER_RATIO` (soft trigger, 50%).
  static constexpr double SOFT_COMPACT_TRIGGER_RATIO = 0.5;
  // `ContextCompactionService.SOFT_COMPACT_TAIL_KEEP_RATIO`: the soft trigger
  // keeps the most recent 30% of the compactable messages.
  static constexpr double SOFT_COMPACT_TAIL_KEEP_RATIO = 0.3;
  // `ContextCompactionService.TRANSCRIPT_SEGMENT_MAX_CHARS`: the transcript is
  // accumulated in segments of this size and joined without losing content.
  static constexpr std::size_t TRANSCRIPT_SEGMENT_MAX_CHARS = 256U * 1024U;
  // `ContextCompactionService.COMPACT_USER_MESSAGE_MAX_TOKENS`.
  static constexpr int COMPACT_USER_MESSAGE_MAX_TOKENS = 20'000;
  // `ContextCompactionService.MAX_COMPACT_RETRIES`.
  static constexpr int MAX_COMPACT_RETRIES = 2;
  // `ContextCompactionService.COMPACT_RETRY_DELAY_MS`.
  static constexpr std::int64_t COMPACT_RETRY_DELAY_MS = 1'000;
  // `shouldSoftCompact` only fires with at least this many compactable
  // messages (`compactableMessageCount(messages) >= 8`).
  static constexpr std::size_t SOFT_COMPACT_MIN_COMPACTABLE_MESSAGES = 8U;

  ContextCompactionService(std::shared_ptr<CompletionGateway> completion,
                           std::shared_ptr<PromptTemplateRepository>
                               prompt_templates,
                           std::shared_ptr<ModelStore> models);

  // Port of `compact(ModelConfig, List, ModelCancellationToken)`.
  //
  // `model.model_id` may be empty; the injected model store then resolves the
  // selected model, matching the legacy controller that failed with "No model
  // available, cannot compact context" before calling the service.
  //
  // A cancelled `std::stop_token` never produces an error: the call returns an
  // empty result (`Empty() == true`), exactly like the legacy `("", "")`
  // cancellation result.
  [[nodiscard]] huxerui::Task<
      std::expected<ContextCompactionResult, ContextCompactionError>>
  Compact(domain::ModelConfig model, std::vector<domain::ChatMessage> messages,
          std::stop_token cancellation = {}) const;

  // Port of the two `shouldCompact` overloads. The four-argument overload
  // prefers the server-observed input tokens and falls back to the local
  // estimate when none were observed.
  [[nodiscard]] static bool
  ShouldCompact(const std::vector<domain::ChatMessage> &messages,
                int context_tokens, bool include_reasoning = true);
  [[nodiscard]] static bool
  ShouldCompact(const std::vector<domain::ChatMessage> &messages,
                int context_tokens, bool include_reasoning,
                int observed_input_tokens);
  // Convenience overloads resolving the window through
  // `domain::ResolveModelContext`, so callers reuse the ported parser.
  [[nodiscard]] static bool
  ShouldCompact(const domain::ModelConfig &model,
                const std::vector<domain::ChatMessage> &messages,
                bool include_reasoning = true);
  [[nodiscard]] static bool
  ShouldCompact(const domain::ModelConfig &model,
                const std::vector<domain::ChatMessage> &messages,
                bool include_reasoning, int observed_input_tokens);

  // Port of the two `shouldSoftCompact` overloads.
  [[nodiscard]] static bool
  ShouldSoftCompact(const std::vector<domain::ChatMessage> &messages,
                    int context_tokens, bool include_reasoning = true);
  [[nodiscard]] static bool
  ShouldSoftCompact(const std::vector<domain::ChatMessage> &messages,
                    int context_tokens, bool include_reasoning,
                    int observed_input_tokens);
  [[nodiscard]] static bool
  ShouldSoftCompact(const domain::ModelConfig &model,
                    const std::vector<domain::ChatMessage> &messages,
                    bool include_reasoning = true);
  [[nodiscard]] static bool
  ShouldSoftCompact(const domain::ModelConfig &model,
                    const std::vector<domain::ChatMessage> &messages,
                    bool include_reasoning, int observed_input_tokens);

  // Port of `compactableMessages`.
  //
  // The legacy filters `isCompactBlock()` and "hidden with a non-empty
  // responseInputItemJson" have no column in `domain::ChatMessage` yet, so the
  // port treats every `hidden` message as a compaction artifact: a caller that
  // writes a compaction summary back into the session must mark it `hidden`
  // once the conversation model can carry that marker.
  [[nodiscard]] static std::vector<domain::ChatMessage>
  CompactableMessages(const std::vector<domain::ChatMessage> &messages);
  // Port of `compactableMessageCount`.
  [[nodiscard]] static std::size_t
  CompactableMessageCount(const std::vector<domain::ChatMessage> &messages);
  // Port of `splitForSoftCompact`.
  [[nodiscard]] static SoftCompactSplit
  SplitForSoftCompact(const std::vector<domain::ChatMessage> &messages);
  // Port of `selectRecentUserMessages`.
  [[nodiscard]] static std::vector<domain::ChatMessage>
  SelectRecentUserMessages(const std::vector<domain::ChatMessage> &messages,
                           int max_tokens);
  // Port of `buildTranscript`.
  [[nodiscard]] static std::string
  BuildTranscript(const std::vector<domain::ChatMessage> &messages);

  // Port of `createCompactSummaryContent`: `<analysis>` blocks are dropped, the
  // first `<summary>` body (case-insensitive) is kept, and the result is
  // rendered into the `contextCompactionSummaryPrefix` template.
  [[nodiscard]] huxerui::Task<std::string>
  CreateCompactSummaryContent(std::string summary) const;
  // Port of `createResponsesCompactFallbackContent`.
  [[nodiscard]] huxerui::Task<std::string>
  CreateResponsesCompactFallbackContent() const;

  // Declarative protocol strategy table. Every protocol difference lives in a
  // row: `applies` decides whether the row owns a model and `execute` performs
  // it. `Compact()` only asks the table which row applies, so a new protocol or
  // compaction API is added by appending a row instead of editing a branch.
  struct StrategyRequest final {
    domain::ModelConfig model;
    // Already filtered through `CompactableMessages`.
    std::vector<domain::ChatMessage> messages;
    std::stop_token cancellation;
  };

  struct Strategy final {
    std::string_view id;
    bool (*applies)(const domain::ModelConfig &model) noexcept;
    huxerui::Task<
        std::expected<ContextCompactionResult, ContextCompactionError>> (
        *execute)(const ContextCompactionService &service,
                  StrategyRequest request);
  };

  [[nodiscard]] static std::span<const Strategy> Strategies() noexcept;
  // First applicable row; the generic summary row always applies, so the
  // result is never null.
  [[nodiscard]] static const Strategy &
  SelectStrategy(const domain::ModelConfig &model) noexcept;

private:
  struct TemplateSet final {
    std::string prompt;
    std::string summary_prefix;
    std::string responses_fallback;
  };

  [[nodiscard]] huxerui::Task<
      std::expected<domain::ModelConfig, ContextCompactionError>>
  ResolveModel(domain::ModelConfig model) const;
  // Loads the three compaction templates, falling back to the built-in text
  // when the settings store has no stored override.
  [[nodiscard]] huxerui::Task<TemplateSet> LoadTemplates() const;
  // Builds `<contextCompaction prompt>\n\nConversation transcript:\n<text>`
  // with reasoning off and no tools, matching `compactionRequestOptions`.
  [[nodiscard]] static CompletionRequest
  SummaryRequest(const domain::ModelConfig &model, const TemplateSet &templates,
                 const std::vector<domain::ChatMessage> &messages);
  // Port of `formatCompactSummary`.
  [[nodiscard]] static std::string
  FormatCompactSummary(std::string_view summary);
  [[nodiscard]] static std::string
  RenderCompactSummary(const TemplateSet &templates,
                       std::string_view summary);
  // Port of `toResponsesModelMessage`.
  [[nodiscard]] static std::vector<CompletionMessage>
  ToResponsesModelMessages(const std::vector<domain::ChatMessage> &messages);
  // Port of `streamSummaryWithRetry` / `compactResponsesItemWithRetry`.
  [[nodiscard]] huxerui::Task<
      std::expected<CompletionResponse, ContextCompactionError>>
  RequestWithRetry(CompletionRequest request,
                   std::stop_token cancellation) const;

  // Strategy executables, ported from the private legacy methods.
  [[nodiscard]] static huxerui::Task<
      std::expected<ContextCompactionResult, ContextCompactionError>>
  CompactWithResponsesApi(const ContextCompactionService &service,
                          StrategyRequest request);
  [[nodiscard]] static huxerui::Task<
      std::expected<ContextCompactionResult, ContextCompactionError>>
  CompactWithOpenAiResponsesSummary(const ContextCompactionService &service,
                                    StrategyRequest request);
  [[nodiscard]] static huxerui::Task<
      std::expected<ContextCompactionResult, ContextCompactionError>>
  CompactWithSummary(const ContextCompactionService &service,
                     StrategyRequest request);

  // Strategy applicability predicates, ported from
  // `shouldUseResponsesCompaction` / `shouldUseOpenAiResponsesSummary`.
  [[nodiscard]] static constexpr bool
  UsesResponsesCompaction(const domain::ModelConfig &model) noexcept;
  [[nodiscard]] static constexpr bool
  UsesOpenAiResponsesSummary(const domain::ModelConfig &model) noexcept;
  [[nodiscard]] static constexpr bool
  UsesGenericSummary(const domain::ModelConfig &model) noexcept;

  std::shared_ptr<CompletionGateway> completion_;
  std::shared_ptr<PromptTemplateRepository> prompt_templates_;
  std::shared_ptr<ModelStore> models_;
};

} // namespace linecode::application
