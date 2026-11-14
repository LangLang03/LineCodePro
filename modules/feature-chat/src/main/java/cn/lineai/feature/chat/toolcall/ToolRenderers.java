package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolIcon;

/** Renderer registry keyed by {@link ToolIcon}; no view ever branches on a tool name. */
final class ToolRenderers {

    /** Static utility; not instantiable. */
    private ToolRenderers() {
    }

    /** Renderer for an icon family; unknown and null icons fall back to the generic card. */
    static ToolCardRenderer renderer(ToolIcon icon) {
        if (icon == null) return GenericToolRenderer.INSTANCE;
        switch (icon) {
            case READ:
            case LIST:
            case SEARCH:
            case FETCH:
                return ReadToolRenderer.INSTANCE;
            case WRITE:
            case EDIT:
            case DIFF:
                return WriteToolRenderer.INSTANCE;
            case TERMINAL:
                return ShellToolRenderer.INSTANCE;
            case DELETE:
                return DeleteToolRenderer.INSTANCE;
            case TODO:
                return TodoToolRenderer.INSTANCE;
            case AGENT:
                return AgentToolRenderers.SPAWN;
            case AGENT_SPAWN:
                return AgentToolRenderers.SPAWN;
            case AGENT_SEND:
                return AgentToolRenderers.SEND;
            case AGENT_WAIT:
                return AgentToolRenderers.WAIT;
            case AGENT_LIST:
                return AgentToolRenderers.LIST;
            case MEMORY:
                return MemoryToolRenderer.INSTANCE;
            case IMAGE:
            case SPARKLES:
                return ImageToolRenderer.INSTANCE;
            case GENERIC:
            case MCP:
            case PHONE:
            default:
                return GenericToolRenderer.INSTANCE;
        }
    }
}
