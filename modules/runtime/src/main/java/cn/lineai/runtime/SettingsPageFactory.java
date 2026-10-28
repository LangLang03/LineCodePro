package cn.lineai.runtime;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.View;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.settings.AboutPage;
import cn.lineai.feature.settings.AdvancedFeaturesPage;
import cn.lineai.feature.settings.BehaviorSettingsPage;
import cn.lineai.feature.settings.DataSettingsPage;
import cn.lineai.feature.settings.ErrorLogsPage;
import cn.lineai.feature.settings.ExtensionsPage;
import cn.lineai.feature.settings.InputSettingsPage;
import cn.lineai.feature.settings.KeepAliveSettingsPage;
import cn.lineai.feature.settings.LicensesPage;
import cn.lineai.feature.settings.McpPreferences;
import cn.lineai.feature.settings.McpSettingsPage;
import cn.lineai.feature.settings.MemoryPage;
import cn.lineai.feature.settings.OutputSettingsPage;
import cn.lineai.feature.settings.PhoneControlPage;
import cn.lineai.feature.settings.PhoneControlPreferences;
import cn.lineai.feature.settings.PromptTemplatesCatalog;
import cn.lineai.feature.settings.PromptTemplatesPage;
import cn.lineai.feature.settings.SecuritySettingsPage;
import cn.lineai.feature.settings.SettingsHome;
import cn.lineai.feature.settings.SettingsScreens;
import cn.lineai.feature.settings.SshSettingsPage;
import cn.lineai.feature.settings.StorageManagementPage;
import cn.lineai.feature.settings.TermuxIntegrationPage;
import cn.lineai.feature.settings.ThemePreferences;
import cn.lineai.feature.settings.ThemeSettingsPage;
import cn.lineai.feature.settings.ToolPreferences;
import cn.lineai.feature.settings.ToolSettingsPage;
import cn.lineai.feature.settings.TutorialPage;
import cn.lineai.feature.terminal.SshPreferences;
import cn.lineai.feature.terminal.SshService;
import cn.lineai.feature.terminal.TermuxClient;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.WebPage;

/** Settings family screens constructed outside the activity. */
public final class SettingsPageFactory {

    private final PageHost host;

    SettingsPageFactory(PageHost host) {
        this.host = host;
    }

    View create(ScreenKey<?, ?> key) {
        if (key.id() == SettingsScreens.HOME) {
            return new SettingsHome(host.activity(), host.tokens(), host.navigation()::back,
                    host::settingsDestination);
        }
        if (key.id() == SettingsScreens.INPUT) {
            InputSettingsPage page = new InputSettingsPage(host.activity(), host.tokens(),
                    host.navigation()::back, host.graph().preferences()::dispatch);
            page.render(host.graph().preferences().state());
            return page;
        }
        if (key.id() == SettingsScreens.OUTPUT) {
            OutputSettingsPage page = new OutputSettingsPage(host.activity(), host.tokens(),
                    host.navigation()::back, host.graph().preferences()::dispatch,
                    host::unavailable);
            page.render(host.graph().preferences().state());
            return page;
        }
        if (key.id() == SettingsScreens.BEHAVIOR) {
            BehaviorSettingsPage page = new BehaviorSettingsPage(host.activity(), host.tokens(),
                    host.navigation()::back, host.graph().preferences()::dispatch,
                    () -> host.navigation().push(SettingsScreens.PROMPTS.key(Params.Empty.INSTANCE)));
            page.render(host.graph().preferences().state());
            return page;
        }
        if (key.id() == SettingsScreens.SECURITY) {
            SecuritySettingsPage page = new SecuritySettingsPage(host.activity(), host.tokens(),
                    host.navigation()::back, host.graph().preferences()::dispatch);
            page.render(host.graph().preferences().state());
            return page;
        }
        if (key.id() == SettingsScreens.MCP) {
            return new McpSettingsPage(host.activity(), host.tokens(), host.navigation()::back,
                    new McpPreferences(host.graph().settings()), new McpSettingsPage.Listener() {
                        public void onOpenSshSettings() {
                            host.navigation().push(SettingsScreens.SSH.key(Params.Empty.INSTANCE));
                        }
                        public void onOpenTermuxIntegration() {
                            host.navigation().push(SettingsScreens.TERMUX.key(Params.Empty.INSTANCE));
                        }
                    });
        }
        if (key.id() == SettingsScreens.EXTENSIONS) {
            return new ExtensionsPage(host.activity(), host.tokens(), host.navigation()::back,
                    this::openExtension);
        }
        if (key.id() == SettingsScreens.MEMORY) return memoryPage();
        if (key.id() == SettingsScreens.TUTORIAL) {
            return new TutorialPage(host.activity(), host.tokens(), host.navigation()::back);
        }
        if (key.id() == SettingsScreens.SSH) return sshPage();
        if (key.id() == SettingsScreens.TERMUX) return termuxPage();
        if (key.id() == SettingsScreens.TOOL_SETTINGS) return toolSettingsPage();
        if (key.id() == SettingsScreens.DATA) {
            return new DataSettingsPage(host.activity(), host.tokens(), host.navigation()::back,
                    new DataSettingsPage.Listener() {
                        public void onExport() { host.requestExport(); }
                        public void onImport() { host.requestImport(); }
                    });
        }
        if (key.id() == SettingsScreens.ERROR_LOGS) return errorLogsPage();
        if (key.id() == SettingsScreens.STORAGE) return storagePage();
        if (key.id() == SettingsScreens.KEEP_ALIVE) return keepAlivePage();
        if (key.id() == SettingsScreens.ADVANCED) {
            return new AdvancedFeaturesPage(host.activity(), host.tokens(),
                    host.navigation()::back,
                    id -> host.navigation().push(
                            SettingsScreens.PHONE_CONTROL.key(Params.Empty.INSTANCE)));
        }
        if (key.id() == SettingsScreens.PHONE_CONTROL) return phoneControlPage();
        if (key.id() == SettingsScreens.PROMPTS) return promptsPage();
        if (key.id() == SettingsScreens.ABOUT) return aboutPage();
        if (key.id() == SettingsScreens.LICENSES) {
            return new LicensesPage(host.activity(), host.tokens(), host.navigation()::back);
        }
        if (key.id() == SettingsScreens.THEME) return themePage();
        if (key.id() == PageFactory.BROWSER) {
            cn.lineai.core.model.UiPreferences values = host.graph().preferences().state().values;
            return new WebPage(host.activity(), host.tokens(),
                    key.args().requireString("url"), values.allowAnyHttp(),
                    values.browserJavaScript(), host.navigation()::back);
        }
        throw new IllegalArgumentException("Unregistered settings screen: " + key.id().route());
    }

