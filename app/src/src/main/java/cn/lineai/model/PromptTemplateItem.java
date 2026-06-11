package cn.lineai.model;

import java.util.Arrays;

public final class PromptTemplateItem {
    private final String id;
    private final String title;
    private final String description;
    private final String sourceLabel;
    private final String[] variables;
    private final String defaultText;
    private final String currentText;
    private final boolean customized;

    public PromptTemplateItem(
            String id,
            String title,
            String description,
            String sourceLabel,
            String[] variables,
            String defaultText,
            String currentText,
            boolean customized
    ) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.variables = variables == null ? new String[0] : Arrays.copyOf(variables, variables.length);
        this.defaultText = defaultText == null ? "" : defaultText;
        this.currentText = currentText == null ? "" : currentText;
        this.customized = customized;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String[] getVariables() {
        return Arrays.copyOf(variables, variables.length);
    }

    public String getDefaultText() {
        return defaultText;
    }

    public String getCurrentText() {
        return currentText;
    }

    public boolean isCustomized() {
        return customized;
    }
}
