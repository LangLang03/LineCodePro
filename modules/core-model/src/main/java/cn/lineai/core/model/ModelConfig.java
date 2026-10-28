package cn.lineai.core.model;

/** Immutable model configuration. Secrets live in the same row; the UI never logs this object. */
public final class ModelConfig {

    public enum Protocol {
        OPENAI_COMPATIBLE(true),
        CODEX_RESPONSES(true),
        ANTHROPIC_MESSAGES(false),
        LOCAL_GGUF(false);

        private final boolean dedicatedCompression;

        Protocol(boolean dedicatedCompression) {
            this.dedicatedCompression = dedicatedCompression;
        }

        public boolean supportsDedicatedCompression() {
            return dedicatedCompression;
        }
    }

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
    public final boolean supportsVision;
    public final boolean supportsTools;
    public final int toolCallLimit;
    public final boolean compressionEnabled;
    public final boolean compressionAuto;
    public final String compressionModelId;
    public final String localModelPath;
    public final String localAcceleration;

    public static final int UNLIMITED_TOOL_CALLS = -1;
    public static final int DEFAULT_TOOL_CALL_LIMIT = 200;
    public static final int CONTEXT_SIZE_UNSET = 0;

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
        this.supportsVision = b.supportsVision;
        this.supportsTools = b.supportsTools;
        this.toolCallLimit = normalizeToolCalls(b.toolCallLimit);
        this.compressionEnabled = b.compressionEnabled;
        this.compressionAuto = b.compressionAuto;
        this.compressionModelId = orEmpty(b.compressionModelId);
        this.localModelPath = orEmpty(b.localModelPath);
        this.localAcceleration = orEmpty(b.localAcceleration);
    }

    public static int normalizeToolCalls(int value) {
        if (value == UNLIMITED_TOOL_CALLS) return UNLIMITED_TOOL_CALLS;
        return Math.max(1, value);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public String displayName() {
        return name == null || name.isEmpty() ? modelId : name;
    }

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
                .capabilities(supportsVision, supportsTools);
    }

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
        private boolean supportsVision;
        private boolean supportsTools = true;
        private int toolCallLimit = DEFAULT_TOOL_CALL_LIMIT;
        private boolean compressionEnabled;
        private boolean compressionAuto = true;
        private String compressionModelId = "";
        private String localModelPath = "";
        private String localAcceleration = "automatic";

        public Builder identity(String idValue, String nameValue) {
            id = idValue == null ? "" : idValue;
            name = nameValue == null ? "" : nameValue;
            return this;
        }

        public Builder endpoint(String provider, String model, String url) {
            providerId = provider == null ? "" : provider;
            modelId = model == null ? "" : model;
            baseUrl = url == null ? "" : url;
            return this;
        }

        public Builder providerLabel(String value) {
            providerLabel = value == null ? "" : value;
            return this;
        }

        public Builder toolCalls(int value) {
            toolCallLimit = value;
            return this;
        }

        public Builder compression(boolean enabled, boolean auto, String modelId) {
            compressionEnabled = enabled;
            compressionAuto = auto;
            compressionModelId = modelId == null ? "" : modelId;
            return this;
        }

        public Builder local(String path, String acceleration) {
            localModelPath = path == null ? "" : path;
            localAcceleration = acceleration == null ? "automatic" : acceleration;
            return this;
        }

        public Builder secret(String value) {
            apiKey = value == null ? "" : value;
            return this;
        }

        public Builder protocol(Protocol value) {
            protocol = value == null ? Protocol.OPENAI_COMPATIBLE : value;
            return this;
        }

        public Builder context(int size, int outputLimit) {
            contextSize = size;
            maxTokens = outputLimit;
            return this;
        }

        public Builder sampling(float value, ReasoningEffort effort) {
            temperature = value;
            reasoningEffort = effort == null ? ReasoningEffort.MEDIUM : effort;
            return this;
        }

        public Builder flags(boolean isDefaultModel, boolean isEnabled) {
            isDefault = isDefaultModel;
            enabled = isEnabled;
            return this;
        }

        public Builder capabilities(boolean vision, boolean tools) {
            supportsVision = vision;
            supportsTools = tools;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(this);
        }
    }
}
