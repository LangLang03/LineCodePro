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
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;

struct PromptMeta final { StringResource title; StringResource description; };

const std::array<PromptMeta, 20> kPromptMeta{{
    {app::strings::prompt_template_system_prompt_title, app::strings::prompt_template_system_prompt_description},
    {app::strings::prompt_template_work_directory_title, app::strings::prompt_template_work_directory_description},
    {app::strings::prompt_template_tone_coding_title, app::strings::prompt_template_tone_coding_description},
    {app::strings::prompt_template_tone_chat_title, app::strings::prompt_template_tone_chat_description},
    {app::strings::prompt_template_chat_mode_chat_title, app::strings::prompt_template_chat_mode_chat_description},
    {app::strings::prompt_template_chat_mode_plan_title, app::strings::prompt_template_chat_mode_plan_description},
    {app::strings::prompt_template_chat_mode_agent_title, app::strings::prompt_template_chat_mode_agent_description},
    {app::strings::prompt_template_learning_context_title, app::strings::prompt_template_learning_context_description},
    {app::strings::prompt_template_context_compaction_title, app::strings::prompt_template_context_compaction_description},
    {app::strings::prompt_template_model_identity_title, app::strings::prompt_template_model_identity_description},
    {app::strings::prompt_template_todo_state_title, app::strings::prompt_template_todo_state_description},
    {app::strings::prompt_template_todo_usage_title, app::strings::prompt_template_todo_usage_description},
    {app::strings::prompt_template_agent_role_explore_remote_title, app::strings::prompt_template_agent_role_explore_remote_description},
    {app::strings::prompt_template_agent_role_coding_remote_title, app::strings::prompt_template_agent_role_coding_remote_description},
    {app::strings::prompt_template_agent_role_explore_local_title, app::strings::prompt_template_agent_role_explore_local_description},
    {app::strings::prompt_template_agent_role_coding_local_title, app::strings::prompt_template_agent_role_coding_local_description},
    {app::strings::prompt_template_agent_system_prompt_title, app::strings::prompt_template_agent_system_prompt_description},
    {app::strings::prompt_template_image_understanding_tool_system_title, app::strings::prompt_template_image_understanding_tool_system_description},
    {app::strings::prompt_template_context_compaction_summary_prefix_title, app::strings::prompt_template_context_compaction_summary_prefix_description},
    {app::strings::prompt_template_context_compaction_responses_fallback_title, app::strings::prompt_template_context_compaction_responses_fallback_description},
}};

struct EditorState final {
  domain::PromptTemplateItem item;
  TextEditingValue editing;

  bool operator==(const EditorState &) const = default;
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon)).Tint(tint).With(Frame{.width = size, .height = size});
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
          .With(Grow(), Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F}, Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

std::string Variables(const domain::PromptTemplateDefinition &definition) {
  std::string result;
  for (const auto &variable : definition.variables) {
    if (!result.empty()) result += ", ";
    result += "{{" + variable + "}}";
  }
  return result;
}

View Section(StringResource title, View body) {
  return Column{
      Text(title).Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 20.0F, .right = 16.0F,
                                   .bottom = 12.0F, .left = 16.0F})),
      LegacySettingsCardFrame{std::move(body).With(
          CornerRadius(12.0F), Background(colors::elevated))},
  }.With(CrossAlign(CrossAxisAlignment::Stretch));
}

