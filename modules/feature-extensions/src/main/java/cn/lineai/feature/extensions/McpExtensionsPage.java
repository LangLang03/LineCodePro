package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.McpExtension;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** MCP extension detail: add a server, toggle or delete installed servers. */
public final class McpExtensionsPage extends ExtensionPage implements Refreshable {

    /** Host callbacks for MCP server extension management. */
    public interface Listener {
        /**
         * Loads the installed MCP extensions.
         *
         * @return current extensions; may be null or empty
         */
        List<McpExtension> load();

        /**
         * Enables or disables one MCP extension.
         *
         * @param id extension identifier
         * @param enabled true to enable the extension
         */
        void setEnabled(String id, boolean enabled);

        /**
         * Deletes one MCP extension.
         *
         * @param id extension identifier
         */
        void delete(String id);

        /** Opens the editor for creating a new MCP extension. */
        void add();

        /**
         * Opens the editor for an existing MCP extension.
         *
         * @param extension extension to edit
         */
        void edit(McpExtension extension);
    }

    private final Listener listener;
    private final LinearLayout installedHost;

    /**
     * Builds the MCP extension page.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param listener host callbacks for extension management
     */
    public McpExtensionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, tokens, back, R.string.screen_extensions_section_mcp);
        this.listener = listener;
        this.installedHost = new LinearLayout(context);
        installedHost.setOrientation(LinearLayout.VERTICAL);
        header(R.string.screen_extensions_section_mcp,
                ExtensionKit.addAction(context, theme,
                        R.string.screen_extension_detail_inline_title_mcp, listener::add));
        SettingsSection create = section(R.string.screen_extension_detail_section_install_other);
        create.row(ExtensionKit.action(getContext(), theme, Icons.MCP,
                R.string.screen_extension_detail_inline_title_mcp,
                R.string.screen_extension_detail_inline_desc_mcp, 68, listener::add), false, 0);
        content.addView(installedHost, new LinearLayout.LayoutParams(-1, -2));
        renderInstalled();
    }

    /** Re-renders the installed list when the page returns to the foreground. */
    @Override public void onShown() {
        renderInstalled();
    }

    /** Forces a re-render of the installed list after external changes. */
    public void reload() {
        renderInstalled();
    }

    /** Rebuilds the installed servers section with switch rows and empty-state handling. */
    private void renderInstalled() {
        installedHost.removeAllViews();
        SettingsSection installed = ExtensionKit.section(getContext(), theme,
                R.string.screen_extension_detail_section_installed);
        installedHost.addView(installed, new LinearLayout.LayoutParams(-1, -2));
        List<McpExtension> servers = listener.load();
        if (servers == null || servers.isEmpty()) {
            installed.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_extension_detail_empty_mcp), false, 0);
            return;
        }
        for (McpExtension server : servers) {
            String description = getContext().getString(
                    R.string.screen_extension_mcp_row_desc, server.tools.size(), server.url);
            ExtensionRows.SwitchRow row = new ExtensionRows.SwitchRow(getContext(), theme,
                    Icons.MCP, server.name, description);
            row.checked(server.enabled);
            row.onToggle(enabled -> listener.setEnabled(server.id, enabled));
            row.onLongClick(() -> openItemMenu(server));
            installed.row(row, false, 0);
        }
    }

    /** Shows the long-press menu with edit and confirmed delete actions for one server. */
    private void openItemMenu(McpExtension server) {
        List<ExtensionKit.SheetItem> items = new ArrayList<>();
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_modify,
                R.string.screen_extension_detail_modify_desc, false, () -> listener.edit(server)));
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_delete,
                R.string.screen_extension_detail_delete_desc, true,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_mcp_delete_title,
                        getContext().getString(R.string.screen_mcp_delete_message, server.name),
                        () -> {
                            listener.delete(server.id);
                            renderInstalled();
                        })));
        ExtensionKit.sheet(getContext(), theme, server.name, items);
    }
}
