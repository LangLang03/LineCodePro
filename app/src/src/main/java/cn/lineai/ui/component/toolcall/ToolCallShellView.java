package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import org.json.JSONObject;

public final class ToolCallShellView extends LinearLayout {
    private static final int COLLAPSED_LINE_COUNT = 4;
    private static final int EXPANDED_OUTPUT_LIMIT = 64 * 1024;
    private static final int EXPANDED_HEAD_LIMIT = 24 * 1024;
    private static final int EXPANDED_TAIL_LIMIT = 36 * 1024;

    private final IconButtonView terminalIcon;
    private final TextView commandView;
    private final ProgressBar progressBar;
    private final LinearLayout viewCommandButton;
    private final LinearLayout confirmSection;
    private final View confirmDivider;
    private final LinearLayout outputSection;
    private final View outputDivider;
    private final LinearLayout outputHeader;
    private final IconButtonView statusIcon;
    private final TextView outputTitle;
    private final TextView outputMeta;
    private final IconButtonView expandIcon;
    private final TextView collapsedOutputView;
    private final BoundedScrollView expandedScrollView;
    private final TextView expandedOutputView;
    private ToolReviewListener toolReviewListener;
    private String toolCallId = "";
    private String command = "";
    private boolean expanded;
    private boolean autoExpanded;
    private boolean canExpand;

