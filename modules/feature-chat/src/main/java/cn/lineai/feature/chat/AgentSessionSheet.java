package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.AgentStatus;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Session picker behind the header's agent chip: the root conversation plus one row per sub
 * agent with its live status. Selecting a row switches the chat view.
 */
final class AgentSessionSheet {

    private AgentSessionSheet() {
    }

    /**
     * Opens the sheet.
     *
     * @param context hosting context
     * @param tokens current theme tokens
     * @param agents the live/persisted sub agent entries
     * @param selectedAgentId currently viewed agent id; empty for the root conversation
     * @param onSelect receives the chosen agent id (empty for root) and closes the sheet
     */
    static void show(Context context, Tokens tokens, List<AgentEntry> agents,
                     String selectedAgentId, Consumer<String> onSelect) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(context, tokens.surface, 24));
        Ui.padding(panel, 8, 8, 8, 24);

        View grabber = new View(context);
        grabber.setBackground(Ui.rounded(context, tokens.borderLight, 2));
        LinearLayout.LayoutParams grabberParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 4));
        grabberParams.gravity = Gravity.CENTER_HORIZONTAL;
        grabberParams.topMargin = Ui.dp(context, 8);
        panel.addView(grabber, grabberParams);

        TextView title = Ui.bold(context, context.getString(R.string.chat_agents_title),
                Tokens.FONT_MD, tokens.text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.leftMargin = Ui.dp(context, 16);
        titleParams.topMargin = Ui.dp(context, 14);
        titleParams.bottomMargin = Ui.dp(context, 6);
        panel.addView(title, titleParams);

        panel.addView(row(context, tokens, context.getString(R.string.drawer_agent_root),
                "", null, selectedAgentId.isEmpty(), onSelect), new LinearLayout.LayoutParams(-1, -2));
        for (AgentEntry entry : agents) {
            panel.addView(row(context, tokens, entry.name.isEmpty() ? entry.id : entry.name,
                    entry.id, entry.status, entry.id.equals(selectedAgentId), onSelect),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        Sheets.showBottom(context, tokens, panel, 400, 200);
    }

    /** One selectable row: title, optional status word and a check when selected. */
    private static LinearLayout row(Context context, Tokens tokens, String title, String agentId,
                                    AgentStatus status, boolean selected,
                                    Consumer<String> onSelect) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 56));
        Ui.padding(row, 16, 10, 16, 10);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(selected ? Ui.rounded(context, tokens.surfaceElevated, 10) : null);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(context, title, Tokens.FONT_MD, tokens.text, Typeface.NORMAL);
        name.setSingleLine(true);
        labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (status != null) {
            TextView state = Ui.text(context, statusText(context, tokens, status),
                    Tokens.FONT_XS, statusColor(tokens, status), Typeface.BOLD);
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
            stateParams.topMargin = Ui.dp(context, 2);
            labels.addView(state, stateParams);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView check = Ui.text(context, selected ? "\u2713" : "", Tokens.FONT_MD,
                tokens.accent, Typeface.BOLD);
        row.addView(check, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> {
            Sheets.dismiss(row);
            onSelect.accept(agentId);
        });
        return row;
    }

    private static String statusText(Context context, Tokens tokens, AgentStatus status) {
        switch (status) {
            case RUNNING:
                return context.getString(R.string.tool_call_status_running);
            case WAITING_INPUT:
                return context.getString(R.string.tool_call_status_pending_review);
            case FAILED:
            case INTERRUPTED:
                return context.getString(status == AgentStatus.INTERRUPTED
                        ? R.string.tool_call_status_interrupted
                        : R.string.tool_call_status_failed);
            default:
                return context.getString(R.string.tool_call_status_done);
        }
    }

    private static int statusColor(Tokens tokens, AgentStatus status) {
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
}
