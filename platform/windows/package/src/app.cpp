#include <algorithm>
#include <filesystem>
#include <iterator>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <installer_resources.h>
#include <huxerui/huxerui.h>
#include <huxerui/windows/installer.h>

using namespace huxerui;
using namespace huxerui::windows;
namespace installer_strings = installer::strings;

std::string InstallPathText(const std::filesystem::path& path) {
  const std::u8string value = path.u8string();
  return {value.begin(), value.end()};
}

std::optional<std::filesystem::path> ParseInstallPath(std::string_view value) {
  if (value.empty()) {
    return std::nullopt;
  }
  try {
    std::filesystem::path path(std::u8string(value.begin(), value.end()));
    return path.is_absolute() ? std::optional<std::filesystem::path>{path.lexically_normal()} : std::nullopt;
  } catch (const std::filesystem::filesystem_error&) {
    return std::nullopt;
  }
}

View InstallerMark(Color foreground, Color detail) {
  return Canvas([foreground, detail](PaintContext& paint, Size size) {
    const float extent = std::min(size.width, size.height);
    Color tile = foreground;
    tile.alpha = 0.14F;
    paint.DrawRect({0.0F, 0.0F, extent, extent}, tile, CornerRadii{18.0F});
    paint.DrawRect({15.0F, 17.0F, 34.0F, 32.0F}, foreground, CornerRadii{7.0F});
    paint.DrawLine({15.0F, 28.0F}, {49.0F, 28.0F}, detail, StrokeStyle{.width = 2.0F});
    paint.DrawLine({32.0F, 17.0F}, {32.0F, 49.0F}, detail, StrokeStyle{.width = 2.0F});
  }).With(Frame{.width = 64.0F, .height = 64.0F});
}

View BrandPanel(const ThemeSpec& theme) {
  Color deep_primary = theme.colors.primary;
  deep_primary.red *= 0.52F;
  deep_primary.green *= 0.52F;
  deep_primary.blue *= 0.52F;
  Color secondary_text = theme.colors.on_primary;
  secondary_text.alpha = 0.72F;

  return Column {
    InstallerMark(theme.colors.on_primary, deep_primary),
    Column {
      Text(installer_strings::application_setup)
          .Style(TextStyle{Font::System(12.0F).WithWeight(FontWeight::SemiBold), secondary_text}),
      Text("LineCodePro")
          .Style(TextStyle{Font::System(28.0F).WithWeight(FontWeight::SemiBold), theme.colors.on_primary}),
      Text(installer_strings::guided_setup)
          .Style(TextStyle{Font::System(theme.typography.body_medium), secondary_text}),
    }.With(Spacing(12.0F), CrossAlign(CrossAxisAlignment::Start)),
    Spacer().With(Grow()),
    Text(installer_strings::platform_windows)
        .Style(TextStyle{Font::System(11.0F).WithWeight(FontWeight::SemiBold), secondary_text}),
  }.With(
      Frame{.width = 236.0F},
      Padding(32.0F),
      Spacing(30.0F),
      Background(LinearGradient{
          .start = {0.0F, 0.0F},
          .end = {1.0F, 1.0F},
          .stops = {{0.0F, theme.colors.primary}, {1.0F, deep_primary}},
      }),
      CrossAlign(CrossAxisAlignment::Start)
  );
}

View CompletionMark(const ThemeSpec& theme) {
  Color background = theme.colors.primary;
  background.alpha = 0.12F;
  return Canvas([background, foreground = theme.colors.primary](PaintContext& paint, Size) {
    paint.DrawCircle({26.0F, 26.0F}, 26.0F, background);
    const StrokeStyle stroke{.width = 3.0F, .cap = StrokeCap::Round, .join = StrokeJoin::Round};
    paint.DrawLine({15.0F, 27.0F}, {23.0F, 35.0F}, foreground, stroke);
    paint.DrawLine({23.0F, 35.0F}, {39.0F, 18.0F}, foreground, stroke);
  }).With(Frame{.width = 52.0F, .height = 52.0F});
}

