package cn.lineai.engine.prompt;

/** Overlay of user edits on top of the packaged catalog. */
public final class PromptStore {

    public static final String KEY_PREFIX = "prompt.";

    private final PromptCatalog catalog;
    private final PromptOverrides overrides;

    public PromptStore(PromptCatalog catalog, PromptOverrides overrides) {
        this.catalog = catalog;
        this.overrides = overrides == null ? PromptOverrides.none() : overrides;
    }

    public String current(PromptId prompt) {
        return overrides.get(KEY_PREFIX + prompt.id, catalog.text(prompt));
    }

    public String defaultText(PromptId prompt) {
        return catalog.text(prompt);
    }

    public boolean customized(PromptId prompt) {
        return !defaultText(prompt).equals(current(prompt));
    }

    public void save(PromptId prompt, String value) {
        String text = value == null ? "" : value;
        PromptRenderer.validate(prompt, text);
        overrides.put(KEY_PREFIX + prompt.id, text);
    }

    public void reset(PromptId prompt) {
        overrides.remove(KEY_PREFIX + prompt.id);
    }
}
