package cn.lineai.context;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.MessageContentSanitizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContextManager {
    private static final int CHARS_PER_TOKEN = 4;
    private static final int MESSAGE_OVERHEAD_TOKENS = 8;
    private static final int DEFAULT_RESERVE_TOKENS = 2048;
    private static final int MIN_CONTEXT_TOKENS = 4096;

    public ContextSnapshot snapshot(List<ChatMessage> messages, int contextTokens) {
        return snapshot(messages, contextTokens, true);
    }

    public ContextSnapshot snapshot(List<ChatMessage> messages, int contextTokens, boolean includeReasoning) {
        return new ContextSnapshot(estimateTokens(messages, includeReasoning), contextTokens);
    }

    public int estimateTokens(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN));
    }

    public int estimateTokens(ChatMessage message) {
        return estimateTokens(message, true);
    }

    public int estimateTokens(ChatMessage message, boolean includeReasoning) {
        if (message == null || message.isExcludeFromContext()) {
            return 0;
        }
        return MESSAGE_OVERHEAD_TOKENS
                + estimateTokens(effectiveContent(message))
                + estimateAttachments(message)
                + (includeReasoning ? estimateTokens(message.getReasoningContent()) : 0)
                + estimateToolCalls(message);
    }

    public int estimateTokens(List<ChatMessage> messages) {
        return estimateTokens(messages, true);
    }

    public int estimateTokens(List<ChatMessage> messages, boolean includeReasoning) {
        int total = 0;
        if (messages == null) {
            return total;
        }
        for (ChatMessage message : messages) {
            total += estimateTokens(message, includeReasoning);
        }
        return total;
    }

    public List<ChatMessage> selectWindow(List<ChatMessage> messages, int contextTokens, int reservedTokens) {
        return selectWindow(messages, contextTokens, reservedTokens, true);
    }

    public List<ChatMessage> selectWindow(List<ChatMessage> messages, int contextTokens, int reservedTokens, boolean includeReasoning) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        int safeContext = Math.max(MIN_CONTEXT_TOKENS, contextTokens);
        int budget = Math.max(512, safeContext - Math.max(DEFAULT_RESERVE_TOKENS, reservedTokens));
        ArrayList<ChatMessage> selected = new ArrayList<>();
        int used = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.isExcludeFromContext()
                    || (message.getContent().trim().length() == 0
                    && message.getReasoningContent().trim().length() == 0
                    && !message.hasToolCalls())) {
                continue;
            }
            int cost = estimateTokens(message, includeReasoning);
            if (!selected.isEmpty() && used + cost > budget) {
                break;
            }
            selected.add(message);
            used += cost;
        }

        Collections.reverse(selected);
        return selected;
    }

    private int estimateToolCalls(ChatMessage message) {
        if (message == null || !message.hasToolCalls()) {
            return 0;
        }
        int total = 0;
        for (cn.lineai.tool.ToolCall call : message.getToolCalls()) {
            total += estimateTokens(call.getName()) + estimateTokens(call.getArguments());
        }
        return total;
    }

    private int estimateAttachments(ChatMessage message) {
        if (message == null || !message.hasAttachments()) {
            return 0;
        }
        int total = 0;
        for (InputAttachment attachment : message.getAttachments()) {
            total += estimateTokens(attachment.getName())
                    + estimateTokens(attachment.getSource())
                    + estimateTokens(attachment.getPath());
        }
        return total;
    }

    private String effectiveContent(ChatMessage message) {
        return MessageContentSanitizer.forModel(message);
    }
}
