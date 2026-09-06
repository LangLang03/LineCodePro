package cn.lineai.tool.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Map;

/** Unknown and MCP extension tools use the same disclosure rhythm as built-in tools. */
public final class ToolCallGenericView extends BaseToolCallView implements ToolCallCardView, ToolCallExpansion {
    private final String fallbackLabel;
    private Map<String, Boolean> expansion;
    private String key = "";
    private boolean localExpanded;
    private ToolCall call;
    private ToolResult result;

    public ToolCallGenericView(Context context, String label) {
        super(context);
        fallbackLabel = label == null || label.trim().isEmpty()
                ? context.getString(R.string.tool_call_generic_mcp)
                : label;
    }

    @Override
    public void setExpansionState(Map<String, Boolean> state, String key) {
        expansion = state;
        this.key = key == null ? "" : key;
    }

    @Override
    public void bind(ToolCall call, ToolResult result) {
        this.call = call;
        this.result = result;
        removeAllViews();

        boolean open = isExpanded();
        boolean error = result != null && result.isError();
        int color = error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY;

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(getContext(), 48));

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.MCP);
        icon.setIconColor(color);
        icon.setIconSizeDp(24, 16);
        icon.setClickable(false);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LayoutParams(
                LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 32)));

        String name = call == null || call.getName().trim().isEmpty() ? fallbackLabel : call.getName();
        int status = error ? R.string.tool_call_status_failed
                : isTerminal(result) ? R.string.tool_call_status_done : R.string.tool_call_status_running;
        TextView title = LineTheme.text(getContext(),
                getContext().getString(status) + "  " + name,
                14, color, Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), 6);
        header.addView(title, titleParams);

        IconButtonView arrow = new IconButtonView(getContext(),
                open ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        arrow.setIconSizeDp(24, 14);
        arrow.setIconColor(LineTheme.TEXT_SECONDARY);
        arrow.setClickable(false);
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(arrow, new LayoutParams(
                LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 32)));
        header.setFocusable(true);
        header.setOnClickListener(v -> {
            if (expansion != null) {
                expansion.put(this.key, !isExpanded());
            } else {
                localExpanded = !localExpanded;
            }
            bind(this.call, this.result);
        });
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (open) {
            addDetails(error);
        }
    }

    private void addDetails(boolean error) {
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);

        String input = ToolCallUtils.prettyJson(ToolCallUtils.parseInput(call));
        boolean hasInput = !"{}".equals(input);
        if (hasInput) {
            addSection(content, R.string.tool_call_input, input, LineTheme.TEXT_SECONDARY, false);
        }
        if (result != null && !result.getContent().isEmpty()) {
            String raw = result.getContent();
            String output = AgentToolResultDisplay.progressPayload(raw) != null
                    ? AgentToolResultDisplay.displayOutput(raw) : raw;
            addSection(content, R.string.tool_call_output, output,
                    error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, hasInput);
        }

        BoundedScrollView detail = new BoundedScrollView(getContext(), 240);
        detail.setFillViewport(false);
        detail.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        detail.addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(detail, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addSection(LinearLayout parent, int titleRes, String value, int color, boolean dividerAbove) {
        if (dividerAbove) {
            View divider = new View(getContext());
            divider.setBackgroundColor(LineTheme.CODE_BORDER);
            parent.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(VERTICAL);
        LineTheme.padding(section, 14, 12, 14, 12);

        TextView heading = LineTheme.text(getContext(), getContext().getString(titleRes),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        section.addView(heading, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        String preview = value == null ? "" : value.length() > 65536
                ? value.substring(0, 65536) + "…" : value;
        TextView body = LineTheme.text(getContext(), preview,
                LineTheme.FONT_SM, color, Typeface.NORMAL);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setLineSpacing(LineTheme.dp(getContext(), 4), 1f);
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = LineTheme.dp(getContext(), 4);
        section.addView(body, bodyParams);
        parent.addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private boolean isExpanded() {
        return expansion == null ? localExpanded : Boolean.TRUE.equals(expansion.get(key));
    }

    @Override
    public void updateContent(ToolCall call, ToolResult result) {
        bind(call, result);
    }

    @Override
    public void setToolReviewListener(ToolReviewListener listener) {
    }

    @Override
    public void setProjectPath(String path) {
    }
}
