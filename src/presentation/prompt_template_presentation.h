#pragma once

#include <array>
#include <cstdint>
#include <functional>
#include <optional>
#include <ranges>
#include <string_view>
#include <type_traits>
#include <utility>

namespace linecode::presentation {

enum class PromptTemplateText : std::uint8_t {
  system_prompt_title,
  system_prompt_description,
  work_directory_title,
  work_directory_description,
  tone_coding_title,
  tone_coding_description,
  tone_chat_title,
  tone_chat_description,
  chat_mode_chat_title,
  chat_mode_chat_description,
  chat_mode_plan_title,
  chat_mode_plan_description,
  chat_mode_agent_title,
  chat_mode_agent_description,
  learning_context_title,
  learning_context_description,
  context_compaction_title,
  context_compaction_description,
  model_identity_title,
  model_identity_description,
  todo_state_title,
  todo_state_description,
  todo_usage_title,
  todo_usage_description,
  agent_role_explore_remote_title,
  agent_role_explore_remote_description,
  agent_role_coding_remote_title,
  agent_role_coding_remote_description,
  agent_role_explore_local_title,
  agent_role_explore_local_description,
  agent_role_coding_local_title,
  agent_role_coding_local_description,
  agent_system_prompt_title,
  agent_system_prompt_description,
  image_understanding_tool_system_title,
  image_understanding_tool_system_description,
  context_compaction_summary_prefix_title,
  context_compaction_summary_prefix_description,
  context_compaction_responses_fallback_title,
  context_compaction_responses_fallback_description,
  source_builtin_chat,
  source_builtin_plan,
  source_builtin_agent,
  count,
};

template <typename Resource> struct PromptTemplatePresentation final {
  std::string_view id;
  Resource title;
  Resource description;
  std::optional<Resource> builtin_source;
};

template <typename Resolver>
  requires std::invocable<Resolver, PromptTemplateText>
[[nodiscard]] constexpr auto
MakePromptTemplatePresentationRegistry(Resolver&& resolve) {
  using Resource =
      std::remove_cvref_t<std::invoke_result_t<Resolver, PromptTemplateText>>;
  using Presentation = PromptTemplatePresentation<Resource>;
  const auto text = [&resolve](PromptTemplateText key) {
    return std::invoke(resolve, key);
  };
  const auto source = [&text](PromptTemplateText key) {
    return std::optional<Resource>{text(key)};
  };

  return std::array{
      Presentation{"systemPrompt", text(PromptTemplateText::system_prompt_title),
                   text(PromptTemplateText::system_prompt_description),
                   std::nullopt},
      Presentation{"workDirectory",
                   text(PromptTemplateText::work_directory_title),
                   text(PromptTemplateText::work_directory_description),
                   std::nullopt},
      Presentation{"toneCoding", text(PromptTemplateText::tone_coding_title),
                   text(PromptTemplateText::tone_coding_description),
                   std::nullopt},
      Presentation{"toneChat", text(PromptTemplateText::tone_chat_title),
                   text(PromptTemplateText::tone_chat_description),
                   std::nullopt},
      Presentation{"chatModeChat",
                   text(PromptTemplateText::chat_mode_chat_title),
                   text(PromptTemplateText::chat_mode_chat_description),
                   source(PromptTemplateText::source_builtin_chat)},
      Presentation{"chatModePlan",
                   text(PromptTemplateText::chat_mode_plan_title),
                   text(PromptTemplateText::chat_mode_plan_description),
                   source(PromptTemplateText::source_builtin_plan)},
      Presentation{"chatModeAgent",
                   text(PromptTemplateText::chat_mode_agent_title),
                   text(PromptTemplateText::chat_mode_agent_description),
                   source(PromptTemplateText::source_builtin_agent)},
      Presentation{"learningContext",
                   text(PromptTemplateText::learning_context_title),
                   text(PromptTemplateText::learning_context_description),
                   std::nullopt},
      Presentation{"contextCompaction",
                   text(PromptTemplateText::context_compaction_title),
                   text(PromptTemplateText::context_compaction_description),
                   std::nullopt},
      Presentation{"modelIdentity",
                   text(PromptTemplateText::model_identity_title),
                   text(PromptTemplateText::model_identity_description),
                   std::nullopt},
      Presentation{"todoState", text(PromptTemplateText::todo_state_title),
                   text(PromptTemplateText::todo_state_description),
                   std::nullopt},
      Presentation{"todoUsage", text(PromptTemplateText::todo_usage_title),
                   text(PromptTemplateText::todo_usage_description),
                   std::nullopt},
      Presentation{
          "agentRoleExploreRemote",
          text(PromptTemplateText::agent_role_explore_remote_title),
          text(PromptTemplateText::agent_role_explore_remote_description),
          std::nullopt},
      Presentation{
          "agentRoleCodingRemote",
          text(PromptTemplateText::agent_role_coding_remote_title),
          text(PromptTemplateText::agent_role_coding_remote_description),
          std::nullopt},
      Presentation{
          "agentRoleExploreLocal",
          text(PromptTemplateText::agent_role_explore_local_title),
          text(PromptTemplateText::agent_role_explore_local_description),
          std::nullopt},
      Presentation{
          "agentRoleCodingLocal",
          text(PromptTemplateText::agent_role_coding_local_title),
          text(PromptTemplateText::agent_role_coding_local_description),
          std::nullopt},
      Presentation{"agentSystemPrompt",
                   text(PromptTemplateText::agent_system_prompt_title),
                   text(PromptTemplateText::agent_system_prompt_description),
                   std::nullopt},
      Presentation{
          "imageUnderstandingToolSystem",
          text(PromptTemplateText::image_understanding_tool_system_title),
          text(PromptTemplateText::image_understanding_tool_system_description),
          std::nullopt},
      Presentation{
          "contextCompactionSummaryPrefix",
          text(PromptTemplateText::context_compaction_summary_prefix_title),
          text(PromptTemplateText::context_compaction_summary_prefix_description),
          std::nullopt},
      Presentation{
          "contextCompactionResponsesFallback",
          text(PromptTemplateText::context_compaction_responses_fallback_title),
          text(PromptTemplateText::context_compaction_responses_fallback_description),
          std::nullopt},
  };
}

template <typename Resource, std::size_t Size>
[[nodiscard]] constexpr const PromptTemplatePresentation<Resource>*
FindPromptTemplatePresentation(
    const std::array<PromptTemplatePresentation<Resource>, Size>& registry,
    std::string_view id) noexcept {
  const auto found = std::ranges::find(registry, id,
                                       &PromptTemplatePresentation<Resource>::id);
  return found == registry.end() ? nullptr : &*found;
}

[[nodiscard]] constexpr std::size_t PromptTemplateTextCount() noexcept {
  return std::to_underlying(PromptTemplateText::count);
}

} // namespace linecode::presentation
