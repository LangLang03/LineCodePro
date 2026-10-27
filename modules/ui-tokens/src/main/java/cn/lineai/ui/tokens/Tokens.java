package cn.lineai.ui.tokens;

import cn.lineai.core.model.ThemePalette;

/**
 * Immutable design tokens: colours, spacing, radii and type sizes.
 *
 * <p>Unlike the legacy static colour holder, a token snapshot is passed down explicitly. A theme
 * change publishes a new snapshot; views restyle in place and never recreate themselves, so the
 * back stack, scroll position and drafts survive.</p>
 */
public final class Tokens {

    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 20;
    public static final int SPACE_XXL = 24;

    public static final int FONT_XS = 11;
    public static final int FONT_SM = 13;
    public static final int FONT_MD = 16;
    public static final int FONT_LG = 17;
    public static final int FONT_XL = 20;
    public static final int FONT_TITLE = 22;
    public static final int FONT_XXL = 26;

    public static final int RADIUS_SM = 12;
    public static final int RADIUS_MD = 16;
    public static final int RADIUS_BUBBLE = 18;
    public static final int RADIUS_PILL = 22;

    public final ThemePalette palette;

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

    private Tokens(ThemePalette p) {
        this.palette = p;
        this.bg = p.bg;
        this.surface = p.surface;
        this.surfaceElevated = p.surfaceElevated;
        this.surfaceLight = p.surfaceLight;
        this.accent = p.accent;
        this.accentDim = p.accentDim;
        this.accentMuted = p.accentMuted;
        this.accentMuted2 = p.accentMuted2;
        this.text = p.text;
        this.textSecondary = p.textSecondary;
        this.textTertiary = p.textTertiary;
        this.textOnColor = p.textOnColor;
        this.border = p.border;
        this.borderLight = p.borderLight;
        this.inputBg = p.inputBg;
        this.userBubble = p.userBubble;
        this.aiBubble = p.aiBubble;
        this.danger = p.danger;
        this.dangerMuted = p.dangerMuted;
        this.dangerMuted2 = p.dangerMuted2;
        this.warning = p.warning;
        this.success = p.success;
        this.processing = p.processing;
        this.processingMuted = p.processingMuted;
        this.overlay = p.overlay;
        this.codeBg = p.codeBg;
        this.codeBorder = p.codeBorder;
        this.diffAddBg = p.diffAddBg;
        this.diffDelBg = p.diffDelBg;
        this.diffAddText = p.diffAddText;
        this.diffDelText = p.diffDelText;
    }

    public static Tokens of(ThemePalette palette) {
        return new Tokens(palette == null ? ThemePalette.dark() : palette);
    }

    public boolean isDark() {
        return palette.isDark();
    }

    /** Colour for one semantic key; unknown keys fall back to the accent colour. */
    public int color(String key) {
        return palette.colorForKey(key);
    }
}
