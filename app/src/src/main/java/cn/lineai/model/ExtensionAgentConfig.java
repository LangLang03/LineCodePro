package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionAgentConfig {
    private final String id;
    private final boolean enabled;
    private final String name;
    private final String slug;
    private final String prompt;
    private final String trigger;
    private final List<String> toolNames;
    private final List<String> mcpIds;
    private final long createdAt;
    private final long updatedAt;

    public ExtensionAgentConfig(
            String id,
            boolean enabled,
            String name,
            String slug,
            String prompt,
            String trigger,
            List<String> toolNames,
            List<String> mcpIds,
            long createdAt,
            long updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.enabled = enabled;
        this.name = name == null ? "" : name;
        this.slug = slug == null ? "" : slug;
        this.prompt = prompt == null ? "" : prompt;
        this.trigger = trigger == null ? "" : trigger;
        this.toolNames = immutableStrings(toolNames);
        this.mcpIds = immutableStrings(mcpIds);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getTrigger() {
        return trigger;
    }

    public List<String> getToolNames() {
        return toolNames;
    }

    public List<String> getMcpIds() {
        return mcpIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    private static List<String> immutableStrings(List<String> source) {
        ArrayList<String> values = new ArrayList<>();
        if (source != null) {
            for (String item : source) {
                if (item != null && item.trim().length() > 0) {
                    values.add(item.trim());
                }
            }
        }
        return Collections.unmodifiableList(values);
    }
}
