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
}
