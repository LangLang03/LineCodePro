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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import cn.lineai.tool.ui.AgentPipelineSummaryParser;
import cn.lineai.tool.ui.NestedToolCallParser;
import cn.lineai.ui.markdown.MarkdownView;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallAgentPipelineView extends BaseToolCallView implements ToolCallCardView {
    private boolean expanded = true;
    private ToolCall lastToolCall;
    private ToolResult lastResult;
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;
    private final java.util.HashMap<String, Boolean> rowExpandedMap = new java.util.HashMap<>();

    public ToolCallAgentPipelineView(Context context) {
        super(context);
        setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        lastToolCall = toolCall;
        lastResult = result;
        removeAllViews();

        JSONObject input = ToolCallUtils.parseInput(toolCall);
        JSONArray agents = input.optJSONArray("agents");
        String parseError = AgentPipelineSummaryParser.parseInputError(toolCall, input);
        if (parseError.length() > 0 && (agents == null || agents.length() == 0)) {
            bindParseError(toolCall, result, parseError);
            return;
        }
        int total = agents == null ? 0 : agents.length();
        JSONObject progress = AgentPipelineSummaryParser.progressPayload(result);
        AgentPipelineSummaryParser.PipelineSummary ps = AgentPipelineSummaryParser.computeSummary(progress, result, total);
        HashMap<String, AgentPipelineSummaryParser.AgentSummary> summaryById = ps.summaryById;
        int completed = ps.completed;
        int running = ps.running;
        int pendingReview = ps.pendingReview;
        int failed = ps.failed;
        boolean complete = ps.complete;
        boolean error = ps.error;

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setOnClickListener(v -> {
            expanded = !expanded;
            bind(lastToolCall, lastResult);
        });
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.GIT_BRANCH);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(30, 15);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 15, LineTheme.CODE_BORDER));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 30), LineTheme.dp(getContext(), 30)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(titleBlock, titleParams);

        TextView title = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_pipeline_title), LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FlowLayoutView summary = new FlowLayoutView(getContext());
        summary.setSpacingDp(LineTheme.SM, 3);
        summary.addView(summaryItem(IconButtonView.CIRCLE_CHECK, getContext().getString(R.string.tool_call_pipeline_completed, completed), LineTheme.SUCCESS));
        if (running > 0) {
            summary.addView(summaryItem(IconButtonView.LOADER, getContext().getString(R.string.tool_call_pipeline_summary_running, running), LineTheme.ACCENT));
        }
        if (pendingReview > 0) {
            summary.addView(summaryItem(IconButtonView.CLOCK_3, getContext().getString(R.string.tool_call_pipeline_summary_pending_review, pendingReview), LineTheme.WARNING));
        }
        int waiting = Math.max(0, total - completed - running - pendingReview - failed);
        if (waiting > 0) {
            summary.addView(summaryItem(IconButtonView.CLOCK_3, getContext().getString(R.string.tool_call_pipeline_summary_waiting, waiting), LineTheme.TEXT_TERTIARY));
        }
        if (failed > 0) {
            summary.addView(summaryItem(IconButtonView.CIRCLE_X, getContext().getString(R.string.tool_call_pipeline_summary_failed, failed), LineTheme.DANGER));
        }
        LayoutParams summaryParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = LineTheme.dp(getContext(), 3);
        titleBlock.addView(summary, summaryParams);

        if (!complete) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            header.addView(bar, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        } else {
            IconButtonView statusIcon = new IconButtonView(getContext(), error ? IconButtonView.CIRCLE_X : IconButtonView.CIRCLE_CHECK);
            statusIcon.setIconColor(error ? LineTheme.DANGER : LineTheme.SUCCESS);
            statusIcon.setIconSizeDp(18, 13);
            statusIcon.setClickable(false);
            header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        }

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

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(VERTICAL);
        LineTheme.padding(list, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        if (agents != null && agents.length() > 0) {
            for (int i = 0; i < agents.length(); i++) {
                JSONObject agent = agents.optJSONObject(i);
                if (agent != null) {
                    addAgentRow(list, agent, summaryById.get(agent.optString("id")), complete, error);
                }
            }
        } else if (result != null && result.getContent().length() > 0) {
            MarkdownView markdownView = new MarkdownView(getContext());
            markdownView.setMarkdown(result.getContent());
            list.addView(markdownView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            TextView empty = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_pipeline_running), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            list.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        String finalSummary = progress != null ? progress.optString("summary") : "";
        if (finalSummary.length() > 0 && (agents == null || agents.length() == 0)) {
            View summaryDivider = new View(getContext());
            summaryDivider.setBackgroundColor(LineTheme.CODE_BORDER);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
            dividerParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
            dividerParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.SM);
            list.addView(summaryDivider, dividerParams);
            MarkdownView summaryMarkdown = new MarkdownView(getContext());
            summaryMarkdown.setMarkdown(finalSummary);
            list.addView(summaryMarkdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    private View summaryItem(int iconType, String text, int color) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(color);
        icon.setIconSizeDp(10, 10);
        icon.setClickable(false);
        item.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 10), LineTheme.dp(getContext(), 10)));
        TextView label = LineTheme.text(getContext(), text, LineTheme.FONT_XS, color, Typeface.BOLD);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(getContext(), 3);
        item.addView(label, labelParams);
        return item;
    }

    private void addAgentRow(LinearLayout list, JSONObject agent, AgentPipelineSummaryParser.AgentSummary summary, boolean complete, boolean pipelineError) {
        String id = agent.optString("id").trim();
        String name = agent.optString("description", id).trim();
        String type = AgentPipelineSummaryParser.normalizeType(agent.optString("type"));
        String rowStatus = summary == null ? "" : summary.status;
        boolean running = "running".equals(rowStatus);
        boolean pendingReview = "pending".equals(rowStatus);
        boolean rowInterrupted = pipelineError && running && complete;
        boolean done = "done".equals(rowStatus) || (complete && summary != null && !summary.error && !rowInterrupted) || (complete && summary == null && !pipelineError);
        boolean error = (summary != null && summary.error) || "error".equals(rowStatus) || rowInterrupted || (complete && pipelineError && summary == null);
        if (rowInterrupted) {
            running = false;
        }
        String status = error ? getContext().getString(R.string.tool_call_status_failed) : pendingReview ? getContext().getString(R.string.tool_call_status_pending_review) : running ? getContext().getString(R.string.tool_call_status_running) : done ? getContext().getString(R.string.tool_call_status_done) : getContext().getString(R.string.tool_call_pipeline_status_waiting);
        int typeColor = "explore".equals(type) ? LineTheme.ACCENT : LineTheme.DANGER;
        int statusColor = error ? LineTheme.DANGER : pendingReview ? LineTheme.WARNING : running ? LineTheme.ACCENT : done ? LineTheme.SUCCESS : LineTheme.TEXT_TERTIARY;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(VERTICAL);
        row.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(row, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        boolean rowExpanded = rowExpandedMap.containsKey(id) && rowExpandedMap.get(id);
        header.setClickable(true);
        header.setOnClickListener(v -> {
            boolean current = rowExpandedMap.containsKey(id) && rowExpandedMap.get(id);
            rowExpandedMap.put(id, !current);
            bind(lastToolCall, lastResult);
        });

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.BOT);
        icon.setIconColor(typeColor);
        icon.setIconSizeDp(24, 12);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 12, typeColor));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));

        LinearLayout textBlock = new LinearLayout(getContext());
        textBlock.setOrientation(VERTICAL);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        textParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(textBlock, textParams);

        TextView title = LineTheme.text(getContext(), name.length() == 0 ? id : name, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        textBlock.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FlowLayoutView meta = new FlowLayoutView(getContext());
        meta.setSpacingDp(LineTheme.XS, 3);
        meta.addView(pill(typeLabel(type), typeColor));
        if (id.length() > 0) {
            meta.addView(pill(id, LineTheme.TEXT_TERTIARY));
        }
        JSONArray deps = agent.optJSONArray("depends_on");
        if (deps != null) {
            for (int i = 0; i < deps.length(); i++) {
                String dep = deps.optString(i);
                if (dep.length() > 0) {
                    meta.addView(pill(dep, LineTheme.SUCCESS));
                }
            }
        }
        LayoutParams metaParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = LineTheme.dp(getContext(), 3);
        textBlock.addView(meta, metaParams);

        IconButtonView statusIcon = new IconButtonView(getContext(),
                error ? IconButtonView.CIRCLE_X : running ? IconButtonView.LOADER : pendingReview ? IconButtonView.CLOCK_3 : done ? IconButtonView.CIRCLE_CHECK : IconButtonView.CLOCK_3);
        statusIcon.setIconColor(statusColor);
        statusIcon.setIconSizeDp(16, 12);
        statusIcon.setClickable(false);
        header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16)));

        TextView statusText = LineTheme.text(getContext(), status, LineTheme.FONT_XS, statusColor, Typeface.BOLD);
        LayoutParams statusParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(statusText, statusParams);
        IconButtonView rowChevron = new IconButtonView(getContext(), rowExpanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        rowChevron.setIconColor(LineTheme.TEXT_TERTIARY);
        rowChevron.setIconSizeDp(14, 10);
        rowChevron.setClickable(false);
        LayoutParams rowChevronParams = new LayoutParams(LineTheme.dp(getContext(), 14), LineTheme.dp(getContext(), 14));
        rowChevronParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(rowChevron, rowChevronParams);
        row.addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (rowExpanded && summary != null) {
            LinearLayout rowContent = new LinearLayout(getContext());
            rowContent.setOrientation(VERTICAL);
            LayoutParams rowContentParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowContentParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
            if (summary.thinking.length() > 0) {
                ThinkingBlockView thinkingView = new ThinkingBlockView(getContext());
                thinkingView.bind(id, summary.thinking, !"done".equals(rowStatus), false, true);
                LinearLayout.LayoutParams thinkingParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                thinkingParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.SM);
                rowContent.addView(thinkingView, thinkingParams);
            }
            if (summary.output.length() > 0) {
                MarkdownView markdownView = new MarkdownView(getContext());
                markdownView.setMarkdown(summary.output);
                rowContent.addView(markdownView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            addNestedToolCalls(rowContent, summary.toolCalls);
            BoundedScrollView scrollView = new BoundedScrollView(getContext(), 280);
            scrollView.addView(rowContent, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
            row.addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        list.addView(row, rowParams);
    }

    private TextView pill(String text, int color) {
        TextView view = LineTheme.text(getContext(), text, 10, color, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 999, LineTheme.CODE_BORDER));
        LineTheme.padding(view, LineTheme.XS, 1, LineTheme.XS, 1);
        return view;
    }

    private void bindParseError(ToolCall toolCall, ToolResult result, String parseError) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setOnClickListener(v -> {
            expanded = !expanded;
            bind(lastToolCall, lastResult);
        });
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.GIT_BRANCH);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(30, 15);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 15, LineTheme.CODE_BORDER));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 30), LineTheme.dp(getContext(), 30)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(titleBlock, titleParams);

        TextView title = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_pipeline_title), LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FlowLayoutView meta = new FlowLayoutView(getContext());
        meta.setSpacingDp(LineTheme.SM, 3);
        meta.addView(summaryItem(IconButtonView.CIRCLE_X, getContext().getString(R.string.tool_call_status_failed), LineTheme.DANGER));
        titleBlock.addView(meta, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        IconButtonView statusIcon = new IconButtonView(getContext(), IconButtonView.CIRCLE_X);
        statusIcon.setIconColor(LineTheme.DANGER);
        statusIcon.setIconSizeDp(16, 12);
        statusIcon.setClickable(false);
        header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16)));

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

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(VERTICAL);
        LineTheme.padding(list, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        MarkdownView errorView = new MarkdownView(getContext());
        errorView.setMarkdown(parseError);
        list.addView(errorView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addNestedToolCalls(LinearLayout row, JSONArray nestedToolCalls) {
        if (nestedToolCalls == null || nestedToolCalls.length() == 0) {
            return;
        }
        LinearLayout tools = new LinearLayout(getContext());
        tools.setOrientation(VERTICAL);
        LayoutParams toolsParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolsParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        row.addView(tools, toolsParams);
        List<NestedToolCallParser.NestedCall> nestedCalls = NestedToolCallParser.parse(nestedToolCalls);
        for (NestedToolCallParser.NestedCall nc : nestedCalls) {
            ToolCallBlockView block = new ToolCallBlockView(getContext());
            block.setProjectPath(projectPath);
            block.setToolReviewListener(toolReviewListener);
            block.bind(nc.call, nc.result);
            LayoutParams blockParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            blockParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            tools.addView(block, blockParams);
        }
    }

    private String typeLabel(String type) {
        return "explore".equals(type) ? getContext().getString(R.string.tool_call_agent_type_explore) : getContext().getString(R.string.tool_call_agent_type_coding);
    }
}
