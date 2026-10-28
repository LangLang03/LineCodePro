package cn.lineai.feature.settings;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.data.api.SettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemePreferencesTest {

    @Test public void defaultModeIsSystemAndResolvesNightToDark() {
        ThemePreferences preferences = new ThemePreferences(new Memory(), Scheduler.immediate());
        assertEquals(ThemePalette.MODE_SYSTEM, preferences.mode());
        assertEquals(ThemePalette.dark().bg, preferences.initial(true).bg);
        assertEquals(ThemePalette.light().bg, preferences.initial(false).bg);
    }

    @Test public void savingAPaletteSwitchesToCustomAndPersistsEveryEditableColor() {
        Memory settings = new Memory();
        ThemePreferences preferences = new ThemePreferences(settings, Scheduler.immediate());
        Map<String, String> palette = ThemePalette.customDefault().editableHexMap();
        palette.put(ThemePalette.KEY_BG, "#123456");
        preferences.saveColors(palette, () -> {});
        assertEquals(ThemePalette.MODE_CUSTOM, preferences.mode());
        assertEquals("#123456", preferences.customColors().get(ThemePalette.KEY_BG));
        ThemePalette resolved = preferences.initial(true);
        assertEquals("#123456", ThemePalette.toHex(resolved.bg));
        assertEquals(ThemePalette.EDITABLE_KEYS.length, preferences.customColors().size());
    }

    @Test public void invalidValuesAreIgnoredOnWriteAndRead() {
        Memory settings = new Memory();
        ThemePreferences preferences = new ThemePreferences(settings, Scheduler.immediate());
        Map<String, String> colors = new HashMap<>();
        colors.put(ThemePalette.KEY_BG, "not-a-color");
        preferences.saveColors(colors, () -> {});
        assertFalse(preferences.customColors().containsKey(ThemePalette.KEY_BG));
    }

    @Test public void modeChangesPersistAndNormaliseUnknownValues() {
        ThemePreferences preferences = new ThemePreferences(new Memory(), Scheduler.immediate());
        preferences.saveMode("bogus", () -> {});
        assertEquals(ThemePalette.MODE_SYSTEM, preferences.mode());
        preferences.saveMode(ThemePalette.MODE_COFFEE, () -> {});
        assertEquals(ThemePalette.MODE_COFFEE, preferences.mode());
        assertEquals(ThemePalette.coffee().bg, preferences.initial(true).bg);
    }

    static final class Memory implements SettingsStore {
        final Map<String, String> values = new HashMap<>();
        public String get(String key, String fallback) { return values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) {
            try { return Integer.parseInt(get(key, String.valueOf(fallback))); }
            catch (NumberFormatException error) { return fallback; }
        }
        public void put(String key, String value) { values.put(key, value); }
        public void remove(String key) { values.remove(key); }
        public void invalidate() { }
        public Map<String, String> all() { return new HashMap<>(values); }
    }
}
