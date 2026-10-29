package cn.lineai.app;

import cn.lineai.runtime.AppGraph;
import cn.lineai.runtime.NextApp;
import cn.lineai.runtime.ScreenHost;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Scope;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;
import cn.lineai.feature.chat.ChatScreenView;
import cn.lineai.feature.extensions.AgentExtensionEditPage;
import cn.lineai.feature.extensions.AgentExtensionsPage;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.extensions.McpExtensionEditPage;
import cn.lineai.feature.extensions.LineCodeExtensionsPage;
import cn.lineai.feature.extensions.McpExtensionsPage;
import cn.lineai.feature.extensions.TerminalProvidersPage;
import cn.lineai.feature.extensions.SkillEditPage;
import cn.lineai.feature.extensions.GitHubSkillPage;
import cn.lineai.feature.extensions.SkillHubPage;
import cn.lineai.feature.extensions.SkillsExtensionsPage;
import cn.lineai.feature.models.ModelEditPage;
import cn.lineai.feature.models.ModelListPage;
import cn.lineai.feature.models.ModelScreens;
import cn.lineai.feature.settings.AdvancedFeaturesPage;
import cn.lineai.feature.settings.BehaviorSettingsPage;
import cn.lineai.feature.settings.KeepAliveSettingsPage;
import cn.lineai.feature.settings.McpPreferences;
import cn.lineai.feature.settings.MemoryPage;
import cn.lineai.feature.settings.PhoneControlPage;
import cn.lineai.feature.settings.ExtensionsPage;
import cn.lineai.feature.settings.PhoneControlPreferences;
import cn.lineai.feature.settings.McpSettingsPage;
import cn.lineai.feature.settings.ToolPreferences;
import cn.lineai.feature.settings.SshSettingsPage;
import cn.lineai.feature.settings.TermuxIntegrationPage;
import cn.lineai.feature.settings.TutorialPage;
import cn.lineai.feature.settings.ToolSettingsPage;
import cn.lineai.feature.workspace.ChangesPage;
import cn.lineai.feature.workspace.WorkspaceProjectsPage;
import cn.lineai.feature.workspace.WorkspaceScreens;
import cn.lineai.feature.settings.InputSettingsPage;
import cn.lineai.feature.settings.OutputSettingsPage;
import cn.lineai.feature.settings.SettingsAction;
import cn.lineai.feature.settings.SecuritySettingsPage;
import cn.lineai.feature.settings.DataSettingsPage;
import cn.lineai.feature.settings.ErrorLogsPage;
import cn.lineai.feature.settings.StorageManagementPage;
import cn.lineai.feature.settings.SettingsHome;
import cn.lineai.feature.settings.SettingsScreens;
import cn.lineai.feature.settings.SettingsState;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.kit.WebPage;
import cn.lineai.ui.tokens.Tokens;

public final class MainActivity extends Activity implements ChatScreenView.Host, cn.lineai.runtime.PageHost {
    private static final ScreenId<UiState, Params.Empty> CHAT = new ScreenId<>("chat", null, true, Params.Empty::fromArgs);
    private AppGraph graph;
    private Tokens tokens;
    private ChatScreenView chat;
    private ScreenHost screens;
    private BackStack navigation;
    private cn.lineai.runtime.PageFactory pages;
    private final Scope scope = new Scope();
    private ErrorCode lastSettingsError;
    private Dialog dialog;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        graph = ((NextApp) getApplication()).graph();
        graph.setAgentRunner(new NestedAgentRunner(graph.modelClient(), graph.models(),
                graph.agentBaseTools(), graph.agentToolContext(), graph.scheduler()));
        showCrashIfNeeded();
        importCrashLog();
        tokens = graph.theme().current();
        configureWindow();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(tokens.bg);
        chat = new ChatScreenView(this, graph.chat(), tokens);
        chat.setHost(this);
        root.addView(chat, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        installInsets(overlay);
        setContentView(root);
        ScreenRegistry registry = new ScreenRegistry().register(CHAT).register(cn.lineai.runtime.PageFactory.BROWSER);
        SettingsScreens.register(registry);
        ModelScreens.register(registry);
        ExtensionScreens.register(registry);
        WorkspaceScreens.register(registry);
        navigation = new BackStack(registry, graph.graph().get(Scheduler.class));
        navigation.push(CHAT.key(Params.Empty.INSTANCE));
        pages = new cn.lineai.runtime.PageFactory(this);
        screens = new ScreenHost(overlay, navigation, this::createScreen);
        scope.track(navigation.observe(entries -> chat.setImportantForAccessibility(entries.size() > 1
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)));
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
        if (saved != null) {
            screens.restore(saved.getBundle("navigation"), registry);
            chat.restoreComposer(saved.getBundle("composer"));
        }
        scope.track(graph.theme().observe(t -> {
            tokens = t;
            root.setBackgroundColor(t.bg);
            chat.applyTokens(t);
            screens.applyTokens(t);
        }));
        scope.track(graph.preferences().observe(this::settingsChanged));
        graph.chat().setModelMissingHandler(this::onOpenModelSettings);
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
    }

