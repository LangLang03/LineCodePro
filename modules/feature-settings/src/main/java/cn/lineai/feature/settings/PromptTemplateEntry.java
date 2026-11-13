package cn.lineai.feature.settings;

/** One editable prompt template shown in the settings screen. */
public final class PromptTemplateEntry {

    /** Stable template identifier used for persistence. */
    public final String id;
    /** String resource of the template title. */
    public final int title;
    /** String resource of the template description. */
    public final int description;
    /** Human-readable label of the template source; empty when unknown. */
    public final String sourceLabel;
    /** Variable names referenced by the template body. */
    public final String[] variables;
    /** Factory-default template text. */
    public final String defaultText;
    /** Currently persisted template text. */
    public final String currentText;

    /**
     * Creates an immutable template entry, normalizing null fields defensively.
     *
     * @param id template identifier
     * @param title title string resource
     * @param description description string resource
     * @param sourceLabel source label; null becomes an empty string
     * @param variables variable names; null becomes an empty array, defensively copied
     * @param defaultText default text; null becomes an empty string
     * @param currentText current text; null becomes an empty string
     */
    public PromptTemplateEntry(String id, int title, int description, String sourceLabel,
                               String[] variables, String defaultText, String currentText) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.variables = variables == null ? new String[0] : variables.clone();
        this.defaultText = defaultText == null ? "" : defaultText;
        this.currentText = currentText == null ? "" : currentText;
    }

    /**
     * Returns whether the current text diverges from the factory default.
     *
     * @return true when the template has been customized
     */
    public boolean customized() {
        return !defaultText.equals(currentText);
    }
}
