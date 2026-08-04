package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import android.graphics.Bitmap;
import cn.lineai.tool.R;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PhoneControlService;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phone control tool: capture screenshot and return saved image path.
 */
public final class PhoneScreenshotTool extends BaseTool {
    public static final String NAME = "phone_screenshot";
    private static final String SCREENSHOT_DESC = "Capture the current screen, save it to the app cache directory, and return the image file path. Accessibility must be enabled.";
    private final Context context;

    public PhoneScreenshotTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return context == null ? SCREENSHOT_DESC : SCREENSHOT_DESC;
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
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_summary_screenshot);
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_screenshot);
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
        if (this.context == null) {
            return error("Screenshot tool is missing app context");
        }
        PhoneControlService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) {
            return error(this.context.getString(R.string.phone_tool_screenshot_failed));
        }
        File dir = PhoneScreenshotCache.directory(this.context);
        if (!dir.exists() && !dir.mkdirs()) {
            bitmap.recycle();
            return error(this.context.getString(R.string.phone_tool_screenshot_cache_dir_failed));
        }
        File file = new File(dir, String.format(Locale.ROOT, "screenshot-%d.png", System.currentTimeMillis()));
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return error(this.context.getString(R.string.phone_tool_screenshot_save_failed));
            }
            return ok(file.getAbsolutePath());
        } catch (Exception e) {
            return error(this.context.getString(R.string.phone_tool_screenshot_save_failed_with_reason, e.getMessage()));
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
            bitmap.recycle();
        }
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
