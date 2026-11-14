package cn.lineai.core.model;

/** Persisted lifecycle state of one sub agent session. */
public enum AgentSessionStatus {
    /** The agent's loop is executing a turn. */
    RUNNING,
    /** The last turn finished normally; the session waits for the next message. */
    COMPLETED,
    /** The last turn ended in failure. */
    FAILED,
    /** The loop is blocked on an approval decision. */
    WAITING_INPUT,
    /** The process died mid-turn; the session must be revived explicitly. */
    INTERRUPTED;

    /**
     * Parses a persisted wire token, falling back to {@link #COMPLETED} for unknown values.
     *
     * @param value stored token
     * @return the matching status
     */
    public static AgentSessionStatus parse(String value) {
        if (value != null) {
            for (AgentSessionStatus status : values()) {
                if (status.name().equals(value)) return status;
            }
        }
        return COMPLETED;
    }
}
