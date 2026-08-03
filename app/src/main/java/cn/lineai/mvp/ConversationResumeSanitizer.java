package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

final class ConversationResumeSanitizer {
    private static final String FALLBACK_TERMINATED_MESSAGE = "上次生成已中断。";

    private ConversationResumeSanitizer() {
    }

    static Result sanitize(ConversationRecord conversation, String terminatedMessage) {
        if (conversation == null) {
            return new Result(null, false);
        }
        String message = terminatedMessage == null || terminatedMessage.trim().length() == 0
                ? FALLBACK_TERMINATED_MESSAGE
                : terminatedMessage;
        ArrayList<MessageRecord> records = new ArrayList<>();
        HashSet<String> existingToolResultIds = new HashSet<>();
        boolean changed = false;
        for (MessageRecord record : conversation.getMessages()) {
            MessageRecord next = sanitizeRecord(record, message);
            if (next == null) {
                changed = true;
                continue;
            }
            if (next != record) {
                changed = true;
            }
            if (next.getRole() == ChatMessage.Role.TOOL && next.getToolCallId().length() > 0) {
                existingToolResultIds.add(next.getToolCallId());
            }
            records.add(next);
        }
        ArrayList<MessageRecord> terminatedResults = missingToolResults(conversation, records, existingToolResultIds, message);
        if (!terminatedResults.isEmpty()) {
            records.addAll(terminatedResults);
            changed = true;
        }
        if (!changed) {
            return new Result(conversation, false);
        }
        return new Result(new ConversationRecord(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getProjectId(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.isCurrent(),
                conversation.getRawJson(),
                records
        ), true);
    }

    private static MessageRecord sanitizeRecord(MessageRecord record, String terminatedMessage) {
        if (record == null) {
            return null;
        }
        String content = record.getContent();
        String rawJson = record.getRawJson();
        boolean streaming = record.isStreaming();
        boolean error = record.isError();
        boolean changed = false;
        String reviewState = readRawString(rawJson, "review_state");
        if (isCompactRunning(rawJson)) {
            rawJson = putRawString(rawJson, "compact_status", ChatMessage.COMPACT_STATUS_ERROR);
            streaming = false;
            changed = true;
        }
        if (streaming) {
            streaming = false;
            changed = true;
        }
        if (record.getRole() == ChatMessage.Role.TOOL) {
            SanitizedPayload payload = sanitizeToolContent(content, terminatedMessage);
            if (!payload.content.equals(content)) {
                content = payload.content;
                changed = true;
            }
            if (payload.error && !error) {
                error = true;
                changed = true;
            }
            boolean reviewStateUnfinished = isUnfinishedReviewState(reviewState, content);
            if (reviewStateUnfinished) {
                if (!error) {
                    error = true;
                    changed = true;
                }
                if (!payload.error && !terminatedMessage.equals(content)) {
                    content = terminatedMessage;
                    changed = true;
                }
                if (reviewState.length() > 0) {
                    rawJson = putRawString(rawJson, "review_state", "");
                    changed = true;
                }
            }
        }
        if (!changed) {
            return record;
        }
        return copyRecord(record, content, streaming, error, rawJson);
    }

    static SanitizedPayload sanitizeToolContent(String content, String terminatedMessage) {
        if (content == null || content.trim().length() == 0) {
            return new SanitizedPayload(content == null ? "" : content, false, false);
        }
        try {
            JSONObject object = new JSONObject(content);
            if (object.optBoolean("linecode_agent_progress")) {
                boolean changed = sanitizeAgentProgress(object, terminatedMessage);
                return new SanitizedPayload(changed ? object.toString() : content, changed, changed);
            }
            if (object.optBoolean("linecode_agent_pipeline_progress")) {
                boolean changed = sanitizePipelineProgress(object, terminatedMessage);
                return new SanitizedPayload(changed ? object.toString() : content, changed, changed);
            }
            return new SanitizedPayload(content, false, false);
        } catch (Exception ignored) {
            return new SanitizedPayload(content, false, false);
        }
    }

    private static boolean sanitizeAgentProgress(JSONObject object, String terminatedMessage) throws Exception {
        boolean changed = false;
        String status = object.optString("status");
        boolean unfinished = isUnfinishedAgentStatus(status);
        if (unfinished) {
            object.put("status", "error");
            object.put("error", true);
            changed = true;
            String output = object.optString("output").trim();
            if (output.length() == 0) {
                object.put("output", terminatedMessage);
            } else if (!output.contains(terminatedMessage)) {
                object.put("output", output + "\n\n" + terminatedMessage);
            }
            object.put("model_content", terminatedMessage);
        }
        JSONArray calls = object.optJSONArray("tool_calls");
        if (sanitizeNestedToolCalls(calls, terminatedMessage, unfinished)) {
            changed = true;
        }
        return changed;
    }

    private static boolean sanitizePipelineProgress(JSONObject object, String terminatedMessage) throws Exception {
        boolean changed = false;
        String status = object.optString("status", "running");
        if (isUnfinishedPipelineStatus(status)) {
            object.put("status", "error");
            object.put("error", true);
            changed = true;
        }
        JSONArray agents = object.optJSONArray("agents");
        if (agents != null) {
            for (int i = 0; i < agents.length(); i++) {
                JSONObject agent = agents.optJSONObject(i);
                if (agent == null) {
                    continue;
                }
                String agentStatus = agent.optString("status", "waiting");
                boolean unfinishedAgent = isUnfinishedPipelineAgentStatus(agentStatus);
                if (unfinishedAgent) {
                    agent.put("status", "error");
                    agent.put("error", true);
                    if (agent.optString("output").trim().length() == 0) {
                        agent.put("output", terminatedMessage);
                    }
                    changed = true;
                }
                if (sanitizeNestedToolCalls(agent.optJSONArray("tool_calls"), terminatedMessage, unfinishedAgent)) {
                    agent.put("error", true);
                    changed = true;
                }
            }
        }
        if (changed) {
            object.put("failed", countFailedAgents(agents));
            object.put("running", 0);
        }
        return changed;
    }

    private static boolean sanitizeNestedToolCalls(
            JSONArray calls,
            String terminatedMessage,
            boolean terminateMissingResults
    ) throws Exception {
        if (calls == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject result = item.optJSONObject("result");
            if (result == null && terminateMissingResults) {
                item.put("result", new JSONObject()
                        .put("content", terminatedMessage)
                        .put("is_error", true)
                        .put("diff_id", "")
                        .put("review_state", "")
                        .put("review_message", ""));
                changed = true;
            } else if (result != null && isUnfinishedReviewState(result.optString("review_state"), result.optString("content"))) {
                result.put("content", terminatedMessage);
                result.put("is_error", true);
                result.put("review_state", "");
                changed = true;
            }
            if (sanitizeNestedToolCalls(item.optJSONArray("tool_calls"), terminatedMessage, terminateMissingResults)) {
                changed = true;
            }
        }
        return changed;
    }

    private static ArrayList<MessageRecord> missingToolResults(
            ConversationRecord conversation,
            ArrayList<MessageRecord> records,
            HashSet<String> existingToolResultIds,
            String terminatedMessage
    ) {
        ArrayList<MessageRecord> results = new ArrayList<>();
        HashSet<String> addedIds = new HashSet<>();
        long timestamp = conversation.getUpdatedAt() > 0 ? conversation.getUpdatedAt() : System.currentTimeMillis();
        for (MessageRecord record : records) {
            if (record == null || record.getRole() != ChatMessage.Role.ASSISTANT) {
                continue;
            }
            for (ToolCall call : readToolCalls(record.getRawJson())) {
                if (call == null || call.getId().length() == 0 || existingToolResultIds.contains(call.getId())
                        || addedIds.contains(call.getId())) {
                    continue;
                }
                results.add(new MessageRecord(
                        recoveredToolMessageId(conversation.getId(), call.getId(), records, results),
                        ChatMessage.Role.TOOL,
                        terminatedMessage,
                        "",
                        timestamp,
                        false,
                        true,
                        false,
                        call.getId(),
                        call.getName(),
                        true,
                        ""
                ));
                addedIds.add(call.getId());
            }
        }
        return results;
    }

    private static MessageRecord copyRecord(
            MessageRecord source,
            String content,
            boolean streaming,
            boolean error,
            String rawJson
    ) {
        return new MessageRecord(
                source.getId(),
                source.getRole(),
                content,
                source.getReasoningContent(),
                source.getTimestamp(),
                streaming,
                source.isHidden(),
                source.isExcludeFromContext(),
                source.getToolCallId(),
                source.getToolName(),
                error,
                rawJson
        );
    }

    private static ArrayList<ToolCall> readToolCalls(String rawJson) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        if (rawJson == null || rawJson.trim().length() == 0) {
            return calls;
        }
        try {
            JSONArray array = new JSONObject(rawJson).optJSONArray("tool_calls");
            if (array == null) {
                return calls;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id").trim();
                if (id.length() == 0) {
                    continue;
                }
                calls.add(new ToolCall(id, item.optString("name"), item.optString("arguments", "{}")));
            }
        } catch (Exception ignored) {
        }
        return calls;
    }

