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

    public interface Listener {
        List<McpExtension> load();

        void setEnabled(String id, boolean enabled);

        void delete(String id);

        void add();

        void edit(McpExtension extension);
    }

    private final Listener listener;
    private final LinearLayout installedHost;

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

    @Override public void onShown() {
        renderInstalled();
    }

    public void reload() {
        renderInstalled();
    }

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
