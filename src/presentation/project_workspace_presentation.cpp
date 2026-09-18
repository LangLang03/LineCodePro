#include "presentation/project_workspace_presentation.h"

#include <algorithm>
#include <filesystem>
#include <functional>
#include <iterator>
#include <ranges>
#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/mcp_execution_settings.h"
#include "application/ports/project_workspace_controller.h"
#include "application/ports/storage_permission.h"
#include "application/ssh_project_workspace.h"
#include "application/ssh_settings_service.h"
#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

using WorkspaceVoidResult = application::ProjectWorkspaceResult<void>;
using WorkspaceMutation = std::function<Task<WorkspaceVoidResult>(
    const std::shared_ptr<application::ProjectWorkspaceController> &,
    const domain::ProjectRecord &)>;

bool IsProtectedProject(std::string_view id) {
  return id == domain::default_project_id ||
         id == application::kDefaultSshProjectId;
}

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

Indication RowIndication(float radius) {
  return Indication{
      .geometry = {.clip_corner_radii = CornerRadii(radius)},
      .press =
          IndicationLayer{
              .fill = VisualFill{colors::accent_muted_strong},
              .corner_radii = CornerRadii(radius),
              .placement = IndicationPlacement::BehindContent,
          },
  };
}

void CollectExpanded(const domain::ProjectFileNode &node,
                     std::vector<std::string> &paths) {
  if (node.directory && node.expanded)
    paths.push_back(node.path);
  for (const auto &child : node.children)
    CollectExpanded(child, paths);
}

void RestoreExpanded(domain::ProjectFileNode &node,
                     std::span<const std::string> paths) {
  node.expanded = std::ranges::find(paths, node.path) != paths.end();
  for (auto &child : node.children)
    RestoreExpanded(child, paths);
}

bool ToggleDrawerDirectory(DrawerFileNode &node, std::string_view path) {
  if (node.directory && node.path == path) {
    node.expanded = !node.expanded;
    return true;
  }
  return std::ranges::any_of(node.children, [&](DrawerFileNode &child) {
    return ToggleDrawerDirectory(child, path);
  });
}

Task<void> RefreshWorkspace(
    const std::shared_ptr<application::ProjectWorkspaceController> &service,
    State<ProjectWorkspacePresentationState> state, State<DrawerModel> drawer,
    ToastHandle toast, bool report_error) {
  std::vector<std::string> expanded;
  if (state->file_tree)
    CollectExpanded(*state->file_tree, expanded);
  state.Update([](auto &next) {
    next.loading = true;
    next.error.clear();
  });

  auto projects = co_await service->ListProjects();
  if (!projects) {
    const auto message = projects.error().message;
    state.Update([&message](auto &next) {
      next.loading = false;
      next.error = message;
    });
    if (report_error)
      toast.Show(message);
    co_return;
  }
  auto selected = co_await service->SelectedProject();
  if (!selected) {
    const auto message = selected.error().message;
    state.Update([&message](auto &next) {
      next.loading = false;
      next.error = message;
    });
    if (report_error)
      toast.Show(message);
    co_return;
  }
  auto tree = co_await service->LoadTree(selected->id);
  if (!tree) {
    const auto message = tree.error().message;
    state.Update([projects = std::move(*projects), selected = *selected,
                  &message](auto &next) mutable {
      next.projects = std::move(projects);
      next.selected = std::move(selected);
      next.file_tree.reset();
      next.loading = false;
      next.error = message;
    });
    drawer.Update([&state](DrawerModel &next) {
      next.project_label = state->selected->label;
      next.project_path = state->selected->path;
      next.project_removable = !IsProtectedProject(state->selected->id);
      next.file_tree.reset();
    });
    if (report_error)
      toast.Show(message);
    co_return;
  }
  if (!expanded.empty())
    RestoreExpanded(*tree, expanded);
  tree->expanded = true;
  state = ProjectWorkspacePresentationState{
      .projects = std::move(*projects),
      .selected = std::move(*selected),
      .file_tree = std::move(*tree),
      .loading = false,
      .error = {},
  };
  drawer.Update([&state](DrawerModel &next) {
    next.project_label = state->selected->label;
    next.project_path = state->selected->path;
    next.project_removable = !IsProtectedProject(state->selected->id);
    next.file_tree = ToDrawerFileNode(*state->file_tree);
  });
}

