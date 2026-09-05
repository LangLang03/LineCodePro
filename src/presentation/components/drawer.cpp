#include "presentation/components/drawer.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <ctime>
#include <functional>
#include <iterator>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

constexpr float kDrawerWidth = 360.0F;
constexpr float kDrawerReveal = 48.0F;
constexpr float kHeaderActionSize = 32.0F;
constexpr float kTabIconSize = 14.0F;
constexpr float kTreeIndent = 16.0F;
constexpr std::size_t kMaximumTreeIndentDepth = 5;

constexpr Color kJavascriptFile = Color::Rgb(240, 219, 79);
constexpr Color kXmlFile = Color::Rgb(255, 159, 10);

consteval float HeaderTopPadding() {
  if constexpr (CurrentHostPlatform() == HostPlatform::android) {
    // DrawerLayout already consumes the Android status-bar safe area. The
    // legacy 40dp inset was measured from the physical screen top, leaving 4dp
    // inside that safe area.
    return 4.0F;
  }
  return 40.0F;
}

consteval float HeaderHeight() {
  if constexpr (CurrentHostPlatform() == HostPlatform::android) {
    return 52.0F;
  }
  return 88.0F;
}

TextStyle DrawerTextStyle(float size, FontWeight weight = FontWeight::Regular,
                          Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

Indication PressIndication(float radius) {
  return Indication{
      .geometry = {.clip_corner_radii = CornerRadii(radius)},
      .press =
          IndicationLayer{
              .fill = VisualFill{colors::accent_muted_strong},
              .corner_radii = CornerRadii(radius),
              .placement = IndicationPlacement::BehindContent,
          },
  };
}

template <typename Callback, typename... Arguments>
void InvokeIfPresent(const Callback &callback, Arguments &&...arguments) {
  if (callback) {
    std::invoke(callback, std::forward<Arguments>(arguments)...);
  }
}

View ActionIcon(ImageResource image, Color tint, float container_size,
                float icon_size, std::function<void()> action) {
  return Stack{
      Image(std::move(image))
          .Tint(tint)
          .With(Frame{.width = icon_size, .height = icon_size}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = container_size, .height = container_size},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Indication(PressIndication(container_size / 2.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View InlineIcon(ImageResource image, Color tint, float size) {
  return Image(std::move(image))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

std::string FormatConversationTime(std::int64_t updated_at_millis) {
  if (updated_at_millis <= 0) {
    return {};
  }

  const auto seconds = std::chrono::seconds(updated_at_millis / 1000);
  const auto clock_time = std::chrono::system_clock::time_point(seconds);
  const std::time_t value = std::chrono::system_clock::to_time_t(clock_time);
  std::tm local{};
#if defined(_WIN32)
  localtime_s(&local, &value);
#else
  localtime_r(&value, &local);
#endif
  std::array<char, 24> result{};
  if (std::strftime(result.data(), result.size(), "%m/%d %H:%M", &local) == 0) {
    return {};
  }

  std::string formatted(result.data());
  if (formatted.size() >= 2 && formatted.front() == '0') {
    formatted.erase(formatted.begin());
  }
  if (const auto slash = formatted.find('/'); slash != std::string::npos &&
                                              slash + 1 < formatted.size() &&
                                              formatted[slash + 1] == '0') {
    formatted.erase(formatted.begin() + static_cast<std::ptrdiff_t>(slash + 1));
  }
  return formatted;
}

View Header(bool files_active, const DrawerActions &actions) {
  std::vector<View> children;
  children.emplace_back(Text(files_active
                                 ? app::strings::drawer_title_files
                                 : app::strings::drawer_title_conversations)
                            .Style(DrawerTextStyle(17.0F, FontWeight::Bold))
                            .With(Grow()));
  if (files_active) {
    children.emplace_back(ActionIcon(app::images::refresh_cw, colors::accent,
                                     kHeaderActionSize, 16.0F,
                                     [callback = actions.on_file_tree_refresh] {
                                       InvokeIfPresent(callback);
                                     }));
  }

  return Row(std::move(children))
      .With(Frame{.height = HeaderHeight()},
            Padding(EdgeInsets{
                .top = HeaderTopPadding(),
                .right = 16.0F,
                .bottom = files_active ? 16.0F : 24.0F,
                .left = 24.0F,
            }),
            Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center));
}

View DrawerTabButton(ImageResource image, StringResource label, bool active,
                     std::function<void()> action) {
  const Color tint = active ? colors::accent : colors::tertiary;
  return Row{
      InlineIcon(std::move(image), tint, kTabIconSize),
      Text(std::move(label))
          .Style(DrawerTextStyle(
              13.0F, active ? FontWeight::Medium : FontWeight::Regular, tint))
          .VerticalAlign(TextVerticalAlign::Center)
          .With(Frame{.height = 18.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.height = 34.0F}, Padding(EdgeInsets::Symmetric(0.0F, 8.0F)),
            Spacing(4.0F), MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::elevated : Color::Transparent()),
            CornerRadius(6.0F), Indication(PressIndication(6.0F)), Grow(),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View DrawerTabs(State<std::size_t> selected_tab, const DrawerActions &actions) {
  const std::size_t active = std::min(selected_tab.Get(), std::size_t{1});
  View tabs =
      Row{
          DrawerTabButton(app::images::message_square,
                          app::strings::drawer_tab_conversations, active == 0,
                          [selected_tab] {
                            selected_tab = static_cast<std::size_t>(
                                DrawerTab::conversations);
                          }),
          DrawerTabButton(
              app::images::folder_open, app::strings::drawer_tab_files,
              active == 1,
              [selected_tab, callback = actions.on_file_tree_activated] {
                if (selected_tab.Get() !=
                    static_cast<std::size_t>(DrawerTab::files)) {
                  selected_tab = static_cast<std::size_t>(DrawerTab::files);
                  InvokeIfPresent(callback);
                }
              }),
      }
          .With(Padding(2.0F), Spacing(0.0F), Background(colors::surface),
                CornerRadius(8.0F), CrossAlign(CrossAxisAlignment::Stretch),
                Grow());
  return Row{std::move(tabs)}.With(
      Padding(EdgeInsets{.right = 16.0F, .bottom = 12.0F, .left = 16.0F}));
}

View NewConversationButton(State<bool> drawer_open,
                           const DrawerActions &actions) {
  View button =
      Row{
          InlineIcon(app::images::plus, colors::text, 18.0F),
          Text(app::strings::drawer_new_conversation)
              .Style(DrawerTextStyle(16.0F, FontWeight::Bold)),
      }
          .OnClick([drawer_open, callback = actions.on_new_conversation] {
            InvokeIfPresent(callback);
            drawer_open = false;
          })
          .With(Frame{.height = 52.0F},
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)), Spacing(8.0F),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::input), CornerRadius(14.0F),
                Indication(PressIndication(14.0F)), Focusable(),
                PointerCursor(PointerCursorKind::Hand), Grow());
  return Row{std::move(button)}.With(
      Padding(EdgeInsets{.right = 16.0F, .bottom = 12.0F, .left = 16.0F}));
}

View ConversationRow(const DrawerConversation &conversation, bool active,
                     State<bool> drawer_open, const DrawerActions &actions) {
  const std::string id = conversation.id;
  const Color background = active ? colors::input : colors::elevated;
  const Color border = active ? colors::accent : colors::border_light;

  return Row{
      Column{
          Text(conversation.title)
              .Style(DrawerTextStyle(16.0F, active ? FontWeight::Medium
                                                   : FontWeight::Regular))
              .With(Frame{.max_height = 21.0F}, ClipChildren()),
          Text(FormatConversationTime(conversation.updated_at_millis))
              .Style(DrawerTextStyle(11.0F, FontWeight::Regular,
                                     colors::tertiary)),
      }
          .With(Spacing(6.0F), Grow(),
                Padding(EdgeInsets::Symmetric(8.0F, 0.0F))),
      ActionIcon(app::images::trash_2, colors::tertiary, 48.0F, 16.0F,
                 [id, callback = actions.on_conversation_deleted] {
                   InvokeIfPresent(callback, std::string_view{id});
                 }),
  }
      .OnClick([id, drawer_open, callback = actions.on_conversation_selected] {
        InvokeIfPresent(callback, std::string_view{id});
        drawer_open = false;
      })
      .With(Padding(EdgeInsets{
                .top = 16.0F, .right = 4.0F, .bottom = 16.0F, .left = 8.0F}),
            CrossAlign(CrossAxisAlignment::Center), Background(background),
            Border{.color = border, .width = 1.0F}, CornerRadius(12.0F),
            Indication(PressIndication(12.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand))
      .Key(id);
}

View ConversationBody(State<bool> drawer_open, const DrawerModel &model,
                      const DrawerActions &actions) {
  std::vector<View> rows;
  if (model.conversations.empty()) {
    rows.emplace_back(Text(app::strings::drawer_empty_conversations)
                          .Style(DrawerTextStyle(13.0F, FontWeight::Regular,
                                                 colors::tertiary))
                          .Align(TextAlign::Center)
                          .With(Padding(EdgeInsets{.top = 80.0F})));
  } else {
    rows.reserve(model.conversations.size());
    std::ranges::transform(
        model.conversations, std::back_inserter(rows),
        [&](const DrawerConversation &conversation) {
          return ConversationRow(
              conversation, conversation.id == model.selected_conversation_id,
              drawer_open, actions);
        });
  }

  return Column{
      NewConversationButton(drawer_open, actions),
      ScrollView(Column(std::move(rows))
                     .With(Spacing(8.0F),
                           Padding(EdgeInsets{.top = 12.0F,
                                              .right = 12.0F,
                                              .bottom = 32.0F,
                                              .left = 12.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(Grow(), CrossAlign(CrossAxisAlignment::Stretch));
}

enum class FilePresentation {
  directory_closed,
  directory_open,
  code,
  text,
  generic,
};

std::string Lowercase(std::string_view value) {
  std::string result(value);
  std::ranges::transform(result, result.begin(), [](unsigned char character) {
    return static_cast<char>(std::tolower(character));
  });
  return result;
}

bool EndsWith(std::string_view value, std::string_view suffix) {
  return value.size() >= suffix.size() &&
         value.substr(value.size() - suffix.size()) == suffix;
}

FilePresentation FileKind(const DrawerFileNode &node) {
  if (node.directory) {
    return node.expanded ? FilePresentation::directory_open
                         : FilePresentation::directory_closed;
  }

  const std::string lower = Lowercase(node.name);
  constexpr std::array code_extensions{
      std::string_view{".java"},   std::string_view{".kt"},
      std::string_view{".js"},     std::string_view{".ts"},
      std::string_view{".tsx"},    std::string_view{".jsx"},
      std::string_view{".xml"},    std::string_view{".json"},
      std::string_view{".gradle"},
  };
  if (std::ranges::any_of(code_extensions, [&](std::string_view extension) {
        return EndsWith(lower, extension);
      })) {
    return FilePresentation::code;
  }
  if (EndsWith(lower, ".md") || EndsWith(lower, ".txt") ||
      EndsWith(lower, ".log")) {
    return FilePresentation::text;
  }
  return FilePresentation::generic;
}

ImageResource FileImage(FilePresentation presentation) {
  switch (presentation) {
  case FilePresentation::directory_closed:
    return app::images::folder;
  case FilePresentation::directory_open:
    return app::images::folder_open;
  case FilePresentation::code:
    return app::images::file_code;
  case FilePresentation::text:
    return app::images::file_text;
  case FilePresentation::generic:
    return app::images::file;
  }
  return app::images::file;
}

Color FileColor(const DrawerFileNode &node, FilePresentation presentation) {
  if (presentation == FilePresentation::directory_open) {
    return colors::accent;
  }
  if (presentation == FilePresentation::directory_closed ||
      presentation == FilePresentation::text) {
    return colors::secondary;
  }
  if (presentation == FilePresentation::code) {
    return EndsWith(Lowercase(node.name), ".xml") ? kXmlFile : kJavascriptFile;
  }
  return colors::tertiary;
}

View FileRow(const DrawerFileNode &node, std::size_t depth, bool root,
             const DrawerActions &actions) {
  const FilePresentation presentation = FileKind(node);
  const float icon_size = node.directory ? 16.0F : 14.0F;
  const DrawerFileTarget target{
      .path = node.path,
      .name = node.name,
      .directory = node.directory,
      .root = root,
  };
  std::vector<View> content;
  content.emplace_back(InlineIcon(FileImage(presentation),
                                  FileColor(node, presentation), icon_size));
  content.emplace_back(Text(node.name)
                           .Style(DrawerTextStyle(13.0F))
                           .With(Frame{.max_height = 18.0F}, ClipChildren(),
                                 Grow(), Padding(EdgeInsets{.left = 8.0F})));
  if (root) {
    content.emplace_back(
        ActionIcon(app::images::plus, colors::tertiary, 22.0F, 14.0F,
                   [target, callback = actions.on_file_node_long_pressed] {
                     InvokeIfPresent(callback, target);
                   }));
  }

  const float left =
      16.0F + static_cast<float>(std::min(depth, kMaximumTreeIndentDepth)) *
                  kTreeIndent;
  return Row(std::move(content))
      .OnClick([target, callback = actions.on_file_node_selected] {
        InvokeIfPresent(callback, target);
      })
      .On<LongPressEvents::Started>(
          [target, callback = actions.on_file_node_long_pressed](
              const LongPressEvent &) { InvokeIfPresent(callback, target); })
      .With(LongPressGesture{}, Frame{.min_height = 48.0F},
            Padding(EdgeInsets{
                .top = 12.0F, .right = 16.0F, .bottom = 12.0F, .left = left}),
            CrossAlign(CrossAxisAlignment::Center),
            Indication(PressIndication(8.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand))
      .Key(node.path);
}

void AppendFileRows(std::vector<View> &rows, const DrawerFileNode &node,
                    std::size_t depth, bool root,
                    const DrawerActions &actions) {
  rows.emplace_back(FileRow(node, depth, root, actions));
  if (!node.directory || !node.expanded) {
    return;
  }
  for (const DrawerFileNode &child : node.children) {
    AppendFileRows(rows, child, depth + 1, false, actions);
  }
}

View ProjectStrip(const DrawerModel &model, const DrawerActions &actions) {
  View strip =
      Column{
          Text(model.project_label.empty() ? std::string{"LineCode"}
                                           : model.project_label)
              .Style(DrawerTextStyle(13.0F, FontWeight::Bold)),
          Text(model.project_path)
              .Style(
                  DrawerTextStyle(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Frame{.max_height = 30.0F}, ClipChildren()),
      }
          .With(Spacing(6.0F), Padding(8.0F), Indication(PressIndication(8.0F)),
                Focusable(model.project_removable),
                PointerCursor(model.project_removable
                                  ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default),
                Grow());
  if (model.project_removable) {
    strip = std::move(strip)
                .On<LongPressEvents::Started>(
                    [callback = actions.on_project_remove_requested](
                        const LongPressEvent &) { InvokeIfPresent(callback); })
                .With(LongPressGesture{});
  }
  return Row{std::move(strip)}.With(
      Padding(EdgeInsets{.right = 16.0F, .bottom = 8.0F, .left = 16.0F}));
}

View FileBody(const DrawerModel &model, const DrawerActions &actions) {
  std::vector<View> rows;
  if (model.file_tree) {
    AppendFileRows(rows, *model.file_tree, 0, true, actions);
  } else {
    rows.emplace_back(Text(app::strings::drawer_files_preparing)
                          .Style(DrawerTextStyle(13.0F, FontWeight::Regular,
                                                 colors::tertiary))
                          .Align(TextAlign::Center)
                          .With(Padding(EdgeInsets{.top = 80.0F})));
  }

  return Column{
      ProjectStrip(model, actions),
      ScrollView(Column(std::move(rows))
                     .With(Padding(EdgeInsets{
                               .top = 8.0F, .right = 8.0F, .left = 8.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(Grow(), CrossAlign(CrossAxisAlignment::Stretch));
}

} // namespace

DrawerStyle LegacyDrawerStyle() {
  return DrawerStyle{
      .background = colors::background,
      .scrim = Color::Rgb(0, 0, 0, 165.0F / 255.0F),
      .shadow = Shadow{.color = Color::Transparent()},
      .preferred_width = kDrawerWidth,
      .minimum_width = 240.0F,
      .minimum_content_width = kDrawerWidth,
      .modal_content_reveal = kDrawerReveal,
      .edge_drag_width = 24.0F,
      .corner_radius = 0.0F,
      .motion =
          DrawerMotion{
              .open = TweenSpec{.duration = 0.18, .easing = Easing::EaseOut},
              .close = TweenSpec{.duration = 0.15, .easing = Easing::EaseIn},
          },
  };
}

View Drawer(State<bool> drawer_open, State<std::size_t> selected_tab) {
  return Drawer(drawer_open, selected_tab, DrawerModel{}, DrawerActions{});
}

View Drawer(State<bool> drawer_open, State<std::size_t> selected_tab,
            const DrawerModel &model, const DrawerActions &actions) {
  const bool files_active = std::min(selected_tab.Get(), std::size_t{1}) ==
                            static_cast<std::size_t>(DrawerTab::files);
  return Column{
      Header(files_active, actions),
      DrawerTabs(selected_tab, actions),
      files_active ? FileBody(model, actions)
                   : ConversationBody(drawer_open, model, actions),
  }
      .With(Frame{.min_width = 240.0F, .max_width = kDrawerWidth},
            CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background));
}

} // namespace linecode::presentation
