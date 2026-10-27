package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/** MCP execution target and per-target tool-group switches. */
public final class McpPreferences {

    public static final String MODE_LOCAL = "local";
    public static final String MODE_SSH = "ssh";
    public static final String MODE_TERMINAL_PROVIDER = "terminal_provider";
    private static final String MODE_KEY = "mcp.executionMode";

    private final SettingsStore settings;

    public McpPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    public String mode() {
        String value = settings.get(MODE_KEY, MODE_LOCAL);
        return MODE_SSH.equals(value) || MODE_TERMINAL_PROVIDER.equals(value) ? value : MODE_LOCAL;
    }

    public void setMode(String mode) {
        settings.put(MODE_KEY, normalize(mode));
    }

    public boolean enabled(String groupId, boolean fallback) {
        return Boolean.parseBoolean(settings.get(key(groupId), String.valueOf(fallback)));
    }

    public void setEnabled(String groupId, boolean value) {
        settings.put(key(groupId), String.valueOf(value));
    }

    private String key(String groupId) {
        return "mcp." + mode() + "." + groupId;
    }

    public static String normalize(String mode) {
        return MODE_SSH.equals(mode) || MODE_TERMINAL_PROVIDER.equals(mode) ? mode : MODE_LOCAL;
    }
}
