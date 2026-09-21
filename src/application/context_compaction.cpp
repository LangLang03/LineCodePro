#include "application/context_compaction.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cmath>
#include <ranges>
#include <regex>
#include <stdexcept>
#include <utility>

#include "domain/context_usage.h"
#include "domain/prompt_renderer.h"
#include "domain/prompt_template.h"

namespace linecode::application {
namespace {

// `PromptTemplateRepository.ID_CONTEXT_COMPACTION*`.
constexpr std::string_view kContextCompactionTemplateId = "contextCompaction";
constexpr std::string_view kContextCompactionSummaryPrefixTemplateId =
    "contextCompactionSummaryPrefix";
constexpr std::string_view kContextCompactionResponsesFallbackTemplateId =
    "contextCompactionResponsesFallback";

// `ToolResult.MAX_TOOL_RESULT_CHARS`: per-message transcript content is
// middle-truncated at this size, so the transcript stays bounded while the
// segmentation below still keeps every retained character.
constexpr std::size_t kMaxToolResultChars = 50U * 1024U;

// Legacy `ANALYSIS_PATTERN` / `SUMMARY_PATTERN`. Java compiled them with
// `Pattern.CASE_INSENSITIVE` and a `[\s\S]` class because `.` does not match
// line terminators; ECMAScript `std::regex` behaves the same way, so the
// patterns are copied verbatim and `icase` replaces the Java flag.
const std::regex &AnalysisPattern() {
  static const std::regex pattern(R"(<analysis>[\s\S]*?</analysis>)",
                                  std::regex::icase);
  return pattern;
}

const std::regex &SummaryPattern() {
  static const std::regex pattern(R"(<summary>([\s\S]*?)</summary>)",
                                  std::regex::icase);
  return pattern;
}

[[nodiscard]] std::string Trim(std::string_view value) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

[[nodiscard]] bool IsBlank(std::string_view value) {
  return Trim(value).empty();
}

[[nodiscard]] ContextCompactionError
Error(ContextCompactionErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

// Legacy `message.hasToolCalls() || !message.getToolResults().isEmpty()`: the
// C++ conversation stores both on the assistant timeline.
[[nodiscard]] bool HasToolActivity(const domain::ChatMessage &message) {
  return std::ranges::any_of(message.timeline, [](const auto &event) {
    return std::holds_alternative<domain::AssistantToolEvent>(event);
  });
}

// Legacy `hasCompactableContent` without the `responseInputItemJson` clause,
// which `domain::ChatMessage` cannot carry yet.
[[nodiscard]] bool HasCompactableContent(const domain::ChatMessage &message) {
  return !IsBlank(message.content) || !IsBlank(message.reasoning_content) ||
         HasToolActivity(message);
}

// Legacy `message.isCompactBlock()` and "hidden with a non-empty
// responseInputItemJson" both mark "produced by an earlier compaction". The
// C++ conversation model carries neither column, so `hidden` is the single
// available marker.
[[nodiscard]] bool IsCompactionArtifact(const domain::ChatMessage &message) {
  return message.hidden;
}

// Port of `ToolResult.truncateContent`: keep the first and last half of the
// budget plus an elision marker.
[[nodiscard]] std::string TruncateContent(std::string_view content) {
  if (content.size() <= kMaxToolResultChars)
    return std::string{content};
  const std::size_t half = kMaxToolResultChars / 2U;
  const std::size_t truncated = content.size() - kMaxToolResultChars;
  std::string result;
  result.reserve(kMaxToolResultChars + 48U);
  result.append(content.substr(0U, half));
  result += "\n... (";
  result += std::to_string(truncated);
  result += " chars truncated) ...\n";
  result.append(content.substr(content.size() - half));
  return result;
}

// Legacy `message.getRole().getProtocolName()`.
[[nodiscard]] std::string_view RoleProtocolName(domain::MessageRole role) {
  switch (role) {
  case domain::MessageRole::user:
    return "user";
  case domain::MessageRole::assistant:
    return "assistant";
  case domain::MessageRole::tool:
    return "tool";
  }
  return "user";
}

// Legacy `message.getToolCallId().length() > 0`: only a finished tool call
// carries the result section.
[[nodiscard]] const domain::AssistantToolEvent *
FirstToolResult(const domain::ChatMessage &message) {
  for (const auto &event : message.timeline) {
    const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
    if (tool != nullptr && tool->result.has_value())
      return tool;
  }
  return nullptr;
}

// Legacy `flushSegmentIfNeeded`: the active builder is emptied once it reached
// the segment size, so intermediate segments can be released while the final
// transcript is joined back without losing a character.
void FlushSegmentIfNeeded(std::vector<std::string> &segments,
                          std::string &current) {
  if (current.size() < ContextCompactionService::TRANSCRIPT_SEGMENT_MAX_CHARS)
    return;
  segments.push_back(current);
  current.clear();
}

[[nodiscard]] std::string TemplateText(
    const std::vector<domain::PromptTemplateItem> &items, std::string_view id,
    std::string_view fallback) {
  const auto found =
      std::ranges::find(items, id, [](const domain::PromptTemplateItem &item) {
        return std::string_view{item.definition.id};
      });
  if (found == items.end() || found->current_text.empty())
    return std::string{fallback};
  return found->current_text;
}

[[nodiscard]] std::string_view BuiltInTemplateText(std::string_view id) {
  const auto definitions = domain::BuiltInPromptTemplates();
  const auto found =
      std::ranges::find(definitions, id,
                        [](const domain::PromptTemplateDefinition &definition) {
                          return std::string_view{definition.id};
                        });
  return found == definitions.end() ? std::string_view{}
                                    : std::string_view{found->default_text};
}

[[nodiscard]] CompletionToolCall
ToCompletionToolCall(const domain::ChatToolCall &call) {
  return CompletionToolCall{.id = call.id,
                            .name = call.name,
                            .arguments_json = call.arguments_json};
}

} // namespace

ContextCompactionService::ContextCompactionService(
    std::shared_ptr<CompletionGateway> completion,
    std::shared_ptr<PromptTemplateRepository> prompt_templates,
    std::shared_ptr<ModelStore> models)
    : completion_(std::move(completion)),
      prompt_templates_(std::move(prompt_templates)),
      models_(std::move(models)) {
  if (!completion_ || !prompt_templates_ || !models_)
    throw std::invalid_argument(
        "ContextCompactionService requires completion, template and model "
        "dependencies");
}

bool ContextCompactionService::ShouldCompact(
    const std::vector<domain::ChatMessage> &messages, const int context_tokens,
    const bool include_reasoning) {
  if (messages.empty())
    return false;
  const int maximum = std::max(1, context_tokens);
  const int usage =
      domain::EstimateContextTokens(messages, include_reasoning);
  return static_cast<double>(usage) >=
         static_cast<double>(maximum) * COMPACT_TRIGGER_RATIO;
}

bool ContextCompactionService::ShouldCompact(
    const std::vector<domain::ChatMessage> &messages, const int context_tokens,
    const bool include_reasoning, const int observed_input_tokens) {
  if (observed_input_tokens > 0) {
    return static_cast<double>(observed_input_tokens) >=
           static_cast<double>(std::max(1, context_tokens)) *
               COMPACT_TRIGGER_RATIO;
  }
  return ShouldCompact(messages, context_tokens, include_reasoning);
}

bool ContextCompactionService::ShouldCompact(
    const domain::ModelConfig &model,
    const std::vector<domain::ChatMessage> &messages,
    const bool include_reasoning) {
  return ShouldCompact(messages, domain::ResolveModelContext(model).context_tokens,
                       include_reasoning);
}

bool ContextCompactionService::ShouldCompact(
    const domain::ModelConfig &model,
    const std::vector<domain::ChatMessage> &messages,
    const bool include_reasoning, const int observed_input_tokens) {
  return ShouldCompact(messages, domain::ResolveModelContext(model).context_tokens,
                       include_reasoning, observed_input_tokens);
}

bool ContextCompactionService::ShouldSoftCompact(
    const std::vector<domain::ChatMessage> &messages, const int context_tokens,
    const bool include_reasoning) {
  if (messages.empty())
    return false;
  const int maximum = std::max(1, context_tokens);
  const int usage =
      domain::EstimateContextTokens(messages, include_reasoning);
  if (static_cast<double>(usage) <
      static_cast<double>(maximum) * SOFT_COMPACT_TRIGGER_RATIO) {
    return false;
  }
  // The hard trigger owns everything at or above its own ratio.
  if (static_cast<double>(usage) >=
      static_cast<double>(maximum) * COMPACT_TRIGGER_RATIO) {
    return false;
  }
  return CompactableMessageCount(messages) >=
         SOFT_COMPACT_MIN_COMPACTABLE_MESSAGES;
}

bool ContextCompactionService::ShouldSoftCompact(
    const std::vector<domain::ChatMessage> &messages, const int context_tokens,
    const bool include_reasoning, const int observed_input_tokens) {
  if (observed_input_tokens <= 0)
    return ShouldSoftCompact(messages, context_tokens, include_reasoning);
  const int maximum = std::max(1, context_tokens);
  if (static_cast<double>(observed_input_tokens) <
      static_cast<double>(maximum) * SOFT_COMPACT_TRIGGER_RATIO) {
    return false;
  }
  if (static_cast<double>(observed_input_tokens) >=
      static_cast<double>(maximum) * COMPACT_TRIGGER_RATIO) {
    return false;
  }
  return CompactableMessageCount(messages) >=
         SOFT_COMPACT_MIN_COMPACTABLE_MESSAGES;
}

bool ContextCompactionService::ShouldSoftCompact(
    const domain::ModelConfig &model,
    const std::vector<domain::ChatMessage> &messages,
    const bool include_reasoning) {
  return ShouldSoftCompact(messages,
                           domain::ResolveModelContext(model).context_tokens,
                           include_reasoning);
}

bool ContextCompactionService::ShouldSoftCompact(
    const domain::ModelConfig &model,
    const std::vector<domain::ChatMessage> &messages,
    const bool include_reasoning, const int observed_input_tokens) {
  return ShouldSoftCompact(messages,
                           domain::ResolveModelContext(model).context_tokens,
                           include_reasoning, observed_input_tokens);
}

std::vector<domain::ChatMessage> ContextCompactionService::CompactableMessages(
    const std::vector<domain::ChatMessage> &messages) {
  std::vector<domain::ChatMessage> result;
  result.reserve(messages.size());
  for (const auto &message : messages) {
    if (message.exclude_from_context || IsCompactionArtifact(message) ||
        !HasCompactableContent(message)) {
      continue;
    }
    result.push_back(message);
  }
  return result;
}

std::size_t ContextCompactionService::CompactableMessageCount(
    const std::vector<domain::ChatMessage> &messages) {
  return static_cast<std::size_t>(std::ranges::count_if(
      messages, [](const domain::ChatMessage &message) {
        return !message.exclude_from_context &&
               !IsCompactionArtifact(message) &&
               HasCompactableContent(message);
      }));
}

SoftCompactSplit ContextCompactionService::SplitForSoftCompact(
    const std::vector<domain::ChatMessage> &messages) {
  SoftCompactSplit split;
  const auto compactable = CompactableMessages(messages);
  if (compactable.empty())
    return split;
  const auto tail_size = std::max<std::size_t>(
      1U, static_cast<std::size_t>(std::llround(
              static_cast<double>(compactable.size()) *
              SOFT_COMPACT_TAIL_KEEP_RATIO)));
  const std::size_t split_index =
      compactable.size() > tail_size ? compactable.size() - tail_size : 0U;
  split.head.assign(compactable.begin(),
                    compactable.begin() +
                        static_cast<std::ptrdiff_t>(split_index));
  split.tail.assign(
      compactable.begin() + static_cast<std::ptrdiff_t>(split_index),
      compactable.end());
  return split;
}

std::vector<domain::ChatMessage>
ContextCompactionService::SelectRecentUserMessages(
    const std::vector<domain::ChatMessage> &messages, const int max_tokens) {
  std::vector<domain::ChatMessage> selected;
  if (messages.empty() || max_tokens <= 0)
    return selected;
  int remaining = max_tokens;
  for (auto message = messages.rbegin(); message != messages.rend(); ++message) {
    if (message->exclude_from_context || IsCompactionArtifact(*message))
      continue;
    if (message->role != domain::MessageRole::user)
      continue;
    if (IsBlank(message->content) && IsBlank(message->reasoning_content))
      continue;
    const int tokens = domain::EstimateMessageTokens(*message, true);
    if (tokens > remaining)
      break;
    selected.push_back(*message);
    remaining -= tokens;
  }
  std::ranges::reverse(selected);
  return selected;
}

std::string
ContextCompactionService::BuildTranscript(
    const std::vector<domain::ChatMessage> &messages) {
  std::vector<std::string> segments;
  std::string current;
  for (std::size_t index = 0; index < messages.size(); ++index) {
    const auto &message = messages[index];
    if (!current.empty())
      current += "\n\n---\n\n";
    current += "## ";
    current += std::to_string(index + 1U);
    current += ". ";
    current += RoleProtocolName(message.role);
    if (!IsBlank(message.content)) {
      current += "\n\n";
      current += TruncateContent(message.content);
    }
    if (HasToolActivity(message)) {
      current += "\n\nTool calls:\n";
      for (const auto &event : message.timeline) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        if (tool == nullptr)
          continue;
        current += "- ";
        current += tool->call.name;
        current += ": ";
        current += TruncateContent(tool->call.arguments_json);
        current += '\n';
        FlushSegmentIfNeeded(segments, current);
      }
    }
    // Legacy `message.getToolCallId()`: the C++ conversation keeps the call id
    // and the result text on the assistant timeline.
    if (const auto *tool = FirstToolResult(message); tool != nullptr) {
      current += "\n\nTool result for: ";
      current += tool->call.id;
      if (!IsBlank(tool->result->content)) {
        current += "\n\n";
        current += TruncateContent(tool->result->content);
      }
    }
    if (!IsBlank(message.reasoning_content)) {
      current += "\n\nReasoning:\n";
      current += TruncateContent(message.reasoning_content);
    }
    FlushSegmentIfNeeded(segments, current);
  }
  if (!current.empty())
    segments.push_back(std::move(current));
  std::string transcript;
  for (const auto &segment : segments) {
    transcript += segment;
  }
  return transcript;
}

std::string
ContextCompactionService::FormatCompactSummary(const std::string_view summary) {
  std::string formatted{summary};
  formatted = std::regex_replace(formatted, AnalysisPattern(), "");
  std::smatch match;
  if (std::regex_search(formatted, match, SummaryPattern()) && match.size() > 1U)
    formatted = "Summary:\n" + Trim(match[1].str());
  // Legacy `replaceAll("\\n\\n+", "\n\n")`: runs of two or more newlines
  // collapse to exactly two.
  std::string collapsed;
  collapsed.reserve(formatted.size());
  for (std::size_t index = 0; index < formatted.size(); ++index) {
    const char character = formatted[index];
    if (character != '\n') {
      collapsed += character;
      continue;
    }
    std::size_t run = index;
    while (run < formatted.size() && formatted[run] == '\n')
      ++run;
    collapsed += run - index >= 2U ? "\n\n" : "\n";
    index = run - 1U;
  }
  return Trim(collapsed);
}

std::string ContextCompactionService::RenderCompactSummary(
    const TemplateSet &templates, const std::string_view summary) {
  const std::string formatted = FormatCompactSummary(summary);
  const std::array variables{
      domain::PromptVariable{"SUMMARY", std::string_view{formatted}}};
  return domain::RenderPromptTemplate(templates.summary_prefix, variables);
}

huxerui::Task<std::string>
ContextCompactionService::CreateCompactSummaryContent(std::string summary) const {
  const auto templates = co_await LoadTemplates();
  co_return RenderCompactSummary(templates, summary);
}

huxerui::Task<std::string>
ContextCompactionService::CreateResponsesCompactFallbackContent() const {
  const auto templates = co_await LoadTemplates();
  co_return templates.responses_fallback;
}

// Legacy `compactionRequestOptions`: reasoning off, no preserved reasoning and
// no tools for the compaction request. The transcript is appended to the
// `contextCompaction` prompt exactly like the legacy
// `prompt() + "\n\nConversation transcript:\n" + transcript`.
CompletionRequest ContextCompactionService::SummaryRequest(
    const domain::ModelConfig &model, const TemplateSet &templates,
    const std::vector<domain::ChatMessage> &messages) {
  std::string content = templates.prompt;
  content += "\n\nConversation transcript:\n";
  content += BuildTranscript(messages);
  CompletionRequest request;
  request.model = model;
  request.messages.push_back(CompletionMessage{.role = CompletionRole::user,
                                               .content = std::move(content)});
  request.tools = {};
  request.reasoning_effort = domain::ReasoningEffort::off;
  request.preserve_reasoning = false;
  request.stream = true;
  return request;
}

huxerui::Task<ContextCompactionService::TemplateSet>
ContextCompactionService::LoadTemplates() const {
  TemplateSet templates{
      .prompt = std::string{BuiltInTemplateText(kContextCompactionTemplateId)},
      .summary_prefix = std::string{
          BuiltInTemplateText(kContextCompactionSummaryPrefixTemplateId)},
      .responses_fallback = std::string{
          BuiltInTemplateText(kContextCompactionResponsesFallbackTemplateId)},
  };
  const auto items = co_await prompt_templates_->Load();
  if (items) {
    templates.prompt = TemplateText(*items, kContextCompactionTemplateId,
                                    templates.prompt);
    templates.summary_prefix =
        TemplateText(*items, kContextCompactionSummaryPrefixTemplateId,
                     templates.summary_prefix);
    templates.responses_fallback =
        TemplateText(*items, kContextCompactionResponsesFallbackTemplateId,
                     templates.responses_fallback);
  }
  co_return templates;
}

std::vector<CompletionMessage>
ContextCompactionService::ToResponsesModelMessages(
    const std::vector<domain::ChatMessage> &messages) {
  std::vector<CompletionMessage> result;
  result.reserve(messages.size());
  for (const auto &message : messages) {
    if (message.role == domain::MessageRole::tool) {
      result.push_back(CompletionMessage::Tool(CompletionToolResult{
          .call_id = {},
          .name = {},
          .content = message.content,
          .error = message.error,
      }));
      continue;
    }
    if (message.role == domain::MessageRole::assistant) {
      std::vector<CompletionToolCall> calls;
      for (const auto &event : message.timeline) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        if (tool != nullptr)
          calls.push_back(ToCompletionToolCall(tool->call));
      }
      result.push_back(CompletionMessage::Assistant(
          message.content, std::move(calls), message.reasoning_content));
      // The legacy model stored every tool result as its own TOOL message; the
      // C++ conversation keeps them on the assistant timeline.
      for (const auto &event : message.timeline) {
        const auto *tool = std::get_if<domain::AssistantToolEvent>(&event);
        if (tool == nullptr || !tool->result.has_value())
          continue;
        result.push_back(CompletionMessage::Tool(CompletionToolResult{
            .call_id = tool->result->call_id,
            .name = tool->result->name,
            .content = tool->result->content,
            .error = tool->result->error,
        }));
      }
      continue;
    }
    result.push_back(CompletionMessage{.role = CompletionRole::user,
                                       .content = message.content});
  }
  return result;
}

