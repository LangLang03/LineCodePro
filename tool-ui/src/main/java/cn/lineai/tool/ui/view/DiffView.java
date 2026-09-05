package cn.lineai.tool.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class DiffView extends HorizontalScrollView {
    private final LinearLayout content;
    public DiffView(Context context) {
        super(context); setFillViewport(true); setHorizontalScrollBarEnabled(true);
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(-2, -2));
    }
    public void bind(String before, String after) { bind(DiffLines.calculate(before, after)); }
    public void bind(DiffLines diff) {
        content.removeAllViews();
        int displayed = 0, omitted = 0;
        boolean[] visible = new boolean[diff.lines.size()];
        for (int i = 0; i < diff.lines.size(); i++) if (diff.lines.get(i).kind != 0) {
            for (int j = Math.max(0, i - 3); j < Math.min(visible.length, i + 4); j++) visible[j] = true;
        }
        for (int i = 0; i < diff.lines.size(); i++) {
            if (!visible[i] && diff.added + diff.removed > 0) { omitted++; continue; }
            if (displayed >= 200) {
                TextView more = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_diff_truncated, diff.lines.size()), 12, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
                LineTheme.padding(more, 14, 12, 14, 12); content.addView(more); break;
            }
            if (omitted > 0) { addGap(); omitted = 0; }
            DiffLines.Line line = diff.lines.get(i);
            content.addView(lineView(line), new LinearLayout.LayoutParams(-1, -2)); displayed++;
            if (!line.terminated) {
                TextView note = LineTheme.text(getContext(), getContext().getString(R.string.tool_call_diff_no_newline), 12, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
                LineTheme.padding(note, 14, 4, 14, 4); content.addView(note, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        if (omitted > 0) addGap();
    }
    private void addGap() {
        TextView gap = LineTheme.text(getContext(), "⋯", 13, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(gap, 18, 4, 0, 4); content.addView(gap, new LinearLayout.LayoutParams(-1, -2));
    }
    private View lineView(DiffLines.Line line) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(getContext(), 26));
        int color = line.kind > 0 ? LineTheme.DIFF_ADD_TEXT : line.kind < 0 ? LineTheme.DIFF_DEL_TEXT : LineTheme.TEXT_SECONDARY;
        if (line.kind != 0) row.setBackgroundColor(line.kind > 0 ? LineTheme.DIFF_ADD_BG : LineTheme.DIFF_DEL_BG);
        View marker = new View(getContext());
        marker.setBackgroundColor(line.kind == 0 ? android.graphics.Color.TRANSPARENT : line.kind > 0 ? LineTheme.SUCCESS : LineTheme.DANGER);
        row.addView(marker, new LinearLayout.LayoutParams(LineTheme.dp(getContext(), 3), -1));
        TextView number = cell(String.valueOf(line.number), color); number.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LineTheme.padding(number, 2, 3, 10, 3);
        row.addView(number, new LinearLayout.LayoutParams(LineTheme.dp(getContext(), 42), -1));
        String text = line.text.length() > 2000 ? line.text.substring(0, 2000) + "…" : line.text;
        TextView code = cell(text, color); LineTheme.padding(code, 4, 3, 14, 3);
        row.addView(code, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }
    private TextView cell(String text, int color) {
        TextView view = LineTheme.text(getContext(), text, 13, color, Typeface.NORMAL);
        view.setTypeface(Typeface.MONOSPACE); view.setSingleLine(true); return view;
    }
}
