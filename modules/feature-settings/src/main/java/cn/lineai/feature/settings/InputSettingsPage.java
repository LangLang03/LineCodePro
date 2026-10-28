package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;
import java.util.function.Consumer;

public final class InputSettingsPage extends SettingsPage {
    private final Consumer<SettingsAction> dispatch;
    private final LinearLayout selector;
    private final TextView value;
    private String behavior = "send";
    private PopupWindow popup;

    public InputSettingsPage(Context context, Tokens tokens, Runnable back, Consumer<SettingsAction> dispatch) {
        super(context, R.string.screen_input_title, tokens, back);
        this.dispatch = dispatch;
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 16, 16, 16);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LayoutParams(0, -2, 1));
        TextView title = Ui.medium(context, context.getString(R.string.screen_input_enter_behavior_label), 16, tokens.text);
        labels.addView(title, new LayoutParams(-1, -2));
        TextView desc = Ui.text(context, context.getString(R.string.screen_input_enter_behavior_desc), 11, tokens.textTertiary, Typeface.NORMAL);
        LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);
        selector = new LinearLayout(context);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setFocusable(true);
        selector.setContentDescription(context.getString(R.string.screen_input_enter_behavior_label));
        Ui.padding(selector, 12, 0, 8, 0);
        selector.setOnClickListener(v -> showOptions());
        value = Ui.medium(context, context.getString(R.string.screen_input_enter_send), 13, tokens.text);
        value.setGravity(Gravity.CENTER_VERTICAL);
        selector.addView(value, new LayoutParams(-2, -1));
        IconButton chevron = new IconButton(context, Icons.CHEVRON_DOWN);
        chevron.setIconSizeDp(18, 13);
        chevron.setClickable(false);
        LayoutParams iconParams = new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18));
        iconParams.leftMargin = Ui.dp(context, 2);
        selector.addView(chevron, iconParams);
        LayoutParams selectParams = new LayoutParams(-2, Ui.dp(context, 34));
        selectParams.leftMargin = Ui.dp(context, 12);
        row.addView(selector, selectParams);
        section(R.string.screen_input_section_input).row(row, false, 0);
        theme.bind(t -> {
            title.setTextColor(t.text);
            desc.setTextColor(t.textTertiary);
            value.setTextColor(t.text);
            selector.setBackground(Ui.roundedStroke(context, t.surfaceLight, 8, t.borderLight));
            chevron.setIconColor(t.textSecondary);
        });
    }

    public void render(SettingsState state) {
        behavior = state.values.get(Field.ENTER_BEHAVIOR);
        value.setText("newline".equals(behavior) ? R.string.screen_input_enter_newline : R.string.screen_input_enter_send);
        selector.setEnabled(state.loaded);
    }

    private void showOptions() {
        if (popup != null && popup.isShowing()) { popup.dismiss(); return; }
        Context context = getContext();
        Tokens t = theme.current();
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(VERTICAL);
        options.setBackground(Ui.roundedStroke(context, t.inputBg, 12, t.borderLight));
        Ui.padding(options, 3, 3, 3, 3);
        option(options, "send", R.string.screen_input_enter_send);
        option(options, "newline", R.string.screen_input_enter_newline);
        int width = Ui.dp(context, 104);
        popup = new PopupWindow(options, width, Ui.dp(context, 82), true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.showAsDropDown(selector, selector.getWidth() - width, Ui.dp(context, 4));
    }

    private void option(LinearLayout options, String key, int label) {
        Context context = getContext();
        Tokens t = theme.current();
        boolean selected = key.equals(behavior);
        TextView choice = Ui.medium(context, context.getString(label), 13, selected ? t.textOnColor : t.textSecondary);
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setSingleLine(true);
        Ui.padding(choice, 12, 0, 12, 0);
        choice.setBackground(Ui.rounded(context, selected ? t.accent : Color.TRANSPARENT, 9));
        choice.setOnClickListener(v -> {
            dispatch.accept(new SettingsAction.Change(Field.ENTER_BEHAVIOR, key));
            popup.dismiss();
        });
        options.addView(choice, new LayoutParams(-1, Ui.dp(context, 38)));
    }

    @Override public void dispose() {
        if (popup != null) popup.dismiss();
    }

    @Override protected void onDetachedFromWindow() {
        dispose();
        super.onDetachedFromWindow();
    }
}
