package cn.lineai.ai.message;

import cn.lineai.tool.ToolCall;
import java.util.Collections;
import java.util.List;

public abstract class ModelMessage {
    private final String content;
    private final String reasoningContent;
    private final String rawInputJson;

    protected ModelMessage(String content) {
        this(content, "");
    }

    protected ModelMessage(String content, String reasoningContent) {
        this(content, reasoningContent, "");
    }

    protected ModelMessage(String content, String reasoningContent, String rawInputJson) {
        this.content = content == null ? "" : content;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.rawInputJson = rawInputJson == null ? "" : rawInputJson.trim();
    }

    public final String getContent() {
        return content;
    }

    public final String getReasoningContent() {
        return reasoningContent;
    }

    public final String getRawInputJson() {
        return rawInputJson;
    }

    public List<ToolCall> getToolCalls() {
        return Collections.emptyList();
    }

    public String getToolCallId() {
        return "";
    }

    public String getToolName() {
        return "";
    }

    public boolean isToolError() {
        return false;
    }

    public abstract String getRole();
}
