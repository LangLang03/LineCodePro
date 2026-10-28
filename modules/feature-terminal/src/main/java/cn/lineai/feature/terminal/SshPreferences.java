package cn.lineai.feature.terminal;

import cn.lineai.core.model.SshConfig;
import cn.lineai.data.api.SettingsStore;

/** Persists SSH credentials in the settings table. */
public final class SshPreferences {

    private static final String PREFIX = "ssh.";

    private final SettingsStore settings;

    public SshPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    public SshConfig config() {
        SshConfig defaults = SshConfig.defaultConfig();
        return new SshConfig(
                settings.get(PREFIX + "host", defaults.host),
                settings.getInt(PREFIX + "port", defaults.port),
                settings.get(PREFIX + "username", ""),
                settings.get(PREFIX + "password", ""),
                settings.get(PREFIX + "privateKey", ""),
                settings.get(PREFIX + "passphrase", ""));
    }

    public void save(SshConfig config) {
        settings.put(PREFIX + "host", config.host);
        settings.put(PREFIX + "port", String.valueOf(config.port));
        settings.put(PREFIX + "username", config.username);
        settings.put(PREFIX + "password", config.password);
        settings.put(PREFIX + "privateKey", config.privateKey);
        settings.put(PREFIX + "passphrase", config.passphrase);
    }
}
