package cn.lineai.mvp;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.InputSettings;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import java.util.List;
import java.util.Map;

public interface SettingsController {
    void onMoreClick();

    void onSettingsItemSelected(String id);

    AiBehaviorSettings getAiBehaviorSettings();

    void onAiToneModeChanged(String toneMode);

    void onAiReasoningEffortChanged(String effort);

    void onAiThinkingScrollChanged(boolean enabled);

    void onAiThinkingAutoExpandChanged(boolean enabled);

    void onAiPreserveReasoningChanged(boolean enabled);

    void onAiLearningModeChanged(boolean enabled);

    InputSettings getInputSettings();

    void onEnterKeyBehaviorChanged(String behavior);

    List<PromptTemplateItem> getPromptTemplates();

    void onPromptTemplateSaved(String id, String value);

    void onPromptTemplateReset(String id);

    MemoryOverviewState getMemoryOverview();

    void onMemorySaved(String id, String scope, String content);

    void onMemoryDeleted(String id);

    OutputSettings getOutputSettings();

    void onCodeWrapChanged(boolean enabled);

    void onBrowserModeChanged(String mode);

    void onBrowserJavaScriptChanged(boolean enabled);

    ThemeSettingsState getThemeSettings();

    void onThemeModeChanged(String mode);

    void onCustomThemeColorsSaved(Map<String, String> colors);

    McpSettingsState getMcpSettingsState();

    void onMcpExecutionModeChanged(String mode);

    void onMcpToolGroupChanged(String id, boolean enabled);

    void onMcpWebSearchConfigChanged(WebSearchConfig config);

    String getImageUnderstandingModelId();

    void onImageUnderstandingModelSelected(String id);

    String getImageGenerationModelId();

    void onImageGenerationModelSelected(String id);

    void onLineCodeExportRequested();

    void onLineCodeExportTargetPicked(String uri, String displayName);

    void onLineCodeExportCancelled();

    void onLineCodeImportRequested();

    void onLineCodeImportPicked(String uri, String displayName);

    void onLineCodeImportCancelled();
}
