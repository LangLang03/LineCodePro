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
        return assemble(modelId, modelName, provider, protocol, tone, "agent", "", "none", "none");
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone, String chatMode) {
        return assemble(modelId, modelName, provider, protocol, tone, chatMode, "", "none", "none");
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone, String chatMode, String learningContext) {
        return assemble(modelId, modelName, provider, protocol, tone, chatMode, learningContext,
                "none", "none");
    }

    public String assemble(String modelId, String modelName, String provider, String protocol,
                           String tone, String chatMode, String learningContext,
                           String modeToolsAllowed, String modeToolsBlocked) {
        String toneContext = prompts.current("chat".equals(tone)
                ? PromptId.TONE_CHAT : PromptId.TONE_CODING);
        Map<String, String> identity = new LinkedHashMap<>();
        identity.put("MODEL_ID", safe(modelId));
        identity.put("MODEL_NAME", safe(modelName));
        identity.put("MODEL_PROVIDER", safe(provider));
        identity.put("MODEL_PROTOCOL", safe(protocol));
        String modelIdentity = PromptRenderer.render(PromptId.MODEL_IDENTITY,
                prompts.current(PromptId.MODEL_IDENTITY), identity);
        Map<String, String> modeValues = new LinkedHashMap<>();
        modeValues.put("MODE_TOOLS_ALLOWED", safe(modeToolsAllowed));
        modeValues.put("MODE_TOOLS_BLOCKED", safe(modeToolsBlocked));
        String modeContext = PromptRenderer.render(modePrompt(chatMode),
                prompts.current(modePrompt(chatMode)), modeValues);
        // Empty allowed AND blocked lists mean the model received the tools natively in the
        // request's tools field - the prompt carries no tool names at all.
        String allowed = safe(modeToolsAllowed);
        String blocked = safe(modeToolsBlocked);
        String toolContext = allowed.isEmpty() && blocked.isEmpty() ? ""
                : "## Available tools\nOnly these tools are injected for this request "
                + "(parameter protocol and constraints are in the tool definitions): "
                + (allowed.isEmpty() ? "none" : allowed)
                + (blocked.isEmpty() ? ""
                : "\nTools denied by the current permission mode: " + blocked);
        Map<String, String> contexts = new LinkedHashMap<>();
        contexts.put("TOOLS_CONTEXT", toolContext);
        contexts.put("TONE_CONTEXT", toneContext);
        contexts.put("CHAT_MODE_CONTEXT", modeContext);
        contexts.put("WORK_DIRECTORY_CONTEXT", "");
        contexts.put("LEARNING_CONTEXT", safe(learningContext));
        contexts.put("MODEL_IDENTITY", modelIdentity);
        contexts.put("TODO_STATE", "");
        return PromptRenderer.render(PromptId.SYSTEM_PROMPT,
                prompts.current(PromptId.SYSTEM_PROMPT), contexts);
    }

    /** One-sentence role prompt used by minimal mode; custom sections are skipped by the caller. */
    public String minimal(String toolNames) {
        Map<String, String> values = new LinkedHashMap<>();
        String names = safe(toolNames);
        values.put("TOOL_NAMES", names);
        String rendered = PromptRenderer.render(PromptId.MINIMAL_PROMPT,
                prompts.current(PromptId.MINIMAL_PROMPT), values);
        // Models without native tool support still need the name list as a prompt fallback;
        // native-tools models render the bare template with no tool names at all.
        return names.isEmpty() ? rendered : rendered + "\n\nTools: " + names;
    }

    /** The editable compaction instruction; the asset is the only copy. */
    public String compactionTemplate() {
        return prompts.current(PromptId.CONTEXT_COMPACTION);
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