huxerui::Task<std::expected<CompletionResponse, ContextCompactionError>>
ContextCompactionService::RequestWithRetry(CompletionRequest request,
                                           const std::stop_token cancellation) const {
  int attempt = 0;
  while (true) {
    auto response = co_await completion_->Complete(request, CompletionObserver{});
    if (response)
      co_return std::move(*response);
    // Legacy `streamSummaryWithRetry` stopped retrying once the token was
    // cancelled and rethrew the last failure; `Compact` turns a cancelled
    // failure into the empty cancellation result.
    if (attempt >= MAX_COMPACT_RETRIES || cancellation.stop_requested()) {
      co_return std::unexpected(
          Error(ContextCompactionErrorCode::completion_failed,
                std::move(response.error().message)));
    }
    ++attempt;
    co_await huxerui::Delay(
        std::chrono::milliseconds{COMPACT_RETRY_DELAY_MS * attempt});
  }
}

huxerui::Task<std::expected<domain::ModelConfig, ContextCompactionError>>
ContextCompactionService::ResolveModel(domain::ModelConfig model) const {
  if (!model.model_id.empty())
    co_return model;
  const auto selected_id = co_await models_->SelectedId();
  if (!selected_id) {
    co_return std::unexpected(
        Error(ContextCompactionErrorCode::no_model, selected_id.error().message));
  }
  auto found = co_await models_->Find(*selected_id);
  if (!found) {
    co_return std::unexpected(
        Error(ContextCompactionErrorCode::no_model, found.error().message));
  }
  if (!found->has_value()) {
    co_return std::unexpected(
        Error(ContextCompactionErrorCode::no_model,
              "No model available, cannot compact context"));
  }
  co_return **found;
}

