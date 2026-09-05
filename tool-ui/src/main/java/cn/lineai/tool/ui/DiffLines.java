package cn.lineai.tool.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded-memory line diff. Large replacement regions remain valid, non-minimal edits. */
public final class DiffLines {
    public static final class Line {
        public final int kind; // -1 removed, 0 unchanged, 1 added
        public final int number;
        public final String text;
        public final boolean terminated;
        Line(int kind, int number, String text) {
            this.kind = kind; this.number = number; this.terminated = text.endsWith("\n");
            this.text = terminated ? text.substring(0, text.length() - 1) : text;
        }
    }
    public final List<Line> lines;
    public final int added;
    public final int removed;
    private DiffLines(List<Line> lines) {
        this.lines = Collections.unmodifiableList(lines);
        int plus = 0, minus = 0;
        for (Line line : lines) { if (line.kind == 1) plus++; else if (line.kind == -1) minus++; }
        added = plus; removed = minus;
    }
    public static DiffLines calculate(String oldText, String newText) {
        String[] a = split(oldText), b = split(newText);
        int prefix = 0, suffix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) prefix++;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - suffix - 1].equals(b[b.length - suffix - 1])) suffix++;
        ArrayList<Line> lines = new ArrayList<>();
        for (int i = 0; i < prefix; i++) lines.add(new Line(0, i + 1, a[i]));
        int m = a.length - prefix - suffix, n = b.length - prefix - suffix;
        if ((long) (m + 1) * (n + 1) <= 1_000_000) {
            int[][] lcs = new int[m + 1][n + 1];
            for (int i = m - 1; i >= 0; i--) for (int j = n - 1; j >= 0; j--) {
                lcs[i][j] = a[prefix + i].equals(b[prefix + j]) ? 1 + lcs[i + 1][j + 1] : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
            int i = 0, j = 0;
            while (i < m || j < n) {
                if (i < m && j < n && a[prefix + i].equals(b[prefix + j])) {
                    lines.add(new Line(0, prefix + j + 1, b[prefix + j])); i++; j++;
                } else if (i < m && (j == n || lcs[i + 1][j] >= lcs[i][j + 1])) {
                    lines.add(new Line(-1, prefix + i + 1, a[prefix + i])); i++;
                } else { lines.add(new Line(1, prefix + j + 1, b[prefix + j])); j++; }
            }
        } else {
            for (int i = 0; i < m; i++) lines.add(new Line(-1, prefix + i + 1, a[prefix + i]));
            for (int j = 0; j < n; j++) lines.add(new Line(1, prefix + j + 1, b[prefix + j]));
        }
        for (int j = b.length - suffix; j < b.length; j++) lines.add(new Line(0, j + 1, b[j]));
        return new DiffLines(lines);
    }
    private static String[] split(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        // A terminating newline is not an extra empty source line.
        String normalized = text.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        boolean terminated = normalized.endsWith("\n");
        if (terminated) lines = java.util.Arrays.copyOf(lines, lines.length - 1);
        for (int i = 0; i < lines.length; i++) if (i < lines.length - 1 || terminated) lines[i] += "\n";
        return lines;
    }
}
