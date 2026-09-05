package cn.lineai.ui.component;

import cn.lineai.ui.theme.LineTheme;

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

/**
 * Compact CLI-style status row shown while the model is working.
 *
 * <p>The dot matrix rotates continuously and a narrow highlight sweeps across the summary. Animation is
 * active only while requested and attached, so recycled chat rows cannot retain animator references.
 */
public final class WorkingStatusView extends View {
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
    private final float matrixSizePx;
    private final float dotRadiusPx;
    private final float dotStepPx;
    private final float summaryGapPx;
    private final float horizontalPaddingPx;
    private final float shimmerWidthPx;
    private final float minimumHeightPx;
    private final String workingLabel;
    private final String thinkingLabel;

    private ValueAnimator animator;
    private boolean working;
    private boolean thinking;
    private float animationProgress;
    private int textStartPx;
    private int textBaselinePx;
    private String fittedSummary = "";

    public WorkingStatusView(Context context) {
        this(
                context,
                context.getString(cn.lineai.R.string.message_assistant_working),
                context.getString(cn.lineai.R.string.message_assistant_thinking)
        );
    }

    WorkingStatusView(Context context, String workingLabel, String thinkingLabel) {
        super(context);
        matrixSizePx = LineTheme.dp(context, 16);
        dotRadiusPx = LineTheme.dp(context, 1.35f);
        dotStepPx = matrixSizePx / DOT_COLUMNS;
        summaryGapPx = LineTheme.dp(context, 8);
        horizontalPaddingPx = LineTheme.dp(context, 1);
        shimmerWidthPx = LineTheme.dp(context, SHIMMER_WIDTH_DP);
        minimumHeightPx = LineTheme.dp(context, 24);
        this.workingLabel = workingLabel == null ? "" : workingLabel;
        this.thinkingLabel = thinkingLabel == null ? "" : thinkingLabel;

        dotPaint.setColor(LineTheme.ACCENT);
        textPaint.setColor(LineTheme.TEXT_SECONDARY);
        textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * LineTheme.FONT_SM);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void bind(boolean thinking) {
        if (this.thinking == thinking) {
            return;
        }
        this.thinking = thinking;
        updateContentDescription();
        requestLayout();
        invalidate();
    }

    public void startWorking() {
        if (working) {
            return;
        }
        working = true;
        updateContentDescription();
        if (isAttachedToWindow()) {
            startAnimator();
        }
    }

    public void stopWorking() {
        working = false;
        stopAnimator();
        animationProgress = 0f;
        textPaint.setShader(null);
        invalidate();
    }

    public boolean isWorking() {
        return working;
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
        int desiredWidth = Math.round(horizontalPaddingPx * 2f + matrixSizePx + summaryGapPx
                + textPaint.measureText(displayLabel()));
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int desiredHeight = Math.max(getSuggestedMinimumHeight(), Math.round(minimumHeightPx));
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
        int frame = (int) (animationProgress * SHIMMER_MS / FRAME_MS);
        int activeOffset = FRAME_OFFSETS[frame % FRAME_OFFSETS.length];
        int trailingOffset = FRAME_OFFSETS[(frame + FRAME_OFFSETS.length - 1)
                % FRAME_OFFSETS.length];
        float top = (getHeight() - matrixSizePx) / 2f;
        for (int row = 0; row < DOT_ROWS; row++) {
            for (int column = 0; column < DOT_COLUMNS; column++) {
                int offset = row * DOT_COLUMNS + column;
                float alpha = offset == activeOffset ? 1f
                        : offset == trailingOffset ? TRAILING_DOT_ALPHA : INACTIVE_DOT_ALPHA;
                dotPaint.setAlpha(Math.round(255f * alpha));
                float x = horizontalPaddingPx + dotStepPx * (column + 0.5f);
                float y = top + dotStepPx * (row + 0.5f);
                canvas.drawCircle(x, y, dotRadiusPx, dotPaint);
            }
        }
    }

    private void drawLabel(Canvas canvas) {
        if (fittedSummary.length() == 0) {
            return;
        }
        float textWidth = textPaint.measureText(fittedSummary);
        float shimmerCenter = textStartPx - shimmerWidthPx
                + animationProgress * (textWidth + shimmerWidthPx * 2f);
        textPaint.setShader(new LinearGradient(
                shimmerCenter - shimmerWidthPx,
                0f,
                shimmerCenter + shimmerWidthPx,
                0f,
                new int[] {LineTheme.TEXT_SECONDARY, highlightColor(LineTheme.TEXT_SECONDARY), LineTheme.TEXT_SECONDARY},
                new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawText(fittedSummary, textStartPx, textBaselinePx, textPaint);
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
        textStartPx = Math.round(horizontalPaddingPx + matrixSizePx + summaryGapPx);
        float availableTextWidth = Math.max(0f, measuredWidth - textStartPx - horizontalPaddingPx);
        fittedSummary = TextUtils.ellipsize(
                displayLabel(), textPaint, availableTextWidth, TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        textBaselinePx = Math.round((getMeasuredHeight() - metrics.bottom - metrics.top) / 2f);
    }

    private String displayLabel() {
        return thinking ? thinkingLabel : workingLabel;
    }

    private void updateContentDescription() {
        setContentDescription(displayLabel());
    }

    static boolean isThinking(String reasoning, String content) {
        return reasoning != null
                && reasoning.trim().length() > 0
                && (content == null || content.trim().length() == 0);
    }

    private void startAnimator() {
        if (animator != null && animator.isStarted()) {
            return;
        }
        if (!animationsEnabled()) {
            animationProgress = 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SHIMMER_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(valueAnimator -> {
            animationProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @SuppressWarnings("deprecation")
    private boolean animationsEnabled() {
        return android.provider.Settings.Global.getFloat(
                getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
        ) > 0f;
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
