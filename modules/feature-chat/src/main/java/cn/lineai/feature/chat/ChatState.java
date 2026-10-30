package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.Message;

/** Immutable snapshot rendered by the chat screen. */
public final class ChatState {

    public final List<Message> messages;
    public final List<ToolApprovalItem> pendingApprovals;
    public final boolean streaming;
    public final String projectLabel;
    public final int contextPercent;
    public final String modelLabel;
    public final String statusText;
    public final String chatMode;
    public final boolean hasModel;
    public final long revision;

    private ChatState(Builder b) {
        List<Message> ordered = new ArrayList<>(b.messages);
        // Sequence is assigned on the single event queue; sorting here is the last line of defence
        // so a late event can never render a tool result above the message that requested it.
        ordered.sort((left, right) -> Long.compare(left.seq, right.seq));
        this.messages = Collections.unmodifiableList(ordered);
        this.pendingApprovals = Collections.unmodifiableList(new ArrayList<>(b.pendingApprovals));
        this.streaming = b.streaming;
        this.projectLabel = b.projectLabel;
        this.contextPercent = b.contextPercent;
        this.modelLabel = b.modelLabel;
        this.statusText = b.statusText;
        this.chatMode = b.chatMode;
        this.hasModel = b.hasModel;
        this.revision = b.revision;
    }

    public static ChatState empty() {
        return builder().build();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public boolean hasPendingApproval() {
        return !pendingApprovals.isEmpty();
    }

    public Builder toBuilder() {
        return new Builder()
                .messages(messages)
                .pendingApprovals(pendingApprovals)
                .streaming(streaming)
                .project(projectLabel)
                .contextPercent(contextPercent)
                .model(modelLabel)
                .status(statusText)
                .chatMode(chatMode)
                .hasModel(hasModel)
                .revision(revision);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Message> messages = new ArrayList<>();
        private List<ToolApprovalItem> pendingApprovals = new ArrayList<>();
        private boolean streaming;
        private String projectLabel = "";
        private int contextPercent;
        private String modelLabel = "";
        private String statusText = "";
        private String chatMode = cn.lineai.core.model.ChatMode.AGENT;
        private boolean hasModel;
        private long revision;

        public Builder messages(List<Message> value) {
            messages = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        public Builder add(Message message) {
            if (message != null) {
                messages.add(message);
            }
            return this;
        }

        public Builder replace(Message message) {
            if (message == null) {
                return this;
            }
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).id.equals(message.id)) {
                    messages.set(i, message);
                    return this;
                }
            }
            messages.add(message);
            return this;
        }

        public Builder streaming(boolean value) {
            streaming = value;
            return this;
        }

        public Builder pendingApprovals(List<ToolApprovalItem> value) {
            pendingApprovals = value == null
                    ? new ArrayList<ToolApprovalItem>() : new ArrayList<>(value);
            return this;
        }

        public Builder project(String value) {
            projectLabel = value == null ? "" : value;
            return this;
        }

        public Builder contextPercent(int value) {
            contextPercent = Math.max(0, Math.min(100, value));
            return this;
        }

        public Builder model(String value) {
            modelLabel = value == null ? "" : value;
            return this;
        }

        public Builder status(String value) {
            statusText = value == null ? "" : value;
            return this;
        }

        public Builder chatMode(String value) {
            chatMode = cn.lineai.core.model.ChatMode.normalize(value);
            return this;
        }

        public Builder hasModel(boolean value) {
            hasModel = value;
            return this;
        }

        public Builder revision(long value) {
            revision = value;
            return this;
        }

        public ChatState build() {
            return new ChatState(this);
        }
    }
}
