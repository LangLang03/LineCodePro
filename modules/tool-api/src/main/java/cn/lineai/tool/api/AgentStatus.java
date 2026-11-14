package cn.lineai.tool.api;

/** Live status of one sub agent session as reported to tools and the UI. */
public enum AgentStatus {
    /** The sub agent's loop is executing a turn. */
    RUNNING,
    /** The sub agent finished its last turn and waits for a message or a new task. */
    COMPLETED,
    /** The sub agent's last turn failed. */
    FAILED,
    /** The sub agent is blocked waiting for an approval decision. */
    WAITING_INPUT
}
