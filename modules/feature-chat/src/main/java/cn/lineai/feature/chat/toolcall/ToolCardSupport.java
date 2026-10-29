package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

/** Shared, presentation-agnostic helpers for the tool card renderers. */
final class ToolCardSupport {

    private ToolCardSupport() {
    }

    /** Status label resource for one entry: running beats failure only while the call is active. */
    static int statusResource(ToolCallEntry entry) {
        if (entry.running) return R.string.tool_call_status_running;
        if (entry.failed) return R.string.tool_call_status_failed;
        if (ToolRunStatus.PENDING.matches(entry.reviewState)) {
            return R.string.tool_call_status_pending_review;
        }
        return R.string.tool_call_status_done;
    }

    /** Status colour for the label next to the title. */
    static int statusColor(Tokens tokens, ToolCallEntry entry) {
        if (entry.running) return tokens.accent;
        if (entry.failed) return tokens.danger;
        if (ToolRunStatus.PENDING.matches(entry.reviewState)) return tokens.warning;
        return tokens.textSecondary;
    }

    /** The one-line secondary summary shown under the title (path, command, task...). */
    static String summary(ToolCallEntry entry, ToolPresentation presentation) {
        if (presentation != null && !presentation.action.isEmpty()) {
            return firstLine(presentation.action);
        }
        return "";
    }

    static String firstLine(String value) {
        if (value == null || value.isEmpty()) return "";
        int index = value.indexOf('\n');
        return index < 0 ? value.trim() : value.substring(0, index).trim();
    }

    /** Legacy header label: {@code status + "  " + target}, single line in the view. */
    static CharSequence headerLabel(android.content.Context context, ToolCallEntry entry,
                                    CharSequence target) {
        StringBuilder builder = new StringBuilder(context.getString(statusResource(entry)));
        if (target != null && target.length() > 0) {
            builder.append("  ").append(target);
        }
        return builder.toString();
    }

    /** Path/pattern/query/title target shown after the status word. */
    static String target(ToolCallEntry entry, ToolPresentation presentation) {
        if (presentation != null && !presentation.action.isEmpty()) {
            return firstLine(presentation.action);
        }
        ToolJson arguments = arguments(entry);
        String[] keys = {"file_path", "path", "pattern", "query", "prompt", "title",
                "task", "command", "url"};
        for (String key : keys) {
            String value = arguments.text(key, "").trim();
            if (!value.isEmpty()) return firstLine(value);
        }
        return "";
    }

    static ToolJson arguments(ToolCallEntry entry) {
        if (entry == null || entry.arguments.trim().isEmpty()) return ToolJson.empty();
        ToolJson parsed = ToolJson.tryParse(entry.arguments);
        return parsed == null ? ToolJson.empty() : parsed;
    }

    /** Parsed result envelope, or {@code null} when the content is not a JSON object. */
    static ToolJson resultJson(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        return ToolJson.tryParse(content);
    }

    static ToolJsonArray array(ToolJson object, String key) {
        return object == null ? null : object.array(key);
    }

    static String string(ToolJson object, String key) {
        return object == null ? "" : object.text(key, "").trim();
    }

    static String prettyJson(ToolJson object) {
        return object == null ? "{}" : object.pretty();
    }

    /** Avoids dumping a raw progress envelope into the visible markdown/text body. */
    static String displayOutput(String content, String... envelopeFlags) {
        if (content == null || content.isEmpty()) return "";
        ToolJson json = resultJson(content);
        if (json == null) return content;
        for (String flag : envelopeFlags) {
            if (json.flag(flag)) {
                String output = json.text("output").trim();
                if (output.isEmpty()) output = json.text("summary").trim();
                return output;
            }
        }
        return content;
    }
}
