package cn.lineai.tool;

import android.content.Context;
import org.json.JSONObject;

/**
 * 工具显示信息解析器：根据工具名解析显示分类、标签、动作名、图标。
 * 供UI层使用，将显示路由逻辑从UI层下沉到tool层。
 * 新增工具只需在 BaseTool 中覆写 getDisplayCategory/getActionName/getActionIcon，
 * 无需修改此类或UI层代码。
 */
public final class ToolDisplayResolver implements cn.lineai.tool.ui.ToolInfoResolver {
    private final ToolRegistry registry;

    private static ToolDisplayResolver defaultInstance;

    public ToolDisplayResolver(ToolRegistry registry) {
        this.registry = registry;
    }

    public static void setDefault(ToolDisplayResolver resolver) {
        defaultInstance = resolver;
    }

    public static ToolDisplayResolver getDefault() {
        return defaultInstance;
    }

    public ToolDisplayCategory getDisplayCategory(String name) {
        if (registry != null) {
            return registry.getCachedDisplayCategory(name);
        }
        return fallbackDisplayCategory(name);
    }

    public String getDisplayLabel(Context context, String name, JSONObject input, String workspacePath) {
        if (registry != null) {
            BaseTool tool = registry.get(name);
            if (tool != null) {
                String label = tool.getDisplayLabel(context, input, workspacePath);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    public String getActionName(Context context, String name) {
        if (registry != null) {
            BaseTool tool = registry.get(name);
            if (tool != null) {
                String actionName = tool.getActionName(context);
                if (actionName != null) {
                    return actionName;
                }
            }
        }
        return null;
    }

    public int getActionIcon(String name) {
        if (registry != null) {
            BaseTool tool = registry.get(name);
            if (tool != null) {
                int icon = tool.getActionIcon();
                if (icon != 0) {
                    return icon;
                }
            }
        }
        return 0;
    }

    @Override
    public cn.lineai.tool.ToolInfo getToolInfo(String name) {
        return registry == null ? null : registry.get(name);
    }

    /**
     * 静态回退方法：仅当registry不可用时使用。
     * 所有内置工具已通过 BaseTool.getDisplayCategory() 提供自己的类别，
     * 此处仅保留动态前缀工具的回退逻辑。
     */
    public static ToolDisplayCategory fallbackDisplayCategory(String name) {
        return ToolDisplayCategory.fallbackDisplayCategory(name);
    }
}
