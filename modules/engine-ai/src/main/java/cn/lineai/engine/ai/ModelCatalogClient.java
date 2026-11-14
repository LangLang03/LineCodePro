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

/**
 * Fetches the provider's model list ({@code GET /models}) for each supported protocol.
 *
 * <p>Shared low-level HTTP helpers ({@link #execute}, {@link #read}, {@link #download},
 * {@link #endpoint}) back the other clients in this module; timeouts and error handling are
 * defined once here.</p>
 */
public final class ModelCatalogClient {

    /** Codex CLI protocol version sent by catalog requests. */
    private static final String CODEX_VERSION = "0.120.0";
    /** Originator identifier required by the Codex backend. */
    private static final String CODEX_ORIGINATOR = "codex_cli_rs";

    /**
     * Fetches the provider's model list.
     *
     * @param protocol wire protocol deciding headers and endpoint shape
     * @param baseUrl provider base URL
     * @param apiKey credential sent as the protocol-appropriate header
     * @return sorted model identifiers; empty when the provider returns none
     * @throws DomainException with {@link ErrorCode#NETWORK} when the request fails
     */
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

    /**
     * Parses a model list response body.
     *
     * @param raw response body
     * @return sorted model identifiers; empty entries are dropped
     * @throws IOException when the body is not a JSON model list
     */
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

    /**
     * Performs one blocking HTTP request.
     *
     * @param method HTTP method
     * @param url absolute URL
     * @param headers request headers applied in order
     * @param body UTF-8 request body, or {@code null} for requests without one
     * @return response body decoded as UTF-8
     * @throws IOException on transport failure or any non-2xx status; the message carries the
     *         status and, when present, the provider's error payload
     */
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

    /**
     * Drains a stream into a UTF-8 string.
     *
     * @param input stream to read; null yields the empty string
     * @return full stream contents as UTF-8 text
     * @throws IOException when reading fails
     */
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

    /**
     * Downloads binary content with a 20 MB safety cap.
     *
     * @param url absolute content URL
     * @return downloaded bytes
     * @throws java.io.IOException on transport failure, non-2xx status or oversized content
     */
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

    /**
     * Appends {@code suffix} to the base URL unless it already ends with it.
     *
     * @param baseUrl provider base URL, with or without trailing slash
     * @param suffix path suffix such as {@code /models}
     * @return URL whose path ends with {@code suffix}
     */
    static String endpoint(String baseUrl, String suffix) {
        String base = safe(baseUrl);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith(suffix)) return base;
        return base + suffix;
    }

    /**
     * Reduces a base URL to scheme, host and port.
     *
     * <p>A missing scheme defaults to HTTPS and an empty base to the Anthropic API origin, so
     * Anthropic-style paths can be appended safely.</p>
     *
     * @param baseUrl provider base URL
     * @return origin-only URL
     * @throws IOException when the URL cannot be parsed
     */
    static String rootOrigin(String baseUrl) throws IOException {
        String base = safe(baseUrl);
        if (base.isEmpty()) base = "https://api.anthropic.com";
        if (!base.contains("://")) base = "https://" + base;
        URL url = new URL(base);
        String port = url.getPort() >= 0 ? ":" + url.getPort() : "";
        return url.getProtocol().toLowerCase(Locale.ROOT) + "://" + url.getHost() + port;
    }

    /** Null-safe trim. */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
