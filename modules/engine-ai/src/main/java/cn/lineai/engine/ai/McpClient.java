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

/**
 * Minimal MCP HTTP client: tools/list discovery and tools/call execution.
 *
 * <p>Requests are JSON-RPC 2.0 POSTs; responses are accepted either as plain JSON or as an SSE
 * body whose last {@code data:} line carries the JSON payload, matching servers that answer
 * {@code text/event-stream}.</p>
 */
public final class McpClient {

    /**
     * Validates and normalises an MCP endpoint URL.
     *
     * <p>HTTPS is mandatory except for loopback and emulator-local HTTP, which keeps MCP traffic
     * and credentials off plaintext transport on public networks.</p>
     *
     * @param raw candidate URL
     * @return trimmed URL without trailing slashes
     * @throws IllegalArgumentException when the URL is not absolute HTTPS, or HTTP on a
     *         loopback/emulator host
     */
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

    /**
     * Extracts tool descriptors from a tools/list response.
     *
     * <p>Accepts the {@code result.tools}, top-level {@code tools}, {@code data.tools} and
     * {@code servers} shapes used by different MCP servers and gateways.</p>
     *
     * @param body raw response body
     * @return tool descriptors with name, description and input schema JSON
     * @throws org.json.JSONException when the body is not JSON at all
     */
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

    /**
     * Extracts human-readable text from a tools/call response.
     *
     * @param body raw response body
     * @return joined text blocks, the serialized result when no text blocks exist, or a prefixed
     *         message when the response carries a JSON-RPC error
     * @throws org.json.JSONException when the body is not JSON at all
     */
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

    /**
     * Discovers the tools exposed by an MCP server.
     *
     * @param url MCP endpoint URL, validated by {@link #normalizeUrl(String)}
     * @param headers authentication headers to send
     * @return discovered tools
     * @throws IOException on transport failure or non-2xx status
     * @throws org.json.JSONException when the response is not JSON at all
     */
    public List<McpToolInfo> listTools(String url, List<McpHeader> headers) throws IOException {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", "linecode_" + System.currentTimeMillis());
        request.put("method", "tools/list");
        request.put("params", new JSONObject());
        return parseTools(post(url, headers, request.toString()));
    }

    /**
     * Invokes one MCP tool.
     *
     * @param url MCP endpoint URL, validated by {@link #normalizeUrl(String)}
     * @param headers authentication headers to send
     * @param toolName tool to invoke
     * @param argumentsJson JSON object of tool arguments; blank yields an empty object
     * @return result text as produced by {@link #toolResultText(String)}
     * @throws IOException on transport failure or non-2xx status
     * @throws org.json.JSONException when the arguments are not a JSON object or the response is
     *         not JSON at all
     */
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

    /**
     * Posts one JSON-RPC request and returns the raw response body.
     *
     * @throws IOException on transport failure or non-2xx status
     */
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

    /** Reads a UTF-8 response body; a null stream yields the empty string. */
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

    /** Extracts the JSON object from a body that may be plain JSON or an SSE data line. */
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
