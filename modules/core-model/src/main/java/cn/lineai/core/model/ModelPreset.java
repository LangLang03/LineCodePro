package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A built-in provider entry shown when adding a model. */
public final class ModelPreset {

    public final String id;
    public final ModelConfig.Protocol protocol;
    public final String baseUrl;

    public ModelPreset(String id, ModelConfig.Protocol protocol, String baseUrl) {
        this.id = id;
        this.protocol = protocol;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    private static final List<ModelPreset> ALL;

    static {
        List<ModelPreset> presets = new ArrayList<>();
        presets.add(new ModelPreset("deepseek", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1"));
        presets.add(new ModelPreset("glm", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4"));
        presets.add(new ModelPreset("mimo", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.xiaomimimo.com/v1"));
        presets.add(new ModelPreset("mimo-token-plan", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://token-plan-cn.xiaomimimo.com/v1"));
        presets.add(new ModelPreset("kimi", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1"));
        presets.add(new ModelPreset("qwen", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        presets.add(new ModelPreset("openai", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1"));
        presets.add(new ModelPreset("claude", ModelConfig.Protocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com"));
        presets.add(new ModelPreset("gemini", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai"));
        presets.add(new ModelPreset("openrouter", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1"));
        presets.add(new ModelPreset("groq", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1"));
        presets.add(new ModelPreset("together", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.together.xyz/v1"));
        presets.add(new ModelPreset("siliconflow", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1"));
        presets.add(new ModelPreset("minimax", ModelConfig.Protocol.OPENAI_COMPATIBLE, "https://api.minimax.chat/v1"));
        presets.add(new ModelPreset("ollama", ModelConfig.Protocol.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1"));
        presets.add(new ModelPreset("lmstudio", ModelConfig.Protocol.OPENAI_COMPATIBLE, "http://127.0.0.1:1234/v1"));
        presets.add(new ModelPreset("codex", ModelConfig.Protocol.CODEX_RESPONSES, "https://api.openai.com/v1"));
        ALL = Collections.unmodifiableList(presets);
    }

    public static List<ModelPreset> all() {
        return ALL;
    }

    public static ModelPreset find(String id) {
        for (ModelPreset preset : ALL) {
            if (preset.id.equals(id)) return preset;
        }
        return null;
    }
}
