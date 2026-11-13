package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Outlined text field with a small upper label, matching the tool/settings forms. */
public final class FormField extends LinearLayout {

    private final EditText input;

    /**
     * Creates a single-line field.
     *
     * @param context hosting context
     * @param theme binder used to register restyle callbacks; never null
     * @param label resource id of the caption rendered above the input
     * @param value initial text; null renders as empty
     * @param hint placeholder shown while the input is empty
     * @param secure true to mask the text as a password
     */
    public FormField(Context context, ThemeBinder theme, int label, String value,
                     CharSequence hint, boolean secure) {
        this(context, theme, label, value, hint, secure, false);
    }

    /**
     * Creates a field with full control over line behaviour.
     *
     * @param context hosting context
     * @param theme binder used to register restyle callbacks; never null
     * @param label resource id of the caption rendered above the input
     * @param value initial text; null renders as empty
     * @param hint placeholder shown while the input is empty
     * @param secure true to mask the text as a password; ignored when {@code multiline} is true
     * @param multiline true for a top-aligned, suggestion-free editor with a 120dp minimum height
     */
    public FormField(Context context, ThemeBinder theme, int label, String value,
                     CharSequence hint, boolean secure, boolean multiline) {
        super(context);
        setOrientation(VERTICAL);
        Tokens tokens = theme.current();
        addView(Ui.medium(context, context.getString(label), Tokens.FONT_SM, tokens.textSecondary),
                new LayoutParams(-1, -2));
        input = new EditText(context);
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setHintTextColor(tokens.textTertiary);
        input.setTextColor(tokens.text);
        input.setTextSize(Tokens.FONT_MD);
        input.setSingleLine(!multiline);
        input.setMinHeight(Ui.dp(context, multiline ? 120 : 48));
        if (multiline) {
            input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        input.setIncludeFontPadding(false);
        input.setBackground(Ui.roundedStroke(context, Color.TRANSPARENT, 12, tokens.border));
        input.setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12));
        input.setInputType(secure
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, 8);
        addView(input, params);
        theme.bind(t -> {
            input.setTextColor(t.text);
            input.setHintTextColor(t.textTertiary);
            input.setBackground(Ui.roundedStroke(context, Color.TRANSPARENT, 12, t.border));
        });
    }

    /** Returns the underlying editor for listeners and selection control; never null. */
    public EditText input() {
        return input;
    }

    /** Returns the trimmed current text, or an empty string when the editor is empty. */
    public String value() {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
