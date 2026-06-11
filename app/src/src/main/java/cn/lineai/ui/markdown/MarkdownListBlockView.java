package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class MarkdownListBlockView extends LinearLayout {
    public MarkdownListBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public LinearLayout addItem(String marker, int depth) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = LineTheme.dp(context, 3);
        addView(row, rowParams);

        TextView markerView = LineTheme.text(context, marker == null ? "•" : marker, LineTheme.FONT_MD, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        markerView.setGravity(Gravity.END);
        int markerWidth = Math.max(18, 22 + Math.max(0, depth) * 4);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(LineTheme.dp(context, markerWidth), LayoutParams.WRAP_CONTENT);
        markerParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(markerView, markerParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        row.addView(content, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        return content;
    }
}
