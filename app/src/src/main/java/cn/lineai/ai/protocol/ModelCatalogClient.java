package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.security.UrlPolicy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ModelCatalogClient {
    public List<String> fetch(ModelProtocolType protocolType, String baseUrl, String apiKey) throws ModelCompletionException {
        if (protocolType == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return fetchAnthropic(baseUrl, apiKey);
        }
        if (protocolType == ModelProtocolType.CODEX_RESPONSES) {
            return fetchCodex(baseUrl, apiKey);
        }
        return fetchOpenAiCompatible(baseUrl, apiKey);
    }

    private List<String> fetchOpenAiCompatible(String baseUrl, String apiKey) throws ModelCompletionException {
        HttpURLConnection connection = null;
        try {
            connection = openGet(endpoint(baseUrl, "/models"));
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            return readModelIds(connection);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("模型列表查询失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<String> fetchCodex(String baseUrl, String apiKey) throws ModelCompletionException {
        HttpURLConnection connection = null;
        try {
            connection = openGet(appendQuery(endpoint(baseUrl, "/models"),
                    "client_version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION));
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION);
            connection.setRequestProperty("originator", CodexResponsesProtocol.CODEX_ORIGINATOR);
            connection.setRequestProperty("User-Agent", CodexResponsesProtocol.codexUserAgent());
            return readModelIds(connection);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex 模型列表查询失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<String> fetchAnthropic(String baseUrl, String apiKey) throws ModelCompletionException {
        HttpURLConnection connection = null;
        try {
            connection = openGet(endpoint(rootOrigin(baseUrl), "/v1/models"));
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", "2023-06-01");
            return readModelIds(connection);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Anthropic 模型列表查询失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openGet(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                UrlPolicy.requireHttpOrLocalCleartextUrl(url, "模型 API 地址")
        ).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private List<String> readModelIds(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        String raw = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new ModelCompletionException("HTTP " + code + ": " + raw);
        }
        JSONObject body = new JSONObject(raw);
        JSONArray data = body.optJSONArray("data");
        ArrayList<String> ids = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id");
                if (id.length() > 0) {
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private String endpoint(String baseUrl, String suffix) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(suffix)) {
            return base;
        }
        return base + suffix;
    }

    private String appendQuery(String baseUrl, String key, String value) throws Exception {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + URLEncoder.encode(key, "UTF-8")
                + "="
                + URLEncoder.encode(value, "UTF-8");
    }

    private String rootOrigin(String baseUrl) throws Exception {
        String base = baseUrl == null || baseUrl.trim().length() == 0 ? "https://api.anthropic.com" : baseUrl.trim();
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        URL url = new URL(base);
        return url.getProtocol() + "://" + url.getHost() + (url.getPort() >= 0 ? ":" + url.getPort() : "");
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
