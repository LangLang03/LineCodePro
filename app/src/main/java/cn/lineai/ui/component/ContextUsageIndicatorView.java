package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import cn.lineai.ui.theme.LineTheme;

/** Small header control that presents context-window usage without adding another text label. */
public final class ContextUsageIndicatorView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int percent;

    public ContextUsageIndicatorView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        track.setStyle(Paint.Style.STROKE);
        progress.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        progress.setStrokeCap(Paint.Cap.ROUND);
        float stroke = LineTheme.dp(context, 2);
        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
    }

    public void setPercent(int percent) {
        int next = Math.max(0, Math.min(100, percent));
        if (this.percent == next) return;
        this.percent = next;
        invalidate();
    }

    public int getPercent() {
        return percent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        track.setColor(LineTheme.BORDER);
        progress.setColor(percent >= 80 ? LineTheme.WARNING : LineTheme.TEXT_SECONDARY);
        float radius = LineTheme.dp(getContext(), 8);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawOval(arc, track);
        if (percent > 0) canvas.drawArc(arc, -90f, percent * 3.6f, false, progress);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
