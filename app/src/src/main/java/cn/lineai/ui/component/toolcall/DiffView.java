package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.List;

public final class DiffView extends HorizontalScrollView {
    private static final int MAX_LINES = 50;
    private final LinearLayout content;

    public DiffView(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void bind(String oldContent, String newContent) {
        content.removeAllViews();
        List<DiffLine> lines = computeDiff(oldContent == null ? "" : oldContent, newContent == null ? "" : newContent);
        int displayCount = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < displayCount; i++) {
            content.addView(lineView(lines.get(i)), new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        if (lines.size() > MAX_LINES) {
            TextView truncated = LineTheme.text(getContext(), "... (diff 已截断，共 " + lines.size() + " 行)",
                    LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.ITALIC);
            truncated.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
            LineTheme.padding(truncated, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
            content.addView(truncated, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private LinearLayout lineView(DiffLine line) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumWidth(getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 64));
        if (line.type == DiffLine.ADD) {
            row.setBackgroundColor(LineTheme.DIFF_ADD_BG);
        } else if (line.type == DiffLine.REMOVE) {
            row.setBackgroundColor(LineTheme.DIFF_DEL_BG);
        }
        LineTheme.padding(row, LineTheme.SM, 1, LineTheme.SM, 1);

        row.addView(codeCell(line.oldLine > 0 ? String.valueOf(line.oldLine) : "", LineTheme.TEXT_TERTIARY, 28, Gravity.END));
        row.addView(codeCell(line.newLine > 0 ? String.valueOf(line.newLine) : "", LineTheme.TEXT_TERTIARY, 28, Gravity.END));
        int textColor = line.type == DiffLine.ADD ? LineTheme.DIFF_ADD_TEXT
                : line.type == DiffLine.REMOVE ? LineTheme.DIFF_DEL_TEXT
                : LineTheme.TEXT;
        String prefix = line.type == DiffLine.ADD ? "+" : line.type == DiffLine.REMOVE ? "-" : " ";
        row.addView(codeCell(prefix, textColor, 12, Gravity.START));
        row.addView(codeCell(line.content, textColor, -1, Gravity.START));
        return row;
    }

    private TextView codeCell(String value, int color, int widthDp, int gravity) {
        TextView view = LineTheme.text(getContext(), value, LineTheme.FONT_XS, color, Typeface.NORMAL);
        view.setTypeface(Typeface.MONOSPACE);
        view.setGravity(gravity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                widthDp > 0 ? LineTheme.dp(getContext(), widthDp) : LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = LineTheme.dp(getContext(), widthDp > 0 ? 4 : 0);
        view.setLayoutParams(params);
        return view;
    }

    private List<DiffLine> computeDiff(String oldText, String newText) {
        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);
        int m = oldLines.length;
        int n = newLines.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                dp[i][j] = oldLines[i - 1].equals(newLines[j - 1])
                        ? dp[i - 1][j - 1] + 1
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }

        ArrayList<DiffLine> reversed = new ArrayList<>();
        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                reversed.add(new DiffLine(DiffLine.CONTEXT, oldLines[i - 1], i, j));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reversed.add(new DiffLine(DiffLine.ADD, newLines[j - 1], 0, j));
                j--;
            } else {
                reversed.add(new DiffLine(DiffLine.REMOVE, oldLines[i - 1], i, 0));
                i--;
            }
        }

        ArrayList<DiffLine> lines = new ArrayList<>(reversed.size());
        for (int k = reversed.size() - 1; k >= 0; k--) {
            lines.add(reversed.get(k));
        }
        return lines;
    }

    private static final class DiffLine {
        static final int ADD = 1;
        static final int REMOVE = 2;
        static final int CONTEXT = 3;

        final int type;
        final String content;
        final int oldLine;
        final int newLine;

        DiffLine(int type, String content, int oldLine, int newLine) {
            this.type = type;
            this.content = content == null ? "" : content;
            this.oldLine = oldLine;
            this.newLine = newLine;
        }
    }
}
