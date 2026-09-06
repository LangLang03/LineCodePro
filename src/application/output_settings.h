#pragma once

#include <concepts>
#include <cstdint>
#include <memory>
#include <string_view>
#include <type_traits>
#include <variant>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"

namespace linecode::application {

namespace output_setting_keys {

inline constexpr std::string_view code_wrap = "@lineai_code_wrap";
inline constexpr std::string_view browser_mode = "@lineai_browser_mode";
inline constexpr std::string_view browser_javascript =
    "@lineai_browser_javascript";
inline constexpr std::string_view allow_any_http = "@lineai_allow_any_http";
inline constexpr std::string_view bypass_path_protection =
    "@lineai_bypass_path_protection";
inline constexpr std::string_view process_auto_expand =
    "@lineai_process_auto_expand";

} // namespace output_setting_keys

enum class BrowserMode : std::uint8_t { builtin, external };

[[nodiscard]] constexpr std::string_view
SerializeBrowserMode(BrowserMode mode) noexcept {
  return mode == BrowserMode::external ? "external" : "builtin";
}

[[nodiscard]] constexpr BrowserMode
ParseBrowserMode(std::string_view mode) noexcept {
  return mode == "external" ? BrowserMode::external : BrowserMode::builtin;
}

struct OutputSettingsState final {
  bool code_wrap_enabled{};
  bool process_auto_expand_enabled{};
  BrowserMode browser_mode{BrowserMode::builtin};
  bool browser_javascript_enabled{};
  bool allow_any_http{};
  bool bypass_path_protection{};

  bool operator==(const OutputSettingsState &) const = default;
};

enum class OutputBooleanSetting : std::uint8_t {
  code_wrap,
  process_auto_expand,
  browser_javascript,
  allow_any_http,
  bypass_path_protection,
};

struct OutputBooleanChange final {
  OutputBooleanSetting setting;
  bool value;

  bool operator==(const OutputBooleanChange &) const = default;
};

struct BrowserModeChange final {
  BrowserMode value;

  bool operator==(const BrowserModeChange &) const = default;
};

using OutputSettingsChange =
    std::variant<OutputBooleanChange, BrowserModeChange>;

[[nodiscard]] constexpr OutputSettingsState
ApplyOutputSettingsChange(OutputSettingsState state,
                          const OutputSettingsChange &change) noexcept {
  std::visit(
      [&state](const auto &typed_change) {
        using Change = std::remove_cvref_t<decltype(typed_change)>;
        if constexpr (std::same_as<Change, BrowserModeChange>) {
          state.browser_mode = typed_change.value;
        } else {
          switch (typed_change.setting) {
          case OutputBooleanSetting::code_wrap:
            state.code_wrap_enabled = typed_change.value;
            break;
          case OutputBooleanSetting::process_auto_expand:
            state.process_auto_expand_enabled = typed_change.value;
            break;
          case OutputBooleanSetting::browser_javascript:
            state.browser_javascript_enabled = typed_change.value;
            break;
          case OutputBooleanSetting::allow_any_http:
            state.allow_any_http = typed_change.value;
            break;
          case OutputBooleanSetting::bypass_path_protection:
            state.bypass_path_protection = typed_change.value;
            break;
          }
        }
      },
      change);
  return state;
}

class OutputSettingsService {
public:
  virtual ~OutputSettingsService() = default;

  [[nodiscard]] virtual huxerui::Task<SettingsResult<OutputSettingsState>>
  Load() = 0;
  [[nodiscard]] virtual huxerui::Task<SettingsResult<void>>
  Persist(OutputSettingsChange change) = 0;
};

class PersistedOutputSettings final : public OutputSettingsService {
public:
  explicit PersistedOutputSettings(std::shared_ptr<AsyncSettingsStore> store);

  [[nodiscard]] huxerui::Task<SettingsResult<OutputSettingsState>>
  Load() override;
  [[nodiscard]] huxerui::Task<SettingsResult<void>>
  Persist(OutputSettingsChange change) override;

private:
  std::shared_ptr<AsyncSettingsStore> store_;
};

} // namespace linecode::application
