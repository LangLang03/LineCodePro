package cn.lineai.feature.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * "Working" indicator shown under a streaming answer.
 *
 * <p>A 3x3 dot matrix in the accent colour with a travelling bright column, plus a monospace
 * label. Matches the legacy indicator's footprint (24dp tall, 16dp matrix, 8dp gap, 13sp).</p>
 */
final class WorkingStatusView extends View {

    private static final int COLUMNS = 3;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float matrixSize;
    private final float dotRadius;
    private final float gap;
    private final String label;
    private ValueAnimator animator;
    private float progress;
    private Tokens tokens;

    WorkingStatusView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        matrixSize = Ui.dp(context, 16);
        dotRadius = Ui.dp(context, 1.35f);
        gap = Ui.dp(context, 8);
        label = context.getString(R.string.message_assistant_working);
        textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * Tokens.FONT_SM);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        applyTokens(tokens);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        dotPaint.setColor(current.accent);
        textPaint.setColor(current.textSecondary);
        invalidate();
    }

    void start() {
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1100L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        progress = 0f;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(Ui.dp(getContext(), 2) + matrixSize + gap
                + textPaint.measureText(label));
        int desiredHeight = Ui.dp(getContext(), 24);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = (getHeight() - matrixSize) / 2f;
        float step = matrixSize / COLUMNS;
        int head = (int) (progress * COLUMNS);
        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row < COLUMNS; row++) {
                int distance = Math.abs(column - head);
                int alpha = distance == 0 ? 255 : (distance == 1 ? 150 : 70);
                dotPaint.setAlpha(alpha);
                float cx = Ui.dp(getContext(), 1) + column * step + step / 2f;
                float cy = top + row * step + step / 2f;
                canvas.drawCircle(cx, cy, dotRadius, dotPaint);
            }
        }
        dotPaint.setAlpha(255);
        float baseline = (getHeight() + textPaint.getTextSize() * 0.72f) / 2f;
        canvas.drawText(label, matrixSize + gap, baseline, textPaint);
    }
}
