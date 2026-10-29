package cn.lineai.tool.workspace;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;
import cn.lineai.tool.api.ToolSchemaBuilder;

/**
 * Fetches one web page and returns its readable text.
 *
 * <p>Only HTTPS is accepted, plus HTTP for loopback/emulator hosts used during development.
 * HTML is reduced to text with scripts/styles/scripts stripped, and the result is truncated to the
 * requested character budget.</p>
 */
public final class WebFetchTool implements Tool {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_CHARS = 12_000;
    private static final int MAX_CHARS = 30_000;

    @Override public String name() { return "web_fetch"; }

    @Override public String description() {
        return "View and extract the text content of a web page. The URL must use HTTPS, "
                + "or HTTP on localhost/127.0.0.1/10.0.2.2.";
    }

    @Override public String parametersJson() {
        return ToolSchemaBuilder.object()
                .string("url", "The web page URL to view")
                .number("maxChars", "Maximum characters to return, default 12000, max 30000")
                .required("url")
                .build();
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Web fetch", ToolIcon.FETCH,
                arguments.optString("url", ""), ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String url = arguments.optString("url", "").trim();
        if (url.isEmpty()) {
            return ToolResult.failure("", "url must not be empty");
        }
        if (!allowedUrl(url)) {
            return ToolResult.failure("", "Only HTTPS URLs are allowed (HTTP only for loopback)");
        }
        int limit = arguments.optInt("maxChars", DEFAULT_CHARS);
        limit = Math.max(1000, Math.min(limit <= 0 ? DEFAULT_CHARS : limit, MAX_CHARS));
        try {
            return ToolResult.success("", fetch(url, limit));
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private String fetch(String rawUrl, int limit) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.6");
            connection.setRequestProperty("User-Agent", "LineCode/1.0");
            int code = connection.getResponseCode();
            String contentType = connection.getContentType();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = read(stream, contentType);
            if (code < 200 || code >= 300) {
                String detail = body.length() > 2000 ? body.substring(0, 2000) : body;
                throw new IOException("Web request failed " + code + ": " + detail);
            }
            String normalized = contentType != null
                    && contentType.toLowerCase(Locale.ROOT).contains("html")
                    ? htmlToText(body) : body;
            String compact = normalized.replaceAll("\\n{3,}", "\n\n").trim();
            if (compact.isEmpty()) {
                return "Web page content is empty or could not extract body text.";
            }
            if (compact.length() > limit) {
                return compact.substring(0, limit)
                        + "\n\n[Content truncated, original length approx. "
                        + compact.length() + " characters]";
            }
            return compact;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Rejects plain HTTP except loopback/emulator hosts so page text cannot be sniffed. */
    static boolean allowedUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null) return false;
            if ("https".equalsIgnoreCase(scheme)) return true;
            if (!"http".equalsIgnoreCase(scheme)) return false;
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "10.0.2.2".equals(host);
        } catch (Exception error) {
            return false;
        }
    }

    static String htmlToText(String html) {
        String value = html == null ? "" : html;
        value = value.replaceAll("(?is)<script[\\s\\S]*?</script>", " ");
        value = value.replaceAll("(?is)<style[\\s\\S]*?</style>", " ");
        value = value.replaceAll("(?is)<noscript[\\s\\S]*?</noscript>", " ");
        value = value.replaceAll("(?is)</(p|div|section|article|header|footer|li|h[1-6]|tr)\\s*>", "\n");
        value = value.replaceAll("(?is)<br\\s*/?>", "\n");
        value = value.replaceAll("(?is)<[^>]+>", " ");
        value = value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return value.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n[ \\t]+", "\n")
                .trim();
    }

    private static String read(InputStream stream, String contentType) throws IOException {
        if (stream == null) return "";
        Charset charset = charsetOf(contentType);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = stream) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
                if (out.size() > MAX_BYTES) {
                    throw new IOException("Web page is too large");
                }
            }
        }
        return new String(out.toByteArray(), charset);
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String value = part.trim();
                if (value.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    try {
                        return Charset.forName(value.substring("charset=".length()).trim());
                    } catch (Exception ignored) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
