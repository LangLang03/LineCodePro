package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.R;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PhoneControlService;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phone control tool: get the current window View hierarchy.
 */
public final class PhoneViewHierarchyTool extends BaseTool {
    public static final String NAME = "phone_view_hierarchy";
    private static final String VIEW_HIERARCHY_DESC = "Get the current window View hierarchy, including class names, resource ids, text, content descriptions, and screen bounds.";
    private final Context context;

    public PhoneViewHierarchyTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return context == null ? VIEW_HIERARCHY_DESC : VIEW_HIERARCHY_DESC;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.PHONE_CONTROL;
    }

    @Override
    public int getActionIcon() {
        return ICON_SMARTPHONE;
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_summary_view_hierarchy);
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_view_hierarchy);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray());
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        PhoneControlService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        return ok(service.viewHierarchy());
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
