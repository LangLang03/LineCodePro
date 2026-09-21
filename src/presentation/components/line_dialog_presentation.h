#pragma once

#include <functional>
#include <optional>
#include <string>

#include <huxerui/presentation.h>
#include <huxerui/resource.h>
#include <huxerui/view.h>

namespace linecode::presentation {

enum class LineDialogActionTone { standard, danger };

struct LineDialogAction final {
  huxerui::StringVariant label;
  std::function<void()> activate;
  LineDialogActionTone tone{LineDialogActionTone::standard};
};

/// Builds the shared LineCode confirmation presentation. Action tone is typed
/// presentation metadata so destructive styling never depends on localized
/// button text.
[[nodiscard]] huxerui::View LineConfirmationDialog(
    huxerui::DialogContext dialog, huxerui::StringVariant title,
    huxerui::StringVariant message, LineDialogAction positive,
    std::optional<LineDialogAction> negative = std::nullopt);

/// Builds the shared single-line input dialog used by project and file
/// operations. The component owns the complete editing value and reports the
/// submitted text only after dismissing the dialog.
[[nodiscard]] [[huxerui::composable]] huxerui::View LineInputDialog(
    huxerui::DialogContext dialog, huxerui::StringVariant title,
    std::optional<huxerui::StringVariant> message,
    huxerui::StringVariant placeholder, std::string initial_value,
    std::function<void(std::string)> submit);

} // namespace linecode::presentation
