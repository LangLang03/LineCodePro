package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;

public final class PhoneControlScreenView extends ScreenScaffoldView {

    private static final String PERMISSION_SCREENSHOT = "screenshot";
    private static final String PERMISSION_CLICK = "click";
    private static final String PERMISSION_SWIPE = "swipe";
    private static final String PERMISSION_LONG_PRESS = "longPress";
    private static final String PERMISSION_VIEW_HIERARCHY = "viewHierarchy";
    private static final String PERMISSION_VIEW_ACTION = "viewAction";
    private static final String PERMISSION_GLOBAL_ACTION = "globalAction";

    public interface Listener {
        void onBack();

        void onOpenAccessibilitySettings();

        void onPermissionEnabledChanged(String permissionId, boolean enabled);

        boolean isPermissionEnabled(String permissionId);

        void onSetPermissionEnabled(String permissionId, boolean enabled);

        void onAcceptDisclaimer();
    }

    private final boolean accessibilityEnabled;
    private final boolean disclaimerAccepted;
    private final Listener listener;

    public PhoneControlScreenView(Context context, boolean accessibilityEnabled,
                                  boolean disclaimerAccepted, Listener listener) {
        super(context, context.getString(R.string.screen_phone_control_title), listener::onBack, null);
        this.accessibilityEnabled = accessibilityEnabled;
        this.disclaimerAccepted = disclaimerAccepted;
        this.listener = listener;

        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, 0, LineTheme.LG, 0);

        content.addView(buildAccessibilityRow(context));

        if (disclaimerAccepted && accessibilityEnabled) {
            content.addView(new SectionHeaderView(context, context.getString(R.string.screen_phone_control_permission_management)));

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(VERTICAL);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_screenshot,
                    R.string.screen_phone_control_permission_screenshot_desc, IconButtonView.ZAP,
                    PERMISSION_SCREENSHOT, true);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_click,
                    R.string.screen_phone_control_permission_click_desc, IconButtonView.PLAY,
                    PERMISSION_CLICK, true);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_swipe,
                    R.string.screen_phone_control_permission_swipe_desc, IconButtonView.SLIDERS_HORIZONTAL,
                    PERMISSION_SWIPE, true);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_long_press,
                    R.string.screen_phone_control_permission_long_press_desc, IconButtonView.CLOCK_3,
                    PERMISSION_LONG_PRESS, true);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_view_hierarchy,
                    R.string.screen_phone_control_permission_view_hierarchy_desc, IconButtonView.SQUARE_FUNCTION,
                    PERMISSION_VIEW_HIERARCHY, true);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_view_action,
                    R.string.screen_phone_control_permission_view_action_desc, IconButtonView.WRENCH,
                    PERMISSION_VIEW_ACTION, false);
            addPermissionSwitch(context, list, R.string.screen_phone_control_permission_global_action,
                    R.string.screen_phone_control_permission_global_action_desc, IconButtonView.SMARTPHONE,
                    PERMISSION_GLOBAL_ACTION, false);
            content.addView(list, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private View buildAccessibilityRow(Context context) {
        String label = context.getString(R.string.screen_phone_control_accessibility_label);
        String desc;
        if (!disclaimerAccepted) {
            desc = context.getString(R.string.screen_phone_control_disclaimer_title);
        } else if (!accessibilityEnabled) {
            desc = context.getString(R.string.screen_phone_control_accessibility_status_disabled);
        } else {
            desc = context.getString(R.string.screen_phone_control_accessibility_status_enabled);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 68));
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        row.setClickable(true);
        row.setOnClickListener(v -> onAccessibilityRowClicked(context));

        IconButtonView icon = new IconButtonView(context, IconButtonView.SHIELD_CHECK);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(20, 20);
        icon.setClickable(false);
        row.addView(icon, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        LinearLayout textWrap = new LinearLayout(context);
        textWrap.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(textWrap, textParams);

        TextView title = LineTheme.textMedium(context, label, LineTheme.FONT_MD, LineTheme.TEXT);
        textWrap.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (desc.length() > 0) {
            TextView description = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            description.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            textWrap.addView(description, descParams);
        }

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(20, 17);
        chevron.setClickable(false);
        row.addView(chevron, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        return row;
    }

    private void onAccessibilityRowClicked(Context context) {
        if (!disclaimerAccepted) {
            showDisclaimerDialog(context);
        } else if (!accessibilityEnabled) {
            listener.onOpenAccessibilitySettings();
        }
    }

    private void showDisclaimerDialog(Context context) {
        LegalDialog.show(
                context,
                context.getString(R.string.screen_phone_control_disclaimer_title),
                context.getString(R.string.screen_phone_control_disclaimer_text),
                context.getString(R.string.screen_phone_control_disclaimer_agree),
                context.getString(R.string.screen_phone_control_disclaimer_disagree),
                () -> {
                    listener.onAcceptDisclaimer();
                    listener.onOpenAccessibilitySettings();
                },
                null
        );
    }

    private void addPermissionSwitch(Context context, LinearLayout list, int labelRes, int descRes,
                                     int iconType, String permissionId, boolean divider) {
        boolean checked = listener.isPermissionEnabled(permissionId);
        SwitchRowView row = new SwitchRowView(context, iconType, context.getString(labelRes),
                context.getString(descRes), checked, (buttonView, isChecked) -> {
            listener.onSetPermissionEnabled(permissionId, isChecked);
            listener.onPermissionEnabledChanged(permissionId, isChecked);
        });
        list.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (divider) {
            View line = new View(context);
            line.setBackgroundColor(LineTheme.BORDER_LIGHT);
            list.addView(line, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
        }
    }
}
