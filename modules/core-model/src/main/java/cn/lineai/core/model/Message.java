package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable conversation message.
 *
 * <p>One object, one concern: it is the domain model. It is deliberately not the wire format,
 * not the database row and not the render unit — those live in {@code engine-protocol},
 * {@code data} and {@code feature-chat} respectively.</p>
 */
public final class Message {

    /** Delivery lifecycle of a message within the transcript. */
    public enum Status {
        PENDING,
        STREAMING,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    /** Controls whether a message renders in the transcript. */
    public enum Visibility {
        VISIBLE,
        HIDDEN
    }

    public final String id;
    public final String conversationId;
    public final long seq;
    public final Role role;
    public final Status status;
    public final Visibility visibility;
    public final List<ContentBlock> blocks;
    public final String modelId;
    public final String providerId;
    public final String toolCallId;
    public final String toolName;
    public final List<ToolCallRecord> toolCalls;
    public final String errorCode;
    public final String finishReason;
    public final String reviewState;
    public final int inputTokens;
    public final int outputTokens;
    public final int cachedTokens;
    public final long createdAt;
    public final long updatedAt;

    /** Creates a message from builder state; lists become unmodifiable and null strings empty. */
    private Message(Builder b) {
        this.id = b.id;
        this.conversationId = b.conversationId;
        this.seq = b.seq;
        this.role = b.role;
        this.status = b.status;
        this.visibility = b.visibility;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(b.blocks));
        this.modelId = orEmpty(b.modelId);
        this.providerId = orEmpty(b.providerId);
        this.toolCallId = orEmpty(b.toolCallId);
        this.toolName = orEmpty(b.toolName);
        this.toolCalls = Collections.unmodifiableList(new ArrayList<>(
                b.toolCalls == null ? Collections.<ToolCallRecord>emptyList() : b.toolCalls));
        this.errorCode = orEmpty(b.errorCode);
        this.finishReason = orEmpty(b.finishReason);
        this.reviewState = orEmpty(b.reviewState);
        this.inputTokens = b.inputTokens;
        this.outputTokens = b.outputTokens;
        this.cachedTokens = b.cachedTokens;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    /** Returns {@code value}, or an empty string when null. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Concatenated prose blocks; used for copy, search and transcript building. */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block.type == ContentBlock.Type.TEXT) {
                sb.append(block.text);
            }
        }
        return sb.toString();
    }

    /** Prose plus attachment contents; used for model requests, not for the bubble. */
    public String wireText() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block.type == ContentBlock.Type.TEXT) {
                sb.append(block.text);
            } else if (block.type == ContentBlock.Type.FILE) {
                String name = new java.io.File(block.refId).getName();
                sb.append("\n\n--- Attachment: ").append(name).append(" ---\n")
                        .append(block.text);
            }
        }
        return sb.toString();
    }

    /** Concatenated reasoning blocks; kept separate so reasoning can be collapsed in the UI. */
    public String reasoningText() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block.type == ContentBlock.Type.REASONING) {
                sb.append(block.text);
            }
        }
        return sb.toString();
    }

    /** Returns whether the message is currently being streamed. */
    public boolean isStreaming() {
        return status == Status.STREAMING;
    }

    /** Returns a builder pre-filled with every field of this message. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .conversationId(conversationId)
                .seq(seq)
                .role(role)
                .status(status)
                .visibility(visibility)
                .blocks(blocks)
                .model(modelId, providerId)
                .tool(toolCallId, toolName)
                .toolCalls(toolCalls)
                .error(errorCode)
                .finishReason(finishReason)
                .reviewState(reviewState)
                .tokens(inputTokens, outputTokens, cachedTokens)
                .times(createdAt, updatedAt);
    }

    /** Messages are equal when their identifiers match. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message)) {
            return false;
        }
        return id.equals(((Message) other).id);
    }

    /** Hashes the identifier. */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Returns a new builder with the documented defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; the only way to construct a {@link Message}. */
    public static final class Builder {
        private String id = "";
        private String conversationId = "";
        private long seq;
        private Role role = Role.USER;
        private Status status = Status.COMPLETE;
        private Visibility visibility = Visibility.VISIBLE;
        private List<ContentBlock> blocks = new ArrayList<>();
        private String modelId = "";
        private String providerId = "";
        private String toolCallId = "";
        private String toolName = "";
        private List<ToolCallRecord> toolCalls = new ArrayList<>();
        private String errorCode = "";
        private String finishReason = "";
        private String reviewState = "";
        private int inputTokens;
        private int outputTokens;
        private int cachedTokens;
        private long createdAt;
        private long updatedAt;

        /**
         * Sets the identifier.
         * @param value identifier to assign
         * @return this builder
         */
        public Builder id(String value) {
            id = value;
            return this;
        }

        /**
         * Sets the owning conversation.
         * @param value conversation identifier to assign
         * @return this builder
         */
        public Builder conversationId(String value) {
            conversationId = value;
            return this;
        }

        /**
         * Sets the order within the conversation.
         * @param value sequence number to assign
         * @return this builder
         */
        public Builder seq(long value) {
            seq = value;
            return this;
        }

        /**
         * Sets the message role.
         * @param value role to assign
         * @return this builder
         */
        public Builder role(Role value) {
            role = value;
            return this;
        }

        /**
         * Sets the tool calls to remember for protocol replay.
         * @param value records to copy, null yields an empty list
         * @return this builder
         */
        public Builder toolCalls(List<ToolCallRecord> value) {
            toolCalls = value == null ? new ArrayList<ToolCallRecord>() : new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the delivery status.
         * @param value status to assign
         * @return this builder
         */
        public Builder status(Status value) {
            status = value;
            return this;
        }

        /**
         * Sets the transcript visibility.
         * @param value visibility to assign
         * @return this builder
         */
        public Builder visibility(Visibility value) {
            visibility = value;
            return this;
        }

        /**
         * Replaces the content blocks.
         * @param value blocks to copy, null yields an empty list
         * @return this builder
         */
        public Builder blocks(List<ContentBlock> value) {
            blocks = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        /**
         * Appends one content block.
         * @param block block to append; null calls are ignored
         * @return this builder
         */
        public Builder addBlock(ContentBlock block) {
            if (block != null) {
                blocks.add(block);
            }
            return this;
        }

        /**
         * Sets the model and provider that produced this message.
         * @param modelIdValue model identifier
         * @param providerIdValue provider identifier
         * @return this builder
         */
        public Builder model(String modelIdValue, String providerIdValue) {
            modelId = modelIdValue;
            providerId = providerIdValue;
            return this;
        }

        /**
         * Sets the single tool-call identity carried by protocol messages.
         * @param callId provider tool-call identifier
         * @param name tool name
         * @return this builder
         */
        public Builder tool(String callId, String name) {
            toolCallId = callId;
            toolName = name;
            return this;
        }

        /**
         * Sets the failure code.
         * @param code error code, null becomes empty
         * @return this builder
         */
        public Builder error(String code) {
            errorCode = code;
            return this;
        }

        /**
         * Sets the provider finish reason.
         * @param value finish reason, null becomes empty
         * @return this builder
         */
        public Builder finishReason(String value) {
            finishReason = value;
            return this;
        }

        /**
         * Sets the review workflow state.
         * @param value review state, null becomes empty
         * @return this builder
         */
        public Builder reviewState(String value) {
            reviewState = value;
            return this;
        }

        /**
         * Sets the token accounting.
         * @param input prompt tokens
         * @param output completion tokens
         * @param cached tokens served from cache
         * @return this builder
         */
        public Builder tokens(int input, int output, int cached) {
            inputTokens = input;
            outputTokens = output;
            cachedTokens = cached;
            return this;
        }

        /**
         * Sets the lifecycle timestamps.
         * @param created creation timestamp in epoch milliseconds
         * @param updated last modification timestamp in epoch milliseconds
         * @return this builder
         */
        public Builder times(long created, long updated) {
            createdAt = created;
            updatedAt = updated;
            return this;
        }

        /**
         * Returns the immutable message accumulated by this builder.
         * @return a new {@link Message}
         */
        public Message build() {
            return new Message(this);
        }
    }
}
