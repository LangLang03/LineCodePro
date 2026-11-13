package cn.lineai.core.model;

/** One tool exposed by an MCP server via tools/list. */
public final class McpToolInfo {

    public final String name;
    public final boolean enabled;
    public final String description;
    public final String inputSchemaJson;

    /**
     * Creates a tool descriptor, normalising null strings to empty.
     * @param name tool name as reported by tools/list
     * @param enabled whether the tool is offered to the model
     * @param description tool description from the server
     * @param inputSchemaJson JSON schema of the tool input
     */
    public McpToolInfo(String name, boolean enabled, String description, String inputSchemaJson) {
        this.name = name == null ? "" : name;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
        this.inputSchemaJson = inputSchemaJson == null ? "" : inputSchemaJson;
    }

    /**
     * Returns a copy with the enabled flag replaced.
     * @param value new enabled state
     * @return a new immutable descriptor
     */
    public McpToolInfo withEnabled(boolean value) {
        return new McpToolInfo(name, value, description, inputSchemaJson);
    }
}
