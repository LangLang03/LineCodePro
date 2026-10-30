package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lenient reader for the JSON that models actually emit as tool arguments.
 *
 * <p>A conservative normalization pass runs before parsing, so well-formed JSON keeps its exact
 * shape while the usual model mistakes are repaired: markdown fences, prose before the object,
 * smart or single quotes, unquoted keys, comments, Python literals, raw newlines inside strings,
 * trailing commas, truncation at the token limit, a double-encoded {@code arguments} string and a
 * single-object array wrapper.</p>
 */
final class ToolJsonRepair {

    private static final int MAX_TRUNCATION_CUTS = 16;

    private ToolJsonRepair() {
    }

    /** Parsed object, or {@code null} when the text carries nothing usable. */
    static JSONObject parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = trimmed(raw);
        if (text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == '[') {
            JSONObject array = unwrapArray(text);
            if (array == null) {
                array = unwrapArray(sanitize(text));
            }
            if (array != null) {
                return array;
            }
        }
        int start = text.indexOf('{');
        if (start >= 0) {
            JSONObject object = parseObject(text.substring(start));
            if (object != null) {
                return object;
            }
        }
        if (text.charAt(0) == '"') {
            return parse(decodeString(text));
        }
        return null;
    }

    /**
     * Always sanitizes before parsing: {@code org.json} silently accepts unquoted keys and
     * single-quoted strings, so a "successful" strict parse can still turn {@code True} into the
     * string "True". Repairing first keeps those values typed.
     */
    private static JSONObject parseObject(String body) {
        String sanitized = trimToObject(sanitize(body));
        sanitized = stripTrailingCommas(sanitized);
        JSONObject candidate = strictObject(closeOpen(sanitized));
        if (candidate != null) {
            return unwrap(candidate);
        }
        // Truncated mid-member (max_tokens hit): drop the last comma-separated member and retry.
        String current = sanitized;
        for (int attempt = 0; attempt < MAX_TRUNCATION_CUTS; attempt++) {
            int cut = lastComma(current);
            if (cut < 0) {
                break;
            }
            current = current.substring(0, cut);
            candidate = strictObject(closeOpen(stripTrailingCommas(current)));
            if (candidate != null) {
                return unwrap(candidate);
            }
        }
        return null;
    }

    private static JSONObject strictObject(String text) {
        try {
            return new JSONObject(text);
        } catch (Exception invalid) {
            return null;
        }
    }

    /** True when the sanitized argument object closes before the text ends. */
    static boolean complete(String raw) {
        if (raw == null) {
            return true;
        }
        String text = trimmed(raw);
        if (text.isEmpty()) {
            return true;
        }
        if (text.charAt(0) == '"') {
            String decoded = decodeString(text);
            return !decoded.isEmpty() && complete(decoded);
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return false;
        }
        int depth = 0;
        boolean opened = false;
        boolean inString = false;
        boolean escaped = false;
        String body = sanitize(text.substring(start));
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                depth++;
                opened = true;
            } else if (current == '}' || current == ']') {
                depth--;
                if (opened && depth <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JSONObject unwrapArray(String text) {
        try {
            JSONArray array = new JSONArray(text);
            if (array.length() == 1 && array.opt(0) instanceof JSONObject) {
                return unwrap((JSONObject) array.opt(0));
            }
        } catch (Exception invalid) {
            return null;
        }
        return null;
    }

    /** Models occasionally echo the wire wrapper: {@code {"arguments": "{...}"}}. */
    private static JSONObject unwrap(JSONObject object) {
        if (object == null || object.length() != 1) {
            return object;
        }
        Object value = object.opt("arguments");
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof String) {
            JSONObject nested = strictObject((String) value);
            if (nested != null) {
                return nested;
            }
        }
        return object;
    }

    /**
     * Strips a BOM and surrounding whitespace only.
     *
     * <p>Fences are not cut here: a {@code write_file} payload can legitimately contain ``` in its
     * content, and the object extraction below already ignores fence lines around the JSON.</p>
     */
    private static String trimmed(String raw) {
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        return text.trim();
    }

    private static String decodeString(String text) {
        try {
            return new JSONArray("[" + text + "]").optString(0, "");
        } catch (Exception invalid) {
            return "";
        }
    }

    /** Cuts trailing prose after the first balanced object; leaves truncated text untouched. */
    private static String trimToObject(String text) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                depth++;
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(0, index + 1);
                }
            }
        }
        return text;
    }

    // ------------------------------------------------------------------ sanitize

    private static String sanitize(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '"' || current == '\u201c' || current == '\u201d') {
                index = quoted(text, index, out, current);
                continue;
            }
            if (current == '\'' || current == '\u2018' || current == '\u2019') {
                index = quoted(text, index, out, current);
                continue;
            }
            if (current == '/' && index + 1 < text.length()) {
                char next = text.charAt(index + 1);
                if (next == '/') {
                    index = lineComment(text, index);
                    continue;
                }
                if (next == '*') {
                    index = blockComment(text, index);
                    continue;
                }
            }
            if (Character.isJavaIdentifierStart(current)) {
                index = bareWord(text, index, out);
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    private static int bareWord(String text, int start, StringBuilder out) {
        int index = start;
        while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
            index++;
        }
        String word = text.substring(start, index);
        int next = index;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        if (next < text.length() && text.charAt(next) == ':') {
            out.append('"').append(word).append('"');
        } else if ("True".equals(word)) {
            out.append("true");
        } else if ("False".equals(word)) {
            out.append("false");
        } else if ("None".equals(word)) {
            out.append("null");
        } else {
            out.append(word);
        }
        return index;
    }

    /** Copies one quoted string as a double-quoted JSON string, escaping what JSON forbids. */
    private static int quoted(String text, int start, StringBuilder out, char open) {
        out.append('"');
        boolean single = open == '\'' || open == '\u2018';
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length()) {
                char next = text.charAt(index + 1);
                if (single && next == '\'') {
                    out.append('\'');
                } else {
                    out.append(current).append(next);
                }
                index += 2;
                continue;
            }
            if (closes(current, open)) {
                out.append('"');
                return index + 1;
            }
            if (single && current == '"') {
                out.append("\\\"");
                index++;
                continue;
            }
            escape(out, current);
            index++;
        }
        out.append('"');
        return index;
    }

    private static boolean closes(char value, char open) {
        if (open == '"') {
            return value == '"';
        }
        if (open == '\'') {
            return value == '\'';
        }
        if (open == '\u201c') {
            return value == '\u201d' || value == '"';
        }
        return value == '\u2019' || value == '\'';
    }

    private static void escape(StringBuilder out, char value) {
        switch (value) {
            case '\n':
                out.append("\\n");
                return;
            case '\r':
                out.append("\\r");
                return;
            case '\t':
                out.append("\\t");
                return;
            default:
                if (value < 0x20) {
                    String hex = Integer.toHexString(value);
                    out.append("\\u");
                    for (int pad = hex.length(); pad < 4; pad++) {
                        out.append('0');
                    }
                    out.append(hex);
                } else {
                    out.append(value);
                }
        }
    }

    private static int lineComment(String text, int start) {
        int end = text.indexOf('\n', start);
        return end < 0 ? text.length() : end + 1;
    }

    private static int blockComment(String text, int start) {
        int end = text.indexOf("*/", start + 2);
        return end < 0 ? text.length() : end + 2;
    }

    // ------------------------------------------------------------------ balance

    private static String stripTrailingCommas(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                out.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(current);
                continue;
            }
            if (current == ',') {
                int next = index + 1;
                while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
                    next++;
                }
                if (next >= text.length() || text.charAt(next) == '}'
                        || text.charAt(next) == ']') {
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private static String closeOpen(String text) {
        StringBuilder out = new StringBuilder(text);
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < out.length(); index++) {
            char current = out.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                stack.push(current);
            } else if (current == '}' || current == ']') {
                char expected = current == '}' ? '{' : '[';
                if (!stack.isEmpty() && stack.peek() == expected) {
                    stack.pop();
                }
            }
        }
        if (inString) {
            out.append('"');
        }
        while (!stack.isEmpty()) {
            out.append(stack.pop() == '{' ? '}' : ']');
        }
        return out.toString();
    }

    private static int lastComma(String text) {
        boolean inString = false;
        boolean escaped = false;
        int last = -1;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == ',') {
                last = index;
            }
        }
        return last;
    }
}
