package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ScannedTerminalProvider;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** Scan, add and manage external terminal provider apps (IPC). */
public final class TerminalProvidersPage extends ExtensionPage {

    public interface Listener {
        List<TerminalProviderConfig> installed();

        List<ScannedTerminalProvider> scan();

        void add(ScannedTerminalProvider provider);

        void setEnabled(String id, boolean enabled);

        void delete(String id);
    }

    private final Listener listener;
    private List<ScannedTerminalProvider> scanResults = new ArrayList<>();
    private boolean scanned;

    public TerminalProvidersPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, tokens, back, R.string.screen_terminal_provider_title);
        this.listener = listener;
        reload();
    }

    public void reload() {
        content.removeAllViews();
        SettingsSection scan = section(R.string.screen_terminal_provider_scan);
        scan.row(ExtensionKit.action(getContext(), theme, Icons.SEARCH,
                R.string.screen_terminal_provider_scan,
                R.string.screen_terminal_provider_scan_desc, 68, () -> {
                    scanResults = listener.scan();
                    scanned = true;
                    reload();
                }), false, 0);
        if (scanned) scanResults();
        installed();
    }

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

    private void confirmAdd(ScannedTerminalProvider provider) {
        ExtensionKit.confirm(getContext(), theme, R.string.screen_terminal_provider_add_confirm,
                getContext().getString(R.string.screen_terminal_provider_add_confirm_desc),
                () -> {
                    listener.add(provider);
                    reload();
                });
    }
}
