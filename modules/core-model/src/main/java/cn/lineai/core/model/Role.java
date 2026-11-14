package cn.lineai.core.model;

/**
 * A message role in the conversation protocol. The wire name is the lowercase enum name so
 * that protocol adapters never need a second mapping table.
 */
public enum Role {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    /** Returns the lowercase protocol name, matching the enum constant. */
    public String wireName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
