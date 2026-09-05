package cn.lineai.tool.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.DiffUiModel;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ToolCallWriteView extends BaseToolCallView implements ToolCallCardView, ToolCallExpansion {
    private static final ExecutorService DIFF_WORKER = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final TextView label;
    private final IconButtonView arrow;
    private final LinearLayout detail;
    private final TextView fileName;
    private final LinearLayout actions;
    private final TextView errorText;
    private final DiffView diffView;
    private ToolReviewListener reviewer;
    private DiffLoader loader;
    private ToolCall call;
    private ToolResult result;
    private DiffUiModel record;
    private DiffLines diff;
    private Future<?> task;
    private boolean loadFailed;
    private int version;
    private String requestedId = "";
    private String projectPath = "";
    private Map<String, Boolean> expansion = new HashMap<>();
    private String key = "write";

    public ToolCallWriteView(Context context) {
        super(context); setBackground(null);
        LinearLayout header = row(); header.setMinimumHeight(dp(48));
        IconButtonView icon = new IconButtonView(context, IconButtonView.FILE_PEN_LINE);
        icon.setIconSizeDp(24, 16); icon.setIconColor(LineTheme.TEXT_SECONDARY); icon.setClickable(false);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LayoutParams(dp(24), dp(32)));
        label = LineTheme.text(context, "", 14, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LayoutParams labelParams = new LayoutParams(0, -2, 1); labelParams.leftMargin = dp(6);
        header.addView(label, labelParams);
        arrow = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT); arrow.setIconSizeDp(24, 14);
        arrow.setIconColor(LineTheme.TEXT_SECONDARY); arrow.setClickable(false);
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(arrow, new LayoutParams(dp(24), dp(32)));
        header.setFocusable(true); header.setOnClickListener(v -> { expansion.put(key, !isExpanded()); render(); });
        addView(header, new LayoutParams(-1, -2));
        detail = new LinearLayout(context); detail.setOrientation(VERTICAL);
        detail.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        detail.setClipToOutline(true);
        LinearLayout fileHeader = row(); LineTheme.padding(fileHeader, 12, 0, 0, 0);
        fileName = LineTheme.text(context, "", 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        fileName.setSingleLine(true); fileName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        fileHeader.addView(fileName, new LayoutParams(0, -2, 1));
        IconButtonView copy = new IconButtonView(context, IconButtonView.COPY); copy.setIconSizeDp(44, 16);
        copy.setIconColor(LineTheme.TEXT_SECONDARY); copy.setContentDescription(context.getString(R.string.tool_call_copy_file));
        copy.setOnClickListener(v -> {
            if (record == null) return;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(record.getFilePath(), record.getNewContent()));
        });
        fileHeader.addView(copy, new LayoutParams(dp(44), dp(44)));
        detail.addView(fileHeader, new LayoutParams(-1, -2));
        BoundedScrollView scroll = new BoundedScrollView(context, 224);
        diffView = new DiffView(context); scroll.addView(diffView, new android.widget.ScrollView.LayoutParams(-1, -2));
        detail.addView(scroll, new LayoutParams(-1, -2));
        errorText = LineTheme.text(context, "", 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LineTheme.padding(errorText, 14, 10, 14, 10); detail.addView(errorText, new LayoutParams(-1, -2));
        actions = row(); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); LineTheme.padding(actions, 8, 6, 8, 6);
        TextView revert = button(context.getString(R.string.tool_call_write_revert));
        revert.setOnClickListener(v -> review("rejected")); actions.addView(revert);
        TextView accept = button(context.getString(R.string.tool_call_write_accept));
        accept.setOnClickListener(v -> review("accepted")); actions.addView(accept);
        detail.addView(actions, new LayoutParams(-1, -2));
        addView(detail, new LayoutParams(-1, -2)); render();
    }
    @Override public void bind(ToolCall call, ToolResult result) {
        boolean different = this.call == null || call == null || !this.call.getId().equals(call.getId());
        this.call = call; this.result = result;
        String id = result == null ? "" : result.getDiffId();
        if (different || !requestedId.equals(id)) {
            cancelLoad(); record = null; diff = null; loadFailed = false; requestedId = id;
        }
        render(); requestDiff();
    }
    public void setDiffLoader(DiffLoader loader) { this.loader = loader; requestDiff(); }
    @Override public void setToolReviewListener(ToolReviewListener reviewer) { this.reviewer = reviewer; }
    @Override public void setProjectPath(String path) { projectPath = path == null ? "" : path; }
    @Override public void setExpansionState(Map<String, Boolean> state, String key) {
        if (state != null) expansion = state;
        this.key = key; render();
    }
    private void requestDiff() {
        if (!isAttachedToWindow() || loader == null || requestedId.isEmpty() || record != null || task != null || loadFailed) return;
        final String id = requestedId;
        final int expected = ++version;
        final DiffLoader source = loader;
        final WeakReference<ToolCallWriteView> reference = new WeakReference<>(this);
        task = DIFF_WORKER.submit(() -> {
            DiffUiModel loaded = null;
            try { loaded = source.loadDiff(id); } catch (Exception ignored) { /* Show an unavailable state, keep the operation reviewable. */ }
            final DiffUiModel value = loaded;
            final DiffLines lines = value == null ? null : DiffLines.calculate(value.getOldContent(), value.getNewContent());
            MAIN.post(() -> {
                ToolCallWriteView target = reference.get();
                if (target == null || target.version != expected || !target.isAttachedToWindow()) return;
                target.task = null; target.record = value; target.diff = lines; target.loadFailed = value == null;
                if (lines != null) target.diffView.bind(lines);
                target.render();
            });
        });
    }
    private void render() {
        if (label == null || detail == null) return;
        String path = record != null ? record.getFilePath() : ToolCallUtils.parseInput(call).optString("file_path",
                ToolCallUtils.parseInput(call).optString("path", ""));
        String name = path.isEmpty() ? getContext().getString(R.string.tool_call_write_unnamed) : path.substring(path.lastIndexOf('/') + 1);
        boolean failed = result != null && result.isError();
        int status = result == null || "running".equals(result.getReviewState()) ? R.string.tool_call_status_running
                : failed ? R.string.tool_call_status_failed : "pending".equals(result.getReviewState()) ? R.string.tool_call_status_pending_review
                : "rejected".equals(result.getReviewState()) ? R.string.tool_call_write_reverted : R.string.tool_call_write_done;
        if (status == R.string.tool_call_write_done && record != null && record.getOldContent().isEmpty()
                && call != null && cn.lineai.tool.ToolNames.FILE_WRITE.equals(call.getName())) status = R.string.tool_call_write_created;
        label.setText(counts(getContext().getString(status) + " " + name));
        label.setTextColor(failed ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        label.setContentDescription(getContext().getString(status) + " " + ToolCallUtils.workspaceDisplayPath(projectPath, path));
        if (fileName != null) fileName.setText(counts(name));
        boolean expanded = isExpanded(); detail.setVisibility(expanded ? VISIBLE : GONE);
        arrow.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        if (diffView != null) diffView.setVisibility(diff == null ? GONE : VISIBLE);
        if (actions != null) actions.setVisibility(result != null && !result.getDiffId().isEmpty()
                && !"accepted".equals(result.getReviewState()) && !"rejected".equals(result.getReviewState()) ? VISIBLE : GONE);
        if (errorText != null) {
            String message = failed ? result.getContent() : result != null && !result.getReviewMessage().isEmpty() ? result.getReviewMessage()
                    : record == null ? getContext().getString(requestedId.isEmpty() || loadFailed ? R.string.tool_call_diff_unavailable : R.string.tool_call_diff_loading) : "";
            errorText.setText(message); errorText.setTextColor(failed ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
            errorText.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        }
    }
    private CharSequence counts(String text) {
        if (diff == null) return text;
        SpannableStringBuilder result = new SpannableStringBuilder(text + "  ");
        int start = result.length(); result.append("+" + diff.added);
        result.setSpan(new ForegroundColorSpan(LineTheme.SUCCESS), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(" "); start = result.length(); result.append("−" + diff.removed);
        result.setSpan(new ForegroundColorSpan(LineTheme.DANGER), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return result;
    }
    private void review(String state) { if (reviewer != null && call != null && result != null) reviewer.onToolReview(call.getId(), state, result.getDiffId()); }
    private boolean isExpanded() { return Boolean.TRUE.equals(expansion.get(key)); }
    private int dp(int value) { return LineTheme.dp(getContext(), value); }
    private LinearLayout row() { LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private TextView button(String text) {
        TextView button = LineTheme.text(getContext(), text, 13, LineTheme.TEXT, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER); button.setMinHeight(dp(48)); LineTheme.padding(button, 14, 0, 14, 0); button.setFocusable(true); return button;
    }
    private void cancelLoad() { version++; if (task != null) task.cancel(true); task = null; }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); requestDiff(); }
    @Override protected void onDetachedFromWindow() { cancelLoad(); super.onDetachedFromWindow(); }
}
