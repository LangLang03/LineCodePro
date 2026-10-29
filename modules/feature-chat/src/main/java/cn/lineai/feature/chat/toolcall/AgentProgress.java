package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.ui.i18n.R;

/** Parsed display fields of an agent result (progress envelope or plain text). */
final class AgentProgress {

    final String status;
    final String description;
    final String type;
    final String agentId;
    final String thinking;
    final String output;
    final ToolJsonArray nestedCalls;
    final int toolCount;

    private AgentProgress(String status, String description, String type, String agentId,
                          String thinking, String output, ToolJsonArray nestedCalls, int toolCount) {
        this.status = status;
        this.description = description;
        this.type = type;
        this.agentId = agentId;
        this.thinking = thinking;
        this.output = output;
        this.nestedCalls = nestedCalls;
        this.toolCount = toolCount;
    }

    static AgentProgress parse(ToolCallEntry entry, String fallbackDescription) {
        ToolJson progress = envelope(entry.result);
        if (progress != null) {
            ToolJsonArray calls = progress.array("tool_calls");
            int count = progress.integer("tool_call_count",
                    calls == null ? 0 : calls.length());
            String description = progress.text("description").trim();
            if (description.isEmpty()) description = fallbackDescription;
            return new AgentProgress(
                    progress.text("status", ToolRunStatus.RUNNING.wire),
                    description, progress.text("type"), progress.text("agent_id"),
                    progress.text("thinking"), output(progress), calls, count);
        }
        String status = entry.running ? ToolRunStatus.RUNNING.wire
                : entry.failed ? ToolRunStatus.ERROR.wire : ToolRunStatus.DONE.wire;
        return new AgentProgress(status, fallbackDescription, "", "", "",
                entry.result, null, 0);
    }

    boolean error() {
        return ToolRunStatus.ERROR.matches(status) || ToolRunStatus.FAILED.matches(status);
    }

    boolean pending() {
        return ToolRunStatus.PENDING.matches(status);
    }

    boolean running() {
        return ToolRunStatus.RUNNING.matches(status)
                || ToolRunStatus.REQUESTED.matches(status)
                || ToolRunStatus.WAITING_UNLOCK.matches(status);
    }

    String normalizedType() {
        String value = type == null ? "" : type.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value)
                || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.isEmpty() ? "explore" : value;
    }

    int typeLabelRes() {
        return "explore".equals(normalizedType())
                ? R.string.tool_call_agent_type_explore
                : R.string.tool_call_agent_type_coding;
    }

    private static ToolJson envelope(String content) {
        ToolJson json = ToolCardSupport.resultJson(content);
        if (json == null) return null;
        return json.flag("linecode_agent_progress") || json.flag("linecode_agent_ref")
                ? json : null;
    }

    private static String output(ToolJson progress) {
        String value = progress.text("output").trim();
        if (value.isEmpty()) value = progress.text("preview").trim();
        if (value.isEmpty()) value = progress.text("model_content").trim();
        return value;
    }
}
