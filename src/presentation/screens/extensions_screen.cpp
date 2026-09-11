#include "presentation/screens/extensions_screen.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstddef>
#include <format>
#include <functional>
#include <optional>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/ports/extension_store.h"
#include "application/ports/mcp_tool_catalog.h"
#include "domain/app_state.h"
#include "domain/extension_config.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/components/legacy_settings_page.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;
using application::ExtensionStoreResult;

struct InstalledExtension final {
  std::string id;
  std::string name;
  std::string description;
  bool enabled{true};
};

struct DetailState final {
  std::vector<InstalledExtension> items;
  std::vector<std::string> marked;
  std::string error;
  bool loading{true};
  bool multi_select{};
};

struct SkillDraftState final {
  TextEditingValue name{TextEditingValue::FromText("")};
  TextEditingValue description{TextEditingValue::FromText("")};
  TextEditingValue content{TextEditingValue::FromText("")};
  TextEditingValue source{TextEditingValue::FromText("")};
  TextEditingValue optional_name{TextEditingValue::FromText("")};
  domain::SkillLocation location{domain::SkillLocation::project};
  bool busy{};
};

using LoadItems = Task<ExtensionStoreResult<std::vector<InstalledExtension>>> (
        *)(const ExtensionScreenServices &);
using SetEnabled = Task<ExtensionStoreResult<void>> (*)(
    const ExtensionScreenServices &, std::string, bool);
using DeleteItems = Task<ExtensionStoreResult<void>> (*)(
    const ExtensionScreenServices &, std::vector<std::string>);
using EditorRoute = domain::AppRoute (*)(std::optional<std::string>);
using ExtensionDestination = domain::AppRoute (*)(domain::ExtensionKind);

struct ExtensionPresentation;
using DetailPrimaryAction = void (*)(
    const ExtensionPresentation *, const ExtensionScreenServices &,
    State<DetailState>, State<SkillDraftState>, TaskScope, BottomSheetHandle,
    DialogHandle, std::shared_ptr<FilePicker>,
    RouteNavigationController<domain::AppRoute>, ToastHandle);
using DetailSupplementAction = void (*)(
    const ExtensionScreenServices &,
    RouteNavigationController<domain::AppRoute>, ToastHandle);
using InstalledItemLongPress = void (*)(
    const ExtensionPresentation *, ExtensionScreenServices,
    State<DetailState>, TaskScope, BottomSheetHandle,
    RouteNavigationController<domain::AppRoute>, const InstalledExtension &);

struct DetailSupplementPresentation final {
  StringResource section_title;
  StringResource title;
  StringResource description;
  ImageResource icon;
  DetailSupplementAction action;
};

struct ExtensionPresentation final {
  domain::ExtensionKind kind;
  StringResource title;
  StringResource description;
  StringResource badge;
  ImageResource icon;
  float minimum_height;
  bool requires_terminal_provider;
  StringResource section_title;
  StringResource inline_title;
  StringResource inline_description;
  StringResource empty_message;
  std::span<const DetailSupplementPresentation> supplements;
  LoadItems load_items;
  SetEnabled set_enabled;
  DeleteItems delete_items;
  EditorRoute editor_route;
  InstalledItemLongPress item_long_press;
  DetailPrimaryAction primary_action;
  ExtensionDestination destination;
};

void OpenExtensionEditor(
    const ExtensionPresentation *presentation,
    const ExtensionScreenServices &services, State<DetailState> detail,
    State<SkillDraftState> skill_draft, TaskScope tasks,
    BottomSheetHandle sheets, DialogHandle dialogs,
    std::shared_ptr<FilePicker> picker,
    RouteNavigationController<domain::AppRoute> navigation, ToastHandle toast);
void OpenSkillActions(
    const ExtensionPresentation *presentation,
    const ExtensionScreenServices &services, State<DetailState> detail,
    State<SkillDraftState> skill_draft, TaskScope tasks,
    BottomSheetHandle sheets, DialogHandle dialogs,
    std::shared_ptr<FilePicker> picker,
    RouteNavigationController<domain::AppRoute> navigation, ToastHandle toast);
void ShowUnavailableAction(
    const ExtensionPresentation *presentation,
    const ExtensionScreenServices &services, State<DetailState> detail,
    State<SkillDraftState> skill_draft, TaskScope tasks,
    BottomSheetHandle sheets, DialogHandle dialogs,
    std::shared_ptr<FilePicker> picker,
    RouteNavigationController<domain::AppRoute> navigation, ToastHandle toast);
void OpenSkillStore(const ExtensionScreenServices &services,
                    RouteNavigationController<domain::AppRoute> navigation,
                    ToastHandle toast);
void ShareSkillWorkspace(
    const ExtensionScreenServices &services,
    RouteNavigationController<domain::AppRoute> navigation, ToastHandle toast);
void OpenEditableItemMenu(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    TaskScope tasks, BottomSheetHandle sheets,
    RouteNavigationController<domain::AppRoute> navigation,
    const InstalledExtension &item);
void BeginSkillMultiSelect(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    TaskScope tasks, BottomSheetHandle sheets,
    RouteNavigationController<domain::AppRoute> navigation,
    const InstalledExtension &item);
Task<void> ReloadDetail(const ExtensionPresentation *presentation,
                        ExtensionScreenServices services,
                        State<DetailState> state);

struct ToolPresentation final {
  std::string_view name;
  std::string_view description;
};

struct AgentEditorState final {
  domain::AgentExtension original;
  TextEditingValue name;
  TextEditingValue slug;
  TextEditingValue prompt;
  TextEditingValue trigger;
  std::vector<std::string> selected_tools;
  std::vector<std::string> selected_mcps;
  std::vector<domain::McpExtension> available_mcps;
  std::string error;
  bool loading{true};
  bool busy{};
};

struct HeaderDraft final {
  std::size_t key{};
  TextEditingValue name;
  TextEditingValue value;
};

struct McpEditorState final {
  domain::McpExtension original;
  TextEditingValue name;
  TextEditingValue url;
  std::vector<HeaderDraft> headers;
  std::vector<domain::McpToolSummary> tools;
  std::string queried_url;
  std::string error;
  std::size_t next_header_key{1};
  bool loading{true};
  bool querying{};
  bool saving{};
  bool queried{};
};

constexpr std::array kToolPresentations{
    ToolPresentation{"file_read", "file · read files"},
    ToolPresentation{"file_write", "file · write files"},
    ToolPresentation{"file_edit", "file · edit files"},
    ToolPresentation{"file_delete", "file · delete files"},
    ToolPresentation{"glob", "file · search files"},
    ToolPresentation{"list_dir", "file · list directories"},
    ToolPresentation{"shell_execute", "shell · execute commands"},
    ToolPresentation{"agent", "agent · delegate a task"},
    ToolPresentation{"agent_pipeline", "agent · run an agent pipeline"},
    ToolPresentation{"todo_update", "session · update the todo list"},
    ToolPresentation{"web_search", "web · search the internet"},
    ToolPresentation{"web_fetch", "web · fetch a web page"},
    ToolPresentation{"image_understanding", "image · understand an image"},
    ToolPresentation{"image_generation", "image · generate an image"},
    ToolPresentation{"memory_update", "memory · update long-term memory"},
};

Task<ExtensionStoreResult<std::vector<InstalledExtension>>>
LoadAgents(const ExtensionScreenServices &services) {
  auto loaded = co_await services.agents->ListAgents();
  if (!loaded)
    co_return std::unexpected(std::move(loaded.error()));
  std::vector<InstalledExtension> result;
  result.reserve(loaded->size());
  for (auto &agent : *loaded) {
    result.push_back({
        .id = std::move(agent.id),
        .name = std::move(agent.name),
        .description =
            std::format("{} · {} tools", agent.slug, agent.tool_names.size()),
        .enabled = agent.enabled,
    });
  }
  co_return result;
}

Task<ExtensionStoreResult<std::vector<InstalledExtension>>>
LoadMcps(const ExtensionScreenServices &services) {
  auto loaded = co_await services.mcps->ListMcps();
  if (!loaded)
    co_return std::unexpected(std::move(loaded.error()));
  std::vector<InstalledExtension> result;
  result.reserve(loaded->size());
  for (auto &mcp : *loaded) {
    result.push_back({
        .id = std::move(mcp.id),
        .name = std::move(mcp.name),
        .description = std::format("{} tools · {}", mcp.tools.size(), mcp.url),
        .enabled = mcp.enabled,
    });
  }
  co_return result;
}

Task<ExtensionStoreResult<std::vector<InstalledExtension>>>
LoadSkills(const ExtensionScreenServices &services) {
  if (!services.skills || !services.skill_roots) {
    co_return std::unexpected(application::ExtensionStoreError{
        .message = "Skill extension service is unavailable"});
  }
  auto loaded = co_await services.skills->List(*services.skill_roots);
  if (!loaded) {
    co_return std::unexpected(application::ExtensionStoreError{
        .message = std::move(loaded.error().message)});
  }
  std::vector<InstalledExtension> result;
  result.reserve(loaded->size());
  for (auto &skill : *loaded) {
    result.push_back({
        .id = std::move(skill.id),
        .name = std::move(skill.name),
        .description =
            std::format("{} · {}", domain::SkillLocationLabel(skill.location),
                        skill.skill_markdown_path),
        .enabled = skill.enabled,
    });
  }
  co_return result;
}

Task<ExtensionStoreResult<std::vector<InstalledExtension>>>
LoadUnavailable(const ExtensionScreenServices &) {
  co_return std::vector<InstalledExtension>{};
}

Task<ExtensionStoreResult<void>>
SetAgentEnabled(const ExtensionScreenServices &services, std::string id,
                bool enabled) {
  co_return co_await services.agents->SetAgentEnabled(std::move(id), enabled);
}

