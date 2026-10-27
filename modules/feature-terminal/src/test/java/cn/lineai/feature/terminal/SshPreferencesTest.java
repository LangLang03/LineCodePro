package cn.lineai.feature.terminal;

import cn.lineai.core.model.SshConfig;
import cn.lineai.data.api.SettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SshPreferencesTest {

    @Test public void defaultsMatchTheLoopbackTermuxPort() {
        SshPreferences preferences = new SshPreferences(new Memory());
        SshConfig config = preferences.config();
        assertEquals(SshConfig.DEFAULT_HOST, config.host);
        assertEquals(SshConfig.DEFAULT_PORT, config.port);
        assertFalse(config.isConfigured());
    }

    @Test public void roundTripsEveryCredentialField() {
        Memory settings = new Memory();
        SshPreferences preferences = new SshPreferences(settings);
        SshConfig config = new SshConfig("192.168.1.5", 2222, "user", "pass", "KEY", "phrase");
        preferences.save(config);
        SshConfig loaded = new SshPreferences(settings).config();
        assertEquals("192.168.1.5", loaded.host);
        assertEquals(2222, loaded.port);
        assertEquals("user", loaded.username);
        assertEquals("pass", loaded.password);
        assertEquals("KEY", loaded.privateKey);
        assertEquals("phrase", loaded.passphrase);
        assertTrue(loaded.isConfigured());
    }

    @Test public void configurationRequiresHostUserAndSecret() {
        assertFalse(new SshConfig("host", 22, "", "", "", "").isConfigured());
        assertFalse(new SshConfig("host", 22, "user", "", "", "").isConfigured());
        assertTrue(new SshConfig("host", 22, "user", "pass", "", "").isConfigured());
        assertTrue(new SshConfig("host", 22, "user", "", "key", "").isConfigured());
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
