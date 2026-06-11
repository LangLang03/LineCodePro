package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class ShellCommandScreenView extends LinearLayout {
    public interface Listener {
        void onBack();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShellCommandScreenView(Context context, String command, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        LinearLayout header = new LinearLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                borderPaint.setColor(LineTheme.BORDER_LIGHT);
                borderPaint.setStrokeWidth(1f);
                canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, borderPaint);
            }
        };
        header.setWillNotDraw(false);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(LineTheme.SURFACE_ELEVATED);
        LineTheme.padding(header, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        header.setMinimumHeight(LineTheme.dp(context, 48));

        LinearLayout back = new LinearLayout(context);
        back.setOrientation(HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> listener.onBack());
        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_LEFT);
        chevron.setIconColor(LineTheme.TEXT);
        chevron.setIconSizeDp(22, 22);
        chevron.setClickable(false);
        back.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 22), LineTheme.dp(context, 22)));
        TextView exit = LineTheme.text(context, "退出", LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        back.addView(exit, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        header.addView(back, new LinearLayout.LayoutParams(LineTheme.dp(context, 68), LayoutParams.WRAP_CONTENT));

        TextView title = LineTheme.textMedium(context, "完整命令", LineTheme.FONT_MD, LineTheme.TEXT);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        header.addView(new LinearLayout(context), new LinearLayout.LayoutParams(LineTheme.dp(context, 68), 1));
        addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView body = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        TextView commandBox = LineTheme.text(context, command == null || command.length() == 0 ? "(空命令)" : command, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        commandBox.setTypeface(Typeface.MONOSPACE);
        commandBox.setTextIsSelectable(true);
        commandBox.setLineSpacing(LineTheme.dp(context, 4), 1f);
        commandBox.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        LineTheme.padding(commandBox, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        content.addView(commandBox, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        body.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }
}
