#include "presentation/screens/model_add_options_screen.h"

#include <functional>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"
#include "presentation/model_protocol_presentation.h"
#include "presentation/model_provider_preset_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon)
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

View Header(const ModelAddOptionsActions &actions) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([callback = actions.on_back] {
            if (callback)
              std::invoke(callback);
          })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::model_add_options_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Offset(Point{0.0F, 0.76F})),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View OptionCard(ImageResource icon, StringVariant title, StringVariant detail,
                std::function<void()> action) {
  constexpr float vertical_padding = 15.8F;
  return Row{
      Stack{Glyph(icon, 22.0F, colors::accent)}.With(
          Frame{.width = 44.0F, .height = 44.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(title).Style(Label(16.0F, FontWeight::Bold)),
          Stack {}.With(Frame{.height = 4.0F}),
          Text(detail)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Grow(), Offset(Point{0.38F, -1.9F})),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary),
  }
      .OnClick([action = std::move(action)] {
        if (action)
          std::invoke(action);
      })
      .With(Frame{.min_height = 92.6F}, Spacing(12.0F),
            Padding(EdgeInsets{.top = vertical_padding,
                               .right = 16.0F,
                               .bottom = vertical_padding,
                               .left = 16.0F}),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(12.0F),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View PresetRow(domain::ModelProviderPreset preset,
               const ModelProviderPresetPresentation &presentation,
               const ModelAddOptionsActions &actions) {
  const auto &row = presentation.row;
  const StringResource protocol =
      ModelProtocolPresentationFor(preset.protocol).descriptive_name;
  const TextStyle subtitle =
      Label(11.0F, FontWeight::Regular, colors::tertiary);
  return Row{
      Stack{Text(presentation.initial)
                .Style(Label(16.0F, FontWeight::Bold, colors::accent))}
          .With(Frame{.width = 38.0F, .height = 38.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(presentation.label).Style(Label(16.0F, FontWeight::Bold)),
          Row{Text(presentation.description).Style(subtitle),
              Text(" · ").Style(subtitle), Text(protocol).Style(subtitle)}
              .With(Padding(EdgeInsets{.top = 3.0F})),
      }
          .With(Grow(), Offset(Point{0.38F, row.text_offset_y})),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary),
  }
      .OnClick([callback = actions.on_preset, preset] {
        if (callback)
          std::invoke(callback, preset);
      })
      .With(Frame{.min_height = row.minimum_height},
            Spacing(12.0F),
            Padding(EdgeInsets{.top = row.vertical_padding,
                               .right = 12.0F,
                               .bottom = row.vertical_padding,
                               .left = 12.0F}),
            CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(12.0F),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

} // namespace

[[huxerui::composable]] View
ModelAddOptionsScreen(ModelAddOptionsActions actions) {
  std::vector<View> content;
  content.reserve(model_provider_preset_presentations.size() + 8);
  content.push_back(OptionCard(
      app::images::sliders_horizontal, app::strings::model_add_custom,
      app::strings::model_add_custom_desc, actions.on_custom));
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 8.4F}));
  content.push_back(
      OptionCard(app::images::file_up, app::strings::model_add_local,
                 app::strings::model_add_local_desc, actions.on_local));
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 8.4F}));
  content.push_back(Row{
      Glyph(app::images::boxes, 16.0F, colors::secondary),
      Text(app::strings::model_add_presets)
          .Style(Label(13.0F, FontWeight::Bold, colors::secondary)),
  }
                        .With(Spacing(4.0F),
                              Padding(EdgeInsets{.top = 20.35F,
                                                 .bottom = 8.35F}),
                              Offset(Point{0.0F, 0.38F}),
                              CrossAlign(CrossAxisAlignment::Center)));
  for (const auto &presentation : model_provider_preset_presentations) {
    const auto &preset = domain::ModelProviderPresetFor(presentation.kind);
    content.push_back(PresetRow(preset, presentation, actions));
    content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 8.45F}));
  }

  return Column{
      Header(actions),
      Divider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(EdgeInsets{.top = 16.35F,
                                              .right = 16.0F,
                                              .bottom = 100.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
