package cn.lineai.tool;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import org.json.JSONObject;

public abstract class BaseTool implements ToolInfo {
    // Icon constants for getActionIcon(), values matching IconButtonView in the app module.
    public static final int ICON_EXPAND = 36;
    public static final int ICON_SEARCH = 65;
    public static final int ICON_GLOBE = 39;
    public static final int ICON_FOLDER_OPEN = 9;
    public static final int ICON_PAINTBRUSH = 48;
    public static final int ICON_SPARKLES = 38;
    public static final int ICON_SMARTPHONE = 57;
    public static final int ICON_BOOK_OPEN = 30;
    public static final int ICON_BOT = 71;

    public abstract String getName();

    public abstract String getDescription();

    public abstract ToolCategory getCategory();

    public boolean needsConfirmation() {
        return false;
    }

    public boolean isAllowedInReadonlyMode() {
        return false;
    }

    public String promptSupplement(String executionMode, boolean isSsh) {
        return null;
    }

    public abstract JSONObject getParameters() throws org.json.JSONException;

    public abstract ToolResult execute(JSONObject input, ToolContext context);

    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.GENERIC;
    }

    public String getDisplayLabel(Context context, JSONObject input, String workspacePath) {
        return null;
    }

    public String getActionName(Context context) {
        return null;
    }

    public int getActionIcon() {
        return 0;
    }

    /**
     * 声明本工具使用的 ToolCall 卡片视图实现类（:tool-ui 模块）。
     * <p>子类覆写返回对应的视图类；返回 null 时按 {@link #getDisplayCategory()}
     * 回退到默认分类视图。需要依赖注入（如 DiffLoader）的视图由
     * ToolCallViewFactoryRegistry 中注册的处理器创建。</p>
     */
    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return null;
    }

    public boolean isConcurrencySafe() {
        return false;
    }

    public boolean shouldRecordDiff() {
        return false;
    }

    public boolean shouldHideOnSuccess() {
        return false;
    }

    protected ToolResult ok(String content) {
        return ToolResult.of("", getName(), content, false);
    }

    protected ToolResult error(String message) {
        return ToolResult.of("", getName(), message, true);
    }

    public final JSONObject toJson() throws org.json.JSONException {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", getParameters());
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }
}
