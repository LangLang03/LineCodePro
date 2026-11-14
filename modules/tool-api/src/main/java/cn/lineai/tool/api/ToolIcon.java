package cn.lineai.tool.api;

/** Semantic icon category for a tool; the UI maps it to a concrete glyph. */
public enum ToolIcon {
    GENERIC,
    READ,
    WRITE,
    EDIT,
    DELETE,
    LIST,
    SEARCH,
    FETCH,
    TERMINAL,
    MCP,
    IMAGE,
    SPARKLES,
    TODO,
    AGENT,
    MEMORY,
    PHONE,
    DIFF,
    /** Spawns one sub agent. */
    AGENT_SPAWN,
    /** Sends a message to a sub agent. */
    AGENT_SEND,
    /** Waits for sub agent activity. */
    AGENT_WAIT,
    /** Lists sub agent sessions. */
    AGENT_LIST,
    /** Sub agent → parent message. */
    AGENT_NOTIFY
}
