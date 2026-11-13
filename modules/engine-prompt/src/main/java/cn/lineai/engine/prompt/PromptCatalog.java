package cn.lineai.engine.prompt;

/** Read-only access to the packaged prompt assets. */
public interface PromptCatalog {

    /**
     * Returns the packaged template text for a prompt.
     *
     * @param prompt identifier of the requested template
     * @return packaged template text
     * @throws IllegalStateException when the prompt is not packaged
     */
    String text(PromptId prompt);

    /**
     * Wraps a map as a catalog.
     *
     * @param texts template text keyed by prompt id
     * @return catalog backed by {@code texts}; lookups fail for absent prompts
     */
    static PromptCatalog of(java.util.Map<PromptId, String> texts) {
        return prompt -> {
            String value = texts.get(prompt);
            if (value == null) throw new IllegalStateException("Prompt missing: " + prompt.id);
            return value;
        };
    }
}
