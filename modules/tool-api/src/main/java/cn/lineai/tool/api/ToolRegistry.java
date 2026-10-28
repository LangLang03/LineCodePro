package cn.lineai.tool.api;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Name-indexed tool set. */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        if (tool == null || tool.name().isEmpty()) throw new IllegalArgumentException("invalid tool");
        tools.put(tool.name(), tool);
        return this;
    }

    public Tool find(String name) {
        return tools.get(name);
    }

    public ToolPresentation presentation(String name) {
        return presentation(name, (JSONObject) null);
    }

    public ToolPresentation presentation(String name, String argumentsJson) {
        JSONObject arguments;
        try {
            arguments = argumentsJson == null || argumentsJson.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(argumentsJson);
        } catch (Exception ignored) {
            arguments = new JSONObject();
        }
        return presentation(name, arguments);
    }

    public ToolPresentation presentation(String name, JSONObject arguments) {
        JSONObject safeArguments = arguments == null ? new JSONObject() : arguments;
        Tool tool = find(name);
        return tool == null
                ? ToolPresentation.generic(name, safeArguments)
                : tool.presentation(safeArguments);
    }

    public Collection<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    public List<Tool> enabled(boolean mutationAllowed) {
        List<Tool> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (mutationAllowed || !tool.mutating()) result.add(tool);
        }
        return result;
    }
}
