package cn.lineai.tool;

/**
 * 工具调用审查回调：用户在工具卡片上接受/还原写入、查看 Shell 命令等。
 */
public interface ToolReviewListener {
    void onToolReview(String toolCallId, String state, String diffId);

    default void onViewShellCommand(String command) {
    }
}
