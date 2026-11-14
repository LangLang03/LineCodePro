package cn.lineai.feature.chat.toolcall;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.lineai.ui.i18n.R;

/**
 * Turns agent tool results into human-readable card text. The model receives the raw
 * {@code id=… status=…} lines; the card shows "Fixer · Running" instead.
 */
final class AgentText {

    /** Output characters kept before truncation. */
    private static final int MAX_TEXT = 48_000;
    /** One registry line: id, name and status tokens in any order. */
    private static final Pattern ENTRY = Pattern.compile(
            "id=\"([^\"]*)\".*?name=\"([^\"]*)\".*?status=([a-z_]+)");
    /** Status-only lines such as {@code id="x" status=completed}. */
    private static final Pattern STATUS_ONLY = Pattern.compile(
            "id=\"([^\"]*)\"[^\\n]*?status=([a-z_]+)");

    private AgentText() {
    }

    /** Caps a text block the way the shell card does. */
    static String cap(String text) {
        if (text.length() <= MAX_TEXT) return text;
        return text.substring(0, MAX_TEXT) + "\n\u2026";
    }

    /** Body used when a card has no argument text: the result or a status line. */
    static String describe(Context context, ToolCallEntry entry) {
        if (!entry.result.isEmpty()) return cap(entry.result);
        return context.getString(ToolCardSupport.statusResource(entry));
    }

    /** Human-readable session list: one "name · status" line per session. */
    static String sessions(Context context, String result) {
        List<String> lines = new ArrayList<>();
        Matcher matcher = ENTRY.matcher(result);
        while (matcher.find()) {
            lines.add(label(matcher.group(2), matcher.group(1)) + " · "
                    + status(context, matcher.group(3)));
        }
        if (lines.isEmpty()) {
            matcher = STATUS_ONLY.matcher(result);
            while (matcher.find()) {
                lines.add(matcher.group(1) + " · " + status(context, matcher.group(2)));
            }
        }
        return lines.isEmpty() ? cap(result) : String.join("\n", lines);
    }

    /** Number of sessions referenced by a list/wait result. */
    static int sessionCount(String result) {
        int count = 0;
        Matcher matcher = ENTRY.matcher(result);
        while (matcher.find()) count++;
        if (count == 0) {
            matcher = STATUS_ONLY.matcher(result);
            while (matcher.find()) count++;
        }
        return count;
    }

    private static String label(String name, String id) {
        return name == null || name.isEmpty() ? id : name;
    }

    /** Localized label for one persisted/status token. */
    private static String status(Context context, String token) {
        if (ToolRunStatus.RUNNING.matches(token) || ToolRunStatus.IN_PROGRESS.matches(token)) {
            return context.getString(R.string.tool_call_status_running);
        }
        if (ToolRunStatus.FAILED.matches(token) || ToolRunStatus.ERROR.matches(token)) {
            return context.getString(R.string.tool_call_status_failed);
        }
        if (ToolRunStatus.WAITING_INPUT.matches(token) || ToolRunStatus.WAITING.matches(token)) {
            return context.getString(R.string.tool_call_status_pending_review);
        }
        if (ToolRunStatus.INTERRUPTED.matches(token)) {
            return context.getString(R.string.tool_call_status_interrupted);
        }
        return context.getString(R.string.tool_call_status_done);
    }
}
