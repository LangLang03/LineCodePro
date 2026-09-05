package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.R;

public final class ModelFormHelper {

    private ModelFormHelper() {
    }

    public static TextView label(Context context, String text) {
        return LineTheme.textMedium(context, text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
    }

    public static LinearLayout.LayoutParams labelParams(Context context, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, top);
        params.bottomMargin = LineTheme.dp(context, bottom);
        return params;
    }

    public static EditText input(Context context, String value, String placeholder, boolean multiline, boolean secure) {
        EditText input = new EditText(context);
        input.setText(value == null ? "" : value);
        input.setHint(placeholder);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(!multiline);
        input.setMinHeight(LineTheme.dp(context, multiline ? 120 : 48));
        input.setIncludeFontPadding(false);
        input.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        input.setPadding(LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.MD));
        input.setInputType(secure
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    public static void addToggle(LinearLayout row, String label, boolean active, boolean enabled, Runnable onClick) {
        Context context = row.getContext();
        TextView button = LineTheme.text(context, label, LineTheme.FONT_MD, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.rounded(context, active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
        button.setAlpha(enabled || active ? 1f : 0.45f);
        if (enabled && onClick != null) {
            button.setOnClickListener(v -> onClick.run());
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 46), 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = LineTheme.dp(context, LineTheme.SM);
        }
        row.addView(button, params);
    }

    public static void tintSwitch(Switch toggle) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT, LineTheme.TEXT_TERTIARY}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT_DIM, LineTheme.SURFACE_LIGHT}));
    }

    public static String value(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    public static void detachFromParent(android.view.View view) {
        if (view == null || view.getParent() == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }
}
