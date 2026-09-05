package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;

/** A reading-width page, including in split screen and landscape windows. */
public class ScreenSurfaceView extends LinearLayout {
    public ScreenSurfaceView(Context context) { super(context); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int gutter = Math.max(0, (MeasureSpec.getSize(widthSpec) - LineTheme.dp(getContext(), 792)) / 2);
        if (getPaddingLeft() != gutter || getPaddingRight() != gutter) {
            setPadding(gutter, getPaddingTop(), gutter, getPaddingBottom());
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