void LaunchMutation(
    const std::shared_ptr<application::ProjectWorkspaceController> &service,
    State<ProjectWorkspacePresentationState> state, State<DrawerModel> drawer,
    const TaskScope &tasks, ToastHandle toast, WorkspaceMutation mutation) {
  const auto selected = state->selected;
  if (!selected) {
    toast.Show(app::strings::workspace_error_no_project);
    return;
  }
  tasks.Launch([service, state, drawer, toast, selected = *selected,
                mutation = std::move(mutation)]() mutable -> Task<void> {
    auto result = co_await std::invoke(mutation, service, selected);
    if (!result) {
      toast.Show(result.error().message);
      co_return;
    }
    co_await RefreshWorkspace(service, state, drawer, toast, true);
  });
}

View SheetHandle() {
  return Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                      Background(colors::tertiary), CornerRadius(2.0F));
}

View SheetTitle(StringVariant title) {
  return Text(std::move(title))
      .Style(Label(17.0F, FontWeight::Bold))
      .With(Padding(EdgeInsets{
          .top = 12.0F, .right = 24.0F, .bottom = 20.0F, .left = 24.0F}));
}

View SheetPanel(StringVariant title, std::vector<View> rows) {
  View choices = ScrollView(Column(std::move(rows))
                                .With(Padding(EdgeInsets{.bottom = 16.0F}),
                                      CrossAlign(CrossAxisAlignment::Stretch)))
                     .ScrollAxis(Axis::Vertical)
                     .With(Frame{.max_height = 620.0F});
  View panel =
      Column{
          Row{SheetHandle()}.With(
              Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F}),
              MainAlign(MainAxisAlignment::Center)),
          SheetTitle(std::move(title)),
          Divider(),
          choices,
      }
          .With(Frame{.max_width = 560.0F},
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::background),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(24.0F), ClipChildren());
  return Row{std::move(panel).With(Grow())}.With(
      Padding(EdgeInsets{
          .top = 0.0F, .right = 16.0F, .bottom = 16.0F, .left = 16.0F}),
      MainAlign(MainAxisAlignment::Center));
}

