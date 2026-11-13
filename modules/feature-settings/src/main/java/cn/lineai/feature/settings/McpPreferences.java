package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/** MCP execution target and per-target tool-group switches. */
public final class McpPreferences {

    public static final String MODE_LOCAL = "local";
    public static final String MODE_SSH = "ssh";
    public static final String MODE_TERMINAL_PROVIDER = "terminal_provider";
    private static final String MODE_KEY = "mcp.executionMode";
    private static final String LOCAL_SHELL_GROUP = "local_shell";
    /** Remote-mode shell group; controls the same shell tool family as {@link #LOCAL_SHELL_GROUP}. */
    public static final String SHELL_GROUP = "shell";

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

    /** Whether local workspace mode may execute shell commands on this device. */
    public boolean localShell() {
        return enabled(LOCAL_SHELL_GROUP, true);
    }

    public void setLocalShell(boolean value) {
        setEnabled(LOCAL_SHELL_GROUP, value);
    }

    /** Whether remote workspace modes may execute shell commands via SSH or a provider. */
    public boolean remoteShell() {
        return enabled(SHELL_GROUP, true);
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
