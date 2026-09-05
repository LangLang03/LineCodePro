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
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

/** Read operations are status rows. File contents are deliberately not interactive. */
public final class ToolCallReadView extends BaseToolCallView implements ToolCallCardView {
    private final TextView label;
    private final IconButtonView icon;
    private final ToolErrorView errorOutput;
    private String projectPath = "";

    public ToolCallReadView(Context context) {
        super(context);
        setBackground(null);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(context, 48));
        icon = new IconButtonView(context, IconButtonView.FILE);
        icon.setIconSizeDp(24, 16);
        icon.setClickable(false);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 32)));
        label = LineTheme.text(context, "", 14, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        params.leftMargin = LineTheme.dp(context, 6);
        header.addView(label, params);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        errorOutput = new ToolErrorView(context);
        addView(errorOutput, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setClickable(false);
    }
    @Override public void setProjectPath(String path) { projectPath = path == null ? "" : path; }
    @Override public void setToolReviewListener(ToolReviewListener listener) {}
    @Override public void bind(ToolCall call, ToolResult result) {
        String name = call == null ? "" : call.getName();
        String path = ToolCallUtils.displayInputLabel(getContext(), name, ToolCallUtils.parseInput(call), projectPath);
        int status = result == null || "running".equals(result.getReviewState()) ? R.string.tool_call_status_running
                : result.isError() ? R.string.tool_call_status_failed : R.string.tool_call_read_done;
        label.setText(getContext().getString(status) + "  " + path);
        int color = result != null && result.isError() ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY;
        label.setTextColor(color); icon.setIconColor(color);
        errorOutput.bind(result);
    }
}
