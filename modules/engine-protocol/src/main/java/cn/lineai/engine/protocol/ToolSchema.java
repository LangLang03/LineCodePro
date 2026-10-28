package cn.lineai.engine.protocol;

/** Protocol-neutral tool description. */
public final class ToolSchema {

    public final String name;
    public final String description;
    public final String parametersJson;

    public ToolSchema(String name, String description, String parametersJson) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.parametersJson = parametersJson == null || parametersJson.trim().isEmpty()
                ? "{\"type\":\"object\",\"properties\":{}}" : parametersJson;
    }
}
