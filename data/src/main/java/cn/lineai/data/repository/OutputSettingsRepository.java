package cn.lineai.data.repository;

import cn.lineai.model.OutputSettings;

public final class OutputSettingsRepository {
    public static final String KEY_CODE_WRAP = "@lineai_code_wrap";
    public static final String KEY_BROWSER_MODE = "@lineai_browser_mode";
    public static final String KEY_BROWSER_JAVASCRIPT = "@lineai_browser_javascript";
    public static final String KEY_ALLOW_ANY_HTTP = "@lineai_allow_any_http";
    public static final String KEY_BYPASS_PATH_PROTECTION = "@lineai_bypass_path_protection";
    public static final String KEY_PROCESS_AUTO_EXPAND = "@lineai_process_auto_expand";

    private final SettingsRepository settingsRepository;

    public OutputSettingsRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public synchronized OutputSettings get() {
        return new OutputSettings(
                settingsRepository.getBoolean(KEY_CODE_WRAP, false),
                settingsRepository.getString(KEY_BROWSER_MODE, OutputSettings.BROWSER_BUILTIN),
                settingsRepository.getBoolean(KEY_BROWSER_JAVASCRIPT, false),
                settingsRepository.getBoolean(KEY_ALLOW_ANY_HTTP, false),
                settingsRepository.getBoolean(KEY_BYPASS_PATH_PROTECTION, false),
                settingsRepository.getBoolean(KEY_PROCESS_AUTO_EXPAND, false)
        );
    }

    public synchronized void setCodeWrapEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_CODE_WRAP, enabled);
    }

    public synchronized void setProcessAutoExpandEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_PROCESS_AUTO_EXPAND, enabled);
    }

    public synchronized void setBrowserMode(String mode) {
        settingsRepository.setString(KEY_BROWSER_MODE, OutputSettings.normalizeBrowserMode(mode));
    }

    public synchronized void setBrowserJavaScriptEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_BROWSER_JAVASCRIPT, enabled);
    }

    public synchronized void setAllowAnyHttp(boolean enabled) {
        settingsRepository.setBoolean(KEY_ALLOW_ANY_HTTP, enabled);
    }

    public synchronized boolean isPathProtectionBypassed() {
        return settingsRepository.getBoolean(KEY_BYPASS_PATH_PROTECTION, false);
    }

    public synchronized void setPathProtectionBypassed(boolean bypassed) {
        settingsRepository.setBoolean(KEY_BYPASS_PATH_PROTECTION, bypassed);
    }
}
