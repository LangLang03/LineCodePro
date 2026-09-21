#include <algorithm>
#include "gtest_support.h"
#include <string_view>

#include "domain/behavior_settings.h"
#include "domain/prompt_template.h"
#include "presentation/prompt_template_presentation.h"

TEST(behavior_settings_tests, LegacySuite) {
  using namespace linecode::domain;

  static_assert(ParseToneMode("chat") == ToneMode::chat);
  static_assert(ParseToneMode("unexpected") == ToneMode::coding);
  static_assert(SerializeToneMode(ToneMode::coding) == "coding");
  static_assert(ParseReasoningEffort("off") == ReasoningEffort::off);
  static_assert(ParseReasoningEffort("auto") == ReasoningEffort::automatic);
  static_assert(ParseReasoningEffort("low") == ReasoningEffort::low);
  static_assert(ParseReasoningEffort("medium") == ReasoningEffort::medium);
  static_assert(ParseReasoningEffort("high") == ReasoningEffort::high);
  static_assert(ParseReasoningEffort("max") == ReasoningEffort::maximum);
  static_assert(ParseReasoningEffort("unexpected") == ReasoningEffort::medium);
  static_assert(ParseEnterKeyBehavior("newline") == EnterKeyBehavior::newline);
  static_assert(ParseEnterKeyBehavior("unexpected") == EnterKeyBehavior::send);
  static_assert(SerializeEnterKeyBehavior(EnterKeyBehavior::send) == "send");
  static_assert(SerializeEnterKeyBehavior(EnterKeyBehavior::newline) ==
                "newline");

  using namespace linecode::presentation;
  constexpr auto presentations = MakePromptTemplatePresentationRegistry(
      [](PromptTemplateText text) { return text; });
  static_assert(presentations.size() == 20);
  static_assert(FindPromptTemplatePresentation(presentations, "missing") ==
                nullptr);
  static_assert(
      FindPromptTemplatePresentation(presentations, "chatModeChat")
          ->builtin_source == PromptTemplateText::source_builtin_chat);
  static_assert(
      FindPromptTemplatePresentation(presentations, "chatModePlan")
          ->builtin_source == PromptTemplateText::source_builtin_plan);
  static_assert(
      FindPromptTemplatePresentation(presentations, "chatModeAgent")
          ->builtin_source == PromptTemplateText::source_builtin_agent);

  const auto templates = BuiltInPromptTemplates();
  EXPECT_EXPRESSION(templates.size() == 20);
  EXPECT_EXPRESSION(templates.front().id == "systemPrompt");
  EXPECT_EXPRESSION(templates.back().id == "contextCompactionResponsesFallback");
  EXPECT_EXPRESSION(std::ranges::none_of(templates, [](const auto &item) {
    return item.id == "chatModeControl" || item.source.find("CONTROL") != std::string::npos;
  }));
  EXPECT_EXPRESSION(std::ranges::all_of(templates, [](const auto &item) {
    return !item.id.empty() && !item.source.empty() && !item.default_text.empty();
  }));
  EXPECT_EXPRESSION(templates.size() == presentations.size());
  EXPECT_EXPRESSION(std::ranges::all_of(templates, [&presentations](const auto& item) {
    return FindPromptTemplatePresentation(presentations, item.id) != nullptr;
  }));
  EXPECT_EXPRESSION(std::ranges::all_of(presentations, [&templates](const auto& item) {
    return std::ranges::find(templates, item.id,
                             &PromptTemplateDefinition::id) != templates.end();
  }));
  EXPECT_EXPRESSION(std::ranges::count_if(presentations, [](const auto& item) {
           return item.builtin_source.has_value();
         }) == 3);
  EXPECT_EXPRESSION(templates.front().default_text.find("You are LineCode") != std::string::npos);
  const auto chat = std::ranges::find(templates, std::string_view{"chatModeChat"},
                                      &PromptTemplateDefinition::id);
  EXPECT_EXPRESSION(chat != templates.end());
  EXPECT_EXPRESSION(chat->default_text.starts_with("## Current Session Mode\nCurrent mode: Chat."));
  const auto work = std::ranges::find(templates, std::string_view{"workDirectory"},
                                      &PromptTemplateDefinition::id);
  EXPECT_EXPRESSION(work != templates.end());
  EXPECT_EXPRESSION(work->variables.size() == 5);
}
