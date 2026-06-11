package cn.lineai.model;

public final class ModelProviderPresets {
    public static final ModelProviderPreset CUSTOM = new ModelProviderPreset(
            "custom",
            "自定义",
            "手动填写协议、Base URL、模型 ID 和密钥",
            ModelProtocolType.OPENAI_COMPATIBLE,
            "",
            "https://api.example.com/v1",
            "OpenAI 兼容协议必须填到 /v1 结尾，例如 https://api.example.com/v1；不要只填域名，也不要加 /chat/completions。"
    );

    private static final ModelProviderPreset[] PRESETS = new ModelProviderPreset[] {
            new ModelProviderPreset("deepseek", "DeepSeek", "DeepSeek Chat / Reasoner", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "https://api.deepseek.com/v1", "DeepSeek 使用 OpenAI 兼容协议，Base URL 填到 /v1。"),
            new ModelProviderPreset("glm", "GLM", "智谱 GLM / Z.ai", ModelProtocolType.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4", "https://open.bigmodel.cn/api/paas/v4", "GLM 使用 OpenAI 兼容协议，Base URL 填到 /api/paas/v4。"),
            new ModelProviderPreset("mimo", "Mimo", "小米 Mimo API", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.xiaomimimo.com/v1", "https://api.xiaomimimo.com/v1", "Mimo 使用 OpenAI 兼容协议，Base URL 填到 /v1。"),
            new ModelProviderPreset("mimo-token-plan", "Mimo Token 计划", "小米 Mimo Token 计划", ModelProtocolType.OPENAI_COMPATIBLE, "https://token-plan-cn.xiaomimimo.com/v1", "https://token-plan-cn.xiaomimimo.com/v1", "Mimo Token 计划使用 OpenAI 兼容协议，Base URL 填到 /v1。"),
            new ModelProviderPreset("kimi", "Kimi", "Moonshot AI", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1", "https://api.moonshot.cn/v1", "Kimi/Moonshot 使用 OpenAI 兼容协议，Base URL 填到 /v1。"),
            new ModelProviderPreset("qwen", "Qwen", "DashScope 兼容模式", ModelProtocolType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1", "https://dashscope.aliyuncs.com/compatible-mode/v1", "Qwen/DashScope 使用 OpenAI 兼容协议，Base URL 填到 /compatible-mode/v1。"),
            new ModelProviderPreset("openai", "OpenAI", "GPT / o 系列", ModelProtocolType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "https://api.openai.com/v1", "OpenAI Chat Completions 兼容模型使用 /v1，不要加 /chat/completions。"),
            new ModelProviderPreset("claude", "Claude", "Anthropic Messages API", ModelProtocolType.ANTHROPIC_MESSAGES, "https://api.anthropic.com", "https://api.anthropic.com", "Claude 使用 Anthropic Messages API，Base URL 填根地址，不要加 /v1/messages。"),
            new ModelProviderPreset("gemini", "Gemini", "Google OpenAI 兼容端点", ModelProtocolType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "https://generativelanguage.googleapis.com/v1beta/openai", "Gemini OpenAI 兼容端点填到 /v1beta/openai，应用会调用 /models 和 /chat/completions。"),
            new ModelProviderPreset("openrouter", "OpenRouter", "多模型聚合", ModelProtocolType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "https://openrouter.ai/api/v1", "OpenRouter 使用 OpenAI 兼容协议，Base URL 填到 /api/v1。"),
            new ModelProviderPreset("codex", "Codex", "Responses API", ModelProtocolType.CODEX_RESPONSES, "https://api.openai.com/v1", "https://api.openai.com/v1", "Codex 使用 Responses API，Base URL 填到 /v1，不要加 /responses。")
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
