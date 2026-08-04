package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.security.SimpleHttpClient;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

public final class CustomMcpHttpTool extends BaseTool {
    private final String name;
    private final ExtensionMcpConfig mcp;
    private final McpToolSummary tool;

    public CustomMcpHttpTool(String name, ExtensionMcpConfig mcp, McpToolSummary tool) {
        this.name = name == null ? "" : name;
        this.mcp = mcp;
        this.tool = tool;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("Invoke the tool ").append(tool.getName()).append(" of the custom HTTP MCP \"").append(mcp.getName()).append("\".");
        if (tool.getDescription().length() > 0) {
            builder.append('\n').append(tool.getDescription());
        }
        builder.append("\nMCP address: ").append(mcp.getUrl());
        return builder.toString();
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.GENERIC;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        if (tool.getInputSchemaJson().length() > 0) {
            JSONObject schema = new JSONObject(tool.getInputSchemaJson());
            if ("object".equals(schema.optString("type"))) {
                return schema;
            }
        }
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("additionalProperties", true);
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            JSONObject body = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", "linecode_" + System.currentTimeMillis())
                    .put("method", "tools/call")
                    .put("params", new JSONObject()
                            .put("name", tool.getName())
                            .put("arguments", input == null ? new JSONObject() : input));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json, text/event-stream");
            headers.put("Content-Type", "application/json");
            for (McpRequestHeader header : mcp.getRequestHeaders()) {
                if (header.getName().length() > 0) {
                    headers.put(header.getName(), header.getValue());
                }
            }
            SimpleHttpClient.Request request = new SimpleHttpClient.Request(mcp.getUrl(), "POST", body.toString());
            request.connectTimeoutMs = 15000;
            request.readTimeoutMs = 60000;
            request.headers.putAll(headers);
            SimpleHttpClient.Response response = SimpleHttpClient.execute(request);
            if (response.code < 200 || response.code >= 300) {
                return error(response.code + ": " + response.body);
            }
            return parseResult(response.body, context);
        } catch (Exception e) {
            return error(context.getString(R.string.tool_mcp_call_failed, e.getMessage()));
        }
    }

    private ToolResult parseResult(String text, ToolContext context) {
        try {
            JSONObject parsed = new JSONObject(extractEventData(text));
            if (parsed.has("error") && !parsed.isNull("error")) {
                return error(summarize(parsed.opt("error")));
            }
            Object result = parsed.has("result") ? parsed.opt("result") : parsed;
            String content = summarize(result);
            return ok(content.length() == 0 ? context.getString(R.string.tool_mcp_completed) : content);
        } catch (Exception ignored) {
            return ok(text == null ? "" : text);
        }
    }

    private String summarize(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String text = object.optString("text");
            if (text.length() > 0) {
                return text;
            }
            String content = object.optString("content");
            if (content.length() > 0) {
                return content;
            }
            String message = object.optString("message");
            if (message.length() > 0) {
                return message;
            }
        }
        return String.valueOf(value);
    }

    private String extractEventData(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = (text == null ? "" : text).split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String value = line.substring("data:".length()).trim();
            if (value.length() > 0 && !"[DONE]".equals(value)) {
                builder.append(value);
            }
        }
        return builder.length() == 0 ? (text == null ? "" : text) : builder.toString();
    }

}
