package cn.lineai.tool.ui;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

/**
 * Agent Pipeline 进度/结果的解析器：从 ToolResult 的 JSON 或文本中提取 AgentSummary。
 * 将解析逻辑从 UI 层下沉到 tool 层。
 */
public final class AgentPipelineSummaryParser {

    public static final class AgentSummary {
        public final String output;
        public final String thinking;
        public final String status;
        public final boolean error;
        public final JSONArray toolCalls;

        public AgentSummary(String output, String thinking, String status, boolean error, JSONArray toolCalls) {
            this.output = output == null ? "" : output;
            this.thinking = thinking == null ? "" : thinking;
            this.status = status == null || status.length() == 0 ? "waiting" : status;
            this.error = error;
            this.toolCalls = toolCalls;
        }
    }

    public static final class PipelineSummary {
        public final HashMap<String, AgentSummary> summaryById;
        public final int completed;
        public final int running;
        public final int pendingReview;
        public final int failed;
        public final boolean complete;
        public final boolean error;

        public PipelineSummary(HashMap<String, AgentSummary> summaryById, int completed, int running,
                               int pendingReview, int failed, boolean complete, boolean error) {
            this.summaryById = summaryById;
            this.completed = completed;
            this.running = running;
            this.pendingReview = pendingReview;
            this.failed = failed;
            this.complete = complete;
            this.error = error;
        }
    }

    public static JSONObject progressPayload(ToolResult result) {
        if (result == null || result.getContent().trim().length() == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(result.getContent());
            return object.optBoolean("linecode_agent_pipeline_progress") ? object : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static HashMap<String, AgentSummary> parseProgress(JSONObject progress) {
        HashMap<String, AgentSummary> values = new HashMap<>();
        if (progress == null) {
            return values;
        }
        JSONArray array = progress.optJSONArray("agents");
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String id = object.optString("id").trim();
            if (id.length() == 0) {
                continue;
            }
            String status = object.optString("status", "waiting");
            boolean error = object.optBoolean("error") || "error".equals(status);
            values.put(id, new AgentSummary(
                    object.optString("output"),
                    object.optString("thinking"),
                    status,
                    error,
                    object.optJSONArray("tool_calls")
            ));
        }
        return values;
    }

    public static HashMap<String, AgentSummary> parseResult(ToolResult result) {
        HashMap<String, AgentSummary> values = new HashMap<>();
        if (result == null || result.getContent().length() == 0) {
            return values;
        }
        String[] sections = result.getContent().split("\\n\\n## ");
        for (String section : sections) {
            String text = section.trim();
            if (text.length() == 0 || text.startsWith("Agent 流水线完成")) {
                continue;
            }
            int titleEnd = text.indexOf('\n');
            String title = titleEnd >= 0 ? text.substring(0, titleEnd) : text;
            String id = title;
            int dot = title.indexOf(" · ");
            if (dot >= 0) {
                id = title.substring(0, dot).trim();
            }
            if (id.length() == 0) {
                continue;
            }
            boolean error = text.contains("\n状态: error");
            String output = text;
            int outputStart = nthLineIndex(text, 4);
            if (outputStart >= 0 && outputStart < text.length()) {
                output = text.substring(outputStart).trim();
            }
            values.put(id, new AgentSummary(output, "", error ? "error" : "done", error, null));
        }
        return values;
    }

    public static String parseInputError(ToolCall toolCall, JSONObject input) {
        if (toolCall == null) {
            return "";
        }
        String arguments = toolCall.getArguments();
        if (arguments == null || arguments.trim().length() == 0) {
            return "";
        }
        if (input == null) {
            return "参数解析失败: 模型返回的不是合法 JSON。";
        }
        if (input.length() == 0) {
            String message = "参数解析失败";
            try {
                new JSONObject(arguments);
            } catch (Exception e) {
                message = "参数解析失败: " + e.getMessage();
            }
            return message;
        }
        return "";
    }

    public static PipelineSummary computeSummary(JSONObject progress, ToolResult result, int total) {
        boolean runningProgress = progress != null && "running".equals(progress.optString("status", "running"));
        boolean error = result != null && (result.isError() || (progress != null && "error".equals(progress.optString("status"))));
        boolean complete = progress != null
                ? ("done".equals(progress.optString("status")) && !error)
                : (result != null && !"running".equals(result.getReviewState()) && !"pending".equals(result.getReviewState()));
        HashMap<String, AgentSummary> summaryById = progress != null ? parseProgress(progress) : parseResult(result);
        int failed = error ? Math.max(progress == null ? 1 : 0, failedCount(summaryById)) : failedCount(summaryById);
        int pendingReview = pendingReviewCount(summaryById);
        int completed = summaryById.isEmpty() && complete && total > 0 && !error ? total : doneCount(summaryById);
        int running = complete ? 0 : runningCount(summaryById);
        if (!complete && running == 0 && progress == null && total > 0) {
            running = 1;
        }
        return new PipelineSummary(summaryById, completed, running, pendingReview, failed, complete, error);
    }

    public static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(java.util.Locale.US);
        if ("sub_coding".equals(value) || "subcoding".equals(value) || "coding".equals(value)) {
            return "sub-coding";
        }
        return value.length() == 0 ? "explore" : value;
    }

    static int nthLineIndex(String text, int lineCount) {
        int index = 0;
        for (int i = 0; i < lineCount; i++) {
            index = text.indexOf('\n', index);
            if (index < 0) {
                return -1;
            }
            index++;
        }
        return index;
    }

    static int doneCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if ("done".equals(summary.status) && !summary.error) {
                count++;
            }
        }
        return count;
    }

    static int failedCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if (summary.error) {
                count++;
            }
        }
        return count;
    }

    static int runningCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if ("running".equals(summary.status)) {
                count++;
            }
        }
        return count;
    }

    static int pendingReviewCount(HashMap<String, AgentSummary> summaryById) {
        int count = 0;
        for (AgentSummary summary : summaryById.values()) {
            if ("pending".equals(summary.status)) {
                count++;
            }
        }
        return count;
    }

    private AgentPipelineSummaryParser() {
    }
}
