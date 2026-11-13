package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import cn.lineai.ui.tokens.Tokens;

/**
 * Drawing, measurement and factory helpers shared by every hand-written view in the kit.
 *
 * <p>All helpers are stateless so any view can call them during layout or theme passes. The text
 * factories bake the typography conventions (tight line spacing, no font padding, simple break
 * strategy) so screens cannot drift into different paragraph metrics, and the drawable factories
 * return fresh instances because a single {@code GradientDrawable} cannot back two views at
 * once.</p>
 */
public final class Ui {

    /** Not instantiable; every helper is static. */
    private Ui() {
    }

    /**
     * Converts a dp value to whole pixels using the context display density.
     *
     * @param context context supplying display metrics
     * @param value length in dp
     * @return rounded pixel value
     */
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Builds a solid rounded rectangle drawable.
     *
     * @param context context used for density conversion
     * @param color fill color
     * @param radiusDp corner radius in dp
     * @return a new drawable, owned exclusively by the caller
     */
    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /**
     * Builds a filled rounded rectangle with a 1px outline.
     *
     * @param context context used for density conversion
     * @param color fill color
     * @param radiusDp corner radius in dp
     * @param strokeColor outline color, drawn at a minimum width of one pixel
     * @return a new drawable, owned exclusively by the caller
     */
    public static GradientDrawable roundedStroke(Context context, int color, float radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        return drawable;
    }

    /**
     * Builds a rounded rectangle whose bottom corners are square, for sheets and headers that must
     * meet the screen edge.
     *
     * @param context context used for density conversion
     * @param color fill color
     * @param radiusDp top corner radius in dp
     * @return a new drawable, owned exclusively by the caller
     */
    public static GradientDrawable roundedTop(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    /**
     * Sets padding in dp in one call, mirroring the XML attribute order.
     *
     * @param view target view
     * @param left left padding in dp
     * @param top top padding in dp
     * @param right right padding in dp
     * @param bottom bottom padding in dp
     */
    public static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

    /**
     * Creates a plain-body text view with the kit typography: no font padding, 2dp line spacing and
     * the simple line-break strategy on API 29 and above.
     *
     * @param context hosting context
     * @param value initial text
     * @param sizeSp text size in sp
     * @param color text color
     * @param style typeface style ({@link Typeface#NORMAL}, {@link Typeface#BOLD}, and so on)
     * @return a new text view, owned exclusively by the caller
     */
    public static TextView text(Context context, CharSequence value, int sizeSp, int color, int style) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sizeSp);
        textView.setIncludeFontPadding(false);
        textView.setLineSpacing(dp(context, 2), 1.0f);
        if (style != Typeface.NORMAL) {
            textView.setTypeface(Typeface.DEFAULT, style);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE);
        }
        return textView;
    }

    /**
     * Creates a medium-weight label, the default weight for row titles and section headings.
     *
     * @param context hosting context
     * @param value initial text
     * @param sizeSp text size in sp
     * @param color text color
     * @return a new text view, owned exclusively by the caller
     */
    public static TextView medium(Context context, CharSequence value, int sizeSp, int color) {
        TextView textView = text(context, value, sizeSp, color, Typeface.NORMAL);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return textView;
    }

    /**
     * Creates a bold text view, used for dialog headings and screen titles.
     *
     * @param context hosting context
     * @param value initial text
     * @param sizeSp text size in sp
     * @param color text color
     * @return a new text view, owned exclusively by the caller
     */
    public static TextView bold(Context context, CharSequence value, int sizeSp, int color) {
        return text(context, value, sizeSp, color, Typeface.BOLD);
    }

    /**
     * Builds the pressed/resting background used by tappable list rows and cards.
     *
     * @param context hosting context
     * @param tokens palette snapshot providing accent and surface colors
     * @return a fresh state-list drawable
     */
    public static Drawable pressable(Context context, Tokens tokens) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, tokens.accentMuted2, 12));
        states.addState(new int[] {},
                roundedStroke(context, tokens.surfaceElevated, 12, tokens.borderLight));
        return states;
    }

    /**
     * Builds the focused/resting background used by text inputs.
     *
     * @param context hosting context
     * @param tokens palette snapshot providing input and border colors
     * @return a fresh state-list drawable
     */
    public static Drawable field(Context context, Tokens tokens) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                roundedStroke(context, tokens.inputBg, 12, tokens.textSecondary));
        states.addState(new int[] {}, rounded(context, tokens.inputBg, 12));
        return states;
    }

    /** Picks readable text for an arbitrary background (same luminance rule as the legacy UI). */
    public static int textOn(int background) {
        double luminance = (0.2126 * Color.red(background)
                + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background)) / 255.0;
        return luminance > 0.55 ? Color.rgb(36, 38, 42) : Color.rgb(237, 240, 242);
    }

    /** False when the system animator duration scale is 0 ("remove animations" accessibility). */
    @SuppressWarnings("deprecation")
    public static boolean animationsEnabled(Context context) {
        return android.provider.Settings.Global.getFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    /** A one-pixel divider that reads as a hairline on every density. */
    public static View hairline(Context context, Tokens tokens) {
        View view = new View(context);
        view.setBackgroundColor(tokens.borderLight);
        view.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f))));
        return view;
    }
}
