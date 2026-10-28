package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneControlPreferencesTest {

    @Test public void disclaimerStartsRejectedAndPersists() {
        Memory settings = new Memory();
        PhoneControlPreferences preferences = new PhoneControlPreferences(settings);
        assertFalse(preferences.disclaimerAccepted());
        preferences.acceptDisclaimer();
        assertTrue(new PhoneControlPreferences(settings).disclaimerAccepted());
    }

    @Test public void dangerousActionsDefaultOff() {
        PhoneControlPreferences preferences = new PhoneControlPreferences(new Memory());
        assertTrue(preferences.enabled(PhoneControlPreferences.SCREENSHOT));
        assertTrue(preferences.enabled(PhoneControlPreferences.CLICK));
        assertFalse(preferences.enabled(PhoneControlPreferences.VIEW_ACTION));
        assertFalse(preferences.enabled(PhoneControlPreferences.GLOBAL_ACTION));
    }

    @Test public void switchesPersistIndividually() {
        Memory settings = new Memory();
        PhoneControlPreferences preferences = new PhoneControlPreferences(settings);
        preferences.setEnabled(PhoneControlPreferences.CLICK, false);
        assertFalse(new PhoneControlPreferences(settings).enabled(PhoneControlPreferences.CLICK));
        assertTrue(new PhoneControlPreferences(settings).enabled(PhoneControlPreferences.SWIPE));
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
