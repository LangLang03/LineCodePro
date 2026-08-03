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
 * Phone control tool: click a View by resource id, text, or coordinates.
 */
public final class PhoneClickViewTool extends BaseTool {
    public static final String NAME = "phone_click_view";
    private static final String CLICK_VIEW_DESC = "Tap a View in the current window. Supports locating by resource id, visible text, or screen coordinates. Accessibility must be enabled.";
    private final Context context;

    public PhoneClickViewTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_click_view";
    }

    @Override
    public String getDescription() {
        return context == null ? CLICK_VIEW_DESC : CLICK_VIEW_DESC;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
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
        if (ctx == null) return getName();
        String resourceId = input.optString("resource_id");
        if (resourceId.length() > 0) {
            return ctx.getString(R.string.tool_call_phone_summary_click_view, resourceId);
        }
        String text = input.optString("text");
        if (text.length() > 0) {
            return ctx.getString(R.string.tool_call_phone_summary_click_text, text);
        }
        return ctx.getString(R.string.tool_call_phone_summary_point, input.optInt("x"), input.optInt("y"));
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_click_view);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("resource_id", new JSONObject().put("type", "string").put("description", "The View's resource id, either the full id or the short name"))
                        .put("text", new JSONObject().put("type", "string").put("description", "Text displayed on the View"))
                        .put("x", new JSONObject().put("type", "integer").put("description", "Screen coordinate x"))
                        .put("y", new JSONObject().put("type", "integer").put("description", "Screen coordinate y")))
                .put("required", new JSONArray());
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        PhoneControlService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        String resourceId = input.optString("resource_id");
        String text = input.optString("text");
        int x = input.optInt("x", Integer.MIN_VALUE);
        int y = input.optInt("y", Integer.MIN_VALUE);
        if (resourceId.length() > 0) {
            boolean success = service.clickById(resourceId);
            return success ? ok(this.context.getString(R.string.phone_tool_click_view_success, resourceId)) : error(this.context.getString(R.string.phone_tool_click_view_failed, resourceId));
        }
        if (text.length() > 0) {
            boolean success = service.clickByText(text);
            return success ? ok(this.context.getString(R.string.phone_tool_click_view_success, text)) : error(this.context.getString(R.string.phone_tool_click_view_failed, text));
        }
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            boolean success = service.clickByCoordinates(x, y);
            return success ? ok(this.context.getString(R.string.phone_tool_click_point_success, x, y)) : error(this.context.getString(R.string.phone_tool_click_point_failed, x, y));
        }
        return error(this.context.getString(R.string.phone_tool_click_view_missing_target));
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
