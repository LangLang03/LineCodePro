#pragma once

#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <variant>
#include <vector>

#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "domain/model_config.h"

namespace linecode::application {

struct ChatModeSlashCommand final {
  domain::ChatMode mode{domain::ChatMode::agent};

  bool operator==(const ChatModeSlashCommand &) const = default;
};

struct ModelSlashCommand final {
  std::string model_id;
  std::optional<domain::ReasoningEffort> reasoning;

  bool operator==(const ModelSlashCommand &) const = default;
};

using SlashCommand = std::variant<ChatModeSlashCommand, ModelSlashCommand>;

struct SlashCommandSuggestion final {
  std::string token;
  std::string description;

  bool operator==(const SlashCommandSuggestion &) const = default;
};

struct MainSlashSuggestions final {
  std::vector<SlashCommandSuggestion> commands;

  bool operator==(const MainSlashSuggestions &) const = default;
};

struct ModelSlashSuggestions final {
  std::vector<std::string> model_ids;

  bool operator==(const ModelSlashSuggestions &) const = default;
};

struct ReasoningSlashSuggestions final {
  std::string model_id;
  std::vector<domain::ReasoningEffort> levels;

  bool operator==(const ReasoningSlashSuggestions &) const = default;
};

using SlashSuggestionState =
    std::variant<MainSlashSuggestions, ModelSlashSuggestions,
                 ReasoningSlashSuggestions>;

[[nodiscard]] std::optional<SlashCommand>
ParseSlashCommand(std::string_view input);

[[nodiscard]] std::vector<SlashCommandSuggestion>
FilterSlashCommandSuggestions(std::string_view input);

[[nodiscard]] std::optional<SlashSuggestionState>
ResolveSlashSuggestionState(std::string_view input,
                            std::span<const domain::ModelConfig> models);

} // namespace linecode::application
