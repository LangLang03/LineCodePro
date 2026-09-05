package cn.lineai.model;

import cn.lineai.model.tool.ToolCall;

/** An active execution request, supplied by the execution controller (never inferred from history). */
public final class ToolApproval {
    private final String reviewId;
    private final ToolCall call;
    private final boolean permanentAllowed;
    public ToolApproval(String reviewId, ToolCall call, boolean permanentAllowed) {
        this.reviewId = reviewId; this.call = call; this.permanentAllowed = permanentAllowed;
    }
    public String getReviewId() { return reviewId; }
    public ToolCall getCall() { return call; }
    public boolean canAllowPermanently() { return permanentAllowed; }
}
