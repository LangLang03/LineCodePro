package cn.lineai.ui.theme;

import android.content.Context;
import android.graphics.text.LineBreaker;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import cn.lineai.model.ThemePalette;

public final class LineTheme {
    public static int BG = Color.parseColor("#000000");
    public static int SURFACE = Color.parseColor("#0A0A0A");
    public static int SURFACE_ELEVATED = Color.parseColor("#141414");
    public static int SURFACE_LIGHT = Color.parseColor("#1C1C1E");
    public static int ACCENT = Color.parseColor("#30D158");
    public static int ACCENT_DIM = Color.parseColor("#1A3A2A");
    public static int ACCENT_MUTED = Color.argb(26, 48, 209, 88);
    public static int ACCENT_MUTED_2 = Color.argb(38, 48, 209, 88);
    public static int USER_BUBBLE = Color.parseColor("#0A84FF");
    public static int AI_BUBBLE = Color.parseColor("#1C1C1E");
    public static int TEXT = Color.parseColor("#FFFFFF");
    public static int TEXT_SECONDARY = Color.parseColor("#8E8E93");
    public static int TEXT_TERTIARY = Color.parseColor("#636366");
    public static int TEXT_ON_COLOR = Color.parseColor("#FFFFFF");
    public static int BORDER = Color.parseColor("#1C1C1E");
    public static int BORDER_LIGHT = Color.parseColor("#2C2C2E");
    public static int INPUT_BG = Color.parseColor("#1C1C1E");
    public static int CODE_BG = Color.parseColor("#151515");
    public static int CODE_BORDER = Color.parseColor("#2C2C2E");
    public static int DANGER = Color.parseColor("#F85149");
    public static int DANGER_MUTED = Color.argb(28, 248, 81, 73);
    public static int DANGER_MUTED_2 = Color.argb(61, 248, 81, 73);
    public static int WARNING = Color.parseColor("#FF9F0A");
    public static int SUCCESS = Color.parseColor("#30D158");
    public static int OVERLAY = Color.argb(165, 0, 0, 0);
    public static int DIFF_ADD_BG = Color.argb(46, 48, 209, 88);
    public static int DIFF_DEL_BG = Color.argb(46, 255, 69, 58);
    public static int DIFF_ADD_TEXT = Color.parseColor("#30D158");
    public static int DIFF_DEL_TEXT = Color.parseColor("#FF453A");

    public static final int XS = 4;
    public static final int SM = 8;
    public static final int MD = 12;
    public static final int LG = 16;
    public static final int XL = 20;
    public static final int XXL = 24;

    public static final int FONT_XS = 11;
    public static final int FONT_SM = 13;
    public static final int FONT_MD = 15;
    public static final int FONT_LG = 17;
    public static final int FONT_XL = 20;
    public static final int FONT_XXL = 28;

    private LineTheme() {
    }

    public static void apply(ThemePalette palette) {
        if (palette == null) {
            return;
        }
        BG = palette.bg;
        SURFACE = palette.surface;
        SURFACE_ELEVATED = palette.surfaceElevated;
        SURFACE_LIGHT = palette.surfaceLight;
        ACCENT = palette.accent;
        ACCENT_DIM = palette.accentDim;
        ACCENT_MUTED = palette.accentMuted;
        ACCENT_MUTED_2 = palette.accentMuted2;
        USER_BUBBLE = palette.userBubble;
        AI_BUBBLE = palette.aiBubble;
        TEXT = palette.text;
        TEXT_SECONDARY = palette.textSecondary;
        TEXT_TERTIARY = palette.textTertiary;
        TEXT_ON_COLOR = palette.textOnColor;
        BORDER = palette.border;
        BORDER_LIGHT = palette.borderLight;
        INPUT_BG = palette.inputBg;
        CODE_BG = palette.codeBg;
        CODE_BORDER = palette.codeBorder;
        DANGER = palette.danger;
        DANGER_MUTED = palette.dangerMuted;
        DANGER_MUTED_2 = palette.dangerMuted2;
        WARNING = palette.warning;
        SUCCESS = palette.success;
        OVERLAY = palette.overlay;
        DIFF_ADD_BG = palette.diffAddBg;
        DIFF_DEL_BG = palette.diffDelBg;
        DIFF_ADD_TEXT = palette.diffAddText;
        DIFF_DEL_TEXT = palette.diffDelText;
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
        drawable.setCornerRadii(new float[] {
                radius, radius,
                radius, radius,
                0, 0,
                0, 0
        });
        return drawable;
    }

    public static GradientDrawable userBubble(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(USER_BUBBLE);
        float large = dp(context, 16);
        float small = dp(context, 4);
        drawable.setCornerRadii(new float[] {
                large, large,
                large, large,
                small, small,
                large, large
        });
        return drawable;
    }

    public static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

    public static TextView text(Context context, String value, int sizeSp, int color, int style) {
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

    public static TextView textMedium(Context context, String value, int sizeSp, int color) {
        TextView textView = text(context, value, sizeSp, color, Typeface.NORMAL);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return textView;
    }
}
