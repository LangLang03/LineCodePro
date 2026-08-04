package cn.lineai.tool.ui;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolInfo;
import org.json.JSONObject;

/**
 * 工具显示信息解析契约：由工具注册方（feature-tool 的 ToolDisplayResolver）实现，
 * :tool-ui 视图通过 {@link ToolInfoResolverProvider} 获取，避免视图层依赖工具模块。
 */
public interface ToolInfoResolver {
    ToolDisplayCategory getDisplayCategory(String name);

    String getDisplayLabel(Context context, String name, JSONObject input, String workspacePath);

    String getActionName(Context context, String name);

    int getActionIcon(String name);

    ToolInfo getToolInfo(String name);
}
