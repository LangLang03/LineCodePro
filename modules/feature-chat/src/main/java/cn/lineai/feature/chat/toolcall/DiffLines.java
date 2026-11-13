package cn.lineai.feature.chat.toolcall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded-memory line diff, a direct port of the legacy {@code DiffLines} helper.
 *
 * <p>Large replacement regions stay valid but non-minimal: above a million LCS cells the middle
 * region degrades to "all removed, then all added" so a huge file never blocks the UI thread.</p>
 */
final class DiffLines {

    /** One diff line with its kind and rendered line number. */
    static final class Line {
        /** Kind token: removed line. */
        static final int REMOVED = -1;
        /** Kind token: line present in both versions. */
        static final int UNCHANGED = 0;
        /** Kind token: added line. */
        static final int ADDED = 1;

        /** One of {@code REMOVED}/{@code UNCHANGED}/{@code ADDED}. */
        final int kind;
        /** Line number in the receiving (old for removed, new otherwise) version. */
        final int number;
        /** Line content without the trailing newline. */
        final String text;
        /** True when the source line ended with a newline. */
        final boolean terminated;

        /** Splits off the trailing newline for display. */
        Line(int kind, int number, String text) {
            this.kind = kind;
            this.number = number;
            boolean ended = text.endsWith("\n");
            this.terminated = ended;
            this.text = ended ? text.substring(0, text.length() - 1) : text;
        }
    }

    /** Diff lines in render order; unmodifiable. */
    final List<Line> lines;
    /** Count of added lines. */
    final int added;
    /** Count of removed lines. */
    final int removed;

    private DiffLines(List<Line> lines) {
        this.lines = Collections.unmodifiableList(lines);
        int plus = 0;
        int minus = 0;
        for (Line line : lines) {
            if (line.kind == Line.ADDED) plus++;
            else if (line.kind == Line.REMOVED) minus++;
        }
        added = plus;
        removed = minus;
    }

    /**
     * Computes the diff: a shared prefix/suffix shortcut plus an LCS over the middle region,
     * degrading to remove-then-add when the LCS table would exceed a million cells.
     */
    static DiffLines calculate(String oldText, String newText) {
        String[] a = split(oldText);
        String[] b = split(newText);
        int prefix = 0;
        int suffix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) prefix++;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - suffix - 1].equals(b[b.length - suffix - 1])) suffix++;
        ArrayList<Line> lines = new ArrayList<>();
        for (int i = 0; i < prefix; i++) lines.add(new Line(Line.UNCHANGED, i + 1, a[i]));
        int m = a.length - prefix - suffix;
        int n = b.length - prefix - suffix;
        if ((long) (m + 1) * (n + 1) <= 1_000_000) {
            int[][] lcs = new int[m + 1][n + 1];
            for (int i = m - 1; i >= 0; i--) {
                for (int j = n - 1; j >= 0; j--) {
                    lcs[i][j] = a[prefix + i].equals(b[prefix + j])
                            ? 1 + lcs[i + 1][j + 1] : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < m || j < n) {
                if (i < m && j < n && a[prefix + i].equals(b[prefix + j])) {
                    lines.add(new Line(Line.UNCHANGED, prefix + j + 1, b[prefix + j]));
                    i++;
                    j++;
                } else if (i < m && (j == n || lcs[i + 1][j] >= lcs[i][j + 1])) {
                    lines.add(new Line(Line.REMOVED, prefix + i + 1, a[prefix + i]));
                    i++;
                } else {
                    lines.add(new Line(Line.ADDED, prefix + j + 1, b[prefix + j]));
                    j++;
                }
            }
        } else {
            for (int i = 0; i < m; i++) lines.add(new Line(Line.REMOVED, prefix + i + 1, a[prefix + i]));
            for (int j = 0; j < n; j++) lines.add(new Line(Line.ADDED, prefix + j + 1, b[prefix + j]));
        }
        for (int j = b.length - suffix; j < b.length; j++) {
            lines.add(new Line(Line.UNCHANGED, j + 1, b[j]));
        }
        return new DiffLines(lines);
    }

    /** Normalizes line endings and keeps the trailing-newline information per line. */
    private static String[] split(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        String normalized = text.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        boolean terminated = normalized.endsWith("\n");
        if (terminated) lines = java.util.Arrays.copyOf(lines, lines.length - 1);
        for (int i = 0; i < lines.length; i++) {
            if (i < lines.length - 1 || terminated) lines[i] += "\n";
        }
        return lines;
    }
}
