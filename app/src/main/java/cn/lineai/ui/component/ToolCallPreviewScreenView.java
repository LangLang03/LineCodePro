package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.DiffUiModel;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ui.DiffLoader;
import cn.lineai.tool.ui.ToolCallViewFactory;
import cn.lineai.tool.ui.ToolCallViewFactoryRegistry;
import cn.lineai.tool.ui.ToolCallWriteView;
import cn.lineai.ui.theme.LineTheme;

/**
 * 工具调用卡片预览页：遍历注册表中的全部 ToolCallViewFactory，
 * 用模拟数据渲染每种卡片，便于在不跑真实工具的情况下检查 UI。
 */
public final class ToolCallPreviewScreenView extends ScreenScaffoldView {

    public ToolCallPreviewScreenView(Context context, Runnable onBack) {
        super(context, context.getString(R.string.screen_toolcall_preview_title), onBack, null);
        LinearLayout content = getContent();

        TextView note = LineTheme.text(context, context.getString(R.string.toolcall_preview_section_note),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(note, LineTheme.LG, LineTheme.XS, LineTheme.LG, 0);
        content.addView(note, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ToolCallViewFactoryRegistry registry = ToolCallViewFactoryRegistry.getDefault();
        if (registry == null) {
            return;
        }
        DiffLoader fakeDiffLoader = diffId -> new DiffUiModel(
                "preview_diff", "app/src/main/java/cn/lineai/MainActivity.java",
                "old line\nold line 2", "new line\nnew line 2\nnew line 3", false);

        for (ToolCallViewFactory factory : registry.getAllFactories()) {
            appendSample(context, content, factory, fakeDiffLoader);
            if (factory.category() == ToolDisplayCategory.SHELL) {
                appendSample(context, content, factory, fakeDiffLoader, ToolCallPreviewSamples.runningShell());
            }
        }
    }

    private void appendSample(Context context, LinearLayout content, ToolCallViewFactory factory,
                              DiffLoader fakeDiffLoader) {
        appendSample(context, content, factory, fakeDiffLoader, ToolCallPreviewSamples.forCategory(factory.category()));
    }

    private void appendSample(Context context, LinearLayout content, ToolCallViewFactory factory,
                              DiffLoader fakeDiffLoader, ToolCallPreviewSamples.Sample sample) {
        if (sample == null) {
            return;
        }
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        LineTheme.padding(group, LineTheme.LG, LineTheme.MD, LineTheme.LG, 0);

        TextView label = LineTheme.text(context, factory.category().name(), LineTheme.FONT_XS,
                LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        LineTheme.padding(label, 0, 0, 0, LineTheme.XS);
        group.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        ToolCallCardView card = factory.createView(context);
        if (card == null) {
            return;
        }
        if (card instanceof ToolCallWriteView) {
            ((ToolCallWriteView) card).setDiffLoader(fakeDiffLoader);
        }
        View cardView = (View) card;
        card.setProjectPath("");
        card.bind(sample.call, sample.result);
        group.addView(cardView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        content.addView(group, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
