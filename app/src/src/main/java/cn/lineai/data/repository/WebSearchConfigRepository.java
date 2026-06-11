package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.WebSearchConfig;

public final class WebSearchConfigRepository {
    public static final String KEY_WEB_SEARCH_CONFIG = "@lineai_web_search_config";

    private final SettingsRepository settingsRepository;

    public WebSearchConfigRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized WebSearchConfig get() {
        return WebSearchConfig.fromJson(settingsRepository.getString(KEY_WEB_SEARCH_CONFIG, ""));
    }

    public synchronized void save(WebSearchConfig config) {
        WebSearchConfig value = config == null ? WebSearchConfig.defaultConfig() : config;
        try {
            settingsRepository.setString(KEY_WEB_SEARCH_CONFIG, value.toJson().toString());
        } catch (Exception ignored) {
            settingsRepository.setString(KEY_WEB_SEARCH_CONFIG, "");
        }
    }
}
