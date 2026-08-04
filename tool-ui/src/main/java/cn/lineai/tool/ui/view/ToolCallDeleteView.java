package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallDeleteView extends BaseToolCallView implements ToolCallCardView {
    private ToolReviewListener toolReviewListener;
    private String projectPath = "";

    public ToolCallDeleteView(Context context) {
        super(context);
        setBackground(LineTheme.roundedStroke(context, LineTheme.DANGER_MUTED, 8, LineTheme.DANGER_MUTED_2));
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        ArrayList<String> paths = paths(input);
        String reason = input.optString("reason").trim();
        if (reason.length() == 0) {
            reason = getContext().getString(R.string.tool_call_delete_no_reason);
        }
        String state = result == null ? "pending" : result.getReviewState();
        boolean pending = result == null || state.length() == 0 || "pending".equals(state);
        boolean accepted = "accepted".equals(state);
        boolean rejected = "rejected".equals(state);
        boolean complete = result != null && result.getContent().length() > 0 && !pending && !rejected;
        boolean error = result != null && result.isError();

        addHeader(reason, description(paths.size(), pending, accepted, rejected, complete, error), pending || rejected || error);
        addPathList(paths);
        if (pending) {
            addActions(toolCall, reason, paths);
        } else if (accepted && result != null && result.getContent().length() == 0) {
            addStateMessage(getContext().getString(R.string.tool_call_delete_pending), LineTheme.WARNING);
        } else if (rejected) {
            addStateMessage(result == null || result.getContent().length() == 0 ? getContext().getString(R.string.tool_call_delete_rejected) : result.getContent(), LineTheme.DANGER);
        } else if (result != null && result.getContent().length() > 0) {
            addStateMessage(result.getContent(), error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        }
    }

    private void addHeader(String reason, String description, boolean danger) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.TRASH_2);
        icon.setIconColor(LineTheme.DANGER);
        icon.setIconSizeDp(30, 15);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 8, LineTheme.DANGER_MUTED_2));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 30), LineTheme.dp(getContext(), 30)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(labels, labelParams);

        TextView title = LineTheme.text(getContext(), reason, LineTheme.FONT_SM, danger ? LineTheme.DANGER : LineTheme.TEXT, Typeface.BOLD);
        title.setSingleLine(false);
        labels.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView desc = LineTheme.text(getContext(), description, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(getContext(), 2);
        labels.addView(desc, descParams);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addPathList(ArrayList<String> paths) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.DANGER_MUTED_2);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(VERTICAL);
        LineTheme.padding(list, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        if (paths.isEmpty()) {
            list.addView(pathRow(getContext().getString(R.string.tool_call_delete_no_path)), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            for (String path : paths) {
                list.addView(pathRow(path), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private TextView pathRow(String path) {
        TextView row = LineTheme.text(getContext(), "- " + displayPath(path), LineTheme.FONT_XS, LineTheme.TEXT, Typeface.NORMAL);
        row.setTypeface(Typeface.MONOSPACE);
        row.setSingleLine(false);
        row.setTextIsSelectable(true);
        LineTheme.padding(row, 0, 2, 0, 2);
        return row;
    }

    private void addActions(ToolCall toolCall, String reason, ArrayList<String> paths) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.DANGER_MUTED_2);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        LinearLayout reject = button(IconButtonView.CLOSE, getContext().getString(R.string.tool_call_delete_reject), LineTheme.DANGER, LineTheme.SURFACE_LIGHT, LineTheme.DANGER_MUTED_2);
        reject.setOnClickListener(v -> {
            if (toolReviewListener != null) {
                toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "rejected", "");
            }
        });
        row.addView(reject, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 32)));

        View spacer = new View(getContext());
        row.addView(spacer, new LayoutParams(0, 1, 1f));

        LinearLayout accept = button(IconButtonView.TRASH_2, getContext().getString(R.string.tool_call_delete_confirm), LineTheme.TEXT_ON_COLOR, LineTheme.DANGER, LineTheme.DANGER);
        accept.setOnClickListener(v -> showConfirmDialog(toolCall, reason, paths));
        row.addView(accept, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 32)));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout button(int iconType, String label, int color, int background, int border) {
        LinearLayout button = new LinearLayout(getContext());
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.roundedStroke(getContext(), background, 6, border));
        button.setClickable(true);
        LineTheme.padding(button, LineTheme.SM, 2, LineTheme.SM, 2);
        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(color);
        icon.setIconSizeDp(18, 12);
        icon.setClickable(false);
        button.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        TextView text = LineTheme.text(getContext(), label, LineTheme.FONT_XS, color, Typeface.BOLD);
        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(getContext(), 2);
        button.addView(text, textParams);
        return button;
    }

    private void showConfirmDialog(ToolCall toolCall, String reason, ArrayList<String> paths) {
        StringBuilder message = new StringBuilder();
        message.append(reason).append("\n\n");
        for (String path : paths) {
            message.append("- ").append(displayPath(path)).append('\n');
        }
        new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.tool_call_delete_confirm_title))
                .setMessage(message.toString().trim())
                .setNegativeButton(getContext().getString(R.string.common_cancel), null)
                .setPositiveButton(getContext().getString(R.string.tool_call_delete_confirm_title), (dialog, which) -> {
                    if (toolReviewListener != null) {
                        toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "accepted", "");
                    }
                })
                .show();
    }

    private void addStateMessage(String text, int color) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.DANGER_MUTED_2);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        TextView message = LineTheme.text(getContext(), text, LineTheme.FONT_XS, color, Typeface.NORMAL);
        message.setSingleLine(false);
        LineTheme.padding(message, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        addView(message, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private String description(int count, boolean pending, boolean accepted, boolean rejected, boolean complete, boolean error) {
        String status = error ? getContext().getString(R.string.tool_call_shell_failed) : pending ? getContext().getString(R.string.tool_call_delete_status_pending) : rejected ? getContext().getString(R.string.tool_call_delete_status_rejected) : complete ? getContext().getString(R.string.tool_call_delete_status_executed) : accepted ? getContext().getString(R.string.tool_call_delete_status_accepted) : getContext().getString(R.string.common_delete);
        return status + getContext().getString(R.string.tool_call_delete_count_suffix, count);
    }

    private String displayPath(String path) {
        String value = path == null ? "" : path.trim();
        if (projectPath.length() == 0 || value.length() == 0) {
            return value;
        }
        if (value.equals(projectPath)) {
            return ".";
        }
        String prefix = projectPath.endsWith("/") ? projectPath : projectPath + "/";
        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    private ArrayList<String> paths(JSONObject input) {
        ArrayList<String> values = new ArrayList<>();
        JSONArray array = input.optJSONArray("paths");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (value.length() > 0) {
                    values.add(value);
                }
            }
        }
        String filePath = input.optString("file_path").trim();
        if (filePath.length() > 0) {
            values.add(filePath);
        }
        String path = input.optString("path").trim();
        if (path.length() > 0) {
            values.add(path);
        }
        return values;
    }
}