Task<ExtensionStoreResult<void>>
SetMcpEnabled(const ExtensionScreenServices &services, std::string id,
              bool enabled) {
  co_return co_await services.mcps->SetMcpEnabled(std::move(id), enabled);
}

Task<ExtensionStoreResult<void>>
DeleteAgents(const ExtensionScreenServices &services,
             std::vector<std::string> ids) {
  co_return co_await services.agents->DeleteAgents(std::move(ids));
}

Task<ExtensionStoreResult<void>>
DeleteMcps(const ExtensionScreenServices &services,
           std::vector<std::string> ids) {
  co_return co_await services.mcps->DeleteMcps(std::move(ids));
}

Task<ExtensionStoreResult<void>>
SetSkillEnabled(const ExtensionScreenServices &services, std::string id,
                const bool enabled) {
  if (!services.skills || !services.skill_roots) {
    co_return std::unexpected(application::ExtensionStoreError{
        .message = "Skill extension service is unavailable"});
  }
  auto changed =
      co_await services.skills->SetEnabled(std::move(id), enabled);
  if (!changed) {
    co_return std::unexpected(application::ExtensionStoreError{
        .message = std::move(changed.error().message)});
  }
  co_return ExtensionStoreResult<void>{};
}

Task<ExtensionStoreResult<void>>
DeleteSkills(const ExtensionScreenServices &services,
             std::vector<std::string> ids) {
  if (!services.skills || !services.skill_roots) {
    co_return std::unexpected(application::ExtensionStoreError{
        .message = "Skill extension service is unavailable"});
  }
  for (auto &id : ids) {
    auto deleted =
        co_await services.skills->Delete(*services.skill_roots, std::move(id));
    if (!deleted) {
      co_return std::unexpected(application::ExtensionStoreError{
          .message = std::move(deleted.error().message)});
    }
  }
  co_return ExtensionStoreResult<void>{};
}

domain::AppRoute AgentEditor(std::optional<std::string> id) {
  return domain::AppRoute::AgentExtensionEditor(std::move(id));
}

domain::AppRoute McpEditor(std::optional<std::string> id) {
  return domain::AppRoute::McpExtensionEditor(std::move(id));
}

domain::AppRoute ExtensionDetailDestination(domain::ExtensionKind kind) {
  return domain::AppRoute::ExtensionDetail(kind);
}

domain::AppRoute TerminalProviderDestination(domain::ExtensionKind) {
  return domain::AppRoute::terminal_provider;
}

const std::array kSkillSupplements{
    DetailSupplementPresentation{app::strings::extension_online_store,
                                 app::strings::extension_skillhub_store,
                                 app::strings::extension_skillhub_store_desc,
                                 app::images::archive, OpenSkillStore},
    DetailSupplementPresentation{
        app::strings::screen_extension_detail_workspace_share,
        app::strings::screen_extension_detail_workspace_share,
        app::strings::screen_extension_detail_workspace_share_desc,
        app::images::folder_open, ShareSkillWorkspace},
};

const std::array<DetailSupplementPresentation, 0> kNoSupplements{};

const std::array kExtensionPresentations{
    ExtensionPresentation{
        .kind = domain::ExtensionKind::agent,
        .title = app::strings::screen_extensions_section_agent,
        .description = app::strings::screen_extensions_desc_agent,
        .badge = app::strings::screen_extensions_badge_can_add,
        .icon = app::images::brain,
        .minimum_height = 89.9F,
        .requires_terminal_provider = false,
        .section_title =
            app::strings::screen_extension_detail_section_install_other,
        .inline_title =
            app::strings::screen_extension_detail_inline_title_agent,
        .inline_description =
            app::strings::screen_extension_detail_inline_desc_agent,
        .empty_message = app::strings::screen_extension_detail_empty_agent,
        .supplements = kNoSupplements,
        .load_items = LoadAgents,
        .set_enabled = SetAgentEnabled,
        .delete_items = DeleteAgents,
        .editor_route = AgentEditor,
        .item_long_press = OpenEditableItemMenu,
        .primary_action = OpenExtensionEditor,
        .destination = ExtensionDetailDestination,
    },
    ExtensionPresentation{
        .kind = domain::ExtensionKind::mcp,
        .title = app::strings::screen_extensions_section_mcp,
        .description = app::strings::screen_extensions_desc_mcp,
        .badge = app::strings::screen_extensions_badge_https,
        .icon = app::images::mcp,
        .minimum_height = 71.6F,
        .requires_terminal_provider = false,
        .section_title =
            app::strings::screen_extension_detail_section_install_other,
        .inline_title = app::strings::screen_extension_detail_inline_title_mcp,
        .inline_description =
            app::strings::screen_extension_detail_inline_desc_mcp,
        .empty_message = app::strings::screen_extension_detail_empty_mcp,
        .supplements = kNoSupplements,
        .load_items = LoadMcps,
        .set_enabled = SetMcpEnabled,
        .delete_items = DeleteMcps,
        .editor_route = McpEditor,
        .item_long_press = OpenEditableItemMenu,
        .primary_action = OpenExtensionEditor,
        .destination = ExtensionDetailDestination,
    },
    ExtensionPresentation{
        .kind = domain::ExtensionKind::skills,
        .title = app::strings::screen_extensions_section_skills,
        .description = app::strings::screen_extensions_desc_skills,
        .badge = app::strings::screen_extensions_badge_zip,
        .icon = app::images::archive,
        .minimum_height = 71.6F,
        .requires_terminal_provider = false,
        .section_title =
            app::strings::screen_extension_detail_section_install_skills,
        .inline_title =
            app::strings::screen_extension_detail_inline_title_skills,
        .inline_description =
            app::strings::screen_extension_detail_inline_desc_skills,
        .empty_message = app::strings::screen_extension_detail_empty_skills,
        .supplements = kSkillSupplements,
        .load_items = LoadSkills,
        .set_enabled = SetSkillEnabled,
        .delete_items = DeleteSkills,
        .item_long_press = BeginSkillMultiSelect,
        .primary_action = OpenSkillActions,
        .destination = ExtensionDetailDestination,
    },
    ExtensionPresentation{
        .kind = domain::ExtensionKind::linecode,
        .title = app::strings::screen_extensions_section_linecode,
        .description = app::strings::screen_extensions_desc_linecode,
        .badge = app::strings::screen_extensions_badge_lip,
        .icon = app::images::package,
        .minimum_height = 71.6F,
        .requires_terminal_provider = false,
        .section_title =
            app::strings::screen_extension_detail_section_install_other,
        .inline_title =
            app::strings::screen_extension_detail_inline_title_linecode,
        .inline_description =
            app::strings::screen_extension_detail_inline_desc_linecode,
        .empty_message = app::strings::screen_extension_detail_empty_linecode,
        .supplements = kNoSupplements,
        .load_items = LoadUnavailable,
        .primary_action = ShowUnavailableAction,
        .destination = ExtensionDetailDestination,
    },
    ExtensionPresentation{
        .kind = domain::ExtensionKind::terminal_provider,
        .title = app::strings::screen_extensions_section_terminal_provider,
        .description = app::strings::screen_extensions_desc_terminal_provider,
        .badge = app::strings::screen_extensions_badge_terminal_provider,
        .icon = app::images::terminal,
        .minimum_height = 93.3F,
        .requires_terminal_provider = true,
        .section_title =
            app::strings::screen_extension_detail_section_install_other,
        .inline_title =
            app::strings::screen_extensions_section_terminal_provider,
        .inline_description =
            app::strings::screen_extensions_desc_terminal_provider,
        .empty_message = app::strings::screen_extension_unavailable,
        .supplements = kNoSupplements,
        .load_items = LoadUnavailable,
        .primary_action = ShowUnavailableAction,
        .destination = TerminalProviderDestination,
    },
};

const ExtensionPresentation &PresentationFor(domain::ExtensionKind kind) {
  const auto found = std::ranges::find(kExtensionPresentations, kind,
                                       &ExtensionPresentation::kind);
  return found != kExtensionPresentations.end() ? *found
                                                : kExtensionPresentations[0];
}

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon).Tint(tint).With(Frame{.width = size, .height = size});
}

bool Contains(const std::vector<std::string> &values, std::string_view value) {
  return std::ranges::find(values, value) != values.end();
}

std::string Trimmed(std::string_view value) {
  const auto first = std::ranges::find_if_not(
      value, [](unsigned char byte) { return std::isspace(byte) != 0; });
  const auto last = std::ranges::find_if_not(value | std::views::reverse,
                                             [](unsigned char byte) {
                                               return std::isspace(byte) != 0;
                                             })
                        .base();
  return first < last ? std::string(first, last) : std::string{};
}

void Toggle(std::vector<std::string> &values, std::string value) {
  const auto found = std::ranges::find(values, value);
  if (found == values.end())
    values.push_back(std::move(value));
  else
    values.erase(found);
}

