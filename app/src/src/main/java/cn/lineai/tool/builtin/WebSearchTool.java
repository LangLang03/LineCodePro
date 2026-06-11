package cn.lineai.tool.builtin;

import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.util.List;
import org.json.JSONObject;

public final class WebSearchTool extends BaseTool {
    private final WebSearchConfigRepository configRepository;
    private final WebSearchService webSearchService = new WebSearchService();

    public WebSearchTool() {
        this(null);
    }

    public WebSearchTool(WebSearchConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索互联网信息。需要用户先在 MCP 工具设置中配置搜索 API、模型/搜索源和密钥。适合查询最新事实、文档、新闻和网页资料。";
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
                        .put("query", new JSONObject().put("type", "string").put("description", "搜索关键词或问题"))
                        .put("limit", new JSONObject().put("type", "number").put("description", "返回结果数量，1-10，默认 5")))
                .put("required", new org.json.JSONArray().put("query"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String query = input.optString("query").trim();
        if (query.length() == 0) {
            return error("搜索关键词不能为空。");
        }
        int limit = input.optInt("limit", 5);
        try {
            List<WebSearchService.SearchResultItem> results = webSearchService.search(config(), query, limit);
            if (results.isEmpty()) {
                return ok("未搜索到与 \"" + query + "\" 相关的网页结果。");
            }
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                WebSearchService.SearchResultItem item = results.get(i);
                if (i > 0) {
                    content.append("\n\n");
                }
                content.append(i + 1).append(". ").append(item.title).append('\n')
                        .append("URL: ").append(item.url);
                if (item.publishedDate.length() > 0) {
                    content.append('\n').append("Date: ").append(item.publishedDate);
                }
                if (item.snippet.length() > 0) {
                    content.append('\n').append("Snippet: ").append(item.snippet);
                }
            }
            return ok(content.toString());
        } catch (Exception e) {
            return error("网页搜索失败: " + e.getMessage());
        }
    }

    private WebSearchConfig config() {
        return configRepository == null ? WebSearchConfig.defaultConfig() : configRepository.get();
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
