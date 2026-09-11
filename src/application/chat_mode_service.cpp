#include "application/chat_mode_service.h"

#include <stdexcept>
#include <string>
#include <utility>

#include "application/tool_permission_service.h"

namespace linecode::application {
namespace {
constexpr auto kChatMode = "@linecode_chat_mode";
constexpr auto kRestorePermission = "@linecode_chat_mode_restore_permission";
} // namespace

ChatModeService::ChatModeService(
    std::shared_ptr<AsyncSettingsStore> settings,
    std::shared_ptr<ToolPermissionService> permissions)
    : settings_(std::move(settings)), permissions_(std::move(permissions)) {
  if (!settings_ || !permissions_)
    throw std::invalid_argument(
        "ChatModeService requires settings and permission services");
}

huxerui::Task<SettingsResult<ChatInteractionModeState>>
ChatModeService::Load() {
  auto stored = co_await settings_->GetString(kChatMode, "agent");
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  co_return co_await SetChatMode(domain::ParseChatMode(*stored));
}

huxerui::Task<SettingsResult<void>>
ChatModeService::RememberWritablePermission(
    domain::ToolPermissionMode mode) {
  if (mode == domain::ToolPermissionMode::read_only)
    co_return SettingsResult<void>{};
  co_return co_await settings_->SetString(
      kRestorePermission,
      std::string{domain::SerializeToolPermissionMode(mode)});
}

huxerui::Task<SettingsResult<domain::ToolPermissionMode>>
ChatModeService::RestorablePermission() {
  auto stored = co_await settings_->GetString(kRestorePermission, "auto");
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  auto parsed = domain::ParseToolPermissionMode(*stored);
  if (parsed == domain::ToolPermissionMode::read_only)
    parsed = domain::ToolPermissionMode::automatic;
  co_return parsed;
}

huxerui::Task<SettingsResult<ChatInteractionModeState>>
ChatModeService::SetChatMode(domain::ChatMode mode) {
  auto permission = co_await permissions_->Load();
  if (!permission)
    co_return std::unexpected(std::move(permission.error()));

  auto effective_permission = permission->mode;
  if (mode == domain::ChatMode::chat) {
    auto remembered = co_await RememberWritablePermission(effective_permission);
    if (!remembered)
      co_return std::unexpected(std::move(remembered.error()));
    if (effective_permission != domain::ToolPermissionMode::read_only) {
      auto saved = co_await permissions_->SetMode(
          domain::ToolPermissionMode::read_only);
      if (!saved)
        co_return std::unexpected(std::move(saved.error()));
      effective_permission = domain::ToolPermissionMode::read_only;
    }
  } else if (effective_permission == domain::ToolPermissionMode::read_only) {
    auto restored = co_await RestorablePermission();
    if (!restored)
      co_return std::unexpected(std::move(restored.error()));
    auto saved = co_await permissions_->SetMode(*restored);
    if (!saved)
      co_return std::unexpected(std::move(saved.error()));
    effective_permission = *restored;
  } else {
    auto remembered = co_await RememberWritablePermission(effective_permission);
    if (!remembered)
      co_return std::unexpected(std::move(remembered.error()));
  }

  auto saved_mode = co_await settings_->SetString(
      kChatMode, std::string{domain::SerializeChatMode(mode)});
  if (!saved_mode)
    co_return std::unexpected(std::move(saved_mode.error()));
  co_return ChatInteractionModeState{.chat_mode = mode,
                                     .permission_mode = effective_permission};
}

huxerui::Task<SettingsResult<ChatInteractionModeState>>
ChatModeService::SetPermissionMode(domain::ToolPermissionMode mode) {
  auto saved_permission = co_await permissions_->SetMode(mode);
  if (!saved_permission)
    co_return std::unexpected(std::move(saved_permission.error()));

  auto stored_mode = co_await settings_->GetString(kChatMode, "agent");
  if (!stored_mode)
    co_return std::unexpected(std::move(stored_mode.error()));
  auto chat_mode = domain::ParseChatMode(*stored_mode);
  if (mode == domain::ToolPermissionMode::read_only) {
    chat_mode = domain::ChatMode::chat;
  } else {
    auto remembered = co_await RememberWritablePermission(mode);
    if (!remembered)
      co_return std::unexpected(std::move(remembered.error()));
    if (chat_mode == domain::ChatMode::chat)
      chat_mode = domain::ChatMode::agent;
  }

  auto saved_mode = co_await settings_->SetString(
      kChatMode, std::string{domain::SerializeChatMode(chat_mode)});
  if (!saved_mode)
    co_return std::unexpected(std::move(saved_mode.error()));
  co_return ChatInteractionModeState{.chat_mode = chat_mode,
                                     .permission_mode = mode};
}

} // namespace linecode::application
