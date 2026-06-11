package cn.lineai.ai.message;

import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssistantModelMessage extends ModelMessage {
    private final List<ToolCall> toolCalls;

    public AssistantModelMessage(String content) {
        super(content);
        this.toolCalls = Collections.emptyList();
    }

    public AssistantModelMessage(String content, String reasoningContent) {
        this(content, reasoningContent, Collections.emptyList());
    }

    public AssistantModelMessage(String content, String reasoningContent, List<ToolCall> toolCalls) {
        super(content, reasoningContent);
        this.toolCalls = toolCalls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    @Override
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    @Override
    public String getRole() {
        return "assistant";
    }
}
