package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.tokens.ThemeBinder;

/** Centred screen header with one optional action on each side and a hairline bottom border. */
public final class ScreenBar extends LinearLayout {

    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ThemeBinder theme;
    private final TextView titleView;
    private IconButton leftButton;
    private IconButton rightButton;
    private Integer rightTint;

    public ScreenBar(Context context, ThemeBinder theme, int title, int leftIcon, int leftDescription,
                     Runnable leftAction, int rightIcon, int rightDescription, Runnable rightAction) {
        super(context);
        this.theme = theme;
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        Ui.padding(this, 16, 12, 16, 12);
        leftButton = new IconButton(context, leftIcon);
        leftButton.setIconSizeDp(36, 22);
        leftButton.setContentDescription(context.getString(leftDescription));
        leftButton.setOnClickListener(v -> leftAction.run());
        addView(leftButton, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        titleView = Ui.bold(context, context.getString(title), 17, theme.current().text);
        titleView.setGravity(android.view.Gravity.CENTER);
        if (android.os.Build.VERSION.SDK_INT >= 28) titleView.setAccessibilityHeading(true);
        addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (rightIcon == 0 || rightAction == null) {
            addView(new android.view.View(context),
                    new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        } else {
            rightButton = new IconButton(context, rightIcon);
            rightButton.setIconSizeDp(36, 22);
            rightButton.setContentDescription(context.getString(rightDescription));
            rightButton.setOnClickListener(v -> rightAction.run());
            addView(rightButton, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
            theme.bind(t -> rightButton.setIconColor(rightTint == null ? t.text : rightTint));
        }
        theme.bind(t -> {
            setBackgroundColor(t.bg);
            leftButton.setIconColor(t.text);
            titleView.setTextColor(t.text);
            invalidate();
        });
    }

    public void title(CharSequence value) {
        titleView.setText(value);
    }

    public ScreenBar rightTint(int color) {
        rightTint = color;
        if (rightButton != null) rightButton.setIconColor(color);
        return this;
    }

    public ScreenBar iconSizes(int leftDp, int rightDp) {
        if (leftButton != null) leftButton.setIconSizeDp(36, leftDp);
        if (rightButton != null) rightButton.setIconSizeDp(36, rightDp);
        return this;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        border.setColor(theme.current().border);
        border.setStrokeWidth(1f);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, border);
    }
}
