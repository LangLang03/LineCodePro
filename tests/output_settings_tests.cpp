#include "gtest_support.h"
#include <string_view>

#include "application/output_settings.h"

TEST(output_settings_tests, LegacySuite) {
  using namespace linecode::application;

  static_assert(ParseBrowserMode("external") == BrowserMode::external);
  static_assert(ParseBrowserMode("builtin") == BrowserMode::builtin);
  static_assert(ParseBrowserMode("unknown") == BrowserMode::builtin);
  static_assert(SerializeBrowserMode(BrowserMode::builtin) == "builtin");
  static_assert(SerializeBrowserMode(BrowserMode::external) == "external");

  OutputSettingsState state;
  state = ApplyOutputSettingsChange(
      state, OutputBooleanChange{OutputBooleanSetting::code_wrap, true});
  EXPECT_EXPRESSION(state.code_wrap_enabled);
  EXPECT_EXPRESSION(!state.process_auto_expand_enabled);

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

  EXPECT_EXPRESSION(state.process_auto_expand_enabled);
  EXPECT_EXPRESSION(state.browser_javascript_enabled);
  EXPECT_EXPRESSION(state.allow_any_http);
  EXPECT_EXPRESSION(state.bypass_path_protection);
  EXPECT_EXPRESSION(state.browser_mode == BrowserMode::external);

  EXPECT_EXPRESSION(output_setting_keys::code_wrap == "@lineai_code_wrap");
  EXPECT_EXPRESSION(output_setting_keys::browser_mode == "@lineai_browser_mode");
  EXPECT_EXPRESSION(output_setting_keys::browser_javascript ==
         "@lineai_browser_javascript");
  EXPECT_EXPRESSION(output_setting_keys::allow_any_http == "@lineai_allow_any_http");
  EXPECT_EXPRESSION(output_setting_keys::bypass_path_protection ==
         "@lineai_bypass_path_protection");
  EXPECT_EXPRESSION(output_setting_keys::process_auto_expand ==
         "@lineai_process_auto_expand");
}
