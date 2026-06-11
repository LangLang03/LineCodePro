package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import cn.lineai.ui.theme.LineTheme;

public final class RefreshCwButtonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int iconDp;

    public RefreshCwButtonView(Context context) {
        this(context, 18);
    }

    public RefreshCwButtonView(Context context, int iconDp) {
        super(context);
        this.iconDp = iconDp;
        setClickable(true);
        setFocusable(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(LineTheme.TEXT);
        paint.setStrokeWidth(LineTheme.dp(context, 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = LineTheme.dp(getContext(), iconDp);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;

        canvas.save();
        canvas.translate(left, top);
        float scale = size / 24f;
        canvas.scale(scale, scale);
        paint.setStrokeWidth(2f);

        path.reset();
        path.moveTo(3f, 12f);
        path.cubicTo(3f, 7.03f, 7.03f, 3f, 12f, 3f);
        path.cubicTo(14.5f, 3f, 16.89f, 3.95f, 18.74f, 5.74f);
        path.lineTo(21f, 8f);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(21f, 3f);
        path.lineTo(21f, 8f);
        path.lineTo(16f, 8f);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(21f, 12f);
        path.cubicTo(21f, 16.97f, 16.97f, 21f, 12f, 21f);
        path.cubicTo(9.5f, 21f, 7.11f, 20.05f, 5.26f, 18.26f);
        path.lineTo(3f, 16f);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(8f, 16f);
        path.lineTo(3f, 16f);
        path.lineTo(3f, 21f);
        canvas.drawPath(path, paint);

        canvas.restore();
    }
}
