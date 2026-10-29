package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.toolcall.ToolCallEntry;
import cn.lineai.feature.chat.toolcall.ToolCallGroupView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.DotMatrixSpinner;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/**
 * The folded process of one assistant turn (C++ {@code AssistantTimeline}).
 *
 * <p>Collapsed it is a single 48dp row: the same accent dot matrix used by the live status row
 * while the turn runs, the state label, the elapsed duration once finished, and the disclosure
 * chevron. While a turn is streaming the panel opens itself so the live tool feed stays visible;
 * once the turn ends it folds back unless the user toggled it. Expanded it renders the turn's
 * process in order behind a hairline rule.</p>
 */
final class ProcessPanel extends LinearLayout {

    private final LinearLayout header;
    private final DotMatrixSpinner spinner;
    private final TextView label;
    private final TextView duration;
    private final IconButton chevron;
    private final View rule;
    private final LinearLayout body;
    private final Map<String, View> children = new LinkedHashMap<>();
    private final Map<String, String> signals = new LinkedHashMap<>();
    private final Map<MarkdownLite, String> markdowns = new LinkedHashMap<>();
    private final Runnable tick = this::updateHeader;
    private Tokens tokens;
    private RowEnv env;
    private String rowKey = "";
    private boolean expanded;
    private boolean running;
    private boolean pending;
    private long startedAt;
    private long finishedAt;
    private boolean live;

