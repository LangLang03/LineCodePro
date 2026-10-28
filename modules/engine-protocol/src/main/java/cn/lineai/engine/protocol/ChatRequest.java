package cn.lineai.engine.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.Message;

/** Everything needed to build one completion request. */
public final class ChatRequest {

    public final String modelId;
    public final String systemPrompt;
    public final List<Message> messages;
    public final float temperature;
    public final int maxTokens;
    public final boolean stream;
    public final ReasoningSpec reasoning;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelId = "";
        private String systemPrompt = "";
        private List<Message> messages = new ArrayList<>();
        private float temperature = 0.7f;
        private int maxTokens = 4096;
        private boolean stream = true;
        private ReasoningSpec reasoning;
        private List<ToolSchema> tools = new ArrayList<>();

        public Builder model(String value) {
            modelId = value == null ? "" : value;
            return this;
        }

        public Builder system(String value) {
            systemPrompt = value == null ? "" : value;
            return this;
        }

        public Builder messages(List<Message> value) {
            messages = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        public Builder sampling(float value, int limit) {
            temperature = value;
            maxTokens = limit;
            return this;
        }

        public Builder stream(boolean value) {
            stream = value;
            return this;
        }

        public Builder reasoning(ReasoningSpec value) {
            reasoning = value;
            return this;
        }

        public Builder tools(List<ToolSchema> value) {
            tools = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
