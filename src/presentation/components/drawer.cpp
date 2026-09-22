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
#if defined(__ANDROID__)
#include "application/ports/window_insets.h"
#endif
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

struct DrawerTabSelection final {
  DrawerTab active;
  std::function<void(DrawerTab)> select;
};

using HeaderActionsFactory = void (*)(std::vector<View>&, const DrawerActions&);
using TabBodyFactory = View (*)(State<bool>, const DrawerModel&,
                                const DrawerActions&);
using TabActivation = void (*)(const DrawerActions&);

struct DrawerTabPresentation final {
  DrawerTab tab;
  StringResource header_title;
  StringResource tab_label;
  ImageResource tab_icon;
  HeaderActionsFactory append_header_actions;
  TabActivation activate;
  TabBodyFactory body;
};

using FileRuleMatcher = bool (*)(const DrawerFileNode&, std::string_view);
using FileTintFactory = Color (*)();

struct FilePresentationRule final {
  FileRuleMatcher matches;
  ImageResource icon;
  FileTintFactory tint;
  float icon_size;
};

struct FilePresentation final {
  ImageResource icon;
  Color tint;
  float icon_size;
};

View ConversationBody(State<bool> drawer_open, const DrawerModel& model,
                      const DrawerActions& actions);
View FileBody(State<bool> drawer_open, const DrawerModel& model,
              const DrawerActions& actions);

// DrawerLayout deliberately constrains modal drawers to the safe viewport.
// The legacy Android drawer instead owns the complete window: its background
// extends behind both system bars and its fixed 40dp header inset is measured
// from the physical top. Expand the child through the remaining safe-area
// insets without changing DrawerLayout or hard-coding a particular device's
// status/navigation bar heights.
class LegacyDrawerViewport final : public Layout<LegacyDrawerViewport> {
public:
  using Layout::Layout;

  struct InsetsValue {
    using Value = EdgeInsets;
  };

  static LayoutResult Measure(LayoutContext& context, ViewNode& node,
                              Constraints constraints) {
    LayoutResult result;
    if (node.ChildCount() == 0) {
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));
    }

    const EdgeInsets insets =
        node.ChildAt(0).LayoutValueOr<InsetsValue>(EdgeInsets{});
    Constraints expanded = constraints;
    expanded.min_height += insets.Vertical();
    if (expanded.HasBoundedHeight()) {
      expanded.max_height += insets.Vertical();
    }
    ViewNode& content = node.ChildAt(0);
    const Size content_size = context.Measure(content, expanded);
    const Size viewport_size = constraints.Constrain(
        {content_size.width,
         std::max(0.0F, content_size.height - insets.Vertical())});
    return result.Place(content, {0.0F, -insets.top})
        .SetSize(viewport_size);
  }
};

