package cn.lineai.ai;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.tool.ToolInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelRequestOptions {
    private final String reasoningEffort;
    private final boolean preserveReasoning;
    private final List<ToolInfo> tools;

    public ModelRequestOptions(String reasoningEffort, boolean preserveReasoning) {
        this(reasoningEffort, preserveReasoning, Collections.emptyList());
    }

    public ModelRequestOptions(String reasoningEffort, boolean preserveReasoning, List<ToolInfo> tools) {
        this.reasoningEffort = AiBehaviorSettings.normalizeReasoningEffort(reasoningEffort);
        this.preserveReasoning = preserveReasoning;
        ArrayList<ToolInfo> ordered = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        ordered.sort(java.util.Comparator.comparing(ToolInfo::getName));
        this.tools = Collections.unmodifiableList(ordered);
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public boolean isPreserveReasoning() {
        return preserveReasoning;
    }

    public List<ToolInfo> getTools() {
        return tools;
    }

    public static ModelRequestOptions defaults() {
        return new ModelRequestOptions(AiBehaviorSettings.REASONING_MEDIUM, false);
    }
}
