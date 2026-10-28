package cn.lineai.feature.models;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import cn.lineai.ui.i18n.R;

/** Localised preset labels and descriptions. */
final class PresetText {

    private static final Map<String, int[]> REGISTRY = new HashMap<>();

    static {
        register("deepseek", R.string.model_provider_preset_deepseek_label, R.string.model_provider_preset_deepseek_desc);
        register("glm", R.string.model_provider_preset_glm_label, R.string.model_provider_preset_glm_desc);
        register("mimo", R.string.model_provider_preset_mimo_label, R.string.model_provider_preset_mimo_desc);
        register("mimo-token-plan", R.string.model_provider_preset_mimo_token_plan_label, R.string.model_provider_preset_mimo_token_plan_desc);
        register("kimi", R.string.model_provider_preset_kimi_label, R.string.model_provider_preset_kimi_desc);
        register("qwen", R.string.model_provider_preset_qwen_label, R.string.model_provider_preset_qwen_desc);
        register("openai", R.string.model_provider_preset_openai_label, R.string.model_provider_preset_openai_desc);
        register("claude", R.string.model_provider_preset_claude_label, R.string.model_provider_preset_claude_desc);
        register("gemini", R.string.model_provider_preset_gemini_label, R.string.model_provider_preset_gemini_desc);
        register("openrouter", R.string.model_provider_preset_openrouter_label, R.string.model_provider_preset_openrouter_desc);
        register("groq", R.string.model_provider_preset_groq_label, R.string.model_provider_preset_groq_desc);
        register("together", R.string.model_provider_preset_together_label, R.string.model_provider_preset_together_desc);
        register("siliconflow", R.string.model_provider_preset_siliconflow_label, R.string.model_provider_preset_siliconflow_desc);
        register("minimax", R.string.model_provider_preset_minimax_label, R.string.model_provider_preset_minimax_desc);
        register("ollama", R.string.model_provider_preset_ollama_label, R.string.model_provider_preset_ollama_desc);
        register("lmstudio", R.string.model_provider_preset_lmstudio_label, R.string.model_provider_preset_lmstudio_desc);
        register("codex", R.string.model_provider_preset_codex_label, R.string.model_provider_preset_codex_desc);
    }

    private PresetText() {
    }

    private static void register(String id, int label, int description) {
        REGISTRY.put(id, new int[] {label, description});
    }

    static String hint(Context context, String id) {
        Integer hint = HINTS.get(id);
        return hint == null ? "" : context.getString(hint);
    }

    private static final Map<String, Integer> HINTS = new HashMap<>();

    static {
        HINTS.put("deepseek", R.string.model_provider_preset_deepseek_hint);
        HINTS.put("glm", R.string.model_provider_preset_glm_hint);
        HINTS.put("mimo", R.string.model_provider_preset_mimo_hint);
        HINTS.put("mimo-token-plan", R.string.model_provider_preset_mimo_token_plan_hint);
        HINTS.put("kimi", R.string.model_provider_preset_kimi_hint);
        HINTS.put("qwen", R.string.model_provider_preset_qwen_hint);
        HINTS.put("openai", R.string.model_provider_preset_openai_hint);
        HINTS.put("claude", R.string.model_provider_preset_claude_hint);
        HINTS.put("gemini", R.string.model_provider_preset_gemini_hint);
        HINTS.put("openrouter", R.string.model_provider_preset_openrouter_hint);
        HINTS.put("groq", R.string.model_provider_preset_groq_hint);
        HINTS.put("together", R.string.model_provider_preset_together_hint);
        HINTS.put("siliconflow", R.string.model_provider_preset_siliconflow_hint);
        HINTS.put("minimax", R.string.model_provider_preset_minimax_hint);
        HINTS.put("ollama", R.string.model_provider_preset_ollama_hint);
        HINTS.put("lmstudio", R.string.model_provider_preset_lmstudio_hint);
        HINTS.put("codex", R.string.model_provider_preset_codex_hint);
    }

    static String label(Context context, String id) {
        int[] entry = REGISTRY.get(id);
        return entry == null ? id : context.getString(entry[0]);
    }

    static String description(Context context, String id) {
        int[] entry = REGISTRY.get(id);
        return entry == null ? "" : context.getString(entry[1]);
    }

    static String protocol(Context context, ModelConfig.Protocol protocol) {
        switch (protocol) {
            case CODEX_RESPONSES: return "Codex";
            case ANTHROPIC_MESSAGES: return "Anthropic";
            case LOCAL_GGUF: return context.getString(R.string.model_provider_local_gguf);
            case OPENAI_COMPATIBLE:
            default: return context.getString(R.string.model_provider_openai_compatible);
        }
    }
}
