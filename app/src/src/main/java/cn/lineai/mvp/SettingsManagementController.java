package cn.lineai.mvp;

import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
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

public final class SettingsManagementController {
    interface Host {
        String currentProjectPath();

        void render();

        void recreateForTheme(String screenId);

        void afterMcpExecutionModeChanged(String executionMode);

        void refreshMcpScreen();

        void returnToToolSettings();
    }

    interface SettingsStore {
        AiBehaviorSettings getAiBehaviorSettings();

        void setToneMode(String toneMode);

        void setReasoningEffort(String effort);

        void setThinkingScrollEnabled(boolean enabled);

        void setThinkingAutoExpandEnabled(boolean enabled);

        void setPreserveReasoningEnabled(boolean enabled);

        void setLearningModeEnabled(boolean enabled);

        InputSettings getInputSettings();

        void setEnterKeyBehavior(String behavior);

        List<PromptTemplateItem> getPromptTemplates();

        void savePromptTemplate(String id, String value);

        void resetPromptTemplate(String id);

        MemoryOverviewState getMemoryOverview(String projectPath);

        void saveMemory(String id, String scope, String projectPath, String content);

        void deleteMemory(String id);

        OutputSettings getOutputSettings();

        void setCodeWrapEnabled(boolean enabled);

        void setBrowserMode(String mode);

        void setBrowserJavaScriptEnabled(boolean enabled);

        ThemeSettingsState getThemeSettings();

        void applyThemeMode(String mode);

        void saveCustomThemeColors(Map<String, String> colors);

        McpSettingsState getMcpSettingsState();

        void setMcpExecutionMode(String mode);

        String getMcpExecutionMode();

        void setMcpEnabled(String id, boolean enabled);

        void setWebSearchConfig(WebSearchConfig config);

        String getImageUnderstandingModelId();

        void setImageUnderstandingModelId(String id);

        String getImageGenerationModelId();

        void setImageGenerationModelId(String id);
    }

    private static final class RepositorySettingsStore implements SettingsStore {
        private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
        private final InputSettingsRepository inputSettingsRepository;
        private final PromptTemplateRepository promptTemplateRepository;
        private final LearningContextRepository learningContextRepository;
        private final OutputSettingsRepository outputSettingsRepository;
        private final ThemeSettingsRepository themeSettingsRepository;
        private final ToolSettingsRepository toolSettingsRepository;

        RepositorySettingsStore(
                AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
                InputSettingsRepository inputSettingsRepository,
                PromptTemplateRepository promptTemplateRepository,
                LearningContextRepository learningContextRepository,
                OutputSettingsRepository outputSettingsRepository,
                ThemeSettingsRepository themeSettingsRepository,
                ToolSettingsRepository toolSettingsRepository
        ) {
            this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
            this.inputSettingsRepository = inputSettingsRepository;
            this.promptTemplateRepository = promptTemplateRepository;
            this.learningContextRepository = learningContextRepository;
            this.outputSettingsRepository = outputSettingsRepository;
            this.themeSettingsRepository = themeSettingsRepository;
            this.toolSettingsRepository = toolSettingsRepository;
        }

        @Override
        public AiBehaviorSettings getAiBehaviorSettings() {
            return aiBehaviorSettingsRepository.get();
        }

        @Override
        public void setToneMode(String toneMode) {
            aiBehaviorSettingsRepository.setToneMode(toneMode);
        }

        @Override
        public void setReasoningEffort(String effort) {
            aiBehaviorSettingsRepository.setReasoningEffort(effort);
        }

        @Override
        public void setThinkingScrollEnabled(boolean enabled) {
            aiBehaviorSettingsRepository.setThinkingScrollEnabled(enabled);
        }

        @Override
        public void setThinkingAutoExpandEnabled(boolean enabled) {
            aiBehaviorSettingsRepository.setThinkingAutoExpandEnabled(enabled);
        }

        @Override
        public void setPreserveReasoningEnabled(boolean enabled) {
            aiBehaviorSettingsRepository.setPreserveReasoningEnabled(enabled);
        }

