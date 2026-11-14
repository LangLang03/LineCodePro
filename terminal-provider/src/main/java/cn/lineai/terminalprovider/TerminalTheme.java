package cn.lineai.terminalprovider;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * Companion app tokens, kept API 24 compatible independently of the API 26 chat UI.
 * Colors live in an immutable {@link Palette} instead of static mutable fields.
 */
final class TerminalTheme {

    /**
     * Immutable set of color tokens for one night-mode variant. Instances are created
     * only through {@link #of(boolean)} so a palette can never be mutated after use.
     */
    static final class Palette {
        /** Window background. */
        final int bg;
        /** Background of input fields and secondary surfaces. */
        final int inputBg;
        /** Primary text color. */
        final int text;
        /** Secondary (muted) text color. */
        final int textSecondary;
        /** Accent used for primary buttons and highlights. */
        final int accent;
        /** Text color drawn on top of {@code accent} surfaces. */
        final int textOnColor;

        /** Private constructor; use {@link #of(boolean)}. */
        private Palette(int bg, int inputBg, int text, int textSecondary,
                        int accent, int textOnColor) {
            this.bg = bg;
            this.inputBg = inputBg;
            this.text = text;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.textOnColor = textOnColor;
        }

        /** Returns the palette matching the current UI mode. */
        static Palette of(boolean dark) {
            if (dark) {
                return new Palette(
                        android.graphics.Color.parseColor("#171819"),
                        android.graphics.Color.parseColor("#242629"),
                        android.graphics.Color.parseColor("#EDF0F2"),
                        android.graphics.Color.parseColor("#969DA5"),
                        android.graphics.Color.parseColor("#E5E9EE"),
                        android.graphics.Color.parseColor("#24262A"));
            }
            return new Palette(
                    android.graphics.Color.parseColor("#FCFCFD"),
                    android.graphics.Color.parseColor("#F0F1F3"),
                    android.graphics.Color.parseColor("#24262A"),
                    android.graphics.Color.parseColor("#6C737D"),
                    android.graphics.Color.parseColor("#333B46"),
                    android.graphics.Color.parseColor("#FFFFFF"));
        }
    }

    /** Converts a design-space size to device pixels using the current display density. */
    static int dp(Context context, int size) {
        return Math.round(context.getResources().getDisplayMetrics().density * size);
    }

    /** Sets the view padding in dp on all four sides (left, top, right, bottom). */
    static void padding(View view, int l, int t, int r, int b) {
        Context c = view.getContext();
        view.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    /** Returns a solid, rounded-corner drawable for buttons and cards. */
    static GradientDrawable rounded(Context context, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radius));
        return d;
    }

    /** Builds a plain TextView with the given text, size in sp, color and typeface style. */
    static TextView text(Context context, String value, int size, int color, int style) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Builds a TextView with the medium-weight sans-serif face for titles and buttons. */
    static TextView textMedium(Context context, String value, int size, int color) {
        TextView v = text(context, value, size, color, Typeface.NORMAL);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
}
