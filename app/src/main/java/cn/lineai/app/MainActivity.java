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

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            android.view.inputmethod.InputMethodManager input = getSystemService(android.view.inputmethod.InputMethodManager.class);
            input.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    @Override public void onProject() {
        navigation.push(WorkspaceScreens.PROJECTS.key(Params.Empty.INSTANCE));
    }

    @Override public java.util.List<cn.lineai.core.model.WorkspaceNode> workspaceFiles(String path) {
        cn.lineai.core.model.Project project = graph.projects().selected();
        if (project == null) return java.util.Collections.emptyList();
        java.io.File baseDir = new java.io.File(project.path);
        if (!baseDir.isDirectory()) return java.util.Collections.emptyList();
        java.util.List<cn.lineai.core.model.WorkspaceNode> nodes = new java.util.ArrayList<>();
        try {
            java.io.File base = baseDir.getCanonicalFile();
            java.io.File dir = path == null || path.isEmpty()
                    ? base : new java.io.File(base, path).getCanonicalFile();
            if (!dir.equals(base) && !dir.getPath().startsWith(base.getPath() + java.io.File.separator)) {
                return nodes;
            }
            java.io.File[] children = dir.listFiles();
            if (children == null) return nodes;
            java.util.Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (java.io.File child : children) {
                if (nodes.size() >= 300) break;
                String relative = base.toPath().relativize(child.toPath()).toString();
                nodes.add(new cn.lineai.core.model.WorkspaceNode(
                        child.getName(), relative, child.isDirectory()));
            }
        } catch (java.io.IOException ignored) {
            return java.util.Collections.emptyList();
        }
        return nodes;
    }
    @Override public void onPermission() {
        showPermissionSheet();
    }

    private void showPermissionSheet() {
        cn.lineai.runtime.DialogFactory.showPermission(this);
    }

    @Override public void onAttachFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, cn.lineai.runtime.SafActions.REQUEST_ATTACH_FILE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onAttach() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, cn.lineai.runtime.SafActions.REQUEST_ATTACH);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
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
        if (cn.lineai.runtime.ArchiveActions.handleResult(this, requestCode, uri)) return;
        cn.lineai.runtime.SafActions.handle(this, pages, requestCode, uri);
    }

    private void loadStorage(java.util.function.Consumer<cn.lineai.data.api.StorageStats> consumer) {
        graph.graph().get(Scheduler.class).io(() -> {
            cn.lineai.data.api.StorageStats stats = graph.storageStats().load();
            graph.graph().get(Scheduler.class).main(() -> consumer.accept(stats));
        });
    }

    @Override public void updateKeepAliveService() {
        cn.lineai.core.model.UiPreferences values = graph.preferences().state().values;
        boolean wake = values.keepAliveWakeLock();
        boolean foreground = values.keepAliveForeground();
        boolean audio = values.keepAliveFakeAudio();
        cn.lineai.platform.KeepAliveService.start(this, wake, foreground, audio);
        if ((foreground || audio) && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 7301);
        }
    }

    @Override public boolean isIgnoringBatteryOptimizations() {
        android.os.PowerManager power = (android.os.PowerManager)
                getSystemService(android.os.PowerManager.class);
        return power == null || power.isIgnoringBatteryOptimizations(getPackageName());
    }

    @Override public void requestBatteryOptimizationExemption() {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    @Override public void probeModel(cn.lineai.core.model.ModelConfig model) {
        cn.lineai.runtime.ModelPickerFactory.probe(this, model);
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override public Activity activity() { return this; }
    @Override public Tokens tokens() { return tokens; }
    @Override public AppGraph graph() { return graph; }
    @Override public BackStack navigation() { return navigation; }
    @Override public void pickSkillArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "text/markdown", "text/plain"});
        try {
            startActivityForResult(intent, cn.lineai.runtime.SafActions.REQUEST_SKILL_IMPORT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void pickLocalModelFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, cn.lineai.runtime.SafActions.REQUEST_MODEL_FILE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void addChatImage(String path) { chat.addAttachment(path); }

    @Override public void addChatFile(String path) { chat.addFileAttachment(path); }

    @Override public void openExternalWorkspace() {
        if (android.os.Build.VERSION.SDK_INT >= 30
                && !android.os.Environment.isExternalStorageManager()) {
            Dialogs.confirm(this, tokens, getString(R.string.header_permission_desc),
                    getString(R.string.permission_denied_manage_all),
                    getString(R.string.common_cancel), getString(R.string.common_confirm),
                    confirmed -> {
                        if (!confirmed) return;
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException error) {
                            Toast.makeText(this, R.string.rewrite_not_available,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(intent, cn.lineai.runtime.SafActions.REQUEST_TREE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.rewrite_not_available, Toast.LENGTH_SHORT).show();
        }
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
