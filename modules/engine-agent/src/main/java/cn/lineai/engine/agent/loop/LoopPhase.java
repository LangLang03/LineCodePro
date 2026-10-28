package cn.lineai.engine.agent.loop;

public enum LoopPhase {
    IDLE,
    AWAITING_MODEL,
    STREAMING,
    AWAITING_CONFIRMATION,
    EXECUTING_TOOLS,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
