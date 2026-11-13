package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolPresentation;

/** Resolves presentation metadata without exposing concrete tools to the view layer. */
public interface ToolPresentationProvider {

    /**
     * Resolves label and icon for a tool name.
     *
     * @param toolName registry name of the tool
     * @return presentation metadata; never derived by branching in the view layer
     */
    ToolPresentation presentation(String toolName);

    /**
     * Resolves presentation metadata with the call's arguments, so tools can show their real
     * target (the listed directory, the shell command, the search query) instead of a placeholder.
     */
    default ToolPresentation presentation(String toolName, String argumentsJson) {
        return presentation(toolName);
    }
}
