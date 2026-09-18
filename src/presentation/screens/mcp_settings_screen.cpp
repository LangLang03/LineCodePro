#include "presentation/screens/mcp_settings_screen.h"

#include <algorithm>
#include <array>
#include <functional>
#include <optional>
#include <string_view>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/mcp_execution_settings.h"
#include "domain/app_state.h"
#include "domain/mcp_execution_settings.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct ToolGroupVisual final {
  ImageResource icon;
  StringResource title;
  StringResource description;
};

struct ModePresentation final {
  domain::McpExecutionMode mode;
  StringResource label;
  StringResource description;
};

struct ToolGroupPresentation final {
  std::string_view id;
  std::optional<domain::McpExecutionMode> mode;
  ToolGroupVisual visual;
};

using ModeSupplementFactory =
    View (*)(const RouteNavigationController<domain::AppRoute> &,
             domain::McpExecutionCapabilities, PlatformCapabilities);

struct ModeSupplementPresentation final {
  domain::McpExecutionMode mode;
  ModeSupplementFactory factory;
};

struct McpSettingsPresentationState final {
  domain::McpExecutionSettings settings{domain::DefaultMcpExecutionSettings()};
  bool loaded{};
  bool busy{true};

  [[nodiscard]] bool Interactive() const noexcept { return loaded && !busy; }
};

// Presentation is an extensible catalog. Rendering contains no knowledge of
// concrete tool ids: adding a group or a mode-specific skin only registers a
// row here.
const std::array kModePresentations{
    ModePresentation{domain::McpExecutionMode::local,
                     app::strings::screen_mcp_execution_local,
                     app::strings::screen_mcp_execution_local_desc},
    ModePresentation{domain::McpExecutionMode::ssh,
                     app::strings::screen_mcp_execution_ssh,
                     app::strings::screen_mcp_execution_ssh_desc},
    ModePresentation{domain::McpExecutionMode::terminal_provider,
                     app::strings::screen_mcp_execution_terminal_provider,
                     app::strings::screen_mcp_execution_terminal_provider_desc},
};

const std::array kToolGroupPresentations{
    ToolGroupPresentation{"file_ops",
                          std::nullopt,
                          {app::images::mcp,
                           app::strings::tool_group_file_ops_name,
                           app::strings::tool_group_file_ops_desc}},
    ToolGroupPresentation{"agent",
                          std::nullopt,
                          {app::images::brain,
                           app::strings::tool_group_agent_name,
                           app::strings::tool_group_agent_desc}},
    ToolGroupPresentation{"todo",
                          std::nullopt,
                          {app::images::scroll_text,
                           app::strings::tool_group_todo_name,
                           app::strings::tool_group_todo_desc}},
    ToolGroupPresentation{"image_understanding",
                          std::nullopt,
                          {app::images::paintbrush,
                           app::strings::tool_group_image_understanding_name,
                           app::strings::tool_group_image_understanding_desc}},
    ToolGroupPresentation{"image_generation",
                          std::nullopt,
                          {app::images::sparkles,
                           app::strings::tool_group_image_generation_name,
                           app::strings::tool_group_image_generation_desc}},
    ToolGroupPresentation{"shell",
                          domain::McpExecutionMode::ssh,
                          {app::images::terminal,
                           app::strings::tool_group_shell_name,
                           app::strings::tool_group_shell_desc}},
    ToolGroupPresentation{"shell",
                          domain::McpExecutionMode::terminal_provider,
                          {app::images::terminal,
                           app::strings::tool_group_ipc_shell_name,
                           app::strings::tool_group_ipc_shell_desc}},
    ToolGroupPresentation{"web_search",
                          std::nullopt,
                          {app::images::search,
                           app::strings::tool_group_web_search_name,
                           app::strings::tool_group_web_search_desc}},
    ToolGroupPresentation{"memory",
                          std::nullopt,
                          {app::images::mcp,
                           app::strings::tool_group_memory_name,
                           app::strings::tool_group_memory_desc}},
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(icon).Tint(tint).With(Frame{.width = size, .height = size});
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Glyph(app::images::chevron_left, 22.0F, colors::text),
      }
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{
          Text(app::strings::screen_mcp_title)
              .Style(Label(17.0F, FontWeight::Bold)),
      }
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

const ModePresentation &ModeVisual(domain::McpExecutionMode mode) {
  const auto found =
      std::ranges::find(kModePresentations, mode, &ModePresentation::mode);
  return found != kModePresentations.end() ? *found
                                           : kModePresentations.front();
}

std::optional<ToolGroupVisual> GroupVisual(std::string_view id,
                                           domain::McpExecutionMode mode) {
  const auto exact = std::ranges::find_if(
      kToolGroupPresentations, [id, mode](const auto &entry) {
        return entry.id == id && entry.mode == mode;
      });
  const auto generic =
      std::ranges::find_if(kToolGroupPresentations, [id](const auto &entry) {
        return entry.id == id && !entry.mode.has_value();
      });
  const auto found = exact != kToolGroupPresentations.end() ? exact : generic;
  return found == kToolGroupPresentations.end()
             ? std::nullopt
             : std::optional<ToolGroupVisual>{found->visual};
}

