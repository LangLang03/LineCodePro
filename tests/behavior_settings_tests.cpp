#include <algorithm>
#include <cassert>
#include <string_view>

#include "domain/behavior_settings.h"
#include "domain/prompt_template.h"
#include "presentation/prompt_template_presentation.h"

int main() {
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
  assert(templates.size() == 20);
  assert(templates.front().id == "systemPrompt");
  assert(templates.back().id == "contextCompactionResponsesFallback");
  assert(std::ranges::none_of(templates, [](const auto &item) {
    return item.id == "chatModeControl" || item.source.find("CONTROL") != std::string::npos;
  }));
  assert(std::ranges::all_of(templates, [](const auto &item) {
    return !item.id.empty() && !item.source.empty() && !item.default_text.empty();
  }));
  assert(templates.size() == presentations.size());
  assert(std::ranges::all_of(templates, [&presentations](const auto& item) {
    return FindPromptTemplatePresentation(presentations, item.id) != nullptr;
  }));
  assert(std::ranges::all_of(presentations, [&templates](const auto& item) {
    return std::ranges::find(templates, item.id,
                             &PromptTemplateDefinition::id) != templates.end();
  }));
  assert(std::ranges::count_if(presentations, [](const auto& item) {
           return item.builtin_source.has_value();
         }) == 3);
  assert(templates.front().default_text.find("You are LineCode") != std::string::npos);
  const auto chat = std::ranges::find(templates, std::string_view{"chatModeChat"},
                                      &PromptTemplateDefinition::id);
  assert(chat != templates.end());
  assert(chat->default_text.starts_with("## Current Session Mode\nCurrent mode: Chat."));
  const auto work = std::ranges::find(templates, std::string_view{"workDirectory"},
                                      &PromptTemplateDefinition::id);
  assert(work != templates.end());
  assert(work->variables.size() == 5);
}
