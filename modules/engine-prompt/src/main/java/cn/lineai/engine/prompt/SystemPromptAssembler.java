package cn.lineai.engine.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the chat system prompt from the packaged templates.
 *
 * <p>Model identity, tone, session mode, learning context and summary sections are filled by the
 * caller; the templates tolerate empty optional sections.</p>
 */
public final class SystemPromptAssembler {

    private final PromptStore prompts;

    public SystemPromptAssembler(PromptStore prompts) {
        this.prompts = prompts;
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone) {
        return assemble(modelId, modelName, provider, protocol, tone, "agent");
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone, String chatMode) {
        return assemble(modelId, modelName, provider, protocol, tone, chatMode, "");
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone, String chatMode, String learningContext) {
        String toneContext = prompts.current("chat".equals(tone)
                ? PromptId.TONE_CHAT : PromptId.TONE_CODING);
        Map<String, String> identity = new LinkedHashMap<>();
        identity.put("MODEL_ID", safe(modelId));
        identity.put("MODEL_NAME", safe(modelName));
        identity.put("MODEL_PROVIDER", safe(provider));
        identity.put("MODEL_PROTOCOL", safe(protocol));
        String modelIdentity = PromptRenderer.render(PromptId.MODEL_IDENTITY,
                prompts.current(PromptId.MODEL_IDENTITY), identity);
        Map<String, String> contexts = new LinkedHashMap<>();
        contexts.put("TOOLS_CONTEXT", "");
        contexts.put("TONE_CONTEXT", toneContext);
        contexts.put("CHAT_MODE_CONTEXT", prompts.current(modePrompt(chatMode)));
        contexts.put("WORK_DIRECTORY_CONTEXT", "");
        contexts.put("LEARNING_CONTEXT", safe(learningContext));
        contexts.put("MODEL_IDENTITY", modelIdentity);
        contexts.put("TODO_STATE", "");
        return PromptRenderer.render(PromptId.SYSTEM_PROMPT,
                prompts.current(PromptId.SYSTEM_PROMPT), contexts);
    }

    /** One-sentence role prompt used by minimal mode; custom sections are skipped by the caller. */
    public String minimal(String chatMode) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CHAT_MODE_CONTEXT", prompts.current(modePrompt(chatMode)));
        return PromptRenderer.render(PromptId.MINIMAL_PROMPT,
                prompts.current(PromptId.MINIMAL_PROMPT), values);
    }

    /** Wraps a stored compaction summary with the editable provider-facing prefix. */
    public String summary(String summary) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("SUMMARY", safe(summary));
        return PromptRenderer.render(PromptId.CONTEXT_COMPACTION_SUMMARY_PREFIX,
                prompts.current(PromptId.CONTEXT_COMPACTION_SUMMARY_PREFIX), values);
    }

    private static PromptId modePrompt(String mode) {
        if ("chat".equals(mode)) return PromptId.CHAT_MODE_CHAT;
        if ("plan".equals(mode)) return PromptId.CHAT_MODE_PLAN;
        if ("control".equals(mode)) return PromptId.CHAT_MODE_CONTROL;
        return PromptId.CHAT_MODE_AGENT;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
