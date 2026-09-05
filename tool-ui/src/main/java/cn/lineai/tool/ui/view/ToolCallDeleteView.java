package cn.lineai.tool.ui;

import android.content.Context;
import android.graphics.Typeface;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Deletion details are disclosed here; the active request is confirmed in the bottom composer slot. */
public final class ToolCallDeleteView extends BaseToolCallView implements ToolCallCardView, ToolCallExpansion {
    private ToolCall call;
    private ToolResult result;
    private String projectPath = "";
    private String key = "delete";
    private Map<String, Boolean> expansion = new HashMap<>();
    public ToolCallDeleteView(Context context) { super(context); setBackground(null); }
    @Override public void setToolReviewListener(ToolReviewListener listener) {}
    @Override public void setProjectPath(String path) { projectPath = path == null ? "" : path; }
    @Override public void setExpansionState(Map<String, Boolean> state, String key) {
        if (state != null) expansion = state;
        this.key = key; if (call != null) render();
    }
    @Override public void bind(ToolCall call, ToolResult result) { this.call = call; this.result = result; render(); }
    private void render() {
        removeAllViews();
        JSONObject input = ToolCallUtils.parseInput(call);
        ArrayList<String> paths = new ArrayList<>(); JSONArray array = input.optJSONArray("paths");
        if (array != null) for (int i = 0; i < array.length(); i++) paths.add(array.optString(i));
        for (String name : new String[]{"file_path", "path"}) if (!input.optString(name).isEmpty()) paths.add(input.optString(name));
        String state = result == null ? "running" : result.getReviewState();
        boolean failed = result != null && result.isError();
        int status = "pending".equals(state) ? R.string.tool_call_status_pending_review
                : "running".equals(state) || "accepted".equals(state) && result.getContent().isEmpty() ? R.string.tool_call_status_running
                : failed ? R.string.tool_call_status_failed : R.string.tool_call_status_done;
        LinearLayout header = new LinearLayout(getContext()); header.setGravity(Gravity.CENTER_VERTICAL); header.setMinimumHeight(dp(48));
        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.TRASH_2); icon.setIconSizeDp(24, 16);
        icon.setIconColor(failed ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY); icon.setClickable(false);
        header.addView(icon, new LayoutParams(dp(24), dp(32)));
        TextView label = LineTheme.text(getContext(), getContext().getString(R.string.common_delete) + " · " + getContext().getString(status)
                + getContext().getString(R.string.tool_call_delete_count_suffix, paths.size()), 14, failed ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(0, -2, 1); labelParams.leftMargin = dp(6); header.addView(label, labelParams);
        boolean open = Boolean.TRUE.equals(expansion.get(key));
        IconButtonView arrow = new IconButtonView(getContext(), open ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        arrow.setIconSizeDp(24, 14); arrow.setIconColor(LineTheme.TEXT_SECONDARY); arrow.setClickable(false);
        header.addView(arrow, new LayoutParams(dp(24), dp(32)));
        header.setFocusable(true); header.setOnClickListener(v -> { expansion.put(key, !open); render(); }); addView(header, new LayoutParams(-1, -2));
        if (!open) return;
        StringBuilder value = new StringBuilder(input.optString("reason").trim());
        for (String path : paths) {
            if (value.length() > 0) value.append('\n');
            value.append(ToolCallUtils.workspaceDisplayPath(projectPath, path));
        }
        if (failed && !result.getContent().isEmpty()) value.append("\n\n").append(result.getContent());
        TextView details = LineTheme.text(getContext(), value.toString(), 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        details.setTextIsSelectable(true); details.setLineSpacing(dp(7), 1); LineTheme.padding(details, 14, 12, 14, 12);
        BoundedScrollView scroll = new BoundedScrollView(getContext(), 200);
        scroll.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        scroll.addView(details, new android.widget.ScrollView.LayoutParams(-1, -2)); addView(scroll, new LayoutParams(-1, -2));
    }
    private int dp(int value) { return LineTheme.dp(getContext(), value); }
}
