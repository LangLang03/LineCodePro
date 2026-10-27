package cn.lineai.feature.chat;

import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolPresentation;

/** One call waiting for approval, already resolved to view data by the controller. */
public final class ToolApprovalItem {

    public final ToolCall call;
    public final ToolPresentation presentation;

    public ToolApprovalItem(ToolCall call, ToolPresentation presentation) {
        this.call = call;
        this.presentation = presentation;
    }
}
