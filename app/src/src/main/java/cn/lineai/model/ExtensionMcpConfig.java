package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionMcpConfig {
    private final String id;
    private final boolean enabled;
    private final String name;
    private final String url;
    private final List<McpRequestHeader> requestHeaders;
    private final List<McpToolSummary> tools;
    private final long createdAt;
    private final long updatedAt;

    public ExtensionMcpConfig(
            String id,
            boolean enabled,
            String name,
            String url,
            List<McpRequestHeader> requestHeaders,
            List<McpToolSummary> tools,
            long createdAt,
            long updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.enabled = enabled;
        this.name = name == null ? "" : name;
        this.url = url == null ? "" : url;
        this.requestHeaders = requestHeaders == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(requestHeaders));
        this.tools = tools == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tools));
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

    public String getUrl() {
        return url;
    }

    public List<McpRequestHeader> getRequestHeaders() {
        return requestHeaders;
    }

    public List<McpToolSummary> getTools() {
        return tools;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
