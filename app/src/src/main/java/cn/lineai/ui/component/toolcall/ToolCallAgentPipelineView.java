package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.FlowLayoutView;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.markdown.MarkdownView;
import cn.lineai.ui.theme.LineTheme;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallAgentPipelineView extends LinearLayout {
    private boolean expanded = true;
    private ToolCall lastToolCall;
    private ToolResult lastResult;

    public ToolCallAgentPipelineView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        lastToolCall = toolCall;
        lastResult = result;
        removeAllViews();

        JSONObject input = ToolCallUtils.parseInput(toolCall);
        JSONArray agents = input.optJSONArray("agents");
        int total = agents == null ? 0 : agents.length();
        JSONObject progress = progressPayload(result);
        boolean runningProgress = progress != null && "running".equals(progress.optString("status", "running"));
        boolean complete = result != null && !runningProgress && !"running".equals(result.getReviewState());
        boolean error = result != null && (result.isError() || (progress != null && "error".equals(progress.optString("status"))));
        HashMap<String, AgentSummary> summaryById = progress != null ? parseProgress(progress) : parseResult(result);
        int failed = error ? Math.max(progress == null ? 1 : 0, failedCount(summaryById)) : failedCount(summaryById);
        int completed = summaryById.isEmpty() && complete && total > 0 && !error ? total : doneCount(summaryById);
        int running = complete ? 0 : runningCount(summaryById);
        if (!complete && running == 0 && progress == null && total > 0) {
            running = 1;
        }

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

        TextView title = LineTheme.text(getContext(), "Agent Pipeline", LineTheme.FONT_SM, LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FlowLayoutView summary = new FlowLayoutView(getContext());
        summary.setSpacingDp(LineTheme.SM, 3);
        summary.addView(summaryItem(IconButtonView.CIRCLE_CHECK, completed + " 完成", LineTheme.SUCCESS));
        if (running > 0) {
            summary.addView(summaryItem(IconButtonView.LOADER, running + " 运行", LineTheme.ACCENT));
        }
        int waiting = Math.max(0, total - completed - running - failed);
        if (waiting > 0) {
            summary.addView(summaryItem(IconButtonView.CLOCK_3, waiting + " 等待中", LineTheme.TEXT_TERTIARY));
        }
        if (failed > 0) {
            summary.addView(summaryItem(IconButtonView.CIRCLE_X, failed + " 失败", LineTheme.DANGER));
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
            TextView empty = LineTheme.text(getContext(), "正在创建 Agent 流水线...", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            list.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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

    private void addAgentRow(LinearLayout list, JSONObject agent, AgentSummary summary, boolean complete, boolean pipelineError) {
        String id = agent.optString("id").trim();
        String name = agent.optString("description", id).trim();
        String type = normalizeType(agent.optString("type"));
        String rowStatus = summary == null ? "" : summary.status;
        boolean running = "running".equals(rowStatus);
        boolean done = "done".equals(rowStatus) || (complete && summary != null && !summary.error) || (complete && summary == null && !pipelineError);
        boolean error = (summary != null && summary.error) || "error".equals(rowStatus) || (complete && pipelineError && summary == null);
        String status = error ? "失败" : running ? "运行中" : done ? "完成" : "等待中";
        int typeColor = "explore".equals(type) ? LineTheme.ACCENT : LineTheme.DANGER;
        int statusColor = error ? LineTheme.DANGER : running ? LineTheme.ACCENT : done ? LineTheme.SUCCESS : LineTheme.TEXT_TERTIARY;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(VERTICAL);
        row.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
        LineTheme.padding(row, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

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
                error ? IconButtonView.CIRCLE_X : running ? IconButtonView.LOADER : done ? IconButtonView.CIRCLE_CHECK : IconButtonView.CLOCK_3);
        statusIcon.setIconColor(statusColor);
        statusIcon.setIconSizeDp(16, 12);
        statusIcon.setClickable(false);
        header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16)));

        TextView statusText = LineTheme.text(getContext(), status, LineTheme.FONT_XS, statusColor, Typeface.BOLD);
        LayoutParams statusParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(statusText, statusParams);
        row.addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (summary != null && (summary.output.length() > 0 || summary.thinking.length() > 0)) {
            String preview = summary.output.length() > 0 ? summary.output : summary.thinking;
            TextView output = LineTheme.text(getContext(), preview, LineTheme.FONT_XS,
                    error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            output.setMaxLines(4);
            LayoutParams outputParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            outputParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
            row.addView(output, outputParams);
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

    private JSONObject progressPayload(ToolResult result) {
        if (result == null || result.getContent().trim().length() == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(result.getContent());
            return object.optBoolean("linecode_agent_pipeline_progress") ? object : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private HashMap<String, AgentSummary> parseProgress(JSONObject progress) {
        HashMap<String, AgentSummary> values = new HashMap<>();
        if (progress == null) {
            return values;
        }
        JSONArray array = progress.optJSONArray("agents");
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String id = object.optString("id").trim();
            if (id.length() == 0) {
                continue;
            }
            String status = object.optString("status", "waiting");
            boolean error = object.optBoolean("error") || "error".equals(status);
            values.put(id, new AgentSummary(
                    object.optString("output"),
                    object.optString("thinking"),
                    status,
                    error
            ));
        }
        return values;
    }

    private HashMap<String, AgentSummary> parseResult(ToolResult result) {
        HashMap<String, AgentSummary> values = new HashMap<>();
        if (result == null || result.getContent().length() == 0) {
            return values;
        }
        String[] sections = result.getContent().split("\\n\\n## ");
        for (String section : sections) {
            String text = section.trim();
            if (text.length() == 0 || text.startsWith("Agent 流水线完成")) {
                continue;
            }
            int titleEnd = text.indexOf('\n');
            String title = titleEnd >= 0 ? text.substring(0, titleEnd) : text;
            String id = title;
            int dot = title.indexOf(" · ");
            if (dot >= 0) {
                id = title.substring(0, dot).trim();
            }
            if (id.length() == 0) {
                continue;
            }
            boolean error = text.contains("\n状态: error");
            String output = text;
            int outputStart = nthLineIndex(text, 4);
            if (outputStart >= 0 && outputStart < text.length()) {
                output = text.substring(outputStart).trim();
            }
            values.put(id, new AgentSummary(output, "", error ? "error" : "done", error));
        }
        return values;
    }

    private int nthLineIndex(String text, int lineCount) {
        int index = 0;
        for (int i = 0; i < lineCount; i++) {
            index = text.indexOf('\n', index);
            if (index < 0) {
                return -1;
            }
            index++;
        }
        return index;
    }

    private int doneCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if ("done".equals(summary.status) && !summary.error) {
                count++;
            }
        }
        return count;
    }

    private int failedCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if (summary.error) {
                count++;
            }
        }
        return count;
    }

    private int runningCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if ("running".equals(summary.status)) {
                count++;
            }
        }
        return count;
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value) || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.length() == 0 ? "explore" : value;
    }

    private String typeLabel(String type) {
        return "explore".equals(type) ? "探索" : "编程";
    }

    private static final class AgentSummary {
        private final String output;
        private final String thinking;
        private final String status;
        private final boolean error;

        AgentSummary(String output, String thinking, String status, boolean error) {
            this.output = output == null ? "" : output;
            this.thinking = thinking == null ? "" : thinking;
            this.status = status == null || status.length() == 0 ? "waiting" : status;
            this.error = error;
        }
    }
}
