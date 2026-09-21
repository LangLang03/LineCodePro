#include "application/output_settings.h"

#include <stdexcept>
#include <string>
#include <utility>

namespace linecode::application {
namespace {

[[nodiscard]] constexpr std::string_view
KeyFor(OutputBooleanSetting setting) noexcept {
  switch (setting) {
  case OutputBooleanSetting::code_wrap:
    return output_setting_keys::code_wrap;
  case OutputBooleanSetting::process_auto_expand:
    return output_setting_keys::process_auto_expand;
  case OutputBooleanSetting::browser_javascript:
    return output_setting_keys::browser_javascript;
  case OutputBooleanSetting::allow_any_http:
    return output_setting_keys::allow_any_http;
  case OutputBooleanSetting::bypass_path_protection:
    return output_setting_keys::bypass_path_protection;
  }
  return output_setting_keys::code_wrap;
}

} // namespace

PersistedOutputSettings::PersistedOutputSettings(
    std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_)
    throw std::invalid_argument("output settings store must not be empty");
}

huxerui::Task<SettingsResult<OutputSettingsState>>
PersistedOutputSettings::Load() {
  auto code_wrap = co_await store_->GetBoolean(
      std::string{output_setting_keys::code_wrap}, false);
  if (!code_wrap)
    co_return std::unexpected(code_wrap.error());

  auto process_auto_expand = co_await store_->GetBoolean(
      std::string{output_setting_keys::process_auto_expand}, false);
  if (!process_auto_expand)
    co_return std::unexpected(process_auto_expand.error());

  auto browser_mode = co_await store_->GetString(
      std::string{output_setting_keys::browser_mode},
      std::string{SerializeBrowserMode(BrowserMode::builtin)});
  if (!browser_mode)
    co_return std::unexpected(browser_mode.error());

  auto browser_javascript = co_await store_->GetBoolean(
      std::string{output_setting_keys::browser_javascript}, false);
  if (!browser_javascript)
    co_return std::unexpected(browser_javascript.error());

  auto allow_any_http = co_await store_->GetBoolean(
      std::string{output_setting_keys::allow_any_http}, false);
  if (!allow_any_http)
    co_return std::unexpected(allow_any_http.error());

  auto bypass_path_protection = co_await store_->GetBoolean(
      std::string{output_setting_keys::bypass_path_protection}, false);
  if (!bypass_path_protection)
    co_return std::unexpected(bypass_path_protection.error());

  co_return OutputSettingsState{
      .code_wrap_enabled = *code_wrap,
      .process_auto_expand_enabled = *process_auto_expand,
      .browser_mode = ParseBrowserMode(*browser_mode),
      .browser_javascript_enabled = *browser_javascript,
      .allow_any_http = *allow_any_http,
      .bypass_path_protection = *bypass_path_protection,
  };
}

huxerui::Task<SettingsResult<void>>
PersistedOutputSettings::Persist(OutputSettingsChange change) {
  if (const auto *boolean = std::get_if<OutputBooleanChange>(&change)) {
    co_return co_await store_->SetBoolean(std::string{KeyFor(boolean->setting)},
                                          boolean->value);
  }
  const auto mode = std::get<BrowserModeChange>(change).value;
  co_return co_await store_->SetString(
      std::string{output_setting_keys::browser_mode},
      std::string{SerializeBrowserMode(mode)});
}

} // namespace linecode::application
