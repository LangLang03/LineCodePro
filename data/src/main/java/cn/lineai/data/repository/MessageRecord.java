package cn.lineai.data.repository;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MessageRecord {
    private final String id;
    private final ChatMessage.Role role;
    private final String content;
    private final String reasoningContent;
    private final long timestamp;
    private final boolean streaming;
    private final boolean hidden;
    private final boolean excludeFromContext;
    private final String toolCallId;
    private final String toolName;
    private final boolean error;
    private final String rawJson;

    public MessageRecord(
            String id,
            ChatMessage.Role role,
            String content,
            String reasoningContent,
            long timestamp,
            boolean streaming,
            boolean hidden,
            boolean excludeFromContext,
            String toolCallId,
            String toolName,
            boolean error,
            String rawJson
    ) {
        this.id = id == null ? "" : id;
        this.role = role == null ? ChatMessage.Role.USER : role;
        this.content = content == null ? "" : content;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.timestamp = timestamp;
        this.streaming = streaming;
        this.hidden = hidden;
        this.excludeFromContext = excludeFromContext;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.error = error;
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public String getId() {
        return id;
    }

    public ChatMessage.Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isExcludeFromContext() {
        return excludeFromContext;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isError() {
        return error;
    }

    public String getRawJson() {
        return rawJson;
    }

    /**
     * 一次加载会话时会对每条消息调用本方法，因此整段 raw_json 只解析一次：
     * 长对话（成百上千条消息）下重复 {@code new JSONObject(rawJson)} 是主线程加载缓慢的主要原因。
     */
    public ChatMessage toChatMessage() {
        RawFields fields = new RawFields(rawJson);
        long startedAt = fields.processingStartedAt;
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                fields.toolCalls, new ArrayList<>(), toolCallId, toolName, error,
                fields.diffId, fields.reviewState, fields.reviewMessage,
                fields.compactStatus, fields.responseInputItemJson,
                fields.attachments, fields.modelSwitchNotification,
                startedAt, restoreProcessingFinish(startedAt, fields));
    }

    private static long restoreProcessingFinish(long start, RawFields fields) {
        if (start == 0) return 0;
        if (fields.processingFinishedAt > 0) return fields.processingFinishedAt;
        // A loaded conversation cannot keep counting an interrupted generation.
        return Math.max(start, fields.processingObservedAt);
    }

    /** Single-pass view over a stored {@code raw_json} blob. */
    private static final class RawFields {
        ArrayList<ToolCall> toolCalls = new ArrayList<>();
        ArrayList<InputAttachment> attachments = new ArrayList<>();
        String diffId = "";
        String reviewState = "";
        String reviewMessage = "";
        String compactStatus = "";
        String responseInputItemJson = "";
        String modelSwitchNotification = "";
        long processingStartedAt;
        long processingFinishedAt;
        long processingObservedAt;

        RawFields(String rawJson) {
            if (rawJson == null || rawJson.trim().length() == 0) {
                return;
            }
            JSONObject object;
            try {
                object = new JSONObject(rawJson);
            } catch (Exception ignored) {
                return;
            }
            toolCalls = readToolCalls(object);
            attachments = readAttachments(object);
            diffId = object.optString("diff_id");
            reviewState = object.optString("review_state");
            reviewMessage = object.optString("review_message");
            compactStatus = object.optString("compact_status");
            responseInputItemJson = object.optString("response_input_item_json");
            modelSwitchNotification = object.optString("model_switch_notification");
            processingStartedAt = object.optLong("processing_started_at", 0);
            processingFinishedAt = object.optLong("processing_finished_at", 0);
            processingObservedAt = object.optLong("processing_observed_at", 0);
        }

        private static ArrayList<ToolCall> readToolCalls(JSONObject object) {
            ArrayList<ToolCall> calls = new ArrayList<>();
            JSONArray array = object.optJSONArray("tool_calls");
            if (array == null) {
                return calls;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                calls.add(new ToolCall(
                        item.optString("id"),
                        item.optString("name"),
                        item.optString("arguments", "{}")
                ));
            }
            return calls;
        }

        private static ArrayList<InputAttachment> readAttachments(JSONObject object) {
            ArrayList<InputAttachment> attachments = new ArrayList<>();
            JSONArray array = object.optJSONArray("attachments");
            if (array == null) {
                return attachments;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String path = item.optString("path");
                if (path.length() == 0) {
                    continue;
                }
                attachments.add(new InputAttachment(
                        item.optString("name"),
                        path,
                        item.optString("source")
                ));
            }
            return attachments;
        }
    }
}
