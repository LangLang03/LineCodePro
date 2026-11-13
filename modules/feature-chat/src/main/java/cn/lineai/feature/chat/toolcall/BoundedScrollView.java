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

    /** @param context    hosting context
     * @param maxHeightDp hard height cap in dp; non-positive disables the cap */
    BoundedScrollView(Context context, int maxHeightDp) {
        super(context);
        this.maxHeightPx = maxHeightDp <= 0 ? 0
                : Math.round(maxHeightDp * context.getResources().getDisplayMetrics().density);
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
    }

    /** Caps the measured height at the configured maximum. */
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

    /** Claims the drag only while the content can still scroll in the gesture direction. */
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

    /** True when the child is taller than the padded viewport. */
    private boolean canScrollContent() {
        return getChildCount() > 0
                && canScrollContent(getHeight() - getPaddingTop() - getPaddingBottom(),
                        getChildAt(0).getHeight());
    }

    /** Asks the outer scroll container to release the gesture to this view. */
    private void requestParentDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /** Pure scrollability check shared with the instance helper. */
    private static boolean canScrollContent(int viewportHeight, int childHeight) {
        return viewportHeight > 0 && childHeight > viewportHeight;
    }

    /** Whether this view should keep the drag: only while the edge has not been reached. */
    private static boolean shouldHandleDrag(boolean contentScrollable, float deltaY,
                                            boolean canScrollUp, boolean canScrollDown) {
        if (!contentScrollable) return false;
        if (deltaY > 0f) return canScrollUp;
        if (deltaY < 0f) return canScrollDown;
        return true;
    }

    /** Claims the gesture on touch-down when the bounded content is present and visible. */
    private static boolean shouldDisallowOnDown(boolean visible, boolean hasContent,
                                                boolean hasMaxHeight) {
        return visible && hasContent && hasMaxHeight;
    }
}
