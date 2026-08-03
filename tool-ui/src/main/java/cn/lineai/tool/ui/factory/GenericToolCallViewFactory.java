package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ui.R;
import cn.lineai.tool.ToolDisplayCategory;

public final class GenericToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.GENERIC;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallGenericView(context, context.getString(R.string.tool_call_block_mcp));
    }
}
