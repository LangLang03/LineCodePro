package cn.lineai.engine.prompt;

/**
 * Overlay of user edits on top of the packaged catalog.
 *
 * <p>Reads fall through to the packaged template when no override is stored; writes are validated
 * before persistence, so a stored template is always renderable.</p>
 */
public final class PromptStore {

    /** Namespace prefix under which overrides are keyed in the persistence layer. */
    public static final String KEY_PREFIX = "prompt.";

    private final PromptCatalog catalog;
    private final PromptOverrides overrides;

    /**
     * Creates a store over the packaged catalog.
     *
     * @param catalog packaged templates; the source of defaults
     * @param overrides persistence for user edits; a no-op store is used when null
     */
    public PromptStore(PromptCatalog catalog, PromptOverrides overrides) {
        this.catalog = catalog;
        this.overrides = overrides == null ? PromptOverrides.none() : overrides;
    }

    /**
     * Returns the user override when present, otherwise the packaged template.
     *
     * @param prompt prompt to read
     * @return current effective template text
     */
    public String current(PromptId prompt) {
        return overrides.get(KEY_PREFIX + prompt.id, catalog.text(prompt));
    }

    /**
     * Returns the packaged template, ignoring overrides.
     *
     * @param prompt prompt to read
     * @return packaged default text
     */
    public String defaultText(PromptId prompt) {
        return catalog.text(prompt);
    }

    /**
     * Returns {@code true} when the stored text differs from the packaged default.
     *
     * @param prompt prompt to inspect
     * @return true when a differing override is stored
     */
    public boolean customized(PromptId prompt) {
        return !defaultText(prompt).equals(current(prompt));
    }

    /**
     * Validates and stores a user edit.
     *
     * @param prompt prompt to override
     * @param value replacement template text; null is stored as empty
     * @throws IllegalArgumentException when the template is malformed or declares placeholders the
     *         prompt does not declare
     */
    public void save(PromptId prompt, String value) {
        String text = value == null ? "" : value;
        PromptRenderer.validate(prompt, text);
        overrides.put(KEY_PREFIX + prompt.id, text);
    }

    /**
     * Removes the user override so the packaged template applies again.
     *
     * @param prompt prompt to reset
     */
    public void reset(PromptId prompt) {
        overrides.remove(KEY_PREFIX + prompt.id);
    }
}
