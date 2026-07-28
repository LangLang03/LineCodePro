package cn.lineai.model;

public final class ModelProviderPresets {
    public static final ModelProviderPreset CUSTOM = new ModelProviderPreset(
            "custom",
            ModelProtocolType.OPENAI_COMPATIBLE,
            "",
            "https://api.example.com/v1"
    );

    private static final ModelProviderPreset[] PRESETS = new ModelProviderPreset[] {
            new ModelProviderPreset("deepseek", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "https://api.deepseek.com/v1"),
            new ModelProviderPreset("glm", ModelProtocolType.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4", "https://open.bigmodel.cn/api/paas/v4"),
            new ModelProviderPreset("mimo", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.xiaomimimo.com/v1", "https://api.xiaomimimo.com/v1"),
            new ModelProviderPreset("mimo-token-plan", ModelProtocolType.OPENAI_COMPATIBLE, "https://token-plan-cn.xiaomimimo.com/v1", "https://token-plan-cn.xiaomimimo.com/v1"),
            new ModelProviderPreset("kimi", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1", "https://api.moonshot.cn/v1"),
            new ModelProviderPreset("qwen", ModelProtocolType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            new ModelProviderPreset("openai", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "https://api.openai.com/v1"),
            new ModelProviderPreset("claude", ModelProtocolType.ANTHROPIC_MESSAGES, "https://api.anthropic.com", "https://api.anthropic.com"),
            new ModelProviderPreset("gemini", ModelProtocolType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "https://generativelanguage.googleapis.com/v1beta/openai"),
            new ModelProviderPreset("openrouter", ModelProtocolType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "https://openrouter.ai/api/v1"),
            new ModelProviderPreset("groq", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "https://api.groq.com/openai/v1"),
            new ModelProviderPreset("together", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.together.xyz/v1", "https://api.together.xyz/v1"),
            new ModelProviderPreset("siliconflow", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1", "https://api.siliconflow.cn/v1"),
            new ModelProviderPreset("minimax", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.minimax.chat/v1", "https://api.minimax.chat/v1"),
            new ModelProviderPreset("ollama", ModelProtocolType.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", "http://127.0.0.1:11434/v1"),
            new ModelProviderPreset("lmstudio", ModelProtocolType.OPENAI_COMPATIBLE, "http://127.0.0.1:1234/v1", "http://127.0.0.1:1234/v1"),
            new ModelProviderPreset("codex", ModelProtocolType.CODEX_RESPONSES, "https://api.openai.com/v1", "https://api.openai.com/v1")
    };

    private ModelProviderPresets() {
    }

    public static ModelProviderPreset[] all() {
        return PRESETS.clone();
    }

    public static ModelProviderPreset find(String id) {
        if (id == null) {
            return null;
        }
        for (ModelProviderPreset preset : PRESETS) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
