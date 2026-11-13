package cn.lineai.engine.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.Message;

/**
 * Everything needed to build one completion request.
 *
 * <p>Immutable by construction: the builder copies the message and tool lists, so later mutation
 * of the caller's lists cannot change an in-flight request.</p>
 */
public final class ChatRequest {

    /** Model identifier the request targets. */
    public final String modelId;
    /** System prompt emitted ahead of the conversation; empty when absent. */
    public final String systemPrompt;
    /** Conversation history in order; unmodifiable. */
    public final List<Message> messages;
    /** Sampling temperature. */
    public final float temperature;
    /** Maximum completion tokens the provider may generate. */
    public final int maxTokens;
    /** Whether the transport should stream the response. */
    public final boolean stream;
    /** Requested reasoning behaviour; null when the request makes no reasoning choice. */
    public final ReasoningSpec reasoning;
    /** Tool schemas offered to the model; unmodifiable. */
    public final List<ToolSchema> tools;

    private ChatRequest(Builder b) {
        this.modelId = b.modelId;
        this.systemPrompt = b.systemPrompt;
        this.messages = Collections.unmodifiableList(new ArrayList<>(b.messages));
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.stream = b.stream;
        this.reasoning = b.reasoning;
        this.tools = Collections.unmodifiableList(new ArrayList<>(b.tools));
    }

    /** Returns a fresh builder with the documented defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ChatRequest}; defaults are listed on each setter. */
    public static final class Builder {
        private String modelId = "";
        private String systemPrompt = "";
        private List<Message> messages = new ArrayList<>();
        private float temperature = 0.7f;
        private int maxTokens = 4096;
        private boolean stream = true;
        private ReasoningSpec reasoning;
        private List<ToolSchema> tools = new ArrayList<>();

        /** Sets the target model; default is the empty string.
         *
         * @param value model identifier, null normalised to empty
         * @return this builder
         */
        public Builder model(String value) {
            modelId = value == null ? "" : value;
            return this;
        }

        /** Sets the system prompt; default is empty.
         *
         * @param value system prompt text, null normalised to empty
         * @return this builder
         */
        public Builder system(String value) {
            systemPrompt = value == null ? "" : value;
            return this;
        }

        /** Replaces the conversation history; default is empty.
         *
         * @param value messages in order, copied defensively; null normalised to empty
         * @return this builder
         */
        public Builder messages(List<Message> value) {
            messages = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        /** Sets sampling parameters; defaults are temperature 0.7 and 4096 max tokens.
         *
         * @param value sampling temperature
         * @param limit maximum completion tokens
         * @return this builder
         */
        public Builder sampling(float value, int limit) {
            temperature = value;
            maxTokens = limit;
            return this;
        }

        /** Sets whether to stream; default is true.
         *
         * @param value true to request a streamed response
         * @return this builder
         */
        public Builder stream(boolean value) {
            stream = value;
            return this;
        }

        /** Sets the reasoning behaviour; default is none.
         *
         * @param value reasoning specification, may be null
         * @return this builder
         */
        public Builder reasoning(ReasoningSpec value) {
            reasoning = value;
            return this;
        }

        /** Replaces the offered tools; default is empty.
         *
         * @param value tool schemas, copied defensively; null normalised to empty
         * @return this builder
         */
        public Builder tools(List<ToolSchema> value) {
            tools = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        /** Builds the immutable request. */
        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