huxerui::Task<std::expected<ContextCompactionResult, ContextCompactionError>>
ContextCompactionService::Compact(domain::ModelConfig model,
                                  std::vector<domain::ChatMessage> messages,
                                  const std::stop_token cancellation) const {
  if (cancellation.stop_requested())
    co_return ContextCompactionResult{};
  auto resolved = co_await ResolveModel(std::move(model));
  if (!resolved)
    co_return std::unexpected(std::move(resolved.error()));
  auto compactable = CompactableMessages(messages);
  if (compactable.empty()) {
    co_return std::unexpected(Error(ContextCompactionErrorCode::nothing_to_compact,
                                    "Context too small, no need to compact"));
  }
  // Every protocol difference is owned by a strategy row; this call only
  // selects one. The legacy `OutOfMemoryError` branch has no C++ counterpart.
  const auto &strategy = SelectStrategy(*resolved);
  auto result = co_await strategy.execute(
      *this, StrategyRequest{.model = std::move(*resolved),
                             .messages = std::move(compactable),
                             .cancellation = cancellation});
  // Cancellation is reported as the empty result rather than as a failure.
  if (!result && cancellation.stop_requested())
    co_return ContextCompactionResult{};
  co_return result;
}

huxerui::Task<std::expected<ContextCompactionResult, ContextCompactionError>>
ContextCompactionService::CompactWithSummary(
    const ContextCompactionService &service, StrategyRequest request) {
  const auto templates = co_await service.LoadTemplates();
  auto response = co_await service.RequestWithRetry(
      SummaryRequest(request.model, templates, request.messages),
      request.cancellation);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (request.cancellation.stop_requested())
    co_return ContextCompactionResult{};
  co_return ContextCompactionResult{
      .summary_content = RenderCompactSummary(templates, response->text),
      .response_input_item_json = {},
  };
}