    private static boolean isCompactRunning(String rawJson) {
        return ChatMessage.COMPACT_STATUS_RUNNING.equals(readRawString(rawJson, "compact_status"));
    }

    private static boolean isUnfinishedReviewState(String state, String content) {
        return "running".equals(state) || "pending".equals(state)
                || ("accepted".equals(state) && (content == null ? "" : content).trim().length() == 0);
    }

    private static boolean isUnfinishedAgentStatus(String status) {
        return "running".equals(status) || "waiting_unlock".equals(status) || "pending".equals(status);
    }

    private static boolean isUnfinishedPipelineStatus(String status) {
        return "running".equals(status) || "pending".equals(status);
    }

    private static boolean isUnfinishedPipelineAgentStatus(String status) {
        return "running".equals(status) || "waiting".equals(status) || "pending".equals(status) || "waiting_unlock".equals(status);
    }

    private static int countFailedAgents(JSONArray agents) {
        if (agents == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent != null && (agent.optBoolean("error") || "error".equals(agent.optString("status")))) {
                count++;
            }
        }
        return count;
    }

    private static String readRawString(String rawJson, String key) {
        if (rawJson == null || rawJson.trim().length() == 0) {
            return "";
        }
        try {
            return new JSONObject(rawJson).optString(key);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String putRawString(String rawJson, String key, String value) {
        try {
            JSONObject object = rawJson == null || rawJson.trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(rawJson);
            object.put(key, value == null ? "" : value);
            return object.toString();
        } catch (Exception ignored) {
            return rawJson == null ? "" : rawJson;
        }
    }

    private static String recoveredToolMessageId(
            String conversationId,
            String toolCallId,
            ArrayList<MessageRecord> records,
            ArrayList<MessageRecord> pendingRecords
    ) {
        String base = "recovered_tool_" + sanitizeId(conversationId) + "_" + sanitizeId(toolCallId);
        String candidate = base;
        int suffix = 1;
        while (containsMessageId(records, candidate) || containsMessageId(pendingRecords, candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static boolean containsMessageId(ArrayList<MessageRecord> records, String id) {
        for (MessageRecord record : records) {
            if (record != null && id.equals(record.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeId(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return safe.length() == 0 ? "unknown" : safe;
    }

    static final class Result {
        private final ConversationRecord conversation;
        private final boolean changed;

        Result(ConversationRecord conversation, boolean changed) {
            this.conversation = conversation;
            this.changed = changed;
        }

        ConversationRecord conversation() {
            return conversation;
        }

        boolean changed() {
            return changed;
        }
    }

    static final class SanitizedPayload {
        private final String content;
        private final boolean changed;
        private final boolean error;

        SanitizedPayload(String content, boolean changed, boolean error) {
            this.content = content == null ? "" : content;
            this.changed = changed;
            this.error = error;
        }

        String content() {
            return content;
        }

        boolean changed() {
            return changed;
        }

        boolean error() {
            return error;
        }
    }
}
