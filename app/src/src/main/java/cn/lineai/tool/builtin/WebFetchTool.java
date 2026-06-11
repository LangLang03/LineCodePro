package cn.lineai.tool.builtin;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONObject;

public final class WebFetchTool extends BaseTool {
    private final WebSearchService webSearchService = new WebSearchService();

    @Override
    public String getName() {
        return "web_fetch";
    }

    @Override
    public String getDescription() {
        return "查看并提取指定网页的文本内容。URL 必须使用 HTTPS，或使用 localhost/127.0.0.1/10.0.2.2 的 HTTP。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("url", new JSONObject().put("type", "string").put("description", "要查看的网页 URL"))
                        .put("maxChars", new JSONObject().put("type", "number").put("description", "最多返回字符数，默认 12000，最大 30000")))
                .put("required", new org.json.JSONArray().put("url"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String url = input.optString("url").trim();
        if (url.length() == 0) {
            return error("URL 不能为空。");
        }
        try {
            String content = webSearchService.fetchPage(url, input.optInt("maxChars", 12000));
            return ok("URL: " + url + "\n\n" + content);
        } catch (Exception e) {
            return error("网页查看失败: " + e.getMessage());
        }
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
