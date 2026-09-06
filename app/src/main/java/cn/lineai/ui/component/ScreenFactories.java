package cn.lineai.ui.component;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.KeepAliveSettings;
import cn.lineai.model.StorageStatsUiModel;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.log.ErrorLogEntry;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.data.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.model.ModelProviderPresets;
import cn.lineai.model.SshConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.mvp.MainUiController;
import cn.lineai.ssh.TermuxHelper;
import cn.lineai.ui.MainChatView;
import java.util.List;
import java.util.Map;

/**
 * Collection of {@link ScreenFactory} implementations extracted from
 * {@code MainChatView.buildScreen}. Each static inner class owns one screen id;
 * prefix-style factories override {@link ScreenFactory#matches(String)}.
 */
public final class ScreenFactories {
    private ScreenFactories() {
    }

    private static String currentScreenId(MainChatView view) {
        String id = view.getCurrentScreenId();
        return id == null ? "" : id;
    }

    private static String imageUnderstandingModelLabel(MainUiController controller) {
        ModelConfig model = controller.getModel(controller.getImageUnderstandingModelId());
        if (model == null) {
            return "";
        }
        String modelId = model.getModelId();
        return model.getName() + (modelId.length() == 0 ? "" : " · " + modelId);
    }

    private static String imageGenerationModelLabel(MainUiController controller) {
        ModelConfig model = controller.getModel(controller.getImageGenerationModelId());
        if (model == null) {
            return "";
        }
        String modelId = model.getModelId();
        return model.getName() + (modelId.length() == 0 ? "" : " · " + modelId);
    }

    private static ExtensionAgentConfig findAgent(ExtensionOverviewState state, String id) {
        if (state == null || id == null || id.length() == 0) {
            return null;
        }
        for (ExtensionAgentConfig agent : state.getAgents()) {
            if (id.equals(agent.getId())) {
                return agent;
            }
        }
        return null;
    }

    private static ExtensionMcpConfig findMcp(ExtensionOverviewState state, String id) {
        if (state == null || id == null || id.length() == 0) {
            return null;
        }
        for (ExtensionMcpConfig mcp : state.getMcps()) {
            if (id.equals(mcp.getId())) {
                return mcp;
            }
        }
        return null;
    }

    private static cn.lineai.model.ExtensionKindUiModel buildExtensionKindUiModel(
            Context context, String kind, cn.lineai.data.model.ExtensionOverviewState state) {
        cn.lineai.mvp.ExtensionKindDescriptor d = cn.lineai.mvp.ExtensionKindRegistry.getInstance().get(kind);
        if (d == null) {
            return null;
        }
        java.util.List<cn.lineai.model.ExtensionItemUiModel> items = new java.util.ArrayList<>();
        java.util.List<cn.lineai.mvp.ExtensionItem> mvpItems = d.getInstalledItems(state);
        if (mvpItems != null) {
            for (cn.lineai.mvp.ExtensionItem item : mvpItems) {
                items.add(new cn.lineai.model.ExtensionItemUiModel(
                        item.getId(), item.getName(), item.getDescription(), item.isEnabled()));
            }
        }
        return new cn.lineai.model.ExtensionKindUiModel(
                d.kind(),
                d.title(context),
                d.iconType(),
                d.sectionTitle(context),
                d.inlineTitle(context),
                d.inlineDesc(context),
                d.addActionType(),
                d.hasModifyAction(),
                d.emptyMessage(context),
                items
        );
    }

    private static View newModelAddScreen(Context context, MainChatView view, MainUiController controller,
                                         ModelProviderPreset preset, boolean local, ModelConfig editingModel) {
        final cn.lineai.ai.protocol.ModelCatalogClient catalogClient = new cn.lineai.ai.protocol.ModelCatalogClient();
        return new ModelAddScreenView(context, preset, local, editingModel, new ModelAddScreenView.Listener() {
            @Override
            public void onBack() {
                view.handleScreenBack();
            }

            @Override
            public void onSave(ModelConfig model) {
                controller.onModelSaved(model);
            }

            @Override
            public void onTest(ModelConfig model) {
                controller.onModelTest(model);
            }

            @Override
            public List<String> onFetchModelCatalog(cn.lineai.model.ModelProtocolType type, String baseUrl, String apiKey) throws Exception {
                return catalogClient.fetch(type, baseUrl, apiKey);
            }
        });
    }

