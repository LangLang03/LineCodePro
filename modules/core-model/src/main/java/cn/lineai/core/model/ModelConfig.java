package cn.lineai.core.model;

/** Immutable model configuration. Secrets live in the same row; the UI never logs this object. */
public final class ModelConfig {

    /** Wire protocol spoken by the provider endpoint. */
    public enum Protocol {
        /** Chat-completions style API. */
        OPENAI_COMPATIBLE(true),
        /** OpenAI Responses protocol. */
        CODEX_RESPONSES(true),
        /** Anthropic Messages protocol. */
        ANTHROPIC_MESSAGES(false),
        /** On-device GGUF inference. */
        LOCAL_GGUF(false);

        private final boolean dedicatedCompression;

        /** Records whether the protocol exposes a dedicated compression endpoint. */
        Protocol(boolean dedicatedCompression) {
            this.dedicatedCompression = dedicatedCompression;
        }

        /** Returns whether history compression can use a dedicated model request. */
        public boolean supportsDedicatedCompression() {
            return dedicatedCompression;
        }
    }

    /** Requested reasoning depth for models exposing a reasoning mode. */
    public enum ReasoningEffort {
        LOW,
        MEDIUM,
        HIGH
    }

    public final String id;
    public final String name;
    public final String providerId;
    public final String providerLabel;
    public final String modelId;
    public final String baseUrl;
    public final String apiKey;
    public final Protocol protocol;
    public final int contextSize;
    public final float temperature;
    public final int maxTokens;
    public final ReasoningEffort reasoningEffort;
    public final boolean enabled;
    public final boolean isDefault;
    public final boolean supportsReasoning;
    public final boolean supportsVision;
    public final boolean supportsVideo;
    public final boolean supportsTools;
    public final int toolCallLimit;
    public final boolean compressionEnabled;
    public final boolean compressionAuto;
    public final String compressionModelId;
    public final String localModelPath;
    public final String localAcceleration;

    /** Sentinel passed to {@link Builder#toolCalls(int)} to disable the per-turn call cap. */
    public static final int UNLIMITED_TOOL_CALLS = -1;
    /** Factory default for the per-turn tool-call cap. */
    public static final int DEFAULT_TOOL_CALL_LIMIT = 200;
    /** Sentinel stored when the user has not set an explicit context size. */
    public static final int CONTEXT_SIZE_UNSET = 0;

    /** Creates a configuration from builder state, applying defaults for blank fields. */
    private ModelConfig(Builder b) {
        this.id = b.id;
        this.name = b.name == null || b.name.isEmpty() ? b.modelId : b.name;
        this.providerId = b.providerId;
        this.providerLabel = b.providerLabel;
        this.modelId = b.modelId;
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey;
        this.protocol = b.protocol;
        this.contextSize = b.contextSize;
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.reasoningEffort = b.reasoningEffort;
        this.enabled = b.enabled;
        this.isDefault = b.isDefault;
        this.supportsReasoning = b.supportsReasoning;
        this.supportsVision = b.supportsVision;
        this.supportsVideo = b.supportsVideo;
        this.supportsTools = b.supportsTools;
        this.toolCallLimit = normalizeToolCalls(b.toolCallLimit);
        this.compressionEnabled = b.compressionEnabled;
        this.compressionAuto = b.compressionAuto;
        this.compressionModelId = orEmpty(b.compressionModelId);
        this.localModelPath = orEmpty(b.localModelPath);
        this.localAcceleration = orEmpty(b.localAcceleration);
    }

    /**
     * Normalises a tool-call limit.
     * @param value configured limit
     * @return {@link #UNLIMITED_TOOL_CALLS} unchanged, otherwise at least one
     */
    public static int normalizeToolCalls(int value) {
        if (value == UNLIMITED_TOOL_CALLS) return UNLIMITED_TOOL_CALLS;
        return Math.max(1, value);
    }

    /** Returns {@code value}, or an empty string when null. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Returns the display name, falling back to the raw model id when blank. */
    public String displayName() {
        return name == null || name.isEmpty() ? modelId : name;
    }

    /** Returns a builder pre-filled with every field of this configuration. */
    public Builder toBuilder() {
        return new Builder()
                .identity(id, name)
                .endpoint(providerId, modelId, baseUrl)
                .providerLabel(providerLabel)
                .toolCalls(toolCallLimit)
                .compression(compressionEnabled, compressionAuto, compressionModelId)
                .local(localModelPath, localAcceleration)
                .secret(apiKey)
                .protocol(protocol)
                .context(contextSize, maxTokens)
                .sampling(temperature, reasoningEffort)
                .flags(isDefault, enabled)
                .capabilities(supportsVision, supportsTools)
                .multimodal(supportsReasoning, supportsVision, supportsVideo);
    }

