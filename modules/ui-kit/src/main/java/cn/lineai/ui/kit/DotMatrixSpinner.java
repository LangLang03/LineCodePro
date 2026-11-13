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

    /**
     * Creates the spinner. Accessibility is disabled on purpose: progress is announced by the
     * owning status row, not by the decorative view.
     *
     * @param context hosting context
     * @param sizeDp requested square side length in dp
     */
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

    /**
     * Starts the repeating chase animation, or arms it if the view is not yet attached; the
     * animator is created on attachment. Disabled system animations (animator duration scale of 0)
     * keep the view static at progress zero.
     */
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

    /** Stops the animation and resets progress to zero so the next start begins a fresh cycle. */
    public void stop() {
        running = false;
        stopAnimator();
        progress = 0f;
        invalidate();
    }

    /** Squares the view to the requested dp size within the parent's constraints. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Ui.dp(getContext(), sizeDp);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec),
                resolveSize(size, heightMeasureSpec));
    }

    /** Draws the current frame centred inside the measured bounds. */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        DotMatrix.draw(canvas, paint, progress, (getWidth() - size) / 2f,
                (getHeight() - size) / 2f, size, color);
    }

    /** Resumes the animation on attachment when {@link #start} armed it earlier. */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) {
            startAnimator();
        }
    }

    /** Cancels the animator on detachment; a later {@link #start} recreates it. */
    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    /**
     * Creates the repeating linear animator unless one already runs or system animations are
     * disabled; in the disabled case the view simply stays at progress zero.
     */
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

    /** Cancels and discards the animator, safe to call repeatedly. */
    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
