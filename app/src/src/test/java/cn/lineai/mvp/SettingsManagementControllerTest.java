package cn.lineai.mvp;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.InputSettings;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.model.ThemePalette;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public final class SettingsManagementControllerTest {
    @Test
    public void thinkingScrollChangeRenders() {
        Fixture fixture = new Fixture();

        fixture.controller.setAiThinkingScrollEnabled(false);

        Assert.assertFalse(fixture.store.thinkingScrollEnabled);
        Assert.assertTrue(fixture.host.rendered);
    }

    @Test
    public void toneChangeDoesNotForceRender() {
        Fixture fixture = new Fixture();

        fixture.controller.setAiToneMode(AiBehaviorSettings.TONE_CHAT);

        Assert.assertEquals(AiBehaviorSettings.TONE_CHAT, fixture.store.toneMode);
        Assert.assertFalse(fixture.host.rendered);
    }

    @Test
    public void themeChangeRecreatesThemeScreen() {
        Fixture fixture = new Fixture();

        fixture.controller.setThemeMode(ThemePalette.MODE_LIGHT);

        Assert.assertEquals(ThemePalette.MODE_LIGHT, fixture.store.themeMode);
        Assert.assertEquals("theme", fixture.host.recreateScreenId);
    }

    @Test
    public void mcpExecutionModeChangeCallsHostWithStoredMode() {
        Fixture fixture = new Fixture();

        fixture.controller.setMcpExecutionMode("ssh");

        Assert.assertEquals("ssh", fixture.store.executionMode);
        Assert.assertEquals("ssh", fixture.host.afterMcpExecutionMode);
    }

    @Test
    public void mcpToolGroupChangeRefreshesAndRenders() {
        Fixture fixture = new Fixture();

        fixture.controller.setMcpToolGroupEnabled("shell", false);

        Assert.assertEquals("shell", fixture.store.mcpGroupId);
        Assert.assertFalse(fixture.store.mcpGroupEnabled);
        Assert.assertTrue(fixture.host.mcpRefreshed);
        Assert.assertTrue(fixture.host.rendered);
    }

    @Test
    public void memorySaveUsesCurrentProjectPath() {
        Fixture fixture = new Fixture();
        fixture.host.projectPath = "/tmp/project";

        fixture.controller.saveMemory("mem1", MemoryOverviewState.Memory.SCOPE_PROJECT, "content");

        Assert.assertEquals("mem1", fixture.store.memoryId);
        Assert.assertEquals(MemoryOverviewState.Memory.SCOPE_PROJECT, fixture.store.memoryScope);
        Assert.assertEquals("/tmp/project", fixture.store.memoryProjectPath);
        Assert.assertEquals("content", fixture.store.memoryContent);
    }

    @Test
    public void imageUnderstandingModelSelectionReturnsToToolSettingsAndRenders() {
        Fixture fixture = new Fixture();

        fixture.controller.setImageUnderstandingModelId("vision");

        Assert.assertEquals("vision", fixture.store.imageUnderstandingModelId);
        Assert.assertTrue(fixture.host.returnedToToolSettings);
        Assert.assertTrue(fixture.host.rendered);
    }

    @Test
    public void imageGenerationModelSelectionReturnsToToolSettingsAndRenders() {
        Fixture fixture = new Fixture();

        fixture.controller.setImageGenerationModelId("image");

        Assert.assertEquals("image", fixture.store.imageGenerationModelId);
        Assert.assertTrue(fixture.host.returnedToToolSettings);
        Assert.assertTrue(fixture.host.rendered);
    }

    private static final class Fixture {
        private final FakeSettingsStore store = new FakeSettingsStore();
        private final FakeHost host = new FakeHost();
        private final SettingsManagementController controller = new SettingsManagementController(store, host);
    }

    private static final class FakeSettingsStore implements SettingsManagementController.SettingsStore {
        private String toneMode = AiBehaviorSettings.TONE_CODING;
        private boolean thinkingScrollEnabled = true;
        private String themeMode = ThemePalette.MODE_DARK;
        private String executionMode = "local";
        private String mcpGroupId = "";
        private boolean mcpGroupEnabled;
        private String memoryId = "";
        private String memoryScope = "";
        private String memoryProjectPath = "";
        private String memoryContent = "";
        private String imageUnderstandingModelId = "";
        private String imageGenerationModelId = "";

        @Override
        public AiBehaviorSettings getAiBehaviorSettings() {
            return new AiBehaviorSettings(toneMode, thinkingScrollEnabled, false,
                    AiBehaviorSettings.REASONING_MEDIUM, false, false);
        }

        @Override
        public void setToneMode(String toneMode) {
            this.toneMode = toneMode;
        }

        @Override
        public void setReasoningEffort(String effort) {
        }

        @Override
        public void setThinkingScrollEnabled(boolean enabled) {
            thinkingScrollEnabled = enabled;
        }

        @Override
        public void setThinkingAutoExpandEnabled(boolean enabled) {
        }

        @Override
        public void setPreserveReasoningEnabled(boolean enabled) {
        }

        @Override
        public void setLearningModeEnabled(boolean enabled) {
        }

        @Override
        public InputSettings getInputSettings() {
            return new InputSettings(InputSettings.ENTER_SEND);
        }

        @Override
        public void setEnterKeyBehavior(String behavior) {
        }

        @Override
        public List<PromptTemplateItem> getPromptTemplates() {
            return Collections.emptyList();
        }

        @Override
        public void savePromptTemplate(String id, String value) {
        }

        @Override
        public void resetPromptTemplate(String id) {
        }

        @Override
        public MemoryOverviewState getMemoryOverview(String projectPath) {
            return new MemoryOverviewState(projectPath, null, null, null, null, null);
        }

        @Override
        public void saveMemory(String id, String scope, String projectPath, String content) {
            memoryId = id;
            memoryScope = scope;
            memoryProjectPath = projectPath;
            memoryContent = content;
        }

        @Override
        public void deleteMemory(String id) {
        }

        @Override
        public OutputSettings getOutputSettings() {
            return new OutputSettings(false, OutputSettings.BROWSER_BUILTIN);
        }

        @Override
        public void setCodeWrapEnabled(boolean enabled) {
        }

        @Override
        public void setBrowserMode(String mode) {
        }

        @Override
        public void setBrowserJavaScriptEnabled(boolean enabled) {
        }

        @Override
        public ThemeSettingsState getThemeSettings() {
            return new ThemeSettingsState(themeMode, themeMode, Collections.emptyMap(), null);
        }

        @Override
        public void applyThemeMode(String mode) {
            themeMode = mode;
        }

        @Override
        public void saveCustomThemeColors(Map<String, String> colors) {
        }

        @Override
        public McpSettingsState getMcpSettingsState() {
            return new McpSettingsState(executionMode, Collections.emptyList());
        }

        @Override
        public void setMcpExecutionMode(String mode) {
            executionMode = mode;
        }

        @Override
        public String getMcpExecutionMode() {
            return executionMode;
        }

        @Override
        public void setMcpEnabled(String id, boolean enabled) {
            mcpGroupId = id;
            mcpGroupEnabled = enabled;
        }

        @Override
        public void setWebSearchConfig(WebSearchConfig config) {
        }

        @Override
        public String getImageUnderstandingModelId() {
            return imageUnderstandingModelId;
        }

        @Override
        public void setImageUnderstandingModelId(String id) {
            imageUnderstandingModelId = id;
        }

        @Override
        public String getImageGenerationModelId() {
            return imageGenerationModelId;
        }

        @Override
        public void setImageGenerationModelId(String id) {
            imageGenerationModelId = id;
        }
    }

    private static final class FakeHost implements SettingsManagementController.Host {
        private String projectPath = "";
        private boolean rendered;
        private String recreateScreenId = "";
        private String afterMcpExecutionMode = "";
        private boolean mcpRefreshed;
        private boolean returnedToToolSettings;

        @Override
        public String currentProjectPath() {
            return projectPath;
        }

        @Override
        public void render() {
            rendered = true;
        }

        @Override
        public void recreateForTheme(String screenId) {
            recreateScreenId = screenId;
        }

        @Override
        public void afterMcpExecutionModeChanged(String executionMode) {
            afterMcpExecutionMode = executionMode;
        }

        @Override
        public void refreshMcpScreen() {
            mcpRefreshed = true;
        }

        @Override
        public void returnToToolSettings() {
            returnedToToolSettings = true;
        }
    }
}
