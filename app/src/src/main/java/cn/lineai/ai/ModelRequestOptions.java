package cn.lineai.ai;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.tool.BaseTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelRequestOptions {
    private final String reasoningEffort;
    private final boolean preserveReasoning;
    private final List<BaseTool> tools;

    public ModelRequestOptions(String reasoningEffort, boolean preserveReasoning) {
        this(reasoningEffort, preserveReasoning, Collections.emptyList());
    }

    public ModelRequestOptions(String reasoningEffort, boolean preserveReasoning, List<BaseTool> tools) {
        this.reasoningEffort = AiBehaviorSettings.normalizeReasoningEffort(reasoningEffort);
        this.preserveReasoning = preserveReasoning;
        this.tools = tools == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(tools));
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public boolean isPreserveReasoning() {
        return preserveReasoning;
    }

    public List<BaseTool> getTools() {
        return tools;
    }

    public static ModelRequestOptions defaults() {
        return new ModelRequestOptions(AiBehaviorSettings.REASONING_MEDIUM, false);
    }
}
