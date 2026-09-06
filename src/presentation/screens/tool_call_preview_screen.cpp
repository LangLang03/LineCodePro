#include "presentation/screens/tool_call_preview_screen.h"

#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
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
                .top = 12.0F, .right = 16.0F, .bottom = 0.0F, .left = 16.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View SimpleToolRow(ImageResource icon, StringResource text,
                   bool show_chevron = true) {
  std::vector<View> children{
      Glyph(std::move(icon), 20.0F, colors::secondary),
      Text(text)
          .Style(Label(13.0F, FontWeight::Regular, colors::secondary))
          .With(Grow()),
  };
  if (show_chevron)
    children.push_back(
        Glyph(app::images::chevron_right, 17.0F, colors::tertiary));
  return Row(std::move(children))
      .With(Frame{.min_height = 56.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(4.0F, 8.0F)),
            CrossAlign(CrossAxisAlignment::Center));
}

View AgentCard() {
  return Column{
      Row{
          Stack{Glyph(app::images::brain, 20.0F, colors::text)}.With(
              Frame{.width = 36.0F, .height = 36.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Border(colors::secondary, 1.0F), CornerRadius(18.0F)),
          Column{
              Text(app::strings::toolcall_preview_agent_title)
                  .Style(Label(14.0F, FontWeight::Bold)),
              Text(app::strings::toolcall_preview_agent_badge)
                  .Style(Label(11.0F, FontWeight::Medium, colors::secondary))
                  .With(Padding(EdgeInsets::Symmetric(4.0F, 1.0F)),
                        Background(colors::surface_light), CornerRadius(6.0F)),
          }
              .With(Spacing(2.0F), Grow()),
          Text(app::strings::toolcall_preview_running)
              .Style(Label(13.0F, FontWeight::Bold, colors::secondary)),
          Glyph(app::images::chevron_down, 16.0F, colors::secondary),
      }
          .With(Spacing(8.0F), Padding(EdgeInsets::All(12.0F)),
                CrossAlign(CrossAxisAlignment::Center)),
      Divider(),
      Text(app::strings::toolcall_preview_agent_body)
          .Style(Label(11.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets::Symmetric(12.0F, 10.0F))),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(10.0F));
}

View PipelineCard() {
  return Column{
      Row{
          Stack{Glyph(app::images::git_branch, 20.0F, colors::text)}.With(
              Frame{.width = 36.0F, .height = 36.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Background(colors::surface_light), CornerRadius(18.0F)),
          Column{
              Text(app::strings::toolcall_preview_pipeline_title)
                  .Style(Label(14.0F, FontWeight::Bold)),
              Text(app::strings::toolcall_preview_pipeline_complete)
                  .Style(Label(11.0F, FontWeight::Bold, colors::success)),
          }
              .With(Spacing(2.0F), Grow()),
          Glyph(app::images::check, 18.0F, colors::success),
          Glyph(app::images::chevron_down, 16.0F, colors::secondary),
      }
          .With(Spacing(8.0F), Padding(EdgeInsets::All(12.0F)),
                CrossAlign(CrossAxisAlignment::Center)),
      Divider(),
      Text(app::strings::toolcall_preview_pipeline_body)
          .Style(Label(11.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets::Symmetric(8.0F, 10.0F))),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(10.0F));
}

} // namespace

[[huxerui::composable]] View ToolCallPreviewScreen() {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> content;
  content.reserve(12);
  content.push_back(
      Text(app::strings::toolcall_preview_section_note)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 4.0F, .right = 16.0F, .bottom = 0.0F, .left = 16.0F})));
  content.push_back(
      PreviewGroup(app::strings::toolcall_preview_category_agent, AgentCard()));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_todo,
      Text(app::strings::toolcall_preview_todo_empty)
          .Style(Label(11.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets::Symmetric(0.0F, 8.0F)))));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_generic,
      SimpleToolRow(app::images::package,
                    app::strings::toolcall_preview_generic_complete)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_shell,
      SimpleToolRow(app::images::terminal,
                    app::strings::toolcall_preview_shell_complete)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_shell,
      SimpleToolRow(app::images::terminal,
                    app::strings::toolcall_preview_shell_running)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_delete,
      SimpleToolRow(app::images::trash_2,
                    app::strings::toolcall_preview_delete_complete)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_read,
      SimpleToolRow(app::images::file,
                    app::strings::toolcall_preview_read_complete, false)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_pipeline, PipelineCard()));
  content.push_back(
      PreviewGroup(app::strings::toolcall_preview_category_write,
                   SimpleToolRow(app::images::file_code,
                                 app::strings::toolcall_preview_write_review)));
  content.push_back(PreviewGroup(
      app::strings::toolcall_preview_category_image,
      SimpleToolRow(app::images::sparkles,
                    app::strings::toolcall_preview_image_complete, false)));
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}));

  return Column{
      LegacySettingsPageHeader(app::strings::screen_toolcall_preview_title,
                               [navigation] { navigation.Pop(); }),
      Divider(),
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