View SecondaryAction(View action, const ThemeSpec& theme) {
  Color disabled_background = theme.colors.on_surface;
  disabled_background.alpha = 0.08F;
  Color disabled_label = theme.colors.on_surface;
  disabled_label.alpha = 0.38F;
  ThemeDefinition definition;
  definition.Set(ButtonStyle{
      .background = theme.colors.surface_container_highest,
      .label_style =
          TextStyle{Font::System(theme.typography.label_large).WithWeight(FontWeight::Medium), theme.colors.on_surface},
      .disabled_background = disabled_background,
      .disabled_label = disabled_label,
      .padding = EdgeInsets::Symmetric(20.0F, 8.0F),
      .minimum_width = 72.0F,
      .minimum_height = 40.0F,
      .corner_radius = theme.shapes.full,
      .indication = theme.interactions.indication,
  });
  return Theme {std::move(definition), std::move(action)};
}

std::vector<View> PromptActions(const InstallerHandle& installer, const InstallerPrompt& prompt,
                                const ThemeSpec& theme) {
  std::vector<View> actions;
  for (const InstallerPromptChoice choice : prompt.choices) {
    StringVariant label = installer_strings::continue_action;
    switch (choice) {
    case InstallerPromptChoice::Ok:
      label = installer_strings::ok;
      break;
    case InstallerPromptChoice::Cancel:
      label = installer_strings::cancel;
      break;
    case InstallerPromptChoice::Abort:
      label = installer_strings::abort;
      break;
    case InstallerPromptChoice::Retry:
      label = installer_strings::retry;
      break;
    case InstallerPromptChoice::TryAgain:
      label = installer_strings::try_again;
      break;
    case InstallerPromptChoice::Ignore:
      label = installer_strings::ignore;
      break;
    case InstallerPromptChoice::Yes:
      label = installer_strings::yes;
      break;
    case InstallerPromptChoice::No:
      label = installer_strings::no;
      break;
    case InstallerPromptChoice::Continue:
      label = installer_strings::continue_action;
      break;
    }
    View action = Button(std::move(label)).OnClick([installer, id = prompt.id, choice] {
      installer.Respond(id, choice);
    });
    if (!prompt.recommended || choice != *prompt.recommended) {
      action = SecondaryAction(std::move(action), theme);
    }
    actions.push_back(std::move(action));
  }
  return actions;
}