    // ===== Settings screens =====

    public static final class SettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SettingsScreenView(context, new SettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onItem(String id) {
                    controller.onSettingsItemSelected(id);
                }
            });
        }

        @Override
        public String screenId() {
            return "settings";
        }
    }

    public static final class LlmSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new LLMSettingsScreenView(context, controller.getAiBehaviorSettings(), new LLMSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onToneModeChanged(String toneMode) {
                    controller.onAiToneModeChanged(toneMode);
                }

                @Override
                public void onReasoningEffortChanged(String effort) {
                    controller.onAiReasoningEffortChanged(effort);
                }

                @Override
                public void onThinkingScrollChanged(boolean enabled) {
                    controller.onAiThinkingScrollChanged(enabled);
                }

                @Override
                public void onThinkingAutoExpandChanged(boolean enabled) {
                    controller.onAiThinkingAutoExpandChanged(enabled);
                }

                @Override
                public void onPreserveReasoningChanged(boolean enabled) {
                    controller.onAiPreserveReasoningChanged(enabled);
                }

                @Override
                public void onLearningModeChanged(boolean enabled) {
                    controller.onAiLearningModeChanged(enabled);
                }

                @Override
                public void onSoftCompactionChanged(boolean enabled) {
                    controller.onAiSoftCompactionChanged(enabled);
                }

                @Override
                public void onOpenPromptTemplates() {
                    controller.onSettingsItemSelected("promptTemplates");
                }
            });
        }

        @Override
        public String screenId() {
            return "llm";
        }
    }

    public static final class PromptTemplatesScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new PromptTemplatesScreenView(context, controller.getPromptTemplates(), new PromptTemplatesScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onPromptTemplateSaved(String id, String value) {
                    controller.onPromptTemplateSaved(id, value);
                }

                @Override
                public void onPromptTemplateReset(String id) {
                    controller.onPromptTemplateReset(id);
                }
            });
        }

        @Override
        public String screenId() {
            return "promptTemplates";
        }
    }

    public static final class InputSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new InputSettingsScreenView(context, controller.getInputSettings(), new InputSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onEnterKeyBehaviorChanged(String behavior) {
                    controller.onEnterKeyBehaviorChanged(behavior);
                }
            });
        }

        @Override
        public String screenId() {
            return "input";
        }
    }

    public static final class ToolSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ToolSettingsScreenView(context, controller.getMcpSettingsState(),
                    imageUnderstandingModelLabel(controller), imageGenerationModelLabel(controller),
                    new ToolSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onWebSearchConfigChanged(WebSearchConfig config) {
                    controller.onMcpWebSearchConfigChanged(config);
                }

                @Override
                public void onOpenImageUnderstandingModelPicker() {
                    controller.onSettingsItemSelected("imageUnderstandingModel");
                }

                @Override
                public void onOpenImageGenerationModelPicker() {
                    controller.onSettingsItemSelected("imageGenerationModel");
                }
            });
        }

        @Override
        public String screenId() {
            return "toolSettings";
        }
    }

    public static final class McpSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new MCPSettingsScreenView(context, controller.getMcpSettingsState(), new MCPSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onExecutionModeChanged(String mode) {
                    controller.onMcpExecutionModeChanged(mode);
                }

                @Override
                public void onToolGroupChanged(String id, boolean enabled) {
                    controller.onMcpToolGroupChanged(id, enabled);
                }

                @Override
                public void onOpenSshSettings() {
                    controller.onSettingsItemSelected("sshSettings");
                }

                @Override
                public void onOpenTermuxIntegration() {
                    controller.onSettingsItemSelected("termuxIntegration");
                }
            });
        }

        @Override
        public String screenId() {
            return "mcp";
        }
    }

    public static final class OutputSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new OutputSettingsScreenView(context, controller.getOutputSettings(), new OutputSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onCodeWrapChanged(boolean enabled) {
                    controller.onCodeWrapChanged(enabled);
                }

                @Override
                public void onProcessAutoExpandChanged(boolean enabled) {
                    controller.onProcessAutoExpandChanged(enabled);
                }

                @Override
                public void onBrowserModeChanged(String mode) {
                    controller.onBrowserModeChanged(mode);
                }

                @Override
                public void onBrowserJavaScriptChanged(boolean enabled) {
                    controller.onBrowserJavaScriptChanged(enabled);
                }

                @Override
                public void onToolCallPreviewClicked() {
                    controller.onSettingsItemSelected("toolcall_preview");
                }
            });
        }

        @Override
        public String screenId() {
            return "output";
        }
    }

    public static final class ToolCallPreviewScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ToolCallPreviewScreenView(context, view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "toolcall_preview";
        }
    }

    public static final class SecuritySettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SecuritySettingsScreenView(context, controller.getOutputSettings(), new SecuritySettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onAllowAnyHttpChanged(boolean enabled) {
                    controller.onAllowAnyHttpChanged(enabled);
                }

                @Override
                public void onBrowserJavaScriptChanged(boolean enabled) {
                    controller.onBrowserJavaScriptChanged(enabled);
                }

                @Override
                public void onBypassPathProtectionChanged(boolean enabled) {
                    controller.onBypassPathProtectionChanged(enabled);
                }
            });
        }

        @Override
        public String screenId() {
            return "security";
        }
    }

    public static final class ThemeSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ThemeSettingsScreenView(context, controller.getThemeSettings(), new ThemeSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onThemeModeChanged(String mode) {
                    controller.onThemeModeChanged(mode);
                }

                @Override
                public void onCustomThemeColorsSaved(Map<String, String> colors) {
                    controller.onCustomThemeColorsSaved(colors);
                }
            });
        }

        @Override
        public String screenId() {
            return "theme";
        }
    }

    public static final class DataSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new DataSettingsScreenView(context, new DataSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onExport() {
                    controller.onLineCodeExportRequested();
                }

                @Override
                public void onImport() {
                    controller.onLineCodeImportRequested();
                }
            });
        }

        @Override
        public String screenId() {
            return "data";
        }
    }

    public static final class StorageManagementScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new StorageManagementScreenView(context, new StorageManagementScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onClearDiffCache() {
                    controller.onClearDiffCache();
                }

                @Override
                public void onClearChatHistory() {
                    controller.onClearChatHistory();
                }

                @Override
                public StorageStatsUiModel onLoadStats() {
                    return controller.getStorageStats();
                }
            });
        }

        @Override
        public String screenId() {
            return "storage";
        }
    }

    public static final class MemorySettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new MemorySettingsScreenView(context, new MemorySettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public cn.lineai.model.MemoryOverviewState getMemoryOverview() {
                    return controller.getMemoryOverview();
                }

                @Override
                public void onMemorySaved(String id, String scope, String content) {
                    controller.onMemorySaved(id, scope, content);
                }

                @Override
                public void onMemoryDeleted(String id) {
                    controller.onMemoryDeleted(id);
                }

                @Override
                public void onMemoriesDeleted(java.util.List<String> ids) {
                    controller.onMemoriesDeleted(ids);
                }
            });
        }

        @Override
        public String screenId() {
            return "memory";
        }
    }

    public static final class ErrorLogsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ErrorLogsScreenView(context, new ErrorLogsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public List<ErrorLogEntry> onLoadLogs() {
                    return controller.getErrorLogs();
                }

                @Override
                public void onClearLogs() {
                    controller.clearErrorLogs();
                }
            });
        }

        @Override
        public String screenId() {
            return "errorLogs";
        }
    }

    public static final class KeepAliveSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            PermissionUiHelper permissionUiHelper = context instanceof Activity
                    ? new PermissionUiHelper((Activity) context)
                    : null;
            return new KeepAliveSettingsScreenView(context, new KeepAliveSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onSettingsChanged() {
                    controller.onKeepAliveSettingsChanged();
                }

                @Override
                public KeepAliveSettings onLoadSettings() {
                    return controller.getKeepAliveSettings();
                }

                @Override
                public void onSetWakeLockEnabled(boolean enabled) {
                    controller.setKeepAliveWakeLockEnabled(enabled);
                }

                @Override
                public void onSetForegroundEnabled(boolean enabled) {
                    controller.setKeepAliveForegroundEnabled(enabled);
                }

                @Override
                public void onSetFakeAudioEnabled(boolean enabled) {
                    controller.setKeepAliveFakeAudioEnabled(enabled);
                }

                @Override
                public void onUpdateService() {
                    controller.updateKeepAliveService();
                }

                @Override
                public void onUpdateServiceStatus(String status) {
                    controller.updateKeepAliveServiceStatus(status);
                }

                @Override
                public void onRequestIgnoreBatteryOptimizations() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        context.startActivity(intent);
                    }
                }
            }, permissionUiHelper);
        }

        @Override
        public String screenId() {
            return "keepAlive";
        }
    }

    public static final class AdvancedFeaturesScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new AdvancedFeaturesScreenView(context, new AdvancedFeaturesScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpen(String id) {
                    if ("phoneControl".equals(id)) {
                        controller.onSettingsItemSelected("phoneControl");
                    }
                }
            });
        }

        @Override
        public String screenId() {
            return "advancedFeatures";
        }
    }

    public static final class PhoneControlScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            boolean accessibilityEnabled = controller.isPhoneControlAccessibilityEnabled();
            boolean disclaimerAccepted = controller.isPhoneControlDisclaimerAccepted();
            return new PhoneControlScreenView(context, accessibilityEnabled, disclaimerAccepted,
                    new PhoneControlScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpenAccessibilitySettings() {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                    } catch (RuntimeException ignored) {
                    }
                }

                @Override
                public void onPermissionEnabledChanged(String permissionId, boolean enabled) {
                    controller.onPhoneControlPermissionEnabledChanged(permissionId, enabled);
                }

                @Override
                public boolean isPermissionEnabled(String permissionId) {
                    return controller.isPhoneControlPermissionEnabled(permissionId);
                }

                @Override
                public void onSetPermissionEnabled(String permissionId, boolean enabled) {
                    controller.onPhoneControlSetPermissionEnabled(permissionId, enabled);
                }

                @Override
                public void onAcceptDisclaimer() {
                    controller.onPhoneControlAcceptDisclaimer();
                }
            });
        }

        @Override
        public String screenId() {
            return "phoneControl";
        }
    }

    public static final class SshSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SshSettingsScreenView(context, new SshSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpenTermuxIntegration() {
                    controller.onSettingsItemSelected("termuxIntegration");
                }

                @Override
                public SshConfig onLoadConfig() {
                    return controller.getSshConfig();
                }

                @Override
                public void onSaveConfig(SshConfig config) {
                    controller.saveSshConfig(config);
                }

                @Override
                public String onTestConnection(SshConfig config) throws Exception {
                    return controller.testSshConnection(config);
                }
            });
        }

        @Override
        public String screenId() {
            return "sshSettings";
        }
    }

    public static final class TermuxIntegrationScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new TermuxIntegrationScreenView(context, new TermuxIntegrationScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpenTermux() throws Exception {
                    controller.openTermux();
                }

                @Override
                public TermuxHelper.TermuxSetupResult onSetupTermuxSsh(int timeoutMs) throws Exception {
                    return controller.setupTermuxSsh(timeoutMs);
                }

                @Override
                public String onTestConnection(SshConfig config) throws Exception {
                    return controller.testSshConnection(config);
                }
            });
        }

        @Override
        public String screenId() {
            return "termuxIntegration";
        }
    }

    public static final class AboutScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new AboutScreenView(context, new AboutScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpenGithub() {
                    controller.onOpenUrl("https://github.com/LangLang03/LineCodePro");
                }

                @Override
                public void onOpenLicenses() {
                    controller.onSettingsItemSelected("licenses");
                }
            });
        }

        @Override
        public String screenId() {
            return "about";
        }
    }

    public static final class LicensesScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new LicensesScreenView(context, view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "licenses";
        }
    }

    public static final class TutorialScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new TutorialScreenView(context, view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "tutorial";
        }
    }

    /** 设置页顶部的教程入口：返回时回到设置页（区别于聊天"更多"菜单的 tutorial）。 */
    public static final class TutorialFromSettingsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new TutorialScreenView(context, view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "tutorialFromSettings";
        }
    }

    // ===== Model screens =====

    public static final class ModelListScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ModelListScreenView(context, controller.getModels(), controller.getSelectedModelId(), new ModelListScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onAddModel() {
                    controller.onSettingsItemSelected("modelAddOptions");
                }

                @Override
                public void onSelectModel(String id) {
                    controller.onModelSelected(id);
                }

                @Override
                public void onEditModel(String id) {
                    controller.onSettingsItemSelected("modelEdit:" + id);
                }

                @Override
                public void onDeleteModels(List<String> ids) {
                    controller.onModelsDeleted(ids);
                }
            });
        }

        @Override
        public String screenId() {
            return "models";
        }
    }

    public static final class ImageUnderstandingModelScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ModelListScreenView(context, controller.getModels(), controller.getImageUnderstandingModelId(),
                    context.getString(R.string.screen_models_pick_image_understanding), false, new ModelListScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onAddModel() {
                }

                @Override
                public void onSelectModel(String id) {
                    controller.onImageUnderstandingModelSelected(id);
                }

                @Override
                public void onEditModel(String id) {
                }

                @Override
                public void onDeleteModels(List<String> ids) {
                }
            });
        }

        @Override
        public String screenId() {
            return "imageUnderstandingModel";
        }
    }

    public static final class ImageGenerationModelScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ModelListScreenView(context, controller.getModels(), controller.getImageGenerationModelId(),
                    context.getString(R.string.screen_models_pick_image_generation), false, new ModelListScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onAddModel() {
                }

                @Override
                public void onSelectModel(String id) {
                    controller.onImageGenerationModelSelected(id);
                }

                @Override
                public void onEditModel(String id) {
                }

                @Override
                public void onDeleteModels(List<String> ids) {
                }
            });
        }

        @Override
        public String screenId() {
            return "imageGenerationModel";
        }
    }

    public static final class ModelAddOptionsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ModelAddOptionsScreenView(context, new ModelAddOptionsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onCustom() {
                    controller.onSettingsItemSelected("modelAdd");
                }

                @Override
                public void onLocal() {
                    controller.onSettingsItemSelected("modelAdd:local");
                }

                @Override
                public void onProvider(String id) {
                    controller.onSettingsItemSelected("modelAdd:preset:" + id);
                }
            });
        }

        @Override
        public String screenId() {
            return "modelAddOptions";
        }
    }

    public static final class ModelAddScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return newModelAddScreen(context, view, controller, null, false, null);
        }

        @Override
        public String screenId() {
            return "modelAdd";
        }
    }

    public static final class ModelAddLocalScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return newModelAddScreen(context, view, controller, null, true, null);
        }

        @Override
        public String screenId() {
            return "modelAdd:local";
        }
    }

    public static final class ModelAddPresetScreenFactory implements ScreenFactory {
        private static final String PREFIX = "modelAdd:preset:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            ModelProviderPreset preset = ModelProviderPresets.find(id.substring(PREFIX.length()));
            return newModelAddScreen(context, view, controller, preset, false, null);
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    public static final class ModelEditScreenFactory implements ScreenFactory {
        private static final String PREFIX = "modelEdit:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            ModelConfig model = controller.getModel(id.substring(PREFIX.length()));
            if (model != null) {
                boolean local = model.getProtocolType().name().equals("LOCAL_GGUF");
                return newModelAddScreen(context, view, controller, null, local, model);
            }
            return newModelAddScreen(context, view, controller, null, false, null);
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    // ===== Extension screens =====

    public static final class ExtensionsScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ExtensionsScreenView(context, new ExtensionsScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpen(String id) {
                    if ("terminalProvider".equals(id)) {
                        controller.onSettingsItemSelected("terminalProvider");
                    } else {
                        controller.onSettingsItemSelected("extension:" + id);
                    }
                }
            });
        }

        @Override
        public String screenId() {
            return "extensions";
        }
    }

    public static final class TerminalProviderScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            ExtensionOverviewState overview = controller.getExtensionOverview();
            return new TerminalProviderDetailScreenView(context,
                    controller.getTerminalProviderScanResults(),
                    overview.getIpcProviders(),
                    controller.hasTerminalProviderScanned(),
                    new TerminalProviderDetailScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onScanProviders() {
                    controller.onTerminalProviderScan();
                    view.showScreen(currentScreenId(view));
                }

                @Override
                public void onProviderAddConfirmed(IpcProviderConfig config) {
                    controller.onTerminalProviderSaved(config);
                }

                @Override
                public void onEnabledChanged(String id, boolean enabled) {
                    controller.onTerminalProviderEnabledChanged(id, enabled);
                }

                @Override
                public void onDelete(String id) {
                    controller.onTerminalProviderDeleted(id);
                }
            });
        }

        @Override
        public String screenId() {
            return "terminalProvider";
        }
    }

    public static final class AgentEditScreenFactory implements ScreenFactory {
        private static final String PREFIX = "agentEdit";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            ExtensionOverviewState overview = controller.getExtensionOverview();
            String agentId = id.substring(PREFIX.length()).replaceFirst("^:", "");
            ExtensionAgentConfig editingAgent = findAgent(overview, agentId);
            List<McpToolConfig> builtInMcps = controller.getMcpSettingsState().getConfigs();
            return new AgentExtensionEditScreenView(
                    context,
                    editingAgent,
                    controller.getExtensionAvailableTools(),
                    builtInMcps,
                    overview.getMcps(),
                    new AgentExtensionEditScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public ExtensionAgentConfig onGenerateDraft(String description) throws Exception {
                    return controller.onAgentDraftGenerated(description);
                }

                @Override
                public void onSave(ExtensionAgentConfig config) {
                    controller.onAgentExtensionSaved(config);
                }
            });
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return PREFIX.equals(id) || (id != null && id.startsWith(PREFIX + ":"));
        }
    }

    public static final class McpEditScreenFactory implements ScreenFactory {
        private static final String PREFIX = "mcpEdit";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            ExtensionOverviewState overview = controller.getExtensionOverview();
            String mcpId = id.substring(PREFIX.length()).replaceFirst("^:", "");
            ExtensionMcpConfig editingMcp = findMcp(overview, mcpId);
            return new McpExtensionEditScreenView(context, editingMcp, new McpExtensionEditScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public List<McpToolSummary> onQueryTools(String url, List<McpRequestHeader> headers) throws Exception {
                    return controller.onMcpToolsQuery(url, headers);
                }

                @Override
                public void onSave(ExtensionMcpConfig config) {
                    controller.onMcpExtensionSaved(config);
                }
            });
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return PREFIX.equals(id) || (id != null && id.startsWith(PREFIX + ":"));
        }
    }

    public static final class ExtensionDetailScreenFactory implements ScreenFactory {
        private static final String PREFIX = "extension:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            String kind = id.substring(PREFIX.length());
            cn.lineai.model.ExtensionKindUiModel uiModel = buildExtensionKindUiModel(context, kind, controller.getExtensionOverview());
            return new ExtensionDetailScreenView(context, uiModel, new ExtensionDetailScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onAddAgent() {
                    controller.onSettingsItemSelected("agentEdit");
                }

                @Override
                public void onEditAgent(String id) {
                    controller.onSettingsItemSelected("agentEdit:" + id);
                }

                @Override
                public void onAddMcp() {
                    controller.onSettingsItemSelected("mcpEdit");
                }

                @Override
                public void onEditMcp(String id) {
                    controller.onSettingsItemSelected("mcpEdit:" + id);
                }

                @Override
                public void onCreateSkill(String location, String name, String description, String content) {
                    controller.onSkillCreated(location, name, description, content);
                }

                @Override
                public void onInstallSkill(String location, String sourcePath, String name) {
                    try {
                        controller.onSkillInstalled(location, sourcePath, name);
                    } catch (Exception e) {
                        Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onInstallSkillFromUri(String location, String uri, String displayName) {
                    try {
                        controller.onSkillInstalledFromUri(location, uri, displayName);
                    } catch (Exception e) {
                        Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onInstallSkillFromGitHub(String location, String githubUrl) {
                    try {
                        controller.onSkillInstalledFromGitHub(location, githubUrl);
                    } catch (Exception e) {
                        Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onOpenSkillStore() {
                    controller.onSettingsItemSelected("skillStore");
                }

                @Override
                public void onEnabledChanged(String kind, String id, boolean enabled) {
                    controller.onExtensionEnabledChanged(kind, id, enabled);
                }

                @Override
                public void onDelete(String kind, String id) {
                    controller.onExtensionDeleted(kind, id);
                }

                @Override
                public void onDeleteMany(String kind, List<String> ids) {
                    controller.onExtensionsDeleted(kind, ids);
                }

                @Override
                public void onShareWorkspace() {
                    cn.lineai.workspace.WorkspaceShareHelper.shareHome(view.getContext());
                }
            });
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    public static final class SkillStoreScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SkillStoreScreenView(context, new SkillStoreScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpen(String slug) {
                    controller.onSettingsItemSelected("skillStoreDetail:" + slug);
                }

                @Override
                public void onLogin() {
                    controller.onSettingsItemSelected("skillHubLogin");
                }

                @Override
                public void onCenter() {
                    controller.onSettingsItemSelected("skillHubCenter");
                }

                @Override
                public void onPublish() {
                    controller.onSettingsItemSelected("skillHubPublish");
                }
            });
        }

        @Override
        public String screenId() {
            return "skillStore";
        }
    }

    public static final class SkillHubCenterScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SkillHubCenterScreenView(context, new SkillHubCenterScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onOpen(String destination) {
                    controller.onSettingsItemSelected("skillHubWeb:" + destination);
                }
            });
        }

        @Override
        public String screenId() {
            return "skillHubCenter";
        }
    }

    public static final class SkillHubWebScreenFactory implements ScreenFactory {
        private static final String PREFIX = "skillHubWeb:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            return new SkillHubWebScreenView(
                    context, id.substring(PREFIX.length()), view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    public static final class SkillHubPublishScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SkillHubPublishScreenView(
                    context,
                    controller.getExtensionOverview().getSkills(),
                    new SkillHubPublishScreenView.Listener() {
                        @Override
                        public void onBack() {
                            view.handleScreenBack();
                        }

                        @Override
                        public void onPublished() {
                            view.handleScreenBack();
                        }
                    });
        }

        @Override
        public String screenId() {
            return "skillHubPublish";
        }
    }

    public static final class SkillHubLoginScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new SkillHubLoginScreenView(
                    context,
                    view::handleScreenBack,
                    view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "skillHubLogin";
        }
    }

    public static final class SkillStoreDetailScreenFactory implements ScreenFactory {
        private static final String PREFIX = "skillStoreDetail:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            String slug = id.substring(PREFIX.length());
            return new SkillStoreDetailScreenView(context, slug, new SkillStoreDetailScreenView.Listener() {
                @Override
                public void onBack() {
                    view.handleScreenBack();
                }

                @Override
                public void onLogin() {
                    controller.onSettingsItemSelected("skillHubLogin");
                }

                @Override
                public void onOfficial(String namespace, String slug) {
                    String owner = namespace == null || namespace.length() == 0
                            ? "official" : namespace;
                    controller.onSettingsItemSelected(
                            "skillHubWeb:skill:" + owner + ":" + slug);
                }

                @Override
                public void onInstall(String location, String slug, String version) throws Exception {
                    controller.onSkillInstalledFromSkillHub(location, slug, version);
                }
            });
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    // ===== Browser screens =====

    public static final class BrowserScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new InAppBrowserScreenView(context, "about:blank",
                    controller.getOutputSettings().isBrowserJavaScriptEnabled(), view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "browser";
        }
    }

    public static final class BrowserPrefixScreenFactory implements ScreenFactory {
        private static final String PREFIX = "browser:";

        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            String id = currentScreenId(view);
            return new InAppBrowserScreenView(context, id.substring(PREFIX.length()),
                    controller.getOutputSettings().isBrowserJavaScriptEnabled(), view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return PREFIX;
        }

        @Override
        public boolean matches(String id) {
            return id != null && id.startsWith(PREFIX);
        }
    }

    // ===== Shell command screen =====

    public static final class ShellCommandScreenFactory implements ScreenFactory {
        @Override
        public View createScreen(MainChatView view, MainUiController controller, Context context) {
            return new ShellCommandScreenView(context, view.getShellCommandText(), view::handleScreenBack);
        }

        @Override
        public String screenId() {
            return "shellCommand";
        }
    }
}
