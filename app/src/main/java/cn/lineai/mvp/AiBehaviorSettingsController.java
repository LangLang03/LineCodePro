package cn.lineai.mvp;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.MemoryOverviewState;

public interface AiBehaviorSettingsController {
    AiBehaviorSettings getAiBehaviorSettings();

    void onAiToneModeChanged(String toneMode);

    void onAiReasoningEffortChanged(String effort);

    void onAiThinkingScrollChanged(boolean enabled);

    void onAiThinkingAutoExpandChanged(boolean enabled);

    void onAiPreserveReasoningChanged(boolean enabled);

    void onAiLearningModeChanged(boolean enabled);

    void onAiSoftCompactionChanged(boolean enabled);

    MemoryOverviewState getMemoryOverview();

    void onMemorySaved(String id, String scope, String content);

    void onMemoryDeleted(String id);

    void onMemoriesDeleted(java.util.List<String> ids);
}
