#pragma once

#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <vector>

#include <huxerui/view.h>

namespace linecode::presentation {

enum class ChatContextAction : std::uint8_t {
  files,
  image,
  model,
  mode,
  workspace,
  permissions,
  settings,
  more,
};

enum class ChatMoreAction : std::uint8_t {
  tutorial,
  settings,
  export_chat,
  select_messages_to_export,
  compact_context,
  clear_chat,
};

enum class ChatPermissionMode : std::uint8_t {
  automatic,
  confirm,
  read_only,
};

enum class ChatPermissionAction : std::uint8_t {
  automatic,
  confirm,
  read_only,
  manage_all_files,
  revoke_saved_commands,
};

// Platform integrations decide availability before composing the menu. This
// keeps Android/Windows concerns out of the reusable view and prevents
// unsupported actions from briefly entering the retained tree.
struct ChatContextAvailability final {
  bool files = true;
  bool image = true;
  bool model = true;
  bool mode = true;
  bool workspace = true;
  bool permissions = true;
  bool settings = true;
  bool more = true;
};

struct ChatMoreAvailability final {
  bool tutorial = true;
  bool settings = true;
  bool export_chat = true;
  bool select_messages_to_export = true;
  bool compact_context = true;
  bool clear_chat = true;
};

struct ChatContextMenuState final {
  bool visible = false;
  std::string model_label;
  std::string mode_label;
  ChatContextAvailability available;
};

struct ChatMoreMenuState final {
  bool visible = false;
  ChatMoreAvailability available;
};

struct ChatPermissionMenuState final {
  bool visible = false;
  ChatPermissionMode mode = ChatPermissionMode::confirm;
  bool manage_all_files_available = false;
  bool external_storage_granted = false;
  bool has_saved_command_permissions = false;
  std::string storage_permission_description;
};

struct ChatAttachmentNode final {
  std::string name;
  std::string path;
  bool directory = false;
  bool expanded = false;
  std::vector<ChatAttachmentNode> children;

  bool operator==(const ChatAttachmentNode &) const = default;
};

struct ChatAttachmentFile final {
  std::string path;
  std::string name;
  std::string source = "local";

  bool operator==(const ChatAttachmentFile &) const = default;
};

struct ChatAttachmentPickerState final {
  bool visible = false;
  std::optional<ChatAttachmentNode> tree;
  std::vector<std::string> selected_paths;
  std::vector<std::string> expanded_directories;
  bool loading = false;
  std::string message;
};

struct ChatAttachmentPickerCallbacks final {
  std::function<void()> on_dismiss_request;
  std::function<void(std::string)> on_directory_toggled;
  std::function<void(ChatAttachmentFile)> on_file_toggled;
};

template <typename Action> struct ChatOverlayCallbacks final {
  std::function<void()> on_dismiss_request;
  std::function<void(Action)> on_action;
};

// These functions return bottom-sheet content only. The integration layer owns
// presentation, navigation, and the authoritative visible state.
[[nodiscard]] huxerui::View
ChatContextMenu(const ChatContextMenuState &state,
                ChatOverlayCallbacks<ChatContextAction> callbacks);

[[nodiscard]] huxerui::View
ChatMoreMenu(const ChatMoreMenuState &state,
             ChatOverlayCallbacks<ChatMoreAction> callbacks);

[[nodiscard]] huxerui::View
ChatPermissionMenu(const ChatPermissionMenuState &state,
                   ChatOverlayCallbacks<ChatPermissionAction> callbacks);

[[nodiscard]] huxerui::View
ChatAttachmentPicker(const ChatAttachmentPickerState &state,
                     ChatAttachmentPickerCallbacks callbacks);

} // namespace linecode::presentation
