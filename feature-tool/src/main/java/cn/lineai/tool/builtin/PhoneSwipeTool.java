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
 * Phone control tool: swipe between screen coordinates.
 */
public final class PhoneSwipeTool extends BaseTool {
    public static final String NAME = "phone_swipe";
    private static final String SWIPE_DESC = "Swipe from the start coordinates to the end coordinates. x1/y1 are the start point, x2/y2 are the end point, duration_ms is optional and defaults to 300.";
    private final Context context;

    public PhoneSwipeTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return context == null ? SWIPE_DESC : SWIPE_DESC;
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
        return ctx.getString(R.string.tool_call_phone_summary_swipe,
                input.optInt("x1"), input.optInt("y1"), input.optInt("x2"), input.optInt("y2"));
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_swipe);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x1", new JSONObject().put("type", "number").put("description", "Start X coordinate"))
                        .put("y1", new JSONObject().put("type", "number").put("description", "Start Y coordinate"))
                        .put("x2", new JSONObject().put("type", "number").put("description", "End X coordinate"))
                        .put("y2", new JSONObject().put("type", "number").put("description", "End Y coordinate"))
                        .put("duration_ms", new JSONObject().put("type", "number").put("description", "Swipe duration in milliseconds, default 300")))
                .put("required", new JSONArray().put("x1").put("y1").put("x2").put("y2"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        PhoneControlService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        int x1 = input.optInt("x1");
        int y1 = input.optInt("y1");
        int x2 = input.optInt("x2");
        int y2 = input.optInt("y2");
        int durationMs = input.optInt("duration_ms", 300);
        boolean success = service.swipe(x1, y1, x2, y2, durationMs);
        return success ? ok(this.context.getString(R.string.phone_tool_swipe_success, x1, y1, x2, y2)) : error(this.context.getString(R.string.phone_tool_swipe_failed));
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
