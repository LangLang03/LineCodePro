package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 终端风格的闪烁块光标，用于标注模型仍在流式输出。
 * 仅在 attached 期间播放动画，detach 时自动停止，避免泄漏。
 */
public final class StreamingCursorView extends View {
    private static final long BLINK_MS = 460L;
    private static final float BLINK_MIN_ALPHA = 0.15f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cursorWidthPx;
    private final float cursorHeightPx;
    private ObjectAnimator blinkAnimator;
    private boolean blinking;

    public StreamingCursorView(Context context) {
        super(context);
        cursorWidthPx = LineTheme.dp(context, 3);
        cursorHeightPx = LineTheme.dp(context, 16);
        paint.setColor(LineTheme.ACCENT);
    }

    public void setCursorColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void startBlinking() {
        if (blinking) {
            return;
        }
        blinking = true;
        if (isAttachedToWindow()) {
            startAnimator();
        }
    }

    public void stopBlinking() {
        blinking = false;
        stopAnimator();
        setAlpha(1f);
    }

    public boolean isBlinking() {
        return blinking;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (blinking) {
            startAnimator();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) cursorWidthPx, (int) cursorHeightPx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = cursorWidthPx / 2f;
        canvas.drawRoundRect(0f, 0f, cursorWidthPx, cursorHeightPx, radius, radius, paint);
    }

    private void startAnimator() {
        if (blinkAnimator != null && blinkAnimator.isStarted()) {
            return;
        }
        blinkAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, BLINK_MIN_ALPHA);
        blinkAnimator.setDuration(BLINK_MS);
        blinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        blinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
        blinkAnimator.start();
    }

    private void stopAnimator() {
        if (blinkAnimator != null) {
            blinkAnimator.cancel();
            blinkAnimator = null;
        }
    }
}
