#include "presentation/screens/prompt_templates_screen.h"

#include <array>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/prompt_template_repository.h"
#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/legacy_text_presentation.h"
#include "presentation/line_theme.h"
#include "presentation/prompt_template_presentation.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;

const std::array kPromptTemplateTextResources{
    app::strings::prompt_template_system_prompt_title,
    app::strings::prompt_template_system_prompt_description,
    app::strings::prompt_template_work_directory_title,
    app::strings::prompt_template_work_directory_description,
    app::strings::prompt_template_tone_coding_title,
    app::strings::prompt_template_tone_coding_description,
    app::strings::prompt_template_tone_chat_title,
    app::strings::prompt_template_tone_chat_description,
    app::strings::prompt_template_chat_mode_chat_title,
    app::strings::prompt_template_chat_mode_chat_description,
    app::strings::prompt_template_chat_mode_plan_title,
    app::strings::prompt_template_chat_mode_plan_description,
    app::strings::prompt_template_chat_mode_agent_title,
    app::strings::prompt_template_chat_mode_agent_description,
    app::strings::prompt_template_learning_context_title,
    app::strings::prompt_template_learning_context_description,
    app::strings::prompt_template_context_compaction_title,
    app::strings::prompt_template_context_compaction_description,
    app::strings::prompt_template_model_identity_title,
    app::strings::prompt_template_model_identity_description,
    app::strings::prompt_template_todo_state_title,
    app::strings::prompt_template_todo_state_description,
    app::strings::prompt_template_todo_usage_title,
    app::strings::prompt_template_todo_usage_description,
    app::strings::prompt_template_agent_role_explore_remote_title,
    app::strings::prompt_template_agent_role_explore_remote_description,
    app::strings::prompt_template_agent_role_coding_remote_title,
    app::strings::prompt_template_agent_role_coding_remote_description,
    app::strings::prompt_template_agent_role_explore_local_title,
    app::strings::prompt_template_agent_role_explore_local_description,
    app::strings::prompt_template_agent_role_coding_local_title,
    app::strings::prompt_template_agent_role_coding_local_description,
    app::strings::prompt_template_agent_system_prompt_title,
    app::strings::prompt_template_agent_system_prompt_description,
    app::strings::prompt_template_image_understanding_tool_system_title,
    app::strings::prompt_template_image_understanding_tool_system_description,
    app::strings::prompt_template_context_compaction_summary_prefix_title,
    app::strings::prompt_template_context_compaction_summary_prefix_description,
    app::strings::prompt_template_context_compaction_responses_fallback_title,
    app::strings::
        prompt_template_context_compaction_responses_fallback_description,
    app::strings::prompt_template_source_builtin_chat,
    app::strings::prompt_template_source_builtin_plan,
    app::strings::prompt_template_source_builtin_agent,
};

static_assert(kPromptTemplateTextResources.size() == PromptTemplateTextCount());

const auto kPromptTemplatePresentations =
    MakePromptTemplatePresentationRegistry([](PromptTemplateText key) {
      return kPromptTemplateTextResources[std::to_underlying(key)];
    });

struct EditorState final {
  domain::PromptTemplateItem item;
  TextEditingValue editing;
  bool busy{};

