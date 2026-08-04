package cn.lineai.ui.markdown;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.view.View;

public final class MarkdownThematicBreakView extends View {
    public MarkdownThematicBreakView(Context context) {
        super(context);
        setBackgroundColor(LineTheme.BORDER_LIGHT);
        setMinimumHeight(Math.max(1, LineTheme.dp(context, 1)));
    }
}
