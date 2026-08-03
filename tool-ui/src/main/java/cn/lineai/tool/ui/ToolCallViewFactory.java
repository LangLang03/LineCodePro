package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public interface ToolCallViewFactory {
    ToolDisplayCategory category();
    ToolCallCardView createView(Context context);
}
