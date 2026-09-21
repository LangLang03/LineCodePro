#include "presentation/screens/tool_settings_screen.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <deque>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "domain/tool_settings.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/image_model_presentation.h"
#include "presentation/legacy_text_presentation.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using application::ToolSettingsChange;
using application::ToolSettingsService;
using domain::ImageModelPurpose;
using domain::ToolSettingsState;
using domain::WebSearchConfig;
using domain::WebSearchProvider;

struct ToolSettingsEditor final {
  ToolSettingsState settings;
  TextEditingValue base_url{
      TextEditingValue::FromText(settings.web_search.base_url)};
  TextEditingValue api_key{
      TextEditingValue::FromText(settings.web_search.api_key)};
  TextEditingValue model{TextEditingValue::FromText(settings.web_search.model)};
  TextEditingValue query_param{
      TextEditingValue::FromText(settings.web_search.query_param)};
  TextEditingValue api_key_header{
      TextEditingValue::FromText(settings.web_search.api_key_header)};
  TextEditingValue api_key_param{
      TextEditingValue::FromText(settings.web_search.api_key_param)};
  std::array<std::string, image_model_presentations.size()> image_model_labels;
  std::uint64_t edit_revision{};

  bool operator==(const ToolSettingsEditor &) const = default;
};

struct ToolSettingsPersistenceQueue final {
  std::deque<ToolSettingsChange> pending;
  bool running{};
  bool failed{};

  bool operator==(const ToolSettingsPersistenceQueue &) const = default;
};

using EditorTextMember = TextEditingValue ToolSettingsEditor::*;
using ConfigTextMember = std::string WebSearchConfig::*;

struct WebSearchFieldBinding final {
  EditorTextMember editor_value;
  ConfigTextMember config_value;
  StringResource label;
  StringVariant placeholder;
  bool secure;
  std::string_view key;
};

struct WebSearchProviderPresentation final {
  WebSearchProvider provider;
  StringResource label;
};

