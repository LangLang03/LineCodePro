package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class WriteToolCallViewFactory implements ToolCallViewFactory {
    private final DiffLoader diffLoader;

    public WriteToolCallViewFactory(DiffLoader diffLoader) {
        this.diffLoader = diffLoader;
    }

    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.WRITE;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        ToolCallWriteView view = new ToolCallWriteView(context);
        view.setDiffLoader(diffLoader);
        return view;
    }
}
