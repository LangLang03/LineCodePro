package cn.lineai.data.repository;

import android.content.Context;
import android.content.res.Configuration;
import cn.lineai.model.ThemePalette;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.ui.theme.LineTheme;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

public final class ThemeSettingsRepository {
    public static final String KEY_THEME_MODE = "@lineai_theme_mode";
    public static final String KEY_CUSTOM_THEME_COLORS = "@lineai_custom_theme_colors";

    private final Context context;
    private final SettingsRepository settingsRepository;

    public ThemeSettingsRepository(Context context) {
        this.context = context.getApplicationContext();
        settingsRepository = new SettingsRepository(this.context);
    }

    public synchronized ThemeSettingsState getState() {
        String mode = getThemeMode();
        String resolvedMode = resolveMode(mode);
        Map<String, String> customColors = getCustomThemeColors();
        ThemePalette palette = paletteFor(resolvedMode, customColors);
        return new ThemeSettingsState(mode, resolvedMode, customColors, palette);
    }

    public synchronized String getThemeMode() {
        return ThemePalette.normalizeMode(settingsRepository.getString(KEY_THEME_MODE, ThemePalette.MODE_SYSTEM));
    }

    public synchronized void setThemeMode(String mode) {
        settingsRepository.setString(KEY_THEME_MODE, ThemePalette.normalizeMode(mode));
    }

    public synchronized Map<String, String> getCustomThemeColors() {
        String raw = settingsRepository.getString(KEY_CUSTOM_THEME_COLORS, "");
        LinkedHashMap<String, String> colors = new LinkedHashMap<>();
        if (raw.length() == 0) {
            return colors;
        }
        try {
            JSONObject object = new JSONObject(raw);
            for (String key : ThemePalette.EDITABLE_KEYS) {
                String value = object.optString(key, "");
                if (ThemePalette.isHexColor(value)) {
                    colors.put(key, value);
                }
            }
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return colors;
    }

    public synchronized void setCustomThemeColors(Map<String, String> colors) {
        JSONObject object = new JSONObject();
        if (colors != null) {
            for (String key : ThemePalette.EDITABLE_KEYS) {
                String value = colors.get(key);
                if (ThemePalette.isHexColor(value)) {
                    try {
                        object.put(key, value);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        settingsRepository.setString(KEY_CUSTOM_THEME_COLORS, object.toString());
    }

    public synchronized ThemePalette applyCurrentTheme() {
        ThemePalette palette = getState().getPalette();
        LineTheme.apply(palette);
        return palette;
    }

    public synchronized ThemePalette applyThemeMode(String mode) {
        setThemeMode(mode);
        return applyCurrentTheme();
    }

    public synchronized ThemePalette saveCustomThemeColors(Map<String, String> colors) {
        setCustomThemeColors(colors);
        setThemeMode(ThemePalette.MODE_CUSTOM);
        return applyCurrentTheme();
    }

    private ThemePalette paletteFor(String resolvedMode, Map<String, String> customColors) {
        ThemePalette base = ThemePalette.forMode(resolvedMode);
        if (ThemePalette.MODE_CUSTOM.equals(resolvedMode)) {
            return base.withCustomColors(customColors);
        }
        return base;
    }

    private String resolveMode(String mode) {
        String normalized = ThemePalette.normalizeMode(mode);
        if (!ThemePalette.MODE_SYSTEM.equals(normalized)) {
            return normalized;
        }
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? ThemePalette.MODE_DARK : ThemePalette.MODE_LIGHT;
    }
}
