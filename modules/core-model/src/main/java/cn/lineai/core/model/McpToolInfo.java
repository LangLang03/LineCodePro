package cn.lineai.core.model;

/** One tool exposed by an MCP server via tools/list. */
public final class McpToolInfo {

    public final String name;
    public final boolean enabled;
    public final String description;
    public final String inputSchemaJson;

    public McpToolInfo(String name, boolean enabled, String description, String inputSchemaJson) {
        this.name = name == null ? "" : name;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
        this.inputSchemaJson = inputSchemaJson == null ? "" : inputSchemaJson;
    }

    public McpToolInfo withEnabled(boolean value) {
        return new McpToolInfo(name, value, description, inputSchemaJson);
    }
}
