package cn.lineai.mvp.agent;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import android.os.SystemClock;
import cn.lineai.ai.ToolCallTextParser;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentProgressSession {
    private static final long AGENT_PROGRESS_RENDER_INTERVAL_MS = 100L;

    private final int generationId;
    private final String toolCallId;
    private final String toolName;
    private final String type;
    private final String description;
    private final LinkedHashMap<String, ToolCall> displayToolCalls = new LinkedHashMap<>();
    private final LinkedHashMap<String, ToolResult> displayToolResults = new LinkedHashMap<>();
    private final HashMap<String, String> displayIdByOriginalId = new HashMap<>();
    private String output = "";
    private String thinking = "";
    private String status = "running";
    private String modelContent = "";
    private boolean error;
    private boolean renderScheduled;
    private long lastRenderAt;
    private final AgentProgressMirror mirror;

    public AgentProgressSession(int generationId, String toolCallId, String toolName, String type, String description) {
        this(generationId, toolCallId, toolName, type, description, null);
    }

    public AgentProgressSession(
            int generationId,
            String toolCallId,
            String toolName,
            String type,
            String description,
            AgentProgressMirror mirror
    ) {
        this.generationId = generationId;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.type = type == null ? "" : type;
        this.description = description == null ? "" : description;
        this.mirror = mirror;
    }

    public int getGenerationId() {
        return generationId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public synchronized boolean canRender() {
        return toolCallId.length() > 0;
    }

    public synchronized boolean canMirror() {
        return mirror != null;
    }

    public synchronized void beginTurn() {
        output = "";
        status = "running";
        error = false;
    }

    public synchronized void appendText(String delta) {
        if (delta != null && delta.length() > 0) {
            output += delta;
            addToolCalls(ToolCallTextParser.parseStreamingPreview(output).getToolCalls());
        }
    }

    public synchronized void appendThinking(String delta) {
        if (delta != null && delta.length() > 0) {
            thinking += delta;
        }
    }

    public synchronized void setTurnResult(String nextOutput, String nextThinking) {
        output = nextOutput == null ? "" : nextOutput;
        if (nextThinking != null && nextThinking.trim().length() > 0) {
            thinking = nextThinking;
        }
    }

    public synchronized void addToolCalls(List<ToolCall> calls) {
        if (calls == null) {
            return;
        }
        for (ToolCall call : calls) {
            if (call == null || call.getId().length() == 0 || call.getName().length() == 0) {
                continue;
            }
            if (displayIdByOriginalId.containsKey(call.getId())) {
                continue;
            }
            String displayId = toolCallId + "_agent_" + displayToolCalls.size();
            displayIdByOriginalId.put(call.getId(), displayId);
            displayToolCalls.put(displayId, new ToolCall(displayId, call.getName(), call.getArguments()));
        }
    }

    public synchronized String displayToolCallId(ToolCall call) {
        if (call == null || call.getId().length() == 0 || call.getName().length() == 0) {
            return "";
        }
        addToolCalls(Collections.singletonList(call));
        String displayId = displayIdByOriginalId.get(call.getId());
        return displayId == null ? "" : displayId;
    }

    public synchronized void putToolResult(ToolCall originalCall, ToolResult result) {
        if (originalCall == null || result == null) {
            return;
        }
        String displayId = displayIdByOriginalId.get(originalCall.getId());
        if (displayId == null || displayId.length() == 0) {
            addToolCalls(Collections.singletonList(originalCall));
            displayId = displayIdByOriginalId.get(originalCall.getId());
        }
        if (displayId == null || displayId.length() == 0) {
            return;
        }
        displayToolResults.put(displayId, result.withCall(displayId, originalCall.getName()));
    }

    public synchronized void setFinished(String nextStatus, boolean nextError, String nextModelContent) {
        status = nextStatus == null || nextStatus.length() == 0 ? "done" : nextStatus;
        error = nextError;
        modelContent = nextModelContent == null ? "" : nextModelContent;
    }

    public synchronized void setStatus(String nextStatus, boolean nextError) {
        status = nextStatus == null || nextStatus.length() == 0 ? status : nextStatus;
        error = nextError;
    }

    public synchronized boolean shouldScheduleRender() {
        if ((!canRender() && !canMirror()) || renderScheduled) {
            return false;
        }
        renderScheduled = true;
        return true;
    }

    public synchronized long renderDelayMs() {
        long now = uptimeMillis();
        return Math.max(0L, AGENT_PROGRESS_RENDER_INTERVAL_MS - (now - lastRenderAt));
    }

    public synchronized ToolResult snapshotResult() {
        renderScheduled = false;
        lastRenderAt = uptimeMillis();
        return ToolResult.withReview(toolCallId, toolName, payload(), error, "", status, "");
    }

    public synchronized void notifyMirror() {
        if (mirror != null) {
            renderScheduled = false;
            lastRenderAt = uptimeMillis();
            mirror.onAgentProgress(payload(), error);
        }
    }

    private long uptimeMillis() {
        try {
            return SystemClock.uptimeMillis();
        } catch (RuntimeException ignored) {
            return System.currentTimeMillis();
        }
    }

    private String payload() {
        try {
            JSONObject object = new JSONObject();
            object.put("linecode_agent_progress", true);
            object.put("kind", toolName);
            object.put("status", status);
            object.put("type", type);
            object.put("description", description);
            object.put("output", visibleOutput(output));
            object.put("thinking", thinking);
            object.put("tool_call_count", displayToolCalls.size());
            object.put("model_content", modelContent);
            JSONArray tools = new JSONArray();
            for (ToolCall call : displayToolCalls.values()) {
                JSONObject item = new JSONObject()
                        .put("id", call.getId())
                        .put("name", call.getName())
                        .put("arguments", call.getArguments());
                ToolResult result = displayToolResults.get(call.getId());
                if (result != null) {
                    item.put("result", new JSONObject()
                            .put("content", result.getContent())
                            .put("is_error", result.isError())
                            .put("diff_id", result.getDiffId())
                            .put("review_state", result.getReviewState())
                            .put("review_message", result.getReviewMessage()));
                }
                tools.put(item);
            }
            object.put("tool_calls", tools);
            return object.toString();
        } catch (Exception e) {
            return modelContent.length() > 0 ? modelContent : visibleOutput(output);
        }
    }

    private String visibleOutput(String rawOutput) {
        ToolCallTextParser.Result parsed = ToolCallTextParser.parse(rawOutput);
        return parsed.hasToolMarkup() ? parsed.getText() : rawOutput == null ? "" : rawOutput;
    }
}
