#include "application/slash_command_catalog.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <iterator>
#include <ranges>
#include <span>
#include <string>
#include <utility>

namespace linecode::application {
namespace {
struct ModeDefinition final {
  std::string_view token;
  std::string_view description;
  domain::ChatMode mode;
};

constexpr std::array kModes{
    ModeDefinition{"/chat", "Switch to chat mode (read-only Q&A).",
                   domain::ChatMode::chat},
    ModeDefinition{"/plan", "Switch to plan mode (read-only planning).",
                   domain::ChatMode::plan},
    ModeDefinition{"/agent", "Switch to agent mode (default execution).",
                   domain::ChatMode::agent},
};

constexpr std::array kReasoning{
    std::pair{std::string_view{"off"}, domain::ReasoningEffort::off},
    std::pair{std::string_view{"auto"}, domain::ReasoningEffort::automatic},
    std::pair{std::string_view{"low"}, domain::ReasoningEffort::low},
    std::pair{std::string_view{"medium"}, domain::ReasoningEffort::medium},
    std::pair{std::string_view{"high"}, domain::ReasoningEffort::high},
    std::pair{std::string_view{"max"}, domain::ReasoningEffort::maximum},
};

std::string Trim(std::string_view value) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

std::string Lower(std::string value) {
  std::ranges::transform(value, value.begin(), [](unsigned char character) {
    return static_cast<char>(std::tolower(character));
  });
  return value;
}

std::pair<std::string, std::string> SplitHead(std::string input) {
  const auto delimiter = std::ranges::find_if(
      input, [](unsigned char character) { return std::isspace(character); });
  if (delimiter == input.end())
    return {Lower(std::move(input)), {}};
  std::string head{input.begin(), delimiter};
  std::string tail{delimiter, input.end()};
  return {Lower(std::move(head)), Trim(tail)};
}

std::vector<std::string_view> SplitPreservingTrailingEmpty(
    std::string_view input) {
  std::vector<std::string_view> tokens;
  std::size_t cursor = 0;
  while (cursor < input.size()) {
    while (cursor < input.size() &&
           std::isspace(static_cast<unsigned char>(input[cursor])) != 0) {
      ++cursor;
    }
    if (cursor == input.size()) {
      tokens.emplace_back();
      break;
    }
    const auto start = cursor;
    while (cursor < input.size() &&
           std::isspace(static_cast<unsigned char>(input[cursor])) == 0) {
      ++cursor;
    }
    tokens.push_back(input.substr(start, cursor - start));
  }
  if (tokens.empty() && !input.empty())
    tokens.push_back(input);
  return tokens;
}

bool ModelMatches(const domain::ModelConfig &model, std::string_view query) {
  if (query.empty())
    return true;
  const auto needle = Lower(std::string{query});
  const auto contains = [&needle](std::string_view value) {
    return Lower(std::string{value}).contains(needle);
  };
  return contains(model.id) || contains(model.name) ||
         contains(model.model_id) || contains(model.provider_label);
}
} // namespace

std::optional<SlashCommand> ParseSlashCommand(std::string_view input) {
  auto trimmed = Trim(input);
  if (trimmed.empty() || trimmed.front() != '/')
    return std::nullopt;
  auto [head, tail] = SplitHead(std::move(trimmed));
  const auto mode = std::ranges::find(kModes, head, &ModeDefinition::token);
  if (mode != kModes.end())
    return ChatModeSlashCommand{.mode = mode->mode};
  if (head != "/model" || tail.empty())
    return std::nullopt;

  const auto delimiter = std::ranges::find_if(
      tail, [](unsigned char character) { return std::isspace(character); });
  std::string model_id{tail.begin(), delimiter};
  std::string reasoning_text =
      delimiter == tail.end()
          ? std::string{}
          : Lower(Trim(std::string_view{delimiter, tail.end()}));
  if (model_id.empty())
    return std::nullopt;
  std::optional<domain::ReasoningEffort> reasoning;
  const auto level = std::ranges::find_if(
      kReasoning, [&reasoning_text](const auto &entry) {
        return entry.first == reasoning_text;
      });
  if (level != kReasoning.end())
    reasoning = level->second;
  return ModelSlashCommand{.model_id = std::move(model_id),
                           .reasoning = reasoning};
}

std::vector<SlashCommandSuggestion>
FilterSlashCommandSuggestions(std::string_view input) {
  auto query = Lower(Trim(input));
  if (query.empty() || query.front() != '/' || query.find_first_of(" \t\r\n") !=
                                                 std::string::npos) {
    return {};
  }
  query.erase(query.begin());
  std::vector<SlashCommandSuggestion> suggestions;
  for (const auto &mode : kModes) {
    const std::string_view token = mode.token.substr(1);
    if (token.starts_with(query)) {
      suggestions.push_back(SlashCommandSuggestion{
          .token = std::string{mode.token},
          .description = std::string{mode.description},
      });
    }
  }
  constexpr std::string_view kModel = "model";
  if (kModel.starts_with(query)) {
    suggestions.push_back(SlashCommandSuggestion{
        .token = "/model",
        .description = "Switch model, optional reasoning level.",
    });
  }
  return suggestions;
}

std::optional<SlashSuggestionState>
ResolveSlashSuggestionState(std::string_view input,
                            std::span<const domain::ModelConfig> models) {
  if (input.empty() || input.front() != '/')
    return std::nullopt;

  const auto tokens = SplitPreservingTrailingEmpty(input);
  if (tokens.empty())
    return std::nullopt;
  const auto head = Lower(std::string{tokens.front()});
  if (head != "/model") {
    auto commands = FilterSlashCommandSuggestions(tokens.front());
    if (commands.empty())
      return std::nullopt;
    return MainSlashSuggestions{.commands = std::move(commands)};
  }

  if (tokens.size() < 2U || tokens[1].empty()) {
    std::vector<std::string> ids;
    ids.reserve(models.size());
    std::ranges::transform(models, std::back_inserter(ids),
                           [](const domain::ModelConfig &model) {
                             return model.id;
                           });
    return ModelSlashSuggestions{.model_ids = std::move(ids)};
  }

  const auto model = std::ranges::find(models, tokens[1],
                                       &domain::ModelConfig::id);
  if (model == models.end()) {
    std::vector<std::string> ids;
    for (const auto &candidate : models) {
      if (ModelMatches(candidate, tokens[1]))
        ids.push_back(candidate.id);
    }
    if (!ids.empty())
      return ModelSlashSuggestions{.model_ids = std::move(ids)};
    return MainSlashSuggestions{
        .commands = FilterSlashCommandSuggestions("/")};
  }

  const auto query = tokens.size() >= 3U ? Lower(std::string{tokens[2]})
                                         : std::string{};
  std::vector<domain::ReasoningEffort> levels;
  for (const auto &[name, effort] : kReasoning) {
    if (name.starts_with(query))
      levels.push_back(effort);
  }
  if (levels.empty())
    return std::nullopt;
  return ReasoningSlashSuggestions{.model_id = model->id,
                                   .levels = std::move(levels)};
}

} // namespace linecode::application
