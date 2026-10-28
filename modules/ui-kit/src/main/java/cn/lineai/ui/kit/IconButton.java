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

    public IconButton setIconType(int type) {
        this.iconType = type;
        setImageResource(Icons.drawable(type));
        applyColor();
        return this;
    }

    public IconButton setIconColor(int color) {
        this.iconColor = color;
        applyColor();
        return this;
    }

    public IconButton setIconSizeDp(int containerDp, int iconDp) {
        requestedIconDp = iconDp;
        int padding = Math.max(0, Math.round((containerDp - iconDp) / 2f));
        setPadding(padding, padding, padding, padding);
        updateIconPadding(getWidth(), getHeight());
        return this;
    }

    public int iconType() {
        return iconType;
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        setFocusable(clickable);
        setImportantForAccessibility(clickable
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateIconPadding(width, height);
    }

    private void updateIconPadding(int width, int height) {
        if (requestedIconDp <= 0 || width <= 0 || height <= 0) {
            return;
        }
        int size = Math.min(Math.min(width, height), Ui.dp(getContext(), requestedIconDp));
        int horizontal = (width - size) / 2;
        int vertical = (height - size) / 2;
        setPadding(horizontal, vertical, width - size - horizontal, height - size - vertical);
    }

    private void applyColor() {
        setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
    }
}
