package cn.lineai.tool.workspace;

import cn.lineai.core.model.WebSearchConfig;
import cn.lineai.tool.api.ToolRegistry;

/** Registers the workspace tools. */
public final class BuiltinTools {

    private BuiltinTools() {
    }

    public static ToolRegistry registry() {
        return registry(WebSearchConfig.defaultConfig());
    }

    public static ToolRegistry registry(WebSearchConfig webSearch) {
        return new ToolRegistry()
                .register(new ReadFileTool())
                .register(new ListDirTool())
                .register(new GlobTool())
                .register(new WriteFileTool())
                .register(new EditFileTool())
                .register(new DeleteFileTool())
                .register(new ShellTool())
                .register(new WebSearchTool(webSearch))
                .register(new TodoUpdateTool())
                .register(new MemoryUpdateTool());
    }
}
