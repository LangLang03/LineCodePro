package cn.lineai.mvp.agent;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.tool.ToolContext;
import cn.lineai.tool.builtin.AgentPipelineTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PipelineProgressSession {
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";
    private static final String AGENT_PIPELINE_RUNNING_MESSAGE = "Agent 流水线运行中。";

    public interface ProgressPublisher {
        void publish(String toolCallId, String toolName, String payload, boolean error);
    }

    private final Object lock = new Object();
    private final ToolContext parentContext;
    private final String toolCallId;
    private final ProgressPublisher publisher;
    private final ArrayList<PipelineAgent> agents;
    private final LinkedHashMap<String, PipelineAgentState> stateById = new LinkedHashMap<>();
    private String status = "running";
    private boolean error;
    private String finalSummary = "";

    public PipelineProgressSession(ToolContext parentContext, ArrayList<PipelineAgent> agents) {
        this(parentContext, agents, null);
    }

    public PipelineProgressSession(ToolContext parentContext, ArrayList<PipelineAgent> agents, ProgressPublisher publisher) {
        this.parentContext = parentContext;
        this.toolCallId = parentContext == null ? "" : parentContext.getToolCallId();
        this.publisher = publisher;
        this.agents = agents == null ? new ArrayList<>() : agents;
        for (PipelineAgent agent : this.agents) {
            stateById.put(agent.getId(), new PipelineAgentState(agent));
        }
    }

    public void setStatus(String nextStatus, boolean nextError) {
        synchronized (lock) {
            this.status = nextStatus == null ? "running" : nextStatus;
            this.error = nextError;
        }
    }

    public void setFinalSummary(String summary) {
        synchronized (lock) {
            this.finalSummary = summary == null ? "" : summary;
        }
    }

    public String getFinalSummary() {
        synchronized (lock) {
            return finalSummary;
        }
    }

    public void beginAgent(PipelineAgent agent) {
        synchronized (lock) {
            PipelineAgentState state = stateById.get(agent.getId());
            if (state != null) {
                state.setStatus("running");
            }
            publishLocked(false);
        }
    }

    public void updateAgent(PipelineAgent agent, String agentProgressPayload, boolean agentError) {
        synchronized (lock) {
            PipelineAgentState state = stateById.get(agent.getId());
            if (state == null) {
                return;
            }
            try {
                JSONObject object = new JSONObject(agentProgressPayload);
                state.setStatus(object.optString("status", state.getStatus()));
                state.setOutput(object.optString("output", state.getOutput()));
                state.setThinking(object.optString("thinking", state.getThinking()));
                state.setToolCallCount(object.optInt("tool_call_count", state.getToolCallCount()));
                JSONArray toolCalls = object.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    state.setToolCalls(new JSONArray(toolCalls.toString()));
                }
                state.setError(agentError || object.optBoolean("error", false) || "error".equals(state.getStatus()));
            } catch (Exception ignored) {
                state.setOutput(agentProgressPayload == null ? state.getOutput() : agentProgressPayload);
                state.setError(agentError);
            }
            error = error || state.isError();
            publishLocked(error);
        }
    }

    public void finishAgent(PipelineAgent agent, AgentRunResult result) {
        synchronized (lock) {
            PipelineAgentState state = stateById.get(agent.getId());
            if (state != null) {
                state.setStatus(result.isError() ? "error" : "done");
                state.setOutput(result.getOutput());
                state.setToolCallCount(result.getToolCallCount());
                state.setError(result.isError());
            }
            error = error || result.isError();
            publishLocked(error);
        }
    }

    public void terminate() {
        synchronized (lock) {
            status = "error";
            error = true;
            for (PipelineAgentState state : stateById.values()) {
                if ("running".equals(state.getStatus()) || "waiting".equals(state.getStatus())) {
                    state.setStatus("error");
                    state.setError(true);
                    state.setOutput(AGENT_TERMINATED_MESSAGE);
                }
            }
            publishLocked(true);
        }
    }

    public void publish(boolean nextError) {
        synchronized (lock) {
            publishLocked(nextError);
        }
    }

    public String payload() {
        synchronized (lock) {
            return buildPayloadLocked();
        }
    }

    private void publishLocked(boolean nextError) {
        String payload = buildPayloadLocked();
        if (publisher != null && toolCallId.length() > 0) {
            publisher.publish(toolCallId, AgentPipelineTool.NAME, payload, nextError);
            return;
        }
        if (parentContext == null || toolCallId.length() == 0) {
            return;
        }
        parentContext.reportToolProgress(AgentPipelineTool.NAME, payload, nextError);
    }

    private String buildPayloadLocked() {
        try {
            JSONObject object = new JSONObject();
            object.put("linecode_agent_pipeline_progress", true);
            object.put("kind", AgentPipelineTool.NAME);
            object.put("status", status);
            object.put("total", agents.size());
            object.put("completed", countStatusLocked("done"));
            object.put("running", countStatusLocked("running"));
            object.put("failed", countFailedLocked());
            object.put("error", error);
            if (finalSummary.length() > 0) {
                object.put("summary", finalSummary);
            }
            JSONArray array = new JSONArray();
            for (PipelineAgentState state : stateById.values()) {
                array.put(state.toJson());
            }
            object.put("agents", array);
            return object.toString();
        } catch (Exception e) {
            return AGENT_PIPELINE_RUNNING_MESSAGE;
        }
    }

    private int countStatusLocked(String value) {
        int count = 0;
        for (PipelineAgentState state : stateById.values()) {
            if (value.equals(state.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private int countFailedLocked() {
        int count = 0;
        for (PipelineAgentState state : stateById.values()) {
            if (state.isError() || "error".equals(state.getStatus())) {
                count++;
            }
        }
        return count;
    }
}
