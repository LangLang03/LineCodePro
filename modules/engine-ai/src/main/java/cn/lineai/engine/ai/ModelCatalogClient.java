package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.ModelConfig;

/** Fetches the provider's model list ({@code GET /models}) for each supported protocol. */
public final class ModelCatalogClient {

    private static final String CODEX_VERSION = "0.120.0";
    private static final String CODEX_ORIGINATOR = "codex_cli_rs";

    public List<String> fetch(ModelConfig.Protocol protocol, String baseUrl, String apiKey) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            String url;
            switch (protocol) {
                case ANTHROPIC_MESSAGES:
                    headers.put("x-api-key", safe(apiKey));
                    headers.put("anthropic-version", "2023-06-01");
                    url = endpoint(rootOrigin(baseUrl), "/v1/models");
                    break;
                case CODEX_RESPONSES:
                    headers.put("Authorization", "Bearer " + safe(apiKey));
                    headers.put("version", CODEX_VERSION);
                    headers.put("originator", CODEX_ORIGINATOR);
                    headers.put("User-Agent", CODEX_ORIGINATOR + "/" + CODEX_VERSION + " (Android; LineCode)");
                    url = endpoint(baseUrl, "/models") + "?client_version="
                            + URLEncoder.encode(CODEX_VERSION, "UTF-8");
                    break;
                case OPENAI_COMPATIBLE:
                case LOCAL_GGUF:
                default:
                    headers.put("Authorization", "Bearer " + safe(apiKey));
                    url = endpoint(baseUrl, "/models");
                    break;
            }
            return parse(execute("GET", url, headers, null));
        } catch (IOException error) {
            throw new DomainException(ErrorCode.NETWORK, error.getClass().getSimpleName(), error);
        }
    }

    static List<String> parse(String raw) throws IOException {
        try {
            JSONArray data = new JSONObject(raw).optJSONArray("data");
            List<String> ids = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    String id = item == null ? "" : item.optString("id", "");
                    if (!id.isEmpty()) ids.add(id);
                }
            }
            Collections.sort(ids);
            return ids;
        } catch (org.json.JSONException error) {
            throw new IOException("Unexpected model list", error);
        }
    }

    static String execute(String method, String url, Map<String, String> headers, String body)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(30_000);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + (response.isEmpty() ? "" : ": " + response));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    static String read(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static byte[] download(String url) throws java.io.IOException {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new java.io.IOException("Image download HTTP " + code);
            }
            try (java.io.InputStream input = connection.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    out.write(buffer, 0, count);
                    if (out.size() > 20 * 1024 * 1024) {
                        throw new java.io.IOException("Image too large");
                    }
                }
                return out.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String endpoint(String baseUrl, String suffix) {
        String base = safe(baseUrl);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith(suffix)) return base;
        return base + suffix;
    }

    static String rootOrigin(String baseUrl) throws IOException {
        String base = safe(baseUrl);
        if (base.isEmpty()) base = "https://api.anthropic.com";
        if (!base.contains("://")) base = "https://" + base;
        URL url = new URL(base);
        String port = url.getPort() >= 0 ? ":" + url.getPort() : "";
        return url.getProtocol().toLowerCase(Locale.ROOT) + "://" + url.getHost() + port;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
