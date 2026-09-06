#include <cassert>
#include <string_view>

#include "application/output_settings.h"

int main() {
  using namespace linecode::application;

  static_assert(ParseBrowserMode("external") == BrowserMode::external);
  static_assert(ParseBrowserMode("builtin") == BrowserMode::builtin);
  static_assert(ParseBrowserMode("unknown") == BrowserMode::builtin);
  static_assert(SerializeBrowserMode(BrowserMode::builtin) == "builtin");
  static_assert(SerializeBrowserMode(BrowserMode::external) == "external");

  OutputSettingsState state;
  state = ApplyOutputSettingsChange(
      state, OutputBooleanChange{OutputBooleanSetting::code_wrap, true});
  assert(state.code_wrap_enabled);
  assert(!state.process_auto_expand_enabled);

  state = ApplyOutputSettingsChange(
      state,
      OutputBooleanChange{OutputBooleanSetting::process_auto_expand, true});
  state = ApplyOutputSettingsChange(
      state,
      OutputBooleanChange{OutputBooleanSetting::browser_javascript, true});
  state = ApplyOutputSettingsChange(
      state, OutputBooleanChange{OutputBooleanSetting::allow_any_http, true});
  state = ApplyOutputSettingsChange(
      state,
      OutputBooleanChange{OutputBooleanSetting::bypass_path_protection, true});
  state = ApplyOutputSettingsChange(state,
                                    BrowserModeChange{BrowserMode::external});

  assert(state.process_auto_expand_enabled);
  assert(state.browser_javascript_enabled);
  assert(state.allow_any_http);
  assert(state.bypass_path_protection);
  assert(state.browser_mode == BrowserMode::external);

  assert(output_setting_keys::code_wrap == "@lineai_code_wrap");
  assert(output_setting_keys::browser_mode == "@lineai_browser_mode");
  assert(output_setting_keys::browser_javascript ==
         "@lineai_browser_javascript");
  assert(output_setting_keys::allow_any_http == "@lineai_allow_any_http");
  assert(output_setting_keys::bypass_path_protection ==
         "@lineai_bypass_path_protection");
  assert(output_setting_keys::process_auto_expand ==
         "@lineai_process_auto_expand");
}
