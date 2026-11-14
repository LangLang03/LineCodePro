package cn.lineai.feature.chat.toolcall;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Write/edit/diff cards (legacy {@code ToolCallWriteView}): a status row with the file name and
 * {@code +N −M}, then the line diff in a code card with a copy action.
 *
 * <p>The diff comes from {@link ToolDiffLookup} when the timeline installed one. Without it the
 * renderer derives a best-effort diff from the request itself ({@code old_string}/{@code
 * new_string} for edits, the new content for writes), so the card still shows real colours.</p>
 */
final class WriteToolRenderer implements ToolCardRenderer {

    /** Shared stateless instance. */
    static final WriteToolRenderer INSTANCE = new WriteToolRenderer();

    private WriteToolRenderer() {
    }

    /** Renders the status row with the diff counters and the copyable diff card when expanded. */
    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        String path = path(arguments);
        ToolDiffLookup.Snapshot snapshot = snapshot(frame, entry);
        if (snapshot != null && !snapshot.path.isEmpty()) path = snapshot.path;

        DiffLines diff = diff(arguments, snapshot);
        String copyText = copyText(arguments, snapshot);
        StringBuilder label = new StringBuilder(statusText(frame, entry, diff,
                presentation.icon));
        if (!path.isEmpty()) label.append("  ").append(path);
        if (diff != null) {
            label.append("  +").append(diff.added).append(" \u2212").append(diff.removed);
        }
        frame.bareHeader(ToolGlyphs.of(presentation.icon), label, entry.failed, true,
                entry.running, true);
        if (!frame.expanded()) return;

        LinearLayout box = frame.codeBox();
        box.addView(detailHeader(frame, path, copyText), new LinearLayout.LayoutParams(-1, -2));
        if (diff != null) {
            final DiffView diffView = new DiffView(frame.context());
            diffView.bind(diff, frame.tokens());
            frame.addTokenApplier(() -> diffView.apply(frame.tokens()));
            frame.addScrollable(box, diffView, 224);
        } else {
            String fallback = !entry.result.isEmpty() ? entry.result
                    : ToolCardSupport.prettyJson(arguments);
            if (fallback.trim().isEmpty()) {
                fallback = frame.context().getString(R.string.tool_call_diff_unavailable);
            }
            frame.addCodeText(box, fallback, 160, t -> t.textSecondary);
        }
        if (entry.failed && !entry.result.isEmpty()) {
            box.addView(frame.divider(), new LinearLayout.LayoutParams(-1, 1));
            frame.addCodeText(box, entry.result, 160, t -> t.danger);
        }
        frame.body.addView(box, frame.fill());
    }

    /** Status word distinguishing created/reverted/pending/done writes. */
    private String statusText(ToolCardFrame frame, ToolCallEntry entry, DiffLines diff,
                              ToolIcon icon) {
        int statusRes;
        if (entry.running) statusRes = R.string.tool_call_status_running;
        else if (entry.failed) statusRes = R.string.tool_call_status_failed;
        else if (ToolRunStatus.REJECTED.matches(entry.reviewState)) {
            statusRes = R.string.tool_call_write_reverted;
        } else if (ToolRunStatus.PENDING.matches(entry.reviewState)) {
            statusRes = R.string.tool_call_status_pending_review;
        } else if (icon == ToolIcon.DIFF) statusRes = R.string.tool_call_status_done;
        else if (icon == ToolIcon.WRITE && diff != null && diff.removed == 0) {
            statusRes = R.string.tool_call_write_created;
        } else {
            statusRes = R.string.tool_call_write_done;
        }
        return frame.context().getString(statusRes);
    }

    /** Path line plus the copy action of the diff card. */
    private LinearLayout detailHeader(final ToolCardFrame frame, String path,
                                      final String copyText) {
        LinearLayout row = frame.row();
        row.setMinimumHeight(Ui.dp(frame.context(), 44));
        final TextView name = frame.mono(
                path.isEmpty() ? frame.context().getString(R.string.tool_call_write_unnamed)
                        : path,
                Tokens.FONT_XS, t -> t.textSecondary, true);
        LinearLayout.LayoutParams nameParams = frame.grow();
        nameParams.leftMargin = Ui.dp(frame.context(), 12);
        row.addView(name, nameParams);
        final IconButton copy = new IconButton(frame.context(), Icons.COPY);
        copy.setIconSizeDp(44, 16);
        copy.setContentDescription(frame.context().getString(R.string.tool_call_copy_file));
        copy.setOnClickListener(v -> copyToClipboard(frame.context(), path, copyText));
        frame.addTokenApplier(() -> copy.setIconColor(frame.tokens().textSecondary));
        row.addView(copy, new LinearLayout.LayoutParams(Ui.dp(frame.context(), 44),
                Ui.dp(frame.context(), 44)));
        return row;
    }

    /** Copies the file content (or diff) labeled by the path. */
    private void copyToClipboard(Context context, String path, String content) {
        if (content == null || content.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                path.isEmpty() ? "diff" : path, content));
    }

    /** Persisted snapshot for the entry's diff id, or null without a bridge/id. */
    private ToolDiffLookup.Snapshot snapshot(ToolCardFrame frame, ToolCallEntry entry) {
        ToolDiffLookup lookup = frame.diffLookup();
        if (lookup == null || entry.diffId.isEmpty()) return null;
        return lookup.find(entry.diffId);
    }

    /** Best-effort diff: persisted snapshot, old/new strings, or full new content. */
    private DiffLines diff(ToolJson arguments, ToolDiffLookup.Snapshot snapshot) {
        if (snapshot != null) return DiffLines.calculate(snapshot.before, snapshot.after);
        String oldText = arguments.text("old_string");
        String newText = arguments.text("new_string");
        if (!oldText.isEmpty() || !newText.isEmpty()) {
            return DiffLines.calculate(oldText, newText);
        }
        String content = arguments.text("content");
        if (!content.isEmpty()) return DiffLines.calculate("", content);
        return null;
    }

    /** Content the copy button writes: snapshot after, new_string, or full content. */
    private String copyText(ToolJson arguments, ToolDiffLookup.Snapshot snapshot) {
        if (snapshot != null) return snapshot.after;
        String newText = arguments.text("new_string");
        if (!newText.isEmpty()) return newText;
        return arguments.text("content");
    }

    /** Target path from the first non-empty path argument. */
    private static String path(ToolJson arguments) {
        for (String key : new String[] {"path", "file_path", "filePath"}) {
            String value = arguments.text(key).trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