View ActionButton(ImageResource icon, StringResource label, std::function<void()> action) {
  return Row{
      Glyph(std::move(icon), 16.0F, colors::secondary),
      Text(label).Style(Label(11.0F, FontWeight::Regular, colors::secondary)),
  }.OnClick(std::move(action))
      .With(Frame{.height = 34.0F, .min_width = 72.0F}, Spacing(5.0F),
            Padding(EdgeInsets::Symmetric(8.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            MainAlign(MainAxisAlignment::Center), Background(colors::surface_light),
            CornerRadius(8.0F), Border{colors::border_light, 1.0F},
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View Editor(std::size_t index, const PromptMeta &meta,
            std::string source,
            State<std::vector<EditorState>> editors,
            std::shared_ptr<application::PromptTemplateRepository> repository,
            TaskScope tasks, ToastHandle toast) {
  const auto snapshot = editors->at(index);
  const auto &definition = snapshot.item.definition;
  const auto save = [index, editors, repository, tasks, toast] {
    const auto id = editors->at(index).item.definition.id;
    const auto value = editors->at(index).editing.text;
    editors.Update([index, &value](auto &next) {
      auto &editor = next.at(index);
      editor.item.current_text = value;
      editor.item.customized = value != editor.item.definition.default_text;
    });
    tasks.Launch([repository, id, value, toast]() -> Task<void> {
      auto result = co_await repository->Save(id, value);
      if (!result) toast.Show(result.error().message);
    });
    toast.Show(app::strings::screen_prompt_templates_toast_saved);
  };
  const auto reset = [index, editors, repository, tasks, toast] {
    const auto id = editors->at(index).item.definition.id;
    const auto default_text = editors->at(index).item.definition.default_text;
    editors.Update([index, &default_text](auto &next) {
      auto &editor = next.at(index);
      editor.editing = TextEditingValue::FromText(default_text);
      editor.item.current_text = default_text;
      editor.item.customized = false;
    });
    tasks.Launch([repository, id, toast]() -> Task<void> {
      auto result = co_await repository->Reset(id);
      if (!result) toast.Show(result.error().message);
    });
    toast.Show(app::strings::screen_prompt_templates_toast_reset);
  };

  auto field = TextField(snapshot.editing)
      .Variant(TextFieldVariant::Standard)
      .LineLimits(TextFieldLineLimits::MultiLine(10))
      .InputConfiguration(TextInputConfiguration{
          .type = TextInputType::Text, .capitalization = TextCapitalization::None,
          .action = TextInputAction::Newline, .multiline = true,
          .secure = false, .autocorrect = false})
      .VerticalAlign(TextVerticalAlign::Top)
      .OnChanged([index, editors](const TextEditingValue &value) {
        editors.Update([index, &value](auto &next) { next.at(index).editing = value; });
      })
      .With(Frame{.min_height = 220.0F}, FontSize(13.0F), Foreground(colors::text),
            Padding(EdgeInsets::All(12.0F)), Background(colors::code),
            CornerRadius(8.0F), Border{colors::code_border, 1.0F});

  auto actions = Row{
      Text(snapshot.item.customized ? app::strings::screen_prompt_templates_status_custom
                                    : app::strings::screen_prompt_templates_status_built_in)
          .Style(Label(11.0F, FontWeight::Regular,
                       snapshot.item.customized ? colors::accent : colors::tertiary))
          .With(Grow()),
      ActionButton(app::images::rotate_ccw, app::strings::common_reset, reset),
      ActionButton(app::images::save, app::strings::common_save, save)
          .With(Padding(EdgeInsets{.left = 8.0F})),
  }.With(Frame{.height = 34.0F}, CrossAlign(CrossAxisAlignment::Center));

  return Column{
      Text(meta.description).Style(Label(13.0F, FontWeight::Regular, colors::secondary)),
      Text::Format(app::strings::screen_prompt_templates_source, source,
                   Variables(definition))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 8.0F})),
      std::move(field).With(Padding(EdgeInsets{.top = 12.0F})),
      std::move(actions).With(Padding(EdgeInsets{.top = 12.0F})),
  }.With(Padding(EdgeInsets::All(16.0F)), CrossAlign(CrossAxisAlignment::Stretch));
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
        next.push_back({.item = std::move(item),
                        .editing = std::move(editing)});
      }
      editors = std::move(next);
    });
  });

  std::vector<View> content;
  std::string intro = UseString(app::strings::screen_prompt_templates_variables);
  for (std::size_t index = 0; index < editors->size() && index < kPromptMeta.size(); ++index) {
    const auto &definition = editors->at(index).item.definition;
    intro += "\n\n- " + UseString(kPromptMeta[index].title) +
             UseString(app::strings::screen_prompt_templates_item_separator) +
             UseString(kPromptMeta[index].description);
    const auto variables = Variables(definition);
    if (!variables.empty()) {
      intro += UseString(app::strings::screen_prompt_templates_item_variables, variables);
    }
  }
  content.push_back(Section(
      app::strings::screen_prompt_templates_section,
      Text(intro).Style(Label(13.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets::All(16.0F))))) ;
  for (std::size_t index = 0; index < editors->size() && index < kPromptMeta.size(); ++index) {
    const auto &definition = editors->at(index).item.definition;
    std::string source = definition.source;
    if (definition.id == "chatModeChat")
      source = UseString(app::strings::prompt_template_source_builtin_chat);
    else if (definition.id == "chatModePlan")
      source = UseString(app::strings::prompt_template_source_builtin_plan);
    else if (definition.id == "chatModeAgent")
      source = UseString(app::strings::prompt_template_source_builtin_agent);
    content.push_back(Section(kPromptMeta[index].title,
                              Editor(index, kPromptMeta[index], std::move(source), editors,
                                     repository, tasks, toast))
                          .Key(editors->at(index).item.definition.id));
  }
  content.push_back(Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}));

  return Column{
      Header(navigation), Divider(),
      ScrollView(Column(std::move(content))
          .With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background)))
          .ScrollAxis(Axis::Vertical).With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background),
         SafeAreaPadding{});
}

} // namespace linecode::presentation
