#pragma once

#include <memory>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"
#include "domain/app_state.h"
#include "domain/tool_permission.h"

namespace linecode::application {

class ToolPermissionService;

struct ChatInteractionModeState final {
  domain::ChatMode chat_mode{domain::ChatMode::agent};
  domain::ToolPermissionMode permission_mode{
      domain::ToolPermissionMode::automatic};

  bool operator==(const ChatInteractionModeState &) const = default;
};

// Coordinates the two legacy settings which form one user-visible mode:
// Chat mode is read-only, while selecting a writable permission leaves Chat.
class ChatModeService final {
public:
  ChatModeService(std::shared_ptr<AsyncSettingsStore> settings,
                  std::shared_ptr<ToolPermissionService> permissions);

  [[nodiscard]] huxerui::Task<SettingsResult<ChatInteractionModeState>> Load();
  [[nodiscard]] huxerui::Task<SettingsResult<ChatInteractionModeState>>
  SetChatMode(domain::ChatMode mode);
  [[nodiscard]] huxerui::Task<SettingsResult<ChatInteractionModeState>>
  SetPermissionMode(domain::ToolPermissionMode mode);

private:
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  RememberWritablePermission(domain::ToolPermissionMode mode);
  [[nodiscard]] huxerui::Task<SettingsResult<domain::ToolPermissionMode>>
  RestorablePermission();

  std::shared_ptr<AsyncSettingsStore> settings_;
  std::shared_ptr<ToolPermissionService> permissions_;
};

} // namespace linecode::application
