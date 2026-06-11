package cn.lineai.model;

public final class McpToolSummary {
    private final String name;
    private final boolean enabled;
    private final String description;
    private final String inputSchemaJson;

    public McpToolSummary(String name, boolean enabled, String description, String inputSchemaJson) {
        this.name = name == null ? "" : name;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
        this.inputSchemaJson = inputSchemaJson == null ? "" : inputSchemaJson;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }
}
