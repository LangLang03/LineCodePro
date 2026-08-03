package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class DeleteToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.DELETE;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallDeleteView(context);
    }
}
