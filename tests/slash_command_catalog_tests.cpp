#include "gtest_support.h"
#include <vector>
#include <variant>

#include "application/slash_command_catalog.h"

TEST(slash_command_catalog_tests, LegacySuite) {
  using namespace linecode;
  auto command = application::ParseSlashCommand(" /PLAN ");
  EXPECT_EXPRESSION(command);
  EXPECT_EXPRESSION(std::get<application::ChatModeSlashCommand>(*command).mode ==
         domain::ChatMode::plan);

  command = application::ParseSlashCommand("/control");
  EXPECT_EXPRESSION(!command);
  command = application::ParseSlashCommand("/model GPT-4o high");
  EXPECT_EXPRESSION(command);
  const auto &model = std::get<application::ModelSlashCommand>(*command);
  EXPECT_EXPRESSION(model.model_id == "GPT-4o");
  EXPECT_EXPRESSION(model.reasoning == domain::ReasoningEffort::high);

  command = application::ParseSlashCommand("/model id invalid");
  EXPECT_EXPRESSION(command);
  EXPECT_EXPRESSION(!std::get<application::ModelSlashCommand>(*command).reasoning);
  EXPECT_EXPRESSION(!application::ParseSlashCommand("hello"));
  EXPECT_EXPRESSION(!application::ParseSlashCommand("/model"));

  const auto all = application::FilterSlashCommandSuggestions("/");
  EXPECT_EXPRESSION(all.size() == 4U);
  const auto filtered = application::FilterSlashCommandSuggestions("/a");
  EXPECT_EXPRESSION(filtered.size() == 1U && filtered.front().token == "/agent");
  EXPECT_EXPRESSION(application::FilterSlashCommandSuggestions("/agent now").empty());

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
  EXPECT_EXPRESSION(state);
  EXPECT_EXPRESSION(std::get<application::ModelSlashSuggestions>(*state).model_ids.size() ==
         2U);
  state = application::ResolveSlashSuggestionState("/model gpt", models);
  EXPECT_EXPRESSION(state);
  EXPECT_EXPRESSION(std::get<application::ModelSlashSuggestions>(*state).model_ids ==
         std::vector<std::string>{"primary"});
  state = application::ResolveSlashSuggestionState("/model primary h", models);
  EXPECT_EXPRESSION(state);
  const auto &reasoning =
      std::get<application::ReasoningSlashSuggestions>(*state);
  EXPECT_EXPRESSION(reasoning.model_id == "primary");
  EXPECT_EXPRESSION(reasoning.levels ==
         std::vector<domain::ReasoningEffort>{domain::ReasoningEffort::high});
}