huxerui::Task<std::expected<ContextCompactionResult, ContextCompactionError>>
ContextCompactionService::CompactWithOpenAiResponsesSummary(
    const ContextCompactionService &service, StrategyRequest request) {
  // Legacy `selectedModel.withModelId(selectedModel.getEffectiveCompressionModelId())`.
  request.model.model_id = request.model.EffectiveCompressionModelId();
  const auto templates = co_await service.LoadTemplates();
  auto response = co_await service.RequestWithRetry(
      SummaryRequest(request.model, templates, request.messages),
      request.cancellation);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (request.cancellation.stop_requested())
    co_return ContextCompactionResult{};
  co_return ContextCompactionResult{
      .summary_content = RenderCompactSummary(templates, response->text),
      .response_input_item_json = {},
  };
}

huxerui::Task<std::expected<ContextCompactionResult, ContextCompactionError>>
ContextCompactionService::CompactWithResponsesApi(
    const ContextCompactionService &service, StrategyRequest request) {
  // The legacy protocol posted the responses input items to
  // `/responses/compact` and used the effective compression model id in the
  // body. Only the completion gateway is available here, so the same input
  // shape is sent as one non-streaming completion request and its reply is
  // treated as the compaction item.
  request.model.model_id = request.model.EffectiveCompressionModelId();
  const auto templates = co_await service.LoadTemplates();
  CompletionRequest completion;
  completion.model = request.model;
  completion.messages = ToResponsesModelMessages(request.messages);
  completion.tools = {};
  completion.reasoning_effort = domain::ReasoningEffort::off;
  completion.preserve_reasoning = false;
  completion.stream = false;
  auto response =
      co_await service.RequestWithRetry(std::move(completion),
                                        request.cancellation);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (request.cancellation.stop_requested())
    co_return ContextCompactionResult{};
  auto item = Trim(response->text);
  if (item.empty()) {
    co_return std::unexpected(
        Error(ContextCompactionErrorCode::missing_compaction_item,
              "OpenAI compaction API did not return a compaction item"));
  }
  co_return ContextCompactionResult{
      .summary_content = templates.responses_fallback,
      .response_input_item_json = std::move(item),
  };
}

