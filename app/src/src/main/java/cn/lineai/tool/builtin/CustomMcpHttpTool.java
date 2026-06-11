package cn.lineai.tool.builtin;

import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        builder.append("调用自定义 HTTP MCP「").append(mcp.getName()).append("」的工具 ").append(tool.getName()).append("。");
        if (tool.getDescription().length() > 0) {
            builder.append('\n').append(tool.getDescription());
        }
        builder.append("\nMCP 地址: ").append(mcp.getUrl());
        return builder.toString();
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
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
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", "linecode_" + System.currentTimeMillis())
                    .put("method", "tools/call")
                    .put("params", new JSONObject()
                            .put("name", tool.getName())
                            .put("arguments", input == null ? new JSONObject() : input));
            connection = (HttpURLConnection) new URL(UrlPolicy.requireHttpOrLocalCleartextUrl(mcp.getUrl(), "MCP 地址")).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("Content-Type", "application/json");
            for (McpRequestHeader header : mcp.getRequestHeaders()) {
                if (header.getName().length() > 0) {
                    connection.setRequestProperty(header.getName(), header.getValue());
                }
            }
            connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
            int code = connection.getResponseCode();
            String text = readStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                return error(code + ": " + text);
            }
            return parseResult(text);
        } catch (Exception e) {
            return error("自定义 MCP 调用失败: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ToolResult parseResult(String text) {
        try {
            JSONObject parsed = new JSONObject(extractEventData(text));
            if (parsed.has("error") && !parsed.isNull("error")) {
                return error(summarize(parsed.opt("error")));
            }
            Object result = parsed.has("result") ? parsed.opt("result") : parsed;
            String content = summarize(result);
            return ok(content.length() == 0 ? "MCP 工具执行完成" : content);
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

    private String readStream(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
