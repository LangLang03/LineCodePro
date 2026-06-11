package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import org.json.JSONObject;

public final class ToolCallGenericView extends LinearLayout {
    private final String label;

    public ToolCallGenericView(Context context, String label) {
        super(context);
        this.label = label == null || label.length() == 0 ? "MCP 调用" : label;
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        boolean running = result == null
                || "running".equals(result.getReviewState())
                || "pending".equals(result.getReviewState());
        boolean hasResult = result != null && result.getContent().length() > 0;
        boolean hasFinalResult = hasResult && !running;
        boolean error = result != null && result.isError();
        int statusColor = error ? LineTheme.DANGER : hasFinalResult ? LineTheme.SUCCESS : LineTheme.ACCENT;

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), iconFor(name));
        icon.setIconColor(statusColor);
        icon.setIconSizeDp(26, 13);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 8, LineTheme.CODE_BORDER));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 26), LineTheme.dp(getContext(), 26)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(titleBlock, titleParams);
        titleBlock.addView(LineTheme.text(getContext(), label, 10, LineTheme.TEXT_TERTIARY, Typeface.BOLD),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView nameView = LineTheme.text(getContext(), name, LineTheme.FONT_SM, error ? LineTheme.DANGER : LineTheme.TEXT, Typeface.NORMAL);
        nameView.setTypeface(Typeface.MONOSPACE);
        nameView.setSingleLine(true);
        titleBlock.addView(nameView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (running) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            header.addView(bar, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        } else {
            IconButtonView done = new IconButtonView(getContext(), error ? IconButtonView.CLOSE : IconButtonView.CHECK);
            done.setIconColor(statusColor);
            done.setIconSizeDp(18, 13);
            done.setClickable(false);
            header.addView(done, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        }
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        String inputText = ToolCallUtils.prettyJson(input);
        if (!"{}".equals(inputText)) {
            addSection("输入", inputText, LineTheme.TEXT_SECONDARY, 2);
        }
        if (hasResult) {
            addSection(running ? "进度" : "返回值", result.getContent(), error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, running ? 3 : 8);
        }
    }

    private int iconFor(String name) {
        if (ToolCallUtils.isShellTool(name)) return IconButtonView.TERMINAL;
        if (ToolCallUtils.isHttpTool(name) || ToolCallUtils.isCustomMcpTool(name)) return IconButtonView.MCP;
        if (ToolCallUtils.isDeleteTool(name)) return IconButtonView.TRASH_2;
        return IconButtonView.MCP;
    }

    private void addSection(String title, String content, int color, int maxHeightRows) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(VERTICAL);
        LineTheme.padding(section, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        section.addView(LineTheme.text(getContext(), title, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView text = LineTheme.text(getContext(), content, LineTheme.FONT_XS, color, Typeface.NORMAL);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        LayoutParams textParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.topMargin = LineTheme.dp(getContext(), 4);
        if (maxHeightRows > 4) {
            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(false);
            scroll.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE, 8, LineTheme.CODE_BORDER));
            LineTheme.padding(scroll, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
            scroll.addView(text, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LayoutParams scrollParams = new LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(getContext(), 220));
            scrollParams.topMargin = LineTheme.dp(getContext(), 4);
            section.addView(scroll, scrollParams);
        } else {
            text.setMaxLines(maxHeightRows);
            section.addView(text, textParams);
        }
        addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
