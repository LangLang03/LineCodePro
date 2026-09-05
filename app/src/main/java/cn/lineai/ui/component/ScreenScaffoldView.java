package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class ScreenScaffoldView extends LinearLayout {
    private final LinearLayout content;
    private final ScrollView scrollView;
    private final View rightAction;

    public ScreenScaffoldView(Context context, String title, Runnable onBack, View rightAction) {
        super(context);
        this.rightAction = rightAction;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        addView(new ScreenHeaderView(context, title, onBack, rightAction), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 0, 0, 0, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public LinearLayout getContent() {
        return content;
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    protected View getRightAction() {
        return rightAction;
    }

    /**
     * Convenience access to string resources for screen subclasses, so screens
     * can call {@code getString(R.string.xxx)} instead of
     * {@code getContext().getString(...)}.
     */
    protected String getString(int resId) {
        return getContext().getString(resId);
    }

    protected String getString(int resId, Object... formatArgs) {
        return getContext().getString(resId, formatArgs);
    }
}
