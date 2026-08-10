package cn.lineai.ui.theme;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * A {@link ScrollView} whose height can be capped to a maximum number of dp, and that only
 * claims the vertical touch gesture while its own content actually has remaining scroll in
 * the drag direction. When the inner content cannot scroll any further in the current
 * direction, the request to disallow parent interception is released so the outer scrolling
 * container (e.g. the chat ListView) regains control of the gesture.
 */
public class BoundedScrollView extends ScrollView {

    private int maxHeightPx;
    private float lastTouchY;

    public BoundedScrollView(Context context) {
        this(context, 0);
    }

    public BoundedScrollView(Context context, int maxHeightDp) {
        super(context);
        maxHeightPx = maxHeightDp <= 0 ? 0 : LineTheme.dp(context, maxHeightDp);
    }

    public void setMaxHeightDp(int maxHeightDp) {
        maxHeightPx = maxHeightDp <= 0 ? 0 : LineTheme.dp(getContext(), maxHeightDp);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);
            int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
            int cappedSize = heightMode == View.MeasureSpec.UNSPECIFIED
                    ? maxHeightPx
                    : Math.min(heightSize, maxHeightPx);
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(cappedSize, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = event.getY(0);
                requestParentDisallowIntercept(shouldDisallowOnDown(
                        getVisibility() == VISIBLE, getChildCount() > 0, maxHeightPx > 0));
                break;
            case MotionEvent.ACTION_MOVE:
                float nextY = event.getY(0);
                requestParentDisallowIntercept(shouldHandleDrag(
                        canScrollContent(),
                        nextY - lastTouchY,
                        canScrollVertically(-1),
                        canScrollVertically(1)));
                lastTouchY = nextY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowIntercept(false);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private boolean canScrollContent() {
        return getChildCount() > 0
                && canScrollContent(
                        getHeight() - getPaddingTop() - getPaddingBottom(),
                        getChildAt(0).getHeight());
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        ViewParent p = getParent();
        if (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /**
     * Whether the inner content would have any scrollable height (child taller than the
     * usable viewport). Pure function, no Android state.
     */
    public static boolean canScrollContent(int viewportHeight, int childHeight) {
        return viewportHeight > 0 && childHeight > viewportHeight;
    }

    /**
     * Whether the inner drag should keep ownership of the vertical gesture, given the drag
     * delta of the primary pointer and the remaining scroll in each direction.
     */
    public static boolean shouldHandleDrag(boolean contentScrollable, float deltaY,
                                           boolean canScrollUp, boolean canScrollDown) {
        if (!contentScrollable) {
            return false;
        }
        if (deltaY > 0f) {
            return canScrollUp;
        }
        if (deltaY < 0f) {
            return canScrollDown;
        }
        return true;
    }

    /**
     * Whether to claim the gesture on ACTION_DOWN: only when the view is visible, actually
     * has a child, and is bounded to a maximum height.
     */
    public static boolean shouldDisallowOnDown(boolean visible, boolean hasContent,
                                               boolean hasMaxHeight) {
        return visible && hasContent && hasMaxHeight;
    }
}
