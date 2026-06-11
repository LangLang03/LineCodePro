package cn.lineai.ui.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import cn.lineai.ui.theme.LineTheme;

public final class FlowLayoutView extends ViewGroup {
    private int horizontalSpacing;
    private int verticalSpacing;

    public FlowLayoutView(Context context) {
        this(context, null);
    }

    public FlowLayoutView(Context context, AttributeSet attrs) {
        super(context, attrs);
        horizontalSpacing = LineTheme.dp(context, LineTheme.SM);
        verticalSpacing = LineTheme.dp(context, LineTheme.SM);
    }

    public void setSpacingDp(int horizontalDp, int verticalDp) {
        horizontalSpacing = LineTheme.dp(getContext(), horizontalDp);
        verticalSpacing = LineTheme.dp(getContext(), verticalDp);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int maxLineWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        int lineWidth = 0;
        int lineHeight = 0;
        int measuredWidth = 0;
        int measuredHeight = getPaddingTop() + getPaddingBottom();
        boolean hasLine = false;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, measuredHeight);
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            int childWidth = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
            int childHeight = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
            if (hasLine && lineWidth + horizontalSpacing + childWidth > maxLineWidth) {
                measuredWidth = Math.max(measuredWidth, lineWidth);
                measuredHeight += lineHeight + verticalSpacing;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth += hasLine ? horizontalSpacing + childWidth : childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
            hasLine = true;
        }

        if (hasLine) {
            measuredWidth = Math.max(measuredWidth, lineWidth);
            measuredHeight += lineHeight;
        }

        int resolvedWidth = resolveSize(measuredWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int resolvedHeight = resolveSize(measuredHeight, heightMeasureSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int maxLineWidth = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        boolean hasLine = false;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            int childWidth = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
            int childHeight = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
            if (hasLine && x - getPaddingLeft() + horizontalSpacing + childWidth > maxLineWidth) {
                x = getPaddingLeft();
                y += lineHeight + verticalSpacing;
                lineHeight = 0;
            } else if (hasLine) {
                x += horizontalSpacing;
            }

            int childLeft = x + params.leftMargin;
            int childTop = y + params.topMargin;
            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
            x += childWidth;
            lineHeight = Math.max(lineHeight, childHeight);
            hasLine = true;
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams params) {
        return new MarginLayoutParams(params);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams;
    }
}
