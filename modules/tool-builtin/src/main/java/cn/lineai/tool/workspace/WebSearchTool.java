package cn.lineai.tool.workspace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.WebSearchConfig;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Searches the web through the provider configured in Tool settings.
 *
 * <p>Provider response shapes (JSON payloads, Bing RSS, plain GET endpoints) are normalized
 * into one numbered title/URL/snippet listing so the model sees a uniform result format
 * regardless of the configured backend.</p>
 */
public final class WebSearchTool implements Tool {

    /** Provider, endpoint and credential configuration; never {@code null} after construction. */
    private final WebSearchConfig config;

    /**
     * Creates the tool with the given provider configuration.
     *
     * @param config provider configuration; {@code null} selects the default configuration
     */
    public WebSearchTool(WebSearchConfig config) {
        this.config = config == null ? WebSearchConfig.defaultConfig() : config;
    }

    /** {@inheritDoc} */
    @Override public String name() { return "web_search"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Search the web and return the top result titles, URLs and snippets.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("query", "Search query")
                .required("query").build();
    }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return false; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Web search", ToolIcon.SEARCH,
                arguments.optString("query", ""), ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fails without network activity when the query is empty or the provider needs an API
     * key that is not configured; transport errors surface as failure results.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        String query = arguments.optString("query", "").trim();
        if (query.isEmpty()) return ToolResult.failure("", "query must not be empty");
        if (config.requiresApiKey() && config.apiKey.isEmpty()) {
            return ToolResult.failure("", "Web search API key is not configured");
        }
        try {
            String body = WebSearchConfig.PROVIDER_TAVILY.equals(config.provider)
                    ? searchTavily(query) : searchGet(query);
            return ToolResult.success("", body);
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    /**
     * Runs a Tavily API POST search and formats the {@code results} array.
     *
     * @param query search query
     * @return normalized result listing
     * @throws Exception on transport or HTTP failure, or when parsing the response fails
     */
    private String searchTavily(String query) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("api_key", config.apiKey);
        payload.put("query", query);
        payload.put("max_results", 8);
        payload.put("include_answer", false);
        String response = request("POST", config.baseUrl, payload.toString());
        return formatJsonResults(response, new String[] {"results"}, "title", "url", "content");
    }

    /**
     * Runs a GET-based provider search, encoding the query and key as URL parameters and
     * dispatching to the response shape of the configured provider (JSON variants or RSS).
     *
     * @param query search query
     * @return normalized result listing
     * @throws Exception on transport or HTTP failure, or when parsing the response fails
     */
    private String searchGet(String query) throws Exception {
        String endpoint = config.baseUrl;
        String param = config.queryParam.isEmpty() ? "q" : config.queryParam;
        String separator = endpoint.contains("?") ? "&" : "?";
        String url = endpoint + separator + param + "="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        if (!config.apiKeyParam.isEmpty() && !config.apiKey.isEmpty()) {
            url += "&" + config.apiKeyParam + "="
                    + URLEncoder.encode(config.apiKey, StandardCharsets.UTF_8.name());
        }
        String response = request("GET", url, null);
        if (WebSearchConfig.PROVIDER_BING_RSS_FREE.equals(config.provider)) {
            return formatRss(response);
        }
        if (WebSearchConfig.PROVIDER_BRAVE.equals(config.provider)) {
            JSONObject root = new JSONObject(response);
            JSONObject web = root.optJSONObject("web");
            if (web != null) {
                return formatResults(web.optJSONArray("results"), "title", "url", "description");
            }
        }
        if (WebSearchConfig.PROVIDER_SERPAPI.equals(config.provider)) {
            return formatJsonResults(response, new String[] {"organic_results"},
                    "title", "link", "snippet");
        }
        if (WebSearchConfig.PROVIDER_BING.equals(config.provider)) {
            JSONObject root = new JSONObject(response);
            JSONObject pages = root.optJSONObject("webPages");
            if (pages != null) {
                return formatResults(pages.optJSONArray("value"), "name", "url", "snippet");
            }
        }
        return formatJsonResults(response,
                new String[] {"results", "organic_results", "items", "data"},
                "title", "url", "snippet");
    }

