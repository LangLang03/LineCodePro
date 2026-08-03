package cn.lineai.tool;
import cn.lineai.model.tool.ToolCall;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 工具元数据接口，供 AI 协议层引用工具信息而不依赖 BaseTool 具体实现。
 * BaseTool 实现此接口；AI 模块只依赖 ToolInfo。
 */
public interface ToolInfo {
    String getName();
    String getDescription();
    ToolCategory getCategory();
    boolean needsConfirmation();
    String promptSupplement(String executionMode, boolean isSsh);
    JSONObject getParameters() throws JSONException;
    JSONObject toJson() throws JSONException;

    /**
     * 声明本工具使用的 ToolCall 卡片视图实现类；返回 null 时按
     * {@link ToolDisplayCategory} 回退到默认视图。
     */
    default Class<? extends ToolCallCardView> getToolCallViewClass() {
        return null;
    }

    /** 将 ToolInfo 集合序列化为 OpenAI tools 格式的 JSONArray */
    static org.json.JSONArray toJsonArray(java.util.Collection<? extends ToolInfo> tools) throws JSONException {
        org.json.JSONArray array = new org.json.JSONArray();
        if (tools == null) {
            return array;
        }
        for (ToolInfo tool : tools) {
            array.put(tool.toJson());
        }
        return array;
    }
}