[[huxerui::composable]]
View InstallerContent() {
  const InstallerHandle installer = UseInstaller();
  const WindowHandle window = UseWindow();
  const TaskScope tasks = UseTaskScope();
  const ThemeSpec& theme = UseTheme();
  const InstallerStatus status = installer.Status();
  auto destination = UseState<std::optional<TextEditingValue>>(std::nullopt);
  auto desktop_shortcut = UseState<std::optional<bool>>(std::nullopt);
  StringVariant eyebrow;
  StringVariant heading;
  std::vector<View> details;
  std::vector<View> actions;
  MainAxisAlignment panel_alignment = MainAxisAlignment::Start;

  if (status.prompt) {
    eyebrow = installer_strings::action_required;
    heading = installer_strings::setup_needs_attention;
    StringVariant prompt_message = status.prompt->message;
    if (status.prompt->kind == InstallerPromptKind::FilesInUse && status.prompt->message.empty()) {
      prompt_message = installer_strings::files_in_use;
    }
    details.push_back(Text(std::move(prompt_message)).With(Foreground(theme.colors.on_surface_variant)));
    actions = PromptActions(installer, *status.prompt, theme);
  } else if (status.phase == InstallerPhase::Detecting) {
    eyebrow = installer_strings::welcome;
    heading = installer_strings::preparing_setup;
    panel_alignment = MainAxisAlignment::Center;
    details.push_back(
        Text(installer_strings::checking_installed_version).With(Foreground(theme.colors.on_surface_variant))
    );
    details.push_back(ProgressBar(status.progress));
  } else if (status.phase == InstallerPhase::Ready &&
             status.product == InstallerProductState::NewerVersion) {
    eyebrow = installer_strings::version_check;
    heading = installer_strings::newer_version_installed;
    panel_alignment = MainAxisAlignment::Center;
    details.push_back(Text(installer_strings::remove_newer_version)
                          .With(Foreground(theme.colors.on_surface_variant)));
    actions.push_back(
        Button(installer_strings::close).OnClick([window] { window.Close(); }).With(Frame{.min_width = 112.0F})
    );
  } else if (status.phase == InstallerPhase::Ready && status.product == InstallerProductState::Present) {
    eyebrow = installer_strings::maintenance;
    heading = installer_strings::manage_installation;
    details.push_back(
        Text(installer_strings::already_installed).With(Foreground(theme.colors.on_surface_variant))
    );
    actions.push_back(
        SecondaryAction(Button(installer_strings::cancel).OnClick([window] { window.Close(); }), theme)
    );
    actions.push_back(SecondaryAction(
        Button(installer_strings::uninstall).OnClick([installer] { installer.Uninstall(); }), theme
    ));
    actions.push_back(
        Button(installer_strings::repair)
            .OnClick([installer] { installer.Repair(); })
            .With(Frame{.min_width = 112.0F})
    );
  } else if (status.phase == InstallerPhase::Ready && status.product == InstallerProductState::Absent) {
    const TextEditingValue destination_value = destination.Get().value_or(
        TextEditingValue::FromText(InstallPathText(status.default_destination))
    );
    const std::optional<std::filesystem::path> destination_path = ParseInstallPath(destination_value.text);
    const std::filesystem::path browse_initial = destination_path.value_or(status.default_destination);
    const bool create_desktop_shortcut =
        desktop_shortcut.Get().value_or(status.default_create_desktop_shortcut);
    eyebrow = installer_strings::installation;
    heading = installer_strings::ready_to_install;
    details.push_back(
        Text(installer_strings::installation_options).With(Foreground(theme.colors.on_surface_variant))
    );
    details.push_back(Row {
      TextField(destination_value)
          .Label(installer_strings::installation_folder)
          .Variant(TextFieldVariant::Outlined)
          .Validation(
              destination_path ? ValidationResult::None()
                               : ValidationResult::Invalid(installer_strings::invalid_installation_folder)
          )
          .OnChanged([destination](const TextEditingValue& value) { destination = value; })
          .With(Grow()),
      SecondaryAction(
          Button(installer_strings::browse).OnClick([installer, destination, browse_initial, tasks] {
            tasks.Launch([installer, destination, browse_initial]() -> Task<void> {
              const std::optional<std::filesystem::path> selected =
                  co_await installer.ChooseDestinationAsync(browse_initial);
              if (selected) {
                destination = TextEditingValue::FromText(InstallPathText(*selected));
              }
            });
          }),
          theme
      ),
    }.With(Spacing(12.0F), CrossAlign(CrossAxisAlignment::Center)));
    Color option_border = theme.colors.outline;
    option_border.alpha = 0.18F;
    details.push_back(
        Checkbox(installer_strings::create_desktop_shortcut, create_desktop_shortcut)
            .OnChanged([desktop_shortcut](bool enabled) { desktop_shortcut = enabled; })
            .With(
                Padding(EdgeInsets::Symmetric(14.0F, 10.0F)),
                Background(theme.colors.surface_container_low),
                Border{.color = option_border},
                CornerRadius(theme.shapes.medium)
            )
    );
    actions.push_back(
        SecondaryAction(Button(installer_strings::cancel).OnClick([window] { window.Close(); }), theme)
    );
    actions.push_back(
        Button(installer_strings::install)
            .OnClick([installer, destination_path, create_desktop_shortcut] {
              if (destination_path) {
                installer.Install({
                    .destination = *destination_path,
                    .create_desktop_shortcut = create_desktop_shortcut,
                });
              }
            })
            .With(Enabled(destination_path.has_value()), Frame{.min_width = 112.0F})
    );
  } else if (status.phase == InstallerPhase::Ready) {
    eyebrow = installer_strings::setup;
    heading = installer_strings::setup_could_not_continue;
    panel_alignment = MainAxisAlignment::Center;
    details.push_back(
        Text(installer_strings::installed_version_unknown).With(Foreground(theme.colors.on_surface_variant))
    );
    actions.push_back(
        Button(installer_strings::close).OnClick([window] { window.Close(); }).With(Frame{.min_width = 112.0F})
    );
  } else if (status.phase == InstallerPhase::Planning || status.phase == InstallerPhase::Applying ||
             status.phase == InstallerPhase::Canceling) {
    eyebrow = installer_strings::installing;
    heading = installer_strings::installing_product;
    panel_alignment = MainAxisAlignment::Center;
    if (status.phase == InstallerPhase::Canceling) {
      eyebrow = installer_strings::rollback;
      heading = installer_strings::canceling_and_rolling_back;
    } else if (status.action == InstallerAction::Repair) {
      eyebrow = installer_strings::repairing;
      heading = installer_strings::repairing_product;
    } else if (status.action == InstallerAction::Uninstall) {
      eyebrow = installer_strings::removing;
      heading = installer_strings::uninstalling_product;
    }
    StringVariant progress_message = status.current_package.empty()
                                         ? StringVariant(installer_strings::operation_may_take_a_moment)
                                         : StringVariant(status.current_package);
    details.push_back(Text(std::move(progress_message)).With(Foreground(theme.colors.on_surface_variant)));
    details.push_back(ProgressBar(status.progress));
    actions.push_back(
        SecondaryAction(Button(installer_strings::cancel).OnClick([installer] { installer.Cancel(); }), theme)
    );
  } else if (status.phase == InstallerPhase::Completed) {
    eyebrow = installer_strings::complete;
    heading = status.action == InstallerAction::Uninstall ? installer_strings::application_removed
                                                          : installer_strings::installation_complete;
    panel_alignment = MainAxisAlignment::Center;
    StringVariant message;
    if (status.action == InstallerAction::Uninstall) {
      message = status.restart == InstallerRestart::Required ? installer_strings::application_removed_restart_message
                                                              : installer_strings::application_removed_message;
    } else {
      message = status.restart == InstallerRestart::Required
                    ? installer_strings::installation_complete_restart_message
                    : installer_strings::installation_complete_message;
    }
    details.push_back(Row {CompletionMark(theme)}.With(MainAlign(MainAxisAlignment::Center)));
    details.push_back(Text(std::move(message)).With(Foreground(theme.colors.on_surface_variant)));
    actions.push_back(
        Button(installer_strings::close).OnClick([window] { window.Close(); }).With(Frame{.min_width = 112.0F})
    );
  } else if (status.phase == InstallerPhase::Canceled) {
    eyebrow = installer_strings::canceled;
    heading = installer_strings::installation_canceled;
    panel_alignment = MainAxisAlignment::Center;
    details.push_back(
        Text(installer_strings::no_further_changes).With(Foreground(theme.colors.on_surface_variant))
    );
    actions.push_back(
        Button(installer_strings::close).OnClick([window] { window.Close(); }).With(Frame{.min_width = 112.0F})
    );
  } else if (status.failure) {
    eyebrow = installer_strings::setup_error;
    heading = installer_strings::installation_failed;
    panel_alignment = MainAxisAlignment::Center;
    details.push_back(Text(status.failure->message).With(Foreground(theme.colors.error)));
    actions.push_back(
        Button(installer_strings::close).OnClick([window] { window.Close(); }).With(Frame{.min_width = 112.0F})
    );
  }

  std::vector<View> panel{
      Text(std::move(eyebrow))
          .Style(TextStyle{Font::System(12.0F).WithWeight(FontWeight::SemiBold), theme.colors.primary}),
      Text(std::move(heading), TextRole::Title).With(FontSize(theme.typography.headline_small)),
  };
  panel.insert(panel.end(), std::make_move_iterator(details.begin()), std::make_move_iterator(details.end()));

  std::vector<View> content;
  content.push_back(Column(std::move(panel)).With(
      Spacing(16.0F),
      MainAlign(panel_alignment),
      CrossAlign(CrossAxisAlignment::Stretch),
      Grow()
  ));
  if (!actions.empty()) {
    content.push_back(Divider());
    content.push_back(Row(std::move(actions)).With(
        Spacing(10.0F),
        MainAlign(MainAxisAlignment::End),
        CrossAlign(CrossAxisAlignment::Center)
    ));
  }

  return Row {
    BrandPanel(theme),
    Column(std::move(content)).With(
        Padding(EdgeInsets::Symmetric(36.0F, 32.0F)),
        Spacing(24.0F),
        CrossAlign(CrossAxisAlignment::Stretch),
        Grow()
    ),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(theme.colors.surface), Grow());
}

View InstallerPage() {
  return MaterialTheme {InstallerContent()};
}

const Application application{
    InstallerPage,
    {
        .window = {
            .title = "LineCodePro",
            .initial_size = {780.0F, 500.0F},
            .minimum_size = Size{720.0F, 460.0F},
        },
        .show_debug_overlay = false,
        .root_hooks = {InstallInstallerSession},
    },
};
