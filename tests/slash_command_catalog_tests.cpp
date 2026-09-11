#include <cassert>
#include <vector>
#include <variant>

#include "application/slash_command_catalog.h"

int main() {
  using namespace linecode;
  auto command = application::ParseSlashCommand(" /PLAN ");
  assert(command);
  assert(std::get<application::ChatModeSlashCommand>(*command).mode ==
         domain::ChatMode::plan);

  command = application::ParseSlashCommand("/control");
  assert(!command);
  command = application::ParseSlashCommand("/model GPT-4o high");
  assert(command);
  const auto &model = std::get<application::ModelSlashCommand>(*command);
  assert(model.model_id == "GPT-4o");
  assert(model.reasoning == domain::ReasoningEffort::high);

  command = application::ParseSlashCommand("/model id invalid");
  assert(command);
  assert(!std::get<application::ModelSlashCommand>(*command).reasoning);
  assert(!application::ParseSlashCommand("hello"));
  assert(!application::ParseSlashCommand("/model"));

  const auto all = application::FilterSlashCommandSuggestions("/");
  assert(all.size() == 4U);
  const auto filtered = application::FilterSlashCommandSuggestions("/a");
  assert(filtered.size() == 1U && filtered.front().token == "/agent");
  assert(application::FilterSlashCommandSuggestions("/agent now").empty());

  domain::ModelConfig primary;
  primary.id = "primary";
  primary.name = "GPT 4o";
  primary.provider_label = "OpenAI";
  primary.model_id = "gpt-4o";
  domain::ModelConfig claude;
  claude.id = "claude";
  claude.name = "Claude";
  claude.provider_label = "Anthropic";
  claude.model_id = "claude-3-7-sonnet";
  const std::vector models{primary, claude};
  auto state = application::ResolveSlashSuggestionState("/model ", models);
  assert(state);
  assert(std::get<application::ModelSlashSuggestions>(*state).model_ids.size() ==
         2U);
  state = application::ResolveSlashSuggestionState("/model gpt", models);
  assert(state);
  assert(std::get<application::ModelSlashSuggestions>(*state).model_ids ==
         std::vector<std::string>{"primary"});
  state = application::ResolveSlashSuggestionState("/model primary h", models);
  assert(state);
  const auto &reasoning =
      std::get<application::ReasoningSlashSuggestions>(*state);
  assert(reasoning.model_id == "primary");
  assert(reasoning.levels ==
         std::vector<domain::ReasoningEffort>{domain::ReasoningEffort::high});
}
