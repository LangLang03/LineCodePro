#include "presentation/screens/tutorial_screen.h"

#include <cstddef>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "domain/tutorial_document.h"
#include "infrastructure/tutorial_markdown_parser.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/tutorial_markdown.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class TutorialMode : std::size_t { simple = 0, pro = 1 };

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Header(const RouteNavigationController<domain::AppRoute>& navigation) {
  return LegacyScreenHeaderLayout {
    Stack {
      Image(app::images::chevron_left)
          .Tint(colors::text)
          .With(Frame{.width = 22.0F, .height = 22.0F}),
    }.OnClick([navigation] { navigation.Pop(); })
        .With(Frame{.width = 36.0F, .height = 36.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Focusable(), PointerCursor(PointerCursorKind::Hand)),
    Stack {
      Text(app::strings::screen_tutorial_title)
          .Style(Label(17.0F, FontWeight::Bold)),
    }.With(Grow(),
           Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
    Stack {}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F},
         Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

View ModeRow(StringResource title, StringResource description, bool selected,
             std::function<void()> select) {
  return Row {
    Column {
      Text(title).Style(Label(16.0F, FontWeight::Bold,
                              selected ? colors::accent : colors::text)),
      Text(description)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
    }.With(Spacing(2.0F), Grow()),
    selected ? Stack {
      Image(app::images::check)
          .Tint(colors::accent)
          .With(Frame{.width = 18.0F, .height = 16.0F}),
    }.With(Frame{.width = 18.0F, .height = 18.0F},
           Align(HorizontalAlignment::Center, VerticalAlignment::Center))
             : Stack {}.With(Frame{.width = 18.0F, .height = 18.0F}),
  }.OnClick(std::move(select))
      .With(Frame{.min_height = 62.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(selected ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View SectionChip(const domain::TutorialSection& section, std::size_t ordinal,
                 ScrollController controller) {
  const std::string label =
      std::to_string(ordinal + 1) + " " +
      infrastructure::TutorialMarkdownParser::ShortSectionTitle(section.title);
  return Stack {
    Text(label).Style(Label(11.0F, FontWeight::Bold, colors::secondary)),
  }.OnClick([controller, block = section.block_index] {
       controller.ScrollToItem(block + 1, ScrollAlignment::Start);
     })
      .With(Padding(EdgeInsets::Symmetric(12.0F, 5.0F)),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(14.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View Intro(TutorialMode mode, State<TutorialMode> selected_mode,
           const domain::TutorialDocument& document,
           ScrollController controller) {
  std::vector<View> chips;
  chips.reserve(document.sections.size());
  for (std::size_t index = 0; index < document.sections.size(); ++index)
    chips.push_back(SectionChip(document.sections[index], index, controller));

  return Column {
    Column {
      ModeRow(app::strings::screen_tutorial_simple_label,
              app::strings::screen_tutorial_simple_desc,
              mode == TutorialMode::simple,
              [selected_mode] { selected_mode = TutorialMode::simple; }),
      ModeRow(app::strings::screen_tutorial_pro_label,
              app::strings::screen_tutorial_pro_desc,
              mode == TutorialMode::pro,
              [selected_mode] { selected_mode = TutorialMode::pro; }),
    }.With(CrossAlign(CrossAxisAlignment::Stretch),
           Background(colors::elevated), Border(colors::border_light, 1.0F),
           CornerRadius(16.0F)),
    Text(app::strings::screen_tutorial_subtitle_brief)
        .Style(Label(14.0F, FontWeight::Regular, colors::secondary))
        .With(Padding(EdgeInsets{.top = 16.0F,
                                 .right = 0.0F,
                                 .bottom = 12.0F,
                                 .left = 0.0F})),
    ScrollView(Row(std::move(chips)).With(Spacing(8.0F)))
        .ScrollAxis(Axis::Horizontal)
        .With(Padding(EdgeInsets{.bottom = 12.0F})),
  }.With(Padding(EdgeInsets{.top = 12.0F,
                            .right = 16.0F,
                            .bottom = 0.0F,
                            .left = 16.0F}),
         CrossAlign(CrossAxisAlignment::Stretch));
}

std::shared_ptr<const domain::TutorialDocument> ParseDocument(
    RawAsset resource, std::string fallback) {
  infrastructure::TutorialMarkdownParser parser;
  const auto source =
      resource.HasValue() ? resource.ReadString(true) : std::move(fallback);
  return std::make_shared<const domain::TutorialDocument>(
      parser.Parse(source));
}

} // namespace

[[huxerui::composable]] View TutorialScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto controller = UseScrollController();
  auto mode = UseState(TutorialMode::simple);
  const auto simple = ParseDocument(UseRawResource(app::raw::tutorial_simple_md),
                                    UseString(app::strings::screen_tutorial_fallback));
  const auto pro = ParseDocument(UseRawResource(app::raw::tutorial_pro_md),
                                 UseString(app::strings::screen_tutorial_fallback));
  const auto document = mode.Get() == TutorialMode::simple ? simple : pro;
  const auto current_mode = mode.Get();

  // Virtual item factories run during measurement rather than composition.
  // Build the declarations here so localized Text/ScrollView descendants may
  // use their normal composition-backed state, then let the virtual factory
  // copy only already-declared Views. This also keeps section-index scrolling
  // without calling composition hooks from the lazy measurement callback.
  std::vector<View> items;
  items.reserve(document->blocks.size() + 1U);
  items.push_back(Intro(current_mode, mode, *document, controller).Key(0U));
  for (std::size_t index = 0; index < document->blocks.size(); ++index) {
    View block = TutorialMarkdownBlockView(document->blocks[index], true);
    const float bottom = index + 1U == document->blocks.size() ? 100.0F : 0.0F;
    items.push_back(
        Stack{std::move(block)}
            .With(Padding(EdgeInsets{.top = 0.0F,
                                     .right = 16.0F,
                                     .bottom = bottom,
                                     .left = 16.0F}))
            .Key(index + 1U));
  }

  auto list = VirtualList(std::move(items), [](const View &item) { return item; })
                  .EstimatedItemExtent(56.0F)
      .CacheExtent(640.0F)
      .Controller(controller)
      // Simple and pro reuse list indices for different block variants. Reset
      // the virtualized subtree when the mode changes so retained Markdown
      // controls cannot inherit state from a different document.
      .Key(current_mode);

  return Column {
    Header(navigation),
    LegacyScreenHeaderDivider(),
    std::move(list).With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch),
         Background(colors::background), SafeAreaPadding {});
}

} // namespace linecode::presentation
