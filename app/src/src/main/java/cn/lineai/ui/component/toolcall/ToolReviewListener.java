package cn.lineai.ui.component.toolcall;

public interface ToolReviewListener {
    void onToolReview(String toolCallId, String state, String diffId);

    default void onViewShellCommand(String command) {
    }
}
