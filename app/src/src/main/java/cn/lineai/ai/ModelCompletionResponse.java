package cn.lineai.ai;

import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelCompletionResponse {
    private final String text;
    private final String reasoningContent;
    private final List<ToolCall> toolCalls;

    public ModelCompletionResponse(String text) {
        this(text, "");
    }

    public ModelCompletionResponse(String text, String reasoningContent) {
        this(text, reasoningContent, Collections.emptyList());
    }

    public ModelCompletionResponse(String text, String reasoningContent, List<ToolCall> toolCalls) {
        this.text = text == null ? "" : text;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
        this.toolCalls = toolCalls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    public String getText() {
        return text;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
