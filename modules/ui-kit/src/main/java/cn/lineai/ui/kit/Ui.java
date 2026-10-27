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

/** Small drawing/measurement helpers shared by every rewritten view. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedStroke(Context context, int color, float radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        return drawable;
    }

    public static GradientDrawable roundedTop(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    public static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

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

    public static TextView medium(Context context, CharSequence value, int sizeSp, int color) {
        TextView textView = text(context, value, sizeSp, color, Typeface.NORMAL);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return textView;
    }

    public static TextView bold(Context context, CharSequence value, int sizeSp, int color) {
        return text(context, value, sizeSp, color, Typeface.BOLD);
    }

    public static Drawable pressable(Context context, Tokens tokens) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, tokens.accentMuted2, 12));
        states.addState(new int[] {},
                roundedStroke(context, tokens.surfaceElevated, 12, tokens.borderLight));
        return states;
    }

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

    /** A one-pixel divider that reads as a hairline on every density. */
    public static View hairline(Context context, Tokens tokens) {
        View view = new View(context);
        view.setBackgroundColor(tokens.borderLight);
        view.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.5f))));
        return view;
    }
}
