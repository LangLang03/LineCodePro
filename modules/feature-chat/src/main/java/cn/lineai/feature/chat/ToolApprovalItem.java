package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolPresentation;

/** One call waiting for approval, already resolved to view data by the controller. */
public final class ToolApprovalItem {

    /** The tool call the engine wants to execute. */
    public final ToolCall call;
    /** Label and icon resolved from the tool registry; UI never branches on the tool name. */
    public final ToolPresentation presentation;

    /** @param call          pending tool call
     * @param presentation  resolved label/icon pair for rendering the approval card */
    public ToolApprovalItem(ToolCall call, ToolPresentation presentation) {
        this.call = call;
        this.presentation = presentation;
    }
}
