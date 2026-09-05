package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;

public final class ShellCommandScreenView extends ScreenSurfaceView {
    public interface Listener {
        void onBack();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShellCommandScreenView(Context context, String command, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, context.getString(R.string.shell_command_title), listener::onBack, null), new LayoutParams(-1,-2));

        ScrollView body = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 28, 8, 28, 48);
        TextView commandBox = LineTheme.text(context, command == null || command.length() == 0 ? context.getString(R.string.shell_command_empty) : command, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
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
