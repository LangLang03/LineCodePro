package cn.lineai.model;

import android.graphics.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemePalette {
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_COFFEE = "coffee";
    public static final String MODE_VSCODE = "vscode";
    public static final String MODE_GITHUB_DARK = "githubDark";
    public static final String MODE_GRUVBOX = "gruvbox";
    public static final String MODE_HIGH_CONTRAST = "highContrast";
    public static final String MODE_CUSTOM = "custom";

    public static final String KEY_BG = "bg";
    public static final String KEY_SURFACE = "surface";
    public static final String KEY_SURFACE_ELEVATED = "surfaceElevated";
    public static final String KEY_SURFACE_LIGHT = "surfaceLight";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_ACCENT_DIM = "accentDim";
    public static final String KEY_TEXT = "text";
    public static final String KEY_TEXT_SECONDARY = "textSecondary";
    public static final String KEY_TEXT_TERTIARY = "textTertiary";
    public static final String KEY_TEXT_ON_COLOR = "textOnColor";
    public static final String KEY_BORDER = "border";
    public static final String KEY_BORDER_LIGHT = "borderLight";
    public static final String KEY_INPUT_BG = "inputBg";
    public static final String KEY_USER_BUBBLE = "userBubble";
    public static final String KEY_AI_BUBBLE = "aiBubble";
    public static final String KEY_DANGER = "danger";
    public static final String KEY_WARNING = "warning";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_PROCESSING = "processing";
    public static final String KEY_CODE_BG = "codeBg";
    public static final String KEY_CODE_BORDER = "codeBorder";

    public static final String[] EDITABLE_KEYS = new String[] {
            KEY_BG,
            KEY_SURFACE_ELEVATED,
            KEY_SURFACE_LIGHT,
            KEY_INPUT_BG,
            KEY_TEXT,
            KEY_TEXT_SECONDARY,
            KEY_TEXT_TERTIARY,
            KEY_ACCENT,
            KEY_USER_BUBBLE,
            KEY_AI_BUBBLE,
            KEY_BORDER,
            KEY_CODE_BG,
            KEY_DANGER,
            KEY_WARNING,
            KEY_SUCCESS
    };

    public final int bg;
    public final int surface;
    public final int surfaceElevated;
    public final int surfaceLight;
    public final int accent;
    public final int accentDim;
    public final int accentMuted;
    public final int accentMuted2;
    public final int text;
    public final int textSecondary;
    public final int textTertiary;
    public final int textOnColor;
    public final int border;
    public final int borderLight;
    public final int inputBg;
    public final int userBubble;
    public final int aiBubble;
    public final int danger;
    public final int warning;
    public final int success;
    public final int processing;
    public final int overlay;
    public final int codeBg;
    public final int codeBorder;
    public final int dangerMuted;
    public final int dangerMuted2;
    public final int processingMuted;
    public final int diffAddBg;
    public final int diffDelBg;
    public final int diffAddText;
    public final int diffDelText;

    private ThemePalette(
            int bg,
            int surface,
            int surfaceElevated,
            int surfaceLight,
            int accent,
            int accentDim,
            int accentMuted,
            int accentMuted2,
            int text,
            int textSecondary,
            int textTertiary,
            int textOnColor,
            int border,
            int borderLight,
            int inputBg,
            int userBubble,
            int aiBubble,
            int danger,
            int warning,
            int success,
            int processing,
            int overlay,
            int codeBg,
            int codeBorder,
            int dangerMuted,
            int dangerMuted2,
            int processingMuted,
            int diffAddBg,
            int diffDelBg,
            int diffAddText,
            int diffDelText
    ) {
        this.bg = bg;
        this.surface = surface;
        this.surfaceElevated = surfaceElevated;
        this.surfaceLight = surfaceLight;
        this.accent = accent;
        this.accentDim = accentDim;
        this.accentMuted = accentMuted;
        this.accentMuted2 = accentMuted2;
        this.text = text;
        this.textSecondary = textSecondary;
        this.textTertiary = textTertiary;
        this.textOnColor = textOnColor;
        this.border = border;
        this.borderLight = borderLight;
        this.inputBg = inputBg;
        this.userBubble = userBubble;
        this.aiBubble = aiBubble;
        this.danger = danger;
        this.warning = warning;
        this.success = success;
        this.processing = processing;
        this.overlay = overlay;
        this.codeBg = codeBg;
        this.codeBorder = codeBorder;
        this.dangerMuted = dangerMuted;
        this.dangerMuted2 = dangerMuted2;
        this.processingMuted = processingMuted;
        this.diffAddBg = diffAddBg;
        this.diffDelBg = diffDelBg;
        this.diffAddText = diffAddText;
        this.diffDelText = diffDelText;
    }

    public static ThemePalette forMode(String mode) {
        String normalized = normalizeMode(mode);
        if (MODE_LIGHT.equals(normalized)) return light();
        if (MODE_COFFEE.equals(normalized)) return coffee();
        if (MODE_VSCODE.equals(normalized)) return vscode();
        if (MODE_GITHUB_DARK.equals(normalized)) return githubDark();
        if (MODE_GRUVBOX.equals(normalized)) return gruvbox();
        if (MODE_HIGH_CONTRAST.equals(normalized)) return highContrast();
        if (MODE_CUSTOM.equals(normalized)) return customDefault();
        return dark();
    }

    public static String normalizeMode(String mode) {
        String value = mode == null ? "" : mode.trim();
        if (MODE_SYSTEM.equals(value)
                || MODE_LIGHT.equals(value)
                || MODE_DARK.equals(value)
                || MODE_COFFEE.equals(value)
                || MODE_VSCODE.equals(value)
                || MODE_GITHUB_DARK.equals(value)
                || MODE_GRUVBOX.equals(value)
                || MODE_HIGH_CONTRAST.equals(value)
                || MODE_CUSTOM.equals(value)) {
            return value;
        }
        return MODE_SYSTEM;
    }

    public ThemePalette withCustomColors(Map<String, String> customColors) {
        if (customColors == null || customColors.isEmpty()) {
            return this;
        }
        return new Builder(this).apply(customColors).build();
    }

    public int colorForKey(String key) {
        if (KEY_BG.equals(key)) return bg;
        if (KEY_SURFACE.equals(key)) return surface;
        if (KEY_SURFACE_ELEVATED.equals(key)) return surfaceElevated;
        if (KEY_SURFACE_LIGHT.equals(key)) return surfaceLight;
        if (KEY_ACCENT.equals(key)) return accent;
        if (KEY_ACCENT_DIM.equals(key)) return accentDim;
        if (KEY_TEXT.equals(key)) return text;
        if (KEY_TEXT_SECONDARY.equals(key)) return textSecondary;
        if (KEY_TEXT_TERTIARY.equals(key)) return textTertiary;
        if (KEY_TEXT_ON_COLOR.equals(key)) return textOnColor;
        if (KEY_BORDER.equals(key)) return border;
        if (KEY_BORDER_LIGHT.equals(key)) return borderLight;
        if (KEY_INPUT_BG.equals(key)) return inputBg;
        if (KEY_USER_BUBBLE.equals(key)) return userBubble;
        if (KEY_AI_BUBBLE.equals(key)) return aiBubble;
        if (KEY_DANGER.equals(key)) return danger;
        if (KEY_WARNING.equals(key)) return warning;
        if (KEY_SUCCESS.equals(key)) return success;
        if (KEY_PROCESSING.equals(key)) return processing;
        if (KEY_CODE_BG.equals(key)) return codeBg;
        if (KEY_CODE_BORDER.equals(key)) return codeBorder;
        return accent;
    }

    public Map<String, String> editableHexMap() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String key : EDITABLE_KEYS) {
            values.put(key, toHex(colorForKey(key)));
        }
        return values;
    }

    public static boolean isHexColor(String value) {
        return value != null && value.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    }

    public static String toHex(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    public static int parseHex(String value, int fallback) {
        if (!isHexColor(value)) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int hex(String value) {
        return Color.parseColor(value);
    }

    private static int rgba(int red, int green, int blue, float alpha) {
        return Color.argb(Math.round(alpha * 255f), red, green, blue);
    }

    private static ThemePalette dark() {
        return new ThemePalette(
                hex("#000000"), hex("#0A0A0A"), hex("#141414"), hex("#1C1C1E"),
                hex("#30D158"), hex("#1A3A2A"), rgba(48, 209, 88, 0.10f), rgba(48, 209, 88, 0.15f),
                hex("#FFFFFF"), hex("#8E8E93"), hex("#636366"), hex("#FFFFFF"),
                hex("#1C1C1E"), hex("#2C2C2E"), hex("#1C1C1E"), hex("#0A84FF"), hex("#1C1C1E"),
                hex("#F85149"), hex("#FF9F0A"), hex("#3FB950"), hex("#FF9800"),
                rgba(0, 0, 0, 0.60f), rgba(255, 255, 255, 0.04f), rgba(255, 255, 255, 0.08f),
                rgba(248, 81, 73, 0.15f), rgba(248, 81, 73, 0.20f), rgba(255, 152, 0, 0.10f),
                rgba(46, 160, 67, 0.12f), rgba(248, 81, 73, 0.12f), hex("#3FB950"), hex("#F85149"));
    }

    private static ThemePalette light() {
        return new ThemePalette(
                hex("#F5F5F7"), hex("#FFFFFF"), hex("#FFFFFF"), hex("#E8E8ED"),
                hex("#30D158"), hex("#D1F7D6"), rgba(48, 209, 88, 0.10f), rgba(48, 209, 88, 0.15f),
                hex("#1C1C1E"), hex("#636366"), hex("#AEAEB2"), hex("#FFFFFF"),
                hex("#D1D1D6"), hex("#E5E5EA"), hex("#FFFFFF"), hex("#0A84FF"), hex("#E8E8ED"),
                hex("#FF3B30"), hex("#FF9500"), hex("#28A745"), hex("#FF9500"),
                rgba(0, 0, 0, 0.40f), rgba(0, 0, 0, 0.04f), rgba(0, 0, 0, 0.08f),
                rgba(255, 59, 48, 0.10f), rgba(255, 59, 48, 0.15f), rgba(255, 149, 0, 0.10f),
                rgba(40, 167, 69, 0.12f), rgba(255, 59, 48, 0.12f), hex("#28A745"), hex("#FF3B30"));
    }

    private static ThemePalette coffee() {
        return new ThemePalette(
                hex("#F4EFE6"), hex("#EEE5D8"), hex("#FBF7EF"), hex("#E7DCCA"),
                hex("#D97757"), hex("#F1D4C6"), rgba(217, 119, 87, 0.12f), rgba(217, 119, 87, 0.18f),
                hex("#2B2118"), hex("#6C5A49"), hex("#9B8976"), hex("#FFFFFF"),
                hex("#DDD0BF"), hex("#E8DDCF"), hex("#FFFBF3"), hex("#B86F50"), hex("#EFE4D4"),
                hex("#B5473F"), hex("#B7791F"), hex("#6A7F46"), hex("#C27A31"),
                rgba(43, 33, 24, 0.38f), rgba(91, 65, 40, 0.07f), rgba(91, 65, 40, 0.14f),
                rgba(181, 71, 63, 0.12f), rgba(181, 71, 63, 0.18f), rgba(194, 122, 49, 0.12f),
                rgba(106, 127, 70, 0.14f), rgba(181, 71, 63, 0.12f), hex("#5E7447"), hex("#A0443E"));
    }

    private static ThemePalette vscode() {
        return new ThemePalette(
                hex("#1E1E1E"), hex("#252526"), hex("#2D2D30"), hex("#333333"),
                hex("#007ACC"), hex("#073A5A"), rgba(0, 122, 204, 0.16f), rgba(0, 122, 204, 0.24f),
                hex("#D4D4D4"), hex("#A6A6A6"), hex("#6A6A6A"), hex("#FFFFFF"),
                hex("#3C3C3C"), hex("#454545"), hex("#3C3C3C"), hex("#094771"), hex("#252526"),
                hex("#F48771"), hex("#CCA700"), hex("#89D185"), hex("#DCDCAA"),
                rgba(0, 0, 0, 0.58f), hex("#1E1E1E"), hex("#3C3C3C"),
                rgba(244, 135, 113, 0.14f), rgba(244, 135, 113, 0.22f), rgba(220, 220, 170, 0.12f),
                rgba(137, 209, 133, 0.12f), rgba(244, 135, 113, 0.12f), hex("#89D185"), hex("#F48771"));
    }

    private static ThemePalette githubDark() {
        return new ThemePalette(
                hex("#0D1117"), hex("#010409"), hex("#161B22"), hex("#21262D"),
                hex("#2F81F7"), hex("#0D419D"), rgba(47, 129, 247, 0.12f), rgba(47, 129, 247, 0.20f),
                hex("#E6EDF3"), hex("#8B949E"), hex("#6E7681"), hex("#FFFFFF"),
                hex("#30363D"), hex("#21262D"), hex("#0D1117"), hex("#1F6FEB"), hex("#161B22"),
                hex("#F85149"), hex("#D29922"), hex("#3FB950"), hex("#D29922"),
                rgba(1, 4, 9, 0.68f), hex("#0D1117"), hex("#30363D"),
                rgba(248, 81, 73, 0.14f), rgba(248, 81, 73, 0.22f), rgba(210, 153, 34, 0.12f),
                rgba(46, 160, 67, 0.14f), rgba(248, 81, 73, 0.14f), hex("#3FB950"), hex("#F85149"));
    }

    private static ThemePalette gruvbox() {
        return new ThemePalette(
                hex("#282828"), hex("#1D2021"), hex("#32302F"), hex("#3C3836"),
                hex("#FABD2F"), hex("#665C2E"), rgba(250, 189, 47, 0.14f), rgba(250, 189, 47, 0.22f),
                hex("#EBDBB2"), hex("#BDAE93"), hex("#928374"), hex("#282828"),
                hex("#504945"), hex("#665C54"), hex("#1D2021"), hex("#458588"), hex("#32302F"),
                hex("#FB4934"), hex("#FE8019"), hex("#B8BB26"), hex("#FABD2F"),
                rgba(29, 32, 33, 0.66f), hex("#1D2021"), hex("#504945"),
                rgba(251, 73, 52, 0.14f), rgba(251, 73, 52, 0.22f), rgba(250, 189, 47, 0.13f),
                rgba(184, 187, 38, 0.13f), rgba(251, 73, 52, 0.13f), hex("#B8BB26"), hex("#FB4934"));
    }

    private static ThemePalette highContrast() {
        return new ThemePalette(
                hex("#000000"), hex("#050505"), hex("#101010"), hex("#1A1A1A"),
                hex("#64D2FF"), hex("#063B4C"), rgba(100, 210, 255, 0.16f), rgba(100, 210, 255, 0.24f),
                hex("#FFFFFF"), hex("#C7C7CC"), hex("#8E8E93"), hex("#000000"),
                hex("#666666"), hex("#3A3A3C"), hex("#111111"), hex("#004D80"), hex("#101010"),
                hex("#FF453A"), hex("#FFD60A"), hex("#30D158"), hex("#FF9F0A"),
                rgba(0, 0, 0, 0.75f), hex("#000000"), hex("#555555"),
                rgba(255, 69, 58, 0.18f), rgba(255, 69, 58, 0.26f), rgba(255, 159, 10, 0.16f),
                rgba(48, 209, 88, 0.18f), rgba(255, 69, 58, 0.18f), hex("#30D158"), hex("#FF453A"));
    }

    private static ThemePalette customDefault() {
        return new Builder(light())
                .set(KEY_SURFACE, hex("#FFFFFF"))
                .set(KEY_SURFACE_ELEVATED, hex("#FFFFFF"))
                .set(KEY_SURFACE_LIGHT, hex("#ECECF1"))
                .set(KEY_INPUT_BG, hex("#FFFFFF"))
                .set(KEY_USER_BUBBLE, hex("#0A84FF"))
                .set(KEY_AI_BUBBLE, hex("#F2F2F7"))
                .set(KEY_CODE_BG, hex("#F2F2F7"))
                .set(KEY_CODE_BORDER, hex("#D9D9DE"))
                .build();
    }

    private static final class Builder {
        private final LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        private final ThemePalette base;

        Builder(ThemePalette palette) {
            base = palette;
            values.put(KEY_BG, palette.bg);
            values.put(KEY_SURFACE, palette.surface);
            values.put(KEY_SURFACE_ELEVATED, palette.surfaceElevated);
            values.put(KEY_SURFACE_LIGHT, palette.surfaceLight);
            values.put(KEY_ACCENT, palette.accent);
            values.put(KEY_ACCENT_DIM, palette.accentDim);
            values.put(KEY_TEXT, palette.text);
            values.put(KEY_TEXT_SECONDARY, palette.textSecondary);
            values.put(KEY_TEXT_TERTIARY, palette.textTertiary);
            values.put(KEY_TEXT_ON_COLOR, palette.textOnColor);
            values.put(KEY_BORDER, palette.border);
            values.put(KEY_BORDER_LIGHT, palette.borderLight);
            values.put(KEY_INPUT_BG, palette.inputBg);
            values.put(KEY_USER_BUBBLE, palette.userBubble);
            values.put(KEY_AI_BUBBLE, palette.aiBubble);
            values.put(KEY_DANGER, palette.danger);
            values.put(KEY_WARNING, palette.warning);
            values.put(KEY_SUCCESS, palette.success);
            values.put(KEY_PROCESSING, palette.processing);
            values.put(KEY_CODE_BG, palette.codeBg);
            values.put(KEY_CODE_BORDER, palette.codeBorder);
        }

        Builder apply(Map<String, String> customColors) {
            for (String key : EDITABLE_KEYS) {
                String value = customColors.get(key);
                if (isHexColor(value)) {
                    values.put(key, parseHex(value, values.get(key)));
                }
            }
            return this;
        }

        Builder set(String key, int color) {
            values.put(key, color);
            return this;
        }

        ThemePalette build() {
            return new ThemePalette(
                    values.get(KEY_BG), values.get(KEY_SURFACE), values.get(KEY_SURFACE_ELEVATED), values.get(KEY_SURFACE_LIGHT),
                    values.get(KEY_ACCENT), values.get(KEY_ACCENT_DIM), base.accentMuted, base.accentMuted2,
                    values.get(KEY_TEXT), values.get(KEY_TEXT_SECONDARY), values.get(KEY_TEXT_TERTIARY), values.get(KEY_TEXT_ON_COLOR),
                    values.get(KEY_BORDER), values.get(KEY_BORDER_LIGHT), values.get(KEY_INPUT_BG), values.get(KEY_USER_BUBBLE), values.get(KEY_AI_BUBBLE),
                    values.get(KEY_DANGER), values.get(KEY_WARNING), values.get(KEY_SUCCESS), values.get(KEY_PROCESSING),
                    base.overlay, values.get(KEY_CODE_BG), values.get(KEY_CODE_BORDER),
                    base.dangerMuted, base.dangerMuted2, base.processingMuted, base.diffAddBg, base.diffDelBg, base.diffAddText, base.diffDelText);
        }
    }
}
