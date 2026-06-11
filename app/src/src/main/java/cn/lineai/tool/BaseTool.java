package cn.lineai.tool;

import org.json.JSONObject;

public abstract class BaseTool {
    public abstract String getName();

    public abstract String getDescription();

    public abstract ToolCategory getCategory();

    public boolean requiresConfirmation() {
        return false;
    }

    public abstract JSONObject getParameters() throws org.json.JSONException;

    public abstract ToolResult execute(JSONObject input, ToolContext context);

    public final JSONObject toJson() throws org.json.JSONException {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", getParameters());
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }
}