View ExtensionCard(
    const ExtensionPresentation &presentation,
    const RouteNavigationController<domain::AppRoute> &navigation) {
  return Row{
      Stack{Glyph(presentation.icon, 22.0F, colors::accent)}.With(
          Frame{.width = 44.0F, .height = 44.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(12.0F)),
      Column{
          Row{
              Text(presentation.title).Style(Label(17.0F, FontWeight::Bold)),
              Text(presentation.badge)
                  .Style(Label(11.0F, FontWeight::Bold, colors::accent))
                  .With(Padding(EdgeInsets::Symmetric(8.0F, 3.0F)),
                        Background(colors::accent_muted), CornerRadius(999.0F)),
          }
              .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
          Text(presentation.description)
              .Style(Label(13.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(4.0F), Grow()),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick([navigation, kind = presentation.kind] {
        navigation.Push(PresentationFor(kind).destination(kind));
      })
      .With(Frame{.min_height = presentation.minimum_height},
            Padding(EdgeInsets{
                .top = 12.0F, .right = 12.0F, .bottom = 12.0F, .left = 16.0F}),
            Spacing(12.0F), CrossAlign(CrossAxisAlignment::Center),
            Background(colors::elevated), Border(colors::border, 1.0F),
            CornerRadius(12.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DetailActionRow(ImageResource icon, StringResource title,
                     StringResource description, std::function<void()> action) {
  return Row{
      Stack{Glyph(icon, 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(title).Style(Label(16.0F, FontWeight::Medium)),
          Text(description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
          .With(Frame{.width = 20.0F, .height = 20.0F}),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 68.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)), Spacing(12.0F),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View EmptyRow(StringVariant message) {
  return Stack{Text(std::move(message))
                   .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))}
      .With(Frame{.min_height = 56.0F}, Padding(16.0F),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center));
}

View SheetPanel(StringVariant title, std::vector<View> rows) {
  rows.push_back(Stack{}.With(Frame{.height = 12.0F}));
  return Column{
      Row{Spacer(),
          Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                       Background(colors::tertiary), CornerRadius(2.0F)),
          Spacer()}
          .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
      Text(std::move(title))
          .Style(Label(17.0F, FontWeight::Bold))
          .With(Padding(
              EdgeInsets{.right = 24.0F, .bottom = 12.0F, .left = 24.0F})),
      Divider(),
      Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch)),
  }
      .With(Frame{.max_width = 560.0F}, Background(colors::elevated),
            CornerRadius(CornerRadii::Top(16.0F)), ClipChildren(),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View ConfirmationSheetPanel(StringVariant title, StringVariant message,
                            std::vector<View> rows) {
  rows.push_back(Stack{}.With(Frame{.height = 12.0F}));
  return Column{
      Row{Spacer(),
          Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                       Background(colors::tertiary), CornerRadius(2.0F)),
          Spacer()}
          .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
      Text(std::move(title))
          .Style(Label(17.0F, FontWeight::Bold))
          .With(Padding(
              EdgeInsets{.right = 24.0F, .bottom = 12.0F, .left = 24.0F})),
      Text(std::move(message))
          .Style(Label(13.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(
              EdgeInsets{.right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
      Divider(),
      Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch)),
  }
      .With(Frame{.max_width = 560.0F}, Background(colors::elevated),
            CornerRadius(CornerRadii::Top(16.0F)), ClipChildren(),
            CrossAlign(CrossAxisAlignment::Stretch));
}

View SheetRow(StringVariant title, StringVariant description, Color tint,
              std::function<void()> action) {
  return Column{
      Text(std::move(title)).Style(Label(16.0F, FontWeight::Regular, tint)),
      Text(std::move(description))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
  }
      .OnClick([action = std::move(action)] {
        if (action)
          std::invoke(action);
      })
      .With(Frame{.min_height = 52.0F}, Spacing(2.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 14.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View SkillLocationOption(StringResource title,
                         const domain::SkillLocation location,
                         const State<SkillDraftState> state) {
  const bool selected = state->location == location;
  return RadioButton(title, selected)
      .OnChanged([state, location](bool checked) {
        if (!checked)
          return;
        auto next = state.Get();
        next.location = location;
        state = std::move(next);
      })
      .With(Grow());
}

View SkillDialogButton(StringResource title, const bool primary,
                       const bool enabled, std::function<void()> action) {
  return Stack{Text(title)
                   .Style(Label(13.0F, FontWeight::Medium,
                                primary ? colors::accent : colors::text))
                   .Align(TextAlign::Center)}
      .OnClick(std::move(action))
      .With(Frame{.min_height = 44.0F}, Padding(12.0F),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(primary ? colors::accent_muted : colors::surface_light),
            CornerRadius(8.0F), Enabled(enabled), Focusable(),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

View SkillDialogPanel(StringResource title, std::vector<View> content) {
  content.insert(content.begin(),
                 Text(title).Style(Label(17.0F, FontWeight::Medium)));
  return Column(std::move(content))
      .With(Frame{.max_width = 560.0F}, Padding(16.0F), Spacing(12.0F),
            Background(colors::elevated),
            Border{.color = colors::border_light, .width = 1.0F},
            CornerRadius(16.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

Task<void> FinishSkillMutation(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    State<SkillDraftState> draft, DialogContext dialog, ToastHandle toast,
    application::SkillResult<domain::SkillRecord> result,
    const StringResource success_message) {
  auto next = draft.Get();
  next.busy = false;
  draft = std::move(next);
  if (!result) {
    toast.Show(result.error().message);
    co_return;
  }
  dialog.Dismiss();
  toast.Show(success_message);
  if (services.on_changed)
    services.on_changed();
  co_await ReloadDetail(presentation, std::move(services), detail);
}

Task<void> CreateSkill(const ExtensionPresentation *presentation,
                       ExtensionScreenServices services,
                       State<DetailState> detail,
                       State<SkillDraftState> draft, DialogContext dialog,
                       ToastHandle toast) {
  if (!services.skills || !services.skill_roots) {
    toast.Show(app::strings::extensions_skill_service_unavailable);
    co_return;
  }
  auto next = draft.Get();
  next.busy = true;
  draft = next;
  auto created = co_await services.skills->Create(
      *services.skill_roots, next.location, Trimmed(next.name.text),
      Trimmed(next.description.text), Trimmed(next.content.text));
  co_await FinishSkillMutation(presentation, std::move(services), detail,
                               draft, dialog, toast, std::move(created),
                               app::strings::extensions_skill_create_success);
}

Task<void> InstallGitHubSkill(const ExtensionPresentation *presentation,
                              ExtensionScreenServices services,
                              State<DetailState> detail,
                              State<SkillDraftState> draft,
                              DialogContext dialog, ToastHandle toast) {
  if (!services.skill_sources || !services.skill_roots) {
    toast.Show(app::strings::extensions_skill_service_unavailable);
    co_return;
  }
  auto next = draft.Get();
  next.busy = true;
  draft = next;
  auto installed = co_await services.skill_sources->InstallGitHub(
      *services.skill_roots, next.location, Trimmed(next.source.text));
  co_await FinishSkillMutation(presentation, std::move(services), detail,
                               draft, dialog, toast, std::move(installed),
                               app::strings::extensions_skill_install_success);
}

Task<application::SkillResult<application::SkillPackage>>
PackageFromPath(const huxerui::File &source) {
  if (source.IsDirectory())
    co_return application::SkillPackage{
        application::SkillDirectoryPackage{source}};
  const std::string lower = [&source] {
    std::string value = source.Name();
    std::ranges::transform(value, value.begin(), [](const unsigned char byte) {
      return static_cast<char>(std::tolower(byte));
    });
    return value;
  }();
  if (lower.ends_with(".md")) {
    auto text = co_await source.ReadStringAsync();
    if (!text.Succeeded()) {
      co_return std::unexpected(
          application::SkillError{.message = text.Error().message});
    }
    co_return application::SkillPackage{application::SkillMarkdownPackage{
        .markdown = std::move(text).Value()}};
  }
  if (lower.ends_with(".zip")) {
    auto bytes = co_await source.ReadBytesAsync();
    if (!bytes.Succeeded()) {
      co_return std::unexpected(
          application::SkillError{.message = bytes.Error().message});
    }
    co_return application::SkillPackage{application::SkillZipPackage{
        .archive = std::move(bytes).Value(), .strip_common_root = true}};
  }
  co_return std::unexpected(application::SkillError{
      .message = "Skill source must be a directory, SKILL.md or ZIP"});
}

Task<void> InstallPathSkill(const ExtensionPresentation *presentation,
                            ExtensionScreenServices services,
                            State<DetailState> detail,
                            State<SkillDraftState> draft,
                            DialogContext dialog, ToastHandle toast) {
  if (!services.skills || !services.skill_roots) {
    toast.Show(app::strings::extensions_skill_service_unavailable);
    co_return;
  }
  auto next = draft.Get();
  next.busy = true;
  draft = next;
  const std::string source_path = Trimmed(next.source.text);
  if (source_path.empty()) {
    next.busy = false;
    draft = std::move(next);
    toast.Show(app::strings::extensions_skill_invalid_source);
    co_return;
  }
  huxerui::File source{source_path};
  auto package = co_await PackageFromPath(source);
  if (!package) {
    next = draft.Get();
    next.busy = false;
    draft = std::move(next);
    toast.Show(package.error().message);
    co_return;
  }
  auto installed = co_await services.skills->Install({
      .roots = *services.skill_roots,
      .location = next.location,
      .name = Trimmed(next.optional_name.text).empty()
                  ? source.Name()
                  : Trimmed(next.optional_name.text),
      .package = std::move(*package),
  });
  co_await FinishSkillMutation(presentation, std::move(services), detail,
                               draft, dialog, toast, std::move(installed),
                               app::strings::extensions_skill_install_success);
}

Task<void> InstallPickedSkill(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    TaskScope tasks, BottomSheetHandle sheets,
    std::shared_ptr<FilePicker> picker, ToastHandle toast) {
  if (!services.skills || !services.skill_roots || !picker || !picker->CanOpenFiles()) {
    toast.Show(app::strings::extensions_skill_service_unavailable);
    co_return;
  }
  auto selected = co_await picker->OpenFileAsync(FilePickerFilter{
      .name = "Skill",
      .extensions = {"md", "zip"},
      .content_types = {"text/markdown", "application/zip"},
  });
  if (!selected)
    co_return;
  std::optional<application::SkillPackage> package;
  if (const auto source = selected->AsFile()) {
    auto loaded = co_await PackageFromPath(*source);
    if (loaded)
      package = std::move(*loaded);
  }
  if (!package) {
    const std::string lower = [&selected] {
      std::string value = selected->Name();
      std::ranges::transform(value, value.begin(), [](const unsigned char byte) {
        return static_cast<char>(std::tolower(byte));
      });
      return value;
    }();
    if (lower.ends_with(".md")) {
      auto text = co_await selected->ReadStringAsync();
      if (text.Succeeded())
        package.emplace(application::SkillMarkdownPackage{
            .markdown = std::move(text).Value()});
    } else if (lower.ends_with(".zip")) {
      auto bytes = co_await selected->ReadBytesAsync();
      if (bytes.Succeeded())
        package.emplace(application::SkillZipPackage{
            .archive = std::move(bytes).Value(), .strip_common_root = true});
    }
  }
  if (!package) {
    toast.Show(app::strings::extensions_skill_invalid_source);
    co_return;
  }
  const std::string name = selected->Name();
  auto selected_package =
      std::make_shared<const application::SkillPackage>(std::move(*package));
  sheets.Show([presentation, services, detail, tasks, toast, name,
               selected_package](BottomSheetContext sheet) {
    std::vector<View> rows;
    const auto install = [presentation, services, detail, tasks, toast, name,
                          selected_package,
                          sheet](domain::SkillLocation location) {
      sheet.Dismiss();
      tasks.Launch([presentation, services, detail, toast, name,
                    package = *selected_package, location]() mutable
                       -> Task<void> {
        auto installed = co_await services.skills->Install({
            .roots = *services.skill_roots,
            .location = location,
            .name = name,
            .package = std::move(package),
        });
        if (!installed) {
          toast.Show(installed.error().message);
          co_return;
        }
        toast.Show(app::strings::extensions_skill_install_success);
        if (services.on_changed)
          services.on_changed();
        co_await ReloadDetail(presentation, std::move(services), detail);
      });
    };
    if (services.skill_roots->project) {
      rows.push_back(SheetRow(
          app::strings::extensions_skill_target_project,
          app::strings::extensions_skill_target_project_desc, colors::text,
          [install] { install(domain::SkillLocation::project); }));
    }
    rows.push_back(SheetRow(
        app::strings::extensions_skill_target_global,
        app::strings::extensions_skill_target_global_desc, colors::text,
        [install] { install(domain::SkillLocation::app); }));
    return SheetPanel(app::strings::skillhub_install_location,
                      std::move(rows));
  });
}

View SkillDraftField(TextEditingValue value, StringResource label,
                     StringResource placeholder, const bool multiline,
                     std::function<void(TextEditingValue)> changed,
                     std::optional<StringResource> helper = std::nullopt) {
  std::vector<View> content;
  content.push_back(
      Text(label).Style(Label(13.0F, FontWeight::Medium, colors::secondary)));
  content.push_back(TextField(std::move(value))
                        .Placeholder(placeholder)
                        .Variant(TextFieldVariant::Outlined)
                        .LineLimits(multiline
                                        ? TextFieldLineLimits::MultiLine(3, 8)
                                        : TextFieldLineLimits::SingleLine())
                        .OnChanged(std::move(changed))
                        .With(Frame{.min_height =
                                        multiline ? 120.0F : 44.0F}));
  if (helper)
    content.push_back(Text(*helper).Style(
        Label(11.0F, FontWeight::Regular, colors::tertiary)));
  return Column(std::move(content))
      .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

template <typename Member>
auto ChangeSkillDraft(State<SkillDraftState> state, Member member) {
  return [state, member](TextEditingValue value) {
    auto next = state.Get();
    next.*member = std::move(value);
    state = std::move(next);
  };
}

[[huxerui::composable]] View SkillCreateDialog(
    DialogContext dialog, const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    State<SkillDraftState> draft, TaskScope tasks, ToastHandle toast) {
  return SkillDialogPanel(
      app::strings::extensions_skill_dialog_create,
      {
          SkillDraftField(draft->name, app::strings::extensions_skill_field_name,
                          app::strings::extensions_skill_hint_name, false,
                          ChangeSkillDraft(draft, &SkillDraftState::name)),
          SkillDraftField(
              draft->description,
              app::strings::extensions_skill_field_description,
              app::strings::extensions_skill_hint_description, false,
              ChangeSkillDraft(draft, &SkillDraftState::description)),
          SkillDraftField(draft->content,
                          app::strings::extensions_skill_field_content,
                          app::strings::extensions_skill_hint_content, true,
                          ChangeSkillDraft(draft, &SkillDraftState::content)),
          Row{
              SkillLocationOption(
                  app::strings::extensions_skill_location_project,
                  domain::SkillLocation::project, draft),
              SkillLocationOption(
                  app::strings::extensions_skill_location_global,
                  domain::SkillLocation::app, draft),
          }.With(Spacing(8.0F)),
          SkillDialogButton(
              app::strings::extensions_skill_create_action, true,
              !draft->busy,
              [presentation, services, detail, draft, tasks, dialog,
               toast] mutable {
                tasks.Launch(CreateSkill(presentation, std::move(services),
                                         detail, draft, dialog, toast));
              }),
      });
}

[[huxerui::composable]] View SkillGitHubDialog(
    DialogContext dialog, const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    State<SkillDraftState> draft, TaskScope tasks, ToastHandle toast) {
  return SkillDialogPanel(
      app::strings::extensions_skill_dialog_github,
      {
          SkillDraftField(
              draft->source, app::strings::extensions_skill_field_github_url,
              app::strings::extensions_skill_hint_github_url, false,
              ChangeSkillDraft(draft, &SkillDraftState::source),
              app::strings::extensions_skill_helper_github_url),
          Row{
              SkillLocationOption(
                  app::strings::extensions_skill_location_project,
                  domain::SkillLocation::project, draft),
              SkillLocationOption(
                  app::strings::extensions_skill_location_global,
                  domain::SkillLocation::app, draft),
          }.With(Spacing(8.0F)),
          SkillDialogButton(
              app::strings::extensions_skill_install, true, !draft->busy,
              [presentation, services, detail, draft, tasks, dialog,
               toast] mutable {
                tasks.Launch(InstallGitHubSkill(
                    presentation, std::move(services), detail, draft, dialog,
                    toast));
              }),
      });
}

[[huxerui::composable]] View SkillPathDialog(
    DialogContext dialog, const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> detail,
    State<SkillDraftState> draft, TaskScope tasks, ToastHandle toast) {
  return SkillDialogPanel(
      app::strings::extensions_skill_dialog_path,
      {
          SkillDraftField(
              draft->source,
              app::strings::extensions_skill_field_source_path,
              app::strings::extensions_skill_hint_source_path, false,
              ChangeSkillDraft(draft, &SkillDraftState::source),
              app::strings::extensions_skill_helper_source_path),
          SkillDraftField(
              draft->optional_name,
              app::strings::extensions_skill_field_optional_name,
              app::strings::extensions_skill_hint_optional_name, false,
              ChangeSkillDraft(draft, &SkillDraftState::optional_name)),
          Row{
              SkillLocationOption(
                  app::strings::extensions_skill_location_project,
                  domain::SkillLocation::project, draft),
              SkillLocationOption(
                  app::strings::extensions_skill_location_global,
                  domain::SkillLocation::app, draft),
          }.With(Spacing(8.0F)),
          SkillDialogButton(
              app::strings::extensions_skill_install, true, !draft->busy,
              [presentation, services, detail, draft, tasks, dialog,
               toast] mutable {
                tasks.Launch(InstallPathSkill(
                    presentation, std::move(services), detail, draft, dialog,
                    toast));
              }),
      });
}

void ResetSkillDraft(State<SkillDraftState> state) {
  state = SkillDraftState{};
}

void OpenExtensionEditor(
    const ExtensionPresentation *presentation, const ExtensionScreenServices &,
    State<DetailState>, State<SkillDraftState>, TaskScope, BottomSheetHandle,
    DialogHandle, std::shared_ptr<FilePicker>,
    RouteNavigationController<domain::AppRoute> navigation, ToastHandle) {
  navigation.Push(presentation->editor_route(std::nullopt));
}

void OpenSkillActions(
    const ExtensionPresentation *presentation,
    const ExtensionScreenServices &services, State<DetailState> detail,
    State<SkillDraftState> skill_draft, TaskScope tasks,
    BottomSheetHandle sheets, DialogHandle dialogs,
    std::shared_ptr<FilePicker> picker,
    RouteNavigationController<domain::AppRoute>, ToastHandle toast) {
  if (!services.skills || !services.skill_roots) {
    toast.Show(app::strings::extensions_skill_service_unavailable);
    return;
  }
  sheets.Show([presentation, services, detail, skill_draft, tasks, sheets,
               dialogs, picker, toast](BottomSheetContext sheet) {
    std::vector<View> rows;
    rows.push_back(SheetRow(
        app::strings::extensions_skill_install_file,
        app::strings::extensions_skill_install_file_desc, colors::text,
        [presentation, services, detail, tasks, sheets, picker, toast, sheet] {
          sheet.Dismiss();
          tasks.Launch(InstallPickedSkill(presentation, services, detail, tasks,
                                          sheets, picker, toast));
        }));
    rows.push_back(SheetRow(
        app::strings::extensions_skill_install_github,
        app::strings::extensions_skill_install_github_desc, colors::text,
        [presentation, services, detail, skill_draft, tasks, dialogs, toast,
         sheet] {
          sheet.Dismiss();
          ResetSkillDraft(skill_draft);
          dialogs.Show(SkillGitHubDialog, presentation, services, detail,
                       skill_draft, tasks, toast);
        }));
    rows.push_back(SheetRow(
        app::strings::extensions_skill_create,
        app::strings::extensions_skill_create_desc, colors::text,
        [presentation, services, detail, skill_draft, tasks, dialogs, toast,
         sheet] {
          sheet.Dismiss();
          ResetSkillDraft(skill_draft);
          dialogs.Show(SkillCreateDialog, presentation, services, detail,
                       skill_draft, tasks, toast);
        }));
    rows.push_back(SheetRow(
        app::strings::extensions_skill_install_path,
        app::strings::extensions_skill_install_path_desc, colors::text,
        [presentation, services, detail, skill_draft, tasks, dialogs, toast,
         sheet] {
          sheet.Dismiss();
          ResetSkillDraft(skill_draft);
          dialogs.Show(SkillPathDialog, presentation, services, detail,
                       skill_draft, tasks, toast);
        }));
    if (!detail->items.empty()) {
      rows.push_back(SheetRow(
          app::strings::screen_extension_multi_select,
          app::strings::screen_extension_multi_select_desc, colors::text,
          [detail, sheet] {
            auto next = detail.Get();
            next.multi_select = true;
            next.marked.clear();
            next.marked.push_back(next.items.front().id);
            detail = std::move(next);
            sheet.Dismiss();
          }));
    }
    return SheetPanel(app::strings::extensions_skill_sheet_title,
                      std::move(rows));
  });
}

void ShowUnavailableAction(
    const ExtensionPresentation *, const ExtensionScreenServices &,
    State<DetailState>, State<SkillDraftState>, TaskScope, BottomSheetHandle,
    DialogHandle, std::shared_ptr<FilePicker>,
    RouteNavigationController<domain::AppRoute>, ToastHandle toast) {
  toast.Show(app::strings::screen_extension_unavailable);
}

void OpenSkillStore(const ExtensionScreenServices &,
                    RouteNavigationController<domain::AppRoute> navigation,
                    ToastHandle) {
  navigation.Push(domain::AppRoute::skill_store);
}

void ShareSkillWorkspace(
    const ExtensionScreenServices &services,
    RouteNavigationController<domain::AppRoute>, ToastHandle toast) {
  if (services.on_share_workspace)
    services.on_share_workspace();
  else
    toast.Show(app::strings::screen_extension_unavailable);
}

bool Marked(const DetailState &state, std::string_view id) {
  return Contains(state.marked, id);
}

Task<void> ReloadDetail(const ExtensionPresentation *presentation,
                        ExtensionScreenServices services,
                        State<DetailState> state) {
  auto loaded = co_await presentation->load_items(services);
  auto next = state.Get();
  next.loading = false;
  if (!loaded) {
    next.error = loaded.error().message;
  } else {
    next.items = std::move(*loaded);
    next.error.clear();
    std::erase_if(next.marked, [&next](const std::string &id) {
      return std::ranges::none_of(
          next.items, [&id](const auto &item) { return item.id == id; });
    });
    if (next.marked.empty())
      next.multi_select = false;
  }
  state = std::move(next);
}

Task<void> ChangeEnabled(const ExtensionPresentation *presentation,
                         ExtensionScreenServices services,
                         State<DetailState> state, std::string id,
                         bool enabled) {
  auto changed =
      co_await presentation->set_enabled(services, std::move(id), enabled);
  if (!changed) {
    auto next = state.Get();
    next.error = changed.error().message;
    state = std::move(next);
    co_return;
  }
  if (services.on_changed)
    services.on_changed();
  co_await ReloadDetail(presentation, std::move(services), state);
}

Task<void> DeleteSelected(const ExtensionPresentation *presentation,
                          ExtensionScreenServices services,
                          State<DetailState> state,
                          std::vector<std::string> ids) {
  auto deleted = co_await presentation->delete_items(services, std::move(ids));
  if (!deleted) {
    auto next = state.Get();
    next.error = deleted.error().message;
    state = std::move(next);
    co_return;
  }
  if (services.on_changed)
    services.on_changed();
  auto next = state.Get();
  next.marked.clear();
  next.multi_select = false;
  state = std::move(next);
  co_await ReloadDetail(presentation, std::move(services), state);
}

void ShowDeleteConfirmation(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> state,
    TaskScope tasks, BottomSheetHandle sheets, std::vector<std::string> ids,
    std::string item_name) {
  sheets.Show([presentation, services, state, tasks,
               ids, item_name = std::move(item_name)](BottomSheetContext sheet) {
    std::vector<View> rows;
    rows.push_back(SheetRow(app::strings::common_cancel, "", colors::text,
                            [sheet] { sheet.Dismiss(); }));
    rows.push_back(SheetRow(
        app::strings::screen_extension_delete,
        app::strings::screen_extension_delete_confirm_desc, colors::danger,
        [sheet, presentation, services, state, tasks, ids]() mutable {
          sheet.Dismiss();
          tasks.Launch([presentation, services, state,
                        ids]() mutable -> Task<void> {
            co_await DeleteSelected(presentation, std::move(services), state,
                                    std::move(ids));
          });
        }));
    return ConfirmationSheetPanel(
        app::strings::screen_extension_delete_title,
        StringVariant::Format(app::strings::screen_extension_delete_confirm,
                              item_name),
        std::move(rows));
  });
}

void ShowSkillDeleteManyConfirmation(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> state,
    TaskScope tasks, BottomSheetHandle sheets) {
  const auto ids = state->marked;
  sheets.Show([presentation, services, state, tasks, ids](
                  BottomSheetContext sheet) mutable {
    std::vector<View> rows;
    rows.push_back(SheetRow(app::strings::common_cancel, "", colors::text,
                            [sheet] { sheet.Dismiss(); }));
    rows.push_back(SheetRow(
        app::strings::screen_extension_delete,
        app::strings::extensions_skill_delete_selected_warning, colors::danger,
        [sheet, presentation, services, state, tasks, ids]() mutable {
          sheet.Dismiss();
          tasks.Launch([presentation, services, state,
                        ids]() mutable -> Task<void> {
            co_await DeleteSelected(presentation, std::move(services), state,
                                    std::move(ids));
          });
        }));
    return ConfirmationSheetPanel(
        app::strings::extensions_skill_delete_selected_title,
        StringVariant::Format(
            app::strings::extensions_skill_delete_selected_message,
            ids.size()),
        std::move(rows));
  });
}

View SkillMultiSelectBar(const ExtensionPresentation *presentation,
                         ExtensionScreenServices services,
                         State<DetailState> state, TaskScope tasks,
                         BottomSheetHandle sheets) {
  return Row{
      Text::Format(app::strings::screen_extension_selected_count,
                   state->marked.size())
          .Style(Label(16.0F, FontWeight::Bold))
          .With(Grow()),
      Text(app::strings::common_cancel)
          .Style(Label(13.0F, FontWeight::Medium, colors::secondary))
          .OnClick([state] {
            auto next = state.Get();
            next.marked.clear();
            next.multi_select = false;
            state = std::move(next);
          })
          .With(Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Text(app::strings::screen_extension_delete)
          .Style(Label(13.0F, FontWeight::Medium, colors::danger))
          .OnClick([presentation, services, state, tasks, sheets] {
            if (!state->marked.empty())
              ShowSkillDeleteManyConfirmation(presentation, services, state,
                                               tasks, sheets);
          })
          .With(Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 44.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 8.0F)), Spacing(12.0F),
            CrossAlign(CrossAxisAlignment::Center));
}

void OpenEditableItemMenu(
    const ExtensionPresentation *presentation,
    ExtensionScreenServices services, State<DetailState> state,
    TaskScope tasks, BottomSheetHandle sheets,
    RouteNavigationController<domain::AppRoute> navigation,
    const InstalledExtension &item) {
  sheets.Show([presentation, services, state, tasks, sheets, navigation,
               item](BottomSheetContext sheet) {
    std::vector<View> rows;
    rows.push_back(SheetRow(
        app::strings::screen_extension_modify,
        app::strings::screen_extension_modify_desc, colors::text,
        [sheet, presentation, navigation, id = item.id] {
          sheet.Dismiss();
          navigation.Push(presentation->editor_route(id));
        }));
    rows.push_back(SheetRow(
        app::strings::screen_extension_delete,
        app::strings::screen_extension_delete_desc, colors::danger,
        [sheet, presentation, services, state, tasks,
         sheets, id = item.id, name = item.name]() mutable {
          sheet.Dismiss();
          ShowDeleteConfirmation(presentation, std::move(services), state,
                                 tasks, sheets, {id}, std::move(name));
        }));
    return SheetPanel(item.name, std::move(rows));
  });
}

void BeginSkillMultiSelect(
    const ExtensionPresentation *, ExtensionScreenServices,
    State<DetailState> state, TaskScope, BottomSheetHandle,
    RouteNavigationController<domain::AppRoute>,
    const InstalledExtension &item) {
  auto next = state.Get();
  next.multi_select = true;
  next.marked.clear();
  next.marked.push_back(item.id);
  state = std::move(next);
}

View InstalledRow(const InstalledExtension &item,
                  const ExtensionPresentation *presentation,
                  ExtensionScreenServices services, State<DetailState> state,
                  TaskScope tasks, BottomSheetHandle sheets,
                  RouteNavigationController<domain::AppRoute> navigation) {
  const bool marked = Marked(state.Get(), item.id);
  if (state->multi_select) {
    return Row{
        Stack{
            marked ? Glyph(app::images::check, 14.0F, colors::text_on_color)
                   : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F}),
        }
            .With(Frame{.width = 22.0F, .height = 22.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(marked ? colors::accent : Color::Transparent()),
                  Border{.color = marked ? colors::accent : colors::border,
                         .width = 1.0F},
                  CornerRadius(11.0F)),
        Column{
            Text(item.name).Style(Label(16.0F, FontWeight::Medium)),
            Text(item.description)
                .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
        }
            .With(Spacing(2.0F), Grow()),
    }
        .OnClick([state, id = item.id] {
          auto next = state.Get();
          Toggle(next.marked, id);
          if (next.marked.empty())
            next.multi_select = false;
          state = std::move(next);
        })
        .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
              Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
              CrossAlign(CrossAxisAlignment::Center),
              Background(marked ? colors::accent_muted : Color::Transparent()),
              Focusable(), PointerCursor(PointerCursorKind::Hand));
  }

  auto actions = [presentation, services, state, tasks, sheets, navigation,
                  item](const LongPressEvent &) {
    std::invoke(presentation->item_long_press, presentation,
                services, state, tasks, sheets, navigation, item);
  };

  return Row{
      Stack{Glyph(presentation->icon, 19.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(item.name).Style(Label(16.0F, FontWeight::Medium)),
          Text(item.description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Switch(item.enabled)
          .OnChanged([presentation, services, state, tasks,
                      id = item.id](bool enabled) mutable {
            tasks.Launch([presentation, services, state, id,
                          enabled]() mutable -> Task<void> {
              co_await ChangeEnabled(presentation, std::move(services), state,
                                     id, enabled);
            });
          }),
  }
      .With(LongPressGesture{})
      .On<LongPressEvents::Started>(std::move(actions))
      .With(Frame{.min_height = 68.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)), Spacing(12.0F),
            CrossAlign(CrossAxisAlignment::Center));
}

View DetailHeader(const ExtensionPresentation *presentation,
                  State<DetailState> state, BottomSheetHandle sheets,
                  State<SkillDraftState> skill_draft, TaskScope tasks,
                  DialogHandle dialogs, std::shared_ptr<FilePicker> picker,
                  ExtensionScreenServices services,
                  RouteNavigationController<domain::AppRoute> navigation,
                  ToastHandle toast) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 20.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(presentation->title).Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{Glyph(app::images::plus, 20.0F, colors::text)}
          .OnClick([presentation, state, skill_draft, sheets, tasks, dialogs,
                    picker, services, navigation, toast] mutable {
            std::invoke(presentation->primary_action, presentation, services,
                        state, skill_draft, tasks, sheets, dialogs, picker,
                        navigation, toast);
          })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View EditorHeader(StringResource title, bool busy,
                  RouteNavigationController<domain::AppRoute> navigation,
                  std::function<void()> save) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 20.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(title).Style(Label(17.0F, FontWeight::Bold))}.With(
          Grow(),
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Text(app::strings::common_save)
          .Style(Label(16.0F, FontWeight::Medium,
                       busy ? colors::tertiary : colors::accent))
          .Align(TextAlign::Center)
          .VerticalAlign(TextVerticalAlign::Center)
          .OnClick([busy, save = std::move(save)] {
            if (!busy && save)
              std::invoke(save);
          })
          .With(Frame{.min_width = 42.0F, .min_height = 39.0F}, Enabled{!busy},
                Focusable(),
                PointerCursor(busy ? PointerCursorKind::Default
                                   : PointerCursorKind::Hand)),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

template <typename Member>
auto ChangeText(State<AgentEditorState> state, Member member) {
  return [state, member](const TextEditingValue &value) {
    auto next = state.Get();
    next.*member = value;
    next.error.clear();
    state = std::move(next);
  };
}

template <typename Member>
auto ChangeText(State<McpEditorState> state, Member member) {
  return [state, member](const TextEditingValue &value) {
    auto next = state.Get();
    next.*member = value;
    next.error.clear();
    state = std::move(next);
  };
}

View FormField(TextEditingValue value, StringResource title,
               StringResource placeholder,
               std::function<void(const TextEditingValue &)> changed,
               bool multiline = false,
               std::optional<StringResource> helper = std::nullopt,
               TextInputType type = TextInputType::Text) {
  std::vector<View> content;
  content.push_back(
      Text(title).Style(Label(13.0F, FontWeight::Medium, colors::secondary)));
  content.push_back(TextField(std::move(value))
                        .Placeholder(placeholder)
                        .Variant(TextFieldVariant::Outlined)
                        .LineLimits(multiline
                                        ? TextFieldLineLimits::MultiLine(3, 8)
                                        : TextFieldLineLimits::SingleLine())
                        .InputConfiguration(TextInputConfiguration{
                            .type = type,
                            .capitalization = TextCapitalization::None,
                            .action = multiline ? TextInputAction::Newline
                                                : TextInputAction::Next,
                            .multiline = multiline,
                            .secure = false,
                            .autocorrect = false,
                        })
                        .OnChanged(std::move(changed))
                        .With(Frame{.min_height = multiline ? 120.0F : 44.0F}));
  if (helper)
    content.push_back(Text(*helper).Style(
        Label(11.0F, FontWeight::Regular, colors::tertiary)));
  return Column(std::move(content))
      .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Stretch));
}

View EditorFormSection(StringResource title, View form) {
  return Column {
    Text(title)
        .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
        .With(Frame{.height = 43.625F},
              Padding(EdgeInsets{.top = 16.0F,
                                 .right = 16.0F,
                                 .bottom = 12.0F,
                                 .left = 16.0F})),
    LegacySettingsCardFrame{
        std::move(form).With(Background(colors::elevated), CornerRadius(12.0F)),
    },
  }.With(CrossAlign(CrossAxisAlignment::Stretch));
}

View SelectionRow(ImageResource icon, StringVariant title,
                  StringVariant description, bool selected,
                  std::function<void(bool)> changed) {
  return Row{
      Stack{Glyph(icon, 18.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(std::move(title)).Style(Label(15.0F, FontWeight::Medium)),
          Text(std::move(description))
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      Switch(selected).OnChanged(std::move(changed)),
  }
      .With(Frame{.min_height = 64.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 10.0F)),
            CrossAlign(CrossAxisAlignment::Center));
}

Task<void> LoadAgentEditor(std::optional<std::string> id,
                           ExtensionScreenServices services,
                           State<AgentEditorState> state) {
  AgentEditorState next = state.Get();
  if (id) {
    auto loaded = co_await services.agents->FindAgent(*id);
    if (!loaded) {
      next.error = loaded.error().message;
    } else if (*loaded) {
      next.original = std::move(**loaded);
      next.name = TextEditingValue::FromText(next.original.name);
      next.slug = TextEditingValue::FromText(next.original.slug);
      next.prompt = TextEditingValue::FromText(next.original.prompt);
      next.trigger = TextEditingValue::FromText(next.original.trigger);
      next.selected_tools = next.original.tool_names;
      next.selected_mcps = next.original.mcp_ids;
    }
  }
  auto mcps = co_await services.mcps->ListMcps();
  if (!mcps) {
    next.error = mcps.error().message;
  } else {
    next.available_mcps = std::move(*mcps);
  }
  next.loading = false;
  state = std::move(next);
}

Task<void> SaveAgent(ExtensionScreenServices services,
                     State<AgentEditorState> state,
                     std::string validation_message,
                     RouteNavigationController<domain::AppRoute> navigation) {
  auto value = state->original;
  value.name = Trimmed(state->name.text);
  value.slug = domain::NormalizeAgentEditorSlug(
      state->slug.text.empty() ? state->name.text : state->slug.text);
  value.prompt = Trimmed(state->prompt.text);
  value.trigger = Trimmed(state->trigger.text);
  value.tool_names = state->selected_tools;
  value.mcp_ids = state->selected_mcps;
  if (value.name.empty() || value.slug.empty() || value.prompt.empty()) {
    auto next = state.Get();
    next.error = std::move(validation_message);
    state = std::move(next);
    co_return;
  }
  auto next = state.Get();
  next.busy = true;
  next.error.clear();
  state = std::move(next);
  auto saved = co_await services.agents->SaveAgent(std::move(value));
  if (!saved) {
    next = state.Get();
    next.busy = false;
    next.error = saved.error().message;
    state = std::move(next);
    co_return;
  }
  if (services.on_changed)
    services.on_changed();
  navigation.Pop();
}

Task<void> LoadMcpEditor(std::optional<std::string> id,
                         ExtensionScreenServices services,
                         State<McpEditorState> state) {
  auto next = state.Get();
  if (id) {
    auto loaded = co_await services.mcps->FindMcp(*id);
    if (!loaded) {
      next.error = loaded.error().message;
    } else if (*loaded) {
      next.original = std::move(**loaded);
      next.name = TextEditingValue::FromText(next.original.name);
      next.url = TextEditingValue::FromText(next.original.url);
      next.tools = next.original.tools;
      next.queried = !next.tools.empty();
      next.queried_url = next.original.url;
      for (const auto &header : next.original.request_headers) {
        next.headers.push_back({
            .key = next.next_header_key++,
            .name = TextEditingValue::FromText(header.name),
            .value = TextEditingValue::FromText(header.value),
        });
      }
    }
  }
  next.loading = false;
  state = std::move(next);
}

std::vector<domain::McpRequestHeader> HeadersFrom(const McpEditorState &state) {
  std::vector<domain::McpRequestHeader> headers;
  headers.reserve(state.headers.size());
  for (const auto &header : state.headers) {
    if (!header.name.text.empty())
      headers.push_back({.name = header.name.text, .value = header.value.text});
  }
  return headers;
}

Task<void> QueryMcp(ExtensionScreenServices services,
                    State<McpEditorState> state, ToastHandle toast,
                    std::string invalid_url_message) {
  auto request = domain::NormalizeMcpExtension(domain::McpExtension{
      .url = state->url.text,
      .request_headers = HeadersFrom(state.Get()),
  });
  if (!domain::IsHttpMcpUrl(request.url)) {
    auto next = state.Get();
    next.error = std::move(invalid_url_message);
    state = std::move(next);
    co_return;
  }
  auto next = state.Get();
  next.querying = true;
  next.error.clear();
  state = std::move(next);
  auto queried = co_await services.mcp_tools->Query(
      request.url, std::move(request.request_headers));
  next = state.Get();
  next.querying = false;
  next.queried = true;
  if (!queried) {
    next.error = queried.error().message;
  } else {
    next.tools = std::move(*queried);
    next.queried_url = request.url;
    next.error.clear();
    toast.Show(app::strings::screen_mcp_query_success);
  }
  state = std::move(next);
}

Task<void> SaveMcp(ExtensionScreenServices services,
                   State<McpEditorState> state,
                   std::string invalid_configuration_message,
                   std::string require_query_message,
                   RouteNavigationController<domain::AppRoute> navigation) {
  auto value = state->original;
  value.name = Trimmed(state->name.text);
  value.url = state->url.text;
  value.request_headers = HeadersFrom(state.Get());
  value.tools = state->tools;
  value = domain::NormalizeMcpExtension(std::move(value));
  if (value.name.empty() || !domain::IsHttpMcpUrl(value.url)) {
    auto next = state.Get();
    next.error = std::move(invalid_configuration_message);
    state = std::move(next);
    co_return;
  }
  if (!state->queried || value.tools.empty() ||
      value.url != state->queried_url) {
    auto next = state.Get();
    next.error = std::move(require_query_message);
    state = std::move(next);
    co_return;
  }
  auto next = state.Get();
  next.saving = true;
  next.error.clear();
  state = std::move(next);
  auto saved = co_await services.mcps->SaveMcp(std::move(value));
  if (!saved) {
    next = state.Get();
    next.saving = false;
    next.error = saved.error().message;
    state = std::move(next);
    co_return;
  }
  if (services.on_changed)
    services.on_changed();
  navigation.Pop();
}

ThemeDefinition ExtensionControlOverrides(TextFieldStyle text_field,
                                          SwitchStyle switch_style) {
  text_field.variant = TextFieldVariant::Outlined;
  text_field.show_label = false;
  text_field.outlined.background = colors::surface_light;
  text_field.outlined.border = colors::border_light;
  text_field.outlined.hovered_border = colors::border_light;
  text_field.outlined.focused_border = colors::border_light;
  text_field.outlined.disabled_border = colors::border_light;
  text_field.outlined.minimum_height = 44.0F;
  text_field.text_style = Label(16.0F);
  text_field.placeholder_style =
      Label(16.0F, FontWeight::Regular, colors::tertiary);
  text_field.caret = colors::accent;
  text_field.border_width = 1.0F;
  text_field.focused_border_width = 1.0F;
  text_field.outlined.corner_radii = CornerRadii{8.0F};
  text_field.padding = EdgeInsets::Symmetric(12.0F, 8.0F);

  switch_style.width = 46.0F;
  switch_style.height = 27.0F;
  switch_style.minimum_interactive_height = 27.0F;
  switch_style.state_layer_size = 27.0F;
  switch_style.unchecked_track = colors::surface_light;
  switch_style.checked_track = colors::accent_dim;
  switch_style.unchecked_track_border = colors::border_light;
  switch_style.checked_track_border = colors::accent;
  switch_style.unchecked_thumb = colors::tertiary;
  switch_style.checked_thumb = colors::accent;
  switch_style.unchecked_thumb_radius = 10.5F;
  switch_style.checked_thumb_radius = 10.5F;
  switch_style.track_border_width = 1.0F;
  switch_style.corner_radius = 13.5F;

  ThemeDefinition overrides;
  overrides.Set(std::move(text_field));
  overrides.Set(std::move(switch_style));
  return overrides;
}

} // namespace

[[huxerui::composable]] View
ExtensionsScreen(bool terminal_provider_available) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> cards;
  cards.reserve(kExtensionPresentations.size());
  for (const auto &presentation : kExtensionPresentations) {
    if (presentation.requires_terminal_provider && !terminal_provider_available)
      continue;
    cards.push_back(
        ExtensionCard(presentation, navigation).Key(presentation.kind));
  }

  return Column{
      LegacySettingsPageHeader(app::strings::screen_extensions_title,
                               [navigation] { navigation.Pop(); }),
      Divider(),
      ScrollView(Column(std::move(cards))
                     .With(Spacing(8.0F),
                           Padding(EdgeInsets{.top = 16.0F,
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

[[huxerui::composable]] View
ExtensionDetailScreen(domain::ExtensionKind kind,
                      ExtensionScreenServices services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto sheets = UseBottomSheet();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  const auto picker = UseService<FilePicker>();
  auto state = UseState(DetailState{});
  auto skill_draft = UseState(SkillDraftState{});
  const auto *presentation = &PresentationFor(kind);
  Lifecycle(
      [tasks, presentation, services, state] {
        tasks.Launch([presentation, services, state]() mutable -> Task<void> {
          co_await ReloadDetail(presentation, std::move(services), state);
        });
      },
      services.revision);

  std::vector<View> content;
  if (state->multi_select) {
    content.push_back(
        SkillMultiSelectBar(presentation, services, state, tasks, sheets));
  }
  content.push_back(LegacySettingsSection(
      presentation->section_title,
      {DetailActionRow(
          presentation->icon, presentation->inline_title,
          presentation->inline_description,
          [presentation, services, state, skill_draft, tasks, sheets, dialogs,
           picker, navigation, toast] {
            std::invoke(presentation->primary_action, presentation, services,
                        state, skill_draft, tasks, sheets, dialogs, picker,
                        navigation, toast);
          })}));
  for (const auto &supplement : presentation->supplements) {
    content.push_back(LegacySettingsSection(
        supplement.section_title,
        {DetailActionRow(
            supplement.icon, supplement.title, supplement.description,
            [action = supplement.action, services, navigation, toast] {
              std::invoke(action, services, navigation, toast);
            })}));
  }

  std::vector<View> installed;
  if (state->loading) {
    installed.push_back(EmptyRow(app::strings::screen_extension_loading));
  } else if (!state->error.empty()) {
    installed.push_back(EmptyRow(state->error));
  } else if (state->items.empty()) {
    installed.push_back(EmptyRow(presentation->empty_message));
  } else {
    installed.reserve(state->items.size());
    for (const auto &item : state->items) {
      installed.push_back(InstalledRow(item, presentation, services, state,
                                       tasks, sheets, navigation)
                              .Key(item.id));
    }
  }
  content.push_back(LegacySettingsSection(
      app::strings::screen_extension_detail_section_installed,
      std::move(installed)));
  content.push_back(Stack{}.With(Frame{.height = 100.0F}));

  View screen =
      Column{
          DetailHeader(presentation, state, sheets, skill_draft, tasks, dialogs,
                       picker, services, navigation, toast),
          Divider(),
          ScrollView(Column(std::move(content))
                         .With(CrossAlign(CrossAxisAlignment::Stretch)))
              .ScrollAxis(Axis::Vertical)
              .With(Grow()),
      }
          .With(CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background), SafeAreaPadding{});
  return Theme(ExtensionControlOverrides(UseEnvironment<TextFieldStyle>(),
                                         UseEnvironment<SwitchStyle>()),
               std::move(screen));
}

[[huxerui::composable]] View
AgentExtensionEditorScreen(std::optional<std::string> id,
                           ExtensionScreenServices services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(AgentEditorState{
      .name = TextEditingValue::FromText(""),
      .slug = TextEditingValue::FromText(""),
      .prompt = TextEditingValue::FromText(""),
      .trigger = TextEditingValue::FromText(""),
      .selected_tools = {"file_read", "glob"},
  });
  Lifecycle([tasks, id, services, state] {
    tasks.Launch([id, services, state]() mutable -> Task<void> {
      co_await LoadAgentEditor(std::move(id), std::move(services), state);
    });
  });

  const std::string validation_message =
      UseString(app::strings::screen_agent_save_require);
  auto save = [services, state, tasks, navigation, validation_message] mutable {
    tasks.Launch([services, state, navigation,
                  validation_message]() mutable -> Task<void> {
      co_await SaveAgent(std::move(services), state,
                         std::move(validation_message), navigation);
    });
  };
  std::vector<View> content;
  if (state->loading) {
    content.push_back(EmptyRow(app::strings::screen_extension_loading));
  } else {
    content.push_back(LegacySettingsSection(
        app::strings::screen_agent_quick_create,
        {DetailActionRow(
            app::images::sparkles, app::strings::screen_agent_let_ai_write,
            app::strings::screen_agent_let_ai_write_desc, [toast] {
              toast.Show(app::strings::screen_agent_ai_unavailable);
            })}));
    content.push_back(EditorFormSection(
        app::strings::screen_agent_form_basic,
        Column{
            FormField(state->name, app::strings::screen_agent_field_name,
                      app::strings::screen_agent_hint_name,
                      ChangeText(state, &AgentEditorState::name)),
            FormField(state->slug, app::strings::screen_agent_field_identifier,
                      app::strings::screen_agent_hint_slug,
                      ChangeText(state, &AgentEditorState::slug), false,
                      app::strings::screen_agent_helper_slug),
        }.With(Spacing(12.0F), Padding(16.0F),
               CrossAlign(CrossAxisAlignment::Stretch))));
    content.push_back(EditorFormSection(
        app::strings::screen_agent_form_behavior,
        Column{
            FormField(state->prompt, app::strings::screen_agent_field_prompt,
                      app::strings::screen_agent_hint_prompt,
                      ChangeText(state, &AgentEditorState::prompt), true),
            FormField(state->trigger, app::strings::screen_agent_field_trigger,
                      app::strings::screen_agent_hint_trigger,
                      ChangeText(state, &AgentEditorState::trigger), true),
        }.With(Spacing(12.0F), Padding(16.0F),
               CrossAlign(CrossAxisAlignment::Stretch))));

    std::vector<View> tool_rows;
    tool_rows.reserve(kToolPresentations.size() + state->selected_tools.size());
    for (const auto &tool : kToolPresentations) {
      const bool selected = Contains(state->selected_tools, tool.name);
      tool_rows.push_back(
          SelectionRow(app::images::settings, std::string{tool.name},
                       std::string{tool.description}, selected,
                       [state, name = std::string{tool.name}](bool) {
                         auto next = state.Get();
                         Toggle(next.selected_tools, name);
                         state = std::move(next);
                       }));
    }
    for (const auto &selected : state->selected_tools) {
      if (std::ranges::none_of(kToolPresentations,
                               [&selected](const auto &tool) {
                                 return tool.name == selected;
                               })) {
        tool_rows.push_back(SelectionRow(
            app::images::settings, selected, "custom · persisted tool", true,
            [state, selected](bool) {
              auto next = state.Get();
              Toggle(next.selected_tools, selected);
              state = std::move(next);
            }));
      }
    }
    content.push_back(LegacySettingsSection(
        app::strings::screen_agent_section_tools,
        tool_rows.empty() ? std::vector<View>{EmptyRow(
                                app::strings::screen_agent_tools_empty)}
                          : std::move(tool_rows)));

    std::vector<View> mcp_rows;
    for (const auto &mcp : state->available_mcps) {
      if (!mcp.enabled)
        continue;
      const std::string key = "custom:" + mcp.id;
      mcp_rows.push_back(SelectionRow(
          app::images::mcp, mcp.name,
          std::format("{}/{} tools · {}",
                      std::ranges::count(mcp.tools, true,
                                         &domain::McpToolSummary::enabled),
                      mcp.tools.size(), mcp.url),
          Contains(state->selected_mcps, key), [state, key](bool) {
            auto next = state.Get();
            Toggle(next.selected_mcps, key);
            state = std::move(next);
          }));
    }
    content.push_back(LegacySettingsSection(
        app::strings::screen_agent_section_mcps,
        mcp_rows.empty()
            ? std::vector<View>{EmptyRow(app::strings::screen_agent_mcps_empty)}
            : std::move(mcp_rows)));
  }
  if (!state->error.empty()) {
    content.push_back(
        Text(state->error)
            .Style(Label(12.0F, FontWeight::Regular, colors::danger))
            .With(Padding(16.0F)));
  }
  content.push_back(Stack{}.With(Frame{.height = 100.0F}));

  View screen =
      Column{
          EditorHeader(app::strings::screen_agent_add_title, state->busy,
                       navigation, std::move(save)),
          Divider(),
          ScrollView(Column(std::move(content))
                         .With(CrossAlign(CrossAxisAlignment::Stretch)))
              .ScrollAxis(Axis::Vertical)
              .With(Grow()),
      }
          .With(CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background), SafeAreaPadding{});
  return Theme(ExtensionControlOverrides(UseEnvironment<TextFieldStyle>(),
                                         UseEnvironment<SwitchStyle>()),
               std::move(screen));
}

[[huxerui::composable]] View
McpExtensionEditorScreen(std::optional<std::string> id,
                         ExtensionScreenServices services) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(McpEditorState{
      .name = TextEditingValue::FromText(""),
      .url = TextEditingValue::FromText(""),
  });
  Lifecycle([tasks, id, services, state] {
    tasks.Launch([id, services, state]() mutable -> Task<void> {
      co_await LoadMcpEditor(std::move(id), std::move(services), state);
    });
  });

  const std::string invalid_configuration_message =
      UseString(app::strings::screen_mcp_save_require_name_url);
  const std::string require_query_message =
      UseString(app::strings::screen_mcp_save_require_query);
  const std::string invalid_url_message =
      UseString(app::strings::screen_mcp_url_invalid);
  auto save = [services, state, tasks, navigation,
               invalid_configuration_message, require_query_message] mutable {
    tasks.Launch([services, state, navigation, invalid_configuration_message,
                  require_query_message]() mutable -> Task<void> {
      co_await SaveMcp(std::move(services), state,
                       std::move(invalid_configuration_message),
                       std::move(require_query_message), navigation);
    });
  };
  std::vector<View> content;
  if (state->loading) {
    content.push_back(EmptyRow(app::strings::screen_extension_loading));
  } else {
    content.push_back(EditorFormSection(
        app::strings::screen_mcp_form_title,
        Column{
            FormField(state->name, app::strings::screen_mcp_field_name,
                      app::strings::screen_mcp_hint_name,
                      ChangeText(state, &McpEditorState::name)),
            FormField(state->url, app::strings::screen_mcp_field_http_url,
                      app::strings::screen_mcp_hint_url,
                      ChangeText(state, &McpEditorState::url), false,
                      app::strings::screen_mcp_helper_url, TextInputType::Url),
        }.With(Spacing(12.0F), Padding(16.0F),
               CrossAlign(CrossAxisAlignment::Stretch))));

    std::vector<View> header_rows;
    header_rows.push_back(
        DetailActionRow(app::images::plus, app::strings::screen_mcp_add_header,
                        app::strings::screen_mcp_add_header_desc, [state] {
                          auto next = state.Get();
                          next.headers.push_back({
                              .key = next.next_header_key++,
                              .name = TextEditingValue::FromText(""),
                              .value = TextEditingValue::FromText(""),
                          });
                          state = std::move(next);
                        }));
    for (const auto &header : state->headers) {
      const auto key = header.key;
      header_rows.push_back(
          Row{
              TextField(header.name)
                  .Placeholder(app::strings::screen_mcp_field_name)
                  .Variant(TextFieldVariant::Outlined)
                  .LineLimits(TextFieldLineLimits::SingleLine())
                  .OnChanged([state, key](const TextEditingValue &value) {
                    auto next = state.Get();
                    const auto found =
                        std::ranges::find(next.headers, key, &HeaderDraft::key);
                    if (found != next.headers.end())
                      found->name = value;
                    state = std::move(next);
                  })
                  .With(Grow(), Frame{.min_height = 42.0F}),
              TextField(header.value)
                  .Placeholder(app::strings::screen_mcp_header_value_hint)
                  .Variant(TextFieldVariant::Outlined)
                  .LineLimits(TextFieldLineLimits::SingleLine())
                  .OnChanged([state, key](const TextEditingValue &value) {
                    auto next = state.Get();
                    const auto found =
                        std::ranges::find(next.headers, key, &HeaderDraft::key);
                    if (found != next.headers.end())
                      found->value = value;
                    state = std::move(next);
                  })
                  .With(Grow(), Frame{.min_height = 42.0F}),
              Stack{Glyph(app::images::trash_2, 16.0F, colors::tertiary)}
                  .OnClick([state, key] {
                    auto next = state.Get();
                    std::erase_if(next.headers, [key](const auto &item) {
                      return item.key == key;
                    });
                    state = std::move(next);
                  })
                  .With(Frame{.width = 34.0F, .height = 42.0F},
                        Align(HorizontalAlignment::Center,
                              VerticalAlignment::Center),
                        Focusable(), PointerCursor(PointerCursorKind::Hand)),
          }
              .With(Spacing(8.0F), Padding(EdgeInsets::Symmetric(16.0F, 8.0F)),
                    CrossAlign(CrossAxisAlignment::Center))
              .Key(key));
    }
    content.push_back(LegacySettingsSection(
        app::strings::screen_mcp_section_headers, std::move(header_rows)));

    content.push_back(LegacySettingsSection(
        app::strings::screen_mcp_query_section_title,
        {DetailActionRow(
            app::images::search, app::strings::screen_mcp_query_tools,
            state->querying ? app::strings::screen_mcp_query_busy
                            : app::strings::screen_mcp_query_empty_desc,
            [services, state, tasks, toast, invalid_url_message] mutable {
              if (state->querying)
                return;
              tasks.Launch([services, state, toast,
                            invalid_url_message]() mutable -> Task<void> {
                co_await QueryMcp(std::move(services), state, toast,
                                  std::move(invalid_url_message));
              });
            })}));

    std::vector<View> tool_rows;
    for (const auto &tool : state->tools) {
      tool_rows.push_back(SelectionRow(
          app::images::mcp, tool.name,
          tool.description.empty()
              ? StringVariant{app::strings::screen_mcp_tool_default_desc}
              : StringVariant{tool.description},
          tool.enabled, [state, name = tool.name](bool enabled) {
            auto next = state.Get();
            const auto found = std::ranges::find(next.tools, name,
                                                 &domain::McpToolSummary::name);
            if (found != next.tools.end())
              found->enabled = enabled;
            state = std::move(next);
          }));
    }
    content.push_back(LegacySettingsSection(
        app::strings::screen_mcp_tools_list,
        tool_rows.empty()
            ? std::vector<View>{EmptyRow(
                  state->queried ? app::strings::screen_mcp_query_no_tools
                                 : app::strings::screen_mcp_query_pending)}
            : std::move(tool_rows)));
  }
  if (!state->error.empty()) {
    content.push_back(
        Text(state->error)
            .Style(Label(12.0F, FontWeight::Regular, colors::danger))
            .With(Padding(16.0F)));
  }
  content.push_back(Stack{}.With(Frame{.height = 100.0F}));

  View screen =
      Column{
          EditorHeader(app::strings::screen_mcp_add_title,
                       state->saving || state->querying, navigation,
                       std::move(save)),
          Divider(),
          ScrollView(Column(std::move(content))
                         .With(CrossAlign(CrossAxisAlignment::Stretch)))
              .ScrollAxis(Axis::Vertical)
              .With(Grow()),
      }
          .With(CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background), SafeAreaPadding{});
  return Theme(ExtensionControlOverrides(UseEnvironment<TextFieldStyle>(),
                                         UseEnvironment<SwitchStyle>()),
               std::move(screen));
}

} // namespace linecode::presentation
