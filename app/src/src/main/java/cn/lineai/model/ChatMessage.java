package cn.lineai.model;

import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatMessage {
    public static final String COMPACT_STATUS_RUNNING = "running";
    public static final String COMPACT_STATUS_DONE = "done";
    public static final String COMPACT_STATUS_ERROR = "error";

    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String protocolName;

        Role(String protocolName) {
            this.protocolName = protocolName;
        }

        public String getProtocolName() {
            return protocolName;
        }
    }

    private final String id;
    private final Role role;
    private final String content;
    private final String reasoningContent;
    private final boolean streaming;
    private final boolean hidden;
    private final boolean excludeFromContext;
    private final List<ToolCall> toolCalls;
    private final List<ToolResult> toolResults;
    private final String toolCallId;
    private final String toolName;
    private final boolean error;
    private final String diffId;
    private final String reviewState;
    private final String reviewMessage;
    private final String compactStatus;
    private final String responseInputItemJson;
    private final List<InputAttachment> attachments;
    private final String modelSwitchNotification;

    public ChatMessage(String id, Role role, String content, boolean streaming) {
        this(id, role, content, "", streaming, false, false);
    }

    public ChatMessage(String id, Role role, String content, boolean streaming, List<InputAttachment> attachments) {
        this(id, role, content, "", streaming, false, false,
                Collections.emptyList(), Collections.emptyList(), "", "", false, "", "", "", "", "", attachments);
    }

    public ChatMessage(String id, Role role, String content, String reasoningContent, boolean streaming) {
        this(id, role, content, reasoningContent, streaming, false, false);
    }

    public ChatMessage(
            String id,
            Role role,
            String content,
            String reasoningContent,
            boolean streaming,
            boolean hidden,
            boolean excludeFromContext
    ) {
        this(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                Collections.emptyList(), Collections.emptyList(), "", "", false, "", "", "");
    }

    public ChatMessage(
            String id,
            Role role,
            String content,
            String reasoningContent,
            boolean streaming,
            boolean hidden,
            boolean excludeFromContext,
            List<ToolCall> toolCalls,
            List<ToolResult> toolResults,
            String toolCallId,
            String toolName,
            boolean error
    ) {
        this(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                toolCalls, toolResults, toolCallId, toolName, error, "", "", "");
    }

    public ChatMessage(
            String id,
            Role role,
            String content,
            String reasoningContent,
            boolean streaming,
            boolean hidden,
            boolean excludeFromContext,
            List<ToolCall> toolCalls,
            List<ToolResult> toolResults,
            String toolCallId,
            String toolName,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        this(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                toolCalls, toolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage, "", "");
    }

    public ChatMessage(
            String id,
            Role role,
            String content,
            String reasoningContent,
            boolean streaming,
            boolean hidden,
            boolean excludeFromContext,
            List<ToolCall> toolCalls,
            List<ToolResult> toolResults,
            String toolCallId,
            String toolName,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage,
            String compactStatus,
            String responseInputItemJson
    ) {
        this(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                toolCalls, toolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage,
                compactStatus, responseInputItemJson, Collections.emptyList());
    }

    public ChatMessage(
            String id, Role role, String content, String reasoningContent,
            boolean streaming, boolean hidden, boolean excludeFromContext,
            List<ToolCall> toolCalls, List<ToolResult> toolResults,
            String toolCallId, String toolName, boolean error,
            String diffId, String reviewState, String reviewMessage,
            String compactStatus, String responseInputItemJson,
            List<InputAttachment> attachments
    ) {
        this(id, role, content, reasoningContent, streaming, hidden, excludeFromContext,
                toolCalls, toolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage,
                compactStatus, responseInputItemJson, attachments, "");
    }

    public ChatMessage(
            String id, Role role, String content, String reasoningContent,
            boolean streaming, boolean hidden, boolean excludeFromContext,
            List<ToolCall> toolCalls, List<ToolResult> toolResults,
            String toolCallId, String toolName, boolean error,
            String diffId, String reviewState, String reviewMessage,
            String compactStatus, String responseInputItemJson,
            List<InputAttachment> attachments,
            String modelSwitchNotification
    ) {
        this.id = id;
        this.role = role == null ? Role.USER : role;
        this.content = content == null ? "" : content;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.streaming = streaming;
        this.hidden = hidden;
        this.excludeFromContext = excludeFromContext;
        this.toolCalls = toolCalls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.toolResults = toolResults == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(toolResults));
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.error = error;
        this.diffId = diffId == null ? "" : diffId;
        this.reviewState = reviewState == null ? "" : reviewState;
        this.reviewMessage = reviewMessage == null ? "" : reviewMessage;
        this.compactStatus = normalizeCompactStatus(compactStatus);
        this.responseInputItemJson = responseInputItemJson == null ? "" : responseInputItemJson.trim();
        this.attachments = attachments == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
        this.modelSwitchNotification = modelSwitchNotification == null ? "" : modelSwitchNotification;
    }

    public String getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
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

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public ToolResult getToolResult(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        for (ToolResult result : toolResults) {
            if (id.equals(result.getToolCallId())) {
                return result;
            }
        }
        return null;
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

    public String getDiffId() {
        return diffId;
    }

    public String getReviewState() {
        return reviewState;
    }

    public String getReviewMessage() {
        return reviewMessage;
    }

    public String getCompactStatus() {
        return compactStatus;
    }

    public boolean isCompactBlock() {
        return compactStatus.length() > 0;
    }

    public String getResponseInputItemJson() {
        return responseInputItemJson;
    }

    public List<InputAttachment> getAttachments() {
        return attachments;
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public String getModelSwitchNotification() {
        return modelSwitchNotification;
    }

    public boolean isModelSwitchNotification() {
        return modelSwitchNotification.length() > 0;
    }

    public String getProtocolRole() {
        return role.getProtocolName();
    }

    public ChatMessage withContent(String nextContent, String nextReasoningContent, boolean nextStreaming) {
        return new ChatMessage(id, role, nextContent, nextReasoningContent, nextStreaming, hidden,
                excludeFromContext, toolCalls, toolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage,
                compactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withToolCalls(List<ToolCall> nextToolCalls, boolean nextHidden) {
        return new ChatMessage(id, role, content, reasoningContent, streaming, nextHidden,
                excludeFromContext, nextToolCalls, toolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage,
                compactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withToolResults(List<ToolResult> nextToolResults) {
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden,
                excludeFromContext, toolCalls, nextToolResults, toolCallId, toolName, error, diffId, reviewState, reviewMessage,
                compactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withToolReview(String nextDiffId, String nextReviewState, String nextReviewMessage) {
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden,
                excludeFromContext, toolCalls, toolResults, toolCallId, toolName, error,
                nextDiffId, nextReviewState, nextReviewMessage, compactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withExcludeFromContext(boolean nextExcludeFromContext) {
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden,
                nextExcludeFromContext, toolCalls, toolResults, toolCallId, toolName, error,
                diffId, reviewState, reviewMessage, compactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withCompactStatus(String nextCompactStatus, boolean nextStreaming) {
        return new ChatMessage(id, role, content, reasoningContent, nextStreaming, hidden,
                excludeFromContext, toolCalls, toolResults, toolCallId, toolName, error,
                diffId, reviewState, reviewMessage, nextCompactStatus, responseInputItemJson, attachments);
    }

    public ChatMessage withResponseInputItemJson(String nextResponseInputItemJson) {
        return new ChatMessage(id, role, content, reasoningContent, streaming, hidden,
                excludeFromContext, toolCalls, toolResults, toolCallId, toolName, error,
                diffId, reviewState, reviewMessage, compactStatus, nextResponseInputItemJson, attachments);
    }

    public static ChatMessage toolResult(String id, String content, String toolCallId, String toolName, boolean error) {
        return toolResult(id, content, toolCallId, toolName, error, "", "", "");
    }

    public static ChatMessage toolResult(
            String id,
            String content,
            String toolCallId,
            String toolName,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        return new ChatMessage(id, Role.TOOL, content, "", false, true, false,
                Collections.emptyList(), Collections.emptyList(), toolCallId, toolName, error,
                diffId, reviewState, reviewMessage);
    }

    public static ChatMessage compactProgress(String id, String status) {
        return new ChatMessage(id, Role.ASSISTANT, "", "", COMPACT_STATUS_RUNNING.equals(status),
                false, true, Collections.emptyList(), Collections.emptyList(), "", "", false,
                "", "", "", status, "");
    }

    public static ChatMessage modelSwitchNotice(String id, String fromModel, String toModel) {
        String content = "\u6a21\u578b\u5df2\u4ece " + fromModel + " \u66f4\u6539\u4e3a " + toModel + "\u3002";
        return new ChatMessage(id, Role.ASSISTANT, "", "", false, false, true,
                Collections.emptyList(), Collections.emptyList(), "", "", false,
                "", "", "", "", "", Collections.emptyList(), content);
    }

    private String normalizeCompactStatus(String value) {
        if (COMPACT_STATUS_RUNNING.equals(value)
                || COMPACT_STATUS_DONE.equals(value)
                || COMPACT_STATUS_ERROR.equals(value)) {
            return value;
        }
        return "";
    }
}
