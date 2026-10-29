package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolPresentation;

/** Resolves presentation metadata without exposing concrete tools to the view layer. */
public interface ToolPresentationProvider {

    ToolPresentation presentation(String toolName);

    /**
     * Resolves presentation metadata with the call's arguments, so tools can show their real
     * target (the listed directory, the shell command, the search query) instead of a placeholder.
     */
    default ToolPresentation presentation(String toolName, String argumentsJson) {
        return presentation(toolName);
    }
}