    private void installInsets(FrameLayout overlay) {
        overlay.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                overlay.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            } else {
                overlay.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    private View createScreen(ScreenKey<?, ?> key) {
        return pages.create(key);
    }

    @Override public void settingsDestination(SettingsHome.Destination destination) {
        switch (destination) {
            case INPUT: navigation.push(SettingsScreens.INPUT.key(Params.Empty.INSTANCE)); break;
            case OUTPUT: navigation.push(SettingsScreens.OUTPUT.key(Params.Empty.INSTANCE)); break;
            case BEHAVIOR: navigation.push(SettingsScreens.BEHAVIOR.key(Params.Empty.INSTANCE)); break;
            case THEME: navigation.push(SettingsScreens.THEME.key(Params.Empty.INSTANCE)); break;
            case ABOUT: navigation.push(SettingsScreens.ABOUT.key(Params.Empty.INSTANCE)); break;
            case SECURITY: navigation.push(SettingsScreens.SECURITY.key(Params.Empty.INSTANCE)); break;
            case ADVANCED: navigation.push(SettingsScreens.ADVANCED.key(Params.Empty.INSTANCE)); break;
            case KEEP_ALIVE: navigation.push(SettingsScreens.KEEP_ALIVE.key(Params.Empty.INSTANCE)); break;
            case STORAGE: navigation.push(SettingsScreens.STORAGE.key(Params.Empty.INSTANCE)); break;
            case LOGS: navigation.push(SettingsScreens.ERROR_LOGS.key(Params.Empty.INSTANCE)); break;
            case DATA: navigation.push(SettingsScreens.DATA.key(Params.Empty.INSTANCE)); break;
            case MCP: navigation.push(SettingsScreens.MCP.key(Params.Empty.INSTANCE)); break;
            case TUTORIAL: navigation.push(SettingsScreens.TUTORIAL.key(Params.Empty.INSTANCE)); break;
            case MEMORY: navigation.push(SettingsScreens.MEMORY.key(Params.Empty.INSTANCE)); break;
            case EXTENSIONS: navigation.push(SettingsScreens.EXTENSIONS.key(Params.Empty.INSTANCE)); break;
            case TOOLS: navigation.push(SettingsScreens.TOOL_SETTINGS.key(Params.Empty.INSTANCE)); break;
            case MODELS: navigation.push(ModelScreens.LIST.key(Params.Empty.INSTANCE)); break;
            default: unavailable();
        }
    }

    private void settingsChanged(SettingsState state) {
        chat.preferences(state.values, this::openLink);
        screens.visit(view -> {
            if (view instanceof InputSettingsPage) ((InputSettingsPage) view).render(state);
            if (view instanceof OutputSettingsPage) ((OutputSettingsPage) view).render(state);
            if (view instanceof BehaviorSettingsPage) ((BehaviorSettingsPage) view).render(state);
            if (view instanceof SecuritySettingsPage) ((SecuritySettingsPage) view).render(state);
            if (view instanceof KeepAliveSettingsPage) ((KeepAliveSettingsPage) view).render(state);
        });
        if (state.error != null && state.error != lastSettingsError) {
            Toast.makeText(this, state.loaded ? R.string.settings_save_failed : R.string.settings_load_failed, Toast.LENGTH_LONG).show();
        }
        lastSettingsError = state.error;
    }

    @Override public void openLink(String url) {
        cn.lineai.core.model.UiPreferences values = graph.preferences().state().values;
        if (!cn.lineai.platform.LinkPolicy.allows(url, values.allowAnyHttp())) {
            Toast.makeText(this, R.string.browser_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!graph.preferences().state().values.externalBrowser()) {
            navigation.push(cn.lineai.runtime.PageFactory.BROWSER.key(Params.Args.of("url", url)));
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.browser_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onOpenSettings() {
        hideKeyboard();
        SettingsState state = graph.preferences().state();
        if (!state.loaded && state.error != null) graph.preferences().dispatch(new SettingsAction.Load());
        navigation.push(SettingsScreens.HOME.key(Params.Empty.INSTANCE));
    }
    @Override public void onOpenModelSettings() {
        hideKeyboard();
        navigation.push(ModelScreens.LIST.key(Params.Empty.INSTANCE));
    }

    @Override public void onModelPicker() {
        hideKeyboard();
        cn.lineai.runtime.DialogFactory.showModelPicker(this);
    }

    @Override public cn.lineai.tool.api.ToolPresentation toolPresentation(String name,
                                                                          String argumentsJson) {
        return graph.tools().registry().presentation(name, argumentsJson);
    }

    @Override public void loadWorkspaceTree(
            java.util.function.Consumer<cn.lineai.feature.chat.DrawerPanel.FileNode> callback) {
        cn.lineai.runtime.ChatSheets.loadWorkspaceTree(this, callback);
    }

    @Override public cn.lineai.feature.chat.ChatCapabilities capabilities() {
        return cn.lineai.feature.chat.ChatCapabilities.of(graph.models().selected());
    }

    @Override public void onVideoPicker() {
        HostActions.pickVisualMedia(this, "video/*", cn.lineai.runtime.SafActions.REQUEST_VIDEO);
    }

    @Override public void onAttachmentPicker() {
        hideKeyboard();
        cn.lineai.runtime.ChatSheets.showAttachmentTree(this,
                chat::fileAttachmentPaths, chat::toggleFileAttachment);
    }

    @Override public void onImagePicker() {
        onAttach();
    }

    @Override public boolean projectRemovable() {
        return graph.projects().list().size() > 1;
    }

    @Override public void onProjectRemoveRequested() {
        cn.lineai.core.model.Project selected = graph.projects().selected();
        if (selected == null || !projectRemovable()) {
            return;
        }
        final cn.lineai.core.model.Project removing = selected;
        Dialogs.confirm(this, tokens, getString(R.string.drawer_project_remove_title),
                getString(R.string.drawer_project_remove_message, removing.name),
                getString(R.string.common_cancel), getString(R.string.common_delete),
                confirmed -> {
                    if (!confirmed) {
                        return;
                    }
                    graph.projects().delete(removing.id);
                    applyProjectLabel(graph.projects().selected());
                    graph.chat().refreshModel();
                });
    }

    @Override public void showChatAttachmentPicker() {
        onAttachmentPicker();
    }

    @Override public void pickChatImage() {
        onAttach();
    }

    @Override public void onReasoning() {
        hideKeyboard();
        cn.lineai.runtime.DialogFactory.showReasoning(this);
    }

    @Override public void onMore() {
        hideKeyboard();
        cn.lineai.runtime.DialogFactory.showMore(this);
    }

    @Override public void openDrawer() {
        chat.openDrawer();
    }

    @Override public String projectLabel() {
        cn.lineai.core.model.Project project = graph.projects().selected();
        return project == null ? "" : project.name;
    }

    @Override public String projectPath() {
        cn.lineai.core.model.Project project = graph.projects().selected();
        return project == null ? "" : project.path;
    }

    @Override public cn.lineai.feature.chat.toolcall.ToolDiffLookup.Snapshot toolDiff(String diffId) {
        return HostActions.toolDiff(graph, diffId);
    }

    @Override public void openFile(String relativePath) {
        HostActions.openWorkspaceFile(this, graph, relativePath);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            android.view.inputmethod.InputMethodManager input =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            input.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    @Override public void onProject() {
        // The workspace page is a utility surface: it appears over the chat instantly instead
        // of running the page slide transition.
        navigation.push(WorkspaceScreens.PROJECTS.key(Params.Empty.INSTANCE), false);
    }

    @Override public java.util.List<cn.lineai.core.model.WorkspaceNode> workspaceFiles(String path) {
        return HostActions.workspaceFiles(graph, path);
    }
    @Override public void onPermission() {
        showPermissionSheet();
    }

    private void showPermissionSheet() {
        cn.lineai.runtime.DialogFactory.showPermission(this);
    }

    @Override public void onAttachFile() {
        HostActions.pickDocument(this, "*/*", null, cn.lineai.runtime.SafActions.REQUEST_ATTACH_FILE);
    }

    @Override public void onAttach() {
        HostActions.pickVisualMedia(this, "image/*", cn.lineai.runtime.SafActions.REQUEST_ATTACH);
    }

    @Override public void pickToolModel(boolean understanding) {
        cn.lineai.runtime.ModelPickerFactory.pick(this, understanding);
    }

    @Override public void saveToolModel(boolean understanding, String id) {
        cn.lineai.runtime.ModelPickerFactory.save(this, understanding, id);
    }


    @Override public void requestExport() {
        cn.lineai.runtime.ArchiveActions.requestExport(this);
    }

    @Override public void requestImport() {
        cn.lineai.runtime.ArchiveActions.requestImport(this);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (cn.lineai.runtime.ArchiveActions.handleResult(this, requestCode, uri)) return;
            cn.lineai.runtime.SafActions.handle(this, pages, requestCode, uri);
        } catch (RuntimeException error) {
            android.util.Log.e("LineCodeSAF", "SAF result failed", error);
            try {
                graph.errorLogs().record(getString(R.string.workspace_provider_open_failed),
                        "SAF request " + requestCode, android.util.Log.getStackTraceString(error));
            } catch (RuntimeException ignored) {
                android.util.Log.e("LineCodeSAF", "cannot record SAF failure", ignored);
            }
            Toast.makeText(this, R.string.workspace_provider_open_failed,
                    Toast.LENGTH_LONG).show();
        }
    }


    @Override public void updateKeepAliveService() {
        HostActions.updateKeepAlive(this, graph);
    }

    @Override public boolean isIgnoringBatteryOptimizations() {
        return HostActions.isIgnoringBatteryOptimizations(this);
    }

    @Override public void requestBatteryOptimizationExemption() {
        HostActions.requestBatteryOptimizationExemption(this);
    }

    @Override public void probeModel(cn.lineai.core.model.ModelConfig model) {
        cn.lineai.runtime.ModelPickerFactory.probe(this, model);
    }


    @Override public Activity activity() { return this; }
    @Override public Tokens tokens() { return tokens; }
    @Override public AppGraph graph() { return graph; }
    @Override public BackStack navigation() { return navigation; }
    @Override public void pickSkillArchive() {
        HostActions.pickDocument(this, "*/*", new String[] {
                "application/zip", "text/markdown", "text/plain"},
                cn.lineai.runtime.SafActions.REQUEST_SKILL_IMPORT);
    }

    @Override public void pickLocalModelFile() {
        HostActions.pickDocument(this, "*/*", null, cn.lineai.runtime.SafActions.REQUEST_MODEL_FILE);
    }

    @Override public void addChatImage(String path) { chat.addAttachment(path); }

    @Override public void addChatFile(String path) { chat.addFileAttachment(path); }

    @Override public void openExternalWorkspace() {
        HostActions.openExternalWorkspace(this, tokens, graph);
    }

    @Override public void shareWorkspace() {
        HostActions.shareWorkspace(this);
    }

    @Override public void applyProjectLabel(cn.lineai.core.model.Project project) {
        graph.settings().put(cn.lineai.feature.chat.ChatController.KEY_PROJECT_LABEL,
                project == null ? "" : project.name);
        graph.chat().refreshModel();
    }

    @Override public void unavailable() { Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show(); }

    @SuppressLint("GestureBackNavigation") // API 33+ is wired through OnBackInvokedDispatcher above.
    @Override public void onBackPressed() {
        if (navigation.entries().size() > 1) navigation.back();
        else if (!chat.handleBack()) super.onBackPressed();
    }


    private void showCrashIfNeeded() {
        HostActions.showCrashIfNeeded(this);
    }

    private void importCrashLog() {
        HostActions.importCrashLog(this, graph);
    }

    @Override protected void onStart() { super.onStart(); chat.start(); }
    @Override protected void onStop() { chat.stop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBundle("navigation", screens.save());
        out.putBundle("composer", chat.saveComposer());
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        graph.chat().setModelMissingHandler(null);
        scope.close();
        screens.close();
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    @Override public void onChatMode() {
        showChatModeSheet();
    }

    @Override public void setChatMode(String mode) {
        cn.lineai.feature.chat.ToolModes.setChatMode(graph.settings(), mode);
        graph.chat().refreshModel();
    }

    private void showChatModeSheet() {
        cn.lineai.runtime.DialogFactory.showChatMode(this);
    }

    @Override public void onContextUsage() {
        if (dialog != null) dialog.dismiss();
        dialog = cn.lineai.runtime.DialogFactory.showContextUsage(this);
    }
}
