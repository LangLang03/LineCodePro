package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/**
 * MCP execution target and per-target tool-group switches.
 *
 * <p>The MCP execution target selects where workspace shell tools run: directly on this device
 * ({@link #MODE_LOCAL}), over an SSH connection ({@link #MODE_SSH}), or through a configured
 * terminal provider ({@link #MODE_TERMINAL_PROVIDER}). The key literals defined here are part of
 * a cross-module contract with the legacy migration layer and must not be renamed.</p>
 */
public final class McpPreferences {

    /** Execution target identifier for commands running locally on the device. */
    public static final String MODE_LOCAL = "local";
    /** Execution target identifier for commands running over an SSH connection. */
    public static final String MODE_SSH = "ssh";
    /** Execution target identifier for commands running through a terminal provider. */
    public static final String MODE_TERMINAL_PROVIDER = "terminal_provider";
    private static final String MODE_KEY = "mcp.executionMode";
    private static final String LOCAL_SHELL_GROUP = "local_shell";
    /** Remote-mode shell group; controls the same shell tool family as {@link #LOCAL_SHELL_GROUP}. */
    public static final String SHELL_GROUP = "shell";

    private final SettingsStore settings;

    /**
     * Creates a preference facade over the given settings store.
     *
     * @param settings backing key-value store; keys are namespaced to avoid collisions
     */
    public McpPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    /**
     * Returns the persisted MCP execution target, falling back to local mode for unknown values.
     *
     * @return one of {@link #MODE_LOCAL}, {@link #MODE_SSH}, or {@link #MODE_TERMINAL_PROVIDER}
     */
    public String mode() {
        String value = settings.get(MODE_KEY, MODE_LOCAL);
        return MODE_SSH.equals(value) || MODE_TERMINAL_PROVIDER.equals(value) ? value : MODE_LOCAL;
    }

    /**
     * Persists the MCP execution target after normalizing it to a supported value.
     *
     * @param mode requested target; unrecognized values are stored as local mode
     */
    public void setMode(String mode) {
        settings.put(MODE_KEY, normalize(mode));
    }

    /** Whether local workspace mode may execute shell commands on this device. */
    public boolean localShell() {
        return enabled(LOCAL_SHELL_GROUP, true);
    }

    /**
     * Enables or disables shell execution in local workspace mode.
     *
     * @param value true to allow local shell tool calls
     */
    public void setLocalShell(boolean value) {
        setEnabled(LOCAL_SHELL_GROUP, value);
    }

    /** Whether remote workspace modes may execute shell commands via SSH or a provider. */
    public boolean remoteShell() {
        return enabled(SHELL_GROUP, true);
    }

    /**
     * Returns whether the given tool group is enabled for the current execution target.
     *
     * @param groupId tool group identifier, matching the groups listed by {@link ToolGroups}
     * @param fallback value returned when the group has never been persisted
     * @return true if the group may run under the active target
     */
    public boolean enabled(String groupId, boolean fallback) {
        return Boolean.parseBoolean(settings.get(key(groupId), String.valueOf(fallback)));
    }

    /**
     * Persists the enablement of a tool group for the current execution target.
     *
     * @param groupId tool group identifier
     * @param value true to allow the group under the active target
     */
    public void setEnabled(String groupId, boolean value) {
        settings.put(key(groupId), String.valueOf(value));
    }

    /**
     * Builds the mode-scoped storage key for a tool group so each target keeps its own switches.
     *
     * @param groupId tool group identifier
     * @return namespaced key of the form {@code mcp.<mode>.<groupId>}
     */
    private String key(String groupId) {
        return "mcp." + mode() + "." + groupId;
    }

    /**
     * Maps an arbitrary mode value onto a supported execution target.
     *
     * @param mode raw mode value, possibly null or unrecognized
     * @return the given mode when supported, otherwise {@link #MODE_LOCAL}
     */
    public static String normalize(String mode) {
        return MODE_SSH.equals(mode) || MODE_TERMINAL_PROVIDER.equals(mode) ? mode : MODE_LOCAL;
    }
}
