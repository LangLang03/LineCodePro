#include "presentation/screens/tool_call_preview_screen.h"

#include <array>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

View PreviewGroup(StringResource category, View preview) {
  return Column{
      Text(category)
          .Style(Label(11.0F, FontWeight::Bold, colors::secondary))
          .With(Padding(EdgeInsets{.bottom = 4.0F})),
      preview,
  }
      .With(Padding(EdgeInsets{
                .top = 12.75F,
                .right = 16.0F,
                .bottom = 0.0F,
                .left = 16.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View SimpleToolRow(ImageResource icon, StringResource text,
                   bool show_chevron = true) {
  std::vector<View> children{
      Stack{Glyph(std::move(icon), 16.0F, colors::secondary)}.With(
          Frame{.width = 24.0F, .height = 32.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Text(text)
          .Style(Label(14.0F, FontWeight::Regular, colors::secondary))
          .With(Grow()),
  };
  if (show_chevron)
    children.push_back(Stack{Glyph(app::images::chevron_right, 14.0F,
                                   colors::tertiary)}
                           .With(Frame{.width = 24.0F, .height = 32.0F},
                                 Align(HorizontalAlignment::Center,
                                       VerticalAlignment::Center)));
  return Row(std::move(children))
      .With(Frame{.min_height = 46.25F}, Spacing(6.0F),
            CrossAlign(CrossAxisAlignment::Center));
}

View AgentCard() {
  return Column{
      Row{
          Stack{Glyph(app::images::bot, 14.0F, colors::accent)}.With(
              Frame{.width = 28.0F, .height = 28.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Border(colors::accent, 1.0F), CornerRadius(14.0F)),
          Column{
              Text(app::strings::toolcall_preview_agent_title)
                  .Style(Label(13.0F, FontWeight::Bold)),
              Text(app::strings::toolcall_preview_agent_badge)
                  .Style(Label(10.0F, FontWeight::Bold, colors::accent))
                  .With(Padding(EdgeInsets::Symmetric(4.0F, 1.0F)),
                        Background(colors::code),
                        Border(colors::code_border, 1.0F),
                        CornerRadius(999.0F)),
          }
              .With(Spacing(3.0F), Grow()),
          ProgressCircle().With(Frame{.width = 18.0F, .height = 18.0F}),
          Text(app::strings::toolcall_preview_running)
              .Style(Label(11.0F, FontWeight::Bold, colors::accent)),
          Glyph(app::images::chevron_down, 16.0F, colors::tertiary),
      }
          .With(Frame{.min_height = 48.0F}, Spacing(8.0F),
                Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                CrossAlign(CrossAxisAlignment::Center)),
      Stack{}.With(Frame{.height = 1.0F}, Background(colors::code_border)),
      Text(app::strings::toolcall_preview_agent_body)
          .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 8.0F,
                                   .right = 12.0F,
                                   .bottom = 6.0F,
                                   .left = 12.0F})),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), ClipChildren());
}

View PipelineCard() {
  return Column{
      Row{
          Stack{Glyph(app::images::git_branch, 15.0F, colors::accent)}.With(
              Frame{.width = 30.0F, .height = 30.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Border(colors::code_border, 1.0F), CornerRadius(15.0F)),
          Column{
              Text(app::strings::toolcall_preview_pipeline_title)
                  .Style(Label(13.0F, FontWeight::Bold)),
              Row{
                  Stack{Glyph(app::images::check, 7.0F, colors::success)}.With(
                      Frame{.width = 10.0F, .height = 10.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Border(colors::success, 1.0F), CornerRadius(5.0F)),
                  Text(app::strings::toolcall_preview_pipeline_complete)
                      .Style(Label(11.0F, FontWeight::Bold, colors::success)),
              }.With(Spacing(3.0F), CrossAlign(CrossAxisAlignment::Center)),
          }
              .With(Spacing(3.0F), Grow()),
          Stack{Glyph(app::images::check, 11.0F, colors::success)}.With(
              Frame{.width = 18.0F, .height = 18.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Border(colors::success, 1.0F), CornerRadius(9.0F)),
          Glyph(app::images::chevron_down, 16.0F, colors::tertiary),
      }
          .With(Frame{.min_height = 48.0F}, Spacing(4.0F),
                Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                CrossAlign(CrossAxisAlignment::Center)),
      Stack{}.With(Frame{.height = 1.0F}, Background(colors::code_border)),
      Text(app::strings::toolcall_preview_pipeline_body)
          .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 8.0F,
                                   .right = 8.0F,
                                   .bottom = 1.5F,
                                   .left = 8.0F})),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), Border(colors::border_light, 1.0F),
            CornerRadius(8.0F), ClipChildren());
}

View ShellCompletePreview() {
  return SimpleToolRow(app::images::terminal,
                       app::strings::toolcall_preview_shell_complete);
}

View ShellRunningPreview() {
  return SimpleToolRow(app::images::terminal,
                       app::strings::toolcall_preview_shell_running);
}

View WritePreview() {
  return SimpleToolRow(app::images::file_pen_line,
                       app::strings::toolcall_preview_write_review);
}

View DeletePreview() {
  return SimpleToolRow(app::images::trash_2,
                       app::strings::toolcall_preview_delete_complete);
}

View TodoPreview() {
  return Text(app::strings::toolcall_preview_todo_empty)
      .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
      .With(Padding(EdgeInsets{.top = 8.0F,
                               .right = 16.0F,
                               .bottom = 8.75F,
                               .left = 16.0F}));
}

View GenericPreview() {
  return SimpleToolRow(app::images::mcp,
                       app::strings::toolcall_preview_generic_complete);
}

View ReadPreview() {
  return SimpleToolRow(app::images::file,
                       app::strings::toolcall_preview_read_complete, false);
}

View ImagePreview() {
  return SimpleToolRow(app::images::sparkles,
                       app::strings::toolcall_preview_image_complete, false);
}

using PreviewFactory = View (*)();

struct PreviewEntry final {
  StringResource category;
  PreviewFactory factory;
};

const std::array preview_entries{
    // The legacy preview enumerates two HashMaps.  Its observable Android
    // order is captured explicitly here instead of relying on container
    // iteration order, so the migration stays deterministic and OCP-friendly.
    PreviewEntry{app::strings::toolcall_preview_category_delete,
                 &DeletePreview},
    PreviewEntry{app::strings::toolcall_preview_category_shell,
                 &ShellCompletePreview},
    PreviewEntry{app::strings::toolcall_preview_category_shell,
                 &ShellRunningPreview},
    PreviewEntry{app::strings::toolcall_preview_category_todo, &TodoPreview},
    PreviewEntry{app::strings::toolcall_preview_category_agent, &AgentCard},
    PreviewEntry{app::strings::toolcall_preview_category_pipeline,
                 &PipelineCard},
    PreviewEntry{app::strings::toolcall_preview_category_read, &ReadPreview},
    PreviewEntry{app::strings::toolcall_preview_category_generic,
                 &GenericPreview},
    PreviewEntry{app::strings::toolcall_preview_category_write, &WritePreview},
    // Phone Control appeared here in the old HashMap order. It is the explicit
    // migration exception, so the following image sample closes that gap.
    PreviewEntry{app::strings::toolcall_preview_category_image, &ImagePreview},
};

} // namespace

[[huxerui::composable]] View ToolCallPreviewScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> content;
  content.reserve(preview_entries.size() + 2U);
  content.push_back(
      Text(app::strings::toolcall_preview_section_note)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 4.0F, .right = 16.0F, .bottom = 0.0F, .left = 16.0F})));
  for (const auto &entry : preview_entries)
    content.push_back(PreviewGroup(entry.category, entry.factory()));
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}));

  return Column{
      LegacySettingsPageHeader(app::strings::screen_toolcall_preview_title,
                               [navigation] { navigation.Pop(); }),
      LegacyScreenHeaderDivider(),
      ScrollView(Column(std::move(content))
                     .With(CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
