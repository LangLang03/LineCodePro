package cn.lineai.ui.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses the small Markdown emphasis subset used by streamed reasoning summaries. */
public final class InlineEmphasisParser {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    private InlineEmphasisParser() {
    }

    public static Parsed parse(String value) {
        String source = value == null ? "" : value;
        StringBuilder text = new StringBuilder(source.length());
        ArrayList<Span> spans = new ArrayList<>();
        int offset = 0;
        while (offset < source.length()) {
            if (source.charAt(offset) == '`') {
                int codeSpanEnd = codeSpanEnd(source, offset);
                if (codeSpanEnd > offset) {
                    text.append(source, offset, codeSpanEnd);
                    offset = codeSpanEnd;
                    continue;
                }
            }
            if (source.charAt(offset) == '\\' && offset + 1 < source.length()
                    && isMarkerCharacter(source.charAt(offset + 1))) {
                text.append(source.charAt(offset + 1));
                offset += 2;
                continue;
            }
            Marker marker = markerAt(source, offset);
            if (marker == null) {
                text.append(source.charAt(offset));
                offset++;
                continue;
            }
            int closing = findClosing(source, marker.token, offset + marker.token.length());
            if (closing <= offset + marker.token.length()) {
                text.append(source.charAt(offset));
                offset++;
                continue;
            }
            int start = text.length();
            text.append(source, offset + marker.token.length(), closing);
            int end = text.length();
            spans.add(new Span(start, end, marker.style));
            offset = closing + marker.token.length();
            if ("**".equals(marker.token) && source.startsWith(marker.token, offset)) {
                text.append(" | ");
            }
        }
        return new Parsed(text.toString(), spans);
    }

    private static Marker markerAt(String source, int offset) {
        if (source.startsWith("***", offset)) {
            return new Marker("***", BOLD_ITALIC);
        }
        if (source.startsWith("___", offset)) {
            return new Marker("___", BOLD_ITALIC);
        }
        if (source.startsWith("**", offset)) {
            return new Marker("**", BOLD);
        }
        if (source.startsWith("__", offset)) {
            return new Marker("__", BOLD);
        }
        if (source.charAt(offset) == '*') {
            return new Marker("*", ITALIC);
        }
        if (source.charAt(offset) == '_' && underscoreCanOpen(source, offset)) {
            return new Marker("_", ITALIC);
        }
        return null;
    }

    private static boolean underscoreCanOpen(String source, int offset) {
        boolean previousIsWord = offset > 0 && Character.isLetterOrDigit(source.charAt(offset - 1));
        boolean nextIsWord = offset + 1 < source.length()
                && Character.isLetterOrDigit(source.charAt(offset + 1));
        return !(previousIsWord && nextIsWord);
    }

    private static int codeSpanEnd(String source, int offset) {
        int markerLength = 1;
        while (offset + markerLength < source.length()
                && source.charAt(offset + markerLength) == '`') {
            markerLength++;
        }
        String marker = source.substring(offset, offset + markerLength);
        int closing = source.indexOf(marker, offset + markerLength);
        return closing < 0 ? -1 : closing + markerLength;
    }

    private static int findClosing(String source, String token, int from) {
        int closing = source.indexOf(token, from);
        while (closing >= 0) {
            boolean escaped = closing > 0 && source.charAt(closing - 1) == '\\';
            boolean invalidUnderscore = "_".equals(token)
                    && closing + 1 < source.length()
                    && Character.isLetterOrDigit(source.charAt(closing + 1));
            if (!escaped && !invalidUnderscore) {
                return closing;
            }
            closing = source.indexOf(token, closing + token.length());
        }
        return -1;
    }

    private static boolean isMarkerCharacter(char value) {
        return value == '*' || value == '_' || value == '\\';
    }

    private static final class Marker {
        final String token;
        final int style;

        Marker(String token, int style) {
            this.token = token;
            this.style = style;
        }
    }

    public static final class Parsed {
        private final String text;
        private final List<Span> spans;

        Parsed(String text, List<Span> spans) {
            this.text = text;
            this.spans = Collections.unmodifiableList(new ArrayList<>(spans));
        }

        public String getText() {
            return text;
        }

        public List<Span> getSpans() {
            return spans;
        }
    }

    public static final class Span {
        private final int start;
        private final int end;
        private final int style;

        Span(int start, int end, int style) {
            this.start = start;
            this.end = end;
            this.style = style;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public int getStyle() {
            return style;
        }
    }
}
