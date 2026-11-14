package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.List;

/** One SKILL.md based extension stored on disk. */
public final class Skill {

    public static final String SOURCE_APP = "app";
    public static final String SOURCE_PROJECT = "project";

    public final String id;
    public final String name;
    public final String description;
    public final String content;
    public final String source;
    public final boolean enabled;
    public final long updatedAt;

    /** Creates a skill from builder state, normalising null strings to empty. */
    private Skill(Builder b) {
        this.id = b.id == null ? "" : b.id;
        this.name = b.name == null ? "" : b.name;
        this.description = b.description == null ? "" : b.description;
        this.content = b.content == null ? "" : b.content;
        this.source = b.source == null ? SOURCE_APP : b.source;
        this.enabled = b.enabled;
        this.updatedAt = b.updatedAt;
    }

    /** Returns a builder pre-filled with every field of this skill. */
    public Builder toBuilder() {
        return builder().id(id).name(name).description(description).content(content)
                .source(source).enabled(enabled).updatedAt(updatedAt);
    }

    /** Returns a new builder defaulting to the {@link #SOURCE_APP} source. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link Skill}. */
    public static final class Builder {
        private String id = "";
        private String name = "";
        private String description = "";
        private String content = "";
        private String source = SOURCE_APP;
        private boolean enabled = true;
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
         * Sets the display name.
         * @param value name to assign
         * @return this builder
         */
        public Builder name(String value) {
            name = value;
            return this;
        }

        /**
         * Sets the short description.
         * @param value description to assign
         * @return this builder
         */
        public Builder description(String value) {
            description = value;
            return this;
        }

        /**
         * Sets the SKILL.md body injected when the skill triggers.
         * @param value markdown content
         * @return this builder
         */
        public Builder content(String value) {
            content = value;
            return this;
        }

        /**
         * Sets the storage origin.
         * @param value {@link #SOURCE_APP} or {@link #SOURCE_PROJECT}
         * @return this builder
         */
        public Builder source(String value) {
            source = value;
            return this;
        }

        /**
         * Sets the enabled flag.
         * @param value whether the skill participates in agent runs
         * @return this builder
         */
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * Sets the last modification timestamp.
         * @param value timestamp in epoch milliseconds
         * @return this builder
         */
        public Builder updatedAt(long value) {
            updatedAt = value;
            return this;
        }

        /**
         * Returns the immutable skill accumulated by this builder.
         * @return a new {@link Skill}
         */
        public Skill build() {
            return new Skill(this);
        }
    }

    /**
     * Extracts skill identifiers.
     * @param skills skills to read
     * @return identifiers in iteration order
     */
    public static List<String> ids(List<Skill> skills) {
        List<String> result = new ArrayList<>();
        for (Skill skill : skills) {
            result.add(skill.id);
        }
        return result;
    }
}
