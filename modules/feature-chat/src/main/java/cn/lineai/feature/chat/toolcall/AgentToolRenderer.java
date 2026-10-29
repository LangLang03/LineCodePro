package cn.lineai.feature.chat.toolcall;

import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Agent call card: bot ring, type/agent-id pills, status and nested tool results. */
final class AgentToolRenderer implements ToolCardRenderer {

    static final AgentToolRenderer INSTANCE = new AgentToolRenderer();

    private AgentToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        String task = arguments.text("task").trim();
        if (task.isEmpty()) task = ToolCardSupport.summary(entry, presentation);
        AgentProgress progress = AgentProgress.parse(entry, presentation.title);
        final boolean explore = "explore".equals(progress.normalizedType());
        ToolCardFrame.Tint ring = t -> explore ? t.accent : t.danger;
        ToolCardFrame.Tint statusTint = t -> progress.error() || entry.failed ? t.danger
                : progress.pending() ? t.warning
                : progress.running() ? t.accent : t.success;

        LinearLayout row = frame.row();
        row.setMinimumHeight(Ui.dp(frame.context(), 48));
        Ui.padding(row, 12, 8, 12, 8);
        row.addView(frame.ringWell(Icons.BOT, 14, 28, ring, ring));
        LinearLayout block = frame.column();
        TextView title = frame.text(progress.description, Tokens.FONT_SM, t -> t.text,
                Typeface.BOLD);
        title.setSingleLine(true);
        block.addView(title, frame.fill());
        TextView summary = frame.mono(task, Tokens.FONT_XS, t -> t.textSecondary, true);
        LinearLayout.LayoutParams summaryParams = frame.fill();
        summaryParams.topMargin = Ui.dp(frame.context(), 2);
        block.addView(summary, summaryParams);
        LinearLayout.LayoutParams blockParams = frame.grow();
        blockParams.leftMargin = Ui.dp(frame.context(), 10);
        row.addView(block, blockParams);

        if (progress.running()) {
            row.addView(frame.spinner(14, statusTint), frame.wrap());
        }
        int statusRes = progress.error() || entry.failed ? R.string.tool_call_status_failed
                : progress.pending() ? R.string.tool_call_status_pending_review
                : progress.running() ? R.string.tool_call_status_running
                : R.string.tool_call_status_done;
        TextView status = frame.text(frame.context().getString(statusRes), 11, statusTint,
                Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = frame.wrap();
        statusParams.leftMargin = Ui.dp(frame.context(), 6);
        row.addView(status, statusParams);
        row.addView(frame.chevron(frame.expanded()));
        row.setOnClickListener(v -> frame.requestToggle());
        frame.header.addView(row, frame.fill());

        if (!frame.expanded()) return;
        frame.body.addView(frame.divider(), new LinearLayout.LayoutParams(-1, 1));

        LinearLayout content = frame.column();
        LinearLayout meta = frame.row();
        meta.addView(frame.pill(frame.context().getString(progress.typeLabelRes()), ring,
                t -> t.codeBg), frame.wrap());
        if (!progress.agentId.isEmpty()) {
            TextView id = frame.pill(progress.agentId, t -> t.textTertiary,
                    t -> t.surfaceLight);
            LinearLayout.LayoutParams idParams = frame.wrap();
            idParams.leftMargin = Ui.dp(frame.context(), 4);
            meta.addView(id, idParams);
        }
        if (progress.toolCount > 0) {
            TextView count = frame.pill(frame.context().getString(
                    R.string.tool_call_agent_tool_count, progress.toolCount),
                    t -> t.textTertiary, t -> t.surfaceLight);
            LinearLayout.LayoutParams countParams = frame.wrap();
            countParams.leftMargin = Ui.dp(frame.context(), 4);
            meta.addView(count, countParams);
        }
        content.addView(meta, frame.fill());

        if (!progress.thinking.isEmpty()) {
            content.addView(frame.inputLabel(frame.context().getString(
                    progress.running() ? R.string.thinking_label
                            : R.string.thinking_done_label)), frame.withTop(8));
            TextView thinking = frame.mono(progress.thinking, Tokens.FONT_XS,
                    t -> t.textTertiary, false);
            content.addView(thinking, frame.withTop(4));
        }
        if (!progress.output.isEmpty()) {
            content.addView(frame.markdown(progress.output), frame.withTop(8));
        } else if (progress.running()) {
            content.addView(frame.text(frame.context().getString(
                    R.string.tool_call_agent_running), Tokens.FONT_XS,
                    t -> t.textTertiary, Typeface.NORMAL), frame.withTop(8));
        } else if (progress.nestedCalls == null || progress.nestedCalls.length() == 0) {
            content.addView(frame.text(frame.context().getString(progress.error()
                            || entry.failed ? R.string.tool_call_agent_failed
                            : R.string.tool_call_agent_done),
                    Tokens.FONT_XS, t -> progress.error() || entry.failed ? t.danger
                            : t.textSecondary, Typeface.NORMAL), frame.withTop(8));
        }
        NestedToolCards.add(frame, content, progress.nestedCalls);
        Ui.padding(content, 12, 8, 12, 12);
        BoundedScrollView scroll = new BoundedScrollView(frame.context(), 400);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
        frame.body.addView(scroll, frame.fill());
    }

    @Override
    public boolean initiallyExpanded() {
        return true;
    }
}
