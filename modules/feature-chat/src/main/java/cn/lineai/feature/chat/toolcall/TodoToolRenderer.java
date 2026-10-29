package cn.lineai.feature.chat.toolcall;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * TODO list card (legacy {@code ToolCallTodoView}): no header row, just the 44dp item rows, or the
 * error output when the update failed.
 */
final class TodoToolRenderer implements ToolCardRenderer {

    static final TodoToolRenderer INSTANCE = new TodoToolRenderer();

    private TodoToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        frame.showBodyAlways();
        if (entry.failed) {
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, entry.result.isEmpty()
                    ? frame.context().getString(R.string.tool_call_status_failed)
                    : entry.result, 240, t -> t.danger);
            frame.body.addView(box, frame.fill());
            return;
        }
        ToolJsonArray items = ToolCardSupport.arguments(entry).array("items");
        if (items == null || items.length() == 0) {
            TextView empty = frame.text(frame.context().getString(R.string.tool_call_todo_empty),
                    Tokens.FONT_XS, t -> t.textTertiary, Typeface.NORMAL);
            Ui.padding(empty, 16, 12, 16, 12);
            frame.body.addView(empty, frame.fill());
            return;
        }
        LinearLayout list = frame.column();
        for (int i = 0; i < items.length(); i++) {
            ToolJson item = items.object(i);
            if (item == null) continue;
            String content = item.text("content").trim();
            if (content.isEmpty()) continue;
            String status = item.text("status", ToolRunStatus.PENDING.wire);
            list.addView(todoRow(frame, content, status),
                    new LinearLayout.LayoutParams(-1, Ui.dp(frame.context(), 44)));
        }
        frame.body.addView(list, frame.withPadding(0, 12, 0, 12));
    }

    private View todoRow(ToolCardFrame frame, String content, String status) {
        LinearLayout row = frame.row();
        row.setMinimumHeight(Ui.dp(frame.context(), 44));
        boolean done = ToolRunStatus.COMPLETED.matches(status);
        boolean active = ToolRunStatus.IN_PROGRESS.matches(status);
        View indicator;
        if (done) {
            final IconButton check = new IconButton(frame.context(), Icons.CHECK);
            check.setIconSizeDp(14, 12);
            check.setClickable(false);
            frame.addTokenApplier(() -> check.setIconColor(frame.tokens().success));
            indicator = check;
        } else {
            final View circle = new View(frame.context());
            frame.addTokenApplier(() -> {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(android.graphics.Color.TRANSPARENT);
                drawable.setStroke(Ui.dp(frame.context(), 2),
                        active ? frame.tokens().accent : frame.tokens().textTertiary);
                circle.setBackground(drawable);
            });
            indicator = circle;
        }
        row.addView(indicator, new LinearLayout.LayoutParams(Ui.dp(frame.context(), 14),
                Ui.dp(frame.context(), 14)));
        ToolCardFrame.Tint tint = t -> done ? t.textTertiary : t.text;
        TextView text = frame.text(content, Tokens.FONT_SM, tint, Typeface.NORMAL);
        text.setPaintFlags(done
                ? text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams params = frame.grow();
        params.leftMargin = Ui.dp(frame.context(), 8);
        row.addView(text, params);
        return row;
    }

    @Override
    public boolean expandable(ToolCallEntry entry, ToolPresentation presentation) {
        return false;
    }
}
