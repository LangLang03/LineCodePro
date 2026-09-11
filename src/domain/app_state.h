#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <variant>
#include <vector>

#include "domain/extension_kind.h"
#include "domain/chat_timeline.h"
#include "domain/input_attachment.h"
#include "domain/skill_hub_route.h"
#include "domain/tool_settings.h"

namespace linecode::domain {

struct BrowserRoute final {
  std::string url;
  bool java_script_enabled = false;

  bool operator==(const BrowserRoute &) const = default;
};

struct ImageModelPickerRoute final {
  ImageModelPurpose purpose{ImageModelPurpose::understanding};

  bool operator==(const ImageModelPickerRoute &) const = default;
};

struct ShellCommandRoute final {
  std::string command;

  bool operator==(const ShellCommandRoute &) const = default;
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
    ssh_settings,
    termux_integration,
    tool_settings,
    extensions,
    terminal_provider,
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
    skill_store,
    skill_hub_login,
    skill_hub_center,
    skill_hub_publish,
  };

  constexpr AppRoute() noexcept = default;
  constexpr AppRoute(Page page) noexcept : value_(page) {}
  explicit AppRoute(BrowserRoute browser) : value_(std::move(browser)) {}

  [[nodiscard]] static AppRoute Browser(std::string url,
                                        bool java_script_enabled = false) {
    return AppRoute(BrowserRoute{.url = std::move(url),
                                 .java_script_enabled = java_script_enabled});
  }

  [[nodiscard]] static AppRoute ImageModelPicker(ImageModelPurpose purpose) {
    return AppRoute(ImageModelPickerRoute{.purpose = purpose});
  }

  [[nodiscard]] static AppRoute ShellCommand(std::string command) {
    return AppRoute(ShellCommandRoute{.command = std::move(command)});
  }

  [[nodiscard]] static AppRoute ExtensionDetail(ExtensionKind kind) {
    return AppRoute(ExtensionDetailRoute{.kind = kind});
  }

  [[nodiscard]] static AppRoute
  AgentExtensionEditor(std::optional<std::string> id = std::nullopt) {
    return AppRoute(AgentExtensionEditorRoute{.id = std::move(id)});
  }

  [[nodiscard]] static AppRoute
  McpExtensionEditor(std::optional<std::string> id = std::nullopt) {
    return AppRoute(McpExtensionEditorRoute{.id = std::move(id)});
  }

  [[nodiscard]] static AppRoute SkillStoreDetail(std::string slug) {
    return AppRoute(SkillStoreDetailRoute{.slug = std::move(slug)});
  }

  [[nodiscard]] static AppRoute SkillHubSite(SkillHubDestination destination) {
    return AppRoute(SkillHubSiteRoute{
        .target = SkillHubSiteRoute::Destination{.value = destination}});
  }

  [[nodiscard]] static AppRoute SkillHubSkillSite(std::string name_space,
                                                  std::string slug) {
    return AppRoute(SkillHubSiteRoute{
        .target = SkillHubSiteRoute::Skill{.name_space = std::move(name_space),
                                           .slug = std::move(slug)}});
  }

  [[nodiscard]] const BrowserRoute *BrowserValue() const noexcept {
    return std::get_if<BrowserRoute>(&value_);
  }

  [[nodiscard]] const ImageModelPickerRoute *
  ImageModelPickerValue() const noexcept {
    return std::get_if<ImageModelPickerRoute>(&value_);
  }

  [[nodiscard]] const ShellCommandRoute *ShellCommandValue() const noexcept {
    return std::get_if<ShellCommandRoute>(&value_);
  }

  [[nodiscard]] const ExtensionDetailRoute *
  ExtensionDetailValue() const noexcept {
    return std::get_if<ExtensionDetailRoute>(&value_);
  }

  [[nodiscard]] const AgentExtensionEditorRoute *
  AgentExtensionEditorValue() const noexcept {
    return std::get_if<AgentExtensionEditorRoute>(&value_);
  }

  [[nodiscard]] const McpExtensionEditorRoute *
  McpExtensionEditorValue() const noexcept {
    return std::get_if<McpExtensionEditorRoute>(&value_);
  }

  [[nodiscard]] const SkillStoreDetailRoute *
  SkillStoreDetailValue() const noexcept {
    return std::get_if<SkillStoreDetailRoute>(&value_);
  }

  [[nodiscard]] const SkillHubSiteRoute *SkillHubSiteValue() const noexcept {
    return std::get_if<SkillHubSiteRoute>(&value_);
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
  explicit AppRoute(ImageModelPickerRoute picker) : value_(picker) {}
  explicit AppRoute(ShellCommandRoute command) : value_(std::move(command)) {}
  explicit AppRoute(ExtensionDetailRoute detail) : value_(detail) {}
  explicit AppRoute(AgentExtensionEditorRoute editor)
      : value_(std::move(editor)) {}
  explicit AppRoute(McpExtensionEditorRoute editor)
      : value_(std::move(editor)) {}

  explicit AppRoute(SkillStoreDetailRoute detail)
      : value_(std::move(detail)) {}

  explicit AppRoute(SkillHubSiteRoute site) : value_(std::move(site)) {}

  std::variant<Page, BrowserRoute, ImageModelPickerRoute, ShellCommandRoute,
               ExtensionDetailRoute, AgentExtensionEditorRoute,
               McpExtensionEditorRoute, SkillStoreDetailRoute,
               SkillHubSiteRoute>
      value_{settings};
};

// The legacy accessibility-backed Control mode is deliberately absent.
enum class ChatMode : std::uint8_t {
  chat,
  plan,
  agent,
};

[[nodiscard]] constexpr ChatMode ParseChatMode(
    std::string_view value) noexcept {
  if (value == "chat")
    return ChatMode::chat;
  if (value == "plan")
    return ChatMode::plan;
  // Historical Control sessions intentionally become normal Agent sessions.
  return ChatMode::agent;
}

[[nodiscard]] constexpr std::string_view
SerializeChatMode(ChatMode value) noexcept {
  switch (value) {
  case ChatMode::chat:
    return "chat";
  case ChatMode::plan:
    return "plan";
  case ChatMode::agent:
    return "agent";
  }
  return "agent";
}

enum class MessageRole : std::uint8_t {
  user,
  assistant,
  tool,
};

struct ChatMessage final {
  std::uint64_t id{};
  MessageRole role{MessageRole::user};
  std::string content;
  std::vector<InputAttachment> attachments;
  std::string reasoning_content{};
  std::vector<AssistantTimelineEvent> timeline{};
  bool streaming{};
  bool hidden{};
  bool exclude_from_context{};
  bool error{};
  std::string error_message{};
  std::int64_t processing_started_at{};
  std::int64_t processing_finished_at{};

  bool operator==(const ChatMessage &) const = default;
};

} // namespace linecode::domain