View Card(View content) {
  return Stack{
      content,
  }
      .With(Padding(16.0F), Background(colors::elevated), CornerRadius(12.0F),
            Align(HorizontalAlignment::Stretch, VerticalAlignment::Stretch));
}

View ModeButton(const ModePresentation &presentation,
                domain::McpExecutionMode selected, bool interactive,
                std::function<void(domain::McpExecutionMode)> changed) {
  const bool active = presentation.mode == selected;
  return Stack{
      Text(presentation.label)
          .Style(Label(13.0F, FontWeight::Bold,
                       active ? colors::text_on_color : colors::secondary)),
  }
      .OnClick([changed = std::move(changed), value = presentation.mode] {
        changed(value);
      })
      .With(Grow(), Frame{.min_height = 36.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(active ? colors::accent : Color::Transparent()),
            CornerRadius(8.0F), Enabled(interactive), Focusable(),
            PointerCursor(interactive ? PointerCursorKind::Hand
                                      : PointerCursorKind::Default));
}

View ExecutionCard(domain::McpExecutionMode selected,
                   domain::McpExecutionCapabilities capabilities,
                   bool interactive,
                   std::function<void(domain::McpExecutionMode)> changed) {
  std::vector<View> buttons;
  buttons.reserve(kModePresentations.size());
  for (const auto &presentation : kModePresentations) {
    if (!domain::IsMcpExecutionModeAvailable(presentation.mode, capabilities))
      continue;
    buttons.push_back(ModeButton(presentation, selected, interactive, changed)
                          .Key(presentation.mode));
  }

  return Card(Column{
      Text(app::strings::screen_mcp_section_execution)
          .Style(Label(16.0F, FontWeight::Bold)),
      Row(buttons).With(Frame{.height = 42.0F}, Padding(3.0F),
                        Background(colors::surface_light), CornerRadius(8.0F)),
      Text(ModeVisual(selected).description)
          .Style(Label(10.5F, FontWeight::Regular, colors::tertiary)),
  }
                  .With(Spacing(8.0F),
                        CrossAlign(CrossAxisAlignment::Stretch)));
}

View ActionButton(StringResource label, ImageResource icon, bool primary,
                  std::function<void()> action) {
  return Row{
      Glyph(icon, 15.0F, primary ? colors::text_on_color : colors::secondary),
      Text(label).Style(
          Label(11.0F, FontWeight::Bold,
                primary ? colors::text_on_color : colors::secondary)),
  }
      .OnClick(std::move(action))
      .With(Grow(), Frame{.height = 42.0F}, Spacing(6.0F),
            Padding(EdgeInsets::Symmetric(8.0F, 0.0F)),
            MainAlign(MainAxisAlignment::Center),
            CrossAlign(CrossAxisAlignment::Center),
            Background(primary ? colors::accent : colors::surface_light),
            Border(primary ? colors::accent : colors::border_light, 1.0F),
            CornerRadius(8.0F), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View SshConnectionCard(std::function<void()> open_ssh,
                       std::function<void()> open_termux,
                       PlatformCapabilities platform_capabilities) {
  std::vector<View> actions;
  actions.push_back(ActionButton(app::strings::screen_mcp_ssh_settings,
                                 app::images::server, true,
                                 std::move(open_ssh)));
  if (platform_capabilities.termux_integration) {
    actions.push_back(ActionButton(app::strings::screen_mcp_termux_integration,
                                   app::images::smartphone, false,
                                   std::move(open_termux)));
  }
  return Card(Column{
      Text(app::strings::screen_mcp_section_ssh)
          .Style(Label(16.0F, FontWeight::Bold)),
      Text(app::strings::screen_mcp_ssh_overview)
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      Stack{}.With(Frame{.height = 10.0F}),
      Row(actions).With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Stretch)),
  }
                  .With(Spacing(2.0F),
                        CrossAlign(CrossAxisAlignment::Stretch)));
}

View SshModeSupplement(
    const RouteNavigationController<domain::AppRoute> &navigation,
    domain::McpExecutionCapabilities,
    PlatformCapabilities platform_capabilities) {
  return SshConnectionCard(
      [navigation] { navigation.Push(domain::AppRoute::ssh_settings); },
      [navigation] { navigation.Push(domain::AppRoute::termux_integration); },
      platform_capabilities);
}

const std::array kModeSupplements{
    ModeSupplementPresentation{domain::McpExecutionMode::ssh,
                               &SshModeSupplement},
};

View ToolCard(const domain::McpToolGroupState &group,
              const ToolGroupVisual &visual, bool interactive,
              std::function<void(bool)> changed) {
  return Card(Row{
      Stack{
          Glyph(visual.icon, 18.0F,
                group.enabled ? colors::accent : colors::tertiary),
      }
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Background(colors::accent_muted), CornerRadius(18.0F)),
      Column{
          Text(visual.title).Style(Label(16.0F, FontWeight::Medium)),
          Text(visual.description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }
          .With(Spacing(2.0F), Grow()),
      LegacySwitch(group.enabled, std::move(changed)),
  }
                  .With(Spacing(12.0F), CrossAlign(CrossAxisAlignment::Center),
                        Enabled(interactive)));
}

} // namespace

