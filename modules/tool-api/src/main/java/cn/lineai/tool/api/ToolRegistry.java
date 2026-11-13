package cn.lineai.tool.api;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Name-indexed tool set.
 *
 * <p>Lookup is by the model-facing tool name; insertion order is preserved so catalog listings
 * stay stable across sessions.</p>
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * Registers one tool under its declared name; later tools replace earlier ones.
     *
     * @param tool tool to register
     * @return this registry for chaining
     * @throws IllegalArgumentException when the tool is {@code null} or has an empty name
     */
    public ToolRegistry register(Tool tool) {
        if (tool == null || tool.name().isEmpty()) throw new IllegalArgumentException("invalid tool");
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * Looks up a tool by name.
     *
     * @param name model-facing tool name
     * @return the registered tool, or {@code null} when the name is unknown
     */
    public Tool find(String name) {
        return tools.get(name);
    }

    /**
     * Builds card metadata for a call without parsed arguments.
     *
     * @param name model-facing tool name, known or unknown
     * @return presentation from the tool, or a generic card for unknown names
     */
    public ToolPresentation presentation(String name) {
        return presentation(name, (JSONObject) null);
    }

    /**
     * Builds card metadata from raw argument JSON.
     *
     * @param name model-facing tool name, known or unknown
     * @param argumentsJson raw argument JSON; malformed input is tolerated
     * @return presentation from the tool, or a generic card for unknown names
     */
    public ToolPresentation presentation(String name, String argumentsJson) {
        return presentation(name, ToolJson.object(argumentsJson));
    }

    /**
     * Builds card metadata for one call, falling back to a generic card for unknown tools.
     *
     * @param name model-facing tool name, known or unknown
     * @param arguments parsed arguments; may be {@code null}
     * @return presentation from the tool itself, or a generic card
     */
    public ToolPresentation presentation(String name, JSONObject arguments) {
        JSONObject safeArguments = arguments == null ? new JSONObject() : arguments;
        Tool tool = find(name);
        return tool == null
                ? ToolPresentation.generic(name, safeArguments)
                : tool.presentation(safeArguments);
    }

    /**
     * Snapshots all registered tools in insertion order.
     *
     * @return an independent list of the registered tools
     */
    public Collection<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Filters the registry by the mutation permission of the current mode.
     *
     * @param mutationAllowed {@code true} to include mutating tools
     * @return tools whose {@link Tool#mutating()} flag matches the permission
     */
    public List<Tool> enabled(boolean mutationAllowed) {
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (mutationAllowed || !tool.mutating()) result.add(tool);
        }
        return result;
    }
}
