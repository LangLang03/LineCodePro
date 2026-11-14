package cn.lineai.tool.api;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds tool argument schemas with org.json instead of hand-concatenated strings.
 *
 * <p>Declarative construction keeps the model-facing schemas syntactically valid; a malformed
 * schema would otherwise disable the tool for providers that validate strictly.</p>
 */
public final class ToolSchemaBuilder {

    private final JSONObject properties = new JSONObject();
    private final JSONArray required = new JSONArray();

    /** Not instantiable; use {@link #object()}. */
    private ToolSchemaBuilder() {
    }

    /**
     * Starts an object-type schema.
     *
     * @return an empty builder awaiting properties
     */
    public static ToolSchemaBuilder object() {
        return new ToolSchemaBuilder();
    }

    /**
     * Declares a required-shape string property.
     *
     * @param name property name the model uses
     * @param description what the value means to the model
     * @return this builder for chaining
     */
    public ToolSchemaBuilder string(String name, String description) {
        return property(name, new JSONObject().put("type", "string").put("description", description));
    }

    /**
     * Declares a number property.
     *
     * @param name property name the model uses
     * @param description what the value means to the model
     * @return this builder for chaining
     */
    public ToolSchemaBuilder number(String name, String description) {
        return property(name, new JSONObject().put("type", "number").put("description", description));
    }

    /**
     * Declares a boolean property.
     *
     * @param name property name the model uses
     * @param description what the value means to the model
     * @return this builder for chaining
     */
    public ToolSchemaBuilder bool(String name, String description) {
        return property(name, new JSONObject().put("type", "boolean").put("description", description));
    }

    /**
     * Declares a string property restricted to the given values.
     *
     * @param name property name the model uses
     * @param description what the value means to the model
     * @param values the permitted options, exposed as the schema {@code enum}
     * @return this builder for chaining
     */
    public ToolSchemaBuilder enumString(String name, String description, String... values) {
        JSONArray options = new JSONArray();
        for (String value : values) options.put(value);
        return property(name, new JSONObject().put("type", "string")
                .put("description", description).put("enum", options));
    }

    /**
     * Declares an array property whose entries are objects built by the given sub-schema.
     *
     * @param name property name the model uses
     * @param description what the entries mean to the model
     * @param items builder describing one entry object
     * @return this builder for chaining
     */
    public ToolSchemaBuilder objectArray(String name, String description,
                                         ToolSchemaBuilder items) {
        return property(name, new JSONObject().put("type", "array")
                .put("description", description).put("items", items.buildObject()));
    }

    /**
     * Declares a property with a fully custom schema fragment.
     *
     * @param name property name the model uses
     * @param schema complete JSON-Schema fragment for the property
     * @return this builder for chaining
     */
    public ToolSchemaBuilder property(String name, JSONObject schema) {
        properties.put(name, schema);
        return this;
    }

    /**
     * Marks a previously declared property as required.
     *
     * @param name property name already added via a {@code property} call
     * @return this builder for chaining
     */
    public ToolSchemaBuilder required(String name) {
        required.put(name);
        return this;
    }

    /**
     * Produces the schema as a JSON object.
     *
     * @return an object schema carrying all declared properties and required names
     */
    public JSONObject buildObject() {
        JSONObject root = new JSONObject().put("type", "object").put("properties", properties);
        if (required.length() > 0) root.put("required", required);
        return root;
    }

    /**
     * Produces the schema as the serialized string stored in {@link Tool#parametersJson()}.
     *
     * @return the serialized JSON-Schema object
     */
    public String build() {
        return buildObject().toString();
    }
}