    private void openExtension(String id) {
        if ("agent".equals(id)) {
            host.navigation().push(ExtensionScreens.AGENTS.key(Params.Empty.INSTANCE));
        } else if ("mcp".equals(id)) {
            host.navigation().push(ExtensionScreens.MCPS.key(Params.Empty.INSTANCE));
        } else if ("skills".equals(id)) {
            host.navigation().push(ExtensionScreens.SKILLS.key(Params.Empty.INSTANCE));
        } else if ("linecode".equals(id)) {
            host.navigation().push(ExtensionScreens.LINECODE.key(Params.Empty.INSTANCE));
        } else if ("terminalProvider".equals(id)) {
            host.navigation().push(ExtensionScreens.TERMINAL_PROVIDERS.key(Params.Empty.INSTANCE));
        } else {
            host.unavailable();
        }
    }

    private View memoryPage() {
        return new MemoryPage(host.activity(), host.tokens(), host.navigation()::back,
                new MemoryPage.Listener() {
                    public java.util.List<cn.lineai.core.model.MemoryItem> load() {
                        return host.graph().memories().list("");
                    }
                    public void save(String id, String scope, String content) {
                        cn.lineai.core.model.MemoryItem existing = null;
                        for (cn.lineai.core.model.MemoryItem memory
                                : host.graph().memories().list("")) {
                            if (memory.id.equals(id)) existing = memory;
                        }
                        host.graph().memories().save(new cn.lineai.core.model.MemoryItem(
                                id, scope, existing == null ? "" : existing.projectId,
                                existing == null ? "" : existing.title, content,
                                existing == null ? "manual" : existing.source,
                                existing == null ? 1.0 : existing.confidence,
                                existing == null ? 0L : existing.createdAt,
                                existing == null ? 0L : existing.updatedAt,
                                existing == null ? 0L : existing.lastUsedAt,
                                existing == null ? 0 : existing.useCount));
                    }
                    public void delete(String id) { host.graph().memories().delete(id); }
                    public void deleteAll(java.util.List<String> ids) {
                        host.graph().memories().deleteAll(ids);
                    }
                    public java.util.List<cn.lineai.core.model.TodoItem> loadShortTerm() {
                        return host.graph().todos().items();
                    }
                    public java.util.List<cn.lineai.core.model.Conversation> loadChatIndex() {
                        return host.graph().conversations().list();
                    }
                });
    }

