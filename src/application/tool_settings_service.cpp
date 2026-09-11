#include "application/tool_settings_service.h"

#include <concepts>
#include <type_traits>

namespace linecode::application {

domain::ToolSettingsState
ApplyToolSettingsChange(domain::ToolSettingsState state,
                        const ToolSettingsChange &change) {
  std::visit(
      [&state](const auto &typed_change) {
        using Change = std::remove_cvref_t<decltype(typed_change)>;
        if constexpr (std::same_as<Change, WebSearchConfigurationChange>) {
          state.web_search = typed_change.value;
        } else {
          const auto &descriptor = ImageModelSettingInfo(typed_change.purpose);
          state.*descriptor.state_member =
              domain::NormalizeToolModelId(typed_change.model_id);
        }
      },
      change);
  return state;
}

} // namespace linecode::application
