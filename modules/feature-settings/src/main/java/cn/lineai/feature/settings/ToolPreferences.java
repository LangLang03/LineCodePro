package cn.lineai.feature.settings;

import cn.lineai.core.model.WebSearchConfig;
import cn.lineai.data.api.SettingsStore;

/** Web-search credentials plus the image tool model slots. */
public final class ToolPreferences {

    private static final String SEARCH_PREFIX = "webSearch.";
    private static final String KEY_IMAGE_UNDERSTANDING = "tools.imageUnderstandingModelId";
    private static final String KEY_IMAGE_GENERATION = "tools.imageGenerationModelId";

    private final SettingsStore settings;

    public ToolPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    public WebSearchConfig webSearch() {
        String provider = WebSearchConfig.normalizeProvider(
                settings.get(SEARCH_PREFIX + "provider", WebSearchConfig.PROVIDER_BING_RSS_FREE));
        WebSearchConfig defaults = WebSearchConfig.defaultConfig(provider);
        return new WebSearchConfig(provider,
                settings.get(SEARCH_PREFIX + "baseUrl", defaults.baseUrl),
                settings.get(SEARCH_PREFIX + "apiKey", defaults.apiKey),
                settings.get(SEARCH_PREFIX + "model", defaults.model),
                settings.get(SEARCH_PREFIX + "queryParam", defaults.queryParam),
                settings.get(SEARCH_PREFIX + "apiKeyHeader", defaults.apiKeyHeader),
                settings.get(SEARCH_PREFIX + "apiKeyParam", defaults.apiKeyParam));
    }

    public void saveWebSearch(WebSearchConfig config) {
        settings.put(SEARCH_PREFIX + "provider", config.provider);
        settings.put(SEARCH_PREFIX + "baseUrl", config.baseUrl);
        settings.put(SEARCH_PREFIX + "apiKey", config.apiKey);
        settings.put(SEARCH_PREFIX + "model", config.model);
        settings.put(SEARCH_PREFIX + "queryParam", config.queryParam);
        settings.put(SEARCH_PREFIX + "apiKeyHeader", config.apiKeyHeader);
        settings.put(SEARCH_PREFIX + "apiKeyParam", config.apiKeyParam);
    }

    public String imageUnderstandingModelId() {
        return settings.get(KEY_IMAGE_UNDERSTANDING, "");
    }

    public void setImageUnderstandingModelId(String id) {
        settings.put(KEY_IMAGE_UNDERSTANDING, id == null ? "" : id);
    }

    public String imageGenerationModelId() {
        return settings.get(KEY_IMAGE_GENERATION, "");
    }

    public void setImageGenerationModelId(String id) {
        settings.put(KEY_IMAGE_GENERATION, id == null ? "" : id);
    }
}
