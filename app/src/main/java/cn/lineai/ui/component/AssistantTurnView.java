package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ChatMessage;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.tool.ui.ToolCallBlockView;
import cn.lineai.ui.markdown.MarkdownLinkHandler;
import cn.lineai.ui.markdown.MarkdownView;
import cn.lineai.ui.model.ConversationTimeline;
import cn.lineai.ui.model.ProcessingDuration;
import cn.lineai.ui.model.ConversationTimeline.Block;
import cn.lineai.ui.model.ConversationTimeline.Operation;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.theme.ThinkingBlockView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A response with manually disclosed work, and a separate, always visible final answer. */
public final class AssistantTurnView extends LinearLayout {
    private final LinearLayout process;
    private final LinearLayout processToggle;
    private final View processRule;
    private final LinearLayout changes;
    private final LinearLayout files;
    private final TextView processLabel;
    private final IconButtonView processArrow;
    private final TextView filesLabel;
    private final IconButtonView filesArrow;
    private final AssistantMessageView answer;
    private final Map<String, View> blocks = new HashMap<>();
    private final Map<String, ToolCallBlockView> fileViews = new HashMap<>();
    private Map<String, Boolean> disclosure;
    private ConversationTimeline.Row row;
    private ToolReviewListener reviewer;
    private MarkdownLinkHandler links;
    private String projectPath = "";
    private String identity = "";
    private boolean codeWrap;
    private boolean generating;
    private boolean hasTools;
    private final Runnable durationTick = this::updateProcessLabel;

