#include <algorithm>
#include <cassert>
#include <string_view>

#include "domain/behavior_settings.h"
#include "domain/prompt_template.h"

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