        @Override
        public void setLearningModeEnabled(boolean enabled) {
            aiBehaviorSettingsRepository.setLearningModeEnabled(enabled);
        }

        @Override
        public InputSettings getInputSettings() {
            return inputSettingsRepository.get();
        }

        @Override
        public void setEnterKeyBehavior(String behavior) {
            inputSettingsRepository.setEnterKeyBehavior(behavior);
        }

        @Override
        public List<PromptTemplateItem> getPromptTemplates() {
            return promptTemplateRepository.getTemplates();
        }

        @Override
        public void savePromptTemplate(String id, String value) {
            promptTemplateRepository.saveTemplate(id, value);
        }

        @Override
        public void resetPromptTemplate(String id) {
            promptTemplateRepository.resetTemplate(id);
        }

        @Override
        public MemoryOverviewState getMemoryOverview(String projectPath) {
            return learningContextRepository.getOverview(projectPath);
        }

        @Override
        public void saveMemory(String id, String scope, String projectPath, String content) {
            learningContextRepository.saveMemory(id, scope, projectPath, content);
        }

        @Override
        public void deleteMemory(String id) {
            learningContextRepository.deleteMemory(id);
        }

        @Override
        public OutputSettings getOutputSettings() {
            return outputSettingsRepository.get();
        }

        @Override
        public void setCodeWrapEnabled(boolean enabled) {
            outputSettingsRepository.setCodeWrapEnabled(enabled);
        }

        @Override
        public void setBrowserMode(String mode) {
            outputSettingsRepository.setBrowserMode(mode);
        }

        @Override
        public void setBrowserJavaScriptEnabled(boolean enabled) {
            outputSettingsRepository.setBrowserJavaScriptEnabled(enabled);
        }

        @Override
        public ThemeSettingsState getThemeSettings() {
            return themeSettingsRepository.getState();
        }

        @Override
        public void applyThemeMode(String mode) {
            themeSettingsRepository.applyThemeMode(mode);
        }

        @Override
        public void saveCustomThemeColors(Map<String, String> colors) {
            themeSettingsRepository.saveCustomThemeColors(colors);
        }

        @Override
        public McpSettingsState getMcpSettingsState() {
            return toolSettingsRepository.getMcpSettingsState();
        }

        @Override
        public void setMcpExecutionMode(String mode) {
            toolSettingsRepository.setExecutionMode(mode);
        }

        @Override
        public String getMcpExecutionMode() {
            return toolSettingsRepository.getExecutionMode();
        }

        @Override
        public void setMcpEnabled(String id, boolean enabled) {
            toolSettingsRepository.setMcpEnabled(id, enabled);
        }

        @Override
        public void setWebSearchConfig(WebSearchConfig config) {
            toolSettingsRepository.setWebSearchConfig(config);
        }

        @Override
        public String getImageUnderstandingModelId() {
            return toolSettingsRepository.getImageUnderstandingModelId();
        }

        @Override
        public void setImageUnderstandingModelId(String id) {
            toolSettingsRepository.setImageUnderstandingModelId(id);
        }

        @Override
        public String getImageGenerationModelId() {
            return toolSettingsRepository.getImageGenerationModelId();
        }

        @Override
        public void setImageGenerationModelId(String id) {
            toolSettingsRepository.setImageGenerationModelId(id);
        }
    }

    private final SettingsStore settingsStore;
    private final Host host;

    public SettingsManagementController(
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            InputSettingsRepository inputSettingsRepository,
            PromptTemplateRepository promptTemplateRepository,
            LearningContextRepository learningContextRepository,
            OutputSettingsRepository outputSettingsRepository,
            ThemeSettingsRepository themeSettingsRepository,
            ToolSettingsRepository toolSettingsRepository,
            Host host
    ) {
        this(
                new RepositorySettingsStore(
                        aiBehaviorSettingsRepository,
                        inputSettingsRepository,
                        promptTemplateRepository,
                        learningContextRepository,
                        outputSettingsRepository,
                        themeSettingsRepository,
                        toolSettingsRepository
                ),
                host
        );
    }

