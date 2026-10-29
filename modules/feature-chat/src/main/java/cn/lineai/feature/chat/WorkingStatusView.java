package cn.lineai.feature.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Compact CLI-style status row shown while a live answer is still being written.
 *
 * <p>A 3x3 accent dot matrix walks the perimeter while a narrow highlight sweeps the 13sp
 * monospace label. Ported from the legacy {@code WorkingStatusView}: 1.5 s cycle, 90 ms frames,
 * 16dp matrix, 8dp gap, 24dp row height. The animator only runs while a working row is attached,
 * so recycled rows never keep it alive.</p>
 */
final class WorkingStatusView extends View {

    private static final int DOT_COLUMNS = 3;
    private static final int DOT_ROWS = 3;
    private static final int[] FRAME_OFFSETS = {1, 2, 5, 8, 7, 6, 3, 0};
    private static final long FRAME_MS = 90L;
    private static final long SHIMMER_MS = 1500L;
    private static final float INACTIVE_DOT_ALPHA = 0.18f;
    private static final float TRAILING_DOT_ALPHA = 0.52f;
    private static final float SHIMMER_WIDTH_DP = 72f;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float matrixSize;
    private final float dotRadius;
    private final float dotStep;
    private final float summaryGap;
    private final float horizontalPadding;
    private final float shimmerWidth;
    private final float minimumHeight;
    private final String workingLabel;
    private final String thinkingLabel;

    private Tokens tokens;
    private ValueAnimator animator;
    private boolean working;
    private boolean thinking;
    private float progress;
    private float textStart;
    private float textBaseline;
    private String fitted = "";

    WorkingStatusView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        matrixSize = dp(16);
        dotRadius = dp(1.35f);
        dotStep = matrixSize / DOT_COLUMNS;
        summaryGap = dp(8);
        horizontalPadding = dp(1);
        shimmerWidth = dp(SHIMMER_WIDTH_DP);
        minimumHeight = dp(24);
        workingLabel = context.getString(R.string.message_assistant_working);
        thinkingLabel = context.getString(R.string.message_assistant_thinking);
        textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity
                * Tokens.FONT_SM);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        applyTokens(tokens);
    }

    void bind(boolean thinking) {
        if (this.thinking == thinking) {
            return;
        }
        this.thinking = thinking;
        setContentDescription(label());
        requestLayout();
        invalidate();
    }

    void start() {
        if (working) {
            return;
        }
        working = true;
        setContentDescription(label());
        if (isAttachedToWindow()) {
            startAnimator();
        }
    }

    void stop() {
        working = false;
        stopAnimator();
        progress = 0f;
        invalidate();
    }

    void applyTokens(Tokens current) {
        tokens = current;
        textPaint.setColor(current.textSecondary);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (working) {
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
        int desiredWidth = Math.round(horizontalPadding * 2f + matrixSize + summaryGap
                + textPaint.measureText(label()));
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int desiredHeight = Math.max(getSuggestedMinimumHeight(), Math.round(minimumHeight));
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
        fitSummary(measuredWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDotMatrix(canvas);
        drawLabel(canvas);
    }

    private void drawDotMatrix(Canvas canvas) {
        int frame = (int) (progress * SHIMMER_MS / FRAME_MS);
        int activeOffset = FRAME_OFFSETS[frame % FRAME_OFFSETS.length];
        int trailingOffset = FRAME_OFFSETS[(frame + FRAME_OFFSETS.length - 1)
                % FRAME_OFFSETS.length];
        float top = (getHeight() - matrixSize) / 2f;
        for (int row = 0; row < DOT_ROWS; row++) {
            for (int column = 0; column < DOT_COLUMNS; column++) {
                int offset = row * DOT_COLUMNS + column;
                float alpha = offset == activeOffset ? 1f
                        : offset == trailingOffset ? TRAILING_DOT_ALPHA : INACTIVE_DOT_ALPHA;
                dotPaint.setColor(tokens.accent);
                dotPaint.setAlpha(Math.round(255f * alpha));
                float x = horizontalPadding + dotStep * (column + 0.5f);
                float y = top + dotStep * (row + 0.5f);
                canvas.drawCircle(x, y, dotRadius, dotPaint);
            }
        }
        dotPaint.setAlpha(255);
    }

    private void drawLabel(Canvas canvas) {
        if (fitted.isEmpty()) {
            return;
        }
        float textWidth = textPaint.measureText(fitted);
        float shimmerCenter = textStart - shimmerWidth
                + progress * (textWidth + shimmerWidth * 2f);
        textPaint.setShader(new LinearGradient(
                shimmerCenter - shimmerWidth, 0f, shimmerCenter + shimmerWidth, 0f,
                new int[] {tokens.textSecondary,
                        highlightColor(tokens.textSecondary), tokens.textSecondary},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawText(fitted, textStart, textBaseline, textPaint);
        textPaint.setShader(null);
    }

    static int highlightColor(int baseColor) {
        return (0xff << 24)
                | (blendChannel((baseColor >> 16) & 0xff) << 16)
                | (blendChannel((baseColor >> 8) & 0xff) << 8)
                | blendChannel(baseColor & 0xff);
    }

    private static int blendChannel(int value) {
        return Math.round(value + (255 - value) * 0.72f);
    }

    private void fitSummary(int measuredWidth) {
        textStart = Math.round(horizontalPadding + matrixSize + summaryGap);
        float available = Math.max(0f, measuredWidth - textStart - horizontalPadding);
        fitted = TextUtils.ellipsize(label(), textPaint, available,
                TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        textBaseline = Math.round((getMeasuredHeight() - metrics.bottom - metrics.top) / 2f);
    }

    private String label() {
        return thinking ? thinkingLabel : workingLabel;
    }

    private void startAnimator() {
        if (animator != null && animator.isStarted()) {
            return;
        }
        if (!animationsEnabled()) {
            progress = 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SHIMMER_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @SuppressWarnings("deprecation")
    private boolean animationsEnabled() {
        return android.provider.Settings.Global.getFloat(getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private float dp(float value) {
        return Ui.dp(getContext(), value);
    }
}
