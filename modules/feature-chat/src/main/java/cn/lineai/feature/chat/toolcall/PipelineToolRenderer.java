package cn.lineai.feature.chat.toolcall;

import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Agent pipeline card: git-branch ring, stage counters and one row per pipeline stage. */
final class PipelineToolRenderer implements ToolCardRenderer {

    static final PipelineToolRenderer INSTANCE = new PipelineToolRenderer();

    private PipelineToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        PipelineProgress progress = PipelineProgress.parse(entry);
        boolean complete = progress.complete;
        LinearLayout row = frame.row();
        row.setMinimumHeight(Ui.dp(frame.context(), 48));
        Ui.padding(row, 12, 8, 12, 8);
        row.addView(frame.ringWell(Icons.GIT_BRANCH, 15, 30, t -> t.accent, t -> t.codeBorder));
        LinearLayout block = frame.column();
        TextView title = frame.text(frame.context().getString(R.string.tool_call_pipeline_title),
                Tokens.FONT_SM, t -> t.text, Typeface.BOLD);
        block.addView(title, frame.fill());
        TextView summary = frame.text(summary(frame, progress), 11, t -> t.textTertiary,
                Typeface.BOLD);
        LinearLayout.LayoutParams summaryParams = frame.fill();
        summaryParams.topMargin = Ui.dp(frame.context(), 2);
        block.addView(summary, summaryParams);
        LinearLayout.LayoutParams blockParams = frame.grow();
        blockParams.leftMargin = Ui.dp(frame.context(), 10);
        row.addView(block, blockParams);

        ToolCardFrame.Tint statusTint = t -> progress.error ? t.danger
                : complete ? t.success : t.accent;
        if (!complete) {
            row.addView(frame.spinner(16, statusTint), frame.wrap());
        }
        TextView status = frame.text(frame.context().getString(complete
                        ? (progress.error ? R.string.tool_call_status_failed
                        : R.string.tool_call_status_done)
                        : R.string.tool_call_status_running),
                11, statusTint, Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = frame.wrap();
        statusParams.leftMargin = Ui.dp(frame.context(), 6);
        row.addView(status, statusParams);
        row.addView(frame.chevron(frame.expanded()));
        row.setOnClickListener(v -> frame.requestToggle());
        frame.header.addView(row, frame.fill());

        if (!frame.expanded()) return;
        frame.body.addView(frame.divider(), new LinearLayout.LayoutParams(-1, 1));
        LinearLayout list = frame.column();
        Ui.padding(list, 8, 8, 8, 8);
        if (progress.stages.isEmpty()) {
            if (!progress.output.isEmpty()) {
                list.addView(frame.markdown(progress.output), frame.fill());
            } else {
                list.addView(frame.text(frame.context().getString(
                        R.string.tool_call_pipeline_running), Tokens.FONT_XS,
                        t -> t.textTertiary, Typeface.NORMAL), frame.fill());
            }
        }
        for (PipelineProgress.Stage stage : progress.stages) {
            LinearLayout stageRow = stage(frame, stage);
            LinearLayout.LayoutParams stageParams = frame.fill();
            stageParams.bottomMargin = Ui.dp(frame.context(), 8);
            list.addView(stageRow, stageParams);
        }
        frame.body.addView(list, frame.fill());
    }