    private View sshPage() {
        final SshPreferences preferences = new SshPreferences(host.graph().settings());
        final SshService service = new SshService(host.activity().getFilesDir());
        final SshSettingsPage[] holder = new SshSettingsPage[1];
        holder[0] = new SshSettingsPage(host.activity(), host.tokens(), host.navigation()::back,
                new SshSettingsPage.Listener() {
                    public cn.lineai.core.model.SshConfig load() { return preferences.config(); }
                    public void save(cn.lineai.core.model.SshConfig config) {
                        preferences.save(config);
                    }
                    public void test(cn.lineai.core.model.SshConfig config) {
                        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
                        scheduler.io(() -> {
                            try {
                                String output = service.testConnection(config);
                                scheduler.main(() -> {
                                    holder[0].setTesting(false);
                                    holder[0].showStatus(string(R.string.screen_ssh_status_success_title),
                                            output.trim().isEmpty()
                                                    ? string(R.string.screen_ssh_status_success_message)
                                                    : output.trim(), false);
                                });
                            } catch (cn.lineai.core.kernel.error.DomainException error) {
                                scheduler.main(() -> {
                                    holder[0].setTesting(false);
                                    holder[0].showStatus(string(R.string.screen_ssh_status_failed_title),
                                            error.detail().isEmpty() ? error.code().name()
                                                    : error.detail(), true);
                                });
                            }
                        });
                    }
                    public void onOpenTermux() {
                        host.navigation().push(SettingsScreens.TERMUX.key(Params.Empty.INSTANCE));
                    }
                });
        return holder[0];
    }

    private View termuxPage() {
        final TermuxClient client = new TermuxClient(host.activity());
        final SshPreferences preferences = new SshPreferences(host.graph().settings());
        final SshService service = new SshService(host.activity().getFilesDir());
        final TermuxIntegrationPage[] holder = new TermuxIntegrationPage[1];
        holder[0] = new TermuxIntegrationPage(host.activity(), host.tokens(), host.navigation()::back,
                new TermuxIntegrationPage.Listener() {
                    public void onOpenTermux() {
                        try {
                            client.openTermux();
                            holder[0].showStatus(string(R.string.screen_termux_status_opened_title),
                                    string(R.string.screen_termux_status_opened_message), false);
                        } catch (RuntimeException error) {
                            holder[0].showStatus(string(R.string.screen_termux_status_open_failed_title),
                                    error.getMessage(), true);
                        }
                    }
                    public void onRequestRunCommandPermission() {
                        host.activity().requestPermissions(new String[] {
                                TermuxClient.TERMUX_RUN_COMMAND_PERMISSION
                        }, 7302);
                        holder[0].showStatus(string(R.string.screen_termux_status_requested_title),
                                string(R.string.screen_termux_status_requested_message), false);
                    }
                    public void onSetup() {
                        holder[0].setSetupRunning(true);
                        holder[0].showStatus(string(R.string.screen_termux_status_setup_title),
                                string(R.string.screen_termux_status_setup_message), false);
                        client.setupOpenSsh(host.graph().graph().get(Scheduler.class),
                                new TermuxClient.Callback() {
                                    public void onSuccess(TermuxClient.SetupResult result) {
                                        preferences.save(result.config);
                                        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
                                        scheduler.io(() -> {
                                            try {
                                                String output = service.testConnection(result.config);
                                                scheduler.main(() -> {
                                                    holder[0].setSetupRunning(false);
                                                    holder[0].showStatus(
                                                            string(R.string.screen_termux_status_setup_done_title),
                                                            string(R.string.screen_termux_status_setup_done_message,
                                                                    result.shell.isEmpty() ? "unknown" : result.shell,
                                                                    result.rcPath.isEmpty() ? "unknown" : result.rcPath,
                                                                    TermuxClient.redact(output.trim().isEmpty()
                                                                            ? result.output : output)),
                                                            false);
                                                });
                                            } catch (cn.lineai.core.kernel.error.DomainException error) {
                                                scheduler.main(() -> {
                                                    holder[0].setSetupRunning(false);
                                                    holder[0].showStatus(
                                                            string(R.string.screen_termux_status_setup_failed_title),
                                                            TermuxClient.redact(error.detail()), true);
                                                });
                                            }
                                        });
                                    }
                                    public void onError(String message) {
                                        holder[0].setSetupRunning(false);
                                        holder[0].showStatus(
                                                string(R.string.screen_termux_status_setup_failed_title),
                                                TermuxClient.redact(message), true);
                                    }
                                });
                    }
                });
        return holder[0];
    }

    private View toolSettingsPage() {
        ToolPreferences preferences = new ToolPreferences(host.graph().settings());
        return new ToolSettingsPage(host.activity(), host.tokens(), host.navigation()::back,
                modelLabel(preferences.imageUnderstandingModelId()),
                modelLabel(preferences.imageGenerationModelId()), preferences.webSearch(),
                new ToolSettingsPage.Listener() {
                    public void onPickImageUnderstanding() { host.pickToolModel(true); }
                    public void onPickImageGeneration() { host.pickToolModel(false); }
                    public void onWebSearchChanged(cn.lineai.core.model.WebSearchConfig config) {
                        preferences.saveWebSearch(config);
                    }
                });
    }

