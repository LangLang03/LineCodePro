package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.OutputSettings;

public final class OutputSettingsRepository {
    public static final String KEY_CODE_WRAP = "@lineai_code_wrap";
    public static final String KEY_BROWSER_MODE = "@lineai_browser_mode";
    public static final String KEY_BROWSER_JAVASCRIPT = "@lineai_browser_javascript";

    private final SettingsRepository settingsRepository;

    public OutputSettingsRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized OutputSettings get() {
        return new OutputSettings(
                settingsRepository.getBoolean(KEY_CODE_WRAP, false),
                settingsRepository.getString(KEY_BROWSER_MODE, OutputSettings.BROWSER_BUILTIN),
                settingsRepository.getBoolean(KEY_BROWSER_JAVASCRIPT, false)
        );
    }

    public synchronized void setCodeWrapEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_CODE_WRAP, enabled);
    }

    public synchronized void setBrowserMode(String mode) {
        settingsRepository.setString(KEY_BROWSER_MODE, OutputSettings.normalizeBrowserMode(mode));
    }

    public synchronized void setBrowserJavaScriptEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_BROWSER_JAVASCRIPT, enabled);
    }
}
