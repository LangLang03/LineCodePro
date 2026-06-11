package cn.lineai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeSettingsState {
    private final String themeMode;
    private final String resolvedMode;
    private final Map<String, String> customColors;
    private final ThemePalette palette;

    public ThemeSettingsState(String themeMode, String resolvedMode, Map<String, String> customColors, ThemePalette palette) {
        this.themeMode = ThemePalette.normalizeMode(themeMode);
        this.resolvedMode = ThemePalette.normalizeMode(resolvedMode);
        this.customColors = customColors == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customColors));
        this.palette = palette == null ? ThemePalette.forMode(ThemePalette.MODE_DARK) : palette;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public String getResolvedMode() {
        return resolvedMode;
    }

    public Map<String, String> getCustomColors() {
        return customColors;
    }

    public ThemePalette getPalette() {
        return palette;
    }
}