    /** Returns a new builder with the documented defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder; grouped setters keep the arity guard happy and the call sites readable. */
    public static final class Builder {
        private String id = "";
        private String name = "";
        private String providerId = "";
        private String providerLabel = "";
        private String modelId = "";
        private String baseUrl = "";
        private String apiKey = "";
        private Protocol protocol = Protocol.OPENAI_COMPATIBLE;
        private int contextSize = 128000;
        private float temperature = 0.7f;
        private int maxTokens = 4096;
        private ReasoningEffort reasoningEffort = ReasoningEffort.MEDIUM;
        private boolean enabled = true;
        private boolean isDefault;
        private boolean supportsReasoning = true;
        private boolean supportsVision;
        private boolean supportsVideo;
        private boolean supportsTools = true;
        private int toolCallLimit = DEFAULT_TOOL_CALL_LIMIT;
        private boolean compressionEnabled;
        private boolean compressionAuto = true;
        private String compressionModelId = "";
        private String localModelPath = "";
        private String localAcceleration = "automatic";

        /**
         * Sets the stored identity.
         * @param idValue configuration identifier
         * @param nameValue display name, blank falls back to the model id
         * @return this builder
         */
        public Builder identity(String idValue, String nameValue) {
            id = idValue == null ? "" : idValue;
            name = nameValue == null ? "" : nameValue;
            return this;
        }

        /**
         * Sets the endpoint identity.
         * @param provider provider identifier
         * @param model provider-side model id
         * @param url API base URL
         * @return this builder
         */
        public Builder endpoint(String provider, String model, String url) {
            providerId = provider == null ? "" : provider;
            modelId = model == null ? "" : model;
            baseUrl = url == null ? "" : url;
            return this;
        }

        /**
         * Sets the provider label shown in the UI.
         * @param value label to assign
         * @return this builder
         */
        public Builder providerLabel(String value) {
            providerLabel = value == null ? "" : value;
            return this;
        }

        /**
         * Sets the per-turn tool-call cap.
         * @param value limit, or {@link #UNLIMITED_TOOL_CALLS} to disable the cap
         * @return this builder
         */
        public Builder toolCalls(int value) {
            toolCallLimit = value;
            return this;
        }

        /**
         * Sets history-compression behaviour.
         * @param enabled whether compression runs at all
         * @param auto whether compression triggers without user action
         * @param modelId model used for dedicated compression, may be blank
         * @return this builder
         */
        public Builder compression(boolean enabled, boolean auto, String modelId) {
            compressionEnabled = enabled;
            compressionAuto = auto;
            compressionModelId = modelId == null ? "" : modelId;
            return this;
        }

        /**
         * Sets local GGUF inference options.
         * @param path model file path, null becomes empty
         * @param acceleration acceleration hint, null becomes {@code "automatic"}
         * @return this builder
         */
        public Builder local(String path, String acceleration) {
            localModelPath = path == null ? "" : path;
            localAcceleration = acceleration == null ? "automatic" : acceleration;
            return this;
        }

        /**
         * Sets the API credential.
         * @param value secret stored alongside the configuration, null becomes empty
         * @return this builder
         */
        public Builder secret(String value) {
            apiKey = value == null ? "" : value;
            return this;
        }

        /**
         * Sets the wire protocol.
         * @param value protocol, null falls back to {@link Protocol#OPENAI_COMPATIBLE}
         * @return this builder
         */
        public Builder protocol(Protocol value) {
            protocol = value == null ? Protocol.OPENAI_COMPATIBLE : value;
            return this;
        }

        /**
         * Sets context-window sizing.
         * @param size input context size in tokens
         * @param outputLimit completion token limit
         * @return this builder
         */
        public Builder context(int size, int outputLimit) {
            contextSize = size;
            maxTokens = outputLimit;
            return this;
        }

        /**
         * Sets sampling behaviour.
         * @param value temperature
         * @param effort reasoning effort, null falls back to {@link ReasoningEffort#MEDIUM}
         * @return this builder
         */
        public Builder sampling(float value, ReasoningEffort effort) {
            temperature = value;
            reasoningEffort = effort == null ? ReasoningEffort.MEDIUM : effort;
            return this;
        }

        /**
         * Sets the default and enabled flags.
         * @param isDefaultModel whether this configuration is the default
         * @param isEnabled whether the configuration is selectable
         * @return this builder
         */
        public Builder flags(boolean isDefaultModel, boolean isEnabled) {
            isDefault = isDefaultModel;
            enabled = isEnabled;
            return this;
        }

        /**
         * Sets legacy capability switches.
         * @param vision whether image inputs are accepted
         * @param tools whether tool calling is enabled
         * @return this builder
         */
        public Builder capabilities(boolean vision, boolean tools) {
            supportsVision = vision;
            supportsTools = tools;
            return this;
        }

        /** Native multimodal switches shown on the model form. */
        public Builder multimodal(boolean reasoning, boolean vision, boolean video) {
            supportsReasoning = reasoning;
            supportsVision = vision;
            supportsVideo = video;
            return this;
        }

        /**
         * Returns the immutable configuration accumulated by this builder.
         * @return a new {@link ModelConfig}
         */
        public ModelConfig build() {
            return new ModelConfig(this);
        }
    }
}
