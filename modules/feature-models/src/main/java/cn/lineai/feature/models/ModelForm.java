package cn.lineai.feature.models;

import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;

/**
 * Pure form state and validation shared by save and test.
 *
 * <p>The class deliberately keeps plain string fields so screens can bind editors directly without
 * re-parsing on every keystroke; parsing and validation happen only when the page asks. It holds no
 * Android references, which is why both the io-loaded save path and the main-thread render path can
 * share one instance. Validation errors are reported as {@link Error} values rather than thrown so
 * the screens can map them to localized field messages.</p>
 */
final class ModelForm {

    /** Default output-token cap; matches the model builder and the setup screen defaults. */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /**
     * Validation outcomes for save and test paths; screens map each value to a localized message.
     */
    enum Error {
        /** Form is ready for the requested operation. */
        NONE,
        /** Model id or endpoint trio is incomplete. */
        MISSING_ID,
        /** API key is empty. */
        MISSING_KEY,
        /** Tool call limit is non-numeric or below the unlimited floor. */
        TOOL_RANGE,
        /** Context size is non-empty but does not parse to a positive token count. */
        CONTEXT_RANGE,
        /** Manual compression mode was selected without a compression model id. */
        COMPRESSION_ID
    }

    String id = "";
    String name = "";
    String providerId = "custom";
    String providerLabel = "";
    ModelConfig.Protocol protocol = ModelConfig.Protocol.OPENAI_COMPATIBLE;
    String baseUrl = "";
    String apiKey = "";
    String modelId = "";
    String toolCallLimit = String.valueOf(ModelConfig.DEFAULT_TOOL_CALL_LIMIT);
    String contextSize = "";
    boolean supportsReasoning = true;
    boolean supportsVision;
    boolean supportsVideo;
    boolean compressionEnabled;
    boolean compressionAuto = true;
    String compressionModelId = "";

    /**
     * Copies an existing model config into editable form state.
     *
     * @param model config to copy; null yields a blank form
     * @return a new form carrying the config's values
     */
    static ModelForm from(ModelConfig model) {
        ModelForm form = new ModelForm();
        if (model == null) return form;
        form.id = model.id;
        form.name = model.name;
        form.providerId = model.providerId;
        form.providerLabel = model.providerLabel;
        form.protocol = model.protocol;
        form.baseUrl = model.baseUrl;
        form.apiKey = model.apiKey;
        form.modelId = model.modelId;
        form.toolCallLimit = String.valueOf(model.toolCallLimit);
        form.contextSize = model.contextSize > 0 ? ContextSizes.format(model.contextSize) : "";
        form.supportsReasoning = model.supportsReasoning;
        form.supportsVision = model.supportsVision;
        form.supportsVideo = model.supportsVideo;
        form.compressionEnabled = model.compressionEnabled;
        form.compressionAuto = model.compressionAuto;
        form.compressionModelId = model.compressionModelId;
        return form;
    }

    /**
     * Seeds a blank form from a provider preset so only the key and model id remain to fill in.
     *
     * @param preset preset to seed from; a null preset leaves the defaults untouched
     * @return a new form carrying the preset's provider id, protocol and endpoint
     */
    static ModelForm forPreset(ModelPreset preset) {
        ModelForm form = new ModelForm();
        if (preset != null) {
            form.providerId = preset.id;
            form.protocol = preset.protocol;
            form.baseUrl = preset.baseUrl;
        }
        return form;
    }

    /**
     * Validates every field the save path needs, in field order so the first error matches the
     * topmost broken input on screen.
     *
     * @return the first blocking error, or {@link Error#NONE}
     */
    Error validateForSave() {
        if (modelId.isEmpty()) return Error.MISSING_ID;
        if (apiKey.isEmpty()) return Error.MISSING_KEY;
        if (toolCallLimit() == null) return Error.TOOL_RANGE;
        if (!contextValid()) return Error.CONTEXT_RANGE;
        if (needsCompressionId()) return Error.COMPRESSION_ID;
        return Error.NONE;
    }

    /**
     * Validates only what a connectivity test needs: a complete endpoint trio and key.
     *
     * @return {@link Error#MISSING_ID} when any of base URL, key or model id is empty, otherwise
     *     {@link Error#NONE}
     */
    Error validateForTest() {
        if (baseUrl.isEmpty() || apiKey.isEmpty() || modelId.isEmpty()) return Error.MISSING_ID;
        return Error.NONE;
    }

    /**
     * Save-gate used to enable the save action; mirrors {@link #validateForSave} so the button and
     * the error list can never disagree.
     *
     * @return true when the form satisfies all save requirements
     */
    boolean canSave() {
        return (displayName().length() > 0 || !modelId.isEmpty())
                && !modelId.isEmpty()
                && !apiKey.isEmpty()
                && toolCallLimit() != null
                && contextValid()
                && !needsCompressionId();
    }

    /**
     * Builds the immutable config from the current fields, defaulting the display name to the model
     * id when left blank. Callers must run {@link #validateForSave} first; the build itself performs
     * no validation.
     *
     * @return a new {@link ModelConfig}
     */
    ModelConfig build() {
        String resolvedName = displayName().isEmpty() ? modelId : displayName();
        return ModelConfig.builder()
                .identity(id, resolvedName)
                .endpoint(providerId, modelId, baseUrl)
                .providerLabel(providerLabel)
                .secret(apiKey)
                .protocol(protocol)
                .context(contextTokens(), DEFAULT_MAX_TOKENS)
                .toolCalls(toolCallLimit())
                .compression(compressionEnabled, compressionAuto, compressionModelId)
                .multimodal(supportsReasoning, supportsVision, supportsVideo)
                .build();
    }

    /**
     * Returns the trimmed display name.
     *
     * @return trimmed name, or an empty string when blank or unset
     */
    String displayName() {
        return name == null ? "" : name.trim();
    }

    /**
     * Parses the context-size field into a token count.
     *
     * @return parsed token count, or the unset sentinel for blank input
     */
    int contextTokens() {
        return ContextSizes.parse(contextSize);
    }

    /** Empty means "use the app default"; anything else must parse to a positive token count. */
    boolean contextValid() {
        String value = contextSize == null ? "" : contextSize.trim();
        return value.isEmpty() || contextTokens() > ContextSizes.UNSET;
    }

    /**
     * Parses the tool-call limit field.
     *
     * @return the parsed limit, or null when blank, non-numeric or below the unlimited floor
     */
    Integer toolCallLimit() {
        if (toolCallLimit == null || toolCallLimit.trim().isEmpty()) return null;
        try {
            int value = Integer.parseInt(toolCallLimit.trim());
            if (value < ModelConfig.UNLIMITED_TOOL_CALLS) return null;
            return value;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /**
     * Checks whether manual compression is selected without a compression model id, the one
     * cross-field rule {@link #validateForSave} reports last.
     *
     * @return true when a compression model id is required but missing
     */
    boolean needsCompressionId() {
        return compressionEnabled && !compressionAuto && compressionModelId.trim().isEmpty();
    }
}
