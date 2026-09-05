package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Switch;
import cn.lineai.R;
import cn.lineai.model.OutputSettings;

public final class SecuritySettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAllowAnyHttpChanged(boolean enabled);

        void onBrowserJavaScriptChanged(boolean enabled);

        void onBypassPathProtectionChanged(boolean enabled);
    }

    private final Listener listener;
    private Switch bypassPathProtectionSwitch;
    private boolean bypassDialogInProgress;

    public SecuritySettingsScreenView(Context context, OutputSettings settings, Listener listener) {
        super(context, context.getString(R.string.screen_security_title), listener::onBack, null);
        this.listener = listener;
        OutputSettings safeSettings = settings == null
                ? new OutputSettings(false, OutputSettings.BROWSER_BUILTIN)
                : settings;
        boolean allowAnyHttp = safeSettings.isAllowAnyHttp();
        boolean browserJavaScriptEnabled = safeSettings.isBrowserJavaScriptEnabled();
        boolean bypassPathProtection = safeSettings.isBypassPathProtection();
        LinearLayout content = getContent();

        SettingsSectionView http = new SettingsSectionView(context, context.getString(R.string.screen_security_section_http));
        http.addRow(new SwitchRowView(context, IconButtonView.SHIELD_CHECK,
                context.getString(R.string.settings_row_security_allow_any_http_title),
                context.getString(R.string.settings_row_security_allow_any_http_desc),
                allowAnyHttp,
                (buttonView, isChecked) -> listener.onAllowAnyHttpChanged(isChecked)), false);
        content.addView(http, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView browser = new SettingsSectionView(context, context.getString(R.string.screen_security_section_browser));
        browser.addRow(new SwitchRowView(context, IconButtonView.CODE,
                context.getString(R.string.screen_output_browser_js_label),
                context.getString(R.string.screen_output_browser_js_desc),
                browserJavaScriptEnabled,
                (buttonView, isChecked) -> listener.onBrowserJavaScriptChanged(isChecked)), false);
        content.addView(browser, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView path = new SettingsSectionView(context, context.getString(R.string.screen_security_section_path));
        SwitchRowView bypassRow = new SwitchRowView(context, IconButtonView.SHIELD,
                context.getString(R.string.settings_row_security_bypass_path_title),
                context.getString(R.string.settings_row_security_bypass_path_desc),
                bypassPathProtection,
                (buttonView, isChecked) -> onBypassPathProtectionToggled(context, isChecked));
        bypassPathProtectionSwitch = findSwitch(bypassRow);
        path.addRow(bypassRow, false);
        content.addView(path, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void onBypassPathProtectionToggled(Context context, boolean isChecked) {
        if (bypassDialogInProgress) {
            return;
        }
        if (!isChecked) {
            listener.onBypassPathProtectionChanged(false);
            return;
        }
        bypassDialogInProgress = true;
        if (bypassPathProtectionSwitch != null) {
            bypassPathProtectionSwitch.setChecked(false);
        }
        new LineAlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settings_row_security_bypass_path_warning_title))
                .setMessage(context.getString(R.string.settings_row_security_bypass_path_warning_message))
                .setNegativeButton(context.getString(R.string.common_cancel), (dialog, which) -> bypassDialogInProgress = false)
                .setPositiveButton(context.getString(R.string.common_confirm), (dialog, which) -> {
                    if (bypassPathProtectionSwitch != null) {
                        bypassPathProtectionSwitch.setChecked(true);
                    }
                    bypassDialogInProgress = false;
                    listener.onBypassPathProtectionChanged(true);
                })
                .setOnCancelListener(dialog -> bypassDialogInProgress = false)
                .show();
    }

    private static Switch findSwitch(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            android.view.View child = row.getChildAt(i);
            if (child instanceof Switch) {
                return (Switch) child;
            }
        }
        return null;
    }
}
