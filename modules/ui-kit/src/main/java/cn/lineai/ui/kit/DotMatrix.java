package cn.lineai.ui.kit;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The 3x3 accent dot matrix shared by every working indicator.
 *
 * <p>Eight perimeter slots are walked clockwise once every 1.5 s in 90 ms frames; the slot behind
 * the head is dimmed so the motion reads as one dot chasing itself around a square. Keeping the
 * painter here means the live status row and the folded process header can never drift into
 * different spinner shapes again.</p>
 */
public final class DotMatrix {

    /** Duration of one animation frame in milliseconds; shared by every spinner consumer. */
    public static final long FRAME_MS = 90L;
    /** Duration of one full clockwise walk around the matrix in milliseconds. */
    public static final long CYCLE_MS = 1500L;

    private static final int COLUMNS = 3;
    private static final int ROWS = 3;
    private static final int[] FRAME_OFFSETS = {1, 2, 5, 8, 7, 6, 3, 0};
    private static final float INACTIVE_ALPHA = 0.18f;
    private static final float TRAILING_ALPHA = 0.52f;
    /** 1.35dp dots on a 16dp matrix, expressed as a ratio so every size looks the same. */
    private static final float DOT_RATIO = 0.0845f;

    /** Not instantiable; the matrix is drawn through the static {@link #draw} painter. */
    private DotMatrix() {
    }

    /**
     * Draws one frame of the chase animation.
     *
     * @param canvas target canvas; the call is a no-op when null
     * @param paint shared paint whose color and alpha are mutated during the pass and restored to
     *     full alpha afterwards
     * @param progress animation progress in the range 0..1 across {@link #CYCLE_MS}; values outside
     *     the range wrap deterministically through the frame table
     * @param left left edge of the square matrix in canvas coordinates
     * @param top top edge of the square matrix in canvas coordinates
     * @param size side length of the square matrix in pixels; the call is a no-op when zero or
     *     negative
     * @param color accent color of the leading dot; a fully transparent alpha is treated as opaque
     */
    public static void draw(Canvas canvas, Paint paint, float progress, float left, float top,
                            float size, int color) {
        if (canvas == null || paint == null || size <= 0f) {
            return;
        }
        int frame = (int) (progress * CYCLE_MS / FRAME_MS);
        int active = FRAME_OFFSETS[frame % FRAME_OFFSETS.length];
        int trailing = FRAME_OFFSETS[(frame + FRAME_OFFSETS.length - 1) % FRAME_OFFSETS.length];
        float step = size / COLUMNS;
        float radius = size * DOT_RATIO;
        int baseAlpha = (color >>> 24) & 0xff;
        if (baseAlpha == 0) {
            baseAlpha = 0xff;
        }
        paint.setColor(color);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int offset = row * COLUMNS + column;
                float alpha = offset == active ? 1f
                        : offset == trailing ? TRAILING_ALPHA : INACTIVE_ALPHA;
                paint.setAlpha(Math.round(baseAlpha * alpha));
                canvas.drawCircle(left + step * (column + 0.5f),
                        top + step * (row + 0.5f), radius, paint);
            }
        }
        paint.setAlpha(255);
    }
}
