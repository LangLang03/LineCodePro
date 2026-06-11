package cn.lineai.ai.stream;

public final class ThinkTagParser {
    private static final String START = "<think>";
    private static final String END = "</think>";

    private final StringBuilder pending = new StringBuilder();
    private boolean inThinking;

    public Result append(String delta) {
        pending.append(delta == null ? "" : delta);
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();

        while (pending.length() > 0) {
            String tag = inThinking ? END : START;
            int tagIndex = indexOf(pending, tag);
            if (tagIndex >= 0) {
                appendRange(inThinking ? thinking : text, pending, 0, tagIndex);
                pending.delete(0, tagIndex + tag.length());
                inThinking = !inThinking;
                continue;
            }

            int keep = trailingPrefixLength(pending, tag);
            int emitEnd = pending.length() - keep;
            if (emitEnd <= 0) {
                break;
            }
            appendRange(inThinking ? thinking : text, pending, 0, emitEnd);
            pending.delete(0, emitEnd);
        }

        return new Result(text.toString(), thinking.toString());
    }

    public Result flush() {
        String remaining = pending.toString();
        pending.setLength(0);
        if (inThinking) {
            return new Result("", remaining);
        }
        return new Result(remaining, "");
    }

    private int indexOf(StringBuilder builder, String needle) {
        int max = builder.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            int j = 0;
            while (j < needle.length() && builder.charAt(i + j) == needle.charAt(j)) {
                j++;
            }
            if (j == needle.length()) {
                return i;
            }
        }
        return -1;
    }

    private int trailingPrefixLength(StringBuilder builder, String tag) {
        int max = Math.min(builder.length(), tag.length() - 1);
        for (int length = max; length > 0; length--) {
            boolean matches = true;
            int start = builder.length() - length;
            for (int i = 0; i < length; i++) {
                if (builder.charAt(start + i) != tag.charAt(i)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return length;
            }
        }
        return 0;
    }

    private void appendRange(StringBuilder target, StringBuilder source, int start, int end) {
        for (int i = start; i < end; i++) {
            target.append(source.charAt(i));
        }
    }

    public static final class Result {
        private final String text;
        private final String thinking;

        private Result(String text, String thinking) {
            this.text = text;
            this.thinking = thinking;
        }

        public String getText() {
            return text;
        }

        public String getThinking() {
            return thinking;
        }
    }
}
