#pragma once

#include <cstdint>
#include <string>
#include <utility>
#include <variant>

namespace linecode::domain {

struct BrowserRoute final {
  std::string url;
  bool java_script_enabled = false;

  bool operator==(const BrowserRoute &) const = default;
};

class AppRoute final {
public:
  enum Page : std::uint8_t {
    settings,
    tutorial,
    models,
    llm,
    prompt_templates,
    mcp,
    tool_settings,
    extensions,
    input,
    theme,
    output,
    tool_call_preview,
    security,
    storage,
    memory,
    data,
    error_logs,
    keep_alive,
    about,
    licenses,
  };

  constexpr AppRoute() noexcept = default;
  constexpr AppRoute(Page page) noexcept : value_(page) {}
  explicit AppRoute(BrowserRoute browser) : value_(std::move(browser)) {}

  [[nodiscard]] static AppRoute Browser(std::string url,
                                        bool java_script_enabled = false) {
    return AppRoute(BrowserRoute{.url = std::move(url),
                                 .java_script_enabled = java_script_enabled});
  }

  [[nodiscard]] const BrowserRoute *BrowserValue() const noexcept {
    return std::get_if<BrowserRoute>(&value_);
  }

  [[nodiscard]] const Page *PageValue() const noexcept {
    return std::get_if<Page>(&value_);
  }

  [[nodiscard]] bool operator==(Page page) const noexcept {
    const auto *current = std::get_if<Page>(&value_);
    return current != nullptr && *current == page;
  }

  friend bool operator==(Page page, const AppRoute &route) noexcept {
    return route == page;
  }

  bool operator==(const AppRoute &) const = default;

private:
  std::variant<Page, BrowserRoute> value_{settings};
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

  bool operator==(const ChatMessage &) const = default;
};

} // namespace linecode::domain
