#include "domain/context_usage.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <limits>
#include <ranges>
#include <string>

namespace linecode::domain {
namespace {

std::string Trim(std::string_view text) {
  const auto not_space = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  while (!text.empty() && !not_space(static_cast<unsigned char>(text.front())))
    text.remove_prefix(1U);
  while (!text.empty() && !not_space(static_cast<unsigned char>(text.back())))
    text.remove_suffix(1U);
  return std::string{text};
}

// A trailing "[<number><unit>]" suffix on the model id, matching
// `ModelContextParser.CONTEXT_SUFFIX`.
struct ContextSuffix final {
  bool present{};
  std::size_t start{};
  int tokens{};
};

ContextSuffix ParseSuffix(const std::string_view model_id) {
  const auto close = model_id.rfind(']');
  if (close == std::string_view::npos || close + 1 != model_id.size())
    return {};
  const auto open = model_id.rfind('[', close);
  if (open == std::string_view::npos)
    return {};
  auto body = model_id.substr(open + 1, close - open - 1);
  if (body.empty())
    return {};
  double multiplier = 1.0;
  const char unit = static_cast<char>(
      std::tolower(static_cast<unsigned char>(body.back())));
  if (unit == 'k' || unit == 'm') {
    multiplier = unit == 'm' ? 1'000'000.0 : 1'000.0;
    body.remove_suffix(1U);
  }
  if (body.empty())
    return {};
  std::string digits{body};
  char *end{};
  const double parsed = std::strtod(digits.c_str(), &end);
  if (end != digits.c_str() + digits.size() || !std::isfinite(parsed))
    return {};
  const double tokens = parsed * multiplier;
  if (tokens <= 0.0 || tokens > static_cast<double>(std::numeric_limits<int>::max()))
    return {};
  return ContextSuffix{.present = true,
                       .start = open,
                       .tokens = static_cast<int>(std::llround(tokens))};
}

int EstimateText(const std::string_view text) {
  if (text.empty())
    return 0;
  return std::max(
      1, static_cast<int>(std::ceil(static_cast<double>(text.size()) /
                                    static_cast<double>(characters_per_token))));
}

// Mirrors `ContextManager.isContextMessage`: a message that is excluded from
// context, or that carries no content, reasoning or tool activity, costs
// nothing.
bool IsContextMessage(const ChatMessage &message) {
  if (message.exclude_from_context)
    return false;
  if (!Trim(message.content).empty() || !Trim(message.reasoning_content).empty())
    return true;
  if (message.role == MessageRole::tool)
    return true;
  return std::ranges::any_of(message.timeline, [](const auto &event) {
    const auto *tool = std::get_if<AssistantToolEvent>(&event);
    return tool != nullptr;
  });
}

int EstimateToolCalls(const ChatMessage &message) {
  int total = 0;
  for (const auto &event : message.timeline) {
    const auto *tool = std::get_if<AssistantToolEvent>(&event);
    if (tool == nullptr)
      continue;
    total += EstimateText(tool->call.name) +
             EstimateText(tool->call.arguments_json);
    if (tool->result.has_value())
      total += EstimateText(tool->result->content);
  }
  return total;
}

int EstimateAttachments(const ChatMessage &message) {
  int total = 0;
  for (const auto &attachment : message.attachments) {
    total += EstimateText(attachment.Name()) +
             EstimateText(attachment.Source()) +
             EstimateText(attachment.Path());
  }
  return total;
}

} // namespace

std::string FormatContextLabel(const int tokens) {
  if (tokens <= 0)
    return {};
  if (tokens % 1'000'000 == 0)
    return std::to_string(tokens / 1'000'000) + "M";
  if (tokens % 1'000 == 0)
    return std::to_string(tokens / 1'000) + "K";
  return std::to_string(tokens);
}

ModelContextInfo ResolveModelContext(const std::string_view model_id) {
  const auto trimmed = Trim(model_id);
  const auto suffix = ParseSuffix(trimmed);
  if (!suffix.present) {
    return ModelContextInfo{.api_model_id = trimmed,
                            .context_tokens = default_context_tokens,
                            .context_label =
                                FormatContextLabel(default_context_tokens)};
  }
  auto api_model_id = Trim(std::string_view{trimmed}.substr(0, suffix.start));
  if (api_model_id.empty())
    api_model_id = trimmed;
  return ModelContextInfo{.api_model_id = std::move(api_model_id),
                          .context_tokens = suffix.tokens,
                          .context_label = FormatContextLabel(suffix.tokens)};
}

ModelContextInfo ResolveModelContext(const ModelConfig &model) {
  // The explicit field wins; the `[size]` suffix stays supported for
  // configurations written before the field existed.
  if (model.context_size > ModelConfig::context_size_unset) {
    return ModelContextInfo{.api_model_id = model.model_id,
                            .context_tokens = model.context_size,
                            .context_label =
                                FormatContextLabel(model.context_size)};
  }
  return ResolveModelContext(model.model_id);
}

int EstimateMessageTokens(const ChatMessage &message,
                          const bool include_reasoning) {
  if (!IsContextMessage(message))
    return 0;
  return message_overhead_tokens + EstimateText(message.content) +
         EstimateAttachments(message) +
         (include_reasoning ? EstimateText(message.reasoning_content) : 0) +
         EstimateToolCalls(message);
}

int EstimateContextTokens(const std::vector<ChatMessage> &messages,
                          const bool include_reasoning) {
  int total = 0;
  for (const auto &message : messages)
    total += EstimateMessageTokens(message, include_reasoning);
  return total;
}

ContextSnapshot SnapshotContext(const std::vector<ChatMessage> &messages,
                                const int context_tokens,
                                const bool include_reasoning) {
  const int maximum = std::max(1, context_tokens);
  const int used = EstimateContextTokens(messages, include_reasoning);
  const int percent = static_cast<int>(
      std::lround(static_cast<double>(used) * 100.0 / static_cast<double>(maximum)));
  return ContextSnapshot{.used_tokens = used,
                         .max_tokens = maximum,
                         .percent = std::clamp(percent, 0, 100)};
}

} // namespace linecode::domain
