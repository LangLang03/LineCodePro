package cn.lineai.ui.kit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Bare dot-matrix spinner used by the process header and running tool cards.
 *
 * <p>Same 16dp matrix, 1.5 s cycle and 90 ms frames as {@code WorkingStatusView}, so a turn that
 * starts streaming never swaps the square dot walk for a circular progress bar when it enters the
 * folded process. The animator only runs while the view is attached and started.</p>
 */
public final class DotMatrixSpinner extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float sizeDp;
    private ValueAnimator animator;
    private int color = 0xffffffff;
    private boolean running;
    private float progress;

    public DotMatrixSpinner(Context context, float sizeDp) {
        super(context);
        this.sizeDp = sizeDp;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Recolors the matrix without restarting the animation (theme changes). */
    public void setColor(int value) {
        if (color == value) {
            return;
        }
        color = value;
        invalidate();
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        if (isAttachedToWindow()) {
            startAnimator();
        }
        invalidate();
    }

    public void stop() {
        running = false;
        stopAnimator();
        progress = 0f;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Ui.dp(getContext(), sizeDp);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec),
                resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        DotMatrix.draw(canvas, paint, progress, (getWidth() - size) / 2f,
                (getHeight() - size) / 2f, size, color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) {
            startAnimator();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    private void startAnimator() {
        if (animator != null && animator.isStarted()) {
            return;
        }
        if (!Ui.animationsEnabled(getContext())) {
            progress = 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DotMatrix.CYCLE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
