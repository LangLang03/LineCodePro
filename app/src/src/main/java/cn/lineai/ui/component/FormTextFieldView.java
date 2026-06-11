package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class FormTextFieldView extends LinearLayout {
    private final EditText input;

    public FormTextFieldView(Context context, String label, String value, String placeholder, String hint, boolean multiline, boolean secure) {
        super(context);
        setOrientation(VERTICAL);

        TextView labelView = LineTheme.textMedium(context, label, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        addView(labelView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        input = new EditText(context);
        input.setText(value == null ? "" : value);
        input.setHint(placeholder);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(!multiline);
        input.setMinHeight(LineTheme.dp(context, multiline ? 120 : 44));
        input.setGravity((multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL) | Gravity.START);
        input.setIncludeFontPadding(false);
        input.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, LineTheme.BORDER_LIGHT));
        input.setPadding(LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.SM));
        if (secure) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTypeface(Typeface.DEFAULT);
        } else if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        addView(input, inputParams);

        if (hint != null && hint.length() > 0) {
            TextView hintView = LineTheme.text(context, hint, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            hintView.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = LineTheme.dp(context, LineTheme.XS);
            addView(hintView, hintParams);
        }
    }

    public EditText getInput() {
        return input;
    }
}
