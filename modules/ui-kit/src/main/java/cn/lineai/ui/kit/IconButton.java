package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.widget.ImageButton;

/**
 * Square icon button with an optically centred glyph.
 *
 * <p>The padding trick (instead of a fixed size drawable) keeps the touch target at 40–48dp while
 * the glyph stays at its designed optical size, which is what the legacy screens leaned on.</p>
 */
public final class IconButton extends ImageButton {

    private int iconType;
    private int iconColor = Color.WHITE;
    private int requestedIconDp;

    /**
     * Creates a transparent, clickable 40dp-class square button showing the requested glyph at its
     * natural size until {@link #setIconSizeDp} narrows it.
     *
     * @param context hosting context
     * @param iconType icon id from {@link Icons}; unknown ids fall back to the plus glyph
     */
    public IconButton(Context context, int iconType) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
        setAdjustViewBounds(false);
        setPadding(0, 0, 0, 0);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        setFocusable(true);
        setIconType(iconType);
    }

    /**
     * Swaps the glyph without touching color or size.
     *
     * @param type icon id from {@link Icons}; unknown ids fall back to the plus glyph
     * @return this button, for fluent call chains
     */
    public IconButton setIconType(int type) {
        this.iconType = type;
        setImageResource(Icons.drawable(type));
        applyColor();
        return this;
    }

    /**
     * Tints the glyph via a source-in color filter, so arbitrary vector resources render in any
     * palette color. The value survives later {@link #setIconType} calls.
     *
     * @param color tint applied to the glyph
     * @return this button, for fluent call chains
     */
    public IconButton setIconColor(int color) {
        this.iconColor = color;
        applyColor();
        return this;
    }

    /**
     * Requests an optical glyph size inside a given container size. The padding trick keeps the
     * touch target at the container size; once the view is measured, {@link #updateIconPadding}
     * recomputes padding from the real bounds and clamps the glyph to the requested dp.
     *
     * @param containerDp touch target size in dp
     * @param iconDp desired glyph size in dp
     * @return this button, for fluent call chains
     */
    public IconButton setIconSizeDp(int containerDp, int iconDp) {
        requestedIconDp = iconDp;
        int padding = Math.max(0, Math.round((containerDp - iconDp) / 2f));
        setPadding(padding, padding, padding, padding);
        updateIconPadding(getWidth(), getHeight());
        return this;
    }

    /** Returns the currently bound icon id from {@link Icons}. */
    public int iconType() {
        return iconType;
    }

    /**
     * Keeps focusability and accessibility participation in lockstep with clickability, so a
     * disabled button never remains a focusable dark hole for screen readers.
     */
    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        setFocusable(clickable);
        setImportantForAccessibility(clickable
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Recomputes centring padding once the real bounds are known. */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateIconPadding(width, height);
    }

    /**
     * Centres a glyph of at most {@code requestedIconDp} inside the current bounds; no-op before the
     * first layout or when no optical size was requested.
     */
    private void updateIconPadding(int width, int height) {
        if (requestedIconDp <= 0 || width <= 0 || height <= 0) {
            return;
        }
        int size = Math.min(Math.min(width, height), Ui.dp(getContext(), requestedIconDp));
        int horizontal = (width - size) / 2;
        int vertical = (height - size) / 2;
        setPadding(horizontal, vertical, width - size - horizontal, height - size - vertical);
    }

    /** Reapplies the source-in tint after a color or glyph change. */
    private void applyColor() {
        setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
    }
}
