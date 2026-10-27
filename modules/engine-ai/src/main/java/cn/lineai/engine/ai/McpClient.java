package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.McpHeader;
import cn.lineai.core.model.McpToolInfo;

/** Minimal MCP HTTP client: tools/list discovery and tools/call execution. */
public final class McpClient {

    public static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/") && value.length() > 8) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid MCP URL: " + raw);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equals(scheme)) {
            boolean local = "http".equals(scheme) && ("localhost".equals(host)
                    || "127.0.0.1".equals(host) || "::1".equals(host) || "10.0.2.2".equals(host));
            if (!local) {
                throw new IllegalArgumentException("MCP URL must start with https:// (http only for localhost)");
            }
        }
        return value;
    }

    public static List<McpToolInfo> parseTools(String body) {
        List<McpToolInfo> result = new ArrayList<>();
        JSONObject parsed = new JSONObject(jsonPayload(body));
        JSONObject resultObject = parsed.optJSONObject("result");
        JSONObject data = parsed.optJSONObject("data");
        JSONArray tools = resultObject == null ? null : resultObject.optJSONArray("tools");
        if (tools == null) tools = parsed.optJSONArray("tools");
        if (tools == null && data != null) tools = data.optJSONArray("tools");
        if (tools == null && resultObject != null) tools = resultObject.optJSONArray("servers");
        if (tools == null) tools = parsed.optJSONArray("servers");
        if (tools == null) return result;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;
            String name = tool.optString("name", "").trim();
            if (name.isEmpty()) continue;
            JSONObject schema = tool.optJSONObject("inputSchema");
            String schemaJson = schema == null ? "{}" : schema.toString();
            result.add(new McpToolInfo(name, true,
                    tool.optString("description", ""), schemaJson));
        }
        return result;
    }

    public static String toolResultText(String body) {
        JSONObject parsed = new JSONObject(jsonPayload(body));
        JSONObject error = parsed.optJSONObject("error");
        if (error != null) {
            return "MCP error: " + error.optString("message", error.toString());
        }
        JSONObject result = parsed.optJSONObject("result");
        if (result == null) return body;
        JSONArray content = result.optJSONArray("content");
        if (content == null) return result.toString();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item == null) continue;
            String value = item.optString("text", "");
            if (value.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }
        return text.length() == 0 ? result.toString() : text.toString();
    }

    public List<McpToolInfo> listTools(String url, List<McpHeader> headers) throws IOException {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", "linecode_" + System.currentTimeMillis());
        request.put("method", "tools/list");
        request.put("params", new JSONObject());
        return parseTools(post(url, headers, request.toString()));
    }

    public String callTool(String url, List<McpHeader> headers, String toolName,
                           String argumentsJson) throws IOException {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", "linecode_" + System.currentTimeMillis());
        request.put("method", "tools/call");
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", new JSONObject(argumentsJson == null || argumentsJson.trim().isEmpty()
                ? "{}" : argumentsJson));
        request.put("params", params);
        return toolResultText(post(url, headers, request.toString()));
    }

    private String post(String url, List<McpHeader> headers, String body) throws IOException {
        String endpoint = normalizeUrl(url);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("Content-Type", "application/json");
            for (McpHeader header : headers) {
                if (!header.name.isEmpty()) {
                    connection.setRequestProperty(header.name, header.value);
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream);
            if (code < 200 || code >= 300) {
                throw new IOException("MCP HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                out.append(buffer, 0, count);
            }
        }
        return out.toString();
    }

    private static String jsonPayload(String body) {
        String text = body == null ? "" : body.trim();
        if (text.startsWith("{") || text.startsWith("[")) return text;
        int start = text.lastIndexOf("data:");
        if (start >= 0) {
            String candidate = text.substring(start + "data:".length()).trim();
            int end = candidate.indexOf('\n');
            if (end >= 0) candidate = candidate.substring(0, end).trim();
            if (candidate.startsWith("{")) return candidate;
        }
        return text;
    }
}