    SettingsManagementController(SettingsStore settingsStore, Host host) {
        this.settingsStore = settingsStore;
        this.host = host;
    }

    public AiBehaviorSettings getAiBehaviorSettings() {
        return settingsStore.getAiBehaviorSettings();
    }

    public void setAiToneMode(String toneMode) {
        settingsStore.setToneMode(toneMode);
    }

    public void setAiReasoningEffort(String effort) {
        settingsStore.setReasoningEffort(effort);
    }

    public void setAiThinkingScrollEnabled(boolean enabled) {
        settingsStore.setThinkingScrollEnabled(enabled);
        host.render();
    }

    public void setAiThinkingAutoExpandEnabled(boolean enabled) {
        settingsStore.setThinkingAutoExpandEnabled(enabled);
        host.render();
    }

    public void setAiPreserveReasoningEnabled(boolean enabled) {
        settingsStore.setPreserveReasoningEnabled(enabled);
    }

    public void setAiLearningModeEnabled(boolean enabled) {
        settingsStore.setLearningModeEnabled(enabled);
    }

    public InputSettings getInputSettings() {
        return settingsStore.getInputSettings();
    }

    public void setEnterKeyBehavior(String behavior) {
        settingsStore.setEnterKeyBehavior(behavior);
        host.render();
    }

    public List<PromptTemplateItem> getPromptTemplates() {
        return settingsStore.getPromptTemplates();
    }

    public void savePromptTemplate(String id, String value) {
        settingsStore.savePromptTemplate(id, value);
    }

    public void resetPromptTemplate(String id) {
        settingsStore.resetPromptTemplate(id);
    }

    public MemoryOverviewState getMemoryOverview() {
        return settingsStore.getMemoryOverview(host.currentProjectPath());
    }

    public void saveMemory(String id, String scope, String content) {
        settingsStore.saveMemory(id, scope, host.currentProjectPath(), content);
    }

    public void deleteMemory(String id) {
        settingsStore.deleteMemory(id);
    }

    public OutputSettings getOutputSettings() {
        return settingsStore.getOutputSettings();
    }

    public void setCodeWrapEnabled(boolean enabled) {
        settingsStore.setCodeWrapEnabled(enabled);
        host.render();
    }

    public void setBrowserMode(String mode) {
        settingsStore.setBrowserMode(mode);
        host.render();
    }

    public void setBrowserJavaScriptEnabled(boolean enabled) {
        settingsStore.setBrowserJavaScriptEnabled(enabled);
        host.render();
    }

    public ThemeSettingsState getThemeSettings() {
        return settingsStore.getThemeSettings();
    }

    public void setThemeMode(String mode) {
        settingsStore.applyThemeMode(mode);
        host.recreateForTheme("theme");
    }

    public void saveCustomThemeColors(Map<String, String> colors) {
        settingsStore.saveCustomThemeColors(colors);
        host.recreateForTheme("theme");
    }

    public McpSettingsState getMcpSettingsState() {
        return settingsStore.getMcpSettingsState();
    }

    public void setMcpExecutionMode(String mode) {
        settingsStore.setMcpExecutionMode(mode);
        host.afterMcpExecutionModeChanged(settingsStore.getMcpExecutionMode());
    }

    public void setMcpToolGroupEnabled(String id, boolean enabled) {
        settingsStore.setMcpEnabled(id, enabled);
        host.refreshMcpScreen();
        host.render();
    }

    public void setMcpWebSearchConfig(WebSearchConfig config) {
        settingsStore.setWebSearchConfig(config);
    }

    public String getImageUnderstandingModelId() {
        return settingsStore.getImageUnderstandingModelId();
    }

    public void setImageUnderstandingModelId(String id) {
        settingsStore.setImageUnderstandingModelId(id);
        host.returnToToolSettings();
        host.render();
    }

    public String getImageGenerationModelId() {
        return settingsStore.getImageGenerationModelId();
    }

    public void setImageGenerationModelId(String id) {
        settingsStore.setImageGenerationModelId(id);
        host.returnToToolSettings();
        host.render();
    }
}
