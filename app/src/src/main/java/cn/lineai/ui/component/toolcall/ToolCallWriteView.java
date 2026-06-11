package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Locale;
import org.json.JSONObject;

public final class ToolCallWriteView extends LinearLayout {
    private ToolReviewListener toolReviewListener;
    private String projectPath = "";
    private boolean diffExpanded;

    public ToolCallWriteView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        setTag(new Object[] {toolCall, result});
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String filePath = input.optString("file_path");
        String fileName = fileName(filePath);
        String displayName = fileName.length() == 0 ? "未命名文件" : fileName;
        String lang = langLabel(displayName);
        boolean complete = result != null;
        boolean error = result != null && result.isError();
        DiffRecord diffRecord = loadDiff(result);
        boolean hasDiff = complete && !error && diffRecord != null;
        String reviewState = result == null ? "" : result.getReviewState();
        boolean rejected = "rejected".equals(reviewState) || (diffRecord != null && diffRecord.isReverted());
        boolean accepted = "accepted".equals(reviewState);
        int statusColor = error || rejected ? LineTheme.DANGER : complete ? LineTheme.SUCCESS : LineTheme.ACCENT;
        String targetPath = diffRecord != null && diffRecord.getFilePath().length() > 0 ? diffRecord.getFilePath() : filePath;
        if (fileName.length() == 0 && targetPath.length() > 0) {
            fileName = fileName(targetPath);
            displayName = fileName.length() == 0 ? "未命名文件" : fileName;
            lang = langLabel(displayName);
        }
        String shownPath = ToolCallUtils.workspaceDisplayPath(projectPath, targetPath);
        if (shownPath.length() == 0) {
            shownPath = displayName;
        }

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        LineTheme.padding(body, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        LinearLayout fileRow = new LinearLayout(getContext());
        fileRow.setOrientation(HORIZONTAL);
        fileRow.setGravity(Gravity.CENTER_VERTICAL);

        IconButtonView fileIcon = new IconButtonView(getContext(), IconButtonView.FILE_CODE);
        fileIcon.setIconColor(statusColor);
        fileIcon.setIconSizeDp(28, 14);
        fileIcon.setClickable(false);
        fileIcon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 8, LineTheme.CODE_BORDER));
        fileRow.addView(fileIcon, new LayoutParams(LineTheme.dp(getContext(), 28), LineTheme.dp(getContext(), 28)));

        LinearLayout meta = new LinearLayout(getContext());
        meta.setOrientation(VERTICAL);
        LayoutParams metaParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        metaParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        fileRow.addView(meta, metaParams);

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = LineTheme.text(getContext(), lang, 10, LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 4, LineTheme.CODE_BORDER));
        LineTheme.padding(badge, LineTheme.XS, 1, LineTheme.XS, 1);
        titleRow.addView(badge, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 18)));
        TextView title = LineTheme.text(getContext(), displayName, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        title.setSingleLine(true);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        titleRow.addView(title, titleParams);
        meta.addView(titleRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView path = LineTheme.text(getContext(), shownPath, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        path.setSingleLine(true);
        path.setHorizontallyScrolling(true);
        HorizontalScrollView pathScroll = horizontalPathScroll();
        pathScroll.addView(path, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        meta.addView(pathScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View status = statusView(complete, error || rejected, statusColor);
        status.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 8, LineTheme.CODE_BORDER));
        fileRow.addView(status, new LayoutParams(LineTheme.dp(getContext(), 28), LineTheme.dp(getContext(), 28)));
        body.addView(fileRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setMinimumHeight(LineTheme.dp(getContext(), 28));
        LayoutParams actionParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        TextView actionBadge = LineTheme.text(getContext(), actionLabel(toolCall, result, input),
                LineTheme.FONT_XS, hasDiff && !accepted && !rejected ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        actionBadge.setGravity(Gravity.CENTER);
        actionBadge.setMinHeight(LineTheme.dp(getContext(), 24));
        actionBadge.setBackground(LineTheme.roundedStroke(getContext(),
                hasDiff && !accepted && !rejected ? LineTheme.ACCENT_MUTED : LineTheme.SURFACE_LIGHT,
                4,
                hasDiff && !accepted && !rejected ? LineTheme.ACCENT_MUTED_2 : LineTheme.CODE_BORDER));
        LineTheme.padding(actionBadge, LineTheme.SM, 2, LineTheme.SM, 2);
        actionRow.addView(actionBadge, actionParams);
        if (hasDiff && !accepted && !rejected) {
            View spacer = new View(getContext());
            actionRow.addView(spacer, new LayoutParams(0, 1, 1f));
            LinearLayout rejectButton = reviewButton(IconButtonView.CLOSE, "撤销", LineTheme.DANGER, LineTheme.SURFACE_LIGHT, LineTheme.DANGER_MUTED_2);
            rejectButton.setOnClickListener(v -> {
                if (toolReviewListener != null) {
                    toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "rejected", result.getDiffId());
                }
            });
            actionRow.addView(rejectButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 30)));

            LinearLayout acceptButton = reviewButton(IconButtonView.CHECK, "同意", LineTheme.TEXT_ON_COLOR, LineTheme.ACCENT, LineTheme.ACCENT);
            acceptButton.setOnClickListener(v -> {
                if (toolReviewListener != null) {
                    toolReviewListener.onToolReview(toolCall == null ? "" : toolCall.getId(), "accepted", result.getDiffId());
                }
            });
            LayoutParams acceptParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(getContext(), 30));
            acceptParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
            actionRow.addView(acceptButton, acceptParams);
        }
        LayoutParams actionRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionRowParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        body.addView(actionRow, actionRowParams);
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (hasDiff) {
            addDiffSection(diffRecord);
        }

        if (error && result.getContent().length() > 0) {
            addMessage(result.getContent(), LineTheme.DANGER);
        } else if (result != null && result.getReviewMessage().length() > 0 && result.getReviewState().length() == 0) {
            addMessage(result.getReviewMessage(), LineTheme.DANGER);
        } else if (complete && !hasDiff && result.getContent().length() > 0) {
            addMessage(result.getContent(), LineTheme.TEXT_SECONDARY);
        }
    }

    private View statusView(boolean complete, boolean error, int statusColor) {
        if (!complete) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            return bar;
        }
        IconButtonView icon = new IconButtonView(getContext(), error ? IconButtonView.CLOSE : IconButtonView.CHECK);
        icon.setIconColor(statusColor);
        icon.setIconSizeDp(28, 14);
        icon.setClickable(false);
        return icon;
    }

    private DiffRecord loadDiff(ToolResult result) {
        if (result == null || result.getDiffId().length() == 0) {
            return null;
        }
        try {
            return new DiffRepository(getContext()).getDiff(result.getDiffId());
        } catch (Exception ignored) {
            return null;
        }
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

    private void addDiffSection(DiffRecord record) {
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

        TextView label = LineTheme.text(getContext(), "查看 Diff", LineTheme.FONT_XS, LineTheme.ACCENT, Typeface.BOLD);
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

    private HorizontalScrollView horizontalPathScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        return scroll;
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
            return "编辑";
        }
        return "写入";
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

    private String langLabel(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index + 1 >= fileName.length()) {
            return "TXT";
        }
        return fileName.substring(index + 1).toUpperCase(java.util.Locale.ROOT);
    }
}
