package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A user-configured MCP server endpoint and the tools discovered on it. */
public final class McpExtension {

    public final String id;
    public final boolean enabled;
    public final String name;
    public final String url;
    public final List<McpHeader> headers;
    public final List<McpToolInfo> tools;
    public final long createdAt;
    public final long updatedAt;

    private McpExtension(Builder b) {
        this.id = b.id == null ? "" : b.id;
        this.enabled = b.enabled;
        this.name = b.name == null ? "" : b.name;
        this.url = b.url == null ? "" : b.url;
        this.headers = Collections.unmodifiableList(new ArrayList<>(b.headers));
        this.tools = Collections.unmodifiableList(new ArrayList<>(b.tools));
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public Builder toBuilder() {
        return builder()
                .id(id)
                .enabled(enabled)
                .name(name)
                .url(url)
                .headers(headers)
                .tools(tools)
                .times(createdAt, updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = "";
        private boolean enabled = true;
        private String name = "";
        private String url = "";
        private List<McpHeader> headers = new ArrayList<>();
        private List<McpToolInfo> tools = new ArrayList<>();
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

        public Builder url(String value) {
            url = value;
            return this;
        }

        public Builder headers(List<McpHeader> value) {
            headers = value == null ? new ArrayList<McpHeader>() : new ArrayList<>(value);
            return this;
        }

        public Builder tools(List<McpToolInfo> value) {
            tools = value == null ? new ArrayList<McpToolInfo>() : new ArrayList<>(value);
            return this;
        }

        public Builder times(long created, long updated) {
            createdAt = created;
            updatedAt = updated;
            return this;
        }

        public McpExtension build() {
            return new McpExtension(this);
        }
    }
}
