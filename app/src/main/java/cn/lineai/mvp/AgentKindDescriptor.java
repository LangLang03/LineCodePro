package cn.lineai.mvp;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.data.model.ExtensionOverviewState;
import java.util.ArrayList;
import java.util.List;

final class AgentKindDescriptor implements ExtensionKindDescriptor {

    @Override
    public String kind() {
        return "agent";
    }

    @Override
    public void setEnabled(ExtensionStore repository, String id, boolean enabled) {
        repository.setAgentEnabled(id, enabled);
    }

    @Override
    public void delete(ExtensionStore repository, String id) {
        repository.deleteAgent(id);
    }

    @Override
    public String title(Context context) {
        return context.getString(R.string.screen_extensions_section_agent);
    }

    @Override
    public int iconType() {
        return IconButtonView.BRAIN;
    }

    @Override
    public String inlineTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_title_agent);
    }

    @Override
    public String inlineDesc(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_desc_agent);
    }

    @Override
    public boolean hasModifyAction() {
        return true;
    }

    @Override
    public int addActionType() {
        return ADD_ACTION_AGENT;
    }

    @Override
    public List<ExtensionItem> getInstalledItems(ExtensionOverviewState state) {
        List<ExtensionAgentConfig> agents = state.getAgents();
        List<ExtensionItem> items = new ArrayList<>(agents.size());
        for (ExtensionAgentConfig agent : agents) {
            String desc = agent.getSlug() + " \u00b7 " + count(agent.getToolNames().size(), "tools");
            items.add(new ExtensionItem(agent.getId(), agent.getName(), desc, agent.isEnabled()));
        }
        return items;
    }

    @Override
    public String emptyMessage(Context context) {
        return context.getString(R.string.screen_extension_detail_empty_agent);
    }

    @Override
    public String sectionTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_section_install_other);
    }

    private static String count(int value, String suffix) {
        return value + " " + suffix;
    }
}
