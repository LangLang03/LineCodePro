package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A user-defined agent extension: prompt, trigger and the tools it may use. */
public final class AgentExtension {

    public final String id;
    public final boolean enabled;
    public final String name;
    public final String slug;
    public final String prompt;
    public final String trigger;
    public final List<String> toolNames;
    public final long createdAt;
    public final long updatedAt;

    private AgentExtension(Builder b) {
        this.id = b.id == null ? "" : b.id;
        this.enabled = b.enabled;
        this.name = b.name == null ? "" : b.name;
        this.slug = b.slug == null ? "" : b.slug;
        this.prompt = b.prompt == null ? "" : b.prompt;
        this.trigger = b.trigger == null ? "" : b.trigger;
        this.toolNames = Collections.unmodifiableList(new ArrayList<>(b.toolNames));
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public Builder toBuilder() {
        return builder()
                .id(id)
                .enabled(enabled)
                .name(name)
                .slug(slug)
                .prompt(prompt)
                .trigger(trigger)
                .toolNames(toolNames)
                .times(createdAt, updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = "";
        private boolean enabled = true;
        private String name = "";
        private String slug = "";
        private String prompt = "";
        private String trigger = "";
        private List<String> toolNames = new ArrayList<>();
        private long createdAt;
        private long updatedAt;

        public Builder id(String value) {
            id = value;
            return this;
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder name(String value) {
            name = value;
            return this;
        }

        public Builder slug(String value) {
            slug = value;
            return this;
        }

        public Builder prompt(String value) {
            prompt = value;
            return this;
        }

        public Builder trigger(String value) {
            trigger = value;
            return this;
        }

        public Builder toolNames(List<String> value) {
            toolNames = value == null ? new ArrayList<String>() : new ArrayList<>(value);
            return this;
        }

        public Builder times(long created, long updated) {
            createdAt = created;
            updatedAt = updated;
            return this;
        }

        public AgentExtension build() {
            return new AgentExtension(this);
        }
    }
}
