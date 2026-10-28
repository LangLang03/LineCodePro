package cn.lineai.feature.settings;

/** One editable prompt template shown in the settings screen. */
public final class PromptTemplateEntry {

    public final String id;
    public final int title;
    public final int description;
    public final String sourceLabel;
    public final String[] variables;
    public final String defaultText;
    public final String currentText;

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

    public boolean customized() {
        return !defaultText.equals(currentText);
    }
}
