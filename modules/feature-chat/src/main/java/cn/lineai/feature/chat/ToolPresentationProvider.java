package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolPresentation;

/** Resolves presentation metadata without exposing concrete tools to the view layer. */
interface ToolPresentationProvider {

    ToolPresentation presentation(String toolName);
}
