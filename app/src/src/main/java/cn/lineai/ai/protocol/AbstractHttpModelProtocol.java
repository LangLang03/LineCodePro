package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.security.UrlPolicy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;

abstract class AbstractHttpModelProtocol implements ModelProtocol {
    protected interface SseEventHandler {
        void onEvent(String eventType, String data) throws Exception;
    }

    protected static final class SseStreamCompleteException extends Exception {
    }

    protected String postJson(String url, JSONObject body, Map<String, String> headers) throws ModelCompletionException {
        return postJson(url, body, headers, null);
    }

    protected String postJson(
            String url,
            JSONObject body,
            Map<String, String> headers,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        HttpURLConnection connection = null;
        try {
            connection = openJsonPost(url, body, headers, "application/json");
            HttpURLConnection activeConnection = connection;
            if (cancellationToken != null) {
                cancellationToken.onCancel(activeConnection::disconnect);
            }

            int code = connection.getResponseCode();
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return "";
            }
            String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new ModelCompletionException("HTTP " + code + ": " + response);
            }
            return response;
        } catch (ModelCompletionException e) {
            throw e;
        } catch (IOException e) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return "";
            }
            throw new ModelCompletionException("模型通信失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ModelCompletionException("模型通信失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected void postJsonSse(
            String url,
            JSONObject body,
            Map<String, String> headers,
            ModelCancellationToken cancellationToken,
            SseEventHandler handler
    ) throws ModelCompletionException {
        HttpURLConnection connection = null;
        try {
            connection = openJsonPost(url, body, headers, "text/event-stream");
            HttpURLConnection activeConnection = connection;
            if (cancellationToken != null) {
                cancellationToken.onCancel(activeConnection::disconnect);
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new ModelCompletionException("HTTP " + code + ": " + readAll(connection.getErrorStream()));
            }

            readSse(connection.getInputStream(), cancellationToken, handler);
        } catch (SseStreamCompleteException e) {
            return;
        } catch (ModelCompletionException e) {
            throw e;
        } catch (IOException e) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            throw new ModelCompletionException("模型流式通信失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ModelCompletionException("模型流式通信失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String endpoint(String baseUrl, String suffix) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(suffix)) {
            return base;
        }
        return base + suffix;
    }

    private HttpURLConnection openJsonPost(String url, JSONObject body, Map<String, String> headers, String accept) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                UrlPolicy.requireHttpOrLocalCleartextUrl(url, "模型 API 地址")
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", accept);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getValue() != null && header.getValue().length() > 0) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(body.toString());
        writer.flush();
        writer.close();
        return connection;
    }

    private void readSse(InputStream stream, ModelCancellationToken cancellationToken, SseEventHandler handler) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            StringBuilder data = new StringBuilder();
            String eventType = "";
            String line;
            while ((cancellationToken == null || !cancellationToken.isCancelled()) && (line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    if (data.length() > 0) {
                        handler.onEvent(eventType, data.toString());
                        data.setLength(0);
                        eventType = "";
                    }
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventType = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    String value = line.substring("data:".length());
                    data.append(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            if ((cancellationToken == null || !cancellationToken.isCancelled()) && data.length() > 0) {
                handler.onEvent(eventType, data.toString());
            }
        } finally {
            reader.close();
        }
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
