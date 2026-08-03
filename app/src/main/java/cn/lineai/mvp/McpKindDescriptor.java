package cn.lineai.mvp;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.data.model.ExtensionOverviewState;
import java.util.ArrayList;
import java.util.List;

final class McpKindDescriptor implements ExtensionKindDescriptor {

    @Override
    public String kind() {
        return "mcp";
    }

    @Override
    public void setEnabled(ExtensionStore repository, String id, boolean enabled) {
        repository.setMcpEnabled(id, enabled);
    }

    @Override
    public void delete(ExtensionStore repository, String id) {
        repository.deleteMcp(id);
    }

    @Override
    public String title(Context context) {
        return context.getString(R.string.screen_extensions_section_mcp);
    }

    @Override
    public int iconType() {
        return IconButtonView.MCP;
    }

    @Override
    public String inlineTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_title_mcp);
    }

    @Override
    public String inlineDesc(Context context) {
        return context.getString(R.string.screen_extension_detail_inline_desc_mcp);
    }

    @Override
    public boolean hasModifyAction() {
        return true;
    }

    @Override
    public int addActionType() {
        return ADD_ACTION_MCP;
    }

    @Override
    public List<ExtensionItem> getInstalledItems(ExtensionOverviewState state) {
        List<ExtensionMcpConfig> mcps = state.getMcps();
        List<ExtensionItem> items = new ArrayList<>(mcps.size());
        for (ExtensionMcpConfig mcp : mcps) {
            String desc = count(mcp.getTools().size(), "tools") + " \u00b7 " + mcp.getUrl();
            items.add(new ExtensionItem(mcp.getId(), mcp.getName(), desc, mcp.isEnabled()));
        }
        return items;
    }

    @Override
    public String emptyMessage(Context context) {
        return context.getString(R.string.screen_extension_detail_empty_mcp);
    }

    @Override
    public String sectionTitle(Context context) {
        return context.getString(R.string.screen_extension_detail_section_install_other);
    }

    private static String count(int value, String suffix) {
        return value + " " + suffix;
    }
}
