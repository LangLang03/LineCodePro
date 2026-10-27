package cn.lineai.engine.prompt;

/** Read-only access to the packaged prompt assets. */
public interface PromptCatalog {

    String text(PromptId prompt);

    static PromptCatalog of(java.util.Map<PromptId, String> texts) {
        return prompt -> {
            String value = texts.get(prompt);
            if (value == null) throw new IllegalStateException("Prompt missing: " + prompt.id);
            return value;
        };
    }
}
