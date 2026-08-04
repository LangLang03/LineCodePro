package cn.lineai.tool;

public enum ToolDisplayCategory {
    READ, WRITE, DELETE, SHELL, AGENT, AGENT_PIPELINE, TODO, IMAGE_GENERATION, PHONE_CONTROL, GENERIC;

    /**
     * Fallback category for dynamic tool name prefixes (phone_*, agentx_*, mcpx_*)
     * when no registered tool info is available.
     */
    public static ToolDisplayCategory fallbackDisplayCategory(String name) {
        if (name == null) {
            return GENERIC;
        }
        if (name.startsWith("phone_")) {
            return PHONE_CONTROL;
        }
        if ("agent".equals(name) || name.startsWith("agentx_")) {
            return AGENT;
        }
        if ("agent_pipeline".equals(name)) {
            return AGENT_PIPELINE;
        }
        return GENERIC;
    }
}
