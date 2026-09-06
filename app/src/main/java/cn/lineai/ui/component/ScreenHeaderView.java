package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ScreenHeaderView extends LinearLayout {
    static final int ACTION_SIZE_DP = 36;
    static final int ICON_SIZE_DP = 22;
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
        addView(left, new LayoutParams(LineTheme.dp(context, ACTION_SIZE_DP), LineTheme.dp(context, ACTION_SIZE_DP)));

        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        if (android.os.Build.VERSION.SDK_INT >= 28) titleView.setAccessibilityHeading(true);
        addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        View right = rightAction == null ? spacer(context) : rightAction;
        LayoutParams rightParams;
        if (rightAction instanceof TextView) {
            right.setMinimumWidth(LineTheme.dp(context, ACTION_SIZE_DP));
            rightParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, ACTION_SIZE_DP));
        } else if (rightAction instanceof LinearLayout) {
            rightParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        } else {
            rightParams = new LayoutParams(LineTheme.dp(context, ACTION_SIZE_DP), LineTheme.dp(context, ACTION_SIZE_DP));
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
        button.setIconSizeDp(ACTION_SIZE_DP, ICON_SIZE_DP);
        button.setOnClickListener(v -> onBack.run());
        return button;
    }
}
