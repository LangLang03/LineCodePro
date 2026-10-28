package cn.lineai.feature.settings;

import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.data.api.SettingsStore;

/** Reads and writes the theme mode plus the custom palette in the settings store. */
public final class ThemePreferences {

    public static final String MODE_KEY = "ui.themeMode";
    private static final String COLOR_PREFIX = "ui.custom.";

    private final SettingsStore settings;
    private final Scheduler scheduler;

    public ThemePreferences(SettingsStore settings, Scheduler scheduler) {
        this.settings = settings;
        this.scheduler = scheduler;
    }

    public String mode() {
        return ThemePalette.normalizeMode(settings.get(MODE_KEY, ThemePalette.MODE_SYSTEM));
    }

    public Map<String, String> customColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        for (String key : ThemePalette.EDITABLE_KEYS) {
            String value = settings.get(COLOR_PREFIX + key, "");
            if (ThemePalette.isHexColor(value)) colors.put(key, value);
        }
        return colors;
    }

    public ThemePalette resolve(String mode, Map<String, String> colors, boolean night) {
        String normalized = ThemePalette.normalizeMode(mode);
        if (ThemePalette.MODE_CUSTOM.equals(normalized)) {
            return ThemePalette.customDefault().withCustomColors(colors);
        }
        if (ThemePalette.MODE_SYSTEM.equals(normalized)) {
            return night ? ThemePalette.dark() : ThemePalette.light();
        }
        return ThemePalette.forMode(normalized);
    }

    public ThemePalette initial(boolean night) {
        return resolve(mode(), customColors(), night);
    }

    public void saveMode(String mode, Runnable done) {
        scheduler.io(() -> {
            settings.put(MODE_KEY, ThemePalette.normalizeMode(mode));
            scheduler.main(done);
        });
    }

    /** Saving a palette also switches the app to the custom theme, like the legacy editor. */
    public void saveColors(Map<String, String> colors, Runnable done) {
        scheduler.io(() -> {
            for (String key : ThemePalette.EDITABLE_KEYS) {
                String value = colors.get(key);
                if (ThemePalette.isHexColor(value)) settings.put(COLOR_PREFIX + key, value);
            }
            settings.put(MODE_KEY, ThemePalette.MODE_CUSTOM);
            scheduler.main(done);
        });
    }
}
