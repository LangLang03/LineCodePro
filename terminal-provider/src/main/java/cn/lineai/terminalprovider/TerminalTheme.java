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

    static final class Palette {
        final int bg;
        final int inputBg;
        final int text;
        final int textSecondary;
        final int accent;
        final int textOnColor;

        private Palette(int bg, int inputBg, int text, int textSecondary,
                        int accent, int textOnColor) {
            this.bg = bg;
            this.inputBg = inputBg;
            this.text = text;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.textOnColor = textOnColor;
        }

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

    static int dp(Context context, int size) {
        return Math.round(context.getResources().getDisplayMetrics().density * size);
    }

    static void padding(View view, int l, int t, int r, int b) {
        Context c = view.getContext();
        view.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    static GradientDrawable rounded(Context context, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radius));
        return d;
    }

    static TextView text(Context context, String value, int size, int color, int style) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setIncludeFontPadding(false);
        return v;
    }

    static TextView textMedium(Context context, String value, int size, int color) {
        TextView v = text(context, value, size, color, Typeface.NORMAL);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
}
