package cn.lineai.tool.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.HashMap;
import java.util.Map;

public final class ToolCallShellView extends BaseToolCallView implements ToolCallCardView, ToolCallExpansion {
    private final TextView label;
    private final IconButtonView arrow;
    private final TextView output;
    private final BoundedScrollView detail;
    private Map<String, Boolean> expansion = new HashMap<>();
    private String key = "shell";
    private String value = "";

    public ToolCallShellView(Context context) {
        super(context); setBackground(null);
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(context, 48));
        IconButtonView icon = new IconButtonView(context, IconButtonView.TERMINAL);
        icon.setIconSizeDp(24, 16); icon.setIconColor(LineTheme.TEXT_SECONDARY); icon.setClickable(false);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 32)));
        label = LineTheme.text(context, "", 14, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1); labelParams.leftMargin = LineTheme.dp(context, 6);
        header.addView(label, labelParams);
        arrow = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT); arrow.setIconSizeDp(24, 14);
        arrow.setIconColor(LineTheme.TEXT_SECONDARY); arrow.setClickable(false);
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(arrow, new LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 32)));
        header.setFocusable(true); header.setOnClickListener(v -> { expansion.put(key, !isExpanded()); renderExpansion(); });
        addView(header, new LayoutParams(-1, -2));
        detail = new BoundedScrollView(context, 240);
        detail.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        output = LineTheme.text(context, "", 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        output.setTypeface(Typeface.MONOSPACE); output.setLineSpacing(LineTheme.dp(context, 6), 1);
        output.setTextIsSelectable(true); LineTheme.padding(output, 14, 12, 14, 12);
        detail.addView(output, new android.widget.ScrollView.LayoutParams(-1, -2));
        addView(detail, new LayoutParams(-1, -2)); renderExpansion();
    }
    @Override public void bind(ToolCall call, ToolResult result) {
        String command = ToolCallUtils.parseInput(call).optString("command", "");
        int status = result == null || "running".equals(result.getReviewState()) ? R.string.tool_call_status_running
                : "pending".equals(result.getReviewState()) ? R.string.tool_call_status_pending_review
                : result.isError() ? R.string.tool_call_status_failed : R.string.tool_call_status_done;
        label.setText(getContext().getString(status) + "  " + command);
        label.setTextColor(result != null && result.isError() ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        output.setTextColor(result != null && result.isError() ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        String content = result == null || "pending".equals(result.getReviewState()) ? "" : result.getContent();
        if (content.length() > 64 * 1024) {
            int omitted = content.length() - 60 * 1024;
            content = content.substring(0, 24 * 1024) + "\n\n" + getContext().getString(R.string.tool_call_shell_folded, omitted)
                    + "\n\n" + content.substring(content.length() - 36 * 1024);
        }
        value = "$ " + command + (content.isEmpty() ? "" : "\n\n" + content);
        renderExpansion();
    }
    @Override public void setExpansionState(Map<String, Boolean> state, String key) {
        if (state != null) expansion = state;
        this.key = key; renderExpansion();
    }
    private boolean isExpanded() { return Boolean.TRUE.equals(expansion.get(key)); }
    private void renderExpansion() {
        boolean open = isExpanded(); detail.setVisibility(open ? VISIBLE : GONE);
        arrow.setIconType(open ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        if (open && !value.contentEquals(output.getText())) output.setText(value);
    }
    @Override public void setProjectPath(String path) {}
    @Override public void setToolReviewListener(ToolReviewListener listener) {}
}
