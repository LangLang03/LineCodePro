package cn.lineai.tool;

import java.text.Normalizer;

/**
 * 清理模型返回的工具参数 JSON，修复常见非法格式。
 */
public final class ToolArgsCleaner {

    private ToolArgsCleaner() {
    }

    /**
     * 清理原始参数字符串，使其尽可能成为合法 JSON。
     *
     * @param raw 模型返回的原始参数
     * @return 清理后的 JSON 字符串
     */
    public static String clean(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return "{}";
        }

        String s = raw.trim();
        s = stripMarkdownFence(s);
        s = normalizeUnicode(s);
        s = removeCommentsAndControlChars(s);
        s = removeTrailingCommas(s);
        s = normalizeSingleQuotes(s);
        s = balanceBraces(s);

        if (s.trim().length() == 0) {
            return "{}";
        }
        return s;
    }

    private static String stripMarkdownFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstLineBreak = -1;
        for (int i = 3; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                firstLineBreak = i;
                break;
            }
        }
        int lastFence = s.lastIndexOf("```");
        if (lastFence <= firstLineBreak) {
            return s;
        }
        int start = firstLineBreak + 1;
        if (start < s.length() && s.charAt(start) == '\n') {
            start++;
        } else if (start < s.length() && s.charAt(start) == '\r') {
            start++;
            if (start < s.length() && s.charAt(start) == '\n') {
                start++;
            }
        }
        return s.substring(start, lastFence).trim();
    }

    private static String normalizeUnicode(String s) {
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\u200B' || c == '\u200C' || c == '\u200D'
                    || c == '\uFEFF' || c == '\u2060' || c == '\u180E') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String removeCommentsAndControlChars(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();
        boolean inString = false;
        boolean escape = false;

        while (i < len) {
            char c = s.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                i++;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escape = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                out.append(c);
                i++;
                continue;
            }
            if (inString) {
                if (c == '\n') {
                    out.append("\\n");
                } else if (c == '\r') {
                    out.append("\\r");
                } else if (c == '\t') {
                    out.append("\\t");
                } else if (c >= 32) {
                    out.append(c);
                }
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len) {
                char next = s.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < len) {
                        char ch = s.charAt(i);
                        if (ch == '\n' || ch == '\r') {
                            break;
                        }
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < len && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                        i++;
                    }
                    if (i + 1 < len) {
                        i += 2;
                    }
                    continue;
                }
            }
            if (c < 32) {
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String removeTrailingCommas(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();
        char[] openChars = new char[len];
        int stackSize = 0;
        boolean inString = false;
        boolean escape = false;

        while (i < len) {
            char c = s.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                i++;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escape = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                out.append(c);
                i++;
                continue;
            }
            if (inString) {
                out.append(c);
                i++;
                continue;
            }
            if (c == '{' || c == '[') {
                openChars[stackSize++] = c;
                out.append(c);
                i++;
                continue;
            }
            if (c == '}' || c == ']') {
                char expectedOpen = c == '}' ? '{' : '[';
                if (stackSize > 0 && openChars[stackSize - 1] == expectedOpen) {
                    stackSize--;
                    int j = out.length() - 1;
                    while (j >= 0 && isJsonWhitespace(out.charAt(j))) {
                        j--;
                    }
                    if (j >= 0 && out.charAt(j) == ',') {
                        out.deleteCharAt(j);
                    }
                }
                out.append(c);
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isJsonWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static String normalizeSingleQuotes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();
        boolean inString = false;
        boolean escape = false;

        while (i < len) {
            char c = s.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                i++;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escape = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                out.append(c);
                i++;
                continue;
            }
            if (inString) {
                out.append(c);
                i++;
                continue;
            }
            if (c == '\'') {
                int close = findNextUnescapedSingleQuote(s, i + 1);
                if (close > 0 && isSimpleSingleQuotedContent(s, i + 1, close)) {
                    out.append('"');
                    out.append(s, i + 1, close);
                    out.append('"');
                    i = close + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int findNextUnescapedSingleQuote(String s, int start) {
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '\'') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSimpleSingleQuotedContent(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\n' || c == '\r' || c < 32) {
                return false;
            }
        }
        return true;
    }

    /**
     * 修复因模型输出截断导致的未闭合 JSON 结构符号。
     * 只追加缺失的闭合符号，不补全字段值。
     */
    private static String balanceBraces(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        char[] stack = new char[s.length()];
        int stackSize = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                out.append(c);
                continue;
            }
            if (inString) {
                out.append(c);
                continue;
            }
            if (c == '{' || c == '[') {
                stack[stackSize++] = c;
                out.append(c);
                continue;
            }
            if (c == '}' || c == ']') {
                char expectedOpen = c == '}' ? '{' : '[';
                if (stackSize > 0 && stack[stackSize - 1] == expectedOpen) {
                    stackSize--;
                }
                out.append(c);
                continue;
            }
            out.append(c);
        }

        if (inString) {
            out.append('"');
        }
        while (stackSize > 0) {
            char open = stack[--stackSize];
            out.append(open == '{' ? '}' : ']');
        }
        return out.toString();
    }
}
