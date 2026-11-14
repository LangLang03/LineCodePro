package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolPresentation;

/** One call waiting for approval, already resolved to view data by the controller. */
public final class ToolApprovalItem {

    /** The tool call the engine wants to execute. */
    public final ToolCall call;
    /** Label and icon resolved from the tool registry; UI never branches on the tool name. */
    public final ToolPresentation presentation;
    /** Owning sub agent id; empty when the call belongs to the viewing loop itself. */
    public final String agentId;
    /** Display name of the owning sub agent; empty for the viewing loop's own calls. */
    public final String agentName;

    /**
     * @param call          pending tool call
     * @param presentation  resolved label/icon pair for rendering the approval card
     */
    public ToolApprovalItem(ToolCall call, ToolPresentation presentation) {
        this(call, presentation, "", "");
    }

    /**
     * @param call          pending tool call
     * @param presentation  resolved label/icon pair for rendering the approval card
     * @param agentId       owning sub agent id; empty for the viewing loop's own calls
     * @param agentName     display name of the owning sub agent
     */
    public ToolApprovalItem(ToolCall call, ToolPresentation presentation,
                            String agentId, String agentName) {
        this.call = call;
        this.presentation = presentation;
        this.agentId = agentId == null ? "" : agentId;
        this.agentName = agentName == null ? "" : agentName;
    }
}
