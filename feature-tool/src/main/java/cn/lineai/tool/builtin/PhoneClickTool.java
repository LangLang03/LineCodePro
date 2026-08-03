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
 * Phone control tool: click at screen coordinates.
 */
public final class PhoneClickTool extends BaseTool {
    public static final String NAME = "phone_click";
    private static final String CLICK_DESC = "Tap the specified screen coordinates. x and y are screen pixel coordinates.";
    private final Context context;

    public PhoneClickTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return context == null ? CLICK_DESC : CLICK_DESC;
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
        return ctx.getString(R.string.tool_call_phone_summary_point, input.optInt("x"), input.optInt("y"));
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_click);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x", new JSONObject().put("type", "number").put("description", "Screen X coordinate"))
                        .put("y", new JSONObject().put("type", "number").put("description", "Screen Y coordinate")))
                .put("required", new JSONArray().put("x").put("y"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        PhoneControlService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        int x = input.optInt("x");
        int y = input.optInt("y");
        boolean success = service.click(x, y);
        return success ? ok(this.context.getString(R.string.phone_tool_click_success, x, y)) : error(this.context.getString(R.string.phone_tool_click_failed));
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
