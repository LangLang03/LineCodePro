package cn.lineai.tool.workspace;

import cn.lineai.core.model.WebSearchConfig;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Registers the workspace tools.
 *
 * <p>This is the single assembly point of the built-in catalog: file tools, the unified shell
 * family ({@code shell}/{@code shell_output}/{@code shell_kill}), web search and fetch, and the
 * TODO and memory tools. Mode and workspace filtering happens later through
 * {@link LocalToolPolicy} and {@link Tool#needsLocalWorkspace()}.</p>
 */
public final class BuiltinTools {

    /** Utility class; not instantiable. */
    private BuiltinTools() {
    }

    /**
     * Builds the built-in registry with default web search configuration.
     *
     * @return a registry containing every built-in tool
     */
    public static ToolRegistry registry() {
        return registry(WebSearchConfig.defaultConfig());
    }

    /**
     * Builds the built-in registry with the given web search configuration.
     *
     * @param webSearch provider, endpoint and credentials for {@code web_search}
     * @return a registry containing every built-in tool
     */
    public static ToolRegistry registry(WebSearchConfig webSearch) {
        return new ToolRegistry()
                .register(new ReadFileTool())
                .register(new ListDirTool())
                .register(new GlobTool())
                .register(new WriteFileTool())
                .register(new EditFileTool())
                .register(new DeleteFileTool())
                .register(new ShellTool())
                .register(new ShellOutputTool())
                .register(new ShellKillTool())
                .register(new WebSearchTool(webSearch))
                .register(new WebFetchTool())
                .register(new TodoUpdateTool())
                .register(new MemoryUpdateTool());
    }
}
