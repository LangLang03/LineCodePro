package cn.lineai.security;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 HTTP 基础设施，自动执行 UrlPolicy 校验、设置标准请求头、保证连接断开。
 */
public final class SimpleHttpClient {

    private SimpleHttpClient() {
    }

    public static String postJson(String url, String jsonBody, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        return postJson(url, jsonBody, connectTimeoutMs, readTimeoutMs, Collections.<String, String>emptyMap());
    }

    public static String postJson(String url, String jsonBody, int connectTimeoutMs, int readTimeoutMs,
                                  Map<String, String> headers) throws Exception {
        Request request = new Request(url, "POST", jsonBody);
        request.connectTimeoutMs = connectTimeoutMs;
        request.readTimeoutMs = readTimeoutMs;
        request.headers.put("Content-Type", "application/json");
        request.headers.put("Accept", "application/json");
        if (headers != null) {
            request.headers.putAll(headers);
        }
        Response response = execute(request);
        if (response.code < 200 || response.code >= 300) {
            throw new Exception("HTTP " + response.code + ": " + response.body);
        }
        return response.body;
    }

    public static String get(String url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        return get(url, connectTimeoutMs, readTimeoutMs, Collections.<String, String>emptyMap());
    }

    public static String get(String url, int connectTimeoutMs, int readTimeoutMs,
                             Map<String, String> headers) throws Exception {
        Request request = new Request(url, "GET", null);
        request.connectTimeoutMs = connectTimeoutMs;
        request.readTimeoutMs = readTimeoutMs;
        request.headers.put("Accept", "application/json");
        if (headers != null) {
            request.headers.putAll(headers);
        }
        Response response = execute(request);
        if (response.code < 200 || response.code >= 300) {
            throw new Exception("HTTP " + response.code + ": " + response.body);
        }
        return response.body;
    }

    public static DownloadResult download(String url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        return download(url, connectTimeoutMs, readTimeoutMs, Integer.MAX_VALUE);
    }

    public static DownloadResult download(String url, int connectTimeoutMs, int readTimeoutMs, int maxBytes) throws Exception {
        String safeUrl = UrlPolicy.requireHttpOrLocalCleartextUrl(url, "URL");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(safeUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LineCode/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP download failed: " + code);
            }
            String mimeType = connection.getContentType();
            if (mimeType == null || mimeType.length() == 0) {
                mimeType = "application/octet-stream";
            } else {
                int semicolon = mimeType.indexOf(';');
                if (semicolon > 0) {
                    mimeType = mimeType.substring(0, semicolon).trim();
                }
            }
            return new DownloadResult(mimeType, readBytes(connection.getInputStream(), maxBytes));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static Response execute(Request request) throws Exception {
        String safeUrl = UrlPolicy.requireHttpOrLocalCleartextUrl(request.url, "URL");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(safeUrl).openConnection();
            connection.setRequestMethod(request.method);
            connection.setConnectTimeout(request.connectTimeoutMs);
            connection.setReadTimeout(request.readTimeoutMs);
            connection.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            byte[] requestBytes = request.bodyBytes != null
                    ? request.bodyBytes
                    : request.body == null ? null : request.body.getBytes(StandardCharsets.UTF_8);
            if (requestBytes != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(requestBytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(requestBytes);
                } finally {
                    output.close();
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String contentType = connection.getContentType();
            String message = connection.getResponseMessage();
            String body = readStream(stream);
            LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
            Map<String, java.util.List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null) {
                for (Map.Entry<String, java.util.List<String>> entry : headerFields.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                        continue;
                    }
                    responseHeaders.put(entry.getKey(), entry.getValue().get(0));
                }
            }
            return new Response(code, message, contentType, body, responseHeaders);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String readStream(InputStream input) throws Exception {
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

    public static byte[] readBytes(InputStream input, int maxBytes) throws Exception {
        if (input == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new Exception("Data too large, current limit is " + (maxBytes / 1024 / 1024) + " MB.");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    public static final class Request {
        public String url;
        public String method;
        public String body;
        public byte[] bodyBytes;
        public int connectTimeoutMs = 15000;
        public int readTimeoutMs = 30000;
        public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

        public Request(String url, String method, String body) {
            this.url = url;
            this.method = method;
            this.body = body;
        }
    }

    public static final class Response {
        public final int code;
        public final String message;
        public final String contentType;
        public final String body;
        public final Map<String, String> headers;

        public Response(int code, String message, String contentType, String body) {
            this(code, message, contentType, body, Collections.<String, String>emptyMap());
        }

        public Response(int code, String message, String contentType, String body, Map<String, String> headers) {
            this.code = code;
            this.message = message == null ? "" : message;
            this.contentType = contentType == null ? "" : contentType;
            this.body = body == null ? "" : body;
            this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        }
    }

    public static final class DownloadResult {
        public final String mimeType;
        public final byte[] bytes;

        public DownloadResult(String mimeType, byte[] bytes) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }
}
