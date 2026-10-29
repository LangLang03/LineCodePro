package cn.lineai.feature.chat.toolcall;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * Scroll view capped at a maximum height that also hands the vertical gesture back to the outer
 * timeline once its own content reaches the edge (port of the legacy {@code BoundedScrollView}).
 */
final class BoundedScrollView extends ScrollView {

    private final int maxHeightPx;
    private float lastTouchY;

    BoundedScrollView(Context context, int maxHeightDp) {
        super(context);
        this.maxHeightPx = maxHeightDp <= 0 ? 0
                : Math.round(maxHeightDp * context.getResources().getDisplayMetrics().density);
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int capped = mode == MeasureSpec.UNSPECIFIED
                    ? maxHeightPx : Math.min(size, maxHeightPx);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST);
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
                        canScrollContent(), nextY - lastTouchY,
                        canScrollVertically(-1), canScrollVertically(1)));
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

    private boolean canScrollContent() {
        return getChildCount() > 0
                && canScrollContent(getHeight() - getPaddingTop() - getPaddingBottom(),
                        getChildAt(0).getHeight());
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static boolean canScrollContent(int viewportHeight, int childHeight) {
        return viewportHeight > 0 && childHeight > viewportHeight;
    }

    private static boolean shouldHandleDrag(boolean contentScrollable, float deltaY,
                                            boolean canScrollUp, boolean canScrollDown) {
        if (!contentScrollable) return false;
        if (deltaY > 0f) return canScrollUp;
        if (deltaY < 0f) return canScrollDown;
        return true;
    }

    private static boolean shouldDisallowOnDown(boolean visible, boolean hasContent,
                                                boolean hasMaxHeight) {
        return visible && hasContent && hasMaxHeight;
    }
}
