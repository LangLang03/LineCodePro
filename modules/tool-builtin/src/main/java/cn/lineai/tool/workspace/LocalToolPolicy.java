package cn.lineai.tool.workspace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.workspace.ShellKillTool;
import cn.lineai.tool.workspace.ShellOutputTool;
import cn.lineai.tool.workspace.ShellTool;

/**
 * Applies the user's shell preference to a tool registry.
 *
 * <p>The unified shell tool family ({@code shell}, {@code shell_output}, {@code shell_kill}) is
 * controlled by two independent switches: {@code local_shell} in local workspace mode and
 * {@code shell} in SSH/terminal provider mode.</p>
 */
public final class LocalToolPolicy {

    /** Utility class; not instantiable. */
    private LocalToolPolicy() {
    }

    /**
     * Removes the whole shell family from the registry when shell access is disabled.
     *
     * <p>The three tools are removed together so the model can never start a task it then
     * cannot observe or stop.</p>
     *
     * @param source registry to filter; returned unchanged when {@code null}
     * @param shellEnabled {@code true} to keep the shell family untouched
     * @return the original registry when enabled, otherwise a filtered copy without shell tools
     */
    public static ToolRegistry apply(ToolRegistry source, boolean shellEnabled) {
        if (source == null || shellEnabled) {
            return source;
        }
        ToolRegistry filtered = new ToolRegistry();
        for (Tool tool : source.all()) {
            if (!SHELL_TOOLS.contains(tool.name())) {
                filtered.register(tool);
            }
        }
        return filtered;
    }

    /** Names of the shell family removed as a unit by {@link #apply}. */
    static final Set<String> SHELL_TOOLS = new HashSet<>(Arrays.asList(
            ShellTool.NAME, ShellOutputTool.NAME, ShellKillTool.NAME));
}
