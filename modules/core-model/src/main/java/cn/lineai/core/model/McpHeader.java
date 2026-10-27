package cn.lineai.core.model;

/** One custom HTTP header sent with every MCP request. */
public final class McpHeader {

    public final String name;
    public final String value;

    public McpHeader(String name, String value) {
        this.name = name == null ? "" : name.trim();
        this.value = value == null ? "" : value.trim();
    }
}
