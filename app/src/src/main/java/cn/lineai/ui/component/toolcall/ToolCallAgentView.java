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
import cn.lineai.ui.component.ThinkingBlockView;
import cn.lineai.ui.markdown.MarkdownView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallAgentView extends LinearLayout {
    private boolean expanded = true;
    private ToolCall lastToolCall;
    private ToolResult lastResult;
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;

    public ToolCallAgentView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        lastToolCall = toolCall;
        lastResult = result;
        removeAllViews();

        JSONObject input = ToolCallUtils.parseInput(toolCall);
        JSONObject progress = progressPayload(result);
        String name = progress == null ? input.optString("description", "Agent") : progress.optString("description", input.optString("description", "Agent"));
        String type = normalizeType(progress == null ? input.optString("type") : progress.optString("type", input.optString("type")));
        String progressStatus = progress == null ? "" : progress.optString("status");
        boolean running = progress != null && ("running".equals(progressStatus) || "waiting_unlock".equals(progressStatus));
        boolean complete = result != null && !running;
        boolean error = result != null && (result.isError() || "error".equals(progressStatus));
        String output = progress == null ? outputText(result) : progress.optString("output");
        String thinking = progress == null ? "" : progress.optString("thinking");
        JSONArray nestedToolCalls = progress == null ? null : progress.optJSONArray("tool_calls");
        int toolCount = progress == null ? toolCount(result) : progress.optInt("tool_call_count", nestedToolCalls == null ? 0 : nestedToolCalls.length());
        String status = error ? "失败" : complete ? "完成" : "运行中";
        int typeColor = "explore".equals(type) ? LineTheme.ACCENT : LineTheme.DANGER;
        int statusColor = error ? LineTheme.DANGER : complete ? LineTheme.SUCCESS : LineTheme.ACCENT;

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
        if (toolCount > 0) {
            meta.addView(pill(toolCount + " 工具", LineTheme.TEXT_TERTIARY, LineTheme.SURFACE_LIGHT, LineTheme.CODE_BORDER));
        }
        LayoutParams metaParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = LineTheme.dp(getContext(), 3);
        titleBlock.addView(meta, metaParams);

        if (!complete) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            header.addView(bar, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        } else {
            IconButtonView statusIcon = new IconButtonView(getContext(), error ? IconButtonView.CIRCLE_X : IconButtonView.CIRCLE_CHECK);
            statusIcon.setIconColor(statusColor);
            statusIcon.setIconSizeDp(18, 13);
            statusIcon.setClickable(false);
            header.addView(statusIcon, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        }
        TextView statusText = LineTheme.text(getContext(), status, LineTheme.FONT_XS, error ? LineTheme.DANGER : LineTheme.TEXT_TERTIARY, Typeface.BOLD);
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
            ThinkingBlockView thinkingView = new ThinkingBlockView(getContext());
            thinkingView.bind(toolCall == null ? "" : toolCall.getId(), thinking, !complete, false, true);
            LayoutParams thinkingParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            thinkingParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.SM);
            content.addView(thinkingView, thinkingParams);
        }
        if (!complete && output.trim().length() == 0) {
            TextView runningText = LineTheme.text(getContext(), "正在执行任务...", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            content.addView(runningText, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else if (output.length() > 0) {
            MarkdownView markdownView = new MarkdownView(getContext());
            markdownView.setMarkdown(output);
            content.addView(markdownView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            TextView empty = LineTheme.text(getContext(), error ? "执行失败" : "任务完成", LineTheme.FONT_XS,
                    error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            content.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addNestedToolCalls(content, nestedToolCalls);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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
        return "explore".equals(type) ? "探索" : "编程";
    }

    private int toolCount(ToolResult result) {
        JSONObject progress = progressPayload(result);
        if (progress != null) {
            JSONArray calls = progress.optJSONArray("tool_calls");
            return progress.optInt("tool_call_count", calls == null ? 0 : calls.length());
        }
        if (result == null) {
            return 0;
        }
        return intAfterLabel(result.getContent(), "工具调用:");
    }

    private String outputText(ToolResult result) {
        JSONObject progress = progressPayload(result);
        if (progress != null) {
            return progress.optString("output");
        }
        if (result == null) {
            return "";
        }
        String content = result.getContent();
        int index = content.indexOf("输出:\n");
        if (index >= 0) {
            return content.substring(index + "输出:\n".length()).trim();
        }
        return content.trim();
    }

    private JSONObject progressPayload(ToolResult result) {
        if (result == null || result.getContent().trim().length() == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(result.getContent());
            return object.optBoolean("linecode_agent_progress") ? object : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addNestedToolCalls(LinearLayout content, JSONArray nestedToolCalls) {
        if (nestedToolCalls == null || nestedToolCalls.length() == 0) {
            return;
        }
        TextView label = LineTheme.text(getContext(), "工具调用", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(label, labelParams);

        for (int i = 0; i < nestedToolCalls.length(); i++) {
            JSONObject item = nestedToolCalls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ToolCall call = new ToolCall(
                    item.optString("id"),
                    item.optString("name"),
                    item.optString("arguments", "{}")
            );
            ToolResult result = null;
            JSONObject resultObject = item.optJSONObject("result");
            if (resultObject != null) {
                result = new ToolResult(
                        call.getId(),
                        call.getName(),
                        resultObject.optString("content"),
                        resultObject.optBoolean("is_error"),
                        resultObject.optString("diff_id"),
                        resultObject.optString("review_state"),
                        resultObject.optString("review_message")
                );
            }
            ToolCallBlockView block = new ToolCallBlockView(getContext());
            block.setProjectPath(projectPath);
            block.setToolReviewListener(toolReviewListener);
            block.bind(call, result);
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
