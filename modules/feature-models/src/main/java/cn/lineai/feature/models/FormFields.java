package cn.lineai.feature.models;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Field builders shared by the model forms (outlined inputs, toggle pills, tinted switches). */
final class FormFields {

    private FormFields() {
    }

    static TextView label(Context context, Tokens tokens, CharSequence text) {
        return Ui.medium(context, text, Tokens.FONT_SM, tokens.textSecondary);
    }

    static LinearLayout.LayoutParams labelParams(Context context, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, top);
        params.bottomMargin = Ui.dp(context, bottom);
        return params;
    }

    static EditText input(Context context, Tokens tokens, String value, CharSequence placeholder,
                          boolean multiline, boolean secure) {
        EditText input = new EditText(context);
        input.setText(value == null ? "" : value);
        input.setHint(placeholder);
        input.setHintTextColor(tokens.textTertiary);
        input.setTextColor(tokens.text);
        input.setTextSize(Tokens.FONT_MD);
        input.setSingleLine(!multiline);
        input.setMinHeight(Ui.dp(context, multiline ? 120 : 48));
        input.setIncludeFontPadding(false);
        input.setBackground(Ui.roundedStroke(context, Color.TRANSPARENT, 12, tokens.border));
        input.setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12));
        input.setInputType(secure
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    static void addToggle(LinearLayout row, Tokens tokens, String label, boolean active,
                          boolean enabled, Runnable onClick) {
        Context context = row.getContext();
        TextView button = Ui.text(context, label, Tokens.FONT_MD,
                active ? tokens.textOnColor : tokens.textSecondary, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.rounded(context, active ? tokens.accent : tokens.surfaceLight, 12));
        button.setAlpha(enabled || active ? 1f : 0.45f);
        if (enabled && onClick != null) button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(context, 46), 1f);
        if (row.getChildCount() > 0) params.leftMargin = Ui.dp(context, 8);
        row.addView(button, params);
    }

    static void tintSwitch(Switch toggle, Tokens tokens) {
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new ColorStateList(states, new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {tokens.accentDim, tokens.surfaceLight}));
    }

    static String value(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }
}
