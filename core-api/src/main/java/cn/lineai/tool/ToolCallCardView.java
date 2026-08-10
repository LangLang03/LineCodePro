package cn.lineai.tool;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;


/**
 * 工具调用卡片视图契约。
 * <p>由各 ToolCall 显示视图实现（:tool-ui 模块），工具通过
 * {@link ToolInfo#getToolCallViewClass()} 声明自己使用的实现类。</p>
 */
public interface ToolCallCardView {
    void bind(ToolCall call, ToolResult result);

    void setToolReviewListener(ToolReviewListener listener);

    void setProjectPath(String projectPath);

    /**
     * 内容增量更新钩子：仅当工具结果内容变化而结构（卡片类型/状态/参数）未变时被调用，
     * 由各实现类覆写以只更新内容文本，避免流式输出时整棵视图树重建。
     * 默认回退到完整绑定，保持既有行为。
     */
    default void updateContent(ToolCall call, ToolResult result) {
        bind(call, result);
    }
}