    /**
     * Performs one HTTP request with the configured key header, fixed timeouts and optional
     * JSON body; non-2xx statuses fail with the response body for diagnosis.
     *
     * @param method HTTP method to use
     * @param endpoint absolute URL to request
     * @param body JSON request body, or {@code null} for a body-less request
     * @return response body text
     * @throws Exception on transport failure or a non-2xx status code
     */
    private String request(String method, String endpoint, String body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept", "application/json, application/rss+xml, text/xml");
            if (!config.apiKeyHeader.isEmpty() && !config.apiKey.isEmpty()) {
                connection.setRequestProperty(config.apiKeyHeader, config.apiKey);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Search HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Reads the whole response stream as UTF-8 text.
     *
     * @param stream response stream; may be {@code null}, yielding the empty string
     * @return full body text
     * @throws Exception when reading the stream fails
     */
    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) out.append(buffer, 0, count);
        }
        return out.toString();
    }

    /**
     * Locates the results array in a provider JSON body, probing the candidate keys at the
     * root and then one nesting level down, and formats the found entries.
     *
     * @param body raw JSON response
     * @param arrays candidate root keys holding the results array, tried in order
     * @param titleKey key of the result title field
     * @param urlKey key of the result URL field
     * @param snippetKey key of the result snippet field
     * @return numbered result listing, or the "No results." marker
     * @throws Exception when {@code body} is not valid JSON
     */
    static String formatJsonResults(String body, String[] arrays,
                                    String titleKey, String urlKey, String snippetKey) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray results = null;
        for (String key : arrays) {
            results = root.optJSONArray(key);
            if (results != null) break;
        }
        if (results == null) {
            for (String key : arrays) {
                JSONObject nested = root.optJSONObject(key);
                if (nested != null) {
                    results = nested.optJSONArray("results");
                    if (results == null) results = nested.optJSONArray("value");
                    if (results != null) break;
                }
            }
        }
        return formatResults(results, titleKey, urlKey, snippetKey);
    }

    /**
     * Formats up to eight result objects into the numbered listing shared by all providers.
     *
     * @param results parsed result entries; may be {@code null}
     * @param titleKey key of the result title field
     * @param urlKey key of the result URL field
     * @param snippetKey key of the result snippet field
     * @return numbered listing, or the "No results." marker
     */
    private static String formatResults(JSONArray results, String titleKey,
                                        String urlKey, String snippetKey) {
        if (results == null || results.length() == 0) return "No results.";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = 0; i < results.length() && count < 8; i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString(titleKey, "").trim();
            String url = item.optString(urlKey, "").trim();
            if (title.isEmpty() && url.isEmpty()) continue;
            count++;
            if (out.length() > 0) out.append("\n\n");
            out.append(count).append(". ").append(title.isEmpty() ? url : title);
            if (!url.isEmpty()) out.append('\n').append(url);
            String snippet = item.optString(snippetKey, "").trim();
            if (!snippet.isEmpty()) out.append('\n').append(snippet);
        }
        return out.length() == 0 ? "No results." : out.toString();
    }

    /**
     * Extracts RSS {@code item} elements from the feed XML and formats them like JSON results.
     *
     * @param xml raw RSS/Atom response
     * @return numbered listing of at most eight items, or the "No results." marker
     */
    static String formatRss(String xml) {
        List<String> items = between(xml, "<item", "</item>");
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String item : items) {
            String title = xmlValue(item, "title");
            String link = xmlValue(item, "link");
            String description = xmlValue(item, "description");
            if (title.isEmpty() && link.isEmpty()) continue;
            count++;
            if (out.length() > 0) out.append("\n\n");
            out.append(count).append(". ").append(title.isEmpty() ? link : title);
            if (!link.isEmpty()) out.append('\n').append(link);
            if (!description.isEmpty()) out.append('\n').append(stripHtml(description));
            if (count >= 8) break;
        }
        return out.length() == 0 ? "No results." : out.toString();
    }

    /**
     * Collects every non-overlapping substring delimited by the given markers.
     *
     * @param text text to scan
     * @param open opening marker
     * @param close closing marker
     * @return substrings including the markers, in order of occurrence
     */
    private static List<String> between(String text, String open, String close) {
        List<String> result = new ArrayList<>();
        int index = 0;
        boolean scanning = true;
        while (scanning) {
            int start = text.indexOf(open, index);
            int end = start < 0 ? -1 : text.indexOf(close, start);
            if (start < 0 || end < 0) {
                scanning = false;
            } else {
                result.add(text.substring(start, end + close.length()));
                index = end + close.length();
            }
        }
        return result;
    }

    /**
     * Reads one XML element's text (with or without attributes), strips CDATA wrappers and
     * decodes the common HTML entities.
     *
     * @param item element substring to scan
     * @param tag tag name to locate
     * @return decoded element text, or the empty string when absent
     */
    private static String xmlValue(String item, String tag) {
        String value = betweenText(item, "<" + tag + ">", "</" + tag + ">");
        if (value.isEmpty()) {
            value = betweenText(item, "<" + tag + " ", "</" + tag + ">");
        }
        value = value.replace("<![CDATA[", "").replace("]]>", "");
        return decodeEntities(value).trim();
    }

    /**
     * Returns the text between the first opening marker and the following closing marker;
     * an open marker ending in a space skips to the end of the tag itself.
     *
     * @param text text to scan
     * @param open opening marker; a trailing space means "skip through {@code >}"
     * @param close closing marker
     * @return enclosed text, or the empty string when either marker is missing
     */
    private static String betweenText(String text, String open, String close) {
        int start = text.indexOf(open);
        if (start < 0) return "";
        if (open.endsWith(" ")) {
            int tagEnd = text.indexOf('>', start);
            if (tagEnd < 0) return "";
            start = tagEnd + 1;
        } else {
            start += open.length();
        }
        int end = text.indexOf(close, start);
        return end < 0 ? "" : text.substring(start, end);
    }

    /** Drops tags and collapses whitespace to single spaces. */
    private static String stripHtml(String value) {
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    /** Decodes the five common HTML entities found in feed text. */
    private static String decodeEntities(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }
}