  bool operator==(const EditorState &) const = default;
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

TextFieldStyle PromptEditorFieldStyle() {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Standard;
  style.show_label = false;
  style.standard.background = colors::code;
  style.standard.border = colors::code_border;
  style.standard.hovered_border = colors::code_border;
  style.standard.focused_border = colors::code_border;
  style.standard.corner_radii = CornerRadii{8.0F};
  style.standard.minimum_height = 220.0F;
  style.text_style = TextStyle{Font::Monospace(13.0F), colors::text};
  style.placeholder_style = TextStyle{Font::Monospace(13.0F), colors::tertiary};
  style.padding = EdgeInsets::All(12.0F);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  style.focused_border_width = 1.0F;
  return style;
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_prompt_templates_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

std::string Variables(const domain::PromptTemplateDefinition &definition) {
  std::string result;
  for (const auto &variable : definition.variables) {
    if (!result.empty())
      result += ", ";
    result += "{{" + variable + "}}";
  }
  return result;
}

View Section(std::string title, View body) {
  return Column{
      Text(std::move(title))
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{
              .top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{std::move(body).With(
          CornerRadius(12.0F), Background(colors::elevated))},
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

View ActionButton(ImageResource icon, StringResource label,
                  std::function<void()> action) {
  return Row{
      Glyph(std::move(icon), 16.0F, colors::secondary),
      Text(label).Style(Label(11.0F, FontWeight::Regular, colors::secondary)),
  }
      .OnClick(std::move(action))
      .With(Frame{.height = 34.0F, .min_width = 72.0F}, Spacing(5.0F),
            Padding(EdgeInsets::Symmetric(8.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            MainAlign(MainAxisAlignment::Center),
            Background(colors::surface_light), CornerRadius(8.0F),
            Border{colors::border_light, 1.0F}, Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View Editor(std::size_t index, StringVariant description, std::string source,
            State<std::vector<EditorState>> editors,
            std::shared_ptr<application::PromptTemplateRepository> repository,
            TaskScope tasks, ToastHandle toast) {
  const auto snapshot = editors->at(index);
  const auto &definition = snapshot.item.definition;
  const auto save = [index, editors, repository, tasks, toast] {
    if (editors->at(index).busy)
      return;
    const auto id = editors->at(index).item.definition.id;
    const auto value = editors->at(index).editing.text;
    editors.Update([index](auto &next) { next.at(index).busy = true; });
    tasks.Launch([repository, id, value, index, editors,
                  toast]() -> Task<void> {
      auto result = co_await repository->Save(id, value);
      if (!result) {
        editors.Update([index](auto &next) { next.at(index).busy = false; });
        toast.Show(result.error().message);
        co_return;
      }
      editors.Update([index, value](auto &next) {
        auto &editor = next.at(index);
        editor.busy = false;
        editor.item.current_text = value;
        editor.item.customized = value != editor.item.definition.default_text;
      });
      toast.Show(app::strings::screen_prompt_templates_toast_saved);
    });
  };
  const auto reset = [index, editors, repository, tasks, toast] {
    if (editors->at(index).busy)
      return;
    const auto id = editors->at(index).item.definition.id;
    const auto default_text = editors->at(index).item.definition.default_text;
    editors.Update([index](auto &next) { next.at(index).busy = true; });
    tasks.Launch([repository, id, default_text, index, editors,
                  toast]() -> Task<void> {
      auto result = co_await repository->Reset(id);
      if (!result) {
        editors.Update([index](auto &next) { next.at(index).busy = false; });
        toast.Show(result.error().message);
        co_return;
      }
      editors.Update([index, default_text](auto &next) {
        auto &editor = next.at(index);
        editor.busy = false;
        editor.editing = TextEditingValue::FromText(default_text);
        editor.item.current_text = default_text;
        editor.item.customized = false;
      });
      toast.Show(app::strings::screen_prompt_templates_toast_reset);
    });
  };

  ThemeDefinition field_theme;
  field_theme.Set(PromptEditorFieldStyle());
  // Theme is represented by an Environment node, which must remain a pure
  // environment boundary. Put layout/interaction behavior on its child.
  auto field = Theme(
      field_theme,
      TextField(snapshot.editing)
          .LineLimits(TextFieldLineLimits::MultiLine())
          .InputConfiguration(TextInputConfiguration{
              .type = TextInputType::Text,
              .capitalization = TextCapitalization::None,
              .action = TextInputAction::Newline,
              .multiline = true,
              .secure = false,
              .autocorrect = false})
          .VerticalAlign(TextVerticalAlign::Top)
          .OnChanged([index, editors](const TextEditingValue &value) {
            // State::Update owns its mutation callback. Keep the new
            // controlled value alive independently of the input event.
            editors.Update([index, value](auto &next) {
              next.at(index).editing = value;
            });
          })
          .With(Frame{.min_height = 220.0F}, Enabled(!snapshot.busy),
                Padding(EdgeInsets{.top = 12.0F})));

  auto actions =
      Row{
          Text(snapshot.item.customized
                   ? app::strings::screen_prompt_templates_status_custom
                   : app::strings::screen_prompt_templates_status_built_in)
              .Style(Label(11.0F, FontWeight::Regular,
                           snapshot.item.customized ? colors::accent
                                                    : colors::tertiary))
              .With(Grow()),
          ActionButton(app::images::rotate_ccw, app::strings::common_reset,
                       reset)
              .With(Enabled(!snapshot.busy)),
          ActionButton(app::images::save, app::strings::common_save, save)
              .With(Enabled(!snapshot.busy)),
      }
          .With(Frame{.height = 34.0F}, Spacing(8.0F),
                CrossAlign(CrossAxisAlignment::Center));

  return Column{
      Text(std::move(description))
          .Style(Label(13.0F, FontWeight::Regular, colors::secondary)),
      Text::Format(app::strings::screen_prompt_templates_source, source,
                   Variables(definition))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 8.0F})),
      std::move(field),
      std::move(actions).With(Padding(EdgeInsets{.top = 12.0F})),
  }
      .With(Padding(EdgeInsets::All(16.0F)),
            CrossAlign(CrossAxisAlignment::Stretch));
}
} // namespace

[[huxerui::composable]] View PromptTemplatesScreen(
    std::shared_ptr<application::PromptTemplateRepository> repository) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto editors = UseState(std::vector<EditorState>{});
  Lifecycle([tasks, repository, editors, toast] {
    tasks.Launch([repository, editors, toast]() -> Task<void> {
      auto loaded = co_await repository->Load();
      if (!loaded) {
        toast.Show(loaded.error().message);
        co_return;
      }
      std::vector<EditorState> next;
      next.reserve(loaded->size());
      for (auto &item : *loaded) {
        auto editing = TextEditingValue::FromText(item.current_text);
        next.push_back(
            {.item = std::move(item), .editing = std::move(editing)});
      }
      editors = std::move(next);
    });
  });

  std::vector<View> content;
  std::string intro =
      UseString(app::strings::screen_prompt_templates_variables);
  std::string item_prefix = "\n\n- ";
  for (std::size_t index = 0; index < editors->size(); ++index) {
    const auto &definition = editors->at(index).item.definition;
    const auto *presentation = FindPromptTemplatePresentation(
        kPromptTemplatePresentations, definition.id);
    const StringVariant unknown = StringVariant::Format(
        app::strings::prompt_template_error_unknown, definition.id);
    const StringVariant title =
        presentation ? StringVariant{presentation->title} : unknown;
    const StringVariant description =
        presentation ? StringVariant{presentation->description} : unknown;
    intro += item_prefix + UseString(title) +
             UseString(app::strings::screen_prompt_templates_item_separator) +
             UseString(description);
    item_prefix = "\n- ";
    const auto variables = Variables(definition);
    if (!variables.empty()) {
      intro += UseString(app::strings::screen_prompt_templates_item_variables,
                         variables);
    }
  }
  content.push_back(
      Section(LegacySectionTitle(
                  UseString(app::strings::screen_prompt_templates_section)),
              Text(intro)
                  .Style(Label(13.0F, FontWeight::Regular, colors::secondary))
                  .With(Padding(EdgeInsets::All(16.0F)))));
  for (std::size_t index = 0; index < editors->size(); ++index) {
    const auto &definition = editors->at(index).item.definition;
    const auto *presentation = FindPromptTemplatePresentation(
        kPromptTemplatePresentations, definition.id);
    const StringVariant unknown = StringVariant::Format(
        app::strings::prompt_template_error_unknown, definition.id);
    const StringVariant title =
        presentation ? StringVariant{presentation->title} : unknown;
    const StringVariant description =
        presentation ? StringVariant{presentation->description} : unknown;
    std::string source = definition.source;
    if (presentation && presentation->builtin_source) {
      source = UseString(*presentation->builtin_source);
    }
    content.push_back(
        Section(LegacySectionTitle(UseString(title)),
                Editor(index, description, std::move(source), editors,
                       repository, tasks, toast))
            .Key(editors->at(index).item.definition.id));
  }
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}));

  return Column{
      Header(navigation),
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
