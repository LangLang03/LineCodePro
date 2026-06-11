package cn.lineai.model;

import org.json.JSONObject;

public final class WebSearchConfig {
    public static final String PROVIDER_TAVILY = "tavily";
    public static final String PROVIDER_BRAVE = "brave";
    public static final String PROVIDER_SERPAPI = "serpapi";
    public static final String PROVIDER_BING = "bing";
    public static final String PROVIDER_CUSTOM = "custom";

    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String queryParam;
    private final String apiKeyHeader;
    private final String apiKeyParam;

    public WebSearchConfig(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            String queryParam,
            String apiKeyHeader,
            String apiKeyParam
    ) {
        this.provider = normalizeProvider(provider);
        this.baseUrl = safe(baseUrl);
        this.apiKey = safe(apiKey);
        this.model = safe(model);
        this.queryParam = safe(queryParam);
        this.apiKeyHeader = safe(apiKeyHeader);
        this.apiKeyParam = safe(apiKeyParam);
    }

    public static WebSearchConfig defaultConfig() {
        return defaultConfig(PROVIDER_TAVILY);
    }

    public static WebSearchConfig defaultConfig(String provider) {
        String normalized = normalizeProvider(provider);
        if (PROVIDER_BRAVE.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://api.search.brave.com/res/v1/web/search", "", "", "q", "X-Subscription-Token", "");
        }
        if (PROVIDER_SERPAPI.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://serpapi.com/search.json", "", "", "q", "", "api_key");
        }
        if (PROVIDER_BING.equals(normalized)) {
            return new WebSearchConfig(normalized, "https://api.bing.microsoft.com/v7.0/search", "", "", "q", "Ocp-Apim-Subscription-Key", "");
        }
        if (PROVIDER_CUSTOM.equals(normalized)) {
            return new WebSearchConfig(normalized, "", "", "", "q", "Authorization", "");
        }
        return new WebSearchConfig(PROVIDER_TAVILY, "https://api.tavily.com/search", "", "", "query", "Authorization", "");
    }

    public static WebSearchConfig fromJson(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return defaultConfig();
        }
        try {
            JSONObject object = new JSONObject(raw);
            String provider = normalizeProvider(object.optString("provider"));
            WebSearchConfig defaults = defaultConfig(provider);
            return new WebSearchConfig(
                    provider,
                    object.optString("baseUrl", defaults.getBaseUrl()),
                    object.optString("apiKey", ""),
                    object.optString("model", ""),
                    object.optString("queryParam", defaults.getQueryParam()),
                    object.optString("apiKeyHeader", defaults.getApiKeyHeader()),
                    object.optString("apiKeyParam", defaults.getApiKeyParam())
            );
        } catch (Exception ignored) {
            return defaultConfig();
        }
    }

    public JSONObject toJson() throws org.json.JSONException {
        return new JSONObject()
                .put("provider", provider)
                .put("baseUrl", baseUrl)
                .put("apiKey", apiKey)
                .put("model", model)
                .put("queryParam", queryParam)
                .put("apiKeyHeader", apiKeyHeader)
                .put("apiKeyParam", apiKeyParam);
    }

    public WebSearchConfig withProviderDefaults(String nextProvider) {
        WebSearchConfig defaults = defaultConfig(nextProvider);
        return new WebSearchConfig(
                defaults.getProvider(),
                defaults.getBaseUrl(),
                "",
                "",
                defaults.getQueryParam(),
                defaults.getApiKeyHeader(),
                defaults.getApiKeyParam()
        );
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public String getQueryParam() {
        return queryParam.length() == 0 ? "q" : queryParam;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public String getApiKeyParam() {
        return apiKeyParam;
    }

    public static String normalizeProvider(String provider) {
        String value = safe(provider);
        if (PROVIDER_BRAVE.equals(value)
                || PROVIDER_SERPAPI.equals(value)
                || PROVIDER_BING.equals(value)
                || PROVIDER_CUSTOM.equals(value)) {
            return value;
        }
        return PROVIDER_TAVILY;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
