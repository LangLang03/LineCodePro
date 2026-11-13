package cn.lineai.feature.settings;

import cn.lineai.core.service.SettingsAction;
import android.content.Context;

import java.util.function.Consumer;

import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.tokens.Tokens;

/** HTTP, browser-script and path-protection switches. */
/** HTTP, browser-script and path-protection switches. */
public final class SecuritySettingsPage extends SettingsPage {

    private final PreferenceRow http;
    private final PreferenceRow javaScript;
    private final PreferenceRow bypass;
    private final PreferenceRow trustExtensions;

    /**
     * Builds the security page; disabling path protection requires an explicit confirmation.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param dispatch consumer receiving settings actions for all switches
     */
    public SecuritySettingsPage(Context context, Tokens tokens, Runnable back,
                                Consumer<SettingsAction> dispatch) {
        super(context, R.string.screen_security_title, tokens, back);
        http = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH, Icons.SHIELD_CHECK,
                R.string.settings_row_security_allow_any_http_title,
                R.string.settings_row_security_allow_any_http_desc);
        http.onToggle(value -> dispatch.accept(new SettingsAction.Change(Field.ALLOW_ANY_HTTP,
                value.toString())));
        section(R.string.screen_security_section_http).row(http, false, 0);

        javaScript = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH, Icons.CODE,
                R.string.screen_output_browser_js_label,
                R.string.screen_output_browser_js_desc);
        javaScript.onToggle(value -> dispatch.accept(new SettingsAction.Change(Field.BROWSER_JAVASCRIPT,
                value.toString())));
        section(R.string.screen_security_section_browser).row(javaScript, false, 0);

        bypass = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH, Icons.SHIELD,
                R.string.settings_row_security_bypass_path_title,
                R.string.settings_row_security_bypass_path_desc);
        bypass.onToggle(value -> {
            if (!value) {
                dispatch.accept(new SettingsAction.Change(Field.BYPASS_PATH_PROTECTION, "false"));
                return;
            }
            bypass.checked(false);
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.settings_row_security_bypass_path_warning_title),
                    context.getString(R.string.settings_row_security_bypass_path_warning_message),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_confirm),
                    confirmed -> {
                        bypass.checked(confirmed);
                        if (confirmed) {
                            dispatch.accept(new SettingsAction.Change(
                                    Field.BYPASS_PATH_PROTECTION, "true"));
                        }
                    });
        });
        section(R.string.screen_security_section_path).row(bypass, false, 0);

        trustExtensions = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH,
                Icons.PACKAGE,
                R.string.settings_row_security_trust_extensions_title,
                R.string.settings_row_security_trust_extensions_desc);
        trustExtensions.onToggle(value -> dispatch.accept(new SettingsAction.Change(
                Field.TRUST_EXTENSION_SIGNATURES, value.toString())));
        section(R.string.screen_security_section_extensions).row(trustExtensions, false, 0);
    }

    /**
     * Syncs all switches to the given state; switches stay disabled until preferences load.
     *
     * @param state current settings state from the presenter
     */
    public void render(SettingsState state) {
        http.checked(state.values.allowAnyHttp());
        javaScript.checked(state.values.browserJavaScript());
        bypass.checked(state.values.bypassPathProtection());
        trustExtensions.checked(state.values.trustExtensionSignatures());
        http.setEnabled(state.loaded);
        javaScript.setEnabled(state.loaded);
        bypass.setEnabled(state.loaded);
        trustExtensions.setEnabled(state.loaded);
    }
}
