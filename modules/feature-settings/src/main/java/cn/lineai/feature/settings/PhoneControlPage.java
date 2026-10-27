package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Accessibility disclaimer, enable shortcut and per-action switches. */
public final class PhoneControlPage extends SettingsPage implements cn.lineai.ui.kit.Refreshable {

    public interface Listener {
        boolean accessibilityEnabled();

        void onOpenAccessibilitySettings();

        void onAcceptDisclaimer();
    }

    private final PhoneControlPreferences preferences;
    private final Listener listener;
    private final LinearLayout section;

    public PhoneControlPage(Context context, Tokens tokens, Runnable back,
                            PhoneControlPreferences preferences, Listener listener) {
        super(context, R.string.screen_phone_control_title, tokens, back);
        this.preferences = preferences;
        this.listener = listener;
        Ui.padding(content, 16, 0, 16, 0);
        Tokens current = tokens;
        LinearLayout accessRow = new LinearLayout(context);
        accessRow.setGravity(Gravity.CENTER_VERTICAL);
        accessRow.setMinimumHeight(Ui.dp(context, 68));
        Ui.padding(accessRow, 16, 12, 16, 12);
        accessRow.setClickable(true);
        accessRow.setOnClickListener(v -> onAccessibilityClicked());
        IconButton icon = new IconButton(context, Icons.SHIELD_CHECK);
        icon.setIconColor(current.accent);
        icon.setIconSizeDp(20, 20);
        icon.setClickable(false);
        accessRow.addView(icon, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
        accessRow.addView(labels, labelParams);
        labels.addView(Ui.medium(context, context.getString(R.string.screen_phone_control_accessibility_label),
                Tokens.FONT_MD, current.text), new LayoutParams(-1, -2));
        TextView desc = Ui.text(context, accessibilityDescription(),
                Tokens.FONT_XS, current.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(current.textTertiary);
        chevron.setIconSizeDp(20, 17);
        chevron.setClickable(false);
        accessRow.addView(chevron, new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        content.addView(accessRow, new LayoutParams(-1, -2));

        section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        content.addView(section, new LayoutParams(-1, -2));
        rebuildRows();
    }

    private void rebuildRows() {
        section.removeAllViews();
        if (!preferences.disclaimerAccepted() || !listener.accessibilityEnabled()) return;
        Context context = getContext();
        Tokens tokens = theme.current();
        TextView header = Ui.medium(context,
                context.getString(R.string.screen_phone_control_permission_management).toUpperCase(java.util.Locale.ROOT),
                Tokens.FONT_XS, tokens.textTertiary);
        header.setLetterSpacing(0.05f);
        Ui.padding(header, 16, 20, 16, 12);
        theme.bind(t -> header.setTextColor(t.textTertiary));
        section.addView(header, new LayoutParams(-1, -2));
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        section.addView(rows, new LayoutParams(-1, -2));
        addSwitch(rows, context, R.string.screen_phone_control_permission_screenshot,
                R.string.screen_phone_control_permission_screenshot_desc, Icons.ZAP,
                PhoneControlPreferences.SCREENSHOT, true);
        addSwitch(rows, context, R.string.screen_phone_control_permission_click,
                R.string.screen_phone_control_permission_click_desc, Icons.PLAY,
                PhoneControlPreferences.CLICK, true);
        addSwitch(rows, context, R.string.screen_phone_control_permission_swipe,
                R.string.screen_phone_control_permission_swipe_desc, Icons.SLIDERS_HORIZONTAL,
                PhoneControlPreferences.SWIPE, true);
        addSwitch(rows, context, R.string.screen_phone_control_permission_long_press,
                R.string.screen_phone_control_permission_long_press_desc, Icons.CLOCK_3,
                PhoneControlPreferences.LONG_PRESS, true);
        addSwitch(rows, context, R.string.screen_phone_control_permission_view_hierarchy,
                R.string.screen_phone_control_permission_view_hierarchy_desc, Icons.SQUARE_FUNCTION,
                PhoneControlPreferences.VIEW_HIERARCHY, true);
        addSwitch(rows, context, R.string.screen_phone_control_permission_view_action,
                R.string.screen_phone_control_permission_view_action_desc, Icons.WRENCH,
                PhoneControlPreferences.VIEW_ACTION, false);
        addSwitch(rows, context, R.string.screen_phone_control_permission_global_action,
                R.string.screen_phone_control_permission_global_action_desc, Icons.SMARTPHONE,
                PhoneControlPreferences.GLOBAL_ACTION, false);
    }

    private void addSwitch(LinearLayout rows, Context context, int label, int description, int icon,
                           String action, boolean divider) {
        PreferenceRow row = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH, icon,
                label, description);
        row.checked(preferences.enabled(action));
        row.onToggle(value -> preferences.setEnabled(action, value));
        rows.addView(row, new LayoutParams(-1, -2));
        if (divider) {
            View line = new View(context);
            theme.bind(t -> line.setBackgroundColor(t.borderLight));
            rows.addView(line, new LayoutParams(-1, 1));
        }
    }

    private String accessibilityDescription() {
        if (!preferences.disclaimerAccepted()) {
            return getContext().getString(R.string.screen_phone_control_disclaimer_title);
        }
        return getContext().getString(listener.accessibilityEnabled()
                ? R.string.screen_phone_control_accessibility_status_enabled
                : R.string.screen_phone_control_accessibility_status_disabled);
    }

    private void onAccessibilityClicked() {
        if (!preferences.disclaimerAccepted()) {
            Dialogs.confirm(getContext(), theme.current(),
                    getContext().getString(R.string.screen_phone_control_disclaimer_title),
                    getContext().getString(R.string.screen_phone_control_disclaimer_text),
                    getContext().getString(R.string.screen_phone_control_disclaimer_disagree),
                    getContext().getString(R.string.screen_phone_control_disclaimer_agree), agreed -> {
                        if (!agreed) return;
                        listener.onAcceptDisclaimer();
                        listener.onOpenAccessibilitySettings();
                        rebuildRows();
                    });
            return;
        }
        if (!listener.accessibilityEnabled()) {
            listener.onOpenAccessibilitySettings();
        }
    }

    @Override public void onShown() {
        rebuildRows();
    }
}
