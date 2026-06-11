package cn.lineai.data.repository;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.tool.ToolCall;
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

    public ChatMessage toChatMessage() {
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                readToolCalls(rawJson), new ArrayList<>(), toolCallId, toolName, error,
                readString(rawJson, "diff_id"),
                readString(rawJson, "review_state"),
                readString(rawJson, "review_message"),
                readString(rawJson, "compact_status"),
                readString(rawJson, "response_input_item_json"),
                readAttachments(rawJson));
    }

    private ArrayList<ToolCall> readToolCalls(String rawJson) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        if (rawJson == null || rawJson.trim().length() == 0) {
            return calls;
        }
        try {
            JSONObject object = new JSONObject(rawJson);
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
        } catch (Exception ignored) {
        }
        return calls;
    }

    private String readString(String rawJson, String key) {
        if (rawJson == null || rawJson.trim().length() == 0) {
            return "";
        }
        try {
            return new JSONObject(rawJson).optString(key);
        } catch (Exception ignored) {
            return "";
        }
    }

    private ArrayList<InputAttachment> readAttachments(String rawJson) {
        ArrayList<InputAttachment> attachments = new ArrayList<>();
        if (rawJson == null || rawJson.trim().length() == 0) {
            return attachments;
        }
        try {
            JSONObject object = new JSONObject(rawJson);
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
        } catch (Exception ignored) {
        }
        return attachments;
    }
}
