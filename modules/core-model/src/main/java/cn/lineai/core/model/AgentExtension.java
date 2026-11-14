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

    /** Creates an extension from builder state, normalising null strings to empty. */
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

    /** Returns a builder pre-filled with every field of this extension. */
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

    /** Returns a new empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link AgentExtension}; list values are defensively copied. */
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

        /**
         * Sets the identifier.
         * @param value identifier to assign
         * @return this builder
         */
        public Builder id(String value) {
            id = value;
            return this;
        }

        /**
         * Sets the enabled flag.
         * @param value whether the extension participates in agent runs
         * @return this builder
         */
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * Sets the display name.
         * @param value name to assign
         * @return this builder
         */
        public Builder name(String value) {
            name = value;
            return this;
        }

        /**
         * Sets the URL-safe slug.
         * @param value slug to assign
         * @return this builder
         */
        public Builder slug(String value) {
            slug = value;
            return this;
        }

        /**
         * Sets the prompt injected when the extension triggers.
         * @param value prompt text
         * @return this builder
         */
        public Builder prompt(String value) {
            prompt = value;
            return this;
        }

        /**
         * Sets the trigger condition expression.
         * @param value trigger expression
         * @return this builder
         */
        public Builder trigger(String value) {
            trigger = value;
            return this;
        }

        /**
         * Sets the tools the extension may use.
         * @param value tool names, null yields an empty list
         * @return this builder
         */
        public Builder toolNames(List<String> value) {
            toolNames = value == null ? new ArrayList<String>() : new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the lifecycle timestamps.
         * @param created creation timestamp in epoch milliseconds
         * @param updated last modification timestamp in epoch milliseconds
         * @return this builder
         */
        public Builder times(long created, long updated) {
            createdAt = created;
            updatedAt = updated;
            return this;
        }

        /**
         * Returns the immutable extension accumulated by this builder.
         * @return a new {@link AgentExtension}
         */
        public AgentExtension build() {
            return new AgentExtension(this);
        }
    }
}
