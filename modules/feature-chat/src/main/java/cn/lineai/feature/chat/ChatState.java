package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.Message;

/**
 * Immutable snapshot rendered by the chat screen.
 *
 * <p>Instances are only replaced, never mutated: {@link ChatController} publishes new snapshots
 * on the main thread and the UI rebinds when {@code revision} changes. Messages are sorted by
 * {@code seq} at construction so a late event can never render out of order.</p>
 */
public final class ChatState {

    /** Transcript ordered by persisted {@code seq}; unmodifiable. */
    public final List<Message> messages;
    /** Tool calls awaiting user approval; unmodifiable. */
    public final List<ToolApprovalItem> pendingApprovals;
    /** True while a model turn is streaming into the timeline. */
    public final boolean streaming;
    public final String projectLabel;
    /** Estimated context usage, clamped to 0..100. */
    public final int contextPercent;
    public final String modelLabel;
    public final String statusText;
    /** Normalized chat permission mode (see {@code ChatMode}). */
    public final String chatMode;
    /** True when a model is configured; drives the "no model" affordances. */
    public final boolean hasModel;
    /** Sub agent owning the viewed conversation; empty when the root conversation is shown. */
    public final String agentId;
    /** True when the viewed conversation belongs to a transient (not messageable) agent. */
    public final boolean transientAgent;
    /** True when a background turn is running in another conversation than the viewed one. */
    public final boolean busyElsewhere;
    /** Monotonic revision stamp; identical values let views skip a rebind. */
    public final long revision;

    /** Sorts the accumulated messages by seq and freezes all lists. */
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
        this.agentId = b.agentId;
        this.transientAgent = b.transientAgent;
        this.busyElsewhere = b.busyElsewhere;
        this.revision = b.revision;
    }

    /** Snapshot with no messages and no pending approvals. */
    public static ChatState empty() {
        return builder().build();
    }

    /** True when the transcript has no messages. */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /** True when tool calls are awaiting user approval. */
    public boolean hasPendingApproval() {
        return !pendingApprovals.isEmpty();
    }

    /** Copy preset carrying every field of this snapshot for incremental updates. */
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
                .agentId(agentId)
                .transientAgent(transientAgent)
                .busyElsewhere(busyElsewhere)
                .revision(revision);
    }

    /** Fresh builder with default field values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Accumulates the snapshot fields; defensive copies keep built states immutable. */
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
        private String agentId = "";
        private boolean transientAgent;
        private boolean busyElsewhere;
        private long revision;

        /** Replaces the transcript; null yields an empty list, otherwise a defensive copy. */
        public Builder messages(List<Message> value) {
            messages = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        /** Appends one message to the transcript; null is ignored. */
        public Builder add(Message message) {
            if (message != null) {
                messages.add(message);
            }
            return this;
        }

        /** Replaces the message with the same id in place, or appends it when the id is unknown. */
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

        /** Sets whether a model turn is streaming. */
        public Builder streaming(boolean value) {
            streaming = value;
            return this;
        }

        /** Replaces the approval batch; null yields an empty list, otherwise a defensive copy. */
        public Builder pendingApprovals(List<ToolApprovalItem> value) {
            pendingApprovals = value == null
                    ? new ArrayList<ToolApprovalItem>() : new ArrayList<>(value);
            return this;
        }

        /** Sets the project label shown in the header; null yields an empty label. */
        public Builder project(String value) {
            projectLabel = value == null ? "" : value;
            return this;
        }

        /** Sets estimated context usage, clamped to 0..100. */
        public Builder contextPercent(int value) {
            contextPercent = Math.max(0, Math.min(100, value));
            return this;
        }

        /** Sets the model display name; null yields an empty label. */
        public Builder model(String value) {
            modelLabel = value == null ? "" : value;
            return this;
        }

        /** Sets the status line text; null yields an empty text. */
        public Builder status(String value) {
            statusText = value == null ? "" : value;
            return this;
        }

        /** Sets the owning sub agent id; empty marks the root conversation view. */
        public Builder agentId(String value) {
            agentId = value == null ? "" : value;
            return this;
        }

        /** Sets whether the viewed conversation belongs to a transient agent. */
        public Builder transientAgent(boolean value) {
            transientAgent = value;
            return this;
        }

        /** Sets whether a background turn owns another conversation right now. */
        public Builder busyElsewhere(boolean value) {
            busyElsewhere = value;
            return this;
        }

        /** Sets and normalizes the chat permission mode. */
        public Builder chatMode(String value) {
            chatMode = cn.lineai.core.model.ChatMode.normalize(value);
            return this;
        }

        /** Sets whether a model is configured. */
        public Builder hasModel(boolean value) {
            hasModel = value;
            return this;
        }

        /** Sets the revision stamp used to skip redundant rebinds. */
        public Builder revision(long value) {
            revision = value;
            return this;
        }

        /** Builds the immutable snapshot, sorting messages by seq. */
        public ChatState build() {
            return new ChatState(this);
        }
    }
}
