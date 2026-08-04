package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.List;
import org.json.JSONObject;

public final class WebSearchTool extends BaseTool {
    public static final String NAME = "web_search";
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
        return NAME;
    }

    @Override
    public String promptSupplement(String executionMode, boolean isSsh) {
        if (isSsh) {
            return "web_search and web_fetch are executed by the app-side network configuration and do not depend on the SSH host environment.";
        }
        return "web_search and web_fetch are executed by the app-side network configuration and do not depend on the terminal provider environment.";
    }

    @Override
    public String getDescription() {
        return "Search the internet. Requires the user to configure the search API, model/search source, and key in MCP tool settings first. Suitable for querying the latest facts, documentation, news, and web materials.";
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
        return context.getString(R.string.tool_call_action_search);
    }

    @Override
    public int getActionIcon() {
        return ICON_SEARCH;
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
                        .put("query", new JSONObject().put("type", "string").put("description", "Search keyword or question"))
                        .put("limit", new JSONObject().put("type", "number").put("description", "Number of results to return, 1-10, default 5")))
                .put("required", new org.json.JSONArray().put("query"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String query = input.optString("query").trim();
        if (query.length() == 0) {
            return error(context.getString(R.string.tool_web_search_query_empty));
        }
        int limit = input.optInt("limit", 5);
        try {
            List<SearchResultItem> results = webSearchService.search(config(), query, limit);
            if (results.isEmpty()) {
                return ok(context.getString(R.string.tool_web_search_no_results, query));
            }
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                SearchResultItem item = results.get(i);
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
            return error(context.getString(R.string.tool_web_search_failed, e.getMessage()));
        }
    }

    private WebSearchConfig config() {
        return configRepository == null ? WebSearchConfig.defaultConfig() : configRepository.get();
    }
}
