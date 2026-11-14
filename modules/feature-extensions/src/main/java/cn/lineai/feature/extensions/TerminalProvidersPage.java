package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ScannedTerminalProvider;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** Scan, add and manage external terminal provider apps (IPC). */
public final class TerminalProvidersPage extends ExtensionPage implements Refreshable {

    /** Host callbacks for provider discovery and management. */
    public interface Listener {
        /**
         * Returns the configured terminal providers.
         *
         * @return installed provider configs; may be null or empty
         */
        List<TerminalProviderConfig> installed();

        /**
         * Scans the device for external terminal provider apps.
         *
         * @return discovered providers; may be null or empty
         */
        List<ScannedTerminalProvider> scan();

        /**
         * Adds a scanned provider to the configured set.
         *
         * @param provider provider confirmed by the user
         */
        void add(ScannedTerminalProvider provider);

        /**
         * Enables or disables one configured provider.
         *
         * @param id provider identifier
         * @param enabled true to enable the provider
         */
        void setEnabled(String id, boolean enabled);

        /**
         * Deletes one configured provider.
         *
         * @param id provider identifier
         */
        void delete(String id);
    }

    private final Listener listener;
    private List<ScannedTerminalProvider> scanResults = new ArrayList<>();
    private boolean scanned;

    /**
     * Builds the terminal providers page and runs the initial scan.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param listener host callbacks for provider discovery and management
     */
    public TerminalProvidersPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, tokens, back, R.string.screen_terminal_provider_title);
        this.listener = listener;
        refresh();
    }

    /** Rescans on every page entry since installs and uninstalls change the provider set. */
    @Override
    public void onShown() {
        refresh();
    }

    /** Re-reads installed providers and rescans available providers on every page entry. */
    private void refresh() {
        scanResults = listener.scan();
        scanned = true;
        reload();
    }

    /** Rebuilds the page: installed section, rescan action, and scan results when present. */
    public void reload() {
        content.removeAllViews();
        installed();
        SettingsSection scan = section(R.string.screen_terminal_provider_scan);
        scan.row(ExtensionKit.action(getContext(), theme, Icons.SEARCH,
                R.string.screen_terminal_provider_scan,
                R.string.screen_terminal_provider_scan_desc, 68, () -> {
                    scanResults = listener.scan();
                    scanned = true;
                    reload();
                }), false, 0);
        if (scanned) scanResults();
    }

    /** Adds one row per scanned provider, opening the add confirmation when tapped. */
    private void scanResults() {
        SettingsSection results = section(R.string.screen_terminal_provider_scan_results);
        if (scanResults == null || scanResults.isEmpty()) {
            results.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_terminal_provider_scan_empty), false, 0);
            return;
        }
        for (ScannedTerminalProvider provider : scanResults) {
            cn.lineai.ui.kit.PreferenceRow row = ExtensionKit.plainAction(getContext(), theme,
                    Icons.TERMINAL, R.string.screen_terminal_provider_scan,
                    provider.packageName, 68);
            row.titleText(provider.name);
            row.setOnClickListener(v -> confirmAdd(provider));
            results.row(row, false, 0);
        }
    }

    /** Rebuilds the installed section with switch rows and long-press delete. */
    private void installed() {
        SettingsSection installed = section(R.string.screen_terminal_provider_installed);
        List<TerminalProviderConfig> providers = listener.installed();
        if (providers == null || providers.isEmpty()) {
            installed.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_terminal_provider_empty), false, 0);
            return;
        }
        for (TerminalProviderConfig provider : providers) {
            ExtensionRows.SwitchRow row = new ExtensionRows.SwitchRow(getContext(), theme,
                    Icons.TERMINAL, provider.name, provider.packageName);
            row.checked(provider.enabled);
            row.onToggle(enabled -> listener.setEnabled(provider.id, enabled));
            row.onLongClick(() -> ExtensionKit.confirm(getContext(), theme,
                    R.string.screen_terminal_provider_delete_title,
                    getContext().getString(R.string.screen_terminal_provider_delete_confirm,
                            provider.name),
                    () -> {
                        listener.delete(provider.id);
                        reload();
                    }));
            installed.row(row, false, 0);
        }
    }

    /** Asks for confirmation before adding a scanned provider to the configured set. */
    private void confirmAdd(ScannedTerminalProvider provider) {
        ExtensionKit.confirmAction(getContext(), theme,
                getContext().getString(R.string.screen_terminal_provider_add_confirm,
                        provider.name),
                getContext().getString(R.string.screen_terminal_provider_add_confirm_desc),
                R.string.common_add,
                () -> {
                    listener.add(provider);
                    reload();
                });
    }
}
