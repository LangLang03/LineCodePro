package cn.lineai.core.model;

/**
 * Immutable colour palette for one of the built-in themes.
 *
 * <p>Every value is a packed ARGB int. Palettes are pure data: they carry no Android types so
 * they can be persisted, diffed and unit-tested on the JVM. The UI layer maps this object onto
 * {@code Tokens} and never reads colours from a static mutable holder.</p>
 */
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

    /** Keys exposed to the custom-theme editor, in display order. */
    public static final String[] EDITABLE_KEYS = {
            KEY_BG, KEY_SURFACE, KEY_SURFACE_ELEVATED, KEY_SURFACE_LIGHT, KEY_INPUT_BG,
            KEY_TEXT, KEY_TEXT_SECONDARY, KEY_TEXT_TERTIARY, KEY_TEXT_ON_COLOR,
            KEY_ACCENT, KEY_USER_BUBBLE, KEY_AI_BUBBLE,
            KEY_BORDER, KEY_BORDER_LIGHT, KEY_CODE_BG, KEY_CODE_BORDER,
            KEY_DANGER, KEY_WARNING, KEY_SUCCESS
    };

    public final String mode;
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
    public final int dangerMuted;
    public final int dangerMuted2;
    public final int warning;
    public final int success;
    public final int processing;
    public final int processingMuted;
    public final int overlay;
    public final int codeBg;
    public final int codeBorder;
    public final int diffAddBg;
    public final int diffDelBg;
    public final int diffAddText;
    public final int diffDelText;

    private ThemePalette(Builder b) {
        this.mode = b.mode;
        this.bg = b.bg;
        this.surface = b.surface;
        this.surfaceElevated = b.surfaceElevated;
        this.surfaceLight = b.surfaceLight;
        this.accent = b.accent;
        this.accentDim = b.accentDim;
        this.accentMuted = b.accentMuted;
        this.accentMuted2 = b.accentMuted2;
        this.text = b.text;
        this.textSecondary = b.textSecondary;
        this.textTertiary = b.textTertiary;
        this.textOnColor = b.textOnColor;
        this.border = b.border;
        this.borderLight = b.borderLight;
        this.inputBg = b.inputBg;
        this.userBubble = b.userBubble;
        this.aiBubble = b.aiBubble;
        this.danger = b.danger;
        this.dangerMuted = b.dangerMuted;
        this.dangerMuted2 = b.dangerMuted2;
        this.warning = b.warning;
        this.success = b.success;
        this.processing = b.processing;
        this.processingMuted = b.processingMuted;
        this.overlay = b.overlay;
        this.codeBg = b.codeBg;
        this.codeBorder = b.codeBorder;
        this.diffAddBg = b.diffAddBg;
        this.diffDelBg = b.diffDelBg;
        this.diffAddText = b.diffAddText;
        this.diffDelText = b.diffDelText;
    }

    public static ThemePalette forMode(String mode) {
        String normalized = normalizeMode(mode);
        switch (normalized) {
            case MODE_LIGHT:
                return light();
            case MODE_COFFEE:
                return coffee();
            case MODE_VSCODE:
                return vscode();
            case MODE_GITHUB_DARK:
                return githubDark();
            case MODE_GRUVBOX:
                return gruvbox();
            case MODE_HIGH_CONTRAST:
                return highContrast();
            case MODE_CUSTOM:
                return customDefault();
            case MODE_DARK:
            case MODE_SYSTEM:
            default:
                return dark();
        }
    }

    public static String normalizeMode(String mode) {
        String value = mode == null ? "" : mode.trim();
        switch (value) {
            case MODE_SYSTEM:
            case MODE_LIGHT:
            case MODE_DARK:
            case MODE_COFFEE:
            case MODE_VSCODE:
            case MODE_GITHUB_DARK:
            case MODE_GRUVBOX:
            case MODE_HIGH_CONTRAST:
            case MODE_CUSTOM:
                return value;
            default:
                return MODE_SYSTEM;
        }
    }

    public boolean isDark() {
        double luminance = (0.2126 * ((bg >> 16) & 0xFF)
                + 0.7152 * ((bg >> 8) & 0xFF)
                + 0.0722 * (bg & 0xFF)) / 255.0;
        return luminance < 0.5;
    }

    public int colorForKey(String key) {
        if (key == null) {
            return accent;
        }
        switch (key) {
            case KEY_BG: return bg;
            case KEY_SURFACE: return surface;
            case KEY_SURFACE_ELEVATED: return surfaceElevated;
            case KEY_SURFACE_LIGHT: return surfaceLight;
            case KEY_ACCENT: return accent;
            case KEY_ACCENT_DIM: return accentDim;
            case KEY_TEXT: return text;
            case KEY_TEXT_SECONDARY: return textSecondary;
            case KEY_TEXT_TERTIARY: return textTertiary;
            case KEY_TEXT_ON_COLOR: return textOnColor;
            case KEY_BORDER: return border;
            case KEY_BORDER_LIGHT: return borderLight;
            case KEY_INPUT_BG: return inputBg;
            case KEY_USER_BUBBLE: return userBubble;
            case KEY_AI_BUBBLE: return aiBubble;
            case KEY_DANGER: return danger;
            case KEY_WARNING: return warning;
            case KEY_SUCCESS: return success;
            case KEY_PROCESSING: return processing;
            case KEY_CODE_BG: return codeBg;
            case KEY_CODE_BORDER: return codeBorder;
            default: return accent;
        }
    }

    /** Returns a copy with one editable colour replaced; unknown keys are ignored. */
    public ThemePalette with(String key, int color) {
        Builder builder = new Builder(this);
        switch (key == null ? "" : key) {
            case KEY_BG: builder.bg = color; break;
            case KEY_SURFACE: builder.surface = color; break;
            case KEY_SURFACE_ELEVATED: builder.surfaceElevated = color; break;
            case KEY_SURFACE_LIGHT: builder.surfaceLight = color; break;
            case KEY_ACCENT: builder.accent = color; break;
            case KEY_ACCENT_DIM: builder.accentDim = color; break;
            case KEY_TEXT: builder.text = color; break;
            case KEY_TEXT_SECONDARY: builder.textSecondary = color; break;
            case KEY_TEXT_TERTIARY: builder.textTertiary = color; break;
            case KEY_TEXT_ON_COLOR: builder.textOnColor = color; break;
            case KEY_BORDER: builder.border = color; break;
            case KEY_BORDER_LIGHT: builder.borderLight = color; break;
            case KEY_INPUT_BG: builder.inputBg = color; break;
            case KEY_USER_BUBBLE: builder.userBubble = color; break;
            case KEY_AI_BUBBLE: builder.aiBubble = color; break;
            case KEY_DANGER: builder.danger = color; break;
            case KEY_WARNING: builder.warning = color; break;
            case KEY_SUCCESS: builder.success = color; break;
            case KEY_PROCESSING: builder.processing = color; break;
            case KEY_CODE_BG: builder.codeBg = color; break;
            case KEY_CODE_BORDER: builder.codeBorder = color; break;
            default: return this;
        }
        return builder.build();
    }

    /** Applies every valid hex value in {@code colors}; invalid entries keep the current value. */
    public ThemePalette withCustomColors(java.util.Map<String, String> colors) {
        if (colors == null || colors.isEmpty()) {
            return this;
        }
        Builder builder = new Builder(this).named(MODE_CUSTOM);
        for (String key : EDITABLE_KEYS) {
            String value = colors.get(key);
            if (isHexColor(value)) {
                builder.set(key, parseHex(value, colorForKey(key)));
            }
        }
        return builder.build();
    }

    /** The editable colours as {@code #RRGGBB}, in {@link #EDITABLE_KEYS} order. */
    public java.util.Map<String, String> editableHexMap() {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (String key : EDITABLE_KEYS) {
            values.put(key, toHex(colorForKey(key)));
        }
        return values;
    }

    public static boolean isHexColor(String value) {
        return value != null && value.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    }

    public static int parseHex(String value, int fallback) {
        if (!isHexColor(value)) {
            return fallback;
        }
        String hex = value.substring(1);
        long parsed = Long.parseLong(hex, 16);
        return hex.length() == 6 ? (int) parsed | 0xFF000000 : (int) parsed;
    }

    public static String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static int rgba(int red, int green, int blue, float alpha) {
        return (Math.round(alpha * 255f) << 24) | (red << 16) | (green << 8) | blue;
    }

    public static ThemePalette dark() {
        return new Builder(MODE_DARK)
                .colors("#171819", "#242629", "#292D31", "#34393F")
                .accent("#E5E9EE", "#353A40", rgba(229, 233, 238, 0.08f), rgba(229, 233, 238, 0.13f))
                .text("#EDF0F2", "#969DA5", "#969DA5", "#24262A")
                .lines("#383D42", "#30353A", "#242629")
                .bubbles("#242629", "#171819")
                .state("#D7A6AB", "#D8B986", "#ADCEB8", "#969DA5")
                .surfaces(rgba(0, 0, 0, 0.45f), "#1D1F21", "#383D42")
                .derived(rgba(215, 166, 171, 0.10f), rgba(215, 166, 171, 0.18f), rgba(150, 157, 165, 0.10f))
                .diff("#22322A", "#35272B", "#ADCEB8", "#D7A6AB")
                .build();
    }

    public static ThemePalette light() {
        return new Builder(MODE_LIGHT)
                .colors("#FCFCFD", "#F0F1F3", "#EAECF0", "#E1E4E9")
                .accent("#333B46", "#E5E8EC", rgba(51, 59, 70, 0.06f), rgba(51, 59, 70, 0.10f))
                .text("#24262A", "#6C737D", "#6C737D", "#FFFFFF")
                .lines("#E1E4E8", "#EAECF0", "#F0F1F3")
                .bubbles("#F0F1F3", "#FCFCFD")
                .state("#9C5058", "#8D6C32", "#35684D", "#6C737D")
                .surfaces(rgba(22, 26, 32, 0.26f), "#F0F1F3", "#E1E4E8")
                .derived(rgba(156, 80, 88, 0.08f), rgba(156, 80, 88, 0.14f), rgba(108, 115, 125, 0.08f))
                .diff("#EDF4EE", "#F8EDEF", "#35684D", "#9C5058")
                .build();
    }

    public static ThemePalette coffee() {
        return new Builder(MODE_COFFEE)
                .colors("#F4EFE6", "#EEE5D8", "#E7DCCA", "#DED0B9")
                .accent("#D97757", "#F1D4C6", rgba(217, 119, 87, 0.12f), rgba(217, 119, 87, 0.18f))
                .text("#2B2118", "#6C5A49", "#9B8976", "#FFFFFF")
                .lines("#DDD0BF", "#E8DDCF", "#FFFBF3")
                .bubbles("#B86F50", "#EFE4D4")
                .state("#B5473F", "#B7791F", "#6A7F46", "#C27A31")
                .surfaces(rgba(43, 33, 24, 0.38f), rgba(91, 65, 40, 0.07f), rgba(91, 65, 40, 0.14f))
                .derived(rgba(181, 71, 63, 0.12f), rgba(181, 71, 63, 0.18f), rgba(194, 122, 49, 0.12f))
                .diff(rgba(106, 127, 70, 0.14f), rgba(181, 71, 63, 0.12f), "#5E7447", "#A0443E")
                .build();
    }

    public static ThemePalette vscode() {
        return new Builder(MODE_VSCODE)
                .colors("#1E1E1E", "#252526", "#333337", "#414145")
                .accent("#007ACC", "#073A5A", rgba(0, 122, 204, 0.16f), rgba(0, 122, 204, 0.24f))
                .text("#D4D4D4", "#A6A6A6", "#6A6A6A", "#FFFFFF")
                .lines("#3C3C3C", "#454545", "#3C3C3C")
                .bubbles("#094771", "#252526")
                .state("#F48771", "#CCA700", "#89D185", "#DCDCAA")
                .surfaces(rgba(0, 0, 0, 0.58f), "#1E1E1E", "#3C3C3C")
                .derived(rgba(244, 135, 113, 0.14f), rgba(244, 135, 113, 0.22f), rgba(220, 220, 170, 0.12f))
                .diff(rgba(137, 209, 133, 0.12f), rgba(244, 135, 113, 0.12f), "#89D185", "#F48771")
                .build();
    }

    public static ThemePalette githubDark() {
        return new Builder(MODE_GITHUB_DARK)
                .colors("#0D1117", "#010409", "#21262D", "#30363D")
                .accent("#2F81F7", "#0D419D", rgba(47, 129, 247, 0.12f), rgba(47, 129, 247, 0.20f))
                .text("#E6EDF3", "#8B949E", "#6E7681", "#FFFFFF")
                .lines("#30363D", "#21262D", "#0D1117")
                .bubbles("#1F6FEB", "#161B22")
                .state("#F85149", "#D29922", "#3FB950", "#D29922")
                .surfaces(rgba(1, 4, 9, 0.68f), "#0D1117", "#30363D")
                .derived(rgba(248, 81, 73, 0.14f), rgba(248, 81, 73, 0.22f), rgba(210, 153, 34, 0.12f))
                .diff(rgba(46, 160, 67, 0.14f), rgba(248, 81, 73, 0.14f), "#3FB950", "#F85149")
                .build();
    }

    public static ThemePalette gruvbox() {
        return new Builder(MODE_GRUVBOX)
                .colors("#282828", "#1D2021", "#3C3836", "#504945")
                .accent("#FABD2F", "#665C2E", rgba(250, 189, 47, 0.14f), rgba(250, 189, 47, 0.22f))
                .text("#EBDBB2", "#BDAE93", "#928374", "#282828")
                .lines("#504945", "#665C54", "#1D2021")
                .bubbles("#458588", "#32302F")
                .state("#FB4934", "#FE8019", "#B8BB26", "#FABD2F")
                .surfaces(rgba(29, 32, 33, 0.66f), "#1D2021", "#504945")
                .derived(rgba(251, 73, 52, 0.14f), rgba(251, 73, 52, 0.22f), rgba(250, 189, 47, 0.13f))
                .diff(rgba(184, 187, 38, 0.13f), rgba(251, 73, 52, 0.13f), "#B8BB26", "#FB4934")
                .build();
    }

    public static ThemePalette highContrast() {
        return new Builder(MODE_HIGH_CONTRAST)
                .colors("#000000", "#050505", "#222222", "#333333")
                .accent("#64D2FF", "#063B4C", rgba(100, 210, 255, 0.16f), rgba(100, 210, 255, 0.24f))
                .text("#FFFFFF", "#C7C7CC", "#8E8E93", "#000000")
                .lines("#666666", "#3A3A3C", "#111111")
                .bubbles("#004D80", "#101010")
                .state("#FF453A", "#FFD60A", "#30D158", "#FF9F0A")
                .surfaces(rgba(0, 0, 0, 0.75f), "#000000", "#555555")
                .derived(rgba(255, 69, 58, 0.18f), rgba(255, 69, 58, 0.26f), rgba(255, 159, 10, 0.16f))
                .diff(rgba(48, 209, 88, 0.18f), rgba(255, 69, 58, 0.18f), "#30D158", "#FF453A")
                .build();
    }

    public static ThemePalette customDefault() {
        return new Builder(light())
                .surface("#FFFFFF", "#FFFFFF", "#ECECF1", "#FFFFFF")
                .bubbles("#0A84FF", "#F2F2F7")
                .code("#F2F2F7", "#D9D9DE")
                .named(MODE_CUSTOM)
                .build();
    }

    /** Fluent builder used by the built-in palettes and the custom-theme editor. */
    public static final class Builder {
        private String mode;
        private int bg;
        private int surface;
        private int surfaceElevated;
        private int surfaceLight;
        private int accent;
        private int accentDim;
        private int accentMuted;
        private int accentMuted2;
        private int text;
        private int textSecondary;
        private int textTertiary;
        private int textOnColor;
        private int border;
        private int borderLight;
        private int inputBg;
        private int userBubble;
        private int aiBubble;
        private int danger;
        private int dangerMuted;
        private int dangerMuted2;
        private int warning;
        private int success;
        private int processing;
        private int processingMuted;
        private int overlay;
        private int codeBg;
        private int codeBorder;
        private int diffAddBg;
        private int diffDelBg;
        private int diffAddText;
        private int diffDelText;

        public Builder(String mode) {
            this.mode = mode;
        }

        public Builder(ThemePalette base) {
            this.mode = base.mode;
            copyFrom(base);
        }

        private void copyFrom(ThemePalette p) {
            bg = p.bg; surface = p.surface; surfaceElevated = p.surfaceElevated; surfaceLight = p.surfaceLight;
            accent = p.accent; accentDim = p.accentDim; accentMuted = p.accentMuted; accentMuted2 = p.accentMuted2;
            text = p.text; textSecondary = p.textSecondary; textTertiary = p.textTertiary; textOnColor = p.textOnColor;
            border = p.border; borderLight = p.borderLight; inputBg = p.inputBg;
            userBubble = p.userBubble; aiBubble = p.aiBubble;
            danger = p.danger; dangerMuted = p.dangerMuted; dangerMuted2 = p.dangerMuted2;
            warning = p.warning; success = p.success; processing = p.processing; processingMuted = p.processingMuted;
            overlay = p.overlay; codeBg = p.codeBg; codeBorder = p.codeBorder;
            diffAddBg = p.diffAddBg; diffDelBg = p.diffDelBg; diffAddText = p.diffAddText; diffDelText = p.diffDelText;
        }

        public Builder named(String value) {
            mode = value;
            return this;
        }

        /** Sets one editable colour directly (used by custom-theme editing). */
        public Builder set(String key, int color) {
            switch (key == null ? "" : key) {
                case KEY_BG: bg = color; break;
                case KEY_SURFACE: surface = color; break;
                case KEY_SURFACE_ELEVATED: surfaceElevated = color; break;
                case KEY_SURFACE_LIGHT: surfaceLight = color; break;
                case KEY_ACCENT: accent = color; break;
                case KEY_ACCENT_DIM: accentDim = color; break;
                case KEY_TEXT: text = color; break;
                case KEY_TEXT_SECONDARY: textSecondary = color; break;
                case KEY_TEXT_TERTIARY: textTertiary = color; break;
                case KEY_TEXT_ON_COLOR: textOnColor = color; break;
                case KEY_BORDER: border = color; break;
                case KEY_BORDER_LIGHT: borderLight = color; break;
                case KEY_INPUT_BG: inputBg = color; break;
                case KEY_USER_BUBBLE: userBubble = color; break;
                case KEY_AI_BUBBLE: aiBubble = color; break;
                case KEY_DANGER: danger = color; break;
                case KEY_WARNING: warning = color; break;
                case KEY_SUCCESS: success = color; break;
                case KEY_PROCESSING: processing = color; break;
                case KEY_CODE_BG: codeBg = color; break;
                case KEY_CODE_BORDER: codeBorder = color; break;
                default: break;
            }
            return this;
        }

        public Builder colors(String bg, String surface, String elevated, String light) {
            this.bg = parseHex(bg, this.bg);
            this.surface = parseHex(surface, this.surface);
            this.surfaceElevated = parseHex(elevated, this.surfaceElevated);
            this.surfaceLight = parseHex(light, this.surfaceLight);
            return this;
        }

        public Builder surface(String surface, String elevated, String light, String input) {
            this.surface = parseHex(surface, this.surface);
            this.surfaceElevated = parseHex(elevated, this.surfaceElevated);
            this.surfaceLight = parseHex(light, this.surfaceLight);
            this.inputBg = parseHex(input, this.inputBg);
            return this;
        }

        public Builder accent(String accent, String dim, int muted, int muted2) {
            this.accent = parseHex(accent, this.accent);
            this.accentDim = parseHex(dim, this.accentDim);
            this.accentMuted = muted;
            this.accentMuted2 = muted2;
            return this;
        }

        public Builder text(String text, String secondary, String tertiary, String onColor) {
            this.text = parseHex(text, this.text);
            this.textSecondary = parseHex(secondary, this.textSecondary);
            this.textTertiary = parseHex(tertiary, this.textTertiary);
            this.textOnColor = parseHex(onColor, this.textOnColor);
            return this;
        }

        public Builder lines(String border, String borderLight, String inputBg) {
            this.border = parseHex(border, this.border);
            this.borderLight = parseHex(borderLight, this.borderLight);
            this.inputBg = parseHex(inputBg, this.inputBg);
            return this;
        }

        public Builder bubbles(String user, String assistant) {
            this.userBubble = parseHex(user, this.userBubble);
            this.aiBubble = parseHex(assistant, this.aiBubble);
            return this;
        }

        public Builder state(String danger, String warning, String success, String processing) {
            this.danger = parseHex(danger, this.danger);
            this.warning = parseHex(warning, this.warning);
            this.success = parseHex(success, this.success);
            this.processing = parseHex(processing, this.processing);
            return this;
        }

        public Builder surfaces(int overlay, String codeBg, String codeBorder) {
            this.overlay = overlay;
            this.codeBg = parseHex(codeBg, this.codeBg);
            this.codeBorder = parseHex(codeBorder, this.codeBorder);
            return this;
        }

        public Builder surfaces(int overlay, int codeBg, int codeBorder) {
            this.overlay = overlay;
            this.codeBg = codeBg;
            this.codeBorder = codeBorder;
            return this;
        }

        public Builder derived(int dangerMuted, int dangerMuted2, int processingMuted) {
            this.dangerMuted = dangerMuted;
            this.dangerMuted2 = dangerMuted2;
            this.processingMuted = processingMuted;
            return this;
        }

        public Builder code(String codeBg, String codeBorder) {
            this.codeBg = parseHex(codeBg, this.codeBg);
            this.codeBorder = parseHex(codeBorder, this.codeBorder);
            return this;
        }

        public Builder diff(String addBg, String delBg, String addText, String delText) {
            this.diffAddBg = parseHex(addBg, this.diffAddBg);
            this.diffDelBg = parseHex(delBg, this.diffDelBg);
            this.diffAddText = parseHex(addText, this.diffAddText);
            this.diffDelText = parseHex(delText, this.diffDelText);
            return this;
        }

        public Builder diff(int addBg, int delBg, String addText, String delText) {
            this.diffAddBg = addBg;
            this.diffDelBg = delBg;
            this.diffAddText = parseHex(addText, this.diffAddText);
            this.diffDelText = parseHex(delText, this.diffDelText);
            return this;
        }

        public ThemePalette build() {
            return new ThemePalette(this);
        }
    }
}
