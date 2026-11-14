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

    /**
     * Creates a theme preference facade.
     *
     * @param settings backing key-value store
     * @param scheduler scheduler used to run persistence on I/O and callbacks on the main thread
     */
    public ThemePreferences(SettingsStore settings, Scheduler scheduler) {
        this.settings = settings;
        this.scheduler = scheduler;
    }

    /**
     * Returns the persisted theme mode, normalized to a supported mode identifier.
     *
     * @return one of the {@link ThemePalette} mode constants
     */
    public String mode() {
        return ThemePalette.normalizeMode(settings.get(MODE_KEY, ThemePalette.MODE_SYSTEM));
    }

    /**
     * Loads the persisted custom palette, keeping only values that parse as hex colors.
     *
     * @return map of editable palette keys to hex color strings; empty when unset
     */
    public Map<String, String> customColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        for (String key : ThemePalette.EDITABLE_KEYS) {
            String value = settings.get(COLOR_PREFIX + key, "");
            if (ThemePalette.isHexColor(value)) colors.put(key, value);
        }
        return colors;
    }

    /**
     * Resolves a concrete palette for the requested mode.
     *
     * <p>Custom mode applies the stored color overrides; system mode delegates to the configured
     * day/night flag; all other modes map to their built-in palette.</p>
     *
     * @param mode requested theme mode identifier
     * @param colors custom palette overrides used only in custom mode
     * @param night day/night flag consulted for system mode
     * @return the palette to render with
     */
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

    /**
     * Resolves the palette for the currently persisted mode and colors.
     *
     * @param night day/night flag consulted for system mode
     * @return the palette to render with at startup
     */
    public ThemePalette initial(boolean night) {
        return resolve(mode(), customColors(), night);
    }

    /**
     * Persists the theme mode asynchronously and reports completion on the main thread.
     *
     * @param mode theme mode to persist; unrecognized values normalize to system
     * @param done callback executed on the main thread after the write completes
     */
    public void saveMode(String mode, Runnable done) {
        scheduler.io(() -> {
            settings.put(MODE_KEY, ThemePalette.normalizeMode(mode));
            scheduler.main(done);
        });
    }

    /** Saving a palette also switches the app to the custom theme, like the legacy editor. */
    /**
     * Persists the custom palette asynchronously and switches the theme mode to custom, matching
     * the legacy editor behavior.
     *
     * @param colors hex color overrides keyed by editable palette key; invalid entries are skipped
     * @param done callback executed on the main thread after the write completes
     */
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
