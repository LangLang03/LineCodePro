package cn.lineai.ui.component;
import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;
/** Bounded by the current parent window, including when the keyboard is visible. */
public final class InsetSheetLayout extends LinearLayout {
    public InsetSheetLayout(Context context) { super(context); setOrientation(VERTICAL); }
    private int availableHeight;
    public void setAvailableHeight(int height) { availableHeight = height; }
    @Override protected void onMeasure(int w, int h) {
        int width = Math.min(MeasureSpec.getSize(w), LineTheme.dp(getContext(),560));
        int height = availableHeight > 0 ? Math.min(MeasureSpec.getSize(h),availableHeight) : MeasureSpec.getSize(h);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.getMode(h)));
    }
}
