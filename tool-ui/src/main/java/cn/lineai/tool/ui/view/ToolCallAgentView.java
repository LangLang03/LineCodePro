package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.FlowLayoutView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.ThinkingBlockView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import cn.lineai.tool.ui.NestedToolCallParser;
import cn.lineai.ui.markdown.MarkdownView;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallAgentView extends BaseToolCallView implements ToolCallCardView {
    private boolean expanded = true;
    private ToolCall lastToolCall;
    private ToolResult lastResult;
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;
    private MarkdownView outputMarkdownView;
    private ThinkingBlockView thinkingView;
    private String lastLayoutSignature = "";

    public ToolCallAgentView(Context context) {
        super(context);
        setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        lastToolCall = toolCall;
        lastResult = result;
        removeAllViews();
        outputMarkdownView = null;
        thinkingView = null;

        String toolName = toolCall == null ? "" : toolCall.getName();
        boolean isCustomAgent = ToolCallUtils.isCustomAgentTool(toolName);
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String resultContent = result == null ? "" : result.getContent();
        JSONObject progress = AgentToolResultDisplay.progressPayload(resultContent);
        String fallbackName = isCustomAgent ? input.optString("task", toolName) : input.optString("description", "Agent");
        String name = AgentToolResultDisplay.description(resultContent, fallbackName);
        if (name == null || name.trim().length() == 0) {
            name = fallbackName;
        }
        String type = normalizeType(AgentToolResultDisplay.type(
                resultContent,
                isCustomAgent ? "sub-coding" : input.optString("type")));
        String outerReviewState = result == null ? "" : result.getReviewState();
        String progressStatus = AgentToolResultDisplay.progressStatus(resultContent);
        boolean running = "running".equals(progressStatus)
                || "waiting_unlock".equals(progressStatus)
                || "running".equals(outerReviewState);
        boolean pendingReview = "pending".equals(progressStatus) || "pending".equals(outerReviewState);
        boolean resultHasError = result != null && result.isError();
        boolean interrupted = resultHasError && running;
        if (interrupted) {
            running = false;
        }
        boolean complete = result != null && !running && !pendingReview;
        boolean error = resultHasError || "error".equals(progressStatus) || interrupted;
        String output = AgentToolResultDisplay.displayOutput(resultContent);
        String thinking = AgentToolResultDisplay.thinking(resultContent);
        JSONArray nestedToolCalls = AgentToolResultDisplay.nestedToolCalls(resultContent);
        int toolCount = progress == null
                ? toolCount(result)
                : AgentToolResultDisplay.toolCallCount(resultContent);
        String agentId = AgentToolResultDisplay.agentId(resultContent);
        String status = error ? getContext().getString(R.string.tool_call_status_failed) : pendingReview ? getContext().getString(R.string.tool_call_status_pending_review) : complete ? getContext().getString(R.string.tool_call_status_done) : getContext().getString(R.string.tool_call_status_running);
        int typeColor = "explore".equals(type) ? LineTheme.ACCENT : LineTheme.DANGER;
        int statusColor = error ? LineTheme.DANGER : pendingReview ? LineTheme.WARNING : complete ? LineTheme.SUCCESS : LineTheme.ACCENT;
        lastLayoutSignature = layoutSignature(toolCall, result);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setOnClickListener(v -> {
            expanded = !expanded;
            bind(lastToolCall, lastResult);
        });
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.BOT);
        icon.setIconColor(typeColor);
        icon.setIconSizeDp(28, 14);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 14, typeColor));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 28), LineTheme.dp(getContext(), 28)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(titleBlock, titleParams);

        TextView title = LineTheme.text(getContext(), name, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FlowLayoutView meta = new FlowLayoutView(getContext());
        meta.setSpacingDp(LineTheme.XS, 3);
        meta.addView(pill(typeLabel(type), typeColor, LineTheme.CODE_BG, LineTheme.CODE_BORDER));
        if (agentId.length() > 0) {
            meta.addView(pill(agentId, LineTheme.TEXT_TERTIARY, LineTheme.SURFACE_LIGHT, LineTheme.CODE_BORDER));
        }
        if (toolCount > 0) {
            meta.addView(pill(getContext().getString(R.string.tool_call_agent_tool_count, toolCount), LineTheme.TEXT_TERTIARY, LineTheme.SURFACE_LIGHT, LineTheme.CODE_BORDER));
        }
        LayoutParams metaParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = LineTheme.dp(getContext(), 3);
        titleBlock.addView(meta, metaParams);

        if (!complete && !pendingReview) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            header.addView(bar, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        } else {
            IconButtonView statusIcon = new IconButtonView(getContext(), error ? IconButtonView.CIRCLE_X : pendingReview ? IconButtonView.CLOCK_3 : IconButtonView.CIRCLE_CHECK);
            statusIcon.setIconColor(statusColor);
            statusIcon.setIconSizeDp(18, 13);
            statusIcon.setClickable(false);
            header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        }
        TextView statusText = LineTheme.text(getContext(), status, LineTheme.FONT_XS, statusColor, Typeface.BOLD);
        LayoutParams statusParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(statusText, statusParams);

        IconButtonView chevron = new IconButtonView(getContext(), expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(16, 12);
        chevron.setClickable(false);
        LayoutParams chevronParams = new LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16));
        chevronParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(chevron, chevronParams);

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (!expanded) {
            return;
        }

        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.MD);
        if (thinking.trim().length() > 0) {
            thinkingView = new ThinkingBlockView(getContext());
            thinkingView.bind(toolCall == null ? "" : toolCall.getId(), thinking, !complete, false, true);
            LayoutParams thinkingParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            thinkingParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.SM);
            content.addView(thinkingView, thinkingParams);
        }
        if (!complete && output.trim().length() == 0) {
            TextView runningText = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_agent_running), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            content.addView(runningText, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else if (output.length() > 0) {
            MarkdownView markdownView = new MarkdownView(getContext());
            outputMarkdownView = markdownView;
            markdownView.setMarkdown(output);
            content.addView(markdownView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            TextView empty = LineTheme.text(getContext(), error ? getContext().getString(R.string.tool_call_agent_failed) : getContext().getString(R.string.tool_call_agent_done), LineTheme.FONT_XS,
                    error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            content.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addNestedToolCalls(content, nestedToolCalls);

        BoundedScrollView scrollView = new BoundedScrollView(getContext(), 400);
        scrollView.addView(content, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public static String layoutSignature(ToolCall toolCall, ToolResult result) {
        String content = result == null ? "" : result.getContent();
        StringBuilder b = new StringBuilder();
        b.append(AgentToolResultDisplay.progressStatus(content)).append('|');
        b.append(result == null ? "" : result.getReviewState()).append('|');
        b.append(result != null && result.isError()).append('|');
        b.append(AgentToolResultDisplay.agentId(content)).append('|');
        b.append(AgentToolResultDisplay.thinking(content).trim().length() > 0).append('|');
        b.append(AgentToolResultDisplay.displayOutput(content).trim().length() > 0).append('|');
        b.append(AgentToolResultDisplay.toolCallCount(content)).append('|');
        b.append(AgentToolResultDisplay.description(content, "")).append('|');
        b.append(AgentToolResultDisplay.type(content, "")).append('|');
        JSONArray nested = AgentToolResultDisplay.nestedToolCalls(content);
        b.append(nested == null ? 0 : nested.length());
        return b.toString();
    }

    @Override
    public void updateContent(ToolCall toolCall, ToolResult result) {
        String sig = layoutSignature(toolCall, result);
        if (!sig.equals(lastLayoutSignature)) {
            bind(toolCall, result);
            return;
        }
        lastLayoutSignature = sig;
        lastToolCall = toolCall;
        lastResult = result;
        String content = result == null ? "" : result.getContent();
        if (thinkingView != null) {
            String progressStatus = AgentToolResultDisplay.progressStatus(content);
            String reviewState = result == null ? "" : result.getReviewState();
            boolean running = "running".equals(progressStatus)
                    || "waiting_unlock".equals(progressStatus)
                    || "running".equals(reviewState);
            boolean pendingReview = "pending".equals(progressStatus) || "pending".equals(reviewState);
            boolean complete = result != null && !running && !pendingReview;
            thinkingView.bind(toolCall == null ? "" : toolCall.getId(),
                    AgentToolResultDisplay.thinking(content), !complete, false, true);
        }
        if (outputMarkdownView != null) {
            outputMarkdownView.setMarkdown(AgentToolResultDisplay.displayOutput(content));
        }
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    private TextView pill(String text, int color, int background, int border) {
        TextView view = LineTheme.text(getContext(), text, 10, color, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LineTheme.roundedStroke(getContext(), background, 999, border));
        LineTheme.padding(view, LineTheme.XS, 1, LineTheme.XS, 1);
        return view;
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value) || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.length() == 0 ? "explore" : value;
    }

    private String typeLabel(String type) {
        return "explore".equals(type) ? getContext().getString(R.string.tool_call_agent_type_explore) : getContext().getString(R.string.tool_call_agent_type_coding);
    }

    private int toolCount(ToolResult result) {
        if (result == null) {
            return 0;
        }
        int fromProgress = AgentToolResultDisplay.toolCallCount(result.getContent());
        if (fromProgress > 0) {
            return fromProgress;
        }
        return intAfterLabel(result.getContent(), "工具调用:");
    }

    private String outputText(ToolResult result) {
        return AgentToolResultDisplay.displayOutput(result == null ? "" : result.getContent());
    }

    private JSONObject progressPayload(ToolResult result) {
        return AgentToolResultDisplay.progressPayload(result == null ? "" : result.getContent());
    }

    private void addNestedToolCalls(LinearLayout content, JSONArray nestedToolCalls) {
        if (nestedToolCalls == null || nestedToolCalls.length() == 0) {
            return;
        }
        TextView label = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_agent_tool_calls_label), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(label, labelParams);

        List<NestedToolCallParser.NestedCall> nestedCalls = NestedToolCallParser.parse(nestedToolCalls);
        for (NestedToolCallParser.NestedCall nc : nestedCalls) {
            ToolCallBlockView block = new ToolCallBlockView(getContext());
            block.setProjectPath(projectPath);
            block.setToolReviewListener(toolReviewListener);
            block.bind(nc.call, nc.result);
            LayoutParams blockParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            blockParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            content.addView(block, blockParams);
        }
    }

    private int intAfterLabel(String text, String label) {
        if (text == null) {
            return 0;
        }
        int index = text.indexOf(label);
        if (index < 0) {
            return 0;
        }
        int start = index + label.length();
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
