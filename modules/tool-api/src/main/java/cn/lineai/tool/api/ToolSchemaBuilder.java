package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds tool argument schemas with org.json instead of hand-concatenated strings. */
public final class ToolSchemaBuilder {

    private final JSONObject properties = new JSONObject();
    private final JSONArray required = new JSONArray();

    private ToolSchemaBuilder() {
    }

    public static ToolSchemaBuilder object() {
        return new ToolSchemaBuilder();
    }

    public ToolSchemaBuilder string(String name, String description) {
        return property(name, new JSONObject().put("type", "string").put("description", description));
    }

    public ToolSchemaBuilder number(String name, String description) {
        return property(name, new JSONObject().put("type", "number").put("description", description));
    }

    public ToolSchemaBuilder enumString(String name, String description, String... values) {
        JSONArray options = new JSONArray();
        for (String value : values) options.put(value);
        return property(name, new JSONObject().put("type", "string")
                .put("description", description).put("enum", options));
    }

    public ToolSchemaBuilder objectArray(String name, String description,
                                         ToolSchemaBuilder items) {
        return property(name, new JSONObject().put("type", "array")
                .put("description", description).put("items", items.buildObject()));
    }

    public ToolSchemaBuilder property(String name, JSONObject schema) {
        properties.put(name, schema);
        return this;
    }

    public ToolSchemaBuilder required(String name) {
        required.put(name);
        return this;
    }

    public JSONObject buildObject() {
        JSONObject root = new JSONObject().put("type", "object").put("properties", properties);
        if (required.length() > 0) root.put("required", required);
        return root;
    }

    public String build() {
        return buildObject().toString();
    }
}
