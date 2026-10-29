package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** Agent extension detail: quick add, installed list, toggle and delete. */
public final class AgentExtensionsPage extends ExtensionPage implements Refreshable {

    public interface Listener {
        List<AgentExtension> load();

        void setEnabled(String id, boolean enabled);

        void delete(String id);

        void add();

        void edit(AgentExtension agent);
    }

    private final Listener listener;
    private final LinearLayout installedHost;

    public AgentExtensionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, tokens, back, R.string.screen_extensions_section_agent);
        this.listener = listener;
        this.installedHost = new LinearLayout(context);
        installedHost.setOrientation(LinearLayout.VERTICAL);
        header(R.string.screen_extensions_section_agent,
                ExtensionKit.addAction(context, theme,
                        R.string.screen_extension_detail_inline_title_agent, listener::add));
        SettingsSection create = section(R.string.screen_extension_detail_section_install_other);
        create.row(ExtensionKit.action(getContext(), theme, Icons.BRAIN,
                R.string.screen_extension_detail_inline_title_agent,
                R.string.screen_extension_detail_inline_desc_agent, 68, listener::add), false, 0);
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
        List<AgentExtension> agents = listener.load();
        if (agents == null || agents.isEmpty()) {
            installed.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_extension_detail_empty_agent), false, 0);
            return;
        }
        for (AgentExtension agent : agents) {
            String name = agent.name.isEmpty() ? agent.slug : agent.name;
            String description = getContext().getString(
                    R.string.screen_extension_agent_row_desc, agent.slug,
                    agent.toolNames.size());
            ExtensionRows.SwitchRow row = new ExtensionRows.SwitchRow(getContext(), theme,
                    Icons.BRAIN, name, description);
            row.checked(agent.enabled);
            row.onToggle(enabled -> listener.setEnabled(agent.id, enabled));
            row.onLongClick(() -> openItemMenu(agent));
            installed.row(row, false, 0);
        }
    }

    private void openItemMenu(AgentExtension agent) {
        String name = agent.name.isEmpty() ? agent.slug : agent.name;
        List<ExtensionKit.SheetItem> items = new ArrayList<>();
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_modify,
                R.string.screen_extension_detail_modify_desc, false, () -> listener.edit(agent)));
        items.add(new ExtensionKit.SheetItem(R.string.screen_extension_detail_delete,
                R.string.screen_extension_detail_delete_desc, true,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_agent_delete_title,
                        getContext().getString(R.string.screen_agent_delete_message, name),
                        () -> {
                            listener.delete(agent.id);
                            renderInstalled();
                        })));
        ExtensionKit.sheet(getContext(), theme, name, items);
    }
}
