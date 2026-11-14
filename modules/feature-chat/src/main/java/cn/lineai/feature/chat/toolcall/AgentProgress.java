package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.ui.i18n.R;

/** Parsed display fields of an agent result (progress envelope or plain text). */
final class AgentProgress {

    /** Wire status token; see {@link ToolRunStatus}. */
    final String status;
    /** Task description, falling back to the presentation title. */
    final String description;
    /** Raw agent type token (explore, sub_coding, ...). */
    final String type;
    /** Agent instance id, empty when the envelope omits it. */
    final String agentId;
    /** Reasoning text streamed by the agent, if any. */
    final String thinking;
    /** Final output text (output/preview/model_content). */
    final String output;
    /** Nested tool calls carried by the envelope; null for plain results. */
    final ToolJsonArray nestedCalls;
    /** Reported tool-call count, 0 when absent. */
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

    /**
     * Extracts the progress envelope from a result, or synthesizes one from the entry state so
     * plain agent results still render.
     *
     * @param entry               call/result pair being rendered
     * @param fallbackDescription description used when the envelope omits one
     */
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

    /** True when the status reports an error or failure. */
    boolean error() {
        return ToolRunStatus.ERROR.matches(status) || ToolRunStatus.FAILED.matches(status);
    }

    /** True while the agent output awaits review. */
    boolean pending() {
        return ToolRunStatus.PENDING.matches(status);
    }

    /** True for every in-flight status token. */
    boolean running() {
        return ToolRunStatus.RUNNING.matches(status)
                || ToolRunStatus.REQUESTED.matches(status)
                || ToolRunStatus.WAITING_UNLOCK.matches(status);
    }

    /** Normalized agent type; legacy snake_case variants collapse to "sub-coding". */
    String normalizedType() {
        String value = type == null ? "" : type.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value)
                || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.isEmpty() ? "explore" : value;
    }

    /** Localized type pill label for the normalized type. */
    int typeLabelRes() {
        return "explore".equals(normalizedType())
                ? R.string.tool_call_agent_type_explore
                : R.string.tool_call_agent_type_coding;
    }

    /** The result JSON only when it carries a recognized agent-progress flag. */
    private static ToolJson envelope(String content) {
        ToolJson json = ToolCardSupport.resultJson(content);
        if (json == null) return null;
        return json.flag("linecode_agent_progress") || json.flag("linecode_agent_ref")
                ? json : null;
    }

    /** Output text from the first non-empty of output, preview and model_content. */
    private static String output(ToolJson progress) {
        String value = progress.text("output").trim();
        if (value.isEmpty()) value = progress.text("preview").trim();
        if (value.isEmpty()) value = progress.text("model_content").trim();
        return value;
    }
}
