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

/**
 * Field builders shared by the model forms (outlined inputs, toggle pills, tinted switches).
 *
 * <p>Package-private utility; every builder returns a freshly styled view so forms can assemble
 * rows without inheriting mutable drawables or listeners from another row. The visual conventions
 * (48dp inputs, 46dp toggle pills, 12dp corner radius) are fixed here so the model pages cannot
 * drift apart.</p>
 */
final class FormFields {

    /** Not instantiable; every builder is static. */
    private FormFields() {
    }

    /**
     * Creates a small medium-weight field caption.
     *
     * @param context hosting context
     * @param tokens palette snapshot
     * @param text caption text
     * @return a new text view owned by the caller
     */
    static TextView label(Context context, Tokens tokens, CharSequence text) {
        return Ui.medium(context, text, Tokens.FONT_SM, tokens.textSecondary);
    }

    /**
     * Builds match-parent/wrap-content layout params with dp vertical margins.
     *
     * @param context hosting context
     * @param top top margin in dp
     * @param bottom bottom margin in dp
     * @return layout params for a field label
     */
    static LinearLayout.LayoutParams labelParams(Context context, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, top);
        params.bottomMargin = Ui.dp(context, bottom);
        return params;
    }

    /**
     * Creates an outlined input with the shared form metrics; the input type follows the requested
     * mode, where {@code secure} wins over {@code multiline}.
     *
     * @param context hosting context
     * @param tokens palette snapshot used for text, hint and border colors
     * @param value initial text; null renders as empty
     * @param placeholder hint shown while the input is empty
     * @param multiline true for a top-aligned 120dp editor without suggestions
     * @param secure true to mask the text as a password
     * @return a new editor owned by the caller
     */
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

    /**
     * Appends one toggle pill to a row. Disabled pills stay visible but dimmed and inert; the
     * active pill uses the accent fill and on-color text.
     *
     * @param row horizontal row receiving the pill
     * @param tokens palette snapshot
     * @param label pill text
     * @param active true to render the selected (accent) state
     * @param enabled false to dim the pill and drop its click handler
     * @param onClick action run on tap when enabled; a null action also disables interaction
     */
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

    /**
     * Applies the shared thumb and track tint states to a switch.
     *
     * @param toggle switch to restyle in place
     * @param tokens palette snapshot providing accent and surface colors
     */
    static void tintSwitch(Switch toggle, Tokens tokens) {
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new ColorStateList(states, new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {tokens.accentDim, tokens.surfaceLight}));
    }

    /**
     * Reads the trimmed text of an editor, tolerating a null editor or null text.
     *
     * @param input editor to read; may be null
     * @return trimmed text, or an empty string when unavailable
     */
    static String value(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    /**
     * Detaches a view from its current parent so it can be re-inserted elsewhere without the
     * "already has a parent" crash; a no-op when the view is parentless or null.
     *
     * @param view view to detach; may be null
     */
    static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }
}