    public AssistantTurnView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LineTheme.padding(this, 16, 0, 16, 32);
        LinearLayout toggle = horizontal();
        processToggle = toggle;
        toggle.setMinimumHeight(dp(48));
        processLabel = LineTheme.text(context, "", 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        toggle.addView(processLabel);
        processArrow = icon(IconButtonView.CHEVRON_RIGHT);
        toggle.addView(processArrow, new LayoutParams(dp(28), dp(32)));
        toggle.setFocusable(true);
        toggle.setOnClickListener(v -> {
            disclosure.put(identity + ":process", !isOpen(identity + ":process"));
            renderProcess();
        });
        addView(toggle, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View rule = new View(context);
        processRule = rule;
        rule.setBackgroundColor(LineTheme.BORDER);
        LayoutParams ruleParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
        ruleParams.bottomMargin = dp(20);
        addView(rule, ruleParams);
        process = vertical();
        addView(process, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        answer = new AssistantMessageView(context);
        answer.setPadding(0, 0, 0, 0);
        addView(answer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        changes = vertical();
        LayoutParams changeParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        changeParams.topMargin = dp(24);
        addView(changes, changeParams);
        View changesRule = new View(context);
        changesRule.setBackgroundColor(LineTheme.BORDER);
        changes.addView(changesRule, new LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout summary = horizontal();
        summary.setMinimumHeight(dp(64));
        summary.addView(icon(IconButtonView.FILE_PEN_LINE), new LayoutParams(dp(26), dp(32)));
        filesLabel = LineTheme.text(context, "", 14, LineTheme.TEXT, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        labelParams.leftMargin = dp(6);
        summary.addView(filesLabel, labelParams);
        TextView review = LineTheme.text(context, context.getString(R.string.chat_review_changes), 14, LineTheme.TEXT, Typeface.NORMAL);
        review.setGravity(Gravity.CENTER);
        review.setMinHeight(dp(48));
        LineTheme.padding(review, 12, 0, 0, 0);
        review.setOnClickListener(v -> {
            disclosure.put(identity + ":files", true);
            for (Operation operation : changedFiles()) disclosure.put("review:" + operation.call.getId(), true);
            renderFiles();
        });
        summary.addView(review);
        filesArrow = icon(IconButtonView.CHEVRON_RIGHT);
        summary.addView(filesArrow, new LayoutParams(dp(24), dp(32)));
        summary.setFocusable(true);
        summary.setOnClickListener(v -> {
            disclosure.put(identity + ":files", !isOpen(identity + ":files"));
            renderFiles();
        });
        changes.addView(summary, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        files = vertical();
        changes.addView(files, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void bind(ConversationTimeline.Row row, Map<String, Boolean> disclosure, String projectPath,
                     ToolReviewListener reviewer, MarkdownLinkHandler links, MessageActionListener actions,
                     boolean codeWrap, boolean generating) {
        bind(row, disclosure, projectPath, reviewer, links, actions, codeWrap, generating, false);
    }

    public void bind(ConversationTimeline.Row row, Map<String, Boolean> disclosure, String projectPath,
                     ToolReviewListener reviewer, MarkdownLinkHandler links, MessageActionListener actions,
                     boolean codeWrap, boolean generating, boolean processAutoExpand) {
        String nextIdentity = row.first.getId();
        if (!identity.equals(nextIdentity)) {
            process.removeAllViews(); blocks.clear(); files.removeAllViews(); fileViews.clear();
        }
        identity = nextIdentity;
        this.row = row; this.disclosure = disclosure; this.projectPath = projectPath;
        this.reviewer = reviewer; this.links = links; this.codeWrap = codeWrap;
        this.generating = generating;
        hasTools = row.isTurn;
        String processKey = identity + ":process";
        if (hasTools && processAutoExpand && !disclosure.containsKey(processKey)) {
            disclosure.put(processKey, true);
        }
        setProcessVisibility(hasTools);
        updateProcessLabel();
        if (row.answer != null) {
            answer.setVisibility(VISIBLE);
            answer.setMarkdownLinkHandler(links);
            answer.setMessageActionListener(actions);
            ChatMessage message = row.answer;
            answer.bind(message, false, true, codeWrap);
        } else answer.setVisibility(GONE);
        renderProcess();
        renderFiles();
    }

    private void updateProcessLabel() {
        removeCallbacks(durationTick);
        if (row == null) return;
        boolean active = generating && row.processingFinishedAt == 0
                && (row.processingStartedAt > 0 || row.running || row.pending);
        int status = row.pending && active ? R.string.chat_process_pending
                : active ? R.string.chat_process_running : R.string.chat_process_done;
        String label = getContext().getString(status);
        if (row.processingStartedAt > 0) {
            long end = row.processingFinishedAt > 0 ? row.processingFinishedAt : System.currentTimeMillis();
            label += " " + ProcessingDuration.format(end - row.processingStartedAt);
        }
        processLabel.setText(label);
        processLabel.setContentDescription(label + ", " + getContext().getString(isOpen(identity + ":process")
                ? R.string.chat_collapse : R.string.chat_expand));
        if (active && row.processingStartedAt > 0 && isAttachedToWindow()) postDelayed(durationTick, 1000);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateProcessLabel();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(durationTick);
        super.onDetachedFromWindow();
    }

    private void setProcessVisibility(boolean visible) {
        processToggle.setVisibility(visible ? VISIBLE : GONE);
        processRule.setVisibility(visible ? VISIBLE : GONE);
        if (!visible) process.setVisibility(GONE);
    }

    private void renderProcess() {
        boolean expanded = hasTools && isOpen(identity + ":process");
        process.setVisibility(expanded ? VISIBLE : GONE);
        processArrow.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        processLabel.setContentDescription(processLabel.getText() + ", " + getContext().getString(expanded ? R.string.chat_collapse : R.string.chat_expand));
        if (!expanded) return;
        ArrayList<View> children = new ArrayList<>();
        for (Block block : row.process) {
            View view = blocks.get(block.id);
            if (block.reasoning) {
                ThinkingBlockView thought = view instanceof ThinkingBlockView ? (ThinkingBlockView) view : new ThinkingBlockView(getContext());
                ChatMessage owner = null;
                for (ChatMessage message : row.messages) if (block.id.equals(message.getId() + ":reasoning")) owner = message;
                thought.bind(identity + ":" + block.id, block.text, owner != null && owner.isStreaming() && generating, false, true);
                view = thought;
            } else if (block.isAgent()) {
                ToolCallBlockView direct = view instanceof ToolCallBlockView ? (ToolCallBlockView) view : new ToolCallBlockView(getContext());
                bindOperation(direct, block.operations.get(0), "call:" + block.operations.get(0).call.getId());
                view = direct;
            } else if (block.isTools()) {
                ToolGroupView group = view instanceof ToolGroupView ? (ToolGroupView) view : new ToolGroupView(getContext());
                group.bind(block);
                view = group;
            } else {
                MarkdownView text = view instanceof MarkdownView ? (MarkdownView) view : new MarkdownView(getContext());
                text.setCodeWrapEnabled(codeWrap);
                text.setLinkHandler(links);
                if (!block.text.equals(text.getTag())) {
                    text.setMarkdown(block.text);
                    text.setTag(block.text);
                }
                view = text;
            }
            blocks.put(block.id, view);
            children.add(view);
        }
        reconcile(process, children, 8);
    }

    private List<Operation> changedFiles() {
        LinkedHashMap<String, Operation> edits = new LinkedHashMap<>();
        for (Block block : row.process) for (Operation operation : block.operations) {
            if (operation.result != null && !operation.result.getDiffId().isEmpty()) {
                // Keep every diff reviewable, including multiple edits to the same file.
                edits.put(operation.result.getDiffId(), operation);
            }
        }
        return new ArrayList<>(edits.values());
    }

    private void renderFiles() {
        List<Operation> edits = changedFiles();
        changes.setVisibility(edits.isEmpty() || row.answer == null ? GONE : VISIBLE);
        java.util.Set<String> paths = new java.util.HashSet<>();
        for (Operation operation : edits) {
            org.json.JSONObject input = cn.lineai.tool.ui.ToolCallUtils.parseInput(operation.call);
            paths.add(input.optString("file_path", input.optString("path", operation.result.getDiffId())));
        }
        filesLabel.setText(getContext().getString(R.string.chat_files_changed, paths.size()));
        boolean expanded = isOpen(identity + ":files");
        files.setVisibility(expanded ? VISIBLE : GONE);
        filesArrow.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        if (!expanded) return;
        ArrayList<View> children = new ArrayList<>();
        for (Operation operation : edits) {
            String id = operation.call.getId();
            ToolCallBlockView view = fileViews.get(id);
            if (view == null) { view = new ToolCallBlockView(getContext()); fileViews.put(id, view); }
            bindOperation(view, operation, "review:" + id);
            children.add(view);
        }
        reconcile(files, children, 12);
    }

    private void bindOperation(ToolCallBlockView view, Operation operation, String key) {
        view.setProjectPath(projectPath);
        view.setToolReviewListener(reviewer);
        view.setExpansionState(disclosure, key);
        view.bind(operation.call, operation.result);
    }

    private final class ToolGroupView extends LinearLayout {
        final TextView label;
        final IconButtonView arrow;
        final LinearLayout content;
        final Map<String, View> views = new HashMap<>();
        Block block;
        ToolGroupView(Context context) {
            super(context); setOrientation(VERTICAL);
            LinearLayout header = horizontal(); header.setMinimumHeight(dp(48));
            header.addView(icon(IconButtonView.TERMINAL), new LayoutParams(dp(24), dp(32)));
            label = LineTheme.text(context, "", 14, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            labelParams.leftMargin = dp(6); header.addView(label, labelParams);
            arrow = icon(IconButtonView.CHEVRON_RIGHT); header.addView(arrow, new LayoutParams(dp(28), dp(32)));
            header.setFocusable(true);
            header.setOnClickListener(v -> { disclosure.put(block.id, !isOpen(block.id)); bind(block); });
            addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            content = vertical(); addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        void bind(Block block) {
            this.block = block;
            label.setText(getContext().getString(R.string.chat_tools_count, block.operations.size()));
            boolean expanded = isOpen(block.id);
            arrow.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
            content.setVisibility(expanded ? VISIBLE : GONE);
            if (!expanded) return;
            ArrayList<View> children = new ArrayList<>();
            for (Block step : block.steps) {
                if (step.reasoning) continue;
                View view = views.get(step.id);
                if (step.isTools()) {
                    Operation operation = step.operations.get(0);
                    ToolCallBlockView callView = view instanceof ToolCallBlockView ? (ToolCallBlockView) view : new ToolCallBlockView(getContext());
                    bindOperation(callView, operation, "call:" + operation.call.getId());
                    view = callView;
                } else {
                    MarkdownView text = view instanceof MarkdownView ? (MarkdownView) view : new MarkdownView(getContext());
                    text.setCodeWrapEnabled(codeWrap); text.setLinkHandler(links); text.setTextScale(.875f);
                    if (!step.text.equals(text.getTag())) { text.setMarkdown(step.text); text.setTag(step.text); }
                    view = text;
                }
                views.put(step.id, view); children.add(view);
            }
            reconcile(content, children, 8);
        }
    }

    private void reconcile(LinearLayout parent, List<View> children, int gap) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) if (!children.contains(parent.getChildAt(i))) parent.removeViewAt(i);
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (parent.getChildAt(i) == child) continue;
            if (child.getParent() == parent) parent.removeView(child);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(gap); parent.addView(child, i, params);
        }
    }
    private boolean isOpen(String key) { return Boolean.TRUE.equals(disclosure.get(key)); }
    private int dp(int value) { return LineTheme.dp(getContext(), value); }
    private LinearLayout horizontal() { LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private LinearLayout vertical() { LinearLayout column = new LinearLayout(getContext()); column.setOrientation(VERTICAL); return column; }
    private IconButtonView icon(int type) {
        IconButtonView icon = new IconButtonView(getContext(), type); icon.setIconSizeDp(28, 16);
        icon.setIconColor(LineTheme.TEXT_SECONDARY); icon.setClickable(false); icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); return icon;
    }
}
