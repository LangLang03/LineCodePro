package cn.lineai.tool.api;

/**
 * One runtime occurrence produced outside the tool loop and delivered to an agent loop
 * through a {@link RuntimeInbox}: shell completions, sub-agent results, user replies and
 * similar asynchronous inputs.
 *
 * <p>Every event renders itself as the text of one hidden user message via
 * {@link #noticeText()}; the loop aggregates those texts into a single invisible user row,
 * which is the only way an event reaches the model. Events are immutable value objects.
 */
public abstract class RuntimeEvent {

    /** Suppresses subclassing outside this file; the hierarchy is closed. */
    private RuntimeEvent() {
    }

    /**
     * Renders the event as the text of one hidden user message line.
     *
     * @return the model-facing notice text; never null
     */
    public abstract String noticeText();

    /** A pre-rendered background-shell notice (usually from a background registry). */
    public static final class ShellNotice extends RuntimeEvent {
        /** The already-rendered notice text. */
        public final String text;

        public ShellNotice(String text) {
            this.text = text == null ? "" : text;
        }

        @Override public String noticeText() {
            return text;
        }
    }

    /** A sub agent finished its turn; only the final output reaches the parent context. */
    public static final class AgentTurnCompleted extends RuntimeEvent {
        /** Output cap for one injected result, mirroring the tool-result truncation rule. */
        private static final int MAX_OUTPUT_CHARS = 32_000;

        /** The sub agent that produced the output. */
        public final String agentId;
        /** Display name of the sub agent. */
        public final String agentName;
        /** True when the turn ended in failure. */
        public final boolean failed;
        /** The sub agent's final answer; its process stays in its own conversation. */
        public final String finalOutput;

        public AgentTurnCompleted(String agentId, String agentName, boolean failed,
                                  String finalOutput) {
            this.agentId = agentId == null ? "" : agentId;
            this.agentName = agentName == null ? "" : agentName;
            this.failed = failed;
            this.finalOutput = finalOutput == null ? "" : finalOutput;
        }

        @Override public String noticeText() {
            StringBuilder out = new StringBuilder();
            out.append(failed ? "Sub agent " : "Sub agent ")
                    .append(agentName.isEmpty() ? agentId : agentName)
                    .append(" [").append(agentId).append("]")
                    .append(failed ? " failed." : " finished.");
            if (!finalOutput.isEmpty()) {
                out.append("\nFinal output:\n");
                if (finalOutput.length() > MAX_OUTPUT_CHARS) {
                    out.append(finalOutput, 0, MAX_OUTPUT_CHARS)
                            .append("\n… output truncated; the full transcript lives in the")
                            .append(" sub agent conversation [").append(agentId).append("]");
                } else {
                    out.append(finalOutput);
                }
            }
            return out.toString();
        }
    }

    /** A mid-turn message from a sub agent to its parent. */
    public static final class AgentMessage extends RuntimeEvent {
        /** The sending sub agent. */
        public final String agentId;
        /** The message body. */
        public final String text;

        public AgentMessage(String agentId, String text) {
            this.agentId = agentId == null ? "" : agentId;
            this.text = text == null ? "" : text;
        }

        @Override public String noticeText() {
            return "Message from sub agent [" + agentId + "]:\n" + text;
        }
    }

    /** A sub agent is blocked and needs its parent (or the user) to act. */
    public static final class AgentNeedsInput extends RuntimeEvent {
        /** The waiting sub agent. */
        public final String agentId;
        /** What it is waiting for. */
        public final String text;

        public AgentNeedsInput(String agentId, String text) {
            this.agentId = agentId == null ? "" : agentId;
            this.text = text == null ? "" : text;
        }

        @Override public String noticeText() {
            return "Sub agent [" + agentId + "] needs input: " + text;
        }
    }

    /** A sub agent's loop failed hard (model error, crash) — no final output exists. */
    public static final class AgentFailed extends RuntimeEvent {
        /** The failed sub agent. */
        public final String agentId;
        /** The failure description. */
        public final String error;

        public AgentFailed(String agentId, String error) {
            this.agentId = agentId == null ? "" : agentId;
            this.error = error == null ? "" : error;
        }

        @Override public String noticeText() {
            return "Sub agent [" + agentId + "] failed: " + error;
        }
    }

    /** The workspace changed outside the loop (files added, build finished, …). */
    public static final class WorkspaceChanged extends RuntimeEvent {
        /** A compact summary of what changed. */
        public final String summary;

        public WorkspaceChanged(String summary) {
            this.summary = summary == null ? "" : summary;
        }

        @Override public String noticeText() {
            return "Workspace changed: " + summary;
        }
    }

    /** A user reply routed through the bus instead of typed into the composer. */
    public static final class UserMessage extends RuntimeEvent {
        /** Who the reply is attributed to (for the notice text). */
        public final String source;
        /** The reply body. */
        public final String text;

        public UserMessage(String source, String text) {
            this.source = source == null ? "" : source;
            this.text = text == null ? "" : text;
        }

        @Override public String noticeText() {
            return "Message from " + (source.isEmpty() ? "user" : source) + ":\n" + text;
        }
    }
}
