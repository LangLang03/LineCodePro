package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class ScreenHeaderView extends LinearLayout {
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScreenHeaderView(Context context, String title, Runnable onBack, View rightAction) {
        this(context, title, onBack == null ? null : backButtonView(context, onBack), rightAction);
    }

    public ScreenHeaderView(Context context, String title, View leftAction, View rightAction) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        View left = leftAction == null ? spacer(context) : leftAction;
        addView(left, new LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36)));

        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        View right = rightAction == null ? spacer(context) : rightAction;
        LayoutParams rightParams;
        if (rightAction instanceof TextView) {
            right.setMinimumWidth(LineTheme.dp(context, 36));
            rightParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 36));
        } else {
            rightParams = new LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36));
        }
        addView(right, rightParams);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        borderPaint.setColor(LineTheme.BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, borderPaint);
    }

    private View spacer(Context context) {
        return new View(context);
    }

    private static View backButtonView(Context context, Runnable onBack) {
        IconButtonView button = new IconButtonView(context, IconButtonView.CHEVRON_LEFT);
        button.setIconColor(LineTheme.TEXT);
        button.setIconSizeDp(36, 22);
        button.setOnClickListener(v -> onBack.run());
        return button;
    }
}
