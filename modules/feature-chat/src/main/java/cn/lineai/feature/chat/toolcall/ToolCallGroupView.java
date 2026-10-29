package cn.lineai.feature.chat.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import cn.lineai.feature.chat.ToolPresentationProvider;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Collapsed group of consecutive tool calls (legacy {@code ToolTimelineGroup}).
 *
 * <p>Collapsed it is a single 48dp row: terminal glyph, {@code Used N tools} and a chevron.
 * Expanded it lists one {@link ToolCallCardView} per call with a 2dp gap, so a run of calls reads
 * as one continuous list instead of separate cards. The timeline owns the disclosure state so a
 * rebind never loses what the user opened.</p>
 */
public class ToolCallGroupView extends LinearLayout {

    private final LinearLayout header;
    private final LinearLayout cards;
    private final IconButton terminal;
    private final IconButton chevron;
    private final TextView label;
    private final List<ToolCallCardView> cardViews = new ArrayList<>();
    private final List<String> boundIds = new ArrayList<>();
    private ToolPresentationProvider provider;
    private ToolDiffLookup diffLookup;
    private Consumer<Boolean> expansionListener;
    private Tokens tokens;
    private List<ToolCallEntry> entries = Collections.emptyList();
    private boolean expanded;
    private boolean running;

    public ToolCallGroupView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 48));
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> toggleExpanded());
        terminal = new IconButton(context, Icons.TERMINAL);
        terminal.setIconSizeDp(24, 16);
        terminal.setClickable(false);
        header.addView(terminal, new LinearLayout.LayoutParams(Ui.dp(context, 24),
                Ui.dp(context, 32)));
        label = Ui.text(context, "", 14, tokens.textSecondary, Typeface.NORMAL);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 6);
        header.addView(label, labelParams);
        chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconSizeDp(24, 14);
        chevron.setClickable(false);
        header.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 24),
                Ui.dp(context, 32)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        cards = new LinearLayout(context);
        cards.setOrientation(VERTICAL);
        cards.setVisibility(GONE);
        addView(cards, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        applyTokens(tokens);
    }

    public void setProvider(ToolPresentationProvider value) {
        provider = value;
        for (ToolCallCardView card : cardViews) card.setProvider(value);
    }

    public void setDiffLookup(ToolDiffLookup value) {
        diffLookup = value;
        for (ToolCallCardView card : cardViews) card.setDiffLookup(value);
    }

    /** Notifies the timeline whenever the user toggles the group, so state can be persisted. */
    public void setOnExpandedChanged(Consumer<Boolean> listener) {
        expansionListener = listener;
    }

    public void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        applyExpanded();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void bind(List<ToolCallEntry> values, Tokens current, boolean active) {
        tokens = current;
        entries = values == null ? Collections.<ToolCallEntry>emptyList() : values;
        running = active;
        label.setText(getContext().getString(R.string.chat_tools_count, entries.size()));
        syncCards();
        applyTokens(current);
        applyExpanded();
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        label.setTextColor(current.textSecondary);
        terminal.setIconColor(current.textSecondary);
        chevron.setIconColor(current.textTertiary);
        for (ToolCallCardView card : cardViews) card.applyTokens(current);
    }

    private void syncCards() {
        List<String> ids = new ArrayList<>();
        for (ToolCallEntry entry : entries) ids.add(entry.callId);
        if (!ids.equals(boundIds)) {
            cards.removeAllViews();
            cardViews.clear();
            boundIds.clear();
            boundIds.addAll(ids);
        }
        for (int i = 0; i < entries.size(); i++) {
            ToolCallCardView card;
            if (i < cardViews.size()) {
                card = cardViews.get(i);
                if (card.getParent() != cards) {
                    cards.removeView(card);
                    cards.addView(card);
                }
            } else {
                card = new ToolCallCardView(getContext(), tokens);
                card.setProvider(provider);
                card.setDiffLookup(diffLookup);
                cardViews.add(card);
                cards.addView(card, new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT));
            }
            LayoutParams params = (LayoutParams) card.getLayoutParams();
            params.bottomMargin = Ui.dp(getContext(), 2);
            card.setLayoutParams(params);
            card.bind(entries.get(i), tokens);
        }
        while (cardViews.size() > entries.size()) {
            ToolCallCardView removed = cardViews.remove(cardViews.size() - 1);
            cards.removeView(removed);
        }
    }

    private void toggleExpanded() {
        expanded = !expanded;
        applyExpanded();
        if (expansionListener != null) expansionListener.accept(expanded);
    }

    private void applyExpanded() {
        chevron.setIconType(expanded ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        cards.setVisibility(expanded ? VISIBLE : GONE);
    }

    /** Glyph used by the collapsed row; kept in sync with the timeline's terminal styling. */
    public void setTerminalIcon(int iconType) {
        terminal.setIconType(iconType);
    }

    /** True while at least one call in the group is still running. */
    public boolean isRunning() {
        return running;
    }

    /** Exposed for idempotent rebinds: avoids recomputing the label when nothing changed. */
    public String signature() {
        StringBuilder builder = new StringBuilder();
        builder.append(entries.size()).append('|').append(expanded).append('|').append(running);
        for (ToolCallEntry entry : entries) {
            builder.append('|').append(entry.callId)
                    .append(':').append(entry.running ? 1 : 0)
                    .append(':').append(entry.failed ? 1 : 0)
                    .append(':').append(TextUtils.getTrimmedLength(entry.arguments))
                    .append(':').append(TextUtils.getTrimmedLength(entry.result));
        }
        return builder.toString();
    }
}