const std::array web_search_provider_presentations{
    WebSearchProviderPresentation{
        WebSearchProvider::bing_rss_free,
        app::strings::screen_tools_provider_bing_rss_free},
    WebSearchProviderPresentation{WebSearchProvider::tavily,
                                  app::strings::screen_tools_provider_tavily},
    WebSearchProviderPresentation{WebSearchProvider::brave_search,
                                  app::strings::screen_tools_provider_brave},
    WebSearchProviderPresentation{WebSearchProvider::serp_api,
                                  app::strings::screen_tools_provider_serpapi},
    WebSearchProviderPresentation{WebSearchProvider::bing_search,
                                  app::strings::screen_tools_provider_bing},
    WebSearchProviderPresentation{WebSearchProvider::custom,
                                  app::strings::screen_tools_provider_custom},
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Gap(float height) {
  return Stack{}.With(Frame{.width = 1.0F, .height = height});
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

ToolSettingsEditor MakeEditor(ToolSettingsState settings) {
  ToolSettingsEditor editor;
  editor.settings = std::move(settings);
  editor.base_url =
      TextEditingValue::FromText(editor.settings.web_search.base_url);
  editor.api_key =
      TextEditingValue::FromText(editor.settings.web_search.api_key);
  editor.model = TextEditingValue::FromText(editor.settings.web_search.model);
  editor.query_param =
      TextEditingValue::FromText(editor.settings.web_search.query_param);
  editor.api_key_header =
      TextEditingValue::FromText(editor.settings.web_search.api_key_header);
  editor.api_key_param =
      TextEditingValue::FromText(editor.settings.web_search.api_key_param);
  return editor;
}

std::array<WebSearchFieldBinding, 6> WebSearchFieldBindings() {
  return {{
      {&ToolSettingsEditor::base_url, &WebSearchConfig::base_url,
       app::strings::screen_tools_field_search_url,
       "https://api.example.com/search", false, "tool-web-base-url"},
      {&ToolSettingsEditor::api_key, &WebSearchConfig::api_key,
       app::strings::screen_tools_field_api_key,
       app::strings::screen_tools_hint_api_key, true, "tool-web-api-key"},
      {&ToolSettingsEditor::model, &WebSearchConfig::model,
       app::strings::screen_tools_field_search_model,
       app::strings::screen_tools_hint_model, false, "tool-web-model"},
      {&ToolSettingsEditor::query_param, &WebSearchConfig::query_param,
       app::strings::screen_tools_field_query_param, "q", false,
       "tool-web-query-param"},
      {&ToolSettingsEditor::api_key_header, &WebSearchConfig::api_key_header,
       app::strings::screen_tools_field_key_header,
       app::strings::screen_tools_hint_key_header, false,
       "tool-web-key-header"},
      {&ToolSettingsEditor::api_key_param, &WebSearchConfig::api_key_param,
       app::strings::screen_tools_field_key_query,
       app::strings::screen_tools_hint_key_query, false, "tool-web-key-query"},
  }};
}

Task<std::string>
ResolveModelLabel(std::shared_ptr<application::ModelStore> models,
                  std::string id) {
  if (!models || id.empty())
    co_return std::string{};
  auto model = co_await models->Find(id);
  if (!model || !model->has_value())
    co_return std::string{};
  std::string label = (**model).name;
  if (!(**model).model_id.empty()) {
    label += " · ";
    label += (**model).model_id;
  }
  co_return label;
}

Task<void> LoadToolSettings(std::shared_ptr<ToolSettingsService> service,
                            std::shared_ptr<application::ModelStore> models,
                            State<ToolSettingsEditor> editor,
                            State<ToolSettingsPersistenceQueue> persistence) {
  auto loaded = co_await service->Load();
  if (!loaded) {
    auto status = persistence.Get();
    status.failed = true;
    persistence = std::move(status);
    co_return;
  }

  auto next = MakeEditor(std::move(*loaded));
  for (const auto &presentation : image_model_presentations) {
    const auto &setting =
        application::ImageModelSettingInfo(presentation.purpose);
    next.image_model_labels[presentation.label_slot] =
        co_await ResolveModelLabel(models, next.settings.*setting.state_member);
  }

  const auto current = editor.Get();
  if (current.edit_revision != 0) {
    next.settings.web_search = current.settings.web_search;
    next.base_url = current.base_url;
    next.api_key = current.api_key;
    next.model = current.model;
    next.query_param = current.query_param;
    next.api_key_header = current.api_key_header;
    next.api_key_param = current.api_key_param;
    next.edit_revision = current.edit_revision;
  }
  editor = std::move(next);
  auto status = persistence.Get();
  status.failed = false;
  persistence = std::move(status);
}

Task<void> DrainPersistenceQueue(std::shared_ptr<ToolSettingsService> service,
                                 State<ToolSettingsPersistenceQueue> queue) {
  for (;;) {
    auto before = queue.Get();
    if (before.pending.empty()) {
      before.running = false;
      queue = std::move(before);
      co_return;
    }
    auto change = std::move(before.pending.front());
    before.pending.pop_front();
    queue = std::move(before);

    auto persisted = co_await service->Persist(std::move(change));
    auto after = queue.Get();
    after.failed = !persisted;
    queue = std::move(after);
  }
}

void QueuePersistence(const TaskScope &tasks,
                      const std::shared_ptr<ToolSettingsService> &service,
                      State<ToolSettingsPersistenceQueue> queue,
                      ToolSettingsChange change) {
  auto next = queue.Get();
  next.pending.push_back(std::move(change));
  const bool launch = !next.running;
  next.running = true;
  queue = std::move(next);
  if (launch) {
    tasks.Launch(
        [service, queue] { return DrainPersistenceQueue(service, queue); });
  }
}

void SetWebSearchField(ToolSettingsEditor &editor,
                       EditorTextMember editor_value,
                       ConfigTextMember config_value,
                       const TextEditingValue &value) {
  editor.*editor_value = value;
  editor.settings.web_search.*config_value = value.text;
  ++editor.edit_revision;
}

TextFieldStyle ToolFormFieldStyle() {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Outlined;
  style.show_label = false;
  style.outlined.background = colors::surface_light;
  style.outlined.border = colors::border_light;
  style.outlined.hovered_border = colors::border_light;
  style.outlined.focused_border = colors::border_light;
  style.outlined.minimum_height = 44.0F;
  style.text_style = Label(16.0F);
  style.placeholder_style = Label(16.0F, FontWeight::Regular, colors::tertiary);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  style.border_width = 1.0F;
  style.focused_border_width = 1.0F;
  style.outlined.corner_radii = CornerRadii{8.0F};
  style.padding = EdgeInsets::Symmetric(12.0F, 8.0F);
  return style;
}

View FormField(TextEditingValue value, StringVariant label,
               StringVariant placeholder,
               std::function<void(const TextEditingValue &)> changed,
               bool secure, std::string_view key) {
  auto input = TextField(std::move(value))
                   .Label(label)
                   .Placeholder(std::move(placeholder))
                   .Variant(TextFieldVariant::Outlined)
                   .LineLimits(TextFieldLineLimits::SingleLine())
                   .VerticalAlign(TextVerticalAlign::Center)
                   .InputConfiguration(TextInputConfiguration{
                       .type = TextInputType::Text,
                       .capitalization = TextCapitalization::None,
                       .action = TextInputAction::Next,
                       .multiline = false,
                       .secure = secure,
                       .autocorrect = false,
                   })
                   .OnChanged(std::move(changed))
                   .With(Frame{.min_height = 44.0F})
                   .Key(key);
  if (secure)
    input = std::move(input).Secure();

  ThemeDefinition definition;
  definition.Set(ToolFormFieldStyle());
  return Column{
      Text(std::move(label))
          .Style(Label(13.0F, FontWeight::Medium, colors::secondary)),
      Theme(std::move(definition), std::move(input)),
  }
      .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View SectionHeader(std::string title) {
  return Text(std::move(title))
      .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
      .With(Padding(EdgeInsets{.top = 20.0F, .bottom = 12.0F}));
}

View ActionButton(ImageResource icon, std::function<void()> action) {
  return Row{
      Glyph(std::move(icon), 15.0F, colors::text_on_color),
      Text(app::strings::screen_tools_pick_model)
          .Style(Label(11.0F, FontWeight::Bold, colors::text_on_color)),
  }
      .OnClick([action = std::move(action)] {
        if (action)
          std::invoke(action);
      })
      .With(Frame{.height = 42.0F}, Spacing(6.0F),
            Padding(EdgeInsets::Symmetric(8.0F, 0.0F)),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center), Background(colors::accent),
            Border(colors::accent, 1.0F), CornerRadius(8.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View ImageModelCard(const ImageModelPresentation &presentation,
                    const std::string &selected_label,
                    std::function<void()> action) {
  const bool selected = !selected_label.empty();
  return Column{
      Text(presentation.settings_title).Style(Label(16.0F, FontWeight::Bold)),
      Gap(2.0F),
      Text(presentation.settings_description)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Frame{.min_height = presentation.description_minimum_height}),
      Gap(12.0F),
      Text(selected
               ? StringVariant{selected_label}
               : StringVariant{app::strings::screen_tools_no_model_selected})
          .Style(Label(13.0F, FontWeight::Bold,
                       selected ? colors::text : colors::tertiary)),
      Gap(12.0F),
      ActionButton(presentation.action_icon, std::move(action)),
  }
      .With(Padding(16.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(12.0F));
}

View ProviderButton(const WebSearchProviderPresentation &presentation,
                    WebSearchProvider selected,
                    std::function<void(WebSearchProvider)> choose) {
  const bool active = presentation.provider == selected;
  return Stack{
      Text(presentation.label)
          .Style(Label(11.0F, FontWeight::Bold,
                       active ? colors::text_on_color : colors::secondary))
          .Align(TextAlign::Center)
          .VerticalAlign(TextVerticalAlign::Center),
  }
      .OnClick([provider = presentation.provider, choose = std::move(choose)] {
        std::invoke(choose, provider);
      })
      .With(Frame{.height = 34.0F}, Grow(),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(active ? colors::accent : colors::surface_light),
            Border(active ? colors::accent : colors::border_light, 1.0F),
            CornerRadius(8.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View ProviderGrid(WebSearchProvider selected,
                  std::function<void(WebSearchProvider)> choose) {
  constexpr std::size_t columns = 3;
  std::vector<View> rows;
  rows.reserve((web_search_provider_presentations.size() + columns - 1) /
               columns);
  for (std::size_t start = 0; start < web_search_provider_presentations.size();
       start += columns) {
    std::vector<View> buttons;
    buttons.reserve(columns);
    const auto end =
        std::min(start + columns, web_search_provider_presentations.size());
    for (std::size_t index = start; index < end; ++index) {
      buttons.push_back(ProviderButton(web_search_provider_presentations[index],
                                       selected, choose));
    }
    rows.push_back(
        Row(std::move(buttons))
            .With(Spacing(8.0F), Padding(EdgeInsets{.right = 8.0F})));
  }
  return Column(std::move(rows))
      .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View WebSearchCard(const ToolSettingsEditor &editor,
                   std::function<void(WebSearchProvider)> choose_provider,
                   std::function<void(EditorTextMember, ConfigTextMember,
                                      const TextEditingValue &)>
                       edit_field) {
  std::vector<View> content;
  content.reserve(16);
  content.push_back(Text(app::strings::screen_tools_web_search_label)
                        .Style(Label(16.0F, FontWeight::Bold)));
  content.push_back(Gap(2.0F));
  content.push_back(
      Text(app::strings::screen_tools_web_search_desc)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)));
  content.push_back(Gap(20.0F));
  content.push_back(ProviderGrid(editor.settings.web_search.provider,
                                 std::move(choose_provider)));

  if (domain::WebSearchFieldsVisible(editor.settings.web_search.provider)) {
    for (const auto &binding : WebSearchFieldBindings()) {
      content.push_back(Gap(12.0F));
      content.push_back(FormField(
          editor.*binding.editor_value, binding.label, binding.placeholder,
          [edit_field, editor_value = binding.editor_value,
           config_value = binding.config_value](const TextEditingValue &next) {
            std::invoke(edit_field, editor_value, config_value, next);
          },
          binding.secure, binding.key));
    }
  }

  return Column(std::move(content))
      .With(Padding(16.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(12.0F));
}

} // namespace

[[huxerui::composable]] View
ToolSettingsScreen(std::shared_ptr<ToolSettingsService> service,
                   std::shared_ptr<application::ModelStore> models,
                   std::size_t reload_revision) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  auto editor = UseState(MakeEditor(ToolSettingsState{}));
  auto persistence = UseState(ToolSettingsPersistenceQueue{});

  Lifecycle(
      [tasks, service, models, editor, persistence] {
        tasks.Launch([service, models, editor, persistence] {
          return LoadToolSettings(service, models, editor, persistence);
        });
      },
      reload_revision);

  auto choose_provider = [tasks, service, editor,
                          persistence](WebSearchProvider provider) {
    auto next = editor.Get();
    next.settings.web_search = domain::DefaultWebSearchConfig(provider);
    next.base_url =
        TextEditingValue::FromText(next.settings.web_search.base_url);
    next.api_key = TextEditingValue::FromText(next.settings.web_search.api_key);
    next.model = TextEditingValue::FromText(next.settings.web_search.model);
    next.query_param =
        TextEditingValue::FromText(next.settings.web_search.query_param);
    next.api_key_header =
        TextEditingValue::FromText(next.settings.web_search.api_key_header);
    next.api_key_param =
        TextEditingValue::FromText(next.settings.web_search.api_key_param);
    ++next.edit_revision;
    const auto config = next.settings.web_search;
    editor = std::move(next);
    QueuePersistence(
        tasks, service, persistence,
        application::WebSearchConfigurationChange{.value = config});
  };

  auto edit_field = [tasks, service, editor,
                     persistence](EditorTextMember editor_value,
                                  ConfigTextMember config_value,
                                  const TextEditingValue &value) {
    auto next = editor.Get();
    SetWebSearchField(next, editor_value, config_value, value);
    const auto config = next.settings.web_search;
    editor = std::move(next);
    QueuePersistence(
        tasks, service, persistence,
        application::WebSearchConfigurationChange{.value = config});
  };

  std::vector<View> content;
  content.reserve(10);
  content.push_back(SectionHeader(LegacySectionTitle(
      UseString(app::strings::screen_tools_section_images))));
  for (const auto &presentation : image_model_presentations) {
    content.push_back(ImageModelCard(
        presentation, editor->image_model_labels[presentation.label_slot],
        [navigation, purpose = presentation.purpose] {
          navigation.Push(domain::AppRoute::ImageModelPicker(purpose));
        }));
    content.push_back(Gap(12.0F));
  }
  content.push_back(SectionHeader(LegacySectionTitle(
      UseString(app::strings::screen_tools_section_search))));
  content.push_back(WebSearchCard(editor.Get(), std::move(choose_provider),
                                  std::move(edit_field)));
  content.push_back(Gap(12.0F));
  if (persistence->failed) {
    content.push_back(
        Text(app::strings::screen_settings_persistence_failed)
            .Style(Label(11.0F, FontWeight::Regular, colors::danger)));
  }
  content.push_back(Gap(100.0F));

  return Column{
      LegacySettingsPageHeader(app::strings::screen_tools_title,
                               [navigation] { navigation.Pop(); }),
      LegacyScreenHeaderDivider(),
      ScrollView(Column(std::move(content))
                     .With(Padding(EdgeInsets{.top = 0.0F,
                                              .right = 16.0F,
                                              .bottom = 0.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch),
                           Background(colors::background)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