    public ToolCallShellView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.rounded(context, LineTheme.CODE_BG, 6));
        LineTheme.padding(this, LineTheme.MD, LineTheme.XS, LineTheme.MD, LineTheme.XS);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        terminalIcon = new IconButtonView(context, IconButtonView.TERMINAL);
        terminalIcon.setIconSizeDp(14, 14);
        terminalIcon.setClickable(false);
        header.addView(terminalIcon, new LayoutParams(LineTheme.dp(context, 14), LineTheme.dp(context, 14)));

        commandView = LineTheme.text(context, "", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        commandView.setTypeface(Typeface.MONOSPACE);
        commandView.setSingleLine(true);
        commandView.setEllipsize(TextUtils.TruncateAt.END);
        commandView.setClickable(true);
        commandView.setOnClickListener(v -> openFullCommand());
        LayoutParams commandParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        commandParams.leftMargin = LineTheme.dp(context, 6);
        commandParams.rightMargin = LineTheme.dp(context, 6);
        header.addView(commandView, commandParams);

        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        LayoutParams progressParams = new LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        progressParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        header.addView(progressBar, progressParams);

        viewCommandButton = smallActionButton(
                context,
                IconButtonView.EXTERNAL_LINK,
                "完整",
                LineTheme.SURFACE_LIGHT,
                LineTheme.BORDER_LIGHT,
                LineTheme.TEXT_SECONDARY,
                true
        );
        viewCommandButton.setOnClickListener(v -> openFullCommand());
        header.addView(viewCommandButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 26)));

        confirmDivider = divider(context);
        addView(confirmDivider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        confirmSection = buildConfirmSection(context);
        addView(confirmSection, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        outputDivider = divider(context);
        addView(outputDivider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        outputSection = new LinearLayout(context);
        outputSection.setOrientation(VERTICAL);
        addView(outputSection, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        outputHeader = new LinearLayout(context);
        outputHeader.setOrientation(HORIZONTAL);
        outputHeader.setGravity(Gravity.CENTER_VERTICAL);
        outputHeader.setMinimumHeight(LineTheme.dp(context, 22));
        outputHeader.setClickable(true);
        outputHeader.setOnClickListener(v -> {
            if (canExpand) {
                expanded = !expanded;
                updateExpandedState();
            }
        });
        outputSection.addView(outputHeader, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        statusIcon = new IconButtonView(context, IconButtonView.CHECK);
        statusIcon.setIconSizeDp(12, 12);
        statusIcon.setClickable(false);
        outputHeader.addView(statusIcon, new LayoutParams(LineTheme.dp(context, 12), LineTheme.dp(context, 12)));

        outputTitle = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.SUCCESS, Typeface.BOLD);
        LayoutParams titleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = LineTheme.dp(context, 5);
        outputHeader.addView(outputTitle, titleParams);

        outputMeta = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        outputMeta.setGravity(Gravity.END);
        LayoutParams metaParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = LineTheme.dp(context, 5);
        outputHeader.addView(outputMeta, metaParams);

        expandIcon = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        expandIcon.setIconColor(LineTheme.TEXT_TERTIARY);
        expandIcon.setIconSizeDp(13, 13);
        expandIcon.setClickable(false);
        outputHeader.addView(expandIcon, new LayoutParams(LineTheme.dp(context, 13), LineTheme.dp(context, 13)));

        collapsedOutputView = outputText(context);
        LayoutParams collapsedParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        collapsedParams.topMargin = LineTheme.dp(context, 4);
        outputSection.addView(collapsedOutputView, collapsedParams);

        expandedScrollView = new BoundedScrollView(context, 280);
        expandedScrollView.setFillViewport(false);
        expandedScrollView.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE, 8, LineTheme.CODE_BORDER));
        expandedScrollView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            boolean active = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
            view.getParent().requestDisallowInterceptTouchEvent(active);
            if (action == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return false;
        });
        LineTheme.padding(expandedScrollView, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        expandedOutputView = outputText(context);
        expandedScrollView.addView(expandedOutputView, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams expandedParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        expandedParams.topMargin = LineTheme.dp(context, 4);
        outputSection.addView(expandedScrollView, expandedParams);
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        toolCallId = toolCall == null ? "" : toolCall.getId();
        command = input.optString("command", "");
        String reviewState = result == null ? "" : result.getReviewState();
        boolean pending = "pending".equals(reviewState);
        boolean streaming = "running".equals(reviewState);
        boolean error = result != null && result.isError();
        String content = result == null ? "" : result.getContent();
        String displayResult = streaming ? (content.length() == 0 ? "正在执行..." : content) : content;

        if (streaming && !autoExpanded) {
            expanded = true;
            autoExpanded = true;
        }

        int headerColor = error ? LineTheme.DANGER : streaming ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY;
        terminalIcon.setIconColor(headerColor);
        commandView.setText(command.length() == 0 ? "shell_execute" : command);
        commandView.setTextColor(headerColor);
        progressBar.setVisibility(streaming ? VISIBLE : GONE);
        viewCommandButton.setVisibility(command.length() > 0 ? VISIBLE : GONE);

        confirmDivider.setVisibility(pending ? VISIBLE : GONE);
        confirmSection.setVisibility(pending ? VISIBLE : GONE);
        outputDivider.setVisibility(displayResult.length() > 0 ? VISIBLE : GONE);
        outputSection.setVisibility(displayResult.length() > 0 ? VISIBLE : GONE);
        if (displayResult.length() > 0) {
            bindOutput(displayResult, streaming, error);
        }
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    private LinearLayout buildConfirmSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(section, 0, LineTheme.XS, 0, 0);

        LinearLayout auto = smallActionButton(context, IconButtonView.ZAP, "自动运行",
                LineTheme.SURFACE_LIGHT, LineTheme.BORDER_LIGHT, LineTheme.TEXT_SECONDARY, true);
        auto.setMinimumWidth(LineTheme.dp(context, 78));
        auto.setOnClickListener(v -> review("session_auto"));
        section.addView(auto, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 30)));

        View spacer = new View(context);
        section.addView(spacer, new LayoutParams(0, 1, 1f));

        LinearLayout cancel = smallActionButton(context, IconButtonView.CLOSE, "跳过",
                LineTheme.CODE_BORDER, LineTheme.CODE_BORDER, LineTheme.TEXT_SECONDARY, false);
        LayoutParams cancelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 30));
        cancelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        cancel.setMinimumWidth(LineTheme.dp(context, 50));
        cancel.setOnClickListener(v -> review("rejected"));
        section.addView(cancel, cancelParams);

        LinearLayout run = smallActionButton(context, IconButtonView.PLAY, "运行",
                LineTheme.ACCENT, LineTheme.ACCENT, LineTheme.TEXT_ON_COLOR, false);
        LayoutParams runParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 30));
        runParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        run.setMinimumWidth(LineTheme.dp(context, 50));
        run.setOnClickListener(v -> review("accepted"));
        section.addView(run, runParams);
        return section;
    }

    private void bindOutput(String displayResult, boolean streaming, boolean error) {
        int lineCount = lineCount(displayResult);
        canExpand = lineCount > COLLAPSED_LINE_COUNT || displayResult.length() > 240 || streaming;
        int statusColor = error ? LineTheme.DANGER : LineTheme.SUCCESS;
        statusIcon.setVisibility(streaming ? GONE : VISIBLE);
        outputTitle.setVisibility(streaming ? GONE : VISIBLE);
        statusIcon.setIconType(error ? IconButtonView.CIRCLE_ALERT : IconButtonView.CHECK);
        statusIcon.setIconColor(statusColor);
        outputTitle.setText(error ? "执行失败" : "执行完成");
        outputTitle.setTextColor(statusColor);
        outputMeta.setText(getResources().getString(R.string.shell_output_line_count, lineCount));
        expandIcon.setVisibility(canExpand ? VISIBLE : GONE);

        collapsedOutputView.setText(collapsePreview(displayResult));
        collapsedOutputView.setTextColor(error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        expandedOutputView.setText(expandedPreview(displayResult));
        expandedOutputView.setTextColor(error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        updateExpandedState();
    }

    private void updateExpandedState() {
        expandIcon.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        expandIcon.setIconColor(LineTheme.TEXT_TERTIARY);
        expandedScrollView.setVisibility(expanded ? VISIBLE : GONE);
        collapsedOutputView.setVisibility(expanded ? GONE : VISIBLE);
    }

    private void review(String state) {
        if (toolReviewListener != null) {
            toolReviewListener.onToolReview(toolCallId, state, "");
        }
    }

    private void openFullCommand() {
        if (toolReviewListener != null && command.length() > 0) {
            toolReviewListener.onViewShellCommand(command);
        }
    }

    private LinearLayout smallActionButton(
            Context context,
            int iconType,
            String label,
            int backgroundColor,
            int borderColor,
            int textColor,
            boolean stroke
    ) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(stroke
                ? LineTheme.roundedStroke(context, backgroundColor, 8, borderColor)
                : LineTheme.rounded(context, backgroundColor, 8));
        LineTheme.padding(button, LineTheme.SM, 0, LineTheme.SM, 0);

        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(textColor);
        icon.setIconSizeDp(13, 13);
        icon.setClickable(false);
        button.addView(icon, new LayoutParams(LineTheme.dp(context, 13), LineTheme.dp(context, 13)));

        TextView text = LineTheme.text(context, label, LineTheme.FONT_XS, textColor, Typeface.BOLD);
        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, 3);
        button.addView(text, textParams);
        return button;
    }

    private TextView outputText(Context context) {
        TextView text = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        text.setTypeface(Typeface.MONOSPACE);
        text.setLineSpacing(LineTheme.dp(context, 3), 1f);
        text.setTextIsSelectable(true);
        return text;
    }

    private View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, 1);
        params.topMargin = LineTheme.dp(context, LineTheme.XS);
        divider.setLayoutParams(params);
        return divider;
    }

    private int lineCount(String value) {
        if (value == null || value.length() == 0) {
            return 0;
        }
        return value.split("\\r?\\n", -1).length;
    }

    private String collapsePreview(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String[] lines = value.split("\\r?\\n", -1);
        int start = Math.max(0, lines.length - COLLAPSED_LINE_COUNT);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        String tail = builder.toString();
        return tail.length() > 320 ? tail.substring(tail.length() - 320) : tail;
    }

    private String expandedPreview(String value) {
        if (value == null || value.length() <= EXPANDED_OUTPUT_LIMIT) {
            return value == null ? "" : value;
        }
        String head = value.substring(0, EXPANDED_HEAD_LIMIT);
        String tail = value.substring(value.length() - EXPANDED_TAIL_LIMIT);
        int folded = value.length() - head.length() - tail.length();
        return head + "\n\n[LineCode 已折叠 " + folded + " 个字符的 shell 输出]\n\n" + tail;
    }

    private static final class BoundedScrollView extends ScrollView {
        private final int maxHeightDp;

        BoundedScrollView(Context context, int maxHeightDp) {
            super(context);
            this.maxHeightDp = maxHeightDp;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = LineTheme.dp(getContext(), maxHeightDp);
            int cappedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeightSpec);
        }
    }
}
