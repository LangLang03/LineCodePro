package cn.lineai.mvp;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public final class ChatSessionStore {
    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private String currentConversationId = "";
    private long currentConversationCreatedAt;
    private int messageSequence = 1;
    private int generationSequence = 1;
    private boolean streaming;

    public ArrayList<ChatMessage> mutableMessages() {
        return messages;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public void replaceMessages(List<ChatMessage> nextMessages) {
        messages.clear();
        if (nextMessages != null) {
            messages.addAll(nextMessages);
        }
        resetMessageSequence();
    }

    public void clearMessages() {
        messages.clear();
        resetMessageSequence();
    }

    public String getCurrentConversationId() {
        return currentConversationId;
    }

    public long getCurrentConversationCreatedAt() {
        return currentConversationCreatedAt;
    }

    public void startNewConversation(long now) {
        messages.clear();
        currentConversationId = String.valueOf(now);
        currentConversationCreatedAt = now;
        resetMessageSequence();
    }

    public void clearCurrentConversation() {
        messages.clear();
        currentConversationId = "";
        currentConversationCreatedAt = 0L;
        resetMessageSequence();
    }

    public void ensureCurrentConversation(long now) {
        if (currentConversationId.length() > 0) {
            return;
        }
        currentConversationId = String.valueOf(now);
        currentConversationCreatedAt = now;
    }

    public void applyConversation(ConversationRecord conversation) {
        messages.clear();
        if (conversation != null) {
            for (MessageRecord record : conversation.getMessages()) {
                messages.add(record.toChatMessage());
            }
            currentConversationId = conversation.getId();
            currentConversationCreatedAt = conversation.getCreatedAt();
        }
        resetMessageSequence();
    }

    public String nextMessageId() {
        return "m" + messageSequence++;
    }

    public int nextGenerationId() {
        return generationSequence++;
    }

    public boolean isActiveGeneration(int generationId) {
        return generationId == generationSequence - 1 && streaming;
    }

    public void invalidateActiveGeneration() {
        generationSequence++;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    private void resetMessageSequence() {
        int max = 0;
        for (ChatMessage message : messages) {
            String id = message.getId();
            if (id != null && id.startsWith("m")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        messageSequence = Math.max(max + 1, messages.size() + 1);
    }
}