std::span<const ContextCompactionService::Strategy>
ContextCompactionService::Strategies() noexcept {
  // Ordered table: the first applicable row wins, and the generic summary row
  // is the always-applicable fallback. Adding a protocol means appending a row.
  static constexpr std::array<Strategy, 3U> kStrategies{{
      Strategy{.id = "responses_compaction",
               .applies = &UsesResponsesCompaction,
               .execute = &CompactWithResponsesApi},
      Strategy{.id = "openai_responses_summary",
               .applies = &UsesOpenAiResponsesSummary,
               .execute = &CompactWithOpenAiResponsesSummary},
      Strategy{.id = "generic_summary",
               .applies = &UsesGenericSummary,
               .execute = &CompactWithSummary},
  }};
  return kStrategies;
}

const ContextCompactionService::Strategy &
ContextCompactionService::SelectStrategy(const domain::ModelConfig &model) noexcept {
  const auto strategies = Strategies();
  for (const auto &strategy : strategies) {
    if (strategy.applies(model))
      return strategy;
  }
  return strategies.back();
}

constexpr bool ContextCompactionService::UsesResponsesCompaction(
    const domain::ModelConfig &model) noexcept {
  // Legacy `shouldUseResponsesCompaction`.
  return model.compression_model_enabled &&
         model.protocol == domain::ModelProtocol::codex_responses;
}

constexpr bool ContextCompactionService::UsesOpenAiResponsesSummary(
    const domain::ModelConfig &model) noexcept {
  // Legacy `shouldUseOpenAiResponsesSummary`.
  return model.compression_model_enabled &&
         model.protocol == domain::ModelProtocol::openai_compatible;
}

constexpr bool ContextCompactionService::UsesGenericSummary(
    const domain::ModelConfig &) noexcept {
  return true;
}

} // namespace linecode::application
