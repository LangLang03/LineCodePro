package cn.lineai.ui.component;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;
import java.util.IdentityHashMap;
/** Stacks actions only when their complete labels cannot fit in a horizontal row. */
public final class AdaptiveActionsView extends LinearLayout {
    private final IdentityHashMap<View, LayoutParams> original = new IdentityHashMap<>();
    public AdaptiveActionsView(Context context) { super(context); }
    @Override protected void onMeasure(int width, int height) {
        int available = MeasureSpec.getSize(width) - getPaddingLeft() - getPaddingRight();
        int total = 0;
        for (int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); if(child.getVisibility()==GONE) continue;
            if(!original.containsKey(child)) original.put(child,new LayoutParams((LayoutParams)child.getLayoutParams()));
            child.setMinimumHeight(LineTheme.dp(getContext(),48));
            child.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            total += child.getMeasuredWidth() + LineTheme.dp(getContext(),16);
        }
        boolean stack=total>available;
        setOrientation(stack?VERTICAL:HORIZONTAL);
        for(int i=0;i<getChildCount();i++) {
            View child=getChildAt(i);LayoutParams saved=original.get(child);if(saved==null)continue;
            LayoutParams lp=new LayoutParams(saved);lp.height=LayoutParams.WRAP_CONTENT;
            if(stack) {lp.width=LayoutParams.MATCH_PARENT;lp.weight=0;lp.leftMargin=lp.rightMargin=0;lp.topMargin=i==0?0:LineTheme.dp(getContext(),8);}
            LayoutParams before = (LayoutParams)child.getLayoutParams();
            if (before.width != lp.width || before.height != lp.height || before.weight != lp.weight
                    || before.leftMargin != lp.leftMargin || before.rightMargin != lp.rightMargin || before.topMargin != lp.topMargin) child.setLayoutParams(lp);
        }
        super.onMeasure(width,height);
    }
}
