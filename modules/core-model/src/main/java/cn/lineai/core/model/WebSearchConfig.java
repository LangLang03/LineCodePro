package cn.lineai.core.model;

/** Web-search provider configuration. */
public final class WebSearchConfig {

    public static final String PROVIDER_BING_RSS_FREE = "bing_rss_free";
    public static final String PROVIDER_TAVILY = "tavily";
    public static final String PROVIDER_BRAVE = "brave";
    public static final String PROVIDER_SERPAPI = "serpapi";
    public static final String PROVIDER_BING = "bing";
    public static final String PROVIDER_CUSTOM = "custom";

    public final String provider;
    public final String baseUrl;
    public final String apiKey;
    public final String model;
    public final String queryParam;
    public final String apiKeyHeader;
    public final String apiKeyParam;

    public WebSearchConfig(String provider, String baseUrl, String apiKey, String model,
                           String queryParam, String apiKeyHeader, String apiKeyParam) {
        this.provider = normalizeProvider(provider);
        this.baseUrl = safe(baseUrl);
        this.apiKey = safe(apiKey);
        this.model = safe(model);
        this.queryParam = safe(queryParam);
        this.apiKeyHeader = safe(apiKeyHeader);
        this.apiKeyParam = safe(apiKeyParam);
    }

    public static WebSearchConfig defaultConfig() {
        return defaultConfig(PROVIDER_BING_RSS_FREE);
    }

    public static WebSearchConfig defaultConfig(String provider) {
        String normalized = normalizeProvider(provider);
        if (PROVIDER_BING_RSS_FREE.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://www.bing.com/search?format=rss",
                    "", "", "q", "", "");
        }
        if (PROVIDER_BRAVE.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://api.search.brave.com/res/v1/web/search",
                    "", "", "q", "X-Subscription-Token", "");
        }
        if (PROVIDER_SERPAPI.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://serpapi.com/search.json",
                    "", "", "q", "", "api_key");
        }
        if (PROVIDER_BING.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://api.bing.microsoft.com/v7.0/search",
                    "", "", "q", "Ocp-Apim-Subscription-Key", "");
        }
        if (PROVIDER_CUSTOM.equals(normalized)) {
            return new WebSearchConfig(normalized, "", "", "", "q", "Authorization", "");
        }
        return new WebSearchConfig(PROVIDER_TAVILY, "https://api.tavily.com/search",
                "", "", "query", "Authorization", "");
    }

    public static boolean requiresApiKey(String provider) {
        return !PROVIDER_BING_RSS_FREE.equals(normalizeProvider(provider));
    }

    public boolean requiresApiKey() {
        return requiresApiKey(provider);
    }

    public static String normalizeProvider(String provider) {
        if (PROVIDER_TAVILY.equals(provider) || PROVIDER_BRAVE.equals(provider)
                || PROVIDER_SERPAPI.equals(provider) || PROVIDER_BING.equals(provider)
                || PROVIDER_CUSTOM.equals(provider)) {
            return provider;
        }
        return PROVIDER_BING_RSS_FREE;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
