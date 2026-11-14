package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.AgentStatus;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Workspace-header companion for the agent drawer: a compact chip showing the live sub agent
 * count that expands into one row per session (title top-left, status pill bottom-left, right
 * arrow). The root conversation is always the first row and the default selection.
 */
final class DrawerAgentsView extends LinearLayout {

    /** Row selection callback; the root row reports an empty id. */
    interface OnSelect {
        void onSelect(String agentId);
    }

    private final Tokens tokens;
    private final OnSelect onSelect;
    private final LinearLayout chipRow;
    private final TextView chipLabel;
    private final IconButton chipGlyph;
    private final IconButton chevron;
    private final LinearLayout content;

    private List<AgentEntry> entries = new ArrayList<>();
    private String selectedId = "";
    private boolean expanded;

    DrawerAgentsView(Context context, Tokens tokens, OnSelect onSelect) {
        super(context);
        this.tokens = tokens;
        this.onSelect = onSelect;
        setOrientation(VERTICAL);

        chipRow = new LinearLayout(context);
        chipRow.setOrientation(HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        chipRow.setClickable(true);
        chipRow.setFocusable(true);
        Ui.padding(chipRow, 10, 6, 10, 6);
        chipGlyph = glyph(context, Icons.BOT, 15);
        chipRow.addView(chipGlyph, lp(15, 15));
        chipLabel = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        chipLabel.setSingleLine(true);
        chipLabel.setEllipsize(TextUtils.TruncateAt.END);
        chipRow.addView(chipLabel, margins(-2, -2, 6, 0, 0, 0));
        chevron = glyph(context, Icons.CHEVRON_DOWN, 12);
        chipRow.addView(chevron, margins(12, 12, 6, 0, 0, 0));
        addView(chipRow, lp(-2, -2));
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setVisibility(GONE);
        addView(content, lp(-1, -2));
        chipRow.setOnClickListener(v -> {
            expanded = !expanded;
            restyle();
        });
        restyle();
    }

    /** Updates the chip count and, when expanded, the session rows. */
    void setAgents(List<AgentEntry> value, String selectedAgentId) {
        entries = value == null ? new ArrayList<AgentEntry>() : new ArrayList<>(value);
        selectedId = selectedAgentId == null ? "" : selectedAgentId;
        render();
        restyle();
    }

    /** Restyles the chip and rows in place. */
    void applyTokens(Tokens current) {
        restyle();
    }

    /** Rebuilds the expandable rows and applies the chip copy. */
    private void render() {
        chipLabel.setText(getContext().getString(R.string.drawer_agent_chip, entries.size()));
        content.setVisibility(expanded ? VISIBLE : GONE);
        content.removeAllViews();
        if (!expanded) {
            return;
        }
        content.addView(row(getContext().getString(R.string.drawer_agent_root), "", null));
        for (AgentEntry entry : entries) {
            content.addView(row(entry.name.isEmpty() ? entry.id : entry.name, entry.id,
                    entry.status));
        }
    }

    /** Applies token colors in place; rows are restyled without being rebuilt. */
    private void restyle() {
        chipRow.setBackground(Ui.rounded(getContext(), tokens.surface, 10));
        chipLabel.setTextColor(tokens.textTertiary);
        chipGlyph.setIconColor(tokens.textSecondary);
        chevron.setIconColor(tokens.textTertiary);
        content.setVisibility(expanded ? VISIBLE : GONE);
        if (expanded) {
            render();
        }
    }

    private LinearLayout row(String title, String agentId, AgentStatus status) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 8, 8, 8, 8);
        boolean selected = agentId.equals(selectedId);
        row.setBackground(selected ? Ui.rounded(context, tokens.surfaceElevated, 8) : null);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        TextView name = Ui.bold(context, title, Tokens.FONT_SM,
                selected ? tokens.text : tokens.textSecondary);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(name, lp(-1, -2));
        if (status != null) {
            TextView pill = Ui.text(context, statusText(status), Tokens.FONT_XS,
                    statusColor(status), Typeface.BOLD);
            text.addView(pill, margins(-2, -2, 0, 2, 0, 0));
        }
        row.addView(text, lp(0, -2, 1f));
        IconButton arrow = glyph(context, Icons.CHEVRON_RIGHT, 12);
        arrow.setIconColor(tokens.textTertiary);
        row.addView(arrow, lp(12, 12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onSelect.onSelect(agentId));
        return row;
    }

    private String statusText(AgentStatus status) {
        switch (status) {
            case RUNNING:
                return getContext().getString(R.string.tool_call_status_running);
            case FAILED:
                return getContext().getString(R.string.tool_call_status_failed);
            case WAITING_INPUT:
                return getContext().getString(R.string.tool_call_status_pending_review);
            case INTERRUPTED:
                return getContext().getString(R.string.tool_call_status_interrupted);
            default:
                return getContext().getString(R.string.tool_call_status_done);
        }
    }

    private int statusColor(AgentStatus status) {
        switch (status) {
            case RUNNING:
            case WAITING_INPUT:
                return tokens.accent;
            case FAILED:
            case INTERRUPTED:
                return tokens.danger;
            default:
                return tokens.textTertiary;
        }
    }

    private static IconButton glyph(Context context, int icon, int sizeDp) {
        IconButton button = new IconButton(context, icon);
        button.setIconSizeDp(sizeDp, sizeDp);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setClickable(false);
        return button;
    }

    private static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private static LinearLayout.LayoutParams margins(int width, int height,
                                                     int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}
