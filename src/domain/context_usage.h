#pragma once

#include <string>
#include <string_view>
#include <vector>

#include "domain/app_state.h"
#include "domain/model_config.h"

namespace linecode::domain {

// Context-window size used when neither the model configuration nor the
// legacy `[size]` model-id suffix declares one. Ported from
// `ModelContextParser.DEFAULT_CONTEXT_TOKENS`.
inline constexpr int default_context_tokens = 250'000;

// Local token estimation constants. Ported from `ContextManager`; the product
// deliberately keeps a cheap `characters / 4` estimate instead of a real
// tokenizer.
inline constexpr int characters_per_token = 4;
inline constexpr int message_overhead_tokens = 8;

// Result of resolving a model's context window.
struct ModelContextInfo final {
  // Model id with any legacy `[size]` suffix stripped.
  std::string api_model_id;
  int context_tokens{default_context_tokens};
  // Display label such as "250K" or "1M".
  std::string context_label;

  bool operator==(const ModelContextInfo &) const = default;
};

// Port of `ModelContextParser.parse(ModelConfig)`. Prefers the explicit
// `context_size` field and falls back to the historical `model[128k]` suffix,
// which older configurations still carry.
[[nodiscard]] ModelContextInfo ResolveModelContext(const ModelConfig &model);

// Port of `ModelContextParser.parse(String)` for a bare model id.
[[nodiscard]] ModelContextInfo ResolveModelContext(std::string_view model_id);

// Renders a token count as the legacy short label ("250K", "1M", "4096").
[[nodiscard]] std::string FormatContextLabel(int tokens);

// One message's estimated cost, mirroring `ContextManager.estimateTokens`.
// Messages flagged `exclude_from_context` cost nothing.
[[nodiscard]] int EstimateMessageTokens(const ChatMessage &message,
                                        bool include_reasoning);

// Port of `ContextManager.estimateTokens(List, boolean)`.
[[nodiscard]] int EstimateContextTokens(
    const std::vector<ChatMessage> &messages, bool include_reasoning = true);

// Port of the `ContextSnapshot` value object.
struct ContextSnapshot final {
  int used_tokens{};
  int max_tokens{default_context_tokens};
  // Integer percentage, clamped to [0, 100].
  int percent{};

  bool operator==(const ContextSnapshot &) const = default;
};

// Renders a token count with thousands grouping, matching the legacy
// `NumberFormat.getIntegerInstance()` used by the usage sheet.
[[nodiscard]] std::string FormatGroupedTokens(int tokens);

// Computes the snapshot the header indicator and usage sheet render.
[[nodiscard]] ContextSnapshot SnapshotContext(
    const std::vector<ChatMessage> &messages, int context_tokens,
    bool include_reasoning = true);

} // namespace linecode::domain
