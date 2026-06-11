package cn.lineai.ui.markdown;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;

public final class MarkdownQuoteBlockView extends LinearLayout {
    private final LinearLayout content;

    public MarkdownQuoteBlockView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);

        View rail = new View(context);
        rail.setBackground(LineTheme.rounded(context, LineTheme.BORDER_LIGHT, 2));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 3), LayoutParams.MATCH_PARENT);
        railParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        addView(rail, railParams);

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        addView(content, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    public LinearLayout getContent() {
        return content;
    }
}