TextStyle DrawerTextStyle(float size, FontWeight weight = FontWeight::Regular,
                          Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
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
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View InlineIcon(ImageResource image, Color tint, float size) {
  return Image(std::move(image))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

void AppendNoHeaderActions(std::vector<View>&, const DrawerActions&) {}

void AppendFileHeaderActions(std::vector<View>& children,
                             const DrawerActions& actions) {
  children.emplace_back(ActionIcon(
      app::images::refresh_cw, colors::accent, kHeaderActionSize, 16.0F,
      [callback = actions.on_file_tree_refresh] { InvokeIfPresent(callback); }));
}

void ActivateWithoutSideEffect(const DrawerActions&) {}

void ActivateFiles(const DrawerActions& actions) {
  InvokeIfPresent(actions.on_file_tree_activated);
}

const std::array kDrawerTabPresentations{
    DrawerTabPresentation{
        .tab = DrawerTab::conversations,
        .header_title = app::strings::drawer_title_conversations,
        .tab_label = app::strings::drawer_tab_conversations,
        .tab_icon = app::images::message_square,
        .append_header_actions = &AppendNoHeaderActions,
        .activate = &ActivateWithoutSideEffect,
        .body = &ConversationBody,
    },
    DrawerTabPresentation{
        .tab = DrawerTab::files,
        .header_title = app::strings::drawer_title_files,
        .tab_label = app::strings::drawer_tab_files,
        .tab_icon = app::images::folder_open,
        .append_header_actions = &AppendFileHeaderActions,
        .activate = &ActivateFiles,
        .body = &FileBody,
    },
};

constexpr std::size_t DrawerTabIndex(DrawerTab tab) noexcept {
  return std::to_underlying(tab);
}

static_assert(DrawerTabIndex(DrawerTab::conversations) == 0);
static_assert(DrawerTabIndex(DrawerTab::files) == 1);
static_assert(kDrawerTabPresentations.size() == DrawerTabIndex(DrawerTab::count));

const DrawerTabPresentation& DrawerTabPresentationFor(DrawerTab tab) {
  return kDrawerTabPresentations[DrawerTabIndex(tab)];
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

View Header(const DrawerTabPresentation& presentation,
            const DrawerActions& actions) {
  std::vector<View> children;
  children.emplace_back(Text(presentation.header_title)
                            .Style(DrawerTextStyle(17.0F, FontWeight::Bold))
                            .With(Grow()));
  std::invoke(presentation.append_header_actions, children, actions);

  return Row(std::move(children))
      .With(Padding(EdgeInsets{
                .top = 40.0F,
                .right = 16.0F,
                .bottom = 24.0F,
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
      .With(Padding(EdgeInsets::Symmetric(0.0F, 8.0F)), Spacing(4.0F),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::elevated : Color::Transparent()),
            CornerRadius(6.0F), Grow(), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DrawerTabs(const DrawerTabSelection& selection,
                const DrawerActions& actions) {
  std::vector<View> buttons;
  buttons.reserve(kDrawerTabPresentations.size());
  for (const auto& presentation : kDrawerTabPresentations) {
    buttons.emplace_back(DrawerTabButton(
        presentation.tab_icon, presentation.tab_label,
        presentation.tab == selection.active,
        [selection, tab = presentation.tab, activate = presentation.activate,
         actions] {
          if (selection.active == tab) {
            return;
          }
          std::invoke(selection.select, tab);
          std::invoke(activate, actions);
        }));
  }
  View tabs = Row(std::move(buttons))
                  .With(Padding(2.0F), Spacing(0.0F),
                        CrossAlign(CrossAxisAlignment::Stretch), Grow());
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
          .With(Frame{.height = 52.62F},
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)), Spacing(8.0F),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::input), CornerRadius(14.0F), Focusable(),
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
            Focusable(), PointerCursor(PointerCursorKind::Hand))
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

constexpr std::array kCodeFileExtensions{
    std::string_view{".java"}, std::string_view{".kt"},
    std::string_view{".js"},   std::string_view{".ts"},
    std::string_view{".tsx"},  std::string_view{".jsx"},
    std::string_view{".json"}, std::string_view{".gradle"},
};

constexpr std::array kTextFileExtensions{
    std::string_view{".md"},
    std::string_view{".txt"},
    std::string_view{".log"},
};

template <std::size_t Size>
bool HasExtension(std::string_view path,
                  const std::array<std::string_view, Size>& extensions) {
  return std::ranges::any_of(extensions, [path](std::string_view extension) {
    return EndsWith(path, extension);
  });
}

bool MatchesOpenDirectory(const DrawerFileNode& node, std::string_view) {
  return node.directory && node.expanded;
}

bool MatchesClosedDirectory(const DrawerFileNode& node, std::string_view) {
  return node.directory && !node.expanded;
}

bool MatchesXmlFile(const DrawerFileNode& node, std::string_view lowercase_name) {
  return !node.directory && EndsWith(lowercase_name, ".xml");
}

bool MatchesCodeFile(const DrawerFileNode& node,
                     std::string_view lowercase_name) {
  return !node.directory && HasExtension(lowercase_name, kCodeFileExtensions);
}

bool MatchesTextFile(const DrawerFileNode& node,
                     std::string_view lowercase_name) {
  return !node.directory && HasExtension(lowercase_name, kTextFileExtensions);
}

bool MatchesAnyFile(const DrawerFileNode&, std::string_view) { return true; }

Color AccentFileTint() { return colors::accent; }
Color SecondaryFileTint() { return colors::secondary; }
Color TertiaryFileTint() { return colors::tertiary; }
Color JavascriptFileTint() { return kJavascriptFile; }
Color XmlFileTint() { return kXmlFile; }

const std::array kFilePresentationRules{
    FilePresentationRule{&MatchesOpenDirectory, app::images::folder_open,
                         &AccentFileTint, 16.0F},
    FilePresentationRule{&MatchesClosedDirectory, app::images::folder,
                         &SecondaryFileTint, 16.0F},
    FilePresentationRule{&MatchesXmlFile, app::images::file_code, &XmlFileTint,
                         14.0F},
    FilePresentationRule{&MatchesCodeFile, app::images::file_code,
                         &JavascriptFileTint, 14.0F},
    FilePresentationRule{&MatchesTextFile, app::images::file_text,
                         &SecondaryFileTint, 14.0F},
    FilePresentationRule{&MatchesAnyFile, app::images::file, &TertiaryFileTint,
                         14.0F},
};

FilePresentation FilePresentationFor(const DrawerFileNode& node) {
  const std::string lowercase_name = Lowercase(node.name);
  const auto rule = std::ranges::find_if(
      kFilePresentationRules, [&node, lowercase_name](const auto& candidate) {
        return std::invoke(candidate.matches, node, lowercase_name);
      });
  return {
      .icon = rule->icon,
      .tint = std::invoke(rule->tint),
      .icon_size = rule->icon_size,
  };
}

View FileRow(const DrawerFileNode &node, std::size_t depth, bool root,
             const DrawerActions &actions) {
  const FilePresentation presentation = FilePresentationFor(node);
  const DrawerFileTarget target{
      .path = node.path,
      .name = node.name,
      .directory = node.directory,
      .root = root,
  };
  std::vector<View> content;
  content.emplace_back(
      InlineIcon(presentation.icon, presentation.tint, presentation.icon_size));
  // Spacer owns Grow(1) by default.  The legacy tree uses a fixed 8 dp icon
  // margin, so a Spacer here pushes leaf names across the whole row.
  content.emplace_back(Stack{}.With(Frame{.width = 8.0F, .height = 1.0F}));
  content.emplace_back(
      Text(node.name)
          .Style(DrawerTextStyle(13.0F))
          .With(Frame{.max_height = 18.0F}, ClipChildren(), Grow()));
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
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
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
          .With(Spacing(6.0F), Padding(8.0F),
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

View FileBody(State<bool>, const DrawerModel &model,
              const DrawerActions &actions) {
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

View RenderDrawerWithInsets(State<bool> drawer_open,
                            const DrawerTabSelection& selection,
                            const DrawerModel& model,
                            const DrawerActions& actions,
                            EdgeInsets insets) {
  const auto& presentation = DrawerTabPresentationFor(selection.active);
  // The drawer spans the full window height, so its scrolling body pads the
  // navigation-bar inset itself: without it the last row ends up under the
  // three-button bar.
  View body = std::invoke(presentation.body, drawer_open, model, actions);
  if (insets.bottom > 0.0F) {
    body = Column{std::move(body)}.With(
        Grow(), Padding(EdgeInsets{.bottom = insets.bottom}));
  }
  return LegacyDrawerViewport{Column{
      Header(presentation, actions), DrawerTabs(selection, actions),
      std::move(body),
  }.With(Frame{.min_width = 240.0F, .max_width = kDrawerWidth},
         CrossAlign(CrossAxisAlignment::Stretch),
         Background(colors::background))
                                  .LayoutValue<LegacyDrawerViewport::InsetsValue>(
                                      insets)};
}

#if defined(__ANDROID__)
[[huxerui::composable]] View
RenderDrawer(State<bool> drawer_open, const DrawerTabSelection& selection,
             const DrawerModel& model, const DrawerActions& actions) {
  const auto window_insets =
      UseService<application::WindowInsetsProvider>()->Current();
  return RenderDrawerWithInsets(
      drawer_open, selection, model, actions,
      EdgeInsets{
          .top = window_insets.top,
          .right = window_insets.right,
          .bottom = window_insets.bottom,
          .left = window_insets.left,
      });
}
#else
View RenderDrawer(State<bool> drawer_open, const DrawerTabSelection& selection,
                  const DrawerModel& model, const DrawerActions& actions) {
  return RenderDrawerWithInsets(drawer_open, selection, model, actions, {});
}
#endif

} // namespace

DrawerStyle LegacyDrawerStyle() {
  return DrawerStyle{
      .background = colors::background,
      .scrim = colors::overlay,
      .shadow = Shadow{.color = Color::Transparent()},
      .preferred_width = kDrawerWidth,
      .minimum_width = 240.0F,
      .minimum_content_width = kDrawerWidth,
      .modal_content_reveal = kDrawerReveal,
      // Keep the drag recognizer clear of the legacy menu icon's 24dp centre.
      // HuxerUI gives the edge drag priority inside this strip, so matching the
      // framework default here would consume taps at the icon centre on
      // Android.
      .edge_drag_width = 16.0F,
      .corner_radius = 0.0F,
      .motion =
          DrawerMotion{
              .open = TweenSpec{.duration = 0.18, .easing = Easing::EaseOut},
              .close = TweenSpec{.duration = 0.15, .easing = Easing::EaseIn},
          },
  };
}

View Drawer(State<bool> drawer_open, State<DrawerTab> selected_tab) {
  return Drawer(drawer_open, selected_tab, DrawerModel{}, DrawerActions{});
}

View Drawer(State<bool> drawer_open, State<DrawerTab> selected_tab,
            const DrawerModel& model, const DrawerActions& actions) {
  const DrawerTabSelection selection{
      .active = selected_tab.Get(),
      .select = [selected_tab](DrawerTab tab) { selected_tab = tab; },
  };
  return RenderDrawer(drawer_open, selection, model, actions);
}

} // namespace linecode::presentation