View SheetRow(StringVariant label, StringVariant description, bool selected,
              std::function<void()> action) {
  std::vector<View> trailing;
  if (selected) {
    trailing.push_back(Image(app::images::check)
                           .Tint(colors::accent)
                           .With(Frame{.width = 18.0F, .height = 18.0F}));
  }
  std::vector<View> labels;
  labels.push_back(Text(std::move(label)).Style(Label(16.0F)));
  labels.push_back(
      Text(std::move(description))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)));
  return Row{
      Column(std::move(labels)).With(Spacing(2.0F), Grow()),
      Row(std::move(trailing)),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 52.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 14.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(selected ? colors::accent_muted : Color::Transparent()),
            Indication(RowIndication(0.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View FileActionRow(DialogContext dialog, StringVariant label,
                   StringVariant description, bool danger,
                   std::function<void()> action) {
  return Column{
      Text(std::move(label))
          .Style(Label(16.0F, FontWeight::Regular,
                       danger ? colors::danger : colors::text)),
      Text(std::move(description))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
  }
      .OnClick([dialog, action = std::move(action)] {
        dialog.Dismiss();
        std::invoke(action);
      })
      .With(Frame{.min_height = 52.0F}, Spacing(6.0F),
            Padding(EdgeInsets::Symmetric(0.0F, 16.0F)),
            CrossAlign(CrossAxisAlignment::Stretch),
            Indication(RowIndication(8.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View FileActionDialog(StringVariant title, StringVariant subtitle,
                      std::vector<View> rows) {
  std::vector<View> children;
  children.reserve(rows.size() + 3);
  children.push_back(
      Text(std::move(title)).Style(Label(17.0F, FontWeight::Medium)));
  children.push_back(
      Text(std::move(subtitle))
          .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 4.0F})));
  children.push_back(
      Divider().With(Padding(EdgeInsets{.top = 12.0F, .bottom = 4.0F})));
  std::ranges::move(rows, std::back_inserter(children));
  return Column(std::move(children))
      .With(Frame{.min_width = 280.0F, .max_width = 560.0F}, Padding(16.0F),
            CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::elevated), CornerRadius(16.0F), ClipChildren());
}

View DialogAction(StringVariant text, Color tint,
                  std::function<void()> action) {
  return Text(std::move(text))
      .Style(Label(16.0F, FontWeight::Bold, tint))
      .Align(TextAlign::Center)
      .OnClick(std::move(action))
      .With(Frame{.min_height = 40.0F},
            Padding(EdgeInsets::Symmetric(12.0F, 8.0F)), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View DialogPanel(StringVariant title, std::vector<View> content) {
  std::vector<View> children;
  children.reserve(content.size() + 1);
  children.push_back(
      Text(std::move(title)).Style(Label(20.0F, FontWeight::Bold)));
  std::ranges::move(content, std::back_inserter(children));
  return Column(std::move(children))
      .With(Frame{.min_width = 280.0F, .max_width = 560.0F}, Spacing(12.0F),
            Padding(24.0F), CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), CornerRadius(24.0F));
}

View ConfirmationDialog(DialogContext dialog, StringVariant title,
                        StringVariant message, std::function<void()> confirm) {
  return DialogPanel(
      std::move(title),
      {Text(std::move(message))
           .Style(Label(13.0F, FontWeight::Regular, colors::secondary)),
       Row{
           DialogAction(app::strings::common_cancel, colors::secondary,
                        [dialog] { dialog.Dismiss(); }),
           DialogAction(app::strings::workspace_delete, colors::danger,
                        [dialog, confirm = std::move(confirm)] {
                          dialog.Dismiss();
                          std::invoke(confirm);
                        }),
       }
           .With(MainAlign(MainAxisAlignment::End),
                 CrossAlign(CrossAxisAlignment::Center),
                 Padding(EdgeInsets{.top = 4.0F}))});
}

TextFieldStyle WorkspaceInputStyle() {
  auto style = TextFieldStyle::Default();
  style.variant = TextFieldVariant::Outlined;
  style.show_label = false;
  style.outlined.background = colors::input;
  style.outlined.border = colors::border_light;
  style.outlined.hovered_border = colors::border_light;
  style.outlined.focused_border = colors::accent;
  style.outlined.minimum_height = 48.0F;
  style.outlined.corner_radii = CornerRadii{8.0F};
  style.text_style = Label(16.0F);
  style.caret = colors::accent;
  style.selection = colors::accent_muted_strong;
  style.padding = EdgeInsets::Symmetric(12.0F, 10.0F);
  return style;
}

[[huxerui::composable]] View
NameDialog(DialogContext dialog, StringVariant title, StringVariant message,
           std::string initial_value, std::function<void(std::string)> submit) {
  auto value = UseState(TextEditingValue::FromText(std::move(initial_value)));
  ThemeDefinition overrides;
  overrides.Set(WorkspaceInputStyle());
  View field = Theme(
      overrides,
      TextField(value)
          .Variant(TextFieldVariant::Outlined)
          .InputConfiguration(TextInputConfiguration{
              .type = TextInputType::Text,
              .capitalization = TextCapitalization::None,
              .action = TextInputAction::Done,
              .multiline = false,
              .secure = false,
              .autocorrect = false,
          })
          .OnChanged([value](const TextEditingValue &next) { value = next; })
          .OnSubmitted([dialog, value, submit] {
            dialog.Dismiss();
            std::invoke(submit, value->text);
          }));
  return DialogPanel(
      std::move(title),
      {Text(std::move(message))
           .Style(Label(13.0F, FontWeight::Regular, colors::secondary)),
       field,
       Row{
           DialogAction(app::strings::common_cancel, colors::secondary,
                        [dialog] { dialog.Dismiss(); }),
           DialogAction(app::strings::common_confirm, colors::accent,
                        [dialog, value, submit] {
                          dialog.Dismiss();
                          std::invoke(submit, value->text);
                        }),
       }
           .With(MainAlign(MainAxisAlignment::End),
                 CrossAlign(CrossAxisAlignment::Center),
                 Padding(EdgeInsets{.top = 4.0F}))});
}

View ProjectRow(BottomSheetContext sheet, const domain::ProjectRecord &project,
                const ProjectWorkspaceCoordinator &coordinator) {
  const auto display_path = WorkspaceDisplayPath(project.path);
  const StringVariant description =
      !display_path.empty() ? StringVariant{display_path}
      : project.source == domain::ProjectSource::ssh
          ? StringVariant{app::strings::workspace_ssh_login_directory}
          : StringVariant{project.description};
  View row = SheetRow(project.label, description, project.selected,
                      [sheet, coordinator, id = project.id] {
                        sheet.Dismiss();
                        coordinator.SelectProject(id);
                      });
  if (!IsProtectedProject(project.id)) {
    row = std::move(row)
              .On<LongPressEvents::Started>(
                  [sheet, coordinator, project](const LongPressEvent &) {
                    sheet.Dismiss();
                    coordinator.ConfirmDeleteProject(project);
                  })
              .With(LongPressGesture{});
  }
  return std::move(row).Key(project.id);
}

[[huxerui::composable]] View
ProjectSheet(BottomSheetContext sheet,
             State<ProjectWorkspacePresentationState> state,
             ProjectWorkspaceCoordinator coordinator) {
  std::vector<View> rows;
  rows.reserve(state->projects.size() + 3);
  for (const auto &project : state->projects)
    rows.push_back(ProjectRow(sheet, project, coordinator));
  const bool ssh_mode =
      state->selected && state->selected->source == domain::ProjectSource::ssh;
  const bool local_project_available =
      !ssh_mode || coordinator.IsTermuxSshMode();
  if (local_project_available) {
    rows.push_back(SheetRow(
        app::strings::workspace_open_local,
        ssh_mode ? StringVariant(app::strings::workspace_open_local_ssh_desc)
                 : StringVariant(app::strings::workspace_open_local_desc),
        false, [sheet, coordinator] {
          sheet.Dismiss();
          coordinator.OpenExternalProject();
        }));
  }
  rows.push_back(
      SheetRow(app::strings::workspace_create,
               ssh_mode ? StringVariant(app::strings::workspace_create_ssh_desc)
                        : StringVariant(app::strings::workspace_create_desc),
               false, [sheet, coordinator] {
                 sheet.Dismiss();
                 coordinator.ShowCreateProjectDialog();
               }));
  if (local_project_available &&
      FeatureAvailable<PlatformFeature::android_storage_permission>) {
    rows.push_back(SheetRow(
        app::strings::permission_mode_manage_all_files,
        coordinator.ExternalStorageGranted()
            ? StringVariant(app::strings::permission_mode_storage_granted)
            : StringVariant(app::strings::permission_mode_storage_required),
        coordinator.ExternalStorageGranted(), [sheet, coordinator] {
          sheet.Dismiss();
          coordinator.OpenStorageManagement();
        }));
  }
  return SheetPanel(ssh_mode ? StringVariant(app::strings::workspace_title_ssh)
                             : StringVariant(app::strings::workspace_title),
                    std::move(rows));
}

} // namespace

ProjectWorkspaceCoordinator::ProjectWorkspaceCoordinator(
    std::shared_ptr<application::ProjectWorkspaceController> service,
    State<ProjectWorkspacePresentationState> state, State<DrawerModel> drawer,
    State<WorkspaceClipboard> clipboard, TaskScope tasks,
    BottomSheetHandle sheets, DialogHandle dialogs, ToastHandle toast,
    std::shared_ptr<FilePicker> picker,
    std::shared_ptr<application::StoragePermissionService> storage_permission,
    State<bool> external_storage_granted, State<bool> termux_ssh_mode,
    std::shared_ptr<application::McpExecutionSettingsService>
        execution_settings,
    std::shared_ptr<application::SshSettingsService> ssh_settings)
    : service_(std::move(service)), state_(state), drawer_(drawer),
      clipboard_(clipboard), tasks_(std::move(tasks)),
      sheets_(std::move(sheets)), dialogs_(std::move(dialogs)),
      toast_(std::move(toast)), picker_(std::move(picker)),
      storage_permission_(std::move(storage_permission)),
      external_storage_granted_(external_storage_granted),
      termux_ssh_mode_(termux_ssh_mode),
      execution_settings_(std::move(execution_settings)),
      ssh_settings_(std::move(ssh_settings)) {}

void ProjectWorkspaceCoordinator::Refresh() const {
  if (!service_)
    return;
  tasks_.Launch(
      [service = service_, state = state_, drawer = drawer_, toast = toast_] {
        return RefreshWorkspace(service, state, drawer, toast, true);
      });
}

void ProjectWorkspaceCoordinator::ShowProjectPicker() const {
  Refresh();
  const auto present = [sheets = sheets_, state = state_, coordinator = *this] {
    sheets.Show([state, coordinator](BottomSheetContext sheet) {
      return ProjectSheet(sheet, state, coordinator);
    });
  };
  const auto present_after_permission = [permission = storage_permission_,
                                         granted = external_storage_granted_,
                                         toast = toast_, present] {
    if constexpr (FeatureAvailable<
                      PlatformFeature::android_storage_permission>) {
      if (permission) {
        permission->Query([granted, toast, present](
                              application::StoragePermissionResult result) {
          if (result.Succeeded()) {
            granted = result.granted;
          } else {
            toast.Show(result.error);
          }
          present();
        });
        return;
      }
    }
    present();
  };
  if (!execution_settings_ || !ssh_settings_) {
    termux_ssh_mode_ = false;
    present_after_permission();
    return;
  }
  tasks_.Launch([execution_settings = execution_settings_,
                 ssh_settings = ssh_settings_,
                 termux_ssh_mode = termux_ssh_mode_,
                 present_after_permission]() -> Task<void> {
    const auto execution = co_await execution_settings->Load();
    if (!execution || execution->mode != domain::McpExecutionMode::ssh) {
      termux_ssh_mode = false;
      present_after_permission();
      co_return;
    }
    const auto ssh = co_await ssh_settings->Load();
    termux_ssh_mode = ssh && domain::IsTermuxSshHost(ssh->host);
    present_after_permission();
  });
}

void ProjectWorkspaceCoordinator::SelectProject(std::string id) const {
  tasks_.Launch([service = service_, state = state_, drawer = drawer_,
                 toast = toast_, id = std::move(id)]() mutable -> Task<void> {
    auto selected = co_await service->SelectProject(std::move(id));
    if (!selected) {
      toast.Show(selected.error().message);
      co_return;
    }
    co_await RefreshWorkspace(service, state, drawer, toast, true);
  });
}

void ProjectWorkspaceCoordinator::ShowCreateProjectDialog() const {
  dialogs_.Show([coordinator = *this](DialogContext dialog) {
    return NameDialog(
        dialog, app::strings::workspace_create,
        app::strings::workspace_create_prompt, {},
        [coordinator](std::string name) {
          coordinator.tasks_.Launch(
              [service = coordinator.service_, state = coordinator.state_,
               drawer = coordinator.drawer_, toast = coordinator.toast_,
               name = std::move(name)]() mutable -> Task<void> {
                auto created =
                    co_await service->CreateManagedProject(std::move(name));
                if (!created) {
                  toast.Show(created.error().message);
                  co_return;
                }
                co_await RefreshWorkspace(service, state, drawer, toast, true);
              });
        });
  });
}

void ProjectWorkspaceCoordinator::OpenExternalProject() const {
  if (!picker_ || !picker_->CanOpenDirectories(true)) {
    toast_.Show(app::strings::workspace_picker_unavailable);
    return;
  }
  tasks_.Launch([service = service_, state = state_, drawer = drawer_,
                 picker = picker_, toast = toast_]() -> Task<void> {
    auto reference = co_await picker->OpenDirectoryAsync(true);
    if (!reference)
      co_return;
    const auto local = reference->AsFile();
    if (!local) {
      toast.Show(app::strings::workspace_picker_path_unavailable);
      co_return;
    }
    auto registered = co_await service->RegisterExternalProject(
        local->Path(), reference->Name());
    if (!registered) {
      toast.Show(registered.error().message);
      co_return;
    }
    co_await RefreshWorkspace(service, state, drawer, toast, true);
  });
}

void ProjectWorkspaceCoordinator::OpenStorageManagement() const {
  if (!storage_permission_)
    return;
  storage_permission_->OpenManagementSettings(
      [granted = external_storage_granted_,
       toast = toast_](application::StoragePermissionResult result) {
        if (!result.Succeeded()) {
          toast.Show(result.error);
          return;
        }
        granted = result.granted;
      });
}

void ProjectWorkspaceCoordinator::ConfirmDeleteProject(
    domain::ProjectRecord project) const {
  if (IsProtectedProject(project.id))
    return;
  dialogs_.Show([coordinator = *this,
                 project = std::move(project)](DialogContext dialog) mutable {
    return ConfirmationDialog(
        dialog, app::strings::workspace_remove_project_title,
        StringVariant::Format(app::strings::workspace_remove_project_message,
                              project.label),
        [coordinator, id = std::move(project.id)] {
          coordinator.tasks_.Launch(
              [service = coordinator.service_, state = coordinator.state_,
               drawer = coordinator.drawer_, toast = coordinator.toast_,
               id]() -> Task<void> {
                auto removed = co_await service->DeleteProject(id);
                if (!removed) {
                  toast.Show(removed.error().message);
                  co_return;
                }
                co_await RefreshWorkspace(service, state, drawer, toast, true);
              });
        });
  });
}

void ProjectWorkspaceCoordinator::ToggleNode(
    const DrawerFileTarget &target) const {
  if (!target.directory || !state_->file_tree)
    return;
  state_.Update([&target](auto &next) {
    if (next.file_tree)
      ToggleWorkspaceDirectory(*next.file_tree, target.path);
  });
  drawer_.Update([&target](DrawerModel &next) {
    if (next.file_tree)
      ToggleDrawerDirectory(*next.file_tree, target.path);
  });
}

void ProjectWorkspaceCoordinator::ShowFileActions(
    DrawerFileTarget target) const {
  if (!target.directory && target.root)
    return;
  dialogs_.Show([coordinator = *this,
                 target = std::move(target)](DialogContext dialog) mutable {
    // Every row owns a typed command. MainScreen never parses action ids.
    std::vector<View> rows;
    if (target.directory) {
      rows.push_back(FileActionRow(dialog, app::strings::workspace_new_file,
                                   target.path, false, [coordinator, target] {
                                     coordinator.ShowCreateEntryDialog(target,
                                                                       false);
                                   }));
      rows.push_back(FileActionRow(dialog, app::strings::workspace_new_folder,
                                   target.path, false, [coordinator, target] {
                                     coordinator.ShowCreateEntryDialog(target,
                                                                       true);
                                   }));
      if (!coordinator.clipboard_->Empty()) {
        rows.push_back(FileActionRow(
            dialog, app::strings::workspace_paste, coordinator.clipboard_->name,
            false, [coordinator, target] { coordinator.PasteInto(target); }));
      }
    }
    if (!target.root) {
      rows.push_back(FileActionRow(
          dialog, app::strings::workspace_copy, target.name, false,
          [coordinator, target] { coordinator.CopyEntry(target); }));
      rows.push_back(FileActionRow(
          dialog, app::strings::workspace_rename, target.name, false,
          [coordinator, target] { coordinator.ShowRenameDialog(target); }));
      rows.push_back(FileActionRow(
          dialog, app::strings::workspace_delete, target.path, true,
          [coordinator, target] { coordinator.ConfirmDeleteEntry(target); }));
    }
    return FileActionDialog(
        target.root ? StringVariant{app::strings::workspace_root_actions}
                    : StringVariant{target.name},
        target.path, std::move(rows));
  });
}

void ProjectWorkspaceCoordinator::ShowCreateEntryDialog(DrawerFileTarget parent,
                                                        bool directory) const {
  dialogs_.Show([coordinator = *this, parent = std::move(parent),
                 directory](DialogContext dialog) {
    return NameDialog(
        dialog,
        directory ? StringVariant{app::strings::workspace_new_folder}
                  : StringVariant{app::strings::workspace_new_file},
        parent.path, {}, [coordinator, parent, directory](std::string name) {
          const auto selected = coordinator.state_->selected;
          if (!selected) {
            coordinator.toast_.Show(app::strings::workspace_error_no_project);
            return;
          }
          const auto parent_relative =
              WorkspaceRelativePath(selected->path, parent.path);
          if (!parent_relative) {
            coordinator.toast_.Show(parent_relative.error());
            return;
          }
          const auto relative =
              (std::filesystem::path{*parent_relative} / name).generic_string();
          LaunchMutation(
              coordinator.service_, coordinator.state_, coordinator.drawer_,
              coordinator.tasks_, coordinator.toast_,
              [relative, directory](const auto &service, const auto &project)
                  -> Task<WorkspaceVoidResult> {
                if (directory)
                  co_return co_await service->CreateDirectory(project.id,
                                                              relative);
                co_return co_await service->CreateFile(project.id, relative);
              });
        });
  });
}

void ProjectWorkspaceCoordinator::ShowRenameDialog(
    DrawerFileTarget target) const {
  dialogs_.Show([coordinator = *this,
                 target = std::move(target)](DialogContext dialog) {
    return NameDialog(
        dialog, app::strings::workspace_rename, target.path, target.name,
        [coordinator, target](std::string name) {
          const auto selected = coordinator.state_->selected;
          if (!selected) {
            coordinator.toast_.Show(app::strings::workspace_error_no_project);
            return;
          }
          const auto relative =
              WorkspaceRelativePath(selected->path, target.path);
          if (!relative) {
            coordinator.toast_.Show(relative.error());
            return;
          }
          LaunchMutation(
              coordinator.service_, coordinator.state_, coordinator.drawer_,
              coordinator.tasks_, coordinator.toast_,
              [relative = *relative, name = std::move(name)](
                  const auto &service, const auto &project) mutable {
                return service->Rename(project.id, relative, std::move(name));
              });
        });
  });
}

void ProjectWorkspaceCoordinator::ConfirmDeleteEntry(
    DrawerFileTarget target) const {
  dialogs_.Show([coordinator = *this,
                 target = std::move(target)](DialogContext dialog) mutable {
    return ConfirmationDialog(
        dialog, app::strings::workspace_confirm_delete,
        StringVariant::Format(app::strings::workspace_confirm_delete_message,
                              target.name, target.path),
        [coordinator, target = std::move(target)] {
          const auto selected = coordinator.state_->selected;
          if (!selected) {
            coordinator.toast_.Show(app::strings::workspace_error_no_project);
            return;
          }
          const auto relative =
              WorkspaceRelativePath(selected->path, target.path);
          if (!relative) {
            coordinator.toast_.Show(relative.error());
            return;
          }
          LaunchMutation(
              coordinator.service_, coordinator.state_, coordinator.drawer_,
              coordinator.tasks_, coordinator.toast_,
              [relative = *relative](const auto &service, const auto &project) {
                return service->Delete(project.id, relative);
              });
        });
  });
}

void ProjectWorkspaceCoordinator::CopyEntry(DrawerFileTarget target) const {
  clipboard_ = WorkspaceClipboard{
      .absolute_path = std::move(target.path),
      .name = std::move(target.name),
  };
  toast_.Show(app::strings::workspace_copied);
}

void ProjectWorkspaceCoordinator::PasteInto(DrawerFileTarget target) const {
  const auto selected = state_->selected;
  const auto clipboard = clipboard_.Get();
  if (!selected || clipboard.Empty())
    return;
  const auto source =
      WorkspaceRelativePath(selected->path, clipboard.absolute_path);
  const auto target_directory =
      WorkspaceRelativePath(selected->path, target.path);
  if (!source || !target_directory) {
    toast_.Show(app::strings::workspace_clipboard_outside);
    return;
  }
  const auto destination =
      (std::filesystem::path{*target_directory} / clipboard.name)
          .generic_string();
  LaunchMutation(service_, state_, drawer_, tasks_, toast_,
                 [source = *source, destination](const auto &service,
                                                 const auto &project) {
                   return service->Copy(project.id, source, destination);
                 });
}

} // namespace linecode::presentation
