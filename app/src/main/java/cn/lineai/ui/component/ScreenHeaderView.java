package cn.lineai.ui.component;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

/** Navigation and page title are separate so actions never squeeze the heading. */
public final class ScreenHeaderView extends LinearLayout {
    static final int ACTION_SIZE_DP = 48;
    static final int ICON_SIZE_DP = 22;
    public ScreenHeaderView(Context context, String title, Runnable onBack, View rightAction) {
        this(context, title, onBack == null ? null : backButton(context, onBack), rightAction);
    }
    public ScreenHeaderView(Context context, String title, Runnable onBack, View rightAction, boolean inlineTitle) {
        this(context, title, onBack == null ? null : backButton(context, onBack), rightAction, inlineTitle);
    }
    public ScreenHeaderView(Context context, String title, View leftAction, View rightAction) {
        this(context, title, leftAction, rightAction, false);
    }
    private ScreenHeaderView(Context context, String title, View leftAction, View rightAction, boolean inlineTitle) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        LinearLayout nav = new LinearLayout(context);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(nav, 4, 4, 8, 0);
        if (leftAction != null) addAction(nav, leftAction);
        TextView heading = LineTheme.textMedium(context, title, inlineTitle ? 18 : 22, LineTheme.TEXT);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        if (inlineTitle) {
            heading.setSingleLine(true);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nav.addView(heading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        } else {
            nav.addView(new View(context), new LayoutParams(0, 1, 1f));
        }
        if (rightAction != null) {
            addAction(nav, rightAction);
        }
        addView(nav, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (!inlineTitle) {
            LineTheme.padding(heading, 16, 8, 16, 14);
            addView(heading, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }
    private void addAction(LinearLayout nav, View action) {
        int size = LineTheme.dp(getContext(), ACTION_SIZE_DP);
        action.setMinimumHeight(size);
        if (action instanceof IconButtonView) {
            ((IconButtonView) action).setIconSizeDp(ACTION_SIZE_DP, ICON_SIZE_DP);
            nav.addView(action, new LayoutParams(size, size));
        } else {
            nav.addView(action, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }
    private static View backButton(Context context, Runnable onBack) {
        IconButtonView button = new IconButtonView(context, IconButtonView.CHEVRON_LEFT);
        button.setIconColor(LineTheme.TEXT);
        button.setIconSizeDp(ACTION_SIZE_DP, ICON_SIZE_DP);
        button.setOnClickListener(v -> onBack.run());
        return button;
    }
}
