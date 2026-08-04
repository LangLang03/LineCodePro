package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import cn.lineai.model.DiffUiModel;
import java.util.Locale;
import org.json.JSONObject;

public final class ToolCallWriteView extends BaseToolCallView implements ToolCallCardView {
    private ToolReviewListener toolReviewListener;
    private String projectPath = "";
    private DiffLoader diffLoader;
    private boolean diffExpanded;

    public ToolCallWriteView(Context context) {
        super(context);
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void setDiffLoader(DiffLoader diffLoader) {
        this.diffLoader = diffLoader;
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        setTag(new Object[] {toolCall, result});
        DiffUiModel diffRecord = loadDiff(result);

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        LineTheme.padding(body, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        body.addView(buildHeader(toolCall, result, diffRecord),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        boolean complete = result != null;
        boolean error = result != null && result.isError();
        boolean hasDiff = complete && !error && diffRecord != null;
        String reviewState = result == null ? "" : result.getReviewState();
        boolean rejected = "rejected".equals(reviewState) || (diffRecord != null && diffRecord.isReverted());
        boolean accepted = "accepted".equals(reviewState);
        // 动作行只在等待审查时出现，完成/失败态不再占用一行
        if (hasDiff && !accepted && !rejected) {
            LayoutParams actionRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            actionRowParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
            body.addView(buildReviewRow(toolCall, result), actionRowParams);
        }
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        buildDiffSection(toolCall, result, diffRecord);
        buildMessage(toolCall, result, diffRecord);
    }

    private LinearLayout buildHeader(ToolCall toolCall, ToolResult result, DiffUiModel diffRecord) {
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String filePath = input.optString("file_path");
        String fileName = fileName(filePath);
        String displayName = fileName.length() == 0 ? getContext().getString(R.string.tool_call_write_unnamed) : fileName;
        boolean complete = result != null;
        boolean error = result != null && result.isError();
        String reviewState = result == null ? "" : result.getReviewState();
        boolean rejected = "rejected".equals(reviewState) || (diffRecord != null && diffRecord.isReverted());
        int statusColor = error || rejected ? LineTheme.DANGER : complete ? LineTheme.SUCCESS : LineTheme.ACCENT;
        String targetPath = diffRecord != null && diffRecord.getFilePath().length() > 0 ? diffRecord.getFilePath() : filePath;
        if (fileName.length() == 0 && targetPath.length() > 0) {
            fileName = fileName(targetPath);
            displayName = fileName.length() == 0 ? getContext().getString(R.string.tool_call_write_unnamed) : fileName;
        }
        String shownPath = ToolCallUtils.workspaceDisplayPath(projectPath, targetPath);
        if (shownPath.length() == 0) {
            shownPath = displayName;
        }

        LinearLayout fileRow = new LinearLayout(getContext());
        fileRow.setOrientation(HORIZONTAL);
        fileRow.setGravity(Gravity.CENTER_VERTICAL);

        IconButtonView fileIcon = new IconButtonView(getContext(), IconButtonView.FILE_CODE);
        fileIcon.setIconColor(statusColor);
        fileIcon.setIconSizeDp(24, 12);
        fileIcon.setClickable(false);
        fileRow.addView(fileIcon, new LayoutParams(LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));

        LinearLayout meta = new LinearLayout(getContext());
        meta.setOrientation(VERTICAL);
        LayoutParams metaParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        metaParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        fileRow.addView(meta, metaParams);

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView action = LineTheme.text(getContext(), actionLabel(toolCall, result, input), 10,
                statusColor == LineTheme.DANGER ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        titleRow.addView(action, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView title = LineTheme.text(getContext(), displayName, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        title.setSingleLine(true);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleRow.addView(title, titleParams);
        meta.addView(titleRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView path = LineTheme.text(getContext(), shownPath, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        path.setSingleLine(true);
        path.setHorizontallyScrolling(true);
        HorizontalScrollView pathScroll = horizontalPathScroll(path);
        meta.addView(pathScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View status = statusView(!complete);
        if (status instanceof IconButtonView) {
            IconButtonView statusIcon = (IconButtonView) status;
            statusIcon.setIconSizeDp(24, 12);
            statusIcon.setIconColor(statusColor);
            if (error || rejected) {
                statusIcon.setIconType(IconButtonView.CLOSE);
            }
        }
        fileRow.addView(status, new LayoutParams(LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));
        return fileRow;
    }

    private LinearLayout buildReviewRow(ToolCall toolCall, ToolResult result) {
        JSONObject input = ToolCallUtils.parseInput(toolCall);

        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setMinimumHeight(LineTheme.dp(getContext(), 28));
        LayoutParams actionParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        TextView actionBadge = LineTheme.text(getContext(), actionLabel(toolCall, result, input),
                LineTheme.FONT_XS, LineTheme.ACCENT, Typeface.BOLD);
        actionBadge.setGravity(Gravity.CENTER);
        actionBadge.setMinHeight(LineTheme.dp(getContext(), 24));
        actionBadge.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.ACCENT_MUTED, 4, LineTheme.ACCENT_MUTED_2));
        LineTheme.padding(actionBadge, LineTheme.SM, 2, LineTheme.SM, 2);
        actionRow.addView(actionBadge, actionParams);

        View spacer = new View(getContext());
        actionRow.addView(spacer, new LayoutParams(0, 1, 1f));
        LinearLayout rejectButton = reviewButton(IconButtonView.CLOSE, getContext().getString(R.string.tool_call_write_revert), LineTheme.DANGER, LineTheme.SURFACE_LIGHT, LineTheme.DANGER_MUTED_2);
        rejectButton.setOnClickListener(v -> {
            if (toolReviewListener != null) {
                toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "rejected", result.getDiffId());
            }
        });
        actionRow.addView(rejectButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 30)));

        LinearLayout acceptButton = reviewButton(IconButtonView.CHECK, getContext().getString(R.string.tool_call_write_accept), LineTheme.TEXT_ON_COLOR, LineTheme.ACCENT, LineTheme.ACCENT);
        acceptButton.setOnClickListener(v -> {
            if (toolReviewListener != null) {
                toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "accepted", result.getDiffId());
            }
        });
        LayoutParams acceptParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 30));
        acceptParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actionRow.addView(acceptButton, acceptParams);
        return actionRow;
    }

    private void buildDiffSection(ToolCall toolCall, ToolResult result, DiffUiModel diffRecord) {
        boolean complete = result != null;
        boolean error = result != null && result.isError();
        boolean hasDiff = complete && !error && diffRecord != null;
        if (hasDiff) {
            addDiffSection(diffRecord);
        }
    }

    private void buildMessage(ToolCall toolCall, ToolResult result, DiffUiModel diffRecord) {
        boolean complete = result != null;
        boolean error = result != null && result.isError();
        boolean hasDiff = complete && !error && diffRecord != null;
        if (error && result.getContent().length() > 0) {
            addMessage(result.getContent(), LineTheme.DANGER);
        } else if (result != null && result.getReviewMessage().length() > 0 && result.getReviewState().length() == 0) {
            addMessage(result.getReviewMessage(), LineTheme.DANGER);
        } else if (complete && !hasDiff && result.getContent().length() > 0) {
            addMessage(result.getContent(), LineTheme.TEXT_SECONDARY);
        }
    }

    private DiffUiModel loadDiff(ToolResult result) {
        if (result == null || result.getDiffId().length() == 0) {
            return null;
        }
        if (diffLoader != null) {
            try {
                return diffLoader.loadDiff(result.getDiffId());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LinearLayout reviewButton(int iconType, String label, int color, int background, int border) {
        LinearLayout button = new LinearLayout(getContext());
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.roundedStroke(getContext(), background, 6, border));
        button.setClickable(true);
        button.setMinimumHeight(LineTheme.dp(getContext(), 30));
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

    private void addDiffSection(DiffUiModel record) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        LineTheme.padding(header, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView arrow = new IconButtonView(getContext(), diffExpanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        arrow.setIconColor(LineTheme.ACCENT);
        arrow.setIconSizeDp(16, 12);
        arrow.setClickable(false);
        header.addView(arrow, new LayoutParams(LineTheme.dp(getContext(), 16), LineTheme.dp(getContext(), 16)));

        TextView label = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_view_diff), LineTheme.FONT_XS, LineTheme.ACCENT, Typeface.BOLD);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        header.addView(label, labelParams);
        header.setOnClickListener(v -> {
            diffExpanded = !diffExpanded;
            bindLast();
        });
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (diffExpanded) {
            DiffView diffView = new DiffView(getContext());
            diffView.bind(record.getOldContent(), record.getNewContent());
            addView(diffView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private void bindLast() {
        if (getTag() instanceof Object[]) {
            Object[] values = (Object[]) getTag();
            bind((ToolCall) values[0], (ToolResult) values[1]);
        }
    }

    private void addMessage(String text, int color) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        TextView result = LineTheme.text(getContext(), text, LineTheme.FONT_XS, color, Typeface.NORMAL);
        LineTheme.padding(result, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        addView(result, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private String fileName(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private String actionLabel(ToolCall toolCall, ToolResult result, JSONObject input) {
        String name = toolCall == null ? "" : toolCall.getName();
        String resultName = result == null ? "" : result.getToolName();
        if (isEditName(name) || isEditName(resultName) || hasEditShape(input)) {
            return getContext().getString(R.string.common_edit);
        }
        return getContext().getString(R.string.tool_call_action_write);
    }

    private boolean isEditName(String name) {
        String compact = name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return "fileedit".equals(compact) || "editfile".equals(compact);
    }

    private boolean hasEditShape(JSONObject input) {
        return input != null && (input.has("old_string")
                || input.has("new_string")
                || input.has("search")
                || input.has("replace")
                || input.has("patch")
                || input.has("edits"));
    }
}
