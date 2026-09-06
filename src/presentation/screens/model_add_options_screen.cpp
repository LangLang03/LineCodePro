#include "presentation/screens/model_add_options_screen.h"

#include <functional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
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
      Spacer().With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View OptionCard(ImageResource icon, StringVariant title, StringVariant detail,
                std::function<void()> action) {
  constexpr float vertical_padding = 15.8F;
  return Row{
      Stack{Glyph(std::move(icon), 22.0F, colors::accent)}.With(
          Frame{.width = 44.0F, .height = 44.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::surface_light), CornerRadius(8.0F)),
      Column{
          Text(std::move(title)).Style(Label(16.0F, FontWeight::Bold)),
          Text(std::move(detail))
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 4.0F})),
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

struct PresetCopy final {
  StringResource label;
  StringResource description;
  std::string_view initial;
};

PresetCopy PresetText(std::string_view id) {
  if (id == "glm")
    return {app::strings::model_preset_glm_label,
            app::strings::model_preset_glm_desc, "G"};
  if (id == "mimo")
    return {app::strings::model_preset_mimo_label,
            app::strings::model_preset_mimo_desc, "M"};
  if (id == "mimo-token-plan")
    return {app::strings::model_preset_mimo_token_plan_label,
            app::strings::model_preset_mimo_token_plan_desc, "M"};
  if (id == "kimi")
    return {app::strings::model_preset_kimi_label,
            app::strings::model_preset_kimi_desc, "K"};
  if (id == "qwen")
    return {app::strings::model_preset_qwen_label,
            app::strings::model_preset_qwen_desc, "Q"};
  if (id == "openai")
    return {app::strings::model_preset_openai_label,
            app::strings::model_preset_openai_desc, "O"};
  if (id == "claude")
    return {app::strings::model_preset_claude_label,
            app::strings::model_preset_claude_desc, "C"};
  if (id == "gemini")
    return {app::strings::model_preset_gemini_label,
            app::strings::model_preset_gemini_desc, "G"};
  if (id == "openrouter")
    return {app::strings::model_preset_openrouter_label,
            app::strings::model_preset_openrouter_desc, "O"};
  if (id == "groq")
    return {app::strings::model_preset_groq_label,
            app::strings::model_preset_groq_desc, "G"};
  if (id == "together")
    return {app::strings::model_preset_together_label,
            app::strings::model_preset_together_desc, "T"};
  if (id == "siliconflow")
    return {app::strings::model_preset_siliconflow_label,
            app::strings::model_preset_siliconflow_desc, "S"};
  if (id == "minimax")
    return {app::strings::model_preset_minimax_label,
            app::strings::model_preset_minimax_desc, "M"};
  if (id == "ollama")
    return {app::strings::model_preset_ollama_label,
            app::strings::model_preset_ollama_desc, "O"};
  if (id == "lmstudio")
    return {app::strings::model_preset_lmstudio_label,
            app::strings::model_preset_lmstudio_desc, "L"};
  if (id == "codex")
    return {app::strings::model_preset_codex_label,
            app::strings::model_preset_codex_desc, "C"};
  return {app::strings::model_preset_deepseek_label,
          app::strings::model_preset_deepseek_desc, "D"};
}

StringResource ProtocolText(domain::ModelProtocol protocol) {
  switch (protocol) {
  case domain::ModelProtocol::codex_responses:
    return app::strings::model_protocol_codex;
  case domain::ModelProtocol::anthropic_messages:
    return app::strings::model_protocol_anthropic;
  case domain::ModelProtocol::local_gguf:
    return app::strings::model_protocol_local;
  case domain::ModelProtocol::openai_compatible:
    return app::strings::model_protocol_openai_compatible;
  }
  return app::strings::model_protocol_openai_compatible;
}

View PresetRow(domain::ModelProviderPreset preset,
               const ModelAddOptionsActions &actions) {
  const PresetCopy copy = PresetText(preset.id);
  const StringResource protocol = ProtocolText(preset.protocol);
  const bool wrapped_title = preset.id == "mimo-token-plan";
  const float vertical_padding = wrapped_title ? 12.0F : 11.0F;
  const TextStyle subtitle =
      Label(11.0F, FontWeight::Regular, colors::tertiary);
  return Row{
      Stack{Text(copy.initial)
                .Style(Label(16.0F, FontWeight::Bold, colors::accent))}
          .With(Frame{.width = 38.0F, .height = 38.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(copy.label).Style(Label(16.0F, FontWeight::Bold)),
          Row{Text(copy.description).Style(subtitle),
              Text(" · ").Style(subtitle), Text(protocol).Style(subtitle)}
              .With(Padding(EdgeInsets{.top = 3.0F})),
      }
          .With(Grow(),
                Offset(Point{0.38F, wrapped_title ? 1.14F : -0.76F})),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary),
  }
      .OnClick([callback = actions.on_preset, preset] {
        if (callback)
          std::invoke(callback, preset);
      })
      .With(Frame{.min_height = wrapped_title ? 65.7F : 61.35F},
            Spacing(12.0F),
            Padding(EdgeInsets{.top = vertical_padding,
                               .right = 12.0F,
                               .bottom = vertical_padding,
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
  content.reserve(domain::ModelProviderPresets().size() + 8);
  content.push_back(OptionCard(
      app::images::sliders_horizontal, app::strings::model_add_custom,
      app::strings::model_add_custom_desc, actions.on_custom));
  content.push_back(Spacer().With(Frame{.height = 8.4F}));
  content.push_back(
      OptionCard(app::images::file_up, app::strings::model_add_local,
                 app::strings::model_add_local_desc, actions.on_local));
  content.push_back(Spacer().With(Frame{.height = 8.4F}));
  content.push_back(Row{
      Glyph(app::images::boxes, 16.0F, colors::tertiary),
      Text(app::strings::model_add_presets)
          .Style(Label(13.0F, FontWeight::Bold, colors::tertiary)),
  }
                        .With(Spacing(8.0F),
                              Padding(EdgeInsets{.top = 20.35F,
                                                 .bottom = 8.35F}),
                              Offset(Point{0.0F, 0.38F}),
                              CrossAlign(CrossAxisAlignment::Center)));
  for (const auto preset : domain::ModelProviderPresets()) {
    content.push_back(PresetRow(preset, actions));
    content.push_back(Spacer().With(Frame{.height = 8.45F}));
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
