package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class AgentToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.AGENT;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallAgentView(context);
    }
}
