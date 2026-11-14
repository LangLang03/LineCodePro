package cn.lineai.feature.chat;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.data.api.SettingsStore;

/**
 * Resolves the context window used for the percentage readout and compaction.
 *
 * <p>The selected model owns its own context size. The legacy single-model settings key is only a
 * fallback for the no-model case and for models saved before the context field existed.</p>
 */
final class ChatContextLimits {

    /** Fallback context window when neither the model nor settings specify one. */
    static final int DEFAULT_CONTEXT = 128000;

    /** Static utility; not instantiable. */
    private ChatContextLimits() {
    }

    /** Context window in tokens for the selected model, with the legacy settings fallback. */
    static int of(ModelConfig model, SettingsStore settings) {
        if (model != null && model.contextSize > ModelConfig.CONTEXT_SIZE_UNSET) {
            return model.contextSize;
        }
        return settings.getInt(ChatController.KEY_CONTEXT_SIZE, DEFAULT_CONTEXT);
    }
}