    ProcessPanel(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 48));
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> toggle());
        spinner = new DotMatrixSpinner(context, 16f);
        spinner.setVisibility(GONE);
        header.addView(spinner, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        label = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = Ui.dp(context, 8);
        header.addView(label, labelParams);
        duration = Ui.text(context, "", Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        LayoutParams durationParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        durationParams.leftMargin = Ui.dp(context, 4);
        header.addView(duration, durationParams);
        header.addView(new View(context), new LayoutParams(0, 1, 1f));
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(28, 16);
        chevron.setClickable(false);
        header.addView(chevron, new LayoutParams(Ui.dp(context, 28), Ui.dp(context, 32)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        rule = Ui.hairline(context, tokens);
        LayoutParams ruleParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                Math.max(1, Ui.dp(context, 0.5f)));
        ruleParams.bottomMargin = Ui.dp(context, 20);
        addView(rule, ruleParams);

        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        applyTokens(tokens);
    }

    void bind(TurnAssembler.Row row, Tokens current, RowEnv rowEnv, String key) {
        tokens = current;
        env = rowEnv;
        rowKey = key;
        running = row.running;
        pending = row.pending;
        live = row.live;
        startedAt = row.processingStartedAt;
        finishedAt = row.processingFinishedAt;
        boolean failed = row.failed;
        // Live turns and failures open themselves; an explicit toggle always wins.
        expanded = resolveExpanded(key, failed);
        renderItems(row, key);
        for (View child : children.values()) {
            if (child instanceof ReasoningBlockView) {
                ((ReasoningBlockView) child).setLive(live);
            }
        }
        applyExpanded();
        updateHeader();
        applyMarkdownPreferences();
        applyTokens(current);
    }

    void applyTokens(Tokens current) {
        tokens = current;
        label.setTextColor(current.textSecondary);
        duration.setTextColor(current.textSecondary);
        chevron.setIconColor(current.textTertiary);
        rule.setBackgroundColor(current.border);
        spinner.setColor(current.accent);
        for (View child : children.values()) {
            if (child instanceof ToolCallGroupView) {
                ((ToolCallGroupView) child).applyTokens(current);
            } else if (child instanceof CompactBlockView) {
                ((CompactBlockView) child).applyTokens(current);
            } else if (child instanceof ReasoningBlockView) {
                ((ReasoningBlockView) child).applyTokens(current);
            }
        }
        for (Map.Entry<MarkdownLite, String> entry : markdowns.entrySet()) {
            entry.getKey().render(entry.getValue(), current);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void scheduleTick() {
        removeCallbacks(tick);
        if (active() && finishedAt == 0 && isAttachedToWindow()) {
            postDelayed(tick, 1000L);
        }
    }

    private boolean active() {
        return running || pending || (live && finishedAt == 0);
    }

    private void updateHeader() {
        boolean isActive = active();
        int text = isActive
                ? (pending ? R.string.chat_process_pending : R.string.chat_process_running)
                : R.string.chat_process_done;
        label.setText(getContext().getString(text));
        spinner.setVisibility(isActive ? VISIBLE : GONE);
        if (isActive) {
            spinner.start();
        } else {
            spinner.stop();
        }
        LayoutParams labelParams = (LayoutParams) label.getLayoutParams();
        int labelStart = isActive ? Ui.dp(getContext(), 8) : 0;
        if (labelParams.leftMargin != labelStart) {
            labelParams.leftMargin = labelStart;
            label.setLayoutParams(labelParams);
        }
        if (isActive && startedAt > 0) {
            duration.setText(formatDuration(System.currentTimeMillis() - startedAt));
            duration.setVisibility(VISIBLE);
        } else if (!isActive && finishedAt > startedAt && startedAt > 0) {
            duration.setText(formatDuration(finishedAt - startedAt));
            duration.setVisibility(VISIBLE);
        } else {
            duration.setText("");
            duration.setVisibility(GONE);
        }
        label.setContentDescription(label.getText());
        scheduleTick();
    }

    private void toggle() {
        if (env == null) {
            return;
        }
        expanded = !expanded;
        env.disclosure.setExplicit(rowKey + ":process", expanded);
        applyExpanded();
        updateHeader();
    }

    /**
     * Live turns open the panel so the running tool feed is watchable, then fold back to the
     * summary once the turn ends. An explicit user toggle wins and survives the state change.
     */
    private boolean resolveExpanded(String key, boolean failed) {
        Boolean choice = env.disclosure.explicit(key + ":process");
        if (choice != null) {
            return choice;
        }
        return processAutoExpand() || failed || active();
    }

    private void applyExpanded() {
        chevron.setIconType(expanded ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        body.setVisibility(expanded ? VISIBLE : GONE);
    }

    private void renderItems(TurnAssembler.Row row, String key) {
        List<String> order = new ArrayList<>();
        for (TurnAssembler.Item item : row.process) {
            String itemKey = key + ":" + item.key;
            order.add(itemKey);
            String signal = signal(item);
            View view = children.get(itemKey);
            boolean rebuild = view == null || !signal.equals(signals.get(itemKey));
            if (rebuild) {
                view = create(item, view, itemKey);
                children.put(itemKey, view);
                signals.put(itemKey, signal);
            }
            if (view.getParent() != body || body.indexOfChild(view) != order.size() - 1) {
                if (view.getParent() == body) {
                    body.removeView(view);
                }
                body.addView(view, Math.min(order.size() - 1, body.getChildCount()));
            }
        }
        for (String stale : new ArrayList<>(children.keySet())) {
            if (!order.contains(stale)) {
                View view = children.remove(stale);
                signals.remove(stale);
                markdowns.remove(view);
                if (view != null && view.getParent() == body) {
                    body.removeView(view);
                }
            }
        }
    }

    private View create(TurnAssembler.Item item, View existing, String key) {
        if (item.kind == TurnAssembler.ITEM_REASONING) {
            ReasoningBlockView block = existing instanceof ReasoningBlockView
                    ? (ReasoningBlockView) existing : new ReasoningBlockView(getContext(), tokens);
            block.bind(key, item.text, live, env.preferences.thinkingScroll(),
                    env.preferences.thinkingAutoExpand(), env.disclosure);
            return block;
        }
        if (item.kind == TurnAssembler.ITEM_TOOLS) {
            ToolCallGroupView group = existing instanceof ToolCallGroupView
                    ? (ToolCallGroupView) existing
                    : new ToolCallGroupView(getContext(), tokens);
            group.setProvider(env.provider);
            group.setDiffLookup(env.diffLookup);
            if (existing == null) {
                group.setExpanded(env.disclosure.isOpen(key, false));
                group.setOnExpandedChanged(open ->
                        env.disclosure.setOpen(key, false, open));
            }
            group.bind(item.tools, tokens, running);
            return group;
        }
        if (item.kind == TurnAssembler.ITEM_COMPACT) {
            CompactBlockView compact = existing instanceof CompactBlockView
                    ? (CompactBlockView) existing : new CompactBlockView(getContext(), tokens);
            compact.bind(item.status);
            return compact;
        }
        MarkdownLite markdown = existing instanceof MarkdownLite
                ? (MarkdownLite) existing : new MarkdownLite(getContext(), tokens);
        markdowns.put(markdown, item.text);
        markdown.setCodeWrapEnabled(env.preferences.codeWrap());
        markdown.setLinkHandler(env.links);
        markdown.render(item.text, tokens);
        return markdown;
    }

    private void applyMarkdownPreferences() {
        for (MarkdownLite markdown : markdowns.keySet()) {
            markdown.setCodeWrapEnabled(env.preferences.codeWrap());
            markdown.setLinkHandler(env.links);
        }
    }

    private boolean processAutoExpand() {
        UiPreferences preferences = env.preferences;
        return preferences.processAutoExpand();
    }

    private static String signal(TurnAssembler.Item item) {
        StringBuilder signal = new StringBuilder().append(item.kind).append('\u0001')
                .append(item.text).append('\u0001').append(item.status);
        for (ToolCallEntry entry : item.tools) {
            signal.append('\u0001').append(entry.callId).append('/').append(entry.name)
                    .append('/').append(entry.result.length()).append('/')
                    .append(entry.failed).append('/').append(entry.running);
        }
        return signal.toString();
    }

    static String formatDuration(long elapsedMillis) {
        long seconds = Math.max(0L, elapsedMillis) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m " + seconds % 60L + "s";
        }
        return minutes / 60L + "h " + minutes % 60L + "m " + seconds % 60L + "s";
    }
}
