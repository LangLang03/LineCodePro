package cn.lineai.engine.agent.loop;

/** Why the loop stopped; typed so callers never compare strings. */
public enum FinishReason {
    STOP,
    BUDGET,
    DENIED,
    CANCELLED,
    ERROR
}
