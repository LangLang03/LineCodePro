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
        if (currentConversationId.length() == 0) {
            return "m" + System.currentTimeMillis() + "_" + (messageSequence++);
        }
        return currentConversationId + "_m" + (messageSequence++);
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
            max = Math.max(max, sequenceFromMessageId(message == null ? null : message.getId()));
        }
        messageSequence = Math.max(max + 1, messages.size() + 1);
    }

    static int sequenceFromMessageId(String id) {
        if (id == null || id.length() == 0) {
            return 0;
        }
        int underscore = id.lastIndexOf("_m");
        if (underscore >= 0 && underscore + 2 < id.length()) {
            return parseTrailingDigits(id.substring(underscore + 2));
        }
        int colon = id.lastIndexOf(":m");
        if (colon >= 0 && colon + 2 < id.length()) {
            return parseTrailingDigits(id.substring(colon + 2));
        }
        if (id.charAt(0) == 'm') {
            return parseTrailingDigits(id.substring(1));
        }
        return 0;
    }

    private static int parseTrailingDigits(String value) {
        if (value == null || value.length() == 0) {
            return 0;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
