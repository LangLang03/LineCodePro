package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.model.InputSettings;
import cn.lineai.ui.theme.LineTheme;

public final class InputSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onEnterKeyBehaviorChanged(String behavior);
    }

    private final Listener listener;
    private final TextView behaviorText;
    private final LinearLayout selector;
    private PopupWindow popupWindow;
    private String enterKeyBehavior;

    public InputSettingsScreenView(Context context, InputSettings settings, Listener listener) {
        super(context, "输入设置", listener::onBack, null);
        this.listener = listener;
        InputSettings safeSettings = settings == null
                ? new InputSettings(InputSettings.ENTER_SEND)
                : settings;
        enterKeyBehavior = safeSettings.getEnterKeyBehavior();

        SettingsSectionView section = new SettingsSectionView(context, "输入");
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(row, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView title = LineTheme.textMedium(context, "回车键逻辑", LineTheme.FONT_MD, LineTheme.TEXT);
        labels.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView desc = LineTheme.text(context, "控制输入框按下 Enter 时发送消息还是插入换行", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        labels.addView(desc, descParams);

        selector = new LinearLayout(context);
        selector.setOrientation(HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setClickable(true);
        selector.setFocusable(true);
        selector.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, LineTheme.BORDER_LIGHT));
        LineTheme.padding(selector, LineTheme.MD, 0, LineTheme.SM, 0);
        selector.setOnClickListener(v -> showBehaviorPopup());

        behaviorText = LineTheme.textMedium(context, behaviorLabel(enterKeyBehavior), LineTheme.FONT_SM, LineTheme.TEXT);
        behaviorText.setGravity(Gravity.CENTER_VERTICAL);
        selector.addView(behaviorText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        chevron.setIconColor(LineTheme.TEXT_SECONDARY);
        chevron.setIconSizeDp(18, 13);
        chevron.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        chevronParams.leftMargin = LineTheme.dp(context, 2);
        selector.addView(chevron, chevronParams);

        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34));
        selectorParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(selector, selectorParams);

        section.addRow(row, false);
        getContent().addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void showBehaviorPopup() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }
        Context context = getContext();
        int popupWidth = LineTheme.dp(context, 104);
        int rowHeight = LineTheme.dp(context, 38);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        content.addView(optionView(context, "发送", InputSettings.ENTER_SEND), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(optionView(context, "换行", InputSettings.ENTER_NEWLINE), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));

        popupWindow = new PopupWindow(content, popupWidth, rowHeight * 2 + LineTheme.dp(context, 6), true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.showAsDropDown(selector, selector.getWidth() - popupWidth, LineTheme.dp(context, 4));
    }

    private TextView optionView(Context context, String label, String behavior) {
        boolean selected = behavior.equals(enterKeyBehavior);
        TextView item = LineTheme.textMedium(context, label, LineTheme.FONT_SM,
                selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setPadding(LineTheme.dp(context, LineTheme.MD), 0, LineTheme.dp(context, LineTheme.MD), 0);
        item.setBackground(LineTheme.rounded(context, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 9));
        item.setClickable(true);
        item.setOnClickListener(v -> {
            setEnterKeyBehavior(behavior);
            if (popupWindow != null) {
                popupWindow.dismiss();
            }
        });
        return item;
    }

    private void setEnterKeyBehavior(String behavior) {
        String normalized = InputSettings.normalizeEnterKeyBehavior(behavior);
        if (normalized.equals(enterKeyBehavior)) {
            return;
        }
        enterKeyBehavior = normalized;
        behaviorText.setText(behaviorLabel(enterKeyBehavior));
        listener.onEnterKeyBehaviorChanged(enterKeyBehavior);
    }

    private String behaviorLabel(String behavior) {
        return InputSettings.ENTER_NEWLINE.equals(behavior) ? "换行" : "发送";
    }
}
