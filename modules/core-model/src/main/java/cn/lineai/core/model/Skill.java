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

    private Skill(Builder b) {
        this.id = b.id == null ? "" : b.id;
        this.name = b.name == null ? "" : b.name;
        this.description = b.description == null ? "" : b.description;
        this.content = b.content == null ? "" : b.content;
        this.source = b.source == null ? SOURCE_APP : b.source;
        this.enabled = b.enabled;
        this.updatedAt = b.updatedAt;
    }

    public Builder toBuilder() {
        return builder().id(id).name(name).description(description).content(content)
                .source(source).enabled(enabled).updatedAt(updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = "";
        private String name = "";
        private String description = "";
        private String content = "";
        private String source = SOURCE_APP;
        private boolean enabled = true;
        private long updatedAt;

        public Builder id(String value) {
            id = value;
            return this;
        }

        public Builder name(String value) {
            name = value;
            return this;
        }

        public Builder description(String value) {
            description = value;
            return this;
        }

        public Builder content(String value) {
            content = value;
            return this;
        }

        public Builder source(String value) {
            source = value;
            return this;
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder updatedAt(long value) {
            updatedAt = value;
            return this;
        }

        public Skill build() {
            return new Skill(this);
        }
    }

    public static List<String> ids(List<Skill> skills) {
        List<String> result = new ArrayList<>();
        for (Skill skill : skills) {
            result.add(skill.id);
        }
        return result;
    }
}