    private LinearLayout stage(final ToolCardFrame frame, PipelineProgress.Stage stage) {
        LinearLayout box = frame.column();
        final int typeColor = "explore".equals(normalizeType(stage.type)) ? 0 : 1;
        ToolCardFrame.Tint ring = t -> typeColor == 0 ? t.accent : t.danger;
        frame.addTokenApplier(() -> box.setBackground(
                cn.lineai.ui.kit.Ui.roundedStroke(frame.context(), frame.tokens().codeBg, 8,
                        frame.tokens().codeBorder)));
        Ui.padding(box, 8, 8, 8, 8);

        LinearLayout header = frame.row();
        header.addView(frame.ringWell(Icons.BOT, 12, 24, ring, ring));
        LinearLayout block = frame.column();
        TextView title = frame.text(stage.name(), Tokens.FONT_SM, t -> t.text, Typeface.BOLD);
        title.setSingleLine(true);
        block.addView(title, frame.fill());
        LinearLayout meta = frame.row();
        meta.addView(frame.pill(frame.context().getString(
                "explore".equals(normalizeType(stage.type))
                        ? R.string.tool_call_agent_type_explore
                        : R.string.tool_call_agent_type_coding), ring, t -> t.codeBg),
                frame.wrap());
        if (!stage.id.isEmpty()) {
            TextView id = frame.pill(stage.id, t -> t.textTertiary, t -> t.surfaceLight);
            LinearLayout.LayoutParams idParams = frame.wrap();
            idParams.leftMargin = Ui.dp(frame.context(), 4);
            meta.addView(id, idParams);
        }
        for (String dependency : stage.dependencies) {
            TextView dep = frame.pill(dependency, t -> t.success, t -> t.surfaceLight);
            LinearLayout.LayoutParams depParams = frame.wrap();
            depParams.leftMargin = Ui.dp(frame.context(), 4);
            meta.addView(dep, depParams);
        }
        LinearLayout.LayoutParams metaParams = frame.fill();
        metaParams.topMargin = Ui.dp(frame.context(), 3);
        block.addView(meta, metaParams);
        LinearLayout.LayoutParams blockParams = frame.grow();
        blockParams.leftMargin = Ui.dp(frame.context(), 8);
        header.addView(block, blockParams);
        header.addView(statusLabel(frame, stage), frame.wrap());
        box.addView(header, frame.fill());

        if (!stage.thinking.isEmpty()) {
            box.addView(frame.text(stage.thinking, Tokens.FONT_XS, t -> t.textTertiary,
                    Typeface.NORMAL), frame.withTop(6));
        }
        if (!stage.output.isEmpty()) {
            box.addView(frame.markdown(stage.output), frame.withTop(6));
        }
        NestedToolCards.add(frame, box, stage.toolCalls);
        return box;
    }

    private TextView statusLabel(ToolCardFrame frame, PipelineProgress.Stage stage) {
        int res;
        if (stage.error || ToolRunStatus.ERROR.matches(stage.status)) {
            res = R.string.tool_call_status_failed;
        }
        else if (ToolRunStatus.PENDING.matches(stage.status)) {
            res = R.string.tool_call_status_pending_review;
        }
        else if (stage.running()) res = R.string.tool_call_status_running;
        else if (ToolRunStatus.DONE.matches(stage.status)) res = R.string.tool_call_status_done;
        else res = R.string.tool_call_pipeline_status_waiting;
        ToolCardFrame.Tint tint = t -> stage.error || ToolRunStatus.ERROR.matches(stage.status)
                ? t.danger
                : ToolRunStatus.PENDING.matches(stage.status) ? t.warning
                : stage.running() ? t.accent
                : ToolRunStatus.DONE.matches(stage.status) ? t.success : t.textTertiary;
        return frame.text(frame.context().getString(res), 11, tint, Typeface.BOLD);
    }

    private String summary(ToolCardFrame frame, PipelineProgress progress) {
        StringBuilder builder = new StringBuilder();
        append(builder, frame.context().getString(
                R.string.tool_call_pipeline_completed, progress.completed));
        if (progress.running > 0) {
            append(builder, frame.context().getString(
                    R.string.tool_call_pipeline_summary_running, progress.running));
        }
        if (progress.pending > 0) {
            append(builder, frame.context().getString(
                    R.string.tool_call_pipeline_summary_pending_review, progress.pending));
        }
        int waiting = Math.max(0, progress.stages.size() - progress.completed
                - progress.running - progress.pending - progress.failed);
        if (waiting > 0) {
            append(builder, frame.context().getString(
                    R.string.tool_call_pipeline_summary_waiting, waiting));
        }
        if (progress.failed > 0) {
            append(builder, frame.context().getString(
                    R.string.tool_call_pipeline_summary_failed, progress.failed));
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append(" \u00b7 ");
        builder.append(value);
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value)
                || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.isEmpty() ? "explore" : value;
    }

    @Override
    public boolean initiallyExpanded() {
        return true;
    }
}
