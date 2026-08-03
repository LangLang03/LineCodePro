package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class ImageGenerationToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.IMAGE_GENERATION;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallReadView(context);
    }
}