    private View errorLogsPage() {
        final ErrorLogsPage[] holder = new ErrorLogsPage[1];
        ErrorLogsPage.Listener listener = new ErrorLogsPage.Listener() {
            public java.util.List<cn.lineai.data.api.ErrorLog> load() {
                return host.graph().errorLogs().list();
            }
            public void clear() {
                host.graph().errorLogs().clear();
                if (holder[0] != null) {
                    holder[0].render(java.util.Collections.emptyList());
                }
            }
        };
        holder[0] = new ErrorLogsPage(host.activity(), host.tokens(), host.navigation()::back,
                listener);
        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
        scheduler.io(() -> {
            java.util.List<cn.lineai.data.api.ErrorLog> logs = listener.load();
            scheduler.main(() -> holder[0].render(logs));
        });
        return holder[0];
    }

    private View storagePage() {
        final StorageManagementPage[] holder = new StorageManagementPage[1];
        holder[0] = new StorageManagementPage(host.activity(), host.tokens(),
                host.navigation()::back,
                () -> loadStorage(stats -> {
                    if (holder[0] != null) holder[0].render(stats);
                }));
        loadStorage(holder[0]::render);
        return holder[0];
    }

    private void loadStorage(java.util.function.Consumer<cn.lineai.data.api.StorageStats> consumer) {
        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
        scheduler.io(() -> {
            cn.lineai.data.api.StorageStats stats = host.graph().storageStats().load();
            scheduler.main(() -> consumer.accept(stats));
        });
    }

    private View keepAlivePage() {
        KeepAliveSettingsPage page = new KeepAliveSettingsPage(host.activity(), host.tokens(),
                host.navigation()::back, host.graph().preferences()::dispatch,
                new KeepAliveSettingsPage.Listener() {
                    public boolean ignoringBatteryOptimizations() {
                        return host.isIgnoringBatteryOptimizations();
                    }
                    public void onRequestBatteryOptimizationExemption() {
                        host.requestBatteryOptimizationExemption();
                    }
                    public void onServiceChanged() { host.updateKeepAliveService(); }
                });
        page.render(host.graph().preferences().state());
        return page;
    }

    private View phoneControlPage() {
        return new PhoneControlPage(host.activity(), host.tokens(), host.navigation()::back,
                new PhoneControlPreferences(host.graph().settings()),
                new PhoneControlPage.Listener() {
                    public boolean accessibilityEnabled() {
                        return cn.lineai.platform.AccessibilitySupport.isServiceEnabled(
                                host.activity(), "cn.lineai.platform.LineCodeAccessibilityService");
                    }
                    public void onOpenAccessibilitySettings() {
                        try {
                            host.activity().startActivity(new Intent(
                                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (ActivityNotFoundException error) {
                            host.unavailable();
                        }
                    }
                    public void onAcceptDisclaimer() {
                        new PhoneControlPreferences(host.graph().settings()).acceptDisclaimer();
                    }
                });
    }

    private View promptsPage() {
        return new PromptTemplatesPage(host.activity(), host.tokens(), host.navigation()::back,
                PromptTemplatesCatalog.entries(host.graph().prompts(), host.activity()),
                new PromptTemplatesPage.Listener() {
                    public void onSave(String id, String value) {
                        host.graph().prompts().save(PromptTemplatesCatalog.prompt(id), value);
                    }
                    public void onReset(String id) {
                        host.graph().prompts().reset(PromptTemplatesCatalog.prompt(id));
                    }
                });
    }

    private View aboutPage() {
        return new AboutPage(host.activity(), host.tokens(), host.navigation()::back,
                AppInfo.label(host.activity()), AppInfo.versionName(host.activity()),
                AppInfo.versionCode(host.activity()),
                () -> host.openLink("https://github.com/LangLang03/LineCodePro"),
                () -> host.navigation().push(SettingsScreens.LICENSES.key(Params.Empty.INSTANCE)));
    }

    private View themePage() {
        ThemePreferences theme = host.graph().themePreferences();
        return new ThemeSettingsPage(host.activity(), host.tokens(), host.navigation()::back,
                theme.mode(), theme.customColors(),
                mode -> theme.saveMode(mode, () -> host.graph().theme().apply(theme.resolve(mode,
                        theme.customColors(), isNightMode()))),
                colors -> theme.saveColors(colors, () -> host.graph().theme().apply(
                        cn.lineai.core.model.ThemePalette.customDefault()
                                .withCustomColors(colors))));
    }

    private boolean isNightMode() {
        return (host.activity().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private String modelLabel(String id) {
        if (id == null || id.isEmpty()) return "";
        cn.lineai.core.model.ModelConfig model = host.graph().models().find(id);
        return model == null ? "" : model.name;
    }

    private String string(int id) {
        return host.activity().getString(id);
    }

    private String string(int id, Object... args) {
        return host.activity().getString(id, args);
    }
}
