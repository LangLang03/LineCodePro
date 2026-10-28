package cn.lineai.feature.models;

import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;

/** Pure form state and validation shared by save and test. */
final class ModelForm {

    enum Error {
        NONE,
        MISSING_ID,
        MISSING_KEY,
        TOOL_RANGE,
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
    boolean compressionEnabled;
    boolean compressionAuto = true;
    String compressionModelId = "";

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
        form.compressionEnabled = model.compressionEnabled;
        form.compressionAuto = model.compressionAuto;
        form.compressionModelId = model.compressionModelId;
        return form;
    }

    static ModelForm forPreset(ModelPreset preset) {
        ModelForm form = new ModelForm();
        if (preset != null) {
            form.providerId = preset.id;
            form.protocol = preset.protocol;
            form.baseUrl = preset.baseUrl;
        }
        return form;
    }

    Error validateForSave() {
        if (modelId.isEmpty()) return Error.MISSING_ID;
        if (apiKey.isEmpty()) return Error.MISSING_KEY;
        if (toolCallLimit() == null) return Error.TOOL_RANGE;
        if (needsCompressionId()) return Error.COMPRESSION_ID;
        return Error.NONE;
    }

    Error validateForTest() {
        if (baseUrl.isEmpty() || apiKey.isEmpty() || modelId.isEmpty()) return Error.MISSING_ID;
        return Error.NONE;
    }

    boolean canSave() {
        return (displayName().length() > 0 || !modelId.isEmpty())
                && !modelId.isEmpty()
                && !apiKey.isEmpty()
                && toolCallLimit() != null
                && !needsCompressionId();
    }

    ModelConfig build() {
        String resolvedName = displayName().isEmpty() ? modelId : displayName();
        return ModelConfig.builder()
                .identity(id, resolvedName)
                .endpoint(providerId, modelId, baseUrl)
                .providerLabel(providerLabel)
                .secret(apiKey)
                .protocol(protocol)
                .context(contextTokens(), ModelConfig.DEFAULT_TOOL_CALL_LIMIT)
                .toolCalls(toolCallLimit())
                .compression(compressionEnabled, compressionAuto, compressionModelId)
                .build();
    }

    String displayName() {
        return name == null ? "" : name.trim();
    }

    int contextTokens() {
        return ContextSizes.parse(contextSize);
    }

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

    boolean needsCompressionId() {
        return compressionEnabled && !compressionAuto && compressionModelId.trim().isEmpty();
    }
}
