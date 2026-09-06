#pragma once

#include <functional>
#include <vector>

#include <huxerui/resource.h>
#include <huxerui/view.h>

namespace linecode::presentation {

[[nodiscard]] huxerui::View
LegacySettingsPageHeader(huxerui::StringResource title,
                         std::function<void()> on_back);

[[nodiscard]] huxerui::View
LegacySettingsSection(huxerui::StringResource title,
                      std::vector<huxerui::View> rows);

[[nodiscard]] huxerui::View
LegacySettingsPage(huxerui::StringResource title, std::function<void()> on_back,
                   std::vector<huxerui::View> content);

} // namespace linecode::presentation