[[huxerui::composable]] View McpSettingsScreen(
    std::shared_ptr<application::McpExecutionSettingsService> service,
    domain::McpExecutionCapabilities capabilities,
    PlatformCapabilities platform_capabilities,
    std::function<void()> on_mode_changed) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  auto state = UseState(McpSettingsPresentationState{});

  Lifecycle([tasks, service, state, toast] {
    tasks.Launch([service, state, toast]() -> Task<void> {
      auto loaded = co_await service->Load();
      if (loaded) {
        state.Update([settings = std::move(*loaded)](
                         McpSettingsPresentationState &next) mutable {
          next.settings = std::move(settings);
          next.loaded = true;
          next.busy = false;
        });
      } else {
        state.Update([](McpSettingsPresentationState &next) {
          next.loaded = false;
          next.busy = false;
        });
        toast.Show(loaded.error().message);
      }
    });
  });

  auto change_mode = [tasks, service, state, toast,
                      on_mode_changed](domain::McpExecutionMode mode) {
    if (!state->Interactive() || state->settings.mode == mode)
      return;
    const auto previous = state->settings;
    state.Update([mode](McpSettingsPresentationState &next) {
      next.settings.mode = mode;
      next.busy = true;
    });
    tasks.Launch([service, state, toast, previous, on_mode_changed,
                  mode]() -> Task<void> {
      auto saved = co_await service->SetMode(mode);
      if (!saved) {
        state.Update([previous](McpSettingsPresentationState &next) {
          next.settings = previous;
          next.busy = false;
        });
        toast.Show(saved.error().message);
        co_return;
      }
      auto loaded = co_await service->Load();
      if (loaded) {
        state.Update([settings = std::move(*loaded)](
                         McpSettingsPresentationState &next) mutable {
          next.settings = std::move(settings);
          next.loaded = true;
          next.busy = false;
        });
        if (on_mode_changed)
          std::invoke(on_mode_changed);
      } else {
        state.Update([previous](McpSettingsPresentationState &next) {
          next.settings = previous;
          next.loaded = false;
          next.busy = false;
        });
        toast.Show(loaded.error().message);
      }
    });
  };

  const auto &settings = state->settings;
  const bool interactive = state->Interactive();
  std::vector<View> cards;
  cards.push_back(
      ExecutionCard(settings.mode, capabilities, interactive, change_mode));
  const auto supplement = std::ranges::find(kModeSupplements, settings.mode,
                                            &ModeSupplementPresentation::mode);
  if (supplement != kModeSupplements.end()) {
    cards.push_back(std::invoke(supplement->factory, navigation, capabilities,
                                platform_capabilities));
  }
  for (const auto &group : settings.groups) {
    if (!domain::SupportsMcpExecutionMode(group.supported_modes,
                                          settings.mode)) {
      continue;
    }
    const auto visual = GroupVisual(group.id, settings.mode);
    if (!visual)
      continue;
    cards.push_back(
        ToolCard(
            group, *visual, interactive,
            [tasks, service, state, toast, id = group.id,
             mode = settings.mode](bool enabled) {
              if (!state->Interactive())
                return;
              const auto previous = state->settings;
              state.Update([&](McpSettingsPresentationState &next) {
                const auto found = std::ranges::find(
                    next.settings.groups, id, &domain::McpToolGroupState::id);
                if (found != next.settings.groups.end())
                  found->enabled = enabled;
                next.busy = true;
              });
              tasks.Launch([service, state, toast, previous, mode, id,
                            enabled]() -> Task<void> {
                auto saved =
                    co_await service->SetToolGroupEnabled(mode, id, enabled);
                if (!saved) {
                  state.Update([previous](McpSettingsPresentationState &next) {
                    next.settings = previous;
                    next.busy = false;
                  });
                  toast.Show(saved.error().message);
                  co_return;
                }
                state.Update([](McpSettingsPresentationState &next) {
                  next.busy = false;
                });
              });
            })
            .Key(group.id));
  }
  cards.push_back(Stack{}.With(Frame{.height = 88.0F}));

  return Column{
      Header(navigation),
      Divider(),
      ScrollView(Column(cards).With(Spacing(12.0F),
                                    CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow(), Padding(16.0F)),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
