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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import cn.lineai.core.service.SettingsAction;
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

/**
 * Application shell activity: owns the chat surface, the overlay screen host and the
 * navigation back stack, and implements the {@code ChatScreenView.Host} and
 * {@code PageHost} contracts by delegating to the app graph and {@link HostActions}.
 */
public final class MainActivity extends Activity implements ChatScreenView.Host, cn.lineai.runtime.PageHost {
    /** Root chat screen; the only entry that renders underneath the overlay host. */
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

    /** Builds the chat view, overlay host and back stack; restores saved navigation. */
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

    /** Edge-to-edge window configuration with transparent system bars. */
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

    /** Pads the overlay for system bars and the keyboard on every API level. */
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

    /** Screen-host factory hook delegating to the page factory. */
    private View createScreen(ScreenKey<?, ?> key) {
        return pages.create(key);
    }

    /** Pushes the screen registered for the selected settings home entry. */
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

    /** Pushes settings state to the chat view and every visible settings page. */
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

    /** Applies the link policy and opens the URL in-app or in the external browser. */
    @Override public void openLink(String url) {
        cn.lineai.core.model.UiPreferences values = graph.preferences().state().values;
        if (!cn.lineai.core.service.LinkPolicy.allows(url, values.allowAnyHttp())) {
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

    /** Opens the settings home page. */
    @Override public void onOpenSettings() {
        hideKeyboard();
        SettingsState state = graph.preferences().state();
        if (!state.loaded && state.error != null) graph.preferences().dispatch(new SettingsAction.Load());
        navigation.push(SettingsScreens.HOME.key(Params.Empty.INSTANCE));
    }
    /** Opens the model list; wired as the chat model-missing handler. */
    @Override public void onOpenModelSettings() {
        hideKeyboard();
        navigation.push(ModelScreens.LIST.key(Params.Empty.INSTANCE));
    }

    /** Opens the chat model picker sheet. */
    @Override public void onModelPicker() {
        hideKeyboard();
        cn.lineai.feature.chat.ChatSheets.showModelPicker(this);
    }

    /** Resolves tool card presentation from the catalog registry. */
    @Override public cn.lineai.tool.api.ToolPresentation toolPresentation(String name,
                                                                          String argumentsJson) {
        return graph.tools().registry().presentation(name, argumentsJson);
    }

    /** Loads the workspace drawer tree through the chat sheets helper. */
    @Override public void loadWorkspaceTree(
            java.util.function.Consumer<cn.lineai.feature.chat.DrawerPanel.FileNode> callback) {
        cn.lineai.feature.chat.ChatSheets.loadWorkspaceTree(this, callback);
    }

    /** Chat capabilities derived from the selected model. */
    @Override public cn.lineai.feature.chat.ChatCapabilities capabilities() {
        return cn.lineai.feature.chat.ChatCapabilities.of(graph.models().selected());
    }

    /** Launches the media picker for videos. */
    @Override public void onVideoPicker() {
        HostActions.pickVisualMedia(this, "video/*", cn.lineai.runtime.SafActions.REQUEST_VIDEO);
    }

    /** Opens the workspace file attachment sheet. */
    @Override public void onAttachmentPicker() {
        hideKeyboard();
        cn.lineai.feature.chat.ChatSheets.showAttachmentTree(this,
                chat::fileAttachmentPaths, chat::toggleFileAttachment);
    }

    /** Launches the image attachment picker. */
    @Override public void onImagePicker() {
        onAttach();
    }

    /** Whether more than one project exists, so the selected one may be removed. */
    @Override public boolean projectRemovable() {
        return graph.projects().list().size() > 1;
    }

    /** Confirms and deletes the selected project, refreshing the label and chat model. */
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

    /** Delegates to the attachment picker. */
    @Override public void showChatAttachmentPicker() { onAttachmentPicker(); }

    /** Delegates to the image picker. */
    @Override public void pickChatImage() {
        onAttach();
    }

    /** Opens the reasoning effort picker sheet. */
    @Override public void onReasoning() {
        hideKeyboard();
        cn.lineai.feature.chat.ChatSheets.showReasoningPicker(this);
    }

    /** Opens the chat "more" menu sheet. */
    @Override public void onMore() {
        hideKeyboard();
        cn.lineai.feature.chat.ChatSheets.showMore(this);
    }

    @Override public void openDrawer() {
        chat.openDrawer();
    }

    /** Name of the selected project; empty when none is selected. */
    @Override public String projectLabel() {
        cn.lineai.core.model.Project project = graph.projects().selected();
        return project == null ? "" : project.name;
    }

    /** Filesystem path of the selected project; empty when none is selected. */
    @Override public String projectPath() {
        cn.lineai.core.model.Project project = graph.projects().selected();
        return project == null ? "" : project.path;
    }

    /** Resolves a recorded diff for a tool card. */
    @Override public cn.lineai.feature.chat.toolcall.ToolDiffLookup.Snapshot toolDiff(String diffId) {
        return HostActions.toolDiff(graph, diffId);
    }

    /** Opens a workspace file in an external viewer through the content provider. */
    @Override public void openFile(String relativePath) {
        HostActions.openWorkspaceFile(this, graph, relativePath);
    }

    /** Hides the keyboard and clears focus before pushing a page or sheet. */
    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            android.view.inputmethod.InputMethodManager input =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            input.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    /** Pushes the workspace page without the slide transition. */
    @Override public void onProject() {
        // The workspace page is a utility surface: it appears over the chat instantly instead
        // of running the page slide transition.
        navigation.push(WorkspaceScreens.PROJECTS.key(Params.Empty.INSTANCE), false);
    }

    /** Opens the permission sheet. */
    @Override public void onPermission() {
        showPermissionSheet();
    }

    /** Opens the permissions explanation sheet. */
    private void showPermissionSheet() {
        cn.lineai.feature.chat.ChatSheets.showPermission(this);
    }

    /** Launches the document picker for file attachments. */
    @Override public void onAttachFile() {
        HostActions.pickDocument(this, "*/*", null, cn.lineai.runtime.SafActions.REQUEST_ATTACH_FILE);
    }

    /** Launches the media picker for image attachments. */
    @Override public void onAttach() {
        HostActions.pickVisualMedia(this, "image/*", cn.lineai.runtime.SafActions.REQUEST_ATTACH);
    }

    /** Opens the tool model picker sheet for the given image role. */
    @Override public void pickToolModel(boolean understanding) {
        cn.lineai.runtime.ModelPickerFactory.pick(this, understanding);
    }

    /** Persists the tool model selection and refreshes the tool settings page. */
    @Override public void saveToolModel(boolean understanding, String id) {
        cn.lineai.runtime.ModelPickerFactory.save(this, understanding, id);
    }

    /** Launches the SAF archive export picker. */
    @Override public void requestExport() {
        cn.lineai.runtime.ArchiveActions.requestExport(this);
    }

    /** Launches the SAF chat-only export picker. */
    @Override public void requestChatExport() { cn.lineai.feature.chat.ChatExportActions.request(this); }

    /** Launches the SAF archive import picker. */
    @Override public void requestImport() {
        cn.lineai.runtime.ArchiveActions.requestImport(this);
    }

    /** Dispatches SAF results to archive, chat export and runtime flows, logging failures. */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (cn.lineai.runtime.ArchiveActions.handleResult(this, requestCode, uri)) return;
            if (cn.lineai.feature.chat.ChatExportActions.handleResult(this, requestCode, uri)) {
                return;
            }
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

    /** Applies the keep-alive preferences to the foreground service. */
    @Override public void updateKeepAliveService() {
        HostActions.updateKeepAlive(this, graph);
    }

    /** Current battery optimization exemption state. */
    @Override public boolean isIgnoringBatteryOptimizations() {
        return HostActions.isIgnoringBatteryOptimizations(this);
    }

    /** Requests the battery optimization exemption. */
    @Override public void requestBatteryOptimizationExemption() {
        HostActions.requestBatteryOptimizationExemption(this);
    }

    /** Runs the model probe flow with dialog reporting. */
    @Override public void probeModel(cn.lineai.core.model.ModelConfig model) {
        cn.lineai.runtime.ModelPickerFactory.probe(this, model);
    }

    /** This activity as view context. */
    @Override public Activity activity() { return this; }
    /** Current design tokens. */
    @Override public Tokens tokens() { return tokens; }
    /** Application graph. */
    @Override public AppGraph graph() { return graph; }
    /** Shared navigation back stack. */
    @Override public BackStack navigation() { return navigation; }
    /** Launches the skill archive picker with zip and markdown mime filters. */
    @Override public void pickSkillArchive() {
        HostActions.pickDocument(this, "*/*", new String[] {
                "application/zip", "text/markdown", "text/plain"},
                cn.lineai.runtime.SafActions.REQUEST_SKILL_IMPORT);
    }

    /** Launches the document picker for GGUF model files. */
    @Override public void pickLocalModelFile() {
        HostActions.pickDocument(this, "*/*", null, cn.lineai.runtime.SafActions.REQUEST_MODEL_FILE);
    }

    /** Adds an image path to the chat composer attachments. */
    @Override public void addChatImage(String path) { chat.addAttachment(path); }

    /** Adds a file path to the chat composer attachments. */
    @Override public void addChatFile(String path) { chat.addFileAttachment(path); }

    /** Opens the external workspace flow, requesting all-files access when required. */
    @Override public void openExternalWorkspace() {
        HostActions.openExternalWorkspace(this, tokens, graph);
    }

    /** Shares the workspace root with read and write grants. */
    @Override public void shareWorkspace() {
        HostActions.shareWorkspace(this);
    }

    /** Stores the project label and refreshes the chat model context. */
    @Override public void applyProjectLabel(cn.lineai.core.model.Project project) {
        graph.settings().put(cn.lineai.feature.chat.ChatController.KEY_PROJECT_LABEL,
                project == null ? "" : project.name);
        graph.chat().refreshModel();
    }

    /** Shows the generic not-available toast. */
    @Override public void unavailable() { Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show(); }

    // ChatHost: the narrow slice the chat sheets need, delegated to the app graph.
    /** Chat controller from the graph. */
    @Override public cn.lineai.feature.chat.ChatController chat() { return graph.chat(); }
    /** Model store from the graph. */
    @Override public cn.lineai.data.api.ModelStore models() { return graph.models(); }
    /** Project store from the graph. */
    @Override public cn.lineai.data.api.ProjectStore projects() { return graph.projects(); }
    /** Settings store from the graph. */
    @Override public cn.lineai.data.api.SettingsStore settings() { return graph.settings(); }
    /** Forwards settings actions to the preferences store. */
    @Override public void dispatchSettings(cn.lineai.core.service.SettingsAction action) { graph.preferences().dispatch(action); }
    /** Scheduler from the graph. */
    @Override public cn.lineai.core.kernel.time.Scheduler scheduler() { return graph.graph().get(cn.lineai.core.kernel.time.Scheduler.class); }
    /** Pushes the tutorial page. */
    @Override public void openTutorial() { navigation.push(cn.lineai.feature.settings.SettingsScreens.TUTORIAL.key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)); }
    /** Pushes the settings home page. */
    @Override public void openSettings() { navigation.push(cn.lineai.feature.settings.SettingsScreens.HOME.key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)); }
    /** Pushes the workspace projects page. */
    @Override public void openWorkspaceProjects() { navigation.push(cn.lineai.feature.workspace.WorkspaceScreens.PROJECTS.key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)); }

    @SuppressLint("GestureBackNavigation") // API 33+ is wired through OnBackInvokedDispatcher above.
    /** Pops the back stack first, then lets the chat view consume back events. */
    @Override public void onBackPressed() {
        if (navigation.entries().size() > 1) navigation.back();
        else if (!chat.handleBack()) super.onBackPressed();
    }

    /** Opens the crash screen when a crash report from a previous run exists. */
    private void showCrashIfNeeded() {
        HostActions.showCrashIfNeeded(this);
    }

    /** Moves a pending crash log into the error log store. */
    private void importCrashLog() {
        HostActions.importCrashLog(this, graph);
    }

    /** Starts chat streaming and its observation. */
    @Override protected void onStart() { super.onStart(); chat.start(); }
    /** Stops chat streaming while the activity is not visible. */
    @Override protected void onStop() { chat.stop(); super.onStop(); }
    /** Saves navigation and composer state for restoration after process death. */
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBundle("navigation", screens.save());
        out.putBundle("composer", chat.saveComposer());
        super.onSaveInstanceState(out);
    }
    /** Detaches the model-missing handler and releases all tracked scopes and pages. */
    @Override protected void onDestroy() {
        graph.chat().setModelMissingHandler(null);
        scope.close();
        screens.close();
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    /** Opens the chat mode sheet. */
    @Override public void onChatMode() {
        showChatModeSheet();
    }

    /** Persists the chat mode and refreshes the chat model. */
    @Override public void setChatMode(String mode) {
        cn.lineai.feature.chat.ToolModes.setChatMode(graph.settings(), mode);
        graph.chat().refreshModel();
    }

    /** Opens the chat mode selection sheet. */
    private void showChatModeSheet() {
        cn.lineai.feature.chat.ChatMenuSheets.showChatMode(this);
    }

    /** Shows the context usage dialog, replacing a previous one. */
    @Override public void onContextUsage() {
        if (dialog != null) dialog.dismiss();
        dialog = cn.lineai.feature.chat.ChatSheets.showContextUsage(this);
    }
}
