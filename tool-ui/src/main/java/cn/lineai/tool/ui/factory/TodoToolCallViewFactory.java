package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class TodoToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.TODO;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallTodoView(context);
    }
}
