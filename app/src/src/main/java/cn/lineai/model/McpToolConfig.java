package cn.lineai.model;

public final class McpToolConfig {
    private final String id;
    private final String name;
    private final String description;
    private final boolean enabled;
    private final String[] tools;

    public McpToolConfig(String id, String name, String description, boolean enabled, String[] tools) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.enabled = enabled;
        this.tools = tools == null ? new String[0] : tools.clone();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String[] getTools() {
        return tools.clone();
    }
}
