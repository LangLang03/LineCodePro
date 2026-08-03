package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONObject;

public final class WebFetchTool extends BaseTool {
    public static final String NAME = "web_fetch";
    private final WebSearchService webSearchService = new WebSearchService();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "View and extract the text content of a specified web page. The URL must use HTTPS, or HTTP on localhost/127.0.0.1/10.0.2.2.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_action_fetch);
    }

    @Override
    public int getActionIcon() {
        return ICON_GLOBE;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("url", new JSONObject().put("type", "string").put("description", "The web page URL to view"))
                        .put("maxChars", new JSONObject().put("type", "number").put("description", "Maximum characters to return, default 12000, max 30000")))
                .put("required", new org.json.JSONArray().put("url"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String url = input.optString("url").trim();
        if (url.length() == 0) {
            return error(context.getString(R.string.tool_web_fetch_url_empty));
        }
        try {
            String content = webSearchService.fetchPage(url, input.optInt("maxChars", 12000));
            return ok("URL: " + url + "\n\n" + content);
        } catch (Exception e) {
            return error(context.getString(R.string.tool_web_fetch_failed, e.getMessage()));
        }
    }
}
