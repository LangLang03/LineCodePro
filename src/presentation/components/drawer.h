#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/navigation.h>
#include <huxerui/state.h>
#include <huxerui/view.h>

namespace linecode::presentation {

enum class DrawerTab : std::size_t {
  conversations = 0,
  files = 1,
};

struct DrawerConversation final {
  std::string id;
  std::string title;
  std::int64_t updated_at_millis = 0;

  bool operator==(const DrawerConversation&) const = default;
};

struct DrawerFileNode final {
  std::string name;
  std::string path;
  bool directory = false;
  bool expanded = false;
  std::vector<DrawerFileNode> children;

  bool operator==(const DrawerFileNode&) const = default;
};

struct DrawerFileTarget final {
  std::string path;
  std::string name;
  bool directory = false;
  bool root = false;

  bool operator==(const DrawerFileTarget&) const = default;
};

struct DrawerModel final {
  std::vector<DrawerConversation> conversations;
  std::string selected_conversation_id;
  std::string project_label = "LineCode";
  std::string project_path;
  bool project_removable = false;
  std::optional<DrawerFileNode> file_tree;

  bool operator==(const DrawerModel&) const = default;
};

// Application-owned commands keep the drawer independent of navigation and persistence.
struct DrawerActions final {
  std::function<void()> on_new_conversation;
  std::function<void(std::string_view)> on_conversation_selected;
  std::function<void(std::string_view)> on_conversation_deleted;
  std::function<void()> on_project_remove_requested;
  std::function<void(DrawerFileTarget)> on_file_node_selected;
  std::function<void(DrawerFileTarget)> on_file_node_long_pressed;
  std::function<void()> on_file_tree_activated;
  std::function<void()> on_file_tree_refresh;
};

// Apply this style to the Theme that owns the surrounding DrawerLayout.
[[nodiscard]] huxerui::DrawerStyle LegacyDrawerStyle();

// Empty-state adapter retained for the first in-memory application slice.
huxerui::View Drawer(
    huxerui::State<bool> drawer_open,
    huxerui::State<std::size_t> selected_tab
);

// Controlled production surface. The application owns data and every side effect.
huxerui::View Drawer(
    huxerui::State<bool> drawer_open,
    huxerui::State<std::size_t> selected_tab,
    const DrawerModel& model,
    const DrawerActions& actions
);

} // namespace linecode::presentation
