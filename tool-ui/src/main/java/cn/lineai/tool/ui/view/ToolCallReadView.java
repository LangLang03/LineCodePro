package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import org.json.JSONObject;

public final class ToolCallReadView extends BaseToolCallView implements ToolCallCardView {
    private String projectPath = "";

    public ToolCallReadView(Context context) {
        super(context);
    }

    @Override
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    @Override
    public void setToolReviewListener(ToolReviewListener listener) {
        // Read view does not use tool review
    }

    @Override
    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String label = ToolCallUtils.displayInputLabel(getContext(), name, input, projectPath);
        // 简化进度圈逻辑：直接根据结果决定最终状态
        TerminalStatus status = computeTerminalStatus(result);
        boolean running = status == TerminalStatus.RUNNING;
        boolean error = status == TerminalStatus.FAILED;
        boolean unknown = status == TerminalStatus.UNKNOWN;
        boolean complete = !running;
        String actionLabel = actionLabel(name);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(getContext(), 36));
        LineTheme.padding(header, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        int headerIconColor = error ? LineTheme.DANGER
                : running ? LineTheme.ACCENT
                : unknown ? LineTheme.TEXT_TERTIARY
                : LineTheme.TEXT_SECONDARY;
        IconButtonView icon = new IconButtonView(getContext(), iconFor(name));
        icon.setIconColor(headerIconColor);
        icon.setIconSizeDp(24, 12);
        icon.setClickable(false);
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));

        // 单行结构：动作标签（加粗）+ 目标路径，省掉第二行
        TextView path = new TextView(getContext());
        path.setTextSize(LineTheme.FONT_SM);
        path.setIncludeFontPadding(false);
        path.setTypeface(Typeface.MONOSPACE);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int actionColor = error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY;
        builder.append(actionLabel);
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, actionLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(actionColor), 0, actionLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append("  ");
        int pathStart = builder.length();
        builder.append(label);
        builder.setSpan(new ForegroundColorSpan(error ? LineTheme.DANGER : LineTheme.TEXT),
                pathStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        path.setText(builder);
        path.setSingleLine(true);
        path.setHorizontallyScrolling(true);
        HorizontalScrollView pathScroll = horizontalPathScroll(path);
        LayoutParams pathParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        pathParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        pathParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(pathScroll, pathParams);

        View statusViewInstance = statusView(running);
        if (complete && statusViewInstance instanceof IconButtonView) {
            IconButtonView statusIcon = (IconButtonView) statusViewInstance;
            if (error) {
                statusIcon.setIconType(IconButtonView.CLOSE);
                statusIcon.setIconColor(LineTheme.DANGER);
            } else if (unknown) {
                // 未知情况：使用时钟图标表示等待结果，颜色用 TEXT_TERTIARY
                statusIcon.setIconType(IconButtonView.CLOCK_3);
                statusIcon.setIconColor(LineTheme.TEXT_TERTIARY);
            }
        }
        header.addView(statusViewInstance, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (running && result != null && result.getContent().length() > 0) {
            addMessageRow(this, IconButtonView.LOADER, result.getContent(), LineTheme.TEXT_SECONDARY,
                    contentMaxHeightDp(result.getContent()));
        } else if (error && result.getContent().length() > 0) {
            addMessageRow(this, IconButtonView.CLOSE, result.getContent(), LineTheme.DANGER,
                    contentMaxHeightDp(result.getContent()));
        }
    }

    private int contentMaxHeightDp(String content) {
        if (content == null || content.trim().length() == 0) {
            return 0;
        }
        int lines = 0;
        int max = 0;
        int n = content.length();
        for (int i = 0; i < n && lines <= 8; i++) {
            if (content.charAt(i) == '\n') {
                lines++;
                max = 0;
            } else {
                max++;
                if (max > 90) {
                    lines++;
                    max = 0;
                }
            }
        }
        return (content.length() > 300 || lines > 8) ? 220 : 0;
    }

    private String actionLabel(String name) {
        cn.lineai.tool.ui.ToolInfoResolver resolver = cn.lineai.tool.ui.ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            String actionName = resolver.getActionName(getContext(), name);
            if (actionName != null) {
                return actionName;
            }
        }
        return getContext().getString(R.string.tool_call_action_read);
    }

    private int iconFor(String name) {
        cn.lineai.tool.ui.ToolInfoResolver resolver = cn.lineai.tool.ui.ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            int icon = resolver.getActionIcon(name);
            if (icon != 0) {
                return icon;
            }
        }
        return IconButtonView.EXPAND;
    }
}
