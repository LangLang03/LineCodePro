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

    /** Creates an extension from builder state; header and tool lists become unmodifiable. */
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

    /** Returns a builder pre-filled with every field of this extension. */
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

    /** Returns a new empty builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link McpExtension}; list values are defensively copied. */
    public static final class Builder {
        private String id = "";
        private boolean enabled = true;
        private String name = "";
        private String url = "";
        private List<McpHeader> headers = new ArrayList<>();
        private List<McpToolInfo> tools = new ArrayList<>();
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
         * @param value whether the server participates in tool discovery
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
         * Sets the MCP server endpoint URL.
         * @param value endpoint URL
         * @return this builder
         */
        public Builder url(String value) {
            url = value;
            return this;
        }

        /**
         * Sets the custom HTTP headers sent with every request.
         * @param value header list, null yields an empty list
         * @return this builder
         */
        public Builder headers(List<McpHeader> value) {
            headers = value == null ? new ArrayList<McpHeader>() : new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the tools discovered on the server.
         * @param value tool descriptors, null yields an empty list
         * @return this builder
         */
        public Builder tools(List<McpToolInfo> value) {
            tools = value == null ? new ArrayList<McpToolInfo>() : new ArrayList<>(value);
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
         * @return a new {@link McpExtension}
         */
        public McpExtension build() {
            return new McpExtension(this);
        }
    }
}
