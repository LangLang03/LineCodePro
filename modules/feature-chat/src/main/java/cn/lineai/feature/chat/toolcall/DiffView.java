package cn.lineai.feature.chat.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Legacy {@code DiffView}: horizontally scrollable unified diff.
 *
 * <p>Three context lines around every change stay visible while distant unchanged regions collapse
 * into one gap marker, and rendering stops after 200 displayed lines. Colours are re-applied in
 * place so a theme change never rebuilds the rows.</p>
 */
final class DiffView extends HorizontalScrollView {

    private static final int CONTEXT_LINES = 3;
    private static final int MAX_DISPLAYED = 200;

    private final LinearLayout content;
    private final List<LineRow> rows = new ArrayList<>();
    private final List<TextView> mutedLabels = new ArrayList<>();

    DiffView(Context context) {
        super(context);
        setFillViewport(true);
        setHorizontalScrollBarEnabled(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    void bind(DiffLines diff, Tokens tokens) {
        content.removeAllViews();
        rows.clear();
        mutedLabels.clear();
        boolean[] visible = new boolean[diff.lines.size()];
        for (int i = 0; i < diff.lines.size(); i++) {
            if (diff.lines.get(i).kind == DiffLines.Line.UNCHANGED) continue;
            int from = Math.max(0, i - CONTEXT_LINES);
            int to = Math.min(visible.length, i + CONTEXT_LINES + 1);
            for (int around = from; around < to; around++) visible[around] = true;
        }
        int displayed = 0;
        boolean omitted = false;
        for (int i = 0; i < diff.lines.size(); i++) {
            if (!visible[i] && diff.added + diff.removed > 0) {
                omitted = true;
                continue;
            }
            if (displayed >= MAX_DISPLAYED) {
                TextView truncated = Ui.text(getContext(), getContext().getString(
                        R.string.tool_call_diff_truncated, diff.lines.size()),
                        Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
                Ui.padding(truncated, 14, 12, 14, 12);
                mutedLabels.add(truncated);
                content.addView(truncated, new LinearLayout.LayoutParams(-1, -2));
                break;
            }
            if (omitted) {
                addGap();
                omitted = false;
            }
            DiffLines.Line line = diff.lines.get(i);
            addLine(line, tokens);
            displayed++;
            if (!line.terminated) {
                TextView note = Ui.text(getContext(),
                        getContext().getString(R.string.tool_call_diff_no_newline),
                        Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
                Ui.padding(note, 14, 4, 14, 4);
                mutedLabels.add(note);
                content.addView(note, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        if (omitted) addGap();
        apply(tokens);
    }

    void apply(Tokens tokens) {
        for (LineRow row : rows) {
            int textColor = row.kind == DiffLines.Line.ADDED ? tokens.diffAddText
                    : row.kind == DiffLines.Line.REMOVED ? tokens.diffDelText
                    : tokens.textSecondary;
            row.number.setTextColor(textColor);
            row.code.setTextColor(textColor);
            row.marker.setBackgroundColor(row.kind == DiffLines.Line.ADDED ? tokens.success
                    : row.kind == DiffLines.Line.REMOVED ? tokens.danger
                    : android.graphics.Color.TRANSPARENT);
            row.root.setBackgroundColor(row.kind == DiffLines.Line.ADDED ? tokens.diffAddBg
                    : row.kind == DiffLines.Line.REMOVED ? tokens.diffDelBg
                    : android.graphics.Color.TRANSPARENT);
        }
        for (TextView label : mutedLabels) label.setTextColor(tokens.textTertiary);
    }

    private void addGap() {
        TextView gap = Ui.text(getContext(), "\u22ef", Tokens.FONT_SM,
                android.graphics.Color.GRAY, Typeface.NORMAL);
        Ui.padding(gap, 18, 4, 0, 4);
        mutedLabels.add(gap);
        content.addView(gap, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addLine(DiffLines.Line line, Tokens tokens) {
        Context context = getContext();
        LinearLayout root = new LinearLayout(context);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(Ui.dp(context, 26));
        View marker = new View(context);
        root.addView(marker, new LinearLayout.LayoutParams(Ui.dp(context, 3), -1));
        TextView number = cell("" + line.number);
        number.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Ui.padding(number, 2, 3, 10, 3);
        root.addView(number, new LinearLayout.LayoutParams(Ui.dp(context, 42), -1));
        String text = line.text.length() > 2000 ? line.text.substring(0, 2000) + "\u2026"
                : line.text;
        TextView code = cell(text);
        Ui.padding(code, 4, 3, 14, 3);
        root.addView(code, new LinearLayout.LayoutParams(-2, -2));
        rows.add(new LineRow(root, marker, number, code, line.kind));
        content.addView(root, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView cell(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_SM,
                android.graphics.Color.GRAY, Typeface.NORMAL);
        view.setTypeface(Typeface.MONOSPACE);
        view.setSingleLine(true);
        return view;
    }

    private static final class LineRow {
        final View root;
        final View marker;
        final TextView number;
        final TextView code;
        final int kind;

        LineRow(View root, View marker, TextView number, TextView code, int kind) {
            this.root = root;
            this.marker = marker;
            this.number = number;
            this.code = code;
            this.kind = kind;
        }
    }
}
