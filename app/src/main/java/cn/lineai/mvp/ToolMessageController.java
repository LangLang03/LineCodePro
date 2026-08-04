package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class ToolMessageController {
    interface IdProvider {
        String nextId();
    }

    private final ArrayList<ChatMessage> messages;
    private final IdProvider idProvider;
    private final ToolRegistry toolRegistry;

    ToolMessageController(ArrayList<ChatMessage> messages, IdProvider idProvider) {
        this(messages, idProvider, null);
    }

    ToolMessageController(ArrayList<ChatMessage> messages, IdProvider idProvider, ToolRegistry toolRegistry) {
        this.messages = messages;
        this.idProvider = idProvider;
        this.toolRegistry = toolRegistry;
    }

    void addOrReplaceToolResults(List<ToolResult> results) {
        if (results == null) {
            return;
        }
        for (ToolResult result : results) {
            addOrReplaceToolResult(result);
        }
    }

    void addOrReplaceToolResult(ToolResult result) {
        if (result == null || result.getToolCallId().length() == 0) {
            return;
        }
        int index = findToolMessageIndex(result.getToolCallId());
        String messageId = index >= 0 ? messages.get(index).getId() : idProvider.nextId();
        String content = result.getContent();
        if (content != null && content.length() > ToolResult.MAX_TOOL_RESULT_CHARS) {
            content = ToolResult.truncateContent(content);
        }
        ChatMessage message = ChatMessage.toolResult(
                messageId,
                content,
                result.getToolCallId(),
                result.getToolName(),
                result.isError(),
                result.getDiffId(),
                result.getReviewState(),
                result.getReviewMessage()
        );
        if (index >= 0) {
            messages.set(index, message);
        } else {
            messages.add(message);
        }
        appendInlineToolResultToAssistant(result);
    }

    private void appendInlineToolResultToAssistant(ToolResult result) {
        if (!isFinalSuccessfulImageGenerationResult(result)) {
            return;
        }
        String markdown = imageGenerationDisplayMarkdown(result.getContent());
        if (markdown.length() == 0) {
            return;
        }
        int assistantIndex = findAssistantMessageIndexForToolCall(result.getToolCallId());
        if (assistantIndex < 0) {
            return;
        }
        ChatMessage assistant = messages.get(assistantIndex);
        if (assistant.getContent().contains(markdown)) {
            return;
        }
        String current = assistant.getContent().trim();
        String nextContent = current.length() == 0 ? markdown : current + "\n\n" + markdown;
        messages.set(assistantIndex, assistant.withContent(nextContent, assistant.getReasoningContent(), assistant.isStreaming()));
    }

    private boolean shouldHideOnSuccess(ToolResult result) {
        if (result == null || result.isError() || result.getContent().trim().length() == 0 || result.getReviewState().length() > 0) {
            return false;
        }
        if (toolRegistry != null) {
            BaseTool tool = toolRegistry.get(result.getToolName());
            return tool != null && tool.shouldHideOnSuccess();
        }
        return false;
    }

    private boolean isFinalSuccessfulImageGenerationResult(ToolResult result) {
        return shouldHideOnSuccess(result);
    }

    private String imageGenerationDisplayMarkdown(String content) {
        return MessageContentSanitizer.imageGenerationDisplayMarkdown(content);
    }

    String currentReviewState(String toolCallId) {
        int index = findToolMessageIndex(toolCallId);
        if (index < 0) {
            return "";
        }
        return messages.get(index).getReviewState();
    }

    int findToolMessageIndex(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                return i;
            }
        }
        return -1;
    }

    private int findAssistantMessageIndexForToolCall(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (ToolCall call : message.getToolCalls()) {
                if (toolCallId.equals(call.getId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    String findToolMessageDiffId(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return "";
        }
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.TOOL
                    && toolCallId.equals(message.getToolCallId())
                    && message.getDiffId().length() > 0) {
                return message.getDiffId();
            }
            if (message.getRole() == ChatMessage.Role.TOOL) {
                String nestedDiffId = findNestedToolDiffId(message.getContent(), toolCallId);
                if (nestedDiffId.length() > 0) {
                    return nestedDiffId;
                }
            }
        }
        return "";
    }

    private String findNestedToolDiffId(String content, String toolCallId) {
        if (content == null || content.trim().length() == 0) {
            return "";
        }
        try {
            return findNestedToolDiffId(new JSONObject(content), toolCallId);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String findNestedToolDiffId(JSONObject object, String toolCallId) {
        if (object == null) {
            return "";
        }
        String diffId = findToolCallArrayDiffId(object.optJSONArray("tool_calls"), toolCallId);
        if (diffId.length() > 0) {
            return diffId;
        }
        JSONArray agents = object.optJSONArray("agents");
        if (agents == null) {
            return "";
        }
        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            diffId = findToolCallArrayDiffId(agent == null ? null : agent.optJSONArray("tool_calls"), toolCallId);
            if (diffId.length() > 0) {
                return diffId;
            }
        }
        return "";
    }

    private String findToolCallArrayDiffId(JSONArray calls, String toolCallId) {
        if (calls == null) {
            return "";
        }
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (toolCallId.equals(item.optString("id"))) {
                JSONObject result = item.optJSONObject("result");
                return result == null ? "" : result.optString("diff_id");
            }
            String diffId = findNestedToolDiffId(item, toolCallId);
            if (diffId.length() > 0) {
                return diffId;
            }
        }
        return "";
    }

    private static final String REJECTED_MODEL_CONTENT =
            "User rejected this change and the file was reverted. "
                    + "The previous tool success is void; re-read the file before editing again.";

    void updateToolReview(String toolCallId, String diffId, String reviewState, String reviewMessage) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        boolean rejected = "rejected".equals(reviewState);
        String modelContent = rejectedModelContent(reviewMessage);
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            ChatMessage next = message;
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                String resolvedDiffId = diffId == null || diffId.length() == 0 ? message.getDiffId() : diffId;
                if (rejected) {
                    next = next.withToolReview(
                            resolvedDiffId,
                            reviewState,
                            reviewMessage,
                            true,
                            modelContent
                    );
                } else {
                    next = next.withToolReview(resolvedDiffId, reviewState, reviewMessage);
                }
            }
            if (message.getRole() == ChatMessage.Role.TOOL) {
                String updatedContent = updateNestedToolReviewContent(
                        next.getContent(),
                        toolCallId,
                        diffId,
                        reviewState,
                        reviewMessage,
                        modelContent
                );
                if (!updatedContent.equals(next.getContent())) {
                    next = next.withContent(updatedContent, next.getReasoningContent(), next.isStreaming());
                }
            }
            if (next != message) {
                messages.set(i, next);
            }
        }
    }

    private static String rejectedModelContent(String reviewMessage) {
        if (reviewMessage != null && reviewMessage.trim().length() > 0) {
            return REJECTED_MODEL_CONTENT + " " + reviewMessage.trim();
        }
        return REJECTED_MODEL_CONTENT;
    }

    void addTerminatedResultsForUnfinishedToolCalls(String terminatedMessage) {
        String terminatedContent = terminatedMessage == null || terminatedMessage.trim().length() == 0
                ? "工具执行已终止。"
                : terminatedMessage;
        ArrayList<ToolResult> results = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (ToolCall call : message.getToolCalls()) {
                if (call == null || call.getId().length() == 0) {
                    continue;
                }
                int resultIndex = findToolMessageIndex(call.getId());
                if (resultIndex < 0 || isUnfinishedToolMessage(messages.get(resultIndex))) {
                    results.add(ToolResult.of(call.getId(), call.getName(), terminatedContent, true));
                }
            }
        }
        addOrReplaceToolResults(results);
    }

    private boolean isUnfinishedToolMessage(ChatMessage message) {
        if (message == null || message.getRole() != ChatMessage.Role.TOOL) {
            return true;
        }
        String state = message.getReviewState();
        if ("running".equals(state) || "pending".equals(state)) {
            return true;
        }
        return "accepted".equals(state) && message.getContent().trim().length() == 0;
    }

    private String updateNestedToolReviewContent(
            String content,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage,
            String modelContent
    ) {
        if (content == null || content.trim().length() == 0) {
            return content == null ? "" : content;
        }
        try {
            JSONObject object = new JSONObject(content);
            return updateNestedToolReview(object, toolCallId, diffId, reviewState, reviewMessage, modelContent)
                    ? object.toString()
                    : content;
        } catch (Exception ignored) {
            return content;
        }
    }

    private boolean updateNestedToolReview(
            JSONObject object,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage,
            String modelContent
    ) throws Exception {
        if (object == null) {
            return false;
        }
        boolean changed = updateToolCallArrayReview(
                object.optJSONArray("tool_calls"),
                toolCallId,
                diffId,
                reviewState,
                reviewMessage,
                modelContent
        );
        JSONArray agents = object.optJSONArray("agents");
        if (agents != null) {
            for (int i = 0; i < agents.length(); i++) {
                JSONObject agent = agents.optJSONObject(i);
                if (agent == null) {
                    continue;
                }
                changed = updateToolCallArrayReview(
                        agent.optJSONArray("tool_calls"),
                        toolCallId,
                        diffId,
                        reviewState,
                        reviewMessage,
                        modelContent
                ) || changed;
            }
        }
        return changed;
    }

    private boolean updateToolCallArrayReview(
            JSONArray calls,
            String toolCallId,
            String diffId,
            String reviewState,
            String reviewMessage,
            String modelContent
    ) throws Exception {
        if (calls == null) {
            return false;
        }
        boolean changed = false;
        boolean rejected = "rejected".equals(reviewState);
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (toolCallId.equals(item.optString("id"))) {
                JSONObject result = item.optJSONObject("result");
                if (result == null) {
                    result = new JSONObject();
                    item.put("result", result);
                }
                String resolvedDiffId = diffId == null || diffId.length() == 0
                        ? result.optString("diff_id")
                        : diffId;
                result.put("diff_id", resolvedDiffId == null ? "" : resolvedDiffId);
                result.put("review_state", reviewState == null ? "" : reviewState);
                result.put("review_message", reviewMessage == null ? "" : reviewMessage);
                if (rejected) {
                    result.put("is_error", true);
                    result.put("content", modelContent == null ? REJECTED_MODEL_CONTENT : modelContent);
                }
                changed = true;
            }
            changed = updateNestedToolReview(item, toolCallId, diffId, reviewState, reviewMessage, modelContent) || changed;
        }
        return changed;
    }
}
