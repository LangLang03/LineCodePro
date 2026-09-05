package cn.lineai.tool.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.LineTheme;

/** Error output embedded in a tool that otherwise has no result body. */
public final class ToolErrorView extends LinearLayout {
    private final TextView details;

    public ToolErrorView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        BoundedScrollView scroll = new BoundedScrollView(context, 240);
        details = LineTheme.text(context, "", 13, LineTheme.DANGER, Typeface.NORMAL);
        LineTheme.padding(details, 14, 12, 14, 12);
        details.setLineSpacing(LineTheme.dp(context, 6), 1);
        details.setTextIsSelectable(true);
        scroll.addView(details, new android.widget.ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, -2));
        setVisibility(GONE);
    }

    public void bind(ToolResult result) {
        if (result == null || !result.isError()) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        String output = AgentToolResultDisplay.progressPayload(result.getContent()) == null
                ? result.getContent() : AgentToolResultDisplay.displayOutput(result.getContent());
        if (output.trim().isEmpty()) output = getContext().getString(R.string.tool_call_status_failed);
        details.setText(output);
    }
}
