#pragma once

#include <cstdint>
#include <string>

namespace linecode::domain {

enum class AppRoute : std::uint8_t {
  settings,
  tutorial,
  models,
  llm,
  mcp,
  tool_settings,
  extensions,
  input,
  theme,
  output,
  security,
  storage,
  memory,
  data,
  error_logs,
  keep_alive,
  about,
};

// The legacy accessibility-backed Control mode is deliberately absent.
enum class ChatMode : std::uint8_t {
  chat,
  plan,
  agent,
};

enum class MessageRole : std::uint8_t {
  user,
  assistant,
  tool,
};

struct ChatMessage final {
  std::uint64_t id{};
  MessageRole role{MessageRole::user};
  std::string content;

  bool operator==(const ChatMessage&) const = default;
};

} // namespace linecode::domain
