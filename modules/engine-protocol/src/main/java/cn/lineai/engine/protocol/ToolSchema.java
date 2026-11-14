package cn.lineai.engine.protocol;

import org.json.JSONObject;

/**
 * Protocol-neutral tool description.
 *
 * <p>{@code parametersJson} must be a JSON object (in practice a JSON Schema); an empty or blank
 * value is replaced by a minimal empty-object schema so every codec can embed it directly.</p>
 */
public final class ToolSchema {

    private static final String EMPTY_SCHEMA = new JSONObject()
            .put("type", "object").put("properties", new JSONObject()).toString();

    /** Tool name as the model must reference it. */
    public final String name;
    /** Natural-language description shown to the model. */
    public final String description;
    /** JSON Schema object for the parameters, never empty. */
    public final String parametersJson;

    /**
     * Creates a schema; blank parameter JSON is replaced by an empty object schema.
     *
     * @param name tool name, may be null
     * @param description tool description, may be null
     * @param parametersJson JSON Schema for the parameters, may be null or blank
     */
    public ToolSchema(String name, String description, String parametersJson) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.parametersJson = parametersJson == null || parametersJson.trim().isEmpty()
                ? EMPTY_SCHEMA : parametersJson;
    }
}
