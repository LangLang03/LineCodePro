package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONObject;

public final class ToolCallGenericView extends BaseToolCallView implements ToolCallCardView {
    private final String label;

    public ToolCallGenericView(Context context, String label) {
        super(context);
        this.label = label == null || label.length() == 0 ? getContext().getString(R.string.tool_call_generic_mcp) : label;
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        // 简化进度圈逻辑：直接根据结果决定最终状态
        TerminalStatus status = computeTerminalStatus(result);
        boolean running = status == TerminalStatus.RUNNING;
        boolean error = status == TerminalStatus.FAILED;
        boolean unknown = status == TerminalStatus.UNKNOWN;
        boolean hasResult = result != null && result.getContent().length() > 0;
        int statusColor = error ? LineTheme.DANGER
                : (status == TerminalStatus.SUCCESS) ? LineTheme.SUCCESS
                : unknown ? LineTheme.TEXT_TERTIARY
                : LineTheme.ACCENT;

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        IconButtonView icon = new IconButtonView(getContext(), iconFor(name));
        icon.setIconColor(statusColor);
        icon.setIconSizeDp(24, 12);
        icon.setClickable(false);
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));

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
            // 工具调用结束：成功 → CHECK；失败 → CLOSE；未知 → CLOCK_3 表示等待结果
            int doneIcon = error ? IconButtonView.CLOSE
                    : (status == TerminalStatus.SUCCESS) ? IconButtonView.CHECK
                    : IconButtonView.CLOCK_3;
            IconButtonView done = new IconButtonView(getContext(), doneIcon);
            done.setIconColor(statusColor);
            done.setIconSizeDp(18, 13);
            done.setClickable(false);
            header.addView(done, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        }
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        String inputText = ToolCallUtils.prettyJson(input);
        if (!"{}".equals(inputText)) {
            addSection(getContext().getString(R.string.tool_call_input), inputText, LineTheme.TEXT_SECONDARY, 2);
        }
        if (hasResult) {
            // If content is structured agent progress but we fell through to generic
            // (missing registry), prefer human output over raw JSON dump.
            String rawContent = result.getContent();
            String displayContent = AgentToolResultDisplay.progressPayload(rawContent) != null
                    ? AgentToolResultDisplay.displayOutput(rawContent)
                    : rawContent;
            if (displayContent == null || displayContent.trim().length() == 0) {
                displayContent = error
                        ? getContext().getString(R.string.tool_call_agent_failed)
                        : getContext().getString(R.string.tool_call_agent_done);
            }
            addSection(
                    running ? getContext().getString(R.string.tool_call_progress)
                            : getContext().getString(R.string.tool_call_output),
                    displayContent,
                    error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY,
                    running ? 3 : 8);
        }
    }

    @Override
    public void setToolReviewListener(ToolReviewListener listener) {
        // Generic view does not use tool review
    }

    @Override
    public void setProjectPath(String projectPath) {
        // Generic view does not use project path
    }

    private int iconFor(String name) {
        ToolDisplayCategory category = ToolCallUtils.getDisplayCategory(name);
        if (category == ToolDisplayCategory.SHELL) return IconButtonView.TERMINAL;
        if (ToolCallUtils.isCustomMcpTool(name)) return IconButtonView.MCP;
        if (category == ToolDisplayCategory.DELETE) return IconButtonView.TRASH_2;
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
            BoundedScrollView scroll = new BoundedScrollView(getContext(), 220);
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
