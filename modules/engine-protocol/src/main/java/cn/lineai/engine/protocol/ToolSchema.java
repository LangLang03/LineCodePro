package cn.lineai.engine.protocol;

import org.json.JSONObject;

/** Protocol-neutral tool description. */
public final class ToolSchema {

    private static final String EMPTY_SCHEMA = new JSONObject()
            .put("type", "object").put("properties", new JSONObject()).toString();

    public final String name;
    public final String description;
    public final String parametersJson;

    public ToolSchema(String name, String description, String parametersJson) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.parametersJson = parametersJson == null || parametersJson.trim().isEmpty()
                ? EMPTY_SCHEMA : parametersJson;
    }
}
