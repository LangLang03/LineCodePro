package cn.lineai.mvp.agent;

import cn.lineai.tool.StoredAgentResult;
import cn.lineai.tool.ToolContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

public final class AgentResultRegistry implements ToolContext.AgentResultStore {
    public static final String COMPACT_MARKER = "linecode_agent_ref";

    private final Object lock = new Object();
    private final LinkedHashMap<String, AgentResultRecord> records = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);

    public String allocateId() {
        long seq = sequence.getAndIncrement();
        long now = System.currentTimeMillis();
        return "ag_" + Long.toString(now, 36) + "_" + Long.toString(seq, 36);
    }

    public void put(AgentResultRecord record) {
        if (record == null || record.getAgentId().length() == 0) {
            return;
        }
        synchronized (lock) {
            records.put(record.getAgentId(), record);
        }
    }

    public AgentResultRecord getRecord(String agentId) {
        if (agentId == null || agentId.length() == 0) {
            return null;
        }
        synchronized (lock) {
            return records.get(agentId);
        }
    }

    @Override
    public StoredAgentResult get(String agentId) {
        AgentResultRecord record = getRecord(agentId);
        if (record == null) {
            return null;
        }
        return new StoredAgentResult(
                record.getAgentId(),
                record.getStatus(),
                record.getType(),
                record.getDescription(),
                record.getPreview(),
                record.getFullOutput(),
                record.isError(),
                record.isAsync(),
                record.getToolCallCount()
        );
    }

    public void updateStatus(String agentId, String status, boolean error, String preview) {
        synchronized (lock) {
            AgentResultRecord current = records.get(agentId);
            if (current == null) {
                return;
            }
            records.put(agentId, current.withStatus(status, error, preview));
        }
    }

    public void updateFullOutput(
            String agentId,
            String fullOutput,
            String thinking,
            String progressJson,
            int toolCallCount
    ) {
        updateFullOutput(agentId, fullOutput, thinking, progressJson, toolCallCount, false);
    }

    public void updateFullOutput(
            String agentId,
            String fullOutput,
            String thinking,
            String progressJson,
            int toolCallCount,
            boolean error
    ) {
        synchronized (lock) {
            AgentResultRecord current = records.get(agentId);
            if (current == null) {
                return;
            }
            records.put(agentId, current.withFullOutput(
                    fullOutput, thinking, progressJson, toolCallCount, error));
        }
    }

    public void clearGeneration(int generationId) {
        synchronized (lock) {
            ArrayList<String> remove = new ArrayList<>();
            for (AgentResultRecord record : records.values()) {
                if (record.getGenerationId() == generationId) {
                    remove.add(record.getAgentId());
                }
            }
            for (String id : remove) {
                records.remove(id);
            }
        }
    }

    public static String toCompactJson(AgentResultRecord record) {
        if (record == null) {
            return "{}";
        }
        try {
            JSONObject object = new JSONObject();
            object.put(COMPACT_MARKER, true);
            object.put("agent_id", record.getAgentId());
            object.put("status", record.getStatus());
            object.put("type", record.getType());
            object.put("description", record.getDescription());
            object.put("preview", record.getPreview());
            object.put("tool_call_count", record.getToolCallCount());
            object.put("error", record.isError());
            object.put("async", record.isAsync());
            if (record.getToolCallId().length() > 0) {
                object.put("tool_call_id", record.getToolCallId());
            }
            return object.toString();
        } catch (Exception e) {
            return "{\"" + COMPACT_MARKER + "\":true,\"agent_id\":\"" + record.getAgentId() + "\"}";
        }
    }

    public static AgentResultRecord parseCompact(String content) {
        if (content == null || content.trim().length() == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(content);
            if (!object.optBoolean(COMPACT_MARKER, false)) {
                return null;
            }
            String agentId = object.optString("agent_id", "").trim();
            if (agentId.length() == 0) {
                return null;
            }
            return new AgentResultRecord(
                    agentId,
                    object.optString("tool_call_id", ""),
                    "",
                    object.optString("status", "running"),
                    object.optString("type", ""),
                    object.optString("description", ""),
                    object.optString("preview", ""),
                    "",
                    "",
                    "",
                    object.optInt("tool_call_count", 0),
                    object.optBoolean("error", false),
                    object.optBoolean("async", false),
                    0,
                    System.currentTimeMillis()
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
