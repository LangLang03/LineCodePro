#include "presentation/screens/data_settings_screen.h"

#include <functional>
#include <memory>
#include <string>
#include <utility>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/data_archive.h"
#include "application/ports/data_archive.h"
#include "domain/app_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
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

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_data_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View ActionRow(ImageResource icon, StringResource title,
               StringResource description, std::function<void()> action) {
  return Row{
      Stack{Glyph(std::move(icon), 20.0F, colors::accent)}.With(
          Frame{.width = 36.0F, .height = 36.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center),
          Background(colors::accent_muted), CornerRadius(8.0F)),
      Column{
          Text(title).Style(Label(16.0F, FontWeight::Medium)),
          Text(description)
              .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
              .With(Padding(EdgeInsets{.top = 2.0F})),
      }
          .With(Grow()),
      Stack{Glyph(app::images::chevron_right, 17.0F, colors::tertiary)}.With(
          Frame{.width = 20.0F, .height = 20.0F},
          Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
  }
      .OnClick(std::move(action))
      .With(Frame{.min_height = 68.0F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

Task<void> ExportArchive(
    const std::shared_ptr<application::DataArchiveService> &service,
    const std::shared_ptr<FilePicker> &picker, ToastHandle toast,
    std::string failure_prefix) {
  auto prepared = co_await service->PrepareExport();
  if (!prepared) {
    toast.Show(failure_prefix + prepared.error().message);
    co_return;
  }
  const bool saved = co_await picker->SaveFileAsync(
      prepared->file,
      SaveFileOptions{
          .suggested_name = prepared->suggested_name,
          .filter = {.name = "LineCode", .extensions = {"linecode"},
                     .content_types = {"application/zip"}},
      });
  if (saved) {
    toast.Show(application::ExportSuccessMessage(prepared->summary));
  }
}

Task<void> ImportArchive(
    const std::shared_ptr<application::DataArchiveService> &service,
    FileReference source, DataSettingsCallbacks callbacks, ToastHandle toast,
    std::string failure_prefix) {
  if (callbacks.before_import) {
    callbacks.before_import();
  }
  auto imported = co_await service->Import(
      std::move(source), domain::ArchiveImportMode::replace);
  if (!imported) {
    toast.Show(failure_prefix + imported.error().message);
    co_return;
  }
  if (callbacks.after_import) {
    callbacks.after_import();
  }
  toast.Show(application::ImportSuccessMessage(*imported));
}

} // namespace

[[huxerui::composable]] View DataSettingsScreen(
    std::shared_ptr<application::DataArchiveService> service,
    DataSettingsCallbacks callbacks) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto picker = UseService<FilePicker>();
  const auto dialogs = UseDialog();
  const auto toast = UseToast();
  const auto tasks = UseTaskScope();
  const auto export_failure = UseString(app::strings::screen_data_export_failed);
  const auto import_failure = UseString(app::strings::screen_data_import_failed);
  const auto picker_unavailable =
      UseString(app::strings::screen_data_picker_unavailable);

  auto export_action = [service, picker, callbacks, toast, tasks,
                        export_failure, picker_unavailable] {
    if (!service || !picker->CanSaveFiles()) {
      toast.Show(picker_unavailable);
      return;
    }
    if (callbacks.persist_before_export) {
      callbacks.persist_before_export();
    }
    tasks.Launch([service, picker, toast, export_failure] {
      return ExportArchive(service, picker, toast, export_failure);
    });
  };
  auto import_action = [service, picker, callbacks, dialogs, toast, tasks,
                        import_failure, picker_unavailable] {
    if (!service || !picker->CanOpenFiles()) {
      toast.Show(picker_unavailable);
      return;
    }
    tasks.Launch([service, picker, callbacks, dialogs, toast, tasks,
                  import_failure]() -> Task<void> {
      auto selected = co_await picker->OpenFileAsync(
          FilePickerFilter{.name = "LineCode",
                           .extensions = {"linecode", "zip"},
                           .content_types = {"application/zip",
                                             "application/octet-stream"}});
      if (!selected) {
        co_return;
      }
      const std::string source_name = selected->Name().empty()
                                          ? ".linecode 文件"
                                          : selected->Name();
      dialogs.Show(
          app::strings::screen_data_import_confirm_title,
          StringVariant::Format(app::strings::screen_data_import_confirm_message,
                                source_name),
          app::strings::common_confirm, app::strings::common_cancel,
          [service, source = std::move(*selected), callbacks, toast, tasks,
           import_failure]() mutable {
            tasks.Launch([service, source = std::move(source), callbacks, toast,
                          import_failure]() mutable {
              return ImportArchive(service, std::move(source), callbacks, toast,
                                   import_failure);
            });
          });
    });
  };

  View rows = Column{
      ActionRow(app::images::download, app::strings::screen_data_export_all,
                app::strings::screen_data_export_all_desc,
                std::move(export_action)),
      Divider(),
      ActionRow(app::images::upload,
                app::strings::screen_data_import_linecode,
                app::strings::screen_data_import_linecode_desc,
                std::move(import_action)),
  }
                  .With(CrossAlign(CrossAxisAlignment::Stretch),
                        Background(colors::elevated), CornerRadius(12.0F));

  return Column{
      Header(navigation),
      Divider(),
      ScrollView(Column{
                     Text(app::strings::screen_data_section_all)
                         .Style(Label(11.0F, FontWeight::Medium,
                                      colors::tertiary))
                         .With(Padding(EdgeInsets{.top = 20.0F,
                                                  .right = 16.0F,
                                                  .bottom = 12.0F,
                                                  .left = 16.0F})),
                     LegacySettingsCardFrame{std::move(rows)},
                     Stack{}.With(Frame{.height = 100.0F}),
                 }.With(CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
